package com.economistwars.ownership;

import com.economistwars.ownership.assetsReferences.AssetsReference;
import com.economistwars.ownership.assetsReferences.ItemAssetsReference;
import com.economistwars.ownership.assetsReferences.LandAssetsReference;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

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

        if (getAssetReference() instanceof ItemAssetsReference item) {
            return item.getItemUUID() == null ? null : owner + "|item|" + item.getItemUUID();
        }
        if (getAssetReference() instanceof LandAssetsReference land) {
            BoundingBox bounds = land.getLandBoundingBox();
            if (bounds == null) {
                return null;
            }
            return owner + "|land|"
                    + bounds.minX() + "," + bounds.minY() + "," + bounds.minZ() + ","
                    + bounds.maxX() + "," + bounds.maxY() + "," + bounds.maxZ();
        }
        return null;
    }

    public static Ownership deserializeOwnership(String serialized) {
        String[] fields = serialized.split("\\|", 3);
        if (fields.length != 3) {
            return null;
        }

        try {
            UUID owner = UUID.fromString(fields[0]);
            return switch (fields[1]) {
                case "item" -> new Ownership(owner, new ItemAssetsReference(UUID.fromString(fields[2])));
                case "land" -> new Ownership(owner, new LandAssetsReference(LandAssetsReference.parseBoundingBox(fields[2])));
                default -> null;
            };
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
