package com.economistwars.ownership.assetsReferences;

import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class LandAssetsReference extends AssetsReference {
    private final BoundingBox LandBoundingBox;

    public LandAssetsReference(BoundingBox landBoundingBox) {
        LandBoundingBox = landBoundingBox;
    }

    public BoundingBox getLandBoundingBox() {
        return LandBoundingBox;
    }

    @Override
    public String getType() {
        return "land";
    }

    public static BoundingBox parseBoundingBox(String serialized) {
        String[] coordinates = serialized.split(",", -1);
        if (coordinates.length != 6) {
            throw new IllegalArgumentException("A land ownership must contain six coordinates");
        }
        return new BoundingBox(
                Integer.parseInt(coordinates[0]),
                Integer.parseInt(coordinates[1]),
                Integer.parseInt(coordinates[2]),
                Integer.parseInt(coordinates[3]),
                Integer.parseInt(coordinates[4]),
                Integer.parseInt(coordinates[5])
        );
    }

    @Override
    public AssetsReference FromString(String serialized) {
        return new LandAssetsReference(parseBoundingBox(serialized));
    }
}
