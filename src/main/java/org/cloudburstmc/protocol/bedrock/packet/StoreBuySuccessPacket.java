package org.cloudburstmc.protocol.bedrock.packet;

import org.cloudburstmc.protocol.bedrock.annotation.NetEaseOnly;
import org.cloudburstmc.protocol.common.PacketSignal;

@NetEaseOnly
public class StoreBuySuccessPacket implements BedrockPacket {

    @Override
    public PacketSignal handle(BedrockPacketHandler handler) {
        return PacketSignal.UNHANDLED;
    }

    @Override
    public BedrockPacketType getPacketType() {
        return BedrockPacketType.STORE_BUY_SUCCESS;
    }

    @Override
    public StoreBuySuccessPacket clone() {
        try {
            return (StoreBuySuccessPacket) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }
}
