package org.cloudburstmc.protocol.bedrock.codec.v630_netease;

import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.netease.serializer.ConfirmSkinSerializer;
import org.cloudburstmc.protocol.bedrock.codec.netease.serializer.NetEaseJsonSerializer;
import org.cloudburstmc.protocol.bedrock.codec.netease.serializer.PlayerEnchantOptionsSerializer_v407_NetEase;
import org.cloudburstmc.protocol.bedrock.codec.netease.serializer.PyRpcSerializer;
import org.cloudburstmc.protocol.bedrock.codec.netease.serializer.StoreBuySuccessSerializer;
import org.cloudburstmc.protocol.bedrock.codec.v575.BedrockCodecHelper_v575;
import org.cloudburstmc.protocol.bedrock.codec.v630.Bedrock_v630;
import org.cloudburstmc.protocol.bedrock.codec.v630_netease.serializer.PlayerAuthInputSerializer_v630_NetEase;
import org.cloudburstmc.protocol.bedrock.codec.v630_netease.serializer.TextSerializer_v630_NetEase;
import org.cloudburstmc.protocol.bedrock.data.PacketRecipient;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.packet.ConfirmSkinPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetEaseJsonPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerEnchantOptionsPacket;
import org.cloudburstmc.protocol.bedrock.packet.PyRpcPacket;
import org.cloudburstmc.protocol.bedrock.packet.StoreBuySuccessPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.protocol.common.util.TypeMap;

public class Bedrock_v630_NetEase extends Bedrock_v630 {
    protected static final TypeMap<ContainerSlotType> CONTAINER_SLOT_TYPES = Bedrock_v630.CONTAINER_SLOT_TYPES
            .toBuilder()
            .shift(17, 1)
            .build();

    public static final BedrockCodec CODEC = Bedrock_v630.CODEC.toBuilder()
            .raknetProtocolVersion(8)
            .helper(() -> new BedrockCodecHelper_v575(ENTITY_DATA, GAME_RULE_TYPES, ITEM_STACK_REQUEST_TYPES, CONTAINER_SLOT_TYPES, PLAYER_ABILITIES, TEXT_PROCESSING_ORIGINS))
            .updateSerializer(PlayerAuthInputPacket.class, PlayerAuthInputSerializer_v630_NetEase.INSTANCE)
            .updateSerializer(TextPacket.class, TextSerializer_v630_NetEase.INSTANCE)
            .updateSerializer(PlayerEnchantOptionsPacket.class, PlayerEnchantOptionsSerializer_v407_NetEase.INSTANCE)
            .registerPacket(PyRpcPacket::new, PyRpcSerializer.INSTANCE, 200, PacketRecipient.BOTH)
            .registerPacket(StoreBuySuccessPacket::new, StoreBuySuccessSerializer.INSTANCE, 202, PacketRecipient.BOTH)
            .registerPacket(NetEaseJsonPacket::new, NetEaseJsonSerializer.INSTANCE, 203, PacketRecipient.BOTH)
            .registerPacket(ConfirmSkinPacket::new, ConfirmSkinSerializer.INSTANCE, 228, PacketRecipient.CLIENT)
            .build();
}
