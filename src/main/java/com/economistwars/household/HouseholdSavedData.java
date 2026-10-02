package com.economistwars.household;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class HouseholdSavedData extends SavedData {
    private static final String DATA_ID = "economistwars_households";
    private record Snapshot(List<String> members, Optional<String> home, List<ItemStack> storage, List<String> economy) {
        private static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.listOf().fieldOf("members").forGetter(Snapshot::members),
                Codec.STRING.optionalFieldOf("home").forGetter(Snapshot::home),
                ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("storage", List.of()).forGetter(Snapshot::storage),
                Codec.STRING.listOf().optionalFieldOf("economy", List.of()).forGetter(Snapshot::economy)
        ).apply(instance, Snapshot::new));
    }
    private static final Codec<HouseholdSavedData> CODEC = Codec.unboundedMap(Codec.STRING, Snapshot.CODEC)
            .xmap(HouseholdSavedData::fromSnapshots, HouseholdSavedData::toSnapshots);
    private static final SavedDataType<HouseholdSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("economistwars", DATA_ID), HouseholdSavedData::new, CODEC, DataFixTypes.LEVEL
    );

    private final Map<UUID, Household> households = new LinkedHashMap<>();
    private final Map<UUID, UUID> householdByCitizen = new HashMap<>();
    private net.minecraft.server.MinecraftServer server;

    public static HouseholdSavedData get(ServerLevel level) {
        HouseholdSavedData data = level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
        data.server = level.getServer();
        return data;
    }

    public Household storageFor(HouseholdStorageBlockEntity storage) {
        return storage.ownerId().map(households::get).orElse(null);
    }
    private boolean findStorage(ServerLevel level, Household household, BlockPos home) {
        for (BlockPos position : BlockPos.betweenClosed(home.offset(-2,0,-2), home.offset(2,0,2))) {
            if (level.getBlockEntity(position) instanceof HouseholdStorageBlockEntity existing) {
                existing.ownerId().filter(owner -> !households.containsKey(owner)).ifPresent(existing::abandon);
            }
            if (level.getBlockEntity(position) instanceof HouseholdStorageBlockEntity storage && storage.claim(household.id())) {
                return true;
            }
        }
        return false;
    }
    private void ensureStorage(ServerLevel level, Household household, BlockPos home) {
        if (findStorage(level, household, home)) return;
        BlockPos position = home.offset(-1,0,0);
        if (!level.getBlockState(position).isAir()) return;
        level.setBlock(position, HouseholdStorageBlock.BLOCK.defaultBlockState(), 3);
        if (level.getBlockEntity(position) instanceof HouseholdStorageBlockEntity storage) storage.claim(household.id());
    }

    public UUID createHousehold() {
        UUID id;
        do {
            id = UUID.randomUUID();
        } while (households.containsKey(id));
        Household created = new Household(id); created.onChange(this::setDirty); households.put(id, created);
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

    public List<Household> households() {
        return List.copyOf(households.values());
    }

    public void registerHome(ServerLevel level, UUID householdId, BlockPos position) {
        server = level.getServer();
        Household household = households.get(householdId);
        if (household == null) throw new IllegalArgumentException("Unknown household: " + householdId);
        household.setHome(level.dimension(), position);
        ensureStorage(level, household, position);
        for (BlockPos crop : BlockPos.betweenClosed(position.offset(-4,0,-4), position.offset(4,0,4)))
            if (level.getBlockState(crop).is(net.minecraft.world.level.block.Blocks.WHEAT) && level.getBlockState(crop.below()).is(net.minecraft.world.level.block.Blocks.FARMLAND)) com.economistwars.ownership.OwnershipSavedData.get(level).claimCrop(level.dimension(), householdId, crop);
        com.economistwars.ownership.OwnershipSavedData.get(level)
                .grantStarter(level.dimension(), householdId, position);
        setDirty();
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
                abandonStorage(household);
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
        abandonStorage(household);
        for (UUID citizenId : household.members()) {
            householdByCitizen.remove(citizenId, householdId);
        }
        setDirty();
    }

    private void abandonStorage(Household household) {
        // Storage blocks resolve owner IDs against this registry and abandon missing owners
        // when next loaded/claimed. Backend death must not inspect or load a home chunk.
    }

    private Map<String, Snapshot> toSnapshots() {
        Map<String, Snapshot> records = new LinkedHashMap<>();
        for (Household household : households.values()) {
            records.put(household.id().toString(), new Snapshot(
                    household.members().stream().map(UUID::toString).toList(),
                    household.serializedHome(), household.storageSlots(), household.economyState()));
        }
        return records;
    }

    private static HouseholdSavedData fromSnapshots(Map<String, Snapshot> records) {
        Map<String, List<String>> memberRecords = new LinkedHashMap<>();
        records.forEach((id, snapshot) -> {
            List<String> fields = new ArrayList<>(snapshot.members());
            snapshot.home().ifPresent(fields::add);
            memberRecords.put(id, fields);
        });
        HouseholdSavedData data = fromMembers(memberRecords);
        records.forEach((id, snapshot) -> {
            try {
                Household household = data.getHousehold(UUID.fromString(id));
                if (household != null) { household.restoreStorage(snapshot.storage()); household.restoreEconomy(snapshot.economy()); }
            } catch (IllegalArgumentException ignored) {
                // Invalid household IDs have already been filtered during membership loading.
            }
        });
        return data;
    }

    private static HouseholdSavedData fromMembers(Map<String, List<String>> records) {
        HouseholdSavedData data = new HouseholdSavedData();
        Map<UUID, List<UUID>> membersByHousehold = new LinkedHashMap<>();
        Map<UUID, List<String>> homesByHousehold = new HashMap<>();
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
            homesByHousehold.put(householdId, entry.getValue().stream()
                    .filter(field -> field.startsWith("home|"))
                    .toList());
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
            homesByHousehold.get(householdId).forEach(household::restoreHome);
            household.onChange(data::setDirty);
            data.households.put(householdId, household);
            for (UUID memberId : validMembers) {
                data.householdByCitizen.put(memberId, householdId);
            }
        }
        return data;
    }
}
