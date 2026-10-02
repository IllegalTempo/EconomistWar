package com.economistwars.citizen;

import com.mojang.serialization.Codec;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class CitizenSavedData extends SavedData {
    public static final Codec<CitizenSavedData> CODEC = CitizenState.CODEC.listOf().xmap(CitizenSavedData::load, CitizenSavedData::states);
    private static final SavedDataType<CitizenSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("economistwars", "economistwars_citizens"), CitizenSavedData::new, CODEC, DataFixTypes.LEVEL);
    private final Map<UUID, CitizenState> citizens = new TreeMap<>();
    public static CitizenSavedData get(ServerLevel level) { return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE); }
    public Optional<CitizenState> find(UUID id) { return Optional.ofNullable(citizens.get(id)); }
    public CitizenState registerIfAbsent(CitizenState initial) {
        CitizenState previous = citizens.putIfAbsent(initial.id(), initial);
        if (previous == null) setDirty();
        return previous == null ? initial : previous;
    }
    /** Rollback only for a record this operation created, never a previously existing record. */
    public void rollbackCreation(CitizenState created) { if (citizens.remove(created.id(), created)) setDirty(); }
    public List<CitizenState> states() { return List.copyOf(citizens.values()); }
    private static CitizenSavedData load(List<CitizenState> states) {
        CitizenSavedData result = new CitizenSavedData(); states.forEach(s -> result.citizens.putIfAbsent(s.id(), s)); return result;
    }
}
