package com.economistwars.ownership;

import com.economistwars.ownership.assetsReferences.LandAssetsReference;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import com.economistwars.household.HouseholdSavedData;

public class OwnershipSavedData extends SavedData {
    private static final Codec<Map<String, List<String>>> RECORDS_CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf());
    private static final Codec<OwnershipSavedData> CODEC = new Codec<>() {
        @Override
        public <T> com.mojang.serialization.DataResult<T> encode(OwnershipSavedData data,
                com.mojang.serialization.DynamicOps<T> ops, T prefix) {
            return RECORDS_CODEC.encode(data.toSerialized(ops), ops, prefix);
        }

        @Override
        public <T> com.mojang.serialization.DataResult<com.mojang.datafixers.util.Pair<OwnershipSavedData, T>> decode(
                com.mojang.serialization.DynamicOps<T> ops, T input) {
            return RECORDS_CODEC.decode(ops, input).map(result -> result.mapFirst(records -> fromSerialized(records, ops)));
        }
    };
    private static final SavedDataType<OwnershipSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("economistwars", "economistwars_ownership"),
            OwnershipSavedData::new, CODEC, DataFixTypes.LEVEL
    );

    private final Map<UUID, HouseholdOwnerships> ownerships = new LinkedHashMap<>();
    private final Set<String> farmParcels = new LinkedHashSet<>();
    private final Set<String> cropClaims = new LinkedHashSet<>();
    public static OwnershipSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }
    public void claimCrop(ResourceKey<Level> dimension, UUID owner, BlockPos pos) {
        String suffix = "|" + dimension.identifier() + "|" + pos.asLong();
        if (cropClaims.stream().noneMatch(s -> s.endsWith(suffix)) && cropClaims.add(owner + suffix)) setDirty();
    }
    public boolean ownsFarmPosition(ResourceKey<Level> dimension, UUID owner, BlockPos pos) {
        if (parcelAt(dimension, pos).map(p -> owner.equals(p.owner())).orElse(false)) return true;
        for (int dy = 0; dy <= 2; dy++) if (cropClaims.contains(owner + "|" + dimension.identifier() + "|" + pos.below(dy).asLong())) return true;
        return false;
    }
    public int farmParcelCount(ResourceKey<Level> dimension, UUID owner) {
        return (int)farmCenters(dimension).stream().filter(p -> owner.equals(ownerAt(dimension,p))).count();
    }
    private List<BlockPos> farmCenters(ResourceKey<Level> dimension) {
        String prefix = dimension.identifier() + "|";
        return farmParcels.stream().filter(s -> s.startsWith(prefix)).map(s -> BlockPos.of(Long.parseLong(s.substring(prefix.length())))).toList();
    }
    public Optional<BlockPos> nearestFarmPosition(ResourceKey<Level> dimension, UUID owner, Vec3 position) {
        return farmPositions(dimension,owner).stream().min(java.util.Comparator.comparingDouble(p -> position.distanceToSqr(p.getX()+0.5,p.getY(),p.getZ()+0.5)));
    }
    public List<BlockPos> farmPositions(ResourceKey<Level> dimension, UUID owner) {
        List<BlockPos> positions = new ArrayList<>(farmCenters(dimension).stream().filter(p -> owner.equals(ownerAt(dimension,p))).toList());
        String prefix = owner + "|" + dimension.identifier() + "|";
        cropClaims.stream().filter(s -> s.startsWith(prefix)).map(s -> BlockPos.of(Long.parseLong(s.substring(prefix.length())))).forEach(positions::add);
        return List.copyOf(positions);
    }
    private void assignParcel(ResourceKey<Level> dimension, UUID owner, BlockPos center) {
        ownerships.computeIfAbsent(owner, ignored -> new HouseholdOwnerships()).addOwnership(new Ownership(owner,
                new LandAssetsReference(dimension.identifier().toString(), new BoundingBox(center.getX()-3,center.getY(),center.getZ()-3,center.getX()+3,center.getY(),center.getZ()+3)))); setDirty();
    }
    public void tickHousehold(ServerLevel level, com.economistwars.household.Household buyer, long day) {
        BlockPos home = buyer.home(level.dimension()).orElse(null);
        if (home == null) return;
        int owned = farmParcelCount(level.dimension(), buyer.id());
        if (owned == 0) { grantStarter(level.dimension(),buyer.id(),home); return; }
        if (!buyer.beginMarketDay(day)) return;
        if (!buyer.canBuyFarm(owned)) return;
        var households = HouseholdSavedData.get(level);
        BlockPos target = farmCenters(level.dimension()).stream().filter(p -> p.distSqr(home) <= 10000)
                .filter(p -> { UUID sellerId = ownerAt(level.dimension(),p); if (sellerId == null) return owned == 1;
                    if (sellerId.equals(buyer.id())) return false;
                    var seller = households.getHousehold(sellerId); return seller != null && seller.foodCount() >= 16 && farmParcelCount(level.dimension(),sellerId)>1;
                }).min(java.util.Comparator.<BlockPos>comparingInt(p -> ownerAt(level.dimension(),p) == null ? 0 : 1).thenComparingDouble(p -> p.distSqr(home))).orElse(null);
        if (target == null) return;
        UUID sellerId = ownerAt(level.dimension(),target);
        buyFarmParcel(level.dimension(), buyer, sellerId == null ? null : households.getHousehold(sellerId), target);
    }
    public boolean buyFarmParcel(ResourceKey<Level> dimension, com.economistwars.household.Household buyer,
                                 com.economistwars.household.Household seller, BlockPos center) {
        UUID owner = ownerAt(dimension, center);
        if (!farmCenters(dimension).contains(center) || !buyer.canBuyFarm(farmParcelCount(dimension, buyer.id()))) return false;
        if (owner != null && (seller == null || !owner.equals(seller.id()) || seller == buyer
                || farmParcelCount(dimension, seller.id()) <= 1 || seller.foodCount() < 16)) return false;
        if (owner == null && seller != null) return false;
        if (!buyer.spendCoins(60)) return false;
        if (seller != null) {
            seller.receiveCoins(60);
            var group = ownerships.get(seller.id());
            for (Ownership deed : group.getOwnerships(LandAssetsReference.class)) {
                LandAssetsReference land = (LandAssetsReference)deed.getAssetReference();
                if (land.matchesDimension(dimension.identifier().toString()) && land.containsWorkPosition(center)) {
                    group.removeOwnership(deed); break;
                }
            }
        }
        assignParcel(dimension, buyer.id(), center); return true;
    }

    public boolean ownsLandPosition(UUID householdId, BlockPos position) {
        HouseholdOwnerships household = ownerships.get(householdId);
        return household != null && household.ownsLandPosition(householdId, position);
    }

    public Optional<BlockPos> nearestLandPosition(UUID householdId, Vec3 position) {
        HouseholdOwnerships household = ownerships.get(householdId);
        return household == null ? Optional.empty() : household.nearestLandPosition(householdId, position);
    }

    public boolean ownsLandPosition(ResourceKey<Level> dimension, UUID householdId, BlockPos position) {
        HouseholdOwnerships household = ownerships.get(householdId);
        return household != null && household.ownsLandPosition(householdId, position, dimension.identifier().toString());
    }

    public int landParcelCount(ResourceKey<Level> dimension, UUID householdId) {
        HouseholdOwnerships household = ownerships.get(householdId);
        return household == null ? 0 : (int) java.util.Arrays.stream(household.getOwnerships(LandAssetsReference.class))
                .filter(ownership -> householdId.equals(ownership.getOwner()))
                .filter(ownership -> ((LandAssetsReference) ownership.getAssetReference())
                        .matchesDimension(dimension.identifier().toString())).count();
    }

    public Optional<BlockPos> nearestLandPosition(ResourceKey<Level> dimension, UUID householdId, Vec3 position) {
        HouseholdOwnerships household = ownerships.get(householdId);
        return household == null ? Optional.empty()
                : household.nearestLandPosition(householdId, position, dimension.identifier().toString());
    }

    public void registerParcel(ServerLevel level, BlockPos center) {
        registerParcel(level.dimension(), center);
        HouseholdSavedData.get(level).households().forEach(household -> household.home(level.dimension())
                .ifPresent(home -> grantStarter(level.dimension(), household.id(), home)));
    }

    public void registerParcel(ResourceKey<Level> dimension, BlockPos center) {
        if (farmParcels.add(dimension.identifier() + "|" + center.asLong())) setDirty();
    }

    public void grantStarter(ResourceKey<Level> dimension, UUID householdId, BlockPos home) {
        if (farmParcelCount(dimension, householdId) > 0) return;
        String prefix = dimension.identifier() + "|";
        BlockPos nearest = farmParcels.stream().filter(key -> key.startsWith(prefix))
                .map(key -> BlockPos.of(Long.parseLong(key.substring(prefix.length()))))
                .filter(center -> center.distSqr(home) <= 100 * 100 && ownerAt(dimension, center) == null)
                .min(java.util.Comparator.comparingDouble(center -> center.distSqr(home))).orElse(null);
        if (nearest == null) return;
        ownerships.computeIfAbsent(householdId, ignored -> new HouseholdOwnerships()).addOwnership(new Ownership(
                householdId, new LandAssetsReference(dimension.identifier().toString(), new BoundingBox(
                        nearest.getX() - 3, nearest.getY(), nearest.getZ() - 3,
                        nearest.getX() + 3, nearest.getY(), nearest.getZ() + 3))));
        setDirty();
    }

    private UUID ownerAt(ResourceKey<Level> dimension, BlockPos position) {
        return ownerships.keySet().stream().filter(id -> ownsLandPosition(dimension, id, position)).findFirst().orElse(null);
    }

    public record ParcelInfo(BlockPos center, UUID owner) {}

    public Optional<ParcelInfo> parcelAt(ResourceKey<Level> dimension, BlockPos position) {
        String prefix = dimension.identifier() + "|";
        return farmParcels.stream().filter(key -> key.startsWith(prefix))
                .map(key -> BlockPos.of(Long.parseLong(key.substring(prefix.length()))))
                .filter(center -> Math.abs(position.getX() - center.getX()) <= 3
                        && Math.abs(position.getZ() - center.getZ()) <= 3
                        && Math.abs(position.getY() - center.getY()) <= 2)
                .map(center -> new ParcelInfo(center, ownerAt(dimension, center))).findFirst();
    }

    public void releaseHousehold(UUID householdId) {
        boolean claimsRemoved = cropClaims.removeIf(s -> s.startsWith(householdId + "|"));
        if (ownerships.remove(householdId) != null || claimsRemoved) setDirty();
    }

    private Map<String, List<String>> toSerialized(com.mojang.serialization.DynamicOps<?> ops) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("crop_claims", List.copyOf(cropClaims));
        if (!farmParcels.isEmpty()) result.put("farm_parcels", List.copyOf(farmParcels));
        for (Map.Entry<UUID, HouseholdOwnerships> entry : ownerships.entrySet()) {
            List<String> serializedOwnerships = new ArrayList<>();
            for (Ownership ownership : entry.getValue().getAllOwnerships()) {
                String serialized = ownership.serializeOwnership(ops);
                if (serialized != null) {
                    serializedOwnerships.add(serialized);
                }
            }
            result.put(entry.getKey().toString(), serializedOwnerships);
        }
        return result;
    }

    private static OwnershipSavedData fromSerialized(Map<String, List<String>> records, com.mojang.serialization.DynamicOps<?> ops) {
        OwnershipSavedData data = new OwnershipSavedData();
        for (String claim : records.getOrDefault("crop_claims", List.of())) { try { String[] f = claim.split("\\|",-1); if (f.length == 3 && Identifier.tryParse(f[1]) != null) { UUID.fromString(f[0]); Long.parseLong(f[2]); data.cropClaims.add(claim); } } catch (IllegalArgumentException ignored) {} }
        for (String parcel : records.getOrDefault("farm_parcels", List.of())) {
            String[] fields = parcel.split("\\|", -1);
            if (fields.length != 2 || Identifier.tryParse(fields[0]) == null) continue;
            try {
                data.farmParcels.add(Identifier.parse(fields[0]) + "|" + Long.parseLong(fields[1]));
            } catch (NumberFormatException ignored) {
                // Ignore malformed parcel positions.
            }
        }
        for (Map.Entry<String, List<String>> entry : records.entrySet()) {
            UUID householdId;
            try {
                householdId = UUID.fromString(entry.getKey());
            } catch (IllegalArgumentException exception) {
                continue;
            }

            HouseholdOwnerships householdOwnerships = new HouseholdOwnerships();
            for (String serialized : entry.getValue()) {
                Ownership ownership = Ownership.deserializeOwnership(serialized, ops);
                if (ownership != null) {
                    householdOwnerships.addOwnership(ownership);
                }
            }
            if (householdOwnerships.GetTotalOwnershipsCount() > 0) {
                data.ownerships.put(householdId, householdOwnerships);
            }
        }
        return data;
    }




}
