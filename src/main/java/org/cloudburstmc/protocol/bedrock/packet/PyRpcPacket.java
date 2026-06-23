package org.cloudburstmc.protocol.bedrock.packet;

import org.cloudburstmc.protocol.bedrock.annotation.NetEaseOnly;
import org.cloudburstmc.protocol.common.PacketSignal;

@NetEaseOnly
public class PyRpcPacket implements BedrockPacket {
    private byte[] data;
    private long msgId;
    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }
    public long getMsgId() { return msgId; }
    public void setMsgId(long msgId) { this.msgId = msgId; }

    @Override
    public PacketSignal handle(BedrockPacketHandler handler) {
        return PacketSignal.UNHANDLED;
    }

    @Override
    public BedrockPacketType getPacketType() {
        return BedrockPacketType.PY_RPC;
    }

    @Override
    public PyRpcPacket clone() {
        try {
            return (PyRpcPacket) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }
}
