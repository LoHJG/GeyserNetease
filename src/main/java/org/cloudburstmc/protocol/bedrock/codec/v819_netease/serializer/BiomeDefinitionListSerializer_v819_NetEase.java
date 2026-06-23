package org.cloudburstmc.protocol.bedrock.codec.v819_netease.serializer;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v800.serializer.BiomeDefinitionListSerializer_v800;
import org.cloudburstmc.protocol.bedrock.data.biome.BiomeDefinitionChunkGenData;
import org.cloudburstmc.protocol.bedrock.data.biome.BiomeDefinitionData;
import org.cloudburstmc.protocol.common.util.Preconditions;
import org.cloudburstmc.protocol.common.util.SequencedHashSet;
import org.cloudburstmc.protocol.common.util.VarInts;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class BiomeDefinitionListSerializer_v819_NetEase extends BiomeDefinitionListSerializer_v800 {
    public static final BiomeDefinitionListSerializer_v819_NetEase INSTANCE = new BiomeDefinitionListSerializer_v819_NetEase();

    @Override
    protected BiomeDefinitionData readDefinition(ByteBuf buffer, BedrockCodecHelper helper, List<String> strings) {
        Integer id = readDefinitionId(buffer, helper);
        float temperature = buffer.readFloatLE();
        float downfall = buffer.readFloatLE();
        float redSporeDensity = buffer.readFloatLE();
        float blueSporeDensity = buffer.readFloatLE();
        float ashDensity = buffer.readFloatLE();
        float whiteAshDensity = buffer.readFloatLE();
        float depth = buffer.readFloatLE();
        float scale = buffer.readFloatLE();
        Color mapWaterColor = new Color(buffer.readIntLE(), true);
        boolean rain = buffer.readBoolean();
        buffer.readIntLE();
        helper.readString(buffer);

        List<String> tags = helper.readOptional(buffer, null, byteBuf -> {
            int length = VarInts.readUnsignedInt(byteBuf);
            Preconditions.checkArgument(byteBuf.isReadable(length * 2), "Not enough readable bytes for tags");
            List<String> tagList = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                tagList.add(strings.get(byteBuf.readUnsignedShortLE()));
            }
            return tagList;
        });

        BiomeDefinitionChunkGenData chunkGenData = helper.readOptional(buffer, null,
                (buf, aHelper) -> readDefinitionChunkGen(buf, aHelper, strings));

        return new BiomeDefinitionData(id, temperature, downfall, redSporeDensity, blueSporeDensity,
                ashDensity, whiteAshDensity, 0f, depth, scale, mapWaterColor, rain, tags, chunkGenData);
    }

    @Override
    protected void writeDefinition(ByteBuf buffer, BedrockCodecHelper helper, BiomeDefinitionData definition, SequencedHashSet<String> strings) {
        writeDefinitionId(buffer, helper, definition);
        buffer.writeFloatLE(definition.getTemperature());
        buffer.writeFloatLE(definition.getDownfall());
        buffer.writeFloatLE(definition.getRedSporeDensity());
        buffer.writeFloatLE(definition.getBlueSporeDensity());
        buffer.writeFloatLE(definition.getAshDensity());
        buffer.writeFloatLE(definition.getWhiteAshDensity());
        buffer.writeFloatLE(definition.getDepth());
        buffer.writeFloatLE(definition.getScale());
        buffer.writeIntLE(definition.getMapWaterColor().getRGB());
        buffer.writeBoolean(definition.isRain());
        buffer.writeIntLE(0);
        helper.writeString(buffer, "");

        helper.writeOptionalNull(buffer, definition.getTags(), (byteBuf, aHelper, tags) -> {
            VarInts.writeUnsignedInt(byteBuf, tags.size());
            for (String tag : tags) {
                byteBuf.writeShortLE(strings.addAndGetIndex(tag));
            }
        });
        helper.writeOptionalNull(buffer, definition.getChunkGenData(),
                (buf, aHelper, data) -> writeDefinitionChunkGen(buf, aHelper, data, strings));
    }
}
