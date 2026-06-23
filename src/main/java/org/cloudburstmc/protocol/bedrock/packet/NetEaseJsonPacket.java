package org.cloudburstmc.protocol.bedrock.packet;

import org.cloudburstmc.protocol.bedrock.annotation.NetEaseOnly;
import org.cloudburstmc.protocol.common.PacketSignal;

@NetEaseOnly
public class NetEaseJsonPacket implements BedrockPacket {
    private String json;
    public String getJson() { return json; }
    public void setJson(String json) { this.json = json; }

    @Override
    public PacketSignal handle(BedrockPacketHandler handler) {
        return PacketSignal.UNHANDLED;
    }

    @Override
    public BedrockPacketType getPacketType() {
        return BedrockPacketType.NET_EASE_JSON;
    }

    @Override
    public NetEaseJsonPacket clone() {
        try {
            return (NetEaseJsonPacket) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }
}
