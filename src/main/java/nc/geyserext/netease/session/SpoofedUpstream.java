/*
 * Copyright (c) 2026 EiluDick/ZDarkZ
 * Released under the MIT License.
 */

package nc.geyserext.netease.session;

import nc.geyserext.netease.util.BlockHashUtils;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.geysermc.geyser.network.GameProtocol;
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

    public SpoofedUpstream(UpstreamSession real, BedrockServerSession s,
            int[] rt, boolean[] hv, boolean hashing) {
        super(s);
        this.real = real;
        this.spoofedVersion = GameProtocol.DEFAULT_BEDROCK_PROTOCOL;
        this.rtToHash = rt;
        this.hValid = hv;
        this.hashing = hashing;
    }

    @Override public void disconnect(String r) { real.disconnect(r); }
    @Override public int getProtocolVersion() { return spoofedVersion; }
    @Override public BedrockServerSession getSession() { return real.getSession(); }
    @Override public boolean isClosed() { return real.isClosed(); }
    @Override public void sendPacket(BedrockPacket p) { real.sendPacket(rewrite(p)); }
    @Override public void sendPacketImmediately(BedrockPacket p) { real.sendPacketImmediately(rewrite(p)); }

    private BedrockPacket rewrite(BedrockPacket p) {
        if (!hashing) return p;
        if (p instanceof UpdateBlockPacket ub) ub.setDefinition(wrapDef(ub.getDefinition()));
        else if (p instanceof StartGamePacket sg) rewriteStartGame(sg);
        return p;
    }

    private void rewriteStartGame(StartGamePacket sg) {
        sg.setBlockNetworkIdsHashed(true);
        sg.setBlockRegistryChecksum(0L);
        List<NbtMap> pal = sg.getBlockPalette();
        if (pal == null || pal.isEmpty()) return;
        TreeMap<Integer, NbtMap> sorted = new TreeMap<>();
        int idx = 0;
        for (NbtMap e : pal) {
            if (idx < hValid.length && hValid[idx]) sorted.put(rtToHash[idx], e);
            else if (idx < rtToHash.length) sorted.put(idx, e);
            idx++;
        }
        pal.clear();
        sorted.values().forEach(pal::add);
    }

    private BlockDefinition wrapDef(BlockDefinition def) {
        if (def == null) return null;
        int id = def.getRuntimeId();
        if (id >= 0 && id < hValid.length && hValid[id]) {
            int h = rtToHash[id];
            return new BlockDefinition() { public int getRuntimeId() { return h; } };
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
        return ok;
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
