/*
 * Copyright (c) 2026 EiluDick/ZDarkZ
 * Released under the MIT License.
 */

package nc.geyserext.netease.util.protocol;

import org.cloudburstmc.protocol.bedrock.codec.*;
import org.cloudburstmc.protocol.bedrock.codec.v630_netease.Bedrock_v630_NetEase;
import org.cloudburstmc.protocol.bedrock.codec.v686_netease.Bedrock_v686_NetEase;
import org.cloudburstmc.protocol.bedrock.codec.v766_netease.Bedrock_v766_NetEase;
import org.cloudburstmc.protocol.bedrock.codec.v819_netease.Bedrock_v819_NetEase;
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NeteaseCodecRegistry {

    private static final Map<Integer, BedrockCodec> CODECS = new ConcurrentHashMap<>();

    static {
        register(Bedrock_v630_NetEase.CODEC);
        register(Bedrock_v686_NetEase.CODEC);
        register(Bedrock_v766_NetEase.CODEC);
        register(Bedrock_v819_NetEase.CODEC);
    }

    private static void register(BedrockCodec c) {
        BedrockCodecHelper h = c.createHelper();
        h.setEncodingSettings(EncodingSettings.builder()
            .maxListSize(Integer.MAX_VALUE).maxByteArraySize(Integer.MAX_VALUE)
            .maxNetworkNBTSize(Integer.MAX_VALUE).maxItemNBTSize(Integer.MAX_VALUE)
            .maxStringLength(Integer.MAX_VALUE).build());
        CODECS.put(c.getProtocolVersion(), c.toBuilder().helper(() -> h).build());
    }

    public static BedrockCodec getCodec(int version) { return CODECS.get(version); }
}
