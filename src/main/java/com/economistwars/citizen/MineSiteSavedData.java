package com.economistwars.citizen;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Dimension-scoped positions of generated mining work sites. */
public final class MineSiteSavedData extends SavedData {
    private static final Codec<MineSiteSavedData> CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG.listOf())
            .xmap(MineSiteSavedData::fromSerialized, MineSiteSavedData::toSerialized);
    private static final SavedDataType<MineSiteSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("economistwars", "economistwars_mine_sites"),
            MineSiteSavedData::new, CODEC, DataFixTypes.LEVEL
    );

    private final Map<String, Set<Long>> positions = new LinkedHashMap<>();

    public static MineSiteSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public void register(ServerLevel level, BlockPos position) {
        String dimension = level.dimension().identifier().toString();
        if (positions.computeIfAbsent(dimension, ignored -> new LinkedHashSet<>()).add(position.asLong())) {
            setDirty();
        }
    }

    public void unregister(ServerLevel level, BlockPos position) {
        Set<Long> dimensionPositions = positions.get(level.dimension().identifier().toString());
        if (dimensionPositions != null && dimensionPositions.remove(position.asLong())) {
            if (dimensionPositions.isEmpty()) positions.remove(level.dimension().identifier().toString());
            setDirty();
        }
    }

    public List<BlockPos> positions(ServerLevel level) {
        return positions.getOrDefault(level.dimension().identifier().toString(), Set.of())
                .stream().map(BlockPos::of).toList();
    }

    private Map<String, List<Long>> toSerialized() {
        Map<String, List<Long>> result = new LinkedHashMap<>();
        positions.forEach((dimension, values) -> result.put(dimension, List.copyOf(values)));
        return result;
    }

    private static MineSiteSavedData fromSerialized(Map<String, List<Long>> serialized) {
        MineSiteSavedData data = new MineSiteSavedData();
        serialized.forEach((dimension, values) -> data.positions.put(dimension, new LinkedHashSet<>(values)));
        return data;
    }
}
