package org.cloudburstmc.protocol.bedrock.codec.netease.serializer;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketSerializer;
import org.cloudburstmc.protocol.bedrock.packet.NetEaseJsonPacket;

public class NetEaseJsonSerializer implements BedrockPacketSerializer<NetEaseJsonPacket> {
    public static final NetEaseJsonSerializer INSTANCE = new NetEaseJsonSerializer();

    @Override
    public void serialize(ByteBuf buffer, BedrockCodecHelper helper, NetEaseJsonPacket packet) {
        helper.writeString(buffer, packet.getJson());
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, NetEaseJsonPacket packet) {
        packet.setJson(helper.readString(buffer));
    }
}
