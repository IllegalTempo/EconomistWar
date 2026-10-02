package com.economistwars.ownership.assetsReferences;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.Identifier;

public class LandAssetsReference extends AssetsReference {
    private final BoundingBox LandBoundingBox;
    private final String dimension;

    public LandAssetsReference(BoundingBox landBoundingBox) {
        this(null, landBoundingBox);
    }

    public LandAssetsReference(String dimension, BoundingBox landBoundingBox) {
        if (dimension != null && Identifier.tryParse(dimension) == null)
            throw new IllegalArgumentException("Invalid land dimension");
        this.dimension = dimension;
        LandBoundingBox = landBoundingBox;
    }

    public boolean matchesDimension(String requested) {
        return requested == null || requested.equals(dimension == null ? "minecraft:overworld" : dimension);
    }

    public BoundingBox getLandBoundingBox() {
        return LandBoundingBox;
    }

    /** Includes the space where a worker stands above a ground-level parcel. */
    public boolean containsWorkPosition(BlockPos position) {
        BoundingBox bounds = LandBoundingBox;
        return bounds != null && position != null
                && position.getX() >= bounds.minX() && position.getX() <= bounds.maxX()
                && position.getZ() >= bounds.minZ() && position.getZ() <= bounds.maxZ()
                && position.getY() >= bounds.minY() && (long) position.getY() <= (long) bounds.maxY() + 2;
    }

    public BlockPos nearestGroundPosition(Vec3 position) {
        BoundingBox bounds = LandBoundingBox;
        if (bounds == null) return null;
        return new BlockPos(
                Mth.clamp(Mth.floor(position.x), bounds.minX(), bounds.maxX()),
                Mth.clamp(Mth.floor(position.y), bounds.minY(), bounds.maxY()),
                Mth.clamp(Mth.floor(position.z), bounds.minZ(), bounds.maxZ()));
    }

    @Override
    public String getType() {
        return "land";
    }

    @Override
    public String serializePayload() {
        BoundingBox bounds = LandBoundingBox;
        if (bounds == null) {
            return null;
        }
        return (dimension == null ? "" : dimension + "|") + bounds.minX() + "," + bounds.minY() + "," + bounds.minZ() + ","
                + bounds.maxX() + "," + bounds.maxY() + "," + bounds.maxZ();
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

    public AssetsReference FromString(String serialized) {
        return parsePayload(serialized);
    }

    public static LandAssetsReference parsePayload(String serialized) {
        int separator = serialized.indexOf('|');
        return separator < 0 ? new LandAssetsReference(parseBoundingBox(serialized))
                : new LandAssetsReference(serialized.substring(0, separator),
                        parseBoundingBox(serialized.substring(separator + 1)));
    }
}
