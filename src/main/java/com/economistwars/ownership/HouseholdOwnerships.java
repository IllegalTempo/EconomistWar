package com.economistwars.ownership;

import com.economistwars.ownership.assetsReferences.AssetsReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
