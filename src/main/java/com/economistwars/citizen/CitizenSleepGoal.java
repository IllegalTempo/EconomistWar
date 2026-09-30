package com.economistwars.citizen;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import com.economistwars.network.CitizenProfilePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/** Sends each assigned citizen home to sleep in an unoccupied household bed at night. */
final class CitizenSleepGoal extends Goal implements CitizenDecisionAction {
    private final CitizenEntity citizen;
    private BlockPos home;
    private BlockPos bed;
    private BlockPos arrivalPoint;
    private BlockPos previousArrivalPoint;
    private UUID householdId;

    CitizenSleepGoal(CitizenEntity citizen) {
        this.citizen = citizen;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override public CitizenDecisionPlanner.Action decisionAction() { return CitizenDecisionPlanner.Action.SLEEP; }
    @Override public int estimatedDurationTicks() {
        if (!(citizen.level() instanceof ServerLevel level)) return 1;
        long timeOfDay = Math.floorMod(level.getOverworldClockTime(), 24_000L);
        return Math.max(1, (int) (23_000L - timeOfDay));
    }
    @Override public double decisionScore() { return CitizenDecisionPlanner.URGENT_SCORE * 2.0; }
    @Override public List<CitizenProfilePayload.DecisionDetail> decisionDetails() {
        return List.of(
                new CitizenProfilePayload.DecisionDetail("Score rule", "Fixed sleep priority override"),
                new CitizenProfilePayload.DecisionDetail("Priority score", "900 × 2 = 1800.0000 utility/tick"),
                new CitizenProfilePayload.DecisionDetail("Remaining night", estimatedDurationTicks() + " ticks"),
                new CitizenProfilePayload.DecisionDetail("Bed available", Boolean.toString(bed != null))
        );
    }

    @Override
    public boolean canUse() {
        if (!(citizen.level() instanceof ServerLevel level) || citizen.isTeleporting() || citizen.isSleeping()
                || !CitizenWorkSchedule.isNight(level)) return false;
        householdId = citizen.householdId().orElse(null);
        if (householdId == null) return false;
        home = com.economistwars.household.HouseholdFarmSavedData.get(level).home(level, householdId).orElse(null);
        if (home == null) return false;
        level.getChunkAt(home);
        bed = findAvailableBed(level, home);
        if (bed == null) return false;
        arrivalPoint = CitizenTeleportPoints.randomSafePosition(level, citizen, bed, 4, 1,
                Double.MAX_VALUE, previousArrivalPoint, candidate -> true);
        if (arrivalPoint == null) {
            bed = null;
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return citizen.level() instanceof ServerLevel level && CitizenWorkSchedule.isNight(level)
                && home != null && bed != null;
    }

    @Override
    public void start() {
        ServerLevel level = (ServerLevel) citizen.level();
        reserveBed(level);
        if (citizen.distanceToSqr(arrivalPoint.getX() + 0.5, arrivalPoint.getY(), arrivalPoint.getZ() + 0.5) > 2.25) {
            citizen.beginTeleport(level, arrivalPoint, "home");
        } else {
            citizen.startSleeping(sleepPosition(level, bed));
        }
    }

    @Override
    public void tick() {
        if (!(citizen.level() instanceof ServerLevel level) || citizen.isTeleporting() || citizen.isSleeping()) return;
        if (!isValidBed(level, bed)) {
            bed = findAvailableBed(level, home);
            if (bed == null) return;
            arrivalPoint = CitizenTeleportPoints.randomSafePosition(level, citizen, bed, 4, 1,
                    Double.MAX_VALUE, previousArrivalPoint, candidate -> true);
            if (arrivalPoint == null) return;
            reserveBed(level);
        }
        if (citizen.distanceToSqr(arrivalPoint.getX() + 0.5, arrivalPoint.getY(), arrivalPoint.getZ() + 0.5) > 2.25) {
            citizen.beginTeleport(level, arrivalPoint, "home");
        } else {
            citizen.startSleeping(sleepPosition(level, bed));
        }
    }

    @Override
    public void stop() {
        if (citizen.isSleeping()) {
            citizen.stopSleeping();
        } else if (citizen.level() instanceof ServerLevel level && bed != null && isValidBed(level, bed)) {
            BlockState state = level.getBlockState(bed);
            level.setBlockAndUpdate(bed, state.setValue(BedBlock.OCCUPIED, false));
        }
        if (!citizen.isSleeping()) citizen.cancelTeleport();
        previousArrivalPoint = arrivalPoint;
        home = null;
        bed = null;
        arrivalPoint = null;
        householdId = null;
    }

    private void reserveBed(ServerLevel level) {
        BlockState state = level.getBlockState(bed);
        if (isValidBed(level, bed) && !state.getValue(BedBlock.OCCUPIED)) {
            level.setBlockAndUpdate(bed, state.setValue(BedBlock.OCCUPIED, true));
        }
    }

    private static BlockPos findAvailableBed(ServerLevel level, BlockPos home) {
        for (BlockPos position : BlockPos.betweenClosed(home.offset(-5, -2, -5), home.offset(5, 3, 5))) {
            if (isValidBed(level, position) && !level.getBlockState(position).getValue(BedBlock.OCCUPIED)) {
                return position.immutable();
            }
        }
        return null;
    }

    private static boolean isValidBed(ServerLevel level, BlockPos position) {
        BlockState state = level.getBlockState(position);
        return state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.FOOT;
    }

    private static BlockPos sleepPosition(ServerLevel level, BlockPos foot) {
        Direction facing = level.getBlockState(foot).getValue(BedBlock.FACING);
        return foot.relative(facing);
    }
}
