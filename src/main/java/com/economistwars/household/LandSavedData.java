//package com.economistwars.household;
//
//import com.mojang.serialization.Codec;
//import java.util.ArrayList;
//import java.util.LinkedHashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.Optional;
//import java.util.UUID;
//import net.minecraft.core.BlockPos;
//import net.minecraft.resources.Identifier;
//import net.minecraft.server.level.ServerLevel;
//import net.minecraft.util.datafix.DataFixTypes;
//import net.minecraft.world.level.saveddata.SavedData;
//import net.minecraft.world.level.saveddata.SavedDataType;
//
///** Persistent title to the settlement's fixed seven-by-seven farm parcels. */
//public final class LandSavedData extends SavedData {
//    public static final int PRICE = 60;
//    private static final Codec<LandSavedData> CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING)
//            .xmap(LandSavedData::fromSerialized, data -> new LinkedHashMap<>(data.owners));
//    private static final SavedDataType<LandSavedData> TYPE = new SavedDataType<>(
//            Identifier.fromNamespaceAndPath("economistwars", "economistwars_land"),
//            LandSavedData::new, CODEC, DataFixTypes.LEVEL
//    );
//
//    private final Map<String, String> owners = new LinkedHashMap<>();
//
//    public static LandSavedData get(ServerLevel level) {
//        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
//    }
//
//    public void registerParcel(ServerLevel level, BlockPos center) {
//        if (owners.putIfAbsent(key(level, center), "") == null) {
//            setDirty();
//            HouseholdFarmSavedData.get(level).homes(level).forEach((householdId, home) ->
//                    grantStarter(level, householdId, home));
//        }
//    }
//
//    public void grantStarter(ServerLevel level, UUID householdId, BlockPos home) {
//        if (parcelCount(householdId) != 0) {
//            return;
//        }
//        String starter = nearestAvailable(level, home);
//        if (starter != null) {
//            owners.put(starter, householdId.toString());
//            setDirty();
//        }
//    }
//
//    public int parcelCount(UUID householdId) {
//        String owner = householdId.toString();
//        return (int) owners.values().stream().filter(owner::equals).count();
//    }
//
//    public Optional<ParcelInfo> parcelAt(ServerLevel level, BlockPos position) {
//        String prefix = level.dimension().identifier() + "|";
//        for (Map.Entry<String, String> entry : owners.entrySet()) {
//            if (!entry.getKey().startsWith(prefix)) {
//                continue;
//            }
//            BlockPos center = BlockPos.of(Long.parseLong(entry.getKey().substring(prefix.length())));
//            if (Math.abs(position.getX() - center.getX()) <= 3
//                    && Math.abs(position.getZ() - center.getZ()) <= 3
//                    && Math.abs(position.getY() - center.getY()) <= 2) {
//                return Optional.of(new ParcelInfo(center, entry.getValue().isBlank()
//                        ? null : UUID.fromString(entry.getValue())));
//            }
//        }
//        return Optional.empty();
//    }
//
//    public List<BlockPos> ownedCrops(ServerLevel level, UUID householdId) {
//        List<BlockPos> result = new ArrayList<>();
//        String prefix = level.dimension().identifier() + "|";
//        String owner = householdId.toString();
//        for (Map.Entry<String, String> entry : owners.entrySet()) {
//            if (!entry.getKey().startsWith(prefix) || !entry.getValue().equals(owner)) {
//                continue;
//            }
//            BlockPos center = BlockPos.of(Long.parseLong(entry.getKey().substring(prefix.length())));
//            for (BlockPos position : BlockPos.betweenClosed(center.offset(-3, 0, -3), center.offset(3, 0, 3))) {
//                if (!position.equals(center)) {
//                    result.add(position.immutable());
//                }
//            }
//        }
//        return result;
//    }
//
//    public boolean ownsCrop(ServerLevel level, UUID householdId, BlockPos crop) {
//        String prefix = level.dimension().identifier() + "|";
//        String owner = householdId.toString();
//        for (Map.Entry<String, String> entry : owners.entrySet()) {
//            if (!entry.getKey().startsWith(prefix) || !entry.getValue().equals(owner)) {
//                continue;
//            }
//            BlockPos center = BlockPos.of(Long.parseLong(entry.getKey().substring(prefix.length())));
//            if (crop.getY() == center.getY() && !crop.equals(center)
//                    && Math.abs(crop.getX() - center.getX()) <= 3
//                    && Math.abs(crop.getZ() - center.getZ()) <= 3) {
//                return true;
//            }
//        }
//        return false;
//    }
//
//    public void tickHousehold(ServerLevel level, UUID householdId, int members, HouseholdFarmSavedData farms) {
//        if (parcelCount(householdId) == 0) {
//            farms.home(level, householdId).ifPresent(home -> grantStarter(level, householdId, home));
//            return;
//        }
//        int owned = parcelCount(householdId);
//        if (!farms.beginMarketDay(level, householdId) || owned >= 3 || farms.coins(householdId) < PRICE
//                || (farms.food(householdId) >= members * 4 && farms.coins(householdId) < 150)) {
//            return;
//        }
//        BlockPos home = farms.home(level, householdId).orElse(null);
//        if (home == null) {
//            return;
//        }
//        String parcel = owned == 1 ? nearestAvailable(level, home) : null;
//        UUID seller = null;
//        if (parcel == null) {
//            String prefix = level.dimension().identifier() + "|";
//            for (Map.Entry<String, String> entry : owners.entrySet()) {
//                if (!entry.getKey().startsWith(prefix) || entry.getValue().isBlank()) {
//                    continue;
//                }
//                BlockPos center = BlockPos.of(Long.parseLong(entry.getKey().substring(prefix.length())));
//                if (center.distSqr(home) > 100 * 100) {
//                    continue;
//                }
//                UUID candidate = UUID.fromString(entry.getValue());
//                if (!candidate.equals(householdId)
//                        && HouseholdSavedData.get(level).getHousehold(candidate) != null
//                        && parcelCount(candidate) > 1
//                        && farms.food(candidate) >= 16) {
//                    parcel = entry.getKey();
//                    seller = candidate;
//                    break;
//                }
//            }
//        }
//        if (parcel != null && farms.spendCoins(householdId, PRICE)) {
//            owners.put(parcel, householdId.toString());
//            if (seller != null) {
//                farms.receiveCoins(seller, PRICE);
//            }
//            setDirty();
//        }
//    }
//
//    public void releaseHousehold(UUID householdId) {
//        String owner = householdId.toString();
//        boolean changed = false;
//        for (Map.Entry<String, String> entry : owners.entrySet()) {
//            if (entry.getValue().equals(owner)) {
//                entry.setValue("");
//                changed = true;
//            }
//        }
//        if (changed) {
//            setDirty();
//        }
//    }
//
//    private String nearestAvailable(ServerLevel level, BlockPos home) {
//        String prefix = level.dimension().identifier() + "|";
//        String best = null;
//        double bestDistance = 100 * 100;
//        for (Map.Entry<String, String> entry : owners.entrySet()) {
//            if (!entry.getKey().startsWith(prefix) || !entry.getValue().isBlank()) {
//                continue;
//            }
//            BlockPos center = BlockPos.of(Long.parseLong(entry.getKey().substring(prefix.length())));
//            double distance = center.distSqr(home);
//            if (distance < bestDistance) {
//                best = entry.getKey();
//                bestDistance = distance;
//            }
//        }
//        return best;
//    }
//
//    private static String key(ServerLevel level, BlockPos center) {
//        return level.dimension().identifier() + "|" + center.asLong();
//    }
//
//    private static LandSavedData fromSerialized(Map<String, String> records) {
//        LandSavedData data = new LandSavedData();
//        for (Map.Entry<String, String> entry : records.entrySet()) {
//            int separator = entry.getKey().lastIndexOf('|');
//            if (separator <= 0) {
//                continue;
//            }
//            try {
//                Long.parseLong(entry.getKey().substring(separator + 1));
//                if (!entry.getValue().isBlank()) {
//                    UUID.fromString(entry.getValue());
//                }
//                data.owners.put(entry.getKey(), entry.getValue());
//            } catch (IllegalArgumentException ignored) {
//                // A malformed deed is not allowed to claim a farm parcel.
//            }
//        }
//        return data;
//    }
//
//    public record ParcelInfo(BlockPos center, UUID owner) {}
//}
