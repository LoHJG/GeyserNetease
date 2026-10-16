/*
 * Copyright (c) 2026 EiluDick/ZDarkZ
 * Released under the MIT License.
 */

package nc.geyserext.netease.session;

import nc.geyserext.netease.NeteaseExtension;
import nc.geyserext.netease.util.BlockHashUtils;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.geysermc.geyser.network.bedrock.GameProtocol;
import org.geysermc.geyser.registry.type.BlockMappings;
import org.geysermc.geyser.registry.type.GeyserBedrockBlock;
import org.geysermc.geyser.session.UpstreamSession;
import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.util.*;

public class SpoofedUpstream extends UpstreamSession {

    private final UpstreamSession real;
    private final int spoofedVersion;
    private final int[] rtToHash;
    private final boolean[] hValid;
    private final boolean hashing;
    private final long neteaseUid;
    private final UUID playerUuid;
    /** Hash-aware block registry the codec must use while hashed IDs are on the wire. */
    private final org.cloudburstmc.protocol.common.DefinitionRegistry<org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition> blockRegistry;
    private boolean skinConfirmed;

    public SpoofedUpstream(UpstreamSession real, BedrockServerSession s,
            int[] rt, boolean[] hv, boolean hashing, long neteaseUid) {
        this(real, s, rt, hv, hashing, neteaseUid, null, null);
    }

    public SpoofedUpstream(UpstreamSession real, BedrockServerSession s,
            int[] rt, boolean[] hv, boolean hashing, long neteaseUid, UUID playerUuid) {
        this(real, s, rt, hv, hashing, neteaseUid, playerUuid, null);
    }

