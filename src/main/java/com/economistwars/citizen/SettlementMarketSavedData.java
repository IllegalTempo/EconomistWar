package com.economistwars.citizen;

import com.mojang.serialization.Codec;
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

/** Registered generated settlement market anchors, indexed by dimension. */
public final class SettlementMarketSavedData extends SavedData {
    private static final Codec<SettlementMarketSavedData> CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG.listOf())
            .xmap(SettlementMarketSavedData::fromSerialized, SettlementMarketSavedData::toSerialized);
    private static final SavedDataType<SettlementMarketSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("economistwars", "economistwars_settlement_markets"),
            SettlementMarketSavedData::new, CODEC, DataFixTypes.LEVEL);

    private final Map<String, Set<Long>> positions = new LinkedHashMap<>();

    public static SettlementMarketSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public void register(ServerLevel level, BlockPos position) {
        String dimension = level.dimension().identifier().toString();
        if (positions.computeIfAbsent(dimension, ignored -> new LinkedHashSet<>()).add(position.asLong())) setDirty();
    }

    public List<BlockPos> positions(ServerLevel level) {
        return positions.getOrDefault(level.dimension().identifier().toString(), Set.of())
                .stream().map(BlockPos::of).toList();
    }

    private Map<String, List<Long>> toSerialized() {
        Map<String, List<Long>> serialized = new LinkedHashMap<>();
        positions.forEach((dimension, values) -> serialized.put(dimension, List.copyOf(values)));
        return serialized;
    }

    private static SettlementMarketSavedData fromSerialized(Map<String, List<Long>> serialized) {
        SettlementMarketSavedData data = new SettlementMarketSavedData();
        serialized.forEach((dimension, values) -> data.positions.put(dimension, new LinkedHashSet<>(values)));
        return data;
    }
}
