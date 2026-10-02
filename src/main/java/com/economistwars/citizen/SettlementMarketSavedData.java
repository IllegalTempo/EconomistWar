package com.economistwars.citizen;

import com.mojang.serialization.Codec;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.Optional;
import java.util.Comparator;
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
    // Keep the old position-index save format readable; authoritative books have their own versioned save.
    private static final SavedDataType<Books> BOOKS_TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("economistwars", "economistwars_market_books"), Books::new, Books.CODEC, DataFixTypes.LEVEL);
    private Books books = new Books();
    private static final class Books extends SavedData {
        static final Codec<Books> CODEC = SettlementMarketState.CODEC.listOf().xmap(list -> {
            Books b = new Books(); list.forEach(s -> { s.onChange(b::setDirty); b.records.put(s.key,s); }); return b;
        }, b -> List.copyOf(b.records.values()));
        final Map<CitizenAssetKey,SettlementMarketState> records = new LinkedHashMap<>();
    }
    public Optional<SettlementMarketState> find(CitizenAssetKey key) {
        SettlementMarketState state = books.records.get(key);
        return state == null || !state.valid ? Optional.empty() : Optional.of(state);
    }
    public List<SettlementMarketState> markets(String dimension) {
        return books.records.values().stream().filter(s -> s.valid && s.key.dimension().equals(dimension)).toList();
    }
    public SettlementMarketState registerIfAbsent(CitizenAssetKey key,SettlementMarketState legacy) {
        SettlementMarketState previous = books.records.putIfAbsent(key,legacy);
        if (previous == null) { legacy.onChange(books::setDirty); books.setDirty(); }
        return previous == null ? legacy : previous;
    }
    public void invalidate(CitizenAssetKey key) {
        var state = books.records.computeIfAbsent(key,SettlementMarketState::new); state.valid = false; books.setDirty();
    }
    public void placed(CitizenAssetKey key) { books.records.remove(key); books.setDirty(); }

    public static SettlementMarketSavedData get(ServerLevel level) {
        SettlementMarketSavedData data = level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
        data.books = level.getServer().overworld().getDataStorage().computeIfAbsent(BOOKS_TYPE);
        return data;
    }

    public void register(ServerLevel level, BlockPos position) {
        register(level.dimension(), position);
    }

    public void register(ResourceKey<Level> dimension, BlockPos position) {
        if (positions.computeIfAbsent(dimension.identifier().toString(), ignored -> new LinkedHashSet<>())
                .add(position.asLong())) setDirty();
    }

    public Optional<BlockPos> nearestPosition(ResourceKey<Level> dimension, BlockPos origin, int radius) {
        if (radius < 0) return Optional.empty();
        double maxDistanceSquared = (double) radius * radius;
        return positions.getOrDefault(dimension.identifier().toString(), Set.of()).stream()
                .map(BlockPos::of)
                .filter(position -> position.distSqr(origin) <= maxDistanceSquared)
                .min(Comparator.comparingDouble(position -> position.distSqr(origin)));
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
