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
    public static final Codec<MineSiteSavedData> CODEC = Codec.either(MineSiteState.CODEC.listOf(),
            Codec.unboundedMap(Codec.STRING, Codec.LONG.listOf())).xmap(
                    value -> value.map(MineSiteSavedData::loadSites, MineSiteSavedData::fromSerialized),
                    data -> com.mojang.datafixers.util.Either.left(List.copyOf(data.sites.values())));
    private static final SavedDataType<MineSiteSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("economistwars", "economistwars_mine_sites"),
            MineSiteSavedData::new, CODEC, DataFixTypes.LEVEL
    );

    private final Map<String, Set<Long>> positions = new LinkedHashMap<>();
    private final Map<CitizenAssetKey, MineSiteState> sites = new LinkedHashMap<>();
    public java.util.Optional<MineSiteState> find(CitizenAssetKey key) {
        MineSiteState site = sites.get(key);
        return site != null && site.valid && site.imported ? java.util.Optional.of(site) : java.util.Optional.empty();
    }
    public List<MineSiteState> sites(String dimension) {
        return sites.values().stream().filter(s -> s.valid && s.imported && s.key.dimension().equals(dimension) && s.bounds() != null).toList();
    }
    public MineSiteState registerIfAbsent(CitizenAssetKey key, int stock, net.minecraft.world.level.levelgen.structure.BoundingBox bounds) {
        MineSiteState site = sites.get(key);
        if (site == null) { site = new MineSiteState(key,stock,bounds); site.onChange(this::setDirty); sites.put(key,site); setDirty(); }
        else site.importOnce(stock,bounds);
        return site;
    }
    public void invalidate(CitizenAssetKey key) {
        MineSiteState site = sites.get(key);
        if (site == null) site = registerIfAbsent(key,0,null);
        site.valid = false; setDirty();
    }
    public void placed(CitizenAssetKey key) {
        sites.remove(key); setDirty();
    }
    private static MineSiteSavedData loadSites(List<MineSiteState> sites) {
        MineSiteSavedData data = new MineSiteSavedData();
        for (MineSiteState site : sites) { site.onChange(data::setDirty); data.sites.put(site.key,site); }
        return data;
    }

    public static MineSiteSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public void register(ServerLevel level, BlockPos position) {
        String dimension = level.dimension().identifier().toString();
        registerIfAbsent(new CitizenAssetKey(dimension,position),10000,null);
        if (positions.computeIfAbsent(dimension, ignored -> new LinkedHashSet<>()).add(position.asLong())) {
            setDirty();
        }
    }

    public void unregister(ServerLevel level, BlockPos position) {
        invalidate(new CitizenAssetKey(level.dimension().identifier().toString(),position));
        Set<Long> dimensionPositions = positions.get(level.dimension().identifier().toString());
        if (dimensionPositions != null && dimensionPositions.remove(position.asLong())) {
            if (dimensionPositions.isEmpty()) positions.remove(level.dimension().identifier().toString());
            setDirty();
        }
    }

    public List<BlockPos> positions(ServerLevel level) {
        return sites(level.dimension().identifier().toString()).stream().map(s -> s.key.position()).toList();
    }

    private Map<String, List<Long>> toSerialized() {
        Map<String, List<Long>> result = new LinkedHashMap<>();
        positions.forEach((dimension, values) -> result.put(dimension, List.copyOf(values)));
        return result;
    }

    private static MineSiteSavedData fromSerialized(Map<String, List<Long>> serialized) {
        MineSiteSavedData data = new MineSiteSavedData();
        serialized.forEach((dimension, values) -> {
            data.positions.put(dimension, new LinkedHashSet<>(values));
            for (long value : values) {
                MineSiteState site = data.registerIfAbsent(new CitizenAssetKey(dimension,BlockPos.of(value)),10000,null);
                site.imported = false;
            }
        });
        return data;
    }
}
