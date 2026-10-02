package com.economistwars.ownership;

import com.economistwars.ownership.assetsReferences.AssetsReference;
import com.economistwars.ownership.assetsReferences.LandAssetsReference;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class HouseholdOwnerships {
    private final HashMap<Class<? extends AssetsReference>, Ownership[]> ownerships = new HashMap<>();
    public final void addOwnership(Ownership ownership) {
        Class<? extends AssetsReference> assetClass = ownership.getAssetReference().getClass();
        Ownership[] existingOwnerships = ownerships.getOrDefault(assetClass, new Ownership[0]);
        Ownership[] newOwnerships = new Ownership[existingOwnerships.length + 1];
        System.arraycopy(existingOwnerships, 0, newOwnerships, 0, existingOwnerships.length);
        newOwnerships[existingOwnerships.length] = ownership;
        ownerships.put(assetClass, newOwnerships);
    }
    public final void removeOwnership(Ownership ownership) {
        Class<? extends AssetsReference> assetClass = ownership.getAssetReference().getClass();
        Ownership[] existingOwnerships = ownerships.getOrDefault(assetClass, new Ownership[0]);
        Ownership[] newOwnerships = new Ownership[existingOwnerships.length - 1];
        int index = 0;
        for (Ownership existingOwnership : existingOwnerships) {
            if (!existingOwnership.equals(ownership)) {
                newOwnerships[index++] = existingOwnership;
            }
        }
        ownerships.put(assetClass, newOwnerships);
    }
    public final Ownership[] getOwnerships(Class<? extends AssetsReference> assetClass) {
        return ownerships.getOrDefault(assetClass, new Ownership[0]);
    }

    public boolean ownsLandPosition(UUID owner, BlockPos position) {
        return ownsLandPosition(owner, position, null);
    }

    public boolean ownsLandPosition(UUID owner, BlockPos position, String dimension) {
        if (owner == null || position == null) return false;
        return Arrays.stream(getOwnerships(LandAssetsReference.class))
                .filter(ownership -> owner.equals(ownership.getOwner()))
                .filter(ownership -> ((LandAssetsReference) ownership.getAssetReference()).matchesDimension(dimension))
                .anyMatch(ownership -> ((LandAssetsReference) ownership.getAssetReference())
                        .containsWorkPosition(position));
    }

    public Optional<BlockPos> nearestLandPosition(UUID owner, Vec3 position) {
        return nearestLandPosition(owner, position, null);
    }

    public Optional<BlockPos> nearestLandPosition(UUID owner, Vec3 position, String dimension) {
        if (owner == null) return Optional.empty();
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Ownership ownership : getOwnerships(LandAssetsReference.class)) {
            if (!owner.equals(ownership.getOwner())) continue;
            if (!((LandAssetsReference) ownership.getAssetReference()).matchesDimension(dimension)) continue;
            BlockPos candidate = ((LandAssetsReference) ownership.getAssetReference()).nearestGroundPosition(position);
            if (candidate == null) continue;
            double distance = position.distanceToSqr(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }
    public final int GetTotalOwnershipsCount() {
        int totalCount = 0;
        for (Ownership[] ownershipArray : ownerships.values()) {
            totalCount += ownershipArray.length;
        }
        return totalCount;
    }
    public final int GetOwnershipsCount(Class<? extends AssetsReference> assetClass) {
        return ownerships.getOrDefault(assetClass, new Ownership[0]).length;
    }

    public final List<Ownership> getAllOwnerships() {
        return ownerships.values().stream()
                .flatMap(array -> Arrays.stream(array))
                .toList();
    }
}
