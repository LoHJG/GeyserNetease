package org.cloudburstmc.protocol.bedrock.codec.netease.serializer;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketSerializer;
import org.cloudburstmc.protocol.bedrock.packet.StoreBuySuccessPacket;

public class StoreBuySuccessSerializer implements BedrockPacketSerializer<StoreBuySuccessPacket> {
    public static final StoreBuySuccessSerializer INSTANCE = new StoreBuySuccessSerializer();

    @Override
    public void serialize(ByteBuf buffer, BedrockCodecHelper helper, StoreBuySuccessPacket packet) {
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, StoreBuySuccessPacket packet) {
    }
}
