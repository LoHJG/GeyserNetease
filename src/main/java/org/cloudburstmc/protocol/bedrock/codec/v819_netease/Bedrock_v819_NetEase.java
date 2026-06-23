package org.cloudburstmc.protocol.bedrock.codec.v819_netease;

import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.netease.serializer.ConfirmSkinSerializer;
import org.cloudburstmc.protocol.bedrock.codec.netease.serializer.NetEaseJsonSerializer;
import org.cloudburstmc.protocol.bedrock.codec.netease.serializer.PlayerEnchantOptionsSerializer_v407_NetEase;
import org.cloudburstmc.protocol.bedrock.codec.netease.serializer.PyRpcSerializer;
import org.cloudburstmc.protocol.bedrock.codec.netease.serializer.StoreBuySuccessSerializer;
import org.cloudburstmc.protocol.bedrock.codec.v686_netease.serializer.TextSerializer_v686_NetEase;
import org.cloudburstmc.protocol.bedrock.codec.v712.Bedrock_v712;
import org.cloudburstmc.protocol.bedrock.codec.v766_netease.serializer.PlayerAuthInputSerializer_v766_NetEase;
import org.cloudburstmc.protocol.bedrock.codec.v819.Bedrock_v819;
import org.cloudburstmc.protocol.bedrock.codec.v819_netease.serializer.BiomeDefinitionListSerializer_v819_NetEase;
import org.cloudburstmc.protocol.bedrock.data.PacketRecipient;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.packet.BiomeDefinitionListPacket;
import org.cloudburstmc.protocol.bedrock.packet.ConfirmSkinPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetEaseJsonPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerEnchantOptionsPacket;
import org.cloudburstmc.protocol.bedrock.packet.PyRpcPacket;
import org.cloudburstmc.protocol.bedrock.packet.StoreBuySuccessPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.protocol.common.util.TypeMap;

public class Bedrock_v819_NetEase extends Bedrock_v819 {
    protected static final TypeMap<ContainerSlotType> CONTAINER_SLOT_TYPES = Bedrock_v712.CONTAINER_SLOT_TYPES
            .toBuilder()
            .shift(17, 1)
            .insert(17, ContainerSlotType.RECIPE_BOOK)
            .build();

    public static final BedrockCodec CODEC = Bedrock_v819.CODEC.toBuilder()
            .raknetProtocolVersion(8)
            .helper(() -> new BedrockCodecHelper_v819_NetEase(ENTITY_DATA, GAME_RULE_TYPES, ITEM_STACK_REQUEST_TYPES, CONTAINER_SLOT_TYPES, PLAYER_ABILITIES, TEXT_PROCESSING_ORIGINS))
            .updateSerializer(PlayerAuthInputPacket.class, PlayerAuthInputSerializer_v766_NetEase.INSTANCE)
            .updateSerializer(TextPacket.class, TextSerializer_v686_NetEase.INSTANCE)
            .updateSerializer(PlayerEnchantOptionsPacket.class, PlayerEnchantOptionsSerializer_v407_NetEase.INSTANCE)
            .updateSerializer(BiomeDefinitionListPacket.class, BiomeDefinitionListSerializer_v819_NetEase.INSTANCE)
            .registerPacket(PyRpcPacket::new, PyRpcSerializer.INSTANCE, 200, PacketRecipient.BOTH)
            .registerPacket(StoreBuySuccessPacket::new, StoreBuySuccessSerializer.INSTANCE, 202, PacketRecipient.BOTH)
            .registerPacket(NetEaseJsonPacket::new, NetEaseJsonSerializer.INSTANCE, 203, PacketRecipient.BOTH)
            .registerPacket(ConfirmSkinPacket::new, ConfirmSkinSerializer.INSTANCE, 228, PacketRecipient.CLIENT)
            .build();
}
