package com.economistwars.citizen;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/** Shared checks and random selection for safe teleport arrival points. */
final class CitizenTeleportPoints {
    private CitizenTeleportPoints() {}

    static boolean isSafe(ServerLevel level, CitizenEntity citizen, BlockPos feet) {
        BlockPos below = feet.below();
        if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()
                || !level.getFluidState(feet).isEmpty()
                || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return false;
        }
        AABB destinationBounds = citizen.getBoundingBox().move(
                feet.getX() + 0.5 - citizen.getX(),
                feet.getY() - citizen.getY(),
                feet.getZ() + 0.5 - citizen.getZ());
        return level.noCollision(citizen, destinationBounds);
    }

    static BlockPos randomSafePosition(ServerLevel level, CitizenEntity citizen, BlockPos center,
            int horizontalRadius, int verticalRadius, double maxDistanceSquared,
            BlockPos previous, Predicate<BlockPos> extraFilter) {
        BlockPos selected = null;
        BlockPos fallback = null;
        int validCount = 0;
        for (int x = center.getX() - horizontalRadius; x <= center.getX() + horizontalRadius; x++) {
            for (int z = center.getZ() - horizontalRadius; z <= center.getZ() + horizontalRadius; z++) {
                for (int y = center.getY() - verticalRadius; y <= center.getY() + verticalRadius; y++) {
                    BlockPos candidate = new BlockPos(x, y, z);
                    if (candidate.distToCenterSqr(center.getX() + 0.5, center.getY(), center.getZ() + 0.5)
                            > maxDistanceSquared
                            || !isSafe(level, citizen, candidate)
                            || !extraFilter.test(candidate)) continue;
                    if (fallback == null) fallback = candidate;
                    if (candidate.equals(previous)) continue;
                    if (citizen.getRandom().nextInt(++validCount) == 0) selected = candidate;
                }
            }
        }
        return selected != null ? selected : fallback;
    }
}
