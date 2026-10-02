package com.economistwars.citizen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.*;

/** Backend reservations survive visual unloads and world saves. */
public final class HouseholdBedSavedData extends SavedData {
    record Bed(CitizenAssetKey key, String household, String citizen) {
        static final Codec<Bed> CODEC = RecordCodecBuilder.create(i -> i.group(
                CitizenAssetKey.CODEC.fieldOf("key").forGetter(Bed::key),
                Codec.STRING.fieldOf("household").forGetter(Bed::household),
                Codec.STRING.optionalFieldOf("citizen", "").forGetter(Bed::citizen)).apply(i,Bed::new));
    }
    public static final Codec<HouseholdBedSavedData> CODEC = Bed.CODEC.listOf().xmap(HouseholdBedSavedData::load, s -> List.copyOf(s.beds.values()));
    private static final SavedDataType<HouseholdBedSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("economistwars","economistwars_beds"),HouseholdBedSavedData::new,CODEC,DataFixTypes.LEVEL);
    private final Map<CitizenAssetKey,Bed> beds = new LinkedHashMap<>();
    public static HouseholdBedSavedData get(ServerLevel level) { return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE); }
    public void register(UUID householdId,CitizenAssetKey key) {
        if (!beds.containsKey(key)) { beds.put(key,new Bed(key,householdId.toString(),"")); setDirty(); }
    }
    public Optional<CitizenAssetKey> available(UUID householdId,UUID citizenId,String dimension) {
        return beds.values().stream().filter(b -> b.key.dimension().equals(dimension) && b.household.equals(householdId.toString())
                && (b.citizen.isEmpty() || b.citizen.equals(citizenId.toString()))).map(Bed::key).findFirst();
    }
    public Optional<CitizenAssetKey> reserve(UUID householdId, UUID citizenId) {
        Optional<Bed> bed = beds.values().stream().filter(b -> b.household.equals(householdId.toString())
                && b.citizen.equals(citizenId.toString())).findFirst();
        if (bed.isEmpty()) bed = beds.values().stream().filter(b -> b.household.equals(householdId.toString()) && b.citizen.isEmpty()).findFirst();
        bed.ifPresent(b -> { beds.put(b.key,new Bed(b.key,b.household,citizenId.toString())); setDirty(); });
        return bed.map(Bed::key);
    }
    public boolean reserve(CitizenAssetKey key,UUID householdId,UUID citizenId) {
        Bed bed = beds.get(key);
        if (bed == null || !bed.household.equals(householdId.toString()) || !bed.citizen.isEmpty() && !bed.citizen.equals(citizenId.toString())) return false;
        beds.put(key,new Bed(key,bed.household,citizenId.toString())); setDirty(); return true;
    }
    public void release(UUID citizenId) {
        for (Bed bed : List.copyOf(beds.values())) if (bed.citizen.equals(citizenId.toString())) {
            beds.put(bed.key,new Bed(bed.key,bed.household,"")); setDirty();
        }
    }
    public void invalidate(CitizenAssetKey key) { if (beds.remove(key) != null) setDirty(); }
    public boolean reserved(CitizenAssetKey key,UUID citizenId) { return beds.containsKey(key) && beds.get(key).citizen.equals(citizenId.toString()); }
    private static HouseholdBedSavedData load(List<Bed> beds) { HouseholdBedSavedData data = new HouseholdBedSavedData(); beds.forEach(b -> data.beds.put(b.key,b)); return data; }
}
