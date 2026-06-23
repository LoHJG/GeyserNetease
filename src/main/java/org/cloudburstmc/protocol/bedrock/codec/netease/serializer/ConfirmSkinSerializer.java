package org.cloudburstmc.protocol.bedrock.codec.netease.serializer;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketSerializer;
import org.cloudburstmc.protocol.bedrock.packet.ConfirmSkinPacket;

public class ConfirmSkinSerializer implements BedrockPacketSerializer<ConfirmSkinPacket> {
    public static final ConfirmSkinSerializer INSTANCE = new ConfirmSkinSerializer();

    @Override
    public void serialize(ByteBuf buffer, BedrockCodecHelper helper, ConfirmSkinPacket packet) {
        helper.writeArray(buffer, packet.getEntries(), (buf, h, entry) -> {
            buf.writeBoolean(entry.isValid());
            h.writeUuid(buf, entry.getUuid());
            h.writeByteArray(buf, entry.getSkinBytes());
        });

        for (ConfirmSkinPacket.SkinEntry entry : packet.getEntries()) {
            String uidStr = entry.getUidStr();
            helper.writeString(buffer, uidStr != null ? uidStr : "");
        }

        for (ConfirmSkinPacket.SkinEntry entry : packet.getEntries()) {
            String geoStr = entry.getGeoStr();
            helper.writeString(buffer, geoStr != null ? geoStr : "");
        }
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, ConfirmSkinPacket packet) {
        helper.readArray(buffer, packet.getEntries(), (buf, h) -> {
            ConfirmSkinPacket.SkinEntry entry = new ConfirmSkinPacket.SkinEntry();
            entry.setValid(buf.readBoolean());
            entry.setUuid(h.readUuid(buf));
            entry.setSkinBytes(h.readByteArray(buf));
            return entry;
        });

        for (ConfirmSkinPacket.SkinEntry entry : packet.getEntries()) {
            entry.setUidStr(helper.readString(buffer));
        }

        for (ConfirmSkinPacket.SkinEntry entry : packet.getEntries()) {
            entry.setGeoStr(helper.readString(buffer));
        }
    }
}
