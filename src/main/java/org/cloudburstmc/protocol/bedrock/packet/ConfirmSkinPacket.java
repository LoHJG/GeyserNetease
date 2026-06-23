package org.cloudburstmc.protocol.bedrock.packet;

import org.cloudburstmc.protocol.bedrock.annotation.NetEaseOnly;
import org.cloudburstmc.protocol.common.PacketSignal;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NetEaseOnly
public class ConfirmSkinPacket implements BedrockPacket {
    private List<SkinEntry> entries = new ArrayList<>();
    public List<SkinEntry> getEntries() { return entries; }
    public void setEntries(List<SkinEntry> entries) { this.entries = entries; }

    @Override
    public PacketSignal handle(BedrockPacketHandler handler) {
        return PacketSignal.UNHANDLED;
    }

    @Override
    public BedrockPacketType getPacketType() {
        return BedrockPacketType.CONFIRM_SKIN;
    }

    @Override
    public ConfirmSkinPacket clone() {
        try {
            return (ConfirmSkinPacket) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    public static class SkinEntry {
        private boolean valid;
        private UUID uuid;
        private byte[] skinBytes;
        private String uidStr;
        private String geoStr;

        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        public UUID getUuid() { return uuid; }
        public void setUuid(UUID uuid) { this.uuid = uuid; }
        public byte[] getSkinBytes() { return skinBytes; }
        public void setSkinBytes(byte[] skinBytes) { this.skinBytes = skinBytes; }
        public String getUidStr() { return uidStr; }
        public void setUidStr(String uidStr) { this.uidStr = uidStr; }
        public String getGeoStr() { return geoStr; }
        public void setGeoStr(String geoStr) { this.geoStr = geoStr; }
    }
}
