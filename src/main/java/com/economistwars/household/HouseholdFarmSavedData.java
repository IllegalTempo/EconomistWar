package com.economistwars.household;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Food stores and claimed crop blocks for each household. */
public final class HouseholdFarmSavedData extends SavedData {
    private static final Codec<HouseholdFarmSavedData> CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf())
            .xmap(HouseholdFarmSavedData::fromSerialized, HouseholdFarmSavedData::toSerialized);
    private static final SavedDataType<HouseholdFarmSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("economistwars", "economistwars_household_farms"),
            HouseholdFarmSavedData::new, CODEC, DataFixTypes.LEVEL
    );

    private final Map<UUID, Farm> farms = new LinkedHashMap<>();

    public static HouseholdFarmSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public void registerHome(ServerLevel level, UUID householdId, BlockPos home) {
        Farm farm = farms.computeIfAbsent(householdId, ignored -> new Farm());
        farm.dimension = level.dimension().identifier().toString();
        farm.home = home.asLong();
        Set<Long> claimed = new LinkedHashSet<>();
        for (Farm other : farms.values()) {
            if (other != farm && other.dimension.equals(farm.dimension)) {
                claimed.addAll(other.crops);
            }
        }
        for (BlockPos crop : BlockPos.betweenClosed(home.offset(-4, 0, -4), home.offset(4, 0, 4))) {
            if (level.getBlockState(crop.below()).is(Blocks.FARMLAND)
                    && level.getBlockState(crop).is(Blocks.WHEAT)
                    && !claimed.contains(crop.asLong())) {
                farm.crops.add(crop.asLong());
            }
        }
        if (farm.food == 0) {
            farm.food = 8;
        }
        farm.lastFedDay = level.getOverworldClockTime() / 24000;
        setDirty();
    }

    public boolean ensureStorage(ServerLevel level, UUID householdId) {
        Farm farm = farms.get(householdId);
        if (farm == null || farm.home == null || !farm.dimension.equals(level.dimension().identifier().toString())) {
            return false;
        }
        if (storage(level, farm, householdId) != null) {
            return true;
        }
        BlockPos home = BlockPos.of(farm.home);
        if (!level.isLoaded(home)) {
            return false;
        }
        HouseholdStorageBlockEntity found = null;
        for (BlockPos position : BlockPos.betweenClosed(home.offset(-2, 0, -2), home.offset(2, 0, 2))) {
            if (level.getBlockEntity(position) instanceof HouseholdStorageBlockEntity storage
                    && (storage.belongsTo(householdId) || storage.claim(householdId))) {
                found = storage;
                break;
            }
        }
        if (found == null) {
            BlockPos fallback = home.offset(-1, 0, 0);
            if (!level.getBlockState(fallback).isAir()
                    || !level.setBlock(fallback, HouseholdStorageBlock.BLOCK.defaultBlockState(), 3)
                    || !(level.getBlockEntity(fallback) instanceof HouseholdStorageBlockEntity storage)
                    || !storage.claim(householdId)) {
                return false;
            }
            found = storage;
        }
        if (found.wheatCount(householdId) == 0 && farm.food > 0) {
            if (farm.food > found.freeWheatCapacity(householdId)) {
                return false;
            }
            found.insert(householdId, new ItemStack(Items.WHEAT, farm.food));
        }
        farm.storage = found.getBlockPos().asLong();
        farm.food = found.wheatCount(householdId);
        setDirty();
        return true;
    }

    public ItemStack deposit(ServerLevel level, UUID householdId, ItemStack wheat) {
        if (!ensureStorage(level, householdId)) {
            return wheat.copy();
        }
        Farm farm = farms.get(householdId);
        HouseholdStorageBlockEntity storage = storage(level, farm, householdId);
        if (storage == null) {
            return wheat.copy();
        }
        ItemStack remainder = storage.insert(householdId, wheat);
        farm.food = storage.wheatCount(householdId);
        setDirty();
        return remainder;
    }

    private HouseholdStorageBlockEntity storage(ServerLevel level, Farm farm, UUID householdId) {
        if (farm.storage == null) {
            return null;
        }
        BlockPos position = BlockPos.of(farm.storage);
        if (!level.isLoaded(position)) {
            return null;
        }
        return level.getBlockEntity(position) instanceof HouseholdStorageBlockEntity storage
                && storage.belongsTo(householdId) ? storage : null;
    }

    public Optional<BlockPos> storagePosition(ServerLevel level, UUID householdId) {
        Farm farm = farms.get(householdId);
        return farm != null && farm.storage != null
                && farm.dimension.equals(level.dimension().identifier().toString())
                ? Optional.of(BlockPos.of(farm.storage)) : Optional.empty();
    }

    public List<BlockPos> crops(ServerLevel level, UUID householdId) {
        Farm farm = farms.get(householdId);
        if (farm == null || !farm.dimension.equals(level.dimension().identifier().toString())) {
            return List.of();
        }
        return farm.crops.stream().map(BlockPos::of).toList();
    }

    public boolean ownsCrop(ServerLevel level, UUID householdId, BlockPos crop) {
        Farm farm = farms.get(householdId);
        return farm != null && farm.dimension.equals(level.dimension().identifier().toString())
                && farm.crops.contains(crop.asLong());
    }

    public Optional<BlockPos> home(ServerLevel level, UUID householdId) {
        Farm farm = farms.get(householdId);
        if (farm == null || farm.home == null || !farm.dimension.equals(level.dimension().identifier().toString())) {
            return Optional.empty();
        }
        return Optional.of(BlockPos.of(farm.home));
    }

    public Map<UUID, BlockPos> homes(ServerLevel level) {
        Map<UUID, BlockPos> result = new LinkedHashMap<>();
        String dimension = level.dimension().identifier().toString();
        for (Map.Entry<UUID, Farm> entry : farms.entrySet()) {
            Farm farm = entry.getValue();
            if (farm.home != null && farm.dimension.equals(dimension)) {
                result.put(entry.getKey(), BlockPos.of(farm.home));
            }
        }
        return result;
    }

    /**
     * Feeds a household at most once per Minecraft day.
     *
     * @return the newly recorded shortage for this day, or {@code -1} when no
     * daily feeding happened during this call
     */
    public int feedHousehold(ServerLevel level, UUID householdId, int members) {
        Farm farm = farms.computeIfAbsent(householdId, ignored -> new Farm());
        if (farm.home != null) {
            if (!ensureStorage(level, householdId)) {
                return -1;
            }
            HouseholdStorageBlockEntity storage = storage(level, farm, householdId);
            if (storage == null) {
                return -1;
            }
            farm.food = storage.wheatCount(householdId);
        }
        long day = level.getOverworldClockTime() / 24000;
        if (farm.lastFedDay < 0) {
            farm.lastFedDay = day;
            setDirty();
        } else if (day > farm.lastFedDay) {
            int need = Math.max(0, members);
            HouseholdStorageBlockEntity storage = storage(level, farm, householdId);
            int consumed = storage == null ? Math.min(need, farm.food) : storage.takeWheat(householdId, need);
            farm.shortage = need - consumed;
            farm.food -= consumed;
            int marketSurplus = Math.min(4, Math.max(0, farm.food - 32));
            if (storage != null) {
                marketSurplus = storage.takeWheat(householdId, marketSurplus);
            }
            farm.food -= marketSurplus;
            farm.coins = Math.min(9999, farm.coins + marketSurplus);
            farm.lastFedDay = day;
            setDirty();
            return farm.shortage;
        }
        return -1;
    }

    /** Returns the bounded health damage applied once for a daily shortage. */
    public static float shortageDamage(int shortage) {
        return Math.min(4.0F, Math.max(0, shortage));
    }

    public int food(UUID householdId) {
        Farm farm = farms.get(householdId);
        return farm == null ? 0 : farm.food;
    }

    public int coins(UUID householdId) {
        Farm farm = farms.get(householdId);
        return farm == null ? 100 : farm.coins;
    }

    public boolean spendCoins(UUID householdId, int amount) {
        if (amount < 0 || coins(householdId) < amount) {
            return false;
        }
        Farm farm = farms.computeIfAbsent(householdId, ignored -> new Farm());
        farm.coins -= amount;
        setDirty();
        return true;
    }

    public void receiveCoins(UUID householdId, int amount) {
        if (amount <= 0) {
            return;
        }
        Farm farm = farms.computeIfAbsent(householdId, ignored -> new Farm());
        farm.coins = Math.min(9999, farm.coins + amount);
        setDirty();
    }

    public boolean beginMarketDay(ServerLevel level, UUID householdId) {
        Farm farm = farms.computeIfAbsent(householdId, ignored -> new Farm());
        long day = level.getOverworldClockTime() / 24000;
        if (farm.lastMarketDay >= day) {
            return false;
        }
        farm.lastMarketDay = day;
        setDirty();
        return true;
    }

    public int shortage(UUID householdId) {
        Farm farm = farms.get(householdId);
        return farm == null ? 0 : farm.shortage;
    }

    public void removeHousehold(UUID householdId) {
        if (farms.remove(householdId) != null) {
            setDirty();
        }
    }

    public void removeHousehold(ServerLevel level, UUID householdId) {
        Farm farm = farms.get(householdId);
        if (farm != null) {
            HouseholdStorageBlockEntity storage = storage(level, farm, householdId);
            if (storage != null) {
                storage.abandon(householdId);
            }
        }
        removeHousehold(householdId);
    }

    private Map<String, List<String>> toSerialized() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<UUID, Farm> entry : farms.entrySet()) {
            Farm farm = entry.getValue();
            List<String> fields = new ArrayList<>();
            fields.add(farm.dimension);
            fields.add(Integer.toString(farm.food));
            fields.add(Long.toString(farm.lastFedDay));
            fields.add(Integer.toString(farm.shortage));
            fields.add(farm.home == null ? "" : Long.toString(farm.home));
            fields.add("coins:" + farm.coins);
            fields.add("market:" + farm.lastMarketDay);
            fields.add("storage:" + (farm.storage == null ? "" : farm.storage));
            farm.crops.forEach(position -> fields.add(Long.toString(position)));
            result.put(entry.getKey().toString(), fields);
        }
        return result;
    }

    private static HouseholdFarmSavedData fromSerialized(Map<String, List<String>> records) {
        HouseholdFarmSavedData data = new HouseholdFarmSavedData();
        for (Map.Entry<String, List<String>> entry : records.entrySet()) {
            List<String> fields = entry.getValue();
            if (fields.size() < 5) {
                continue;
            }
            try {
                UUID id = UUID.fromString(entry.getKey());
                Farm farm = new Farm();
                farm.dimension = fields.get(0);
                farm.food = Math.clamp(Integer.parseInt(fields.get(1)), 0, 9999);
                farm.lastFedDay = Long.parseLong(fields.get(2));
                farm.shortage = Math.max(0, Integer.parseInt(fields.get(3)));
                if (!fields.get(4).isBlank()) {
                    farm.home = Long.parseLong(fields.get(4));
                }
                int cropStart = 5;
                if (fields.size() > 5 && fields.get(5).startsWith("coins:")) {
                    try {
                        farm.coins = Math.clamp(Integer.parseInt(fields.get(5).substring(6)), 0, 9999);
                    } catch (NumberFormatException ignored) {
                        // Keep the default wallet if this optional field is damaged.
                    }
                    cropStart = 6;
                }
                if (fields.size() > cropStart && fields.get(cropStart).startsWith("market:")) {
                    try {
                        farm.lastMarketDay = Long.parseLong(fields.get(cropStart).substring(7));
                    } catch (NumberFormatException ignored) {
                        // A damaged market timestamp should not discard food or land.
                    }
                    cropStart++;
                }
                if (fields.size() > cropStart && fields.get(cropStart).startsWith("storage:")) {
                    String storedPosition = fields.get(cropStart).substring(8);
                    if (!storedPosition.isBlank()) {
                        try {
                            farm.storage = Long.parseLong(storedPosition);
                        } catch (NumberFormatException ignored) {
                            // A missing storage location can be recovered from the home.
                        }
                    }
                    cropStart++;
                }
                for (int index = cropStart; index < fields.size(); index++) {
                    try {
                        farm.crops.add(Long.parseLong(fields.get(index)));
                    } catch (NumberFormatException ignored) {
                        // A damaged position does not invalidate the household's food store.
                    }
                }
                data.farms.put(id, farm);
            } catch (IllegalArgumentException ignored) {
                // Skip malformed records without losing valid households.
            }
        }
        return data;
    }

    private static final class Farm {
        private String dimension = "";
        private Long home;
        private Long storage;
        private final Set<Long> crops = new LinkedHashSet<>();
        private int food;
        private int coins = 100;
        private long lastMarketDay = -1;
        private long lastFedDay = -1;
        private int shortage;
    }
}