    public SpoofedUpstream(UpstreamSession real, BedrockServerSession s,
            int[] rt, boolean[] hv, boolean hashing, long neteaseUid, UUID playerUuid,
            org.cloudburstmc.protocol.common.DefinitionRegistry<org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition> blockRegistry) {
        super(s);
        this.real = real;
        this.spoofedVersion = GameProtocol.DEFAULT_BEDROCK_PROTOCOL;
        this.rtToHash = rt;
        this.hValid = hv;
        this.hashing = hashing;
        this.neteaseUid = neteaseUid;
        this.blockRegistry = blockRegistry;
        // The skin confirmation packet always carries a UUID; derive a stable one when the
        // caller has none, mirroring the login fallback so both agree.
        this.playerUuid = playerUuid != null
            ? playerUuid
            : UUID.nameUUIDFromBytes(("netease:player:" + neteaseUid).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override public void disconnect(String r) { real.disconnect(r); }
    @Override public int getProtocolVersion() { return spoofedVersion; }
    @Override public BedrockServerSession getSession() { return real.getSession(); }
    @Override public boolean isClosed() { return real.isClosed(); }
    @Override public void sendPacket(BedrockPacket p) { ensureBlockRegistry(); real.sendPacket(rewrite(p)); hook(p, false); }
    @Override public void sendPacketImmediately(BedrockPacket p) { ensureBlockRegistry(); real.sendPacketImmediately(rewrite(p)); hook(p, true); }

    /**
     * Geyser installs its own block registry while starting the game, after this session's login was
     * handled, so the hash-aware registry has to be re-asserted. Every outbound packet passes through
     * here before the codec encodes it, which is exactly the moment it has to be in place.
     */
    private void ensureBlockRegistry() {
        if (blockRegistry == null) {
            return;
        }
        try {
            var helper = real.getCodecHelper();
            if (helper != null && helper.getBlockDefinitions() != blockRegistry) {
                helper.setBlockDefinitions(blockRegistry);
            }
        } catch (Throwable ignored) {
            // Never let registry bookkeeping break packet flow.
        }
    }

    private BedrockPacket rewrite(BedrockPacket p) {
        if (p instanceof StartGamePacket sg) {
            // Independent of the hashing switch: this only removes newer registry content.
            if (NeteaseExtension.CONFIG != null && NeteaseExtension.CONFIG.clearStartGameBlockProperties()) {
                int size = sg.getBlockProperties().size();
                sg.getBlockProperties().clear();
                log("StartGame block properties cleared (" + size + " entries).");
            }
            if (hashing) rewriteStartGame(sg);
            return p;
        }
        if (!hashing) return p;
        if (p instanceof UpdateBlockPacket ub) ub.setDefinition(wrapDef(ub.getDefinition()));
        else if (p instanceof CreativeContentPacket cc) rewriteCreativeContents(cc);
        return p;
    }

    /**
     * Translates the block IDs inside the creative item list from palette indices to hashed
     * network IDs.
     *
     * <p>Geyser builds that list once at startup from its own mappings, before any NetEase session
     * exists, so it never sees the per-session patch applied to {@code blockMappings}. Every block
     * item in it therefore still carries an index while the client, told block IDs are hashed,
     * reads it as a hash and cannot resolve the block — which kills the client when it opens the
     * creative inventory.</p>
     */
    private void rewriteCreativeContents(CreativeContentPacket cc) {
        boolean empty = NeteaseExtension.CONFIG != null && NeteaseExtension.CONFIG.emptyCreativeContent();
        rewriteCreativeContents(cc, rtToHash, hValid, empty);
    }

    /** Testable core of {@link #rewriteCreativeContents(CreativeContentPacket)}. */
    public static void rewriteCreativeContents(CreativeContentPacket cc, int[] rtToHash, boolean[] hValid, boolean empty) {
        var contents = cc.getContents();
        // Always report, even when there is nothing to do: whether this packet was seen at all is
        // the first thing to establish when the creative inventory still fails.
        if (contents == null || contents.isEmpty()) {
            log("CreativeContent: seen with no entries; nothing to rewrite.");
            return;
        }

        int total = contents.size();
        if (empty) {
            contents.clear();
            log("CreativeContent emptied (" + total + " entries) as configured.");
            return;
        }

        int rewritten = 0;
        int blockItems = 0;
        for (int i = 0; i < contents.size(); i++) {
            var entry = contents.get(i);
            var item = entry.getItem();
            if (item == null || item.getBlockDefinition() == null) continue;
            blockItems++;
            var hashed = wrapDef(item.getBlockDefinition(), rtToHash, hValid);
            if (hashed == item.getBlockDefinition()) continue;
            contents.set(i, new org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData(
                item.toBuilder().blockDefinition(hashed).build(), entry.getNetId(), entry.getGroupId()));
            rewritten++;
        }
        log("CreativeContent: " + total + " entries, " + blockItems + " block items, rewrote " + rewritten
            + " block IDs from palette index to hashed network ID.");
    }

    private void hook(BedrockPacket p, boolean immediate) {
        if (!hashing) return;
        if (p instanceof StartGamePacket && !skinConfirmed) {
            skinConfirmed = true;
            // No skin data exists this early, and an empty confirmation makes NetEase clients drop
            // the connection. Only send it when explicitly asked to.
            if (NeteaseExtension.CONFIG != null && NeteaseExtension.CONFIG.confirmSkinAtStartGame()) {
                ConfirmSkinPacket csp = new ConfirmSkinPacket();
                ConfirmSkinPacket.SkinEntry e = new ConfirmSkinPacket.SkinEntry();
                e.setValid(true);
                e.setUuid(playerUuid);
                e.setUidStr(String.valueOf(neteaseUid));
                csp.getEntries().add(e);
                if (immediate) real.sendPacketImmediately(csp); else real.sendPacket(csp);
            }
        } else if (p instanceof PlayerSkinPacket ps && ps.getSkin() != null) {
            sendSkinCsp(ps.getUuid(), ps.getSkin(), immediate);
        } else if (p instanceof PlayerListPacket pl && pl.getAction() == PlayerListPacket.Action.ADD) {
            for (PlayerListPacket.Entry entry : pl.getEntries()) {
                if (entry.getSkin() != null && entry.getSkin().getSkinData().getImage().length > 0) {
                    sendSkinCsp(entry.getUuid(), entry.getSkin(), immediate);
                }
            }
        }
    }

    private void sendSkinCsp(UUID uuid, SerializedSkin skin, boolean immediate) {
        ConfirmSkinPacket csp = new ConfirmSkinPacket();
        ConfirmSkinPacket.SkinEntry e = new ConfirmSkinPacket.SkinEntry();
        e.setValid(true);
        e.setUuid(uuid);
        e.setSkinBytes(skin.getSkinData().getImage());
        e.setGeoStr(skin.getGeometryData());
        e.setUidStr(String.valueOf(Math.abs(uuid.toString().replace("-", "").hashCode())));
        csp.getEntries().add(e);
        if (immediate) real.sendPacketImmediately(csp); else real.sendPacket(csp);
    }

    private void rewriteStartGame(StartGamePacket sg) {
        sg.setBlockNetworkIdsHashed(true);
        sg.setBlockRegistryChecksum(0L);
        List<NbtMap> pal = sg.getBlockPalette();
        if (pal == null || pal.isEmpty()) {
            log("StartGame block palette is empty (Geyser 2.12 sends none); block IDs are hashed separately.");
            return;
        }
        TreeMap<Integer, NbtMap> sorted = new TreeMap<>();
        int idx = 0;
        int hashed = 0;
        for (NbtMap e : pal) {
            if (idx < hValid.length && hValid[idx]) { sorted.put(rtToHash[idx], e); hashed++; }
            else if (idx < rtToHash.length) sorted.put(idx, e);
            idx++;
        }
        pal.clear();
        sorted.values().forEach(pal::add);
        log("StartGame block palette rewritten: " + hashed + "/" + idx + " entries hashed (table=" + hValid.length
            + "). Publish it with blockNetworkIdsHashed=true.");
    }

    private static void log(String message) {
        if (NeteaseExtension.LOG != null) {
            NeteaseExtension.LOG.info("[GeyserNetease] " + message);
        }
    }

    private BlockDefinition wrapDef(BlockDefinition def) {
        return wrapDef(def, rtToHash, hValid);
    }

    /** Maps a palette-index block definition to its hashed network ID; anything else is returned as is. */
    static BlockDefinition wrapDef(BlockDefinition def, int[] rtToHash, boolean[] hValid) {
        if (def == null) return null;
        int id = def.getRuntimeId();
        if (id >= 0 && id < hValid.length && id < rtToHash.length && hValid[id]) {
            int h = rtToHash[id];
            return new BlockDefinition() {
                @Override public int getRuntimeId() { return h; }
                @Override public String toString() { return "NetEaseHashedBlock{" + h + "}"; }
            };
        }
        return def;
    }

    private static final Unsafe U;
    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            U = (Unsafe) f.get(null);
        } catch (Throwable e) { throw new ExceptionInInitializerError(e); }
    }

