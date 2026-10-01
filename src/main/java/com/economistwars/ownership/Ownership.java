package com.economistwars.ownership;

import com.economistwars.ownership.assetsReferences.AssetsReference;
import com.economistwars.ownership.assetsReferences.AssetsReferenceRegistry;

import java.util.UUID;

///
/// Ownerships should be transferable between households.
///
public class Ownership {

    private UUID owner;
    private final AssetsReference assetReference;

    public Ownership(AssetsReference assetReference) {
        this.assetReference = assetReference;
        owner = null;
    }
    public Ownership(UUID owner, AssetsReference assetReference) {
        this.owner = owner;
        this.assetReference = assetReference;
    }
    public UUID getOwner() {
        return owner;
    }
    public void setOwner(UUID owner) {
        this.owner = owner;
    }
    public AssetsReference getAssetReference() {
        return assetReference;
    }
    public final String ToSerialize()
    {
        return owner.toString() + ":" + assetReference.toString();
    }
    public String serializeOwnership() {
        UUID owner = getOwner();
        if (owner == null || getAssetReference() == null) {
            return null;
        }

        String payload = assetReference.serializePayload();
        return payload == null ? null : owner + "|" + assetReference.getType() + "|" + payload;
    }

    public static Ownership deserializeOwnership(String serialized) {
        String[] fields = serialized.split("\\|", 3);
        if (fields.length != 3) {
            return null;
        }

        try {
            UUID owner = UUID.fromString(fields[0]);
            AssetsReference reference = AssetsReferenceRegistry.deserialize(fields[1], fields[2]);
            return reference == null ? null : new Ownership(owner, reference);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
