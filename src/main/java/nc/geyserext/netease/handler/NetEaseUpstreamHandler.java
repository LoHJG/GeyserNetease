/*
 * Copyright (c) 2026 EiluDick/ZDarkZ
 * Released under the MIT License.
 */

package nc.geyserext.netease.handler;

import nc.geyserext.netease.NeteaseExtension;
import nc.geyserext.netease.session.NeteaseSession;
import nc.geyserext.netease.util.protocol.NeteaseCodecRegistry;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryActionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventorySource;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.*;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.bedrock.util.*;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.event.bedrock.SessionInitializeEvent;
import org.geysermc.geyser.event.type.SessionLoadResourcePacksEventImpl;
import org.geysermc.geyser.network.bedrock.GameProtocol;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.session.auth.*;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;

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
        if (codec == null) {
            if (NeteaseExtension.LOG != null) {
                NeteaseExtension.LOG.info("[GeyserNetease] No NetEase codec for reported protocol v"
                    + packet.getProtocolVersion() + "; falling back to the vanilla pipeline.");
            }
            return super.handle(packet);
        }

        session.getUpstream().getSession().setCodec(codec);
        neteaseSession = new NeteaseSession(session, packet.getProtocolVersion(), codec);
        if (NeteaseExtension.LOG != null) {
            NeteaseExtension.LOG.info("[GeyserNetease] NetEase client: reported protocol v" + packet.getProtocolVersion()
                + ", using codec " + codec.getMinecraftVersion() + " (codec protocol v" + codec.getProtocolVersion()
                + ", raknet v" + (rakVer != null ? rakVer : -1) + ")");
        }

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
                Long iat = (Long) result.rawIdentityClaims().get("iat");

                long neteaseUid = 0;
                Object red = result.rawIdentityClaims().get("extraData");
                if (red instanceof Map) {
                    Object u = ((Map<?, ?>) red).get("uid");
                    if (u instanceof Number) neteaseUid = ((Number) u).longValue();
                }
                if (neteaseUid == 0) neteaseUid = Math.abs(name.hashCode());
                neteaseSession.setUid(neteaseUid);

                String rawId = extra.identity != null ? extra.identity.toString() : null;
                UUID javaUuid = toStableUUID(rawId, neteaseUid);

                String xuid = String.valueOf(neteaseUid);

                session.setAuthData(new AuthData(name, javaUuid, xuid, iat != null ? iat : -1, extra.minecraftId != null ? extra.minecraftId : ""));
                session.setCertChainData(chain.getChain());

                if (packet.getClientJwt() != null && !packet.getClientJwt().isEmpty()) {
                    try {
                        byte[] cd = EncryptionUtils.verifyClientData(packet.getClientJwt(), result.identityClaims().parsedIdentityPublicKey());
                        if (cd != null) { BedrockClientData bcd = GeyserImpl.GSON.fromJson(new String(cd), BedrockClientData.class); bcd.setOriginalString(packet.getClientJwt()); session.setClientData(bcd); }
                    } catch (Exception ignored) {}
                }
                if (session.getClientData() == null)
                    session.setClientData(buildFakeClientData(name));

                startEncryptionHandshake(session, result.identityClaims().parsedIdentityPublicKey());
                if (session.isClosed()) { session.forciblyCloseUpstream(); return PacketSignal.HANDLED; }
                neteaseSession.setLoginDeferred(true);
            }
        } catch (Exception e) { session.disconnect("disconnectionScreen.internalError.cantConnect"); NeteaseExtension.LOG.error("NetEase auth failed", e); return PacketSignal.HANDLED; }

        int spoofedVersion = GameProtocol.DEFAULT_BEDROCK_PROTOCOL;
        var shared = BlockRegistries.BLOCKS.forVersion(spoofedVersion);
        var blockMappings = shared;
        try { blockMappings = nc.geyserext.netease.session.SpoofedUpstream.cloneBlockMappings(shared); }
        catch (Throwable e) { NeteaseExtension.LOG.error("BlockMappings clone failed, using shared: " + e.getMessage()); }
        session.setBlockMappings(blockMappings);
        session.setItemMappings(Registries.ITEMS.forVersion(spoofedVersion));

        // NetEase clients resolve blocks by a hash of the block state, so the runtime IDs are
        // rewritten below. Both switches exist so the behaviour can be bisected on a live client.
        boolean hashBlockIds = NeteaseExtension.CONFIG == null || NeteaseExtension.CONFIG.blockNetworkIdsHashed();

        boolean patched = false;
        if (hashBlockIds) {
            try { patched = nc.geyserext.netease.session.SpoofedUpstream.patchAll(blockMappings); }
            catch (Throwable e) { NeteaseExtension.LOG.error("BlockMappings Unsafe: " + e.getMessage()); }
        }
        if (NeteaseExtension.LOG != null) {
            NeteaseExtension.LOG.info("[GeyserNetease] Block network IDs: "
                + (hashBlockIds ? "hashed from block state (patched=" + patched + ")" : "Geyser index IDs (hashing disabled)")
                + ", registry version v" + spoofedVersion);
            if (!hashBlockIds) {
                // Index IDs only make sense for a client on Geyser's own palette version; for an
                // older client every block resolves to the wrong state.
                NeteaseExtension.LOG.warning("[GeyserNetease] block-network-ids-hashed is false: block IDs are "
                    + "Geyser palette indices and will NOT match an older NetEase client. Expect wrong blocks; "
                    + "this is only useful for finding what crashes the client.");
            }
            logKnownBlockIds(blockMappings);
        }

        int maxId = 0;
        for (int i = 0; blockMappings.getDefinition(i) != null; i++) maxId = i;
        int[] rtToHash = new int[maxId + 1];
        boolean[] hValid = new boolean[maxId + 1];
        java.util.Map<Integer, org.geysermc.geyser.registry.type.GeyserBedrockBlock> byHash = new java.util.HashMap<>();
        for (int i = 0; i <= maxId; i++) {
            var block = blockMappings.getDefinition(i);
            if (block != null) {
                var gb = (org.geysermc.geyser.registry.type.GeyserBedrockBlock) block;
                if (gb.getState() != null) {
                    int hash = nc.geyserext.netease.util.BlockHashUtils.fnv1a32Nbt(gb.getState());
                    rtToHash[i] = hash;
                    hValid[i] = true;
                    byHash.put(hash, gb);
                }
            }
        }

        // The codec refuses to encode a block definition its registry does not know, and Geyser's
        // registry looks blocks up by array index, which a hash is not. Hand the codec a registry
        // that resolves hashes, so hashed IDs survive the round trip in both directions.
        nc.geyserext.netease.util.HashAwareBlockRegistry blockRegistry =
            new nc.geyserext.netease.util.HashAwareBlockRegistry(byHash, blockMappings);
        try {
            session.getUpstream().getCodecHelper().setBlockDefinitions(blockRegistry);
        } catch (Throwable e) {
            NeteaseExtension.LOG.error("Installing hash-aware block registry failed: " + e.getMessage());
        }

        try {
            var authData = session.getAuthData();
            var spoofed = new nc.geyserext.netease.session.SpoofedUpstream(
                session.getUpstream(), session.getUpstream().getSession(), rtToHash, hValid, patched, neteaseSession.uid(),
                authData != null ? authData.uuid() : null, blockRegistry);
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

    public PacketSignal handle(InventoryTransactionPacket packet) {
        if (neteaseSession != null && packet.getTransactionType() == InventoryTransactionType.NORMAL
                && packet.getActions().size() == 2) {
            List<InventoryActionData> actions = packet.getActions();
            InventoryActionData action0 = actions.get(0);
            InventoryActionData action1 = actions.get(1);
            InventoryActionData worldAction = action0.getSource().getType() == InventorySource.Type.WORLD_INTERACTION ? action0 : action1;
            InventoryActionData containerAction = (worldAction == action0) ? action1 : action0;
            if (worldAction.getSource().getType() != InventorySource.Type.WORLD_INTERACTION
                    || containerAction.getSource().getType() != InventorySource.Type.CONTAINER)
                return defaultHandler(packet);

            boolean dropAll = worldAction.getToItem().getCount() > 1;
            var inventory = session.getPlayerInventory();
            if (inventory.getItemInHand().isEmpty()) return PacketSignal.HANDLED;

            session.sendDownstreamGamePacket(new ServerboundPlayerActionPacket(
                dropAll ? PlayerAction.DROP_ITEM_STACK : PlayerAction.DROP_ITEM, Vector3i.ZERO, Direction.DOWN, 0));

            if (dropAll) inventory.setItemInHand(org.geysermc.geyser.inventory.GeyserItemStack.EMPTY);
            else inventory.getItemInHand().sub(1);

            return PacketSignal.HANDLED;
        }
        return defaultHandler(packet);
    }

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

    /**
     * Logs the runtime ID the extension will actually put on the wire for a few blocks whose
     * Bedrock network_id is fixed and known, so a live session can be checked against it:
     * minecraft:stone must be -2144268767 and minecraft:air -604749536. Different values mean the
     * client is being sent IDs it cannot resolve, which shows up as wrong blocks.
     */
    private static void logKnownBlockIds(org.geysermc.geyser.registry.type.BlockMappings mappings) {
        String stone = null;
        String air = null;
        for (int i = 0; ; i++) {
            var block = mappings.getDefinition(i);
            if (block == null) break;
            var state = block.getState();
            if (state == null) continue;
            String name = state.getString("name", "");
            if (stone == null && "minecraft:stone".equals(name)) stone = i + "->" + block.getRuntimeId();
            else if (air == null && "minecraft:air".equals(name)) air = i + "->" + block.getRuntimeId();
            if (stone != null && air != null) break;
        }
        NeteaseExtension.LOG.info("[GeyserNetease] Sample block IDs (index->id sent): stone=" + stone
            + " air=" + air + " | expected stone=-2144268767 air=-604749536");
    }

    private static BedrockClientData buildFakeClientData(String name) {
        return GeyserImpl.GSON.fromJson(
            "{\"GameVersion\":\"1.21.40\",\"LanguageCode\":\"en_us\",\"DeviceOS\":1," +
            "\"DeviceModel\":\"NetEase\",\"ThirdPartyName\":\"" + name + "\"," +
            "\"SkinId\":\"\",\"SkinData\":\"\",\"SkinImageWidth\":64,\"SkinImageHeight\":64," +
            "\"CapeId\":\"\",\"CapeData\":\"\",\"CapeImageWidth\":64,\"CapeImageHeight\":32," +
            "\"SkinGeometryData\":\"\",\"SkinResourcePatch\":\"\"," +
            "\"SkinGeometryDataEngineVersion\":\"\",\"PlayFabId\":\"\"," +
            "\"PersonaSkin\":false,\"PremiumSkin\":false,\"IsEditorMode\":false," +
            "\"SkinColor\":\"#0\",\"ArmSize\":\"wide\",\"PersonaPieces\":[],\"PieceTintColors\":[]}",
            BedrockClientData.class);
    }

    private static UUID toStableUUID(String rawIdentity, long neteaseUid) {
        if (rawIdentity != null && !rawIdentity.isEmpty()) {
            String hex = rawIdentity.replace("-", "");
            if (hex.length() == 32) {
                try {
                    return UUID.fromString(
                        hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-"
                        + hex.substring(16, 20) + "-" + hex.substring(20));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        String seed = "netease:player:" + neteaseUid;
        return UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
