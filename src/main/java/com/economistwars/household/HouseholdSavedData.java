package com.economistwars.household;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class HouseholdSavedData extends SavedData {
    private static final String DATA_ID = "economistwars_households";
    private static final Codec<HouseholdSavedData> CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf())
            .xmap(HouseholdSavedData::fromSerialized, HouseholdSavedData::toSerialized);
    private static final SavedDataType<HouseholdSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("economistwars", DATA_ID), HouseholdSavedData::new, CODEC, DataFixTypes.LEVEL
    );

    private final Map<UUID, Household> households = new LinkedHashMap<>();
    private final Map<UUID, UUID> householdByCitizen = new HashMap<>();

    public static HouseholdSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public UUID createHousehold() {
        UUID id;
        do {
            id = UUID.randomUUID();
        } while (households.containsKey(id));
        households.put(id, new Household(id));
        setDirty();
        return id;
    }

    public boolean addMember(UUID householdId, UUID citizenId) {
        Household household = households.get(householdId);
        if (household == null) {
            return false;
        }
        UUID currentHousehold = householdByCitizen.get(citizenId);
        if (currentHousehold != null) {
            return currentHousehold.equals(householdId);
        }
        household.add(citizenId);
        householdByCitizen.put(citizenId, householdId);
        setDirty();
        return true;
    }

    public Household getHousehold(UUID householdId) {
        return households.get(householdId);
    }

    public UUID getHouseholdForCitizen(UUID citizenId) {
        return householdByCitizen.get(citizenId);
    }

    public boolean hasMember(UUID householdId, UUID citizenId) {
        Household household = households.get(householdId);
        return household != null
                && household.members().contains(citizenId)
                && householdId.equals(householdByCitizen.get(citizenId));
    }

    public void removeCitizen(UUID citizenId) {
        UUID householdId = householdByCitizen.remove(citizenId);
        if (householdId == null) {
            return;
        }
        Household household = households.get(householdId);
        if (household != null) {
            household.remove(citizenId);
            if (household.isEmpty()) {
                households.remove(householdId);
            }
        }
        setDirty();
    }

    public void removeHousehold(UUID householdId) {
        Household household = households.remove(householdId);
        if (household == null) {
            return;
        }
        for (UUID citizenId : household.members()) {
            householdByCitizen.remove(citizenId, householdId);
        }
        setDirty();
    }

    private Map<String, List<String>> toSerialized() {
        Map<String, List<String>> records = new LinkedHashMap<>();
        for (Household household : households.values()) {
            records.put(household.id().toString(), household.members().stream().map(UUID::toString).toList());
        }
        return records;
    }

    private static HouseholdSavedData fromSerialized(Map<String, List<String>> records) {
        HouseholdSavedData data = new HouseholdSavedData();
        Map<UUID, List<UUID>> membersByHousehold = new LinkedHashMap<>();
        Map<UUID, Integer> referenceCounts = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : records.entrySet()) {
            UUID householdId;
            try {
                householdId = UUID.fromString(entry.getKey());
            } catch (IllegalArgumentException exception) {
                continue;
            }
            List<UUID> parsedMembers = new ArrayList<>();
            for (String memberValue : entry.getValue()) {
                try {
                    UUID memberId = UUID.fromString(memberValue);
                    parsedMembers.add(memberId);
                    referenceCounts.merge(memberId, 1, Integer::sum);
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed member IDs and continue loading valid records.
                }
            }
            membersByHousehold.put(householdId, parsedMembers);
        }
        for (Map.Entry<UUID, List<UUID>> entry : membersByHousehold.entrySet()) {
            UUID householdId = entry.getKey();
            List<UUID> validMembers = entry.getValue().stream()
                    .filter(memberId -> referenceCounts.getOrDefault(memberId, 0) == 1)
                    .toList();
            if (validMembers.isEmpty()) {
                continue;
            }
            Household household = new Household(householdId, Set.copyOf(validMembers));
            data.households.put(householdId, household);
            for (UUID memberId : validMembers) {
                data.householdByCitizen.put(memberId, householdId);
            }
        }
        return data;
    }
}
