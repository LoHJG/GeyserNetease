/*
 * Copyright (c) 2026 EiluDick/ZDarkZ
 * Released under the MIT License.
 */

package nc.geyserext.netease.handler;

import nc.geyserext.netease.NeteaseExtension;
import nc.geyserext.netease.session.NeteaseSession;
import nc.geyserext.netease.util.protocol.NeteaseCodecRegistry;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.*;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.bedrock.util.*;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.event.bedrock.SessionInitializeEvent;
import org.geysermc.geyser.event.type.SessionLoadResourcePacksEventImpl;
import org.geysermc.geyser.network.GameProtocol;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.session.auth.*;
import org.geysermc.geyser.text.GeyserLocale;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.*;

public class NetEaseUpstreamHandler extends UpstreamHandlerBase {

    private NeteaseSession neteaseSession;

    public NetEaseUpstreamHandler(GeyserImpl geyser, GeyserSession session) {
        super(geyser, session);
    }

    @Override
    public PacketSignal handle(RequestNetworkSettingsPacket packet) {
        Integer rakVer = getRakVersion();
        boolean isNetEase = rakVer != null && rakVer == 8;
        if (!isNetEase) return super.handle(packet);

        BedrockCodec codec = NeteaseCodecRegistry.getCodec(packet.getProtocolVersion());
        if (codec == null) return super.handle(packet);

        session.getUpstream().getSession().setCodec(codec);
        neteaseSession = new NeteaseSession(session, packet.getProtocolVersion(), codec);

        NetworkSettingsPacket resp = new NetworkSettingsPacket();
        resp.setCompressionAlgorithm(PacketCompressionAlgorithm.ZLIB);
        resp.setCompressionThreshold(512);
        session.sendUpstreamPacketImmediately(resp);

        NetEaseCompression nc = new NetEaseCompression();
        nc.setLevel(geyser.config().advanced().bedrock().compressionLevel());
        session.getUpstream().getSession().getPeer().setCompression(new SimpleCompressionStrategy(nc));

        networkSettingsRequested = true;
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(LoginPacket packet) {
        if (neteaseSession == null) return super.handle(packet);
        if (!networkSettingsRequested) { session.disconnect("Protocol error"); return PacketSignal.HANDLED; }
        if (receivedLoginPacket) { session.disconnect("Duplicate login"); session.forciblyCloseUpstream(); return PacketSignal.HANDLED; }
        receivedLoginPacket = true;

        try {
            if (packet.getAuthPayload() instanceof CertificateChainPayload chain) {
                ChainValidationResult result = NetEaseEncryptionUtils.validateChain(chain);
                var extra = result.identityClaims().extraData;
                String name = extra.displayName != null ? extra.displayName : "NetEasePlayer";
                String id = extra.identity != null ? extra.identity.toString() : UUID.randomUUID().toString();
                String x = extra.xuid != null ? extra.xuid : "0";
                Long iat = (Long) result.rawIdentityClaims().get("iat");

                session.setAuthData(new AuthData(name, UUID.fromString(id), x, iat != null ? iat : -1, extra.minecraftId != null ? extra.minecraftId : ""));
                session.setCertChainData(chain.getChain());

                if (packet.getClientJwt() != null && !packet.getClientJwt().isEmpty()) {
                    try {
                        byte[] cd = EncryptionUtils.verifyClientData(packet.getClientJwt(), result.identityClaims().parsedIdentityPublicKey());
                        if (cd != null) { BedrockClientData bcd = GeyserImpl.GSON.fromJson(new String(cd), BedrockClientData.class); bcd.setOriginalString(packet.getClientJwt()); session.setClientData(bcd); }
                    } catch (Exception ignored) {}
                }
                if (session.getClientData() == null)
                    session.setClientData(GeyserImpl.GSON.fromJson("{\"GameVersion\":\"1.21.40\",\"LanguageCode\":\"en_us\",\"DeviceOS\":1,\"DeviceModel\":\"NetEase\",\"ThirdPartyName\":\"" + name + "\"}", BedrockClientData.class));

                Object red = result.rawIdentityClaims().get("extraData");
                if (red instanceof Map) { Object u = ((Map<?,?>)red).get("uid"); if (u instanceof Number) neteaseSession.setUid(((Number)u).longValue()); }
                if (neteaseSession.uid() == 0) neteaseSession.setUid(Math.abs(name.hashCode()));

                startEncryptionHandshake(session, result.identityClaims().parsedIdentityPublicKey());
                if (session.isClosed()) { session.forciblyCloseUpstream(); return PacketSignal.HANDLED; }
                neteaseSession.setLoginDeferred(true);
            }
        } catch (Exception e) { session.disconnect("disconnectionScreen.internalError.cantConnect"); NeteaseExtension.LOG.error("NetEase auth failed", e); return PacketSignal.HANDLED; }

        int spoofedVersion = GameProtocol.DEFAULT_BEDROCK_PROTOCOL;
        var blockMappings = BlockRegistries.BLOCKS.forVersion(spoofedVersion);
        session.setBlockMappings(blockMappings);
        session.setItemMappings(Registries.ITEMS.forVersion(spoofedVersion));

        boolean patched = false;
        try { patched = nc.geyserext.netease.session.SpoofedUpstream.patchAll(blockMappings); }
        catch (Throwable e) { NeteaseExtension.LOG.error("BlockMappings Unsafe: " + e.getMessage()); }

        int maxId = 0;
        for (int i = 0; blockMappings.getDefinition(i) != null; i++) maxId = i;
        int[] rtToHash = new int[maxId + 1];
        boolean[] hValid = new boolean[maxId + 1];
        for (int i = 0; i <= maxId; i++) {
            var block = blockMappings.getDefinition(i);
            if (block instanceof org.geysermc.geyser.registry.type.GeyserBedrockBlock gb && gb.getState() != null) {
                rtToHash[i] = nc.geyserext.netease.util.BlockHashUtils.fnv1a32Nbt(gb.getState());
                hValid[i] = true;
            }
        }

        try {
            var spoofed = new nc.geyserext.netease.session.SpoofedUpstream(
                session.getUpstream(), session.getUpstream().getSession(), rtToHash, hValid, patched);
            java.lang.reflect.Field upF = org.geysermc.geyser.session.GeyserSession.class.getDeclaredField("upstream");
            upF.setAccessible(true); upF.set(session, spoofed);
        } catch (Exception e) { NeteaseExtension.LOG.error("SpoofedUpstream inject failed: " + e.getMessage()); }

        geyser.getSessionManager().addPendingSession(session);
        geyser.eventBus().fire(new SessionInitializeEvent(session));
        this.resourcePackLoadEvent = new SessionLoadResourcePacksEventImpl(session);
        this.geyser.eventBus().fireEventElseKick(this.resourcePackLoadEvent, session);
        if (session.isClosed()) return PacketSignal.HANDLED;
        session.integratedPackActive(resourcePackLoadEvent.isIntegratedPackActive());
        GeyserLocale.loadGeyserLocale(session.locale());
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(PlayerAuthInputPacket packet) {
        if (neteaseSession != null && session.isSpawned() && !session.isClosed()) {
            try {
                var pos = packet.getPosition();
                float footY = pos.getY() - 1.62f;
                float dy = packet.getDelta().getY();
                int blockBelow = session.getGeyser().getWorldManager()
                    .getBlockAt(session, (int) Math.floor(pos.getX()), (int) Math.floor(footY - 0.01), (int) Math.floor(pos.getZ()));
                if (blockBelow != 0 && dy <= 0.01f) {
                    packet.getInputData().add(org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData.VERTICAL_COLLISION);
                    session.getPlayerEntity().setLastTickEndVelocity(
                        org.cloudburstmc.math.vector.Vector3f.from(packet.getDelta().getX(), -0.01f, packet.getDelta().getZ()));
                }
            } catch (Exception ignored) {}
        }
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ClientToServerHandshakePacket packet) {
        if (neteaseSession != null && neteaseSession.loginDeferred()) {
            neteaseSession.setLoginDeferred(false);
            PlayStatusPacket ps = new PlayStatusPacket();
            ps.setStatus(PlayStatusPacket.Status.LOGIN_SUCCESS);
            session.sendUpstreamPacket(ps);
            if (this.resourcePackLoadEvent == null) {
                this.resourcePackLoadEvent = new org.geysermc.geyser.event.type.SessionLoadResourcePacksEventImpl(session);
                this.geyser.eventBus().fireEventElseKick(this.resourcePackLoadEvent, session);
            }
            if (session.isClosed()) return PacketSignal.HANDLED;
            session.integratedPackActive(resourcePackLoadEvent.isIntegratedPackActive());
            ResourcePacksInfoPacket rpi = new ResourcePacksInfoPacket();
            rpi.getResourcePackInfos().addAll(this.resourcePackLoadEvent.infoPacketEntries());
            rpi.setVibrantVisualsForceDisabled(!session.isAllowVibrantVisuals());
            rpi.setForcedToAccept(geyser.config().gameplay().forceResourcePacks() || resourcePackLoadEvent.isIntegratedPackActive());
            rpi.setWorldTemplateId(java.util.UUID.randomUUID()); rpi.setWorldTemplateVersion("*");
            session.sendUpstreamPacket(rpi);
            return PacketSignal.HANDLED;
        }
        return super.handle(packet);
    }

    @Override
    public PacketSignal handle(ResourcePackClientResponsePacket packet) {
        if (neteaseSession == null) return super.handle(packet);
        if (session.getUpstream().isClosed() || session.isClosed()) return PacketSignal.HANDLED;
        if (finishedResourcePackSending) return PacketSignal.HANDLED;

        switch (packet.getStatus()) {
            case COMPLETED -> {
                finishedResourcePackSending = true;
                if (geyser.config().java().authType() != AuthType.ONLINE) {
                    session.authenticate(session.getAuthData().name());
                } else if (!couldLoginUserByName(session.getAuthData().name())) {
                    session.connect();
                }
                neteaseSession.setAuthenticated(true);
            }
            case SEND_PACKS -> { if (!packet.getPackIds().isEmpty()) { packsToSend.addAll(packet.getPackIds()); sendPackDataInfo(packsToSend.pop()); } return PacketSignal.HANDLED; }
            case HAVE_ALL_PACKS -> {
                ResourcePackStackPacket sp = new ResourcePackStackPacket();
                sp.setExperimentsPreviouslyToggled(false); sp.setForcedToAccept(false);
                sp.setGameVersion(session.getClientData() != null ? session.getClientData().getGameVersion() : "1.21.40");
                sp.getResourcePacks().addAll(this.resourcePackLoadEvent.orderedPacks()); session.sendUpstreamPacket(sp);
            }
            case REFUSED -> session.disconnect("disconnectionScreen.resourcePack");
            default -> session.disconnect("disconnectionScreen.resourcePack");
        }
        return PacketSignal.HANDLED;
    }

    public PacketSignal handle(NetEaseJsonPacket p)  { return PacketSignal.HANDLED; }
    public PacketSignal handle(PyRpcPacket p)         { return PacketSignal.HANDLED; }
    public PacketSignal handle(StoreBuySuccessPacket p) { return PacketSignal.HANDLED; }
    public PacketSignal handle(ConfirmSkinPacket p)   { return PacketSignal.HANDLED; }

    private static void startEncryptionHandshake(GeyserSession session, PublicKey clientKey) throws Exception {
        KeyPair serverKeyPair = EncryptionUtils.createKeyPair();
        byte[] token = EncryptionUtils.generateRandomToken();

        ServerToClientHandshakePacket handshake = new ServerToClientHandshakePacket();
        handshake.setJwt(EncryptionUtils.createHandshakeJwt(serverKeyPair, token));
        session.sendUpstreamPacketImmediately(handshake);

        SecretKey encryptionKey = EncryptionUtils.getSecretKey(serverKeyPair.getPrivate(), clientKey, token);
        session.getUpstream().getSession().enableEncryption(encryptionKey);
    }

    private Integer getRakVersion() {
        try { return session.getUpstream().getSession().getPeer().getChannel().config().getOption(RakChannelOption.RAK_PROTOCOL_VERSION); }
        catch (Exception e) { return null; }
    }
    private boolean couldLoginUserByName(String name) {
        if (geyser.config().savedUserLogins().contains(name)) {
            String chain = geyser.authChainFor(name);
            if (chain != null) { session.authenticateWithAuthChain(chain); return true; }
        }
        return false;
    }

    public NeteaseSession neteaseSession() { return neteaseSession; }
}
