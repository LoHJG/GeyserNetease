/*
 * Copyright (c) 2026 EiluDick/ZDarkZ
 * Released under the MIT License.
 */

package nc.geyserext.netease.util;

import org.cloudburstmc.nbt.NbtMap; import org.cloudburstmc.nbt.NbtUtils;
import java.io.*;

public final class BlockHashUtils {
    private static final int FNV1_32_INIT = 0x811c9dc5, FNV1_PRIME_32 = 0x01000193;
    private BlockHashUtils() {}

    @SuppressWarnings("unchecked")
    public static int fnv1a32Nbt(NbtMap tag) {
        try (var baos = new ByteArrayOutputStream(); var w = NbtUtils.createWriterLE(baos)) {
            String name = tag.getString("name", "minecraft:unknown");
            NbtMap states = tag.getCompound("states");

            java.util.TreeMap<String, Object> sorted = new java.util.TreeMap<>();
            if (states != null) sorted.putAll(states);

            NbtMap stripped = NbtMap.builder()
                    .putString("name", name)
                    .putCompound("states", NbtMap.fromMap(sorted))
                    .build();
            w.writeTag(stripped);
            return fnv1a32(baos.toByteArray());
        } catch (IOException e) { throw new RuntimeException(e); }
    }
    public static int fnv1a32(byte[] data) {
        int h = FNV1_32_INIT; for (byte b : data) { h ^= (b & 0xff); h *= FNV1_PRIME_32; } return h;
    }
}
