/*
 * Copyright (c) 2026 EiluDick/ZDarkZ
 * Released under the MIT License.
 */

package nc.geyserext.netease.session;

import nc.geyserext.netease.util.BlockHashUtils;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.geysermc.geyser.registry.type.BlockMappings;
import org.geysermc.geyser.registry.type.GeyserBedrockBlock;
import org.geysermc.geyser.session.GeyserSession;

public class NeteaseSession {

    private final GeyserSession session;
    private final BedrockCodec codec;
    private final BedrockCodecHelper helper;
    private boolean loginDeferred;
    private boolean authenticated;
    private long uid;

    private volatile int[] blockRtToHash;
    private volatile boolean[] blockHashValid;

    public NeteaseSession(GeyserSession session, int protocolVersion, BedrockCodec codec) {
        this.session = session;
        this.codec = codec;
        this.helper = codec.createHelper();
        this.uid = Math.abs(System.nanoTime());
    }

    public GeyserSession session() { return session; }
    public BedrockCodec codec() { return codec; }
    public BedrockCodecHelper helper() { return helper; }
    public boolean loginDeferred() { return loginDeferred; }
    public void setLoginDeferred(boolean v) { loginDeferred = v; }
    public boolean authenticated() { return authenticated; }
    public void setAuthenticated(boolean v) { authenticated = v; }
    public long uid() { return uid; }
    public void setUid(long v) { uid = v; }

    public int toBlockNetworkId(int runtimeId) {
        int[] hash = getBlockRtToHash();
        if (runtimeId >= 0 && runtimeId < hash.length && runtimeId < blockHashValid.length && blockHashValid[runtimeId])
            return hash[runtimeId];
        return runtimeId;
    }

    public synchronized int[] getBlockRtToHash() {
        if (blockRtToHash == null) buildHashTable();
        return blockRtToHash;
    }

    public synchronized boolean[] blockHashValid() {
        if (blockHashValid == null) buildHashTable();
        return blockHashValid;
    }

    private void buildHashTable() {
        BlockMappings bm = session.getBlockMappings();
        if (bm == null) { blockRtToHash = new int[0]; blockHashValid = new boolean[0]; return; }
        int max = 0;
        for (int i = 0; bm.getDefinition(i) != null; i++) max = i;
        int[] h = new int[max + 1];
        boolean[] v = new boolean[max + 1];
        for (int i = 0; i <= max; i++) {
            GeyserBedrockBlock b = bm.getDefinition(i);
            if (b != null && b.getState() != null) { h[i] = BlockHashUtils.fnv1a32Nbt(b.getState()); v[i] = true; }
        }
        blockRtToHash = h;
        blockHashValid = v;
    }
}
