package org.cloudburstmc.protocol.bedrock.codec.netease.serializer;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketSerializer;
import org.cloudburstmc.protocol.bedrock.packet.PyRpcPacket;

public class PyRpcSerializer implements BedrockPacketSerializer<PyRpcPacket> {
    public static final PyRpcSerializer INSTANCE = new PyRpcSerializer();

    @Override
    public void serialize(ByteBuf buffer, BedrockCodecHelper helper, PyRpcPacket packet) {
        helper.writeByteArray(buffer, packet.getData());
        buffer.writeIntLE((int) packet.getMsgId());
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, PyRpcPacket packet) {
        packet.setData(helper.readByteArray(buffer));
        packet.setMsgId(buffer.readUnsignedIntLE());
    }
}
