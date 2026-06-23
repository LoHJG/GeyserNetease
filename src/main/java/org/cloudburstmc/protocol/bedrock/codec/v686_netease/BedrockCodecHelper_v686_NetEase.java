package org.cloudburstmc.protocol.bedrock.codec.v686_netease;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.EntityDataTypeMap;
import org.cloudburstmc.protocol.bedrock.codec.v575.BedrockCodecHelper_v575;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.TextProcessingEventOrigin;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestActionType;
import org.cloudburstmc.protocol.common.util.TypeMap;
import org.cloudburstmc.protocol.common.util.VarInts;

import java.math.BigInteger;
import java.util.Set;

public class BedrockCodecHelper_v686_NetEase extends BedrockCodecHelper_v575 {
    private static final int PLAYER_AUTH_INPUT_DATA_NETEASE = PlayerAuthInputData.RECEIVED_SERVER_DATA.ordinal() + 1;

    public BedrockCodecHelper_v686_NetEase(EntityDataTypeMap entityData, TypeMap<Class<?>> gameRulesTypes,
                                           TypeMap<ItemStackRequestActionType> stackRequestActionTypes,
                                           TypeMap<ContainerSlotType> containerSlotTypes, TypeMap<Ability> abilities,
                                           TypeMap<TextProcessingEventOrigin> textProcessingEventOrigins) {
        super(entityData, gameRulesTypes, stackRequestActionTypes, containerSlotTypes, abilities, textProcessingEventOrigins);
    }

    @Override
    public <T extends Enum<?>> void readLargeVarIntFlags(ByteBuf buffer, Set<T> flags, Class<T> clazz) {
        boolean adaptNetEase = clazz == PlayerAuthInputData.class;
        BigInteger flagsInt = VarInts.readUnsignedBigVarInt(buffer, clazz.getEnumConstants().length);
        for (T flag : clazz.getEnumConstants()) {
            int ordinal = flag.ordinal();
            if (adaptNetEase && ordinal >= PLAYER_AUTH_INPUT_DATA_NETEASE) {
                ordinal += 1;
            }
            if (flagsInt.testBit(ordinal)) {
                flags.add(flag);
            }
        }
    }

    @Override
    public <T extends Enum<?>> void writeLargeVarIntFlags(ByteBuf buffer, Set<T> flags, Class<T> clazz) {
        boolean adaptNetEase = clazz == PlayerAuthInputData.class;
        BigInteger flagsInt = BigInteger.ZERO;
        for (T flag : flags) {
            int ordinal = flag.ordinal();
            if (adaptNetEase && ordinal >= PLAYER_AUTH_INPUT_DATA_NETEASE) {
                ordinal += 1;
            }
            flagsInt = flagsInt.setBit(ordinal);
        }
        VarInts.writeUnsignedBigVarInt(buffer, flagsInt);
    }
}