    public static boolean patchAll(BlockMappings orig) throws Throwable {
        boolean ok = patchOne(orig, "javaToBedrockBlocks");
        ok |= patchOne(orig, "bedrockRuntimeMap");
        ok |= patchOne(orig, "javaToVanillaBedrockBlocks");
        patchSingletonBlockDef(orig, "bedrockAir");
        patchSingletonBlockDef(orig, "bedrockWater");
        patchSingletonBlockDef(orig, "bedrockMovingBlock");
        return ok;
    }

    private static void patchSingletonBlockDef(BlockMappings orig, String fieldName) {
        try {
            Field f = BlockMappings.class.getDeclaredField(fieldName);
            long offset = U.objectFieldOffset(f);
            Object old = U.getObject(orig, offset);
            if (old instanceof GeyserBedrockBlock gb && gb.getState() != null) {
                U.putObject(orig, offset, new GeyserBedrockBlock(BlockHashUtils.fnv1a32Nbt(gb.getState()), gb.getState()));
            }
        } catch (Throwable ignored) {}
    }

    private static boolean patchOne(BlockMappings orig, String fieldName) {
        try {
            Field f = BlockMappings.class.getDeclaredField(fieldName);
            long offset = U.objectFieldOffset(f);
            GeyserBedrockBlock[] old = (GeyserBedrockBlock[]) U.getObject(orig, offset);
            if (old == null) return false;
            GeyserBedrockBlock[] rep = new GeyserBedrockBlock[old.length];
            for (int i = 0; i < old.length; i++) {
                GeyserBedrockBlock b = old[i];
                rep[i] = (b != null && b.getState() != null)
                    ? new GeyserBedrockBlock(BlockHashUtils.fnv1a32Nbt(b.getState()), b.getState())
                    : b;
            }
            U.putObject(orig, offset, rep);
            return true;
        } catch (Throwable e) { return false; }
    }

    public static BlockMappings cloneBlockMappings(BlockMappings orig) throws Throwable {
        BlockMappings copy = (BlockMappings) U.allocateInstance(BlockMappings.class);
        for (Field f : BlockMappings.class.getDeclaredFields()) {
            long off = U.objectFieldOffset(f);
            U.putObject(copy, off, U.getObject(orig, off));
        }
        for (String name : new String[]{"javaToBedrockBlocks", "bedrockRuntimeMap", "javaToVanillaBedrockBlocks"}) {
            Field f = BlockMappings.class.getDeclaredField(name);
            long off = U.objectFieldOffset(f);
            Object[] arr = (Object[]) U.getObject(orig, off);
            if (arr != null) U.putObject(copy, off, arr.clone());
        }
        return copy;
    }
}
