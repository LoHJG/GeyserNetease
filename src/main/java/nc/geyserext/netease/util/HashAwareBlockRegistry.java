/*
 * Copyright (c) 2026 EiluDick/ZDarkZ
 * Released under the MIT License.
 */

package nc.geyserext.netease.util;

import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.geysermc.geyser.registry.type.BlockMappings;
import org.geysermc.geyser.registry.type.GeyserBedrockBlock;

import java.util.Map;

/**
 * Definition registry that resolves a NetEase block by its hashed network ID.
 *
 * <p>NetEase clients look blocks up by a hash of the block state, so the extension rewrites every
 * runtime ID to that hash. Geyser's own {@link BlockMappings} then breaks, because it stores blocks
 * in an array indexed by the <em>original</em> runtime ID:</p>
 *
 * <pre>
 * public boolean isRegistered(BlockDefinition block) {
 *     return getDefinition(block.getRuntimeId()) == block;   // hash is used as an array index
 * }
 * </pre>
 *
 * <p>A hash like {@code -2108756090} is out of bounds, so {@code getDefinition} returns null, the
 * codec decides the block is unregistered and aborts the session while serializing entity data
 * ("Definition is not registered"). The codec only consults
 * {@code BedrockCodecHelper#getBlockDefinitions()}, which is an interface, so handing it this
 * registry restores the invariant: hashed IDs resolve in both directions while the wire keeps
 * carrying the hash.</p>
 */
public final class HashAwareBlockRegistry implements DefinitionRegistry<BlockDefinition> {
    private final Map<Integer, GeyserBedrockBlock> byHash;
    /** Keeps ordinary index lookups working for any path that still uses them. */
    private final BlockMappings mappings;

    public HashAwareBlockRegistry(Map<Integer, GeyserBedrockBlock> byHash, BlockMappings mappings) {
        this.byHash = byHash;
        this.mappings = mappings;
    }

    @Override
    public BlockDefinition getDefinition(int id) {
        GeyserBedrockBlock hashed = byHash.get(id);
        if (hashed != null) {
            return hashed;
        }
        return mappings != null ? mappings.getDefinition(id) : null;
    }

    @Override
    public boolean isRegistered(BlockDefinition definition) {
        if (definition == null) {
            return false;
        }
        // Any definition carrying a known hash is valid on the wire, regardless of which patched
        // array slot produced it: the same state appears as several distinct objects.
        if (byHash.containsKey(definition.getRuntimeId())) {
            return true;
        }
        return mappings != null && mappings.getDefinition(definition.getRuntimeId()) == definition;
    }
}
