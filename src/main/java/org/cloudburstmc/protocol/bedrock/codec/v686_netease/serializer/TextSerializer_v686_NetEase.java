package org.cloudburstmc.protocol.bedrock.codec.v686_netease.serializer;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v685.serializer.TextSerializer_v685;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;

public class TextSerializer_v686_NetEase extends TextSerializer_v685 {
    public static final TextSerializer_v686_NetEase INSTANCE = new TextSerializer_v686_NetEase();

    @Override
    public void serialize(ByteBuf buffer, BedrockCodecHelper helper, TextPacket packet) {
        super.serialize(buffer, helper, packet);

        TextPacket.Type type = packet.getType();
        if (type == TextPacket.Type.CHAT || type == TextPacket.Type.POPUP) {
            helper.writeString(buffer, "");
        }
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, TextPacket packet) {
        super.deserialize(buffer, helper, packet);

        TextPacket.Type type = packet.getType();
        if (type == TextPacket.Type.CHAT || type == TextPacket.Type.POPUP) {
            helper.readString(buffer);
        }
    }
}
