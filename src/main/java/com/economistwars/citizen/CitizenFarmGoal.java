package com.economistwars.citizen;

import com.economistwars.household.HouseholdFarmSavedData;
import com.economistwars.household.HouseholdSavedData;
import com.economistwars.household.LandSavedData;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Produces food abstractly while working inside a farm plot owned by the household. */
final class CitizenFarmGoal extends CitizenProductionGoal {
    private BlockPos target;
    private BlockPos teleportPoint;
    private BlockPos lastTeleportPoint;
    private UUID householdId;
    private int searchCooldown;

    CitizenFarmGoal(CitizenEntity citizen) {
        super(citizen);
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override public CitizenDecisionPlanner.Action decisionAction() { return CitizenDecisionPlanner.Action.FARM; }
    @Override protected List<CitizenDecisionPlanner.OutputOutcome> productionOutcomes() {
        return List.of(new CitizenDecisionPlanner.OutputOutcome(Items.WHEAT, 2, 1.0, true));
    }
    @Override protected CitizenSkill productionSkill() { return CitizenSkill.FARMING; }
    @Override protected int baseWorkTicks() { return 12; }
    @Override protected int scoreTravelTicks() {
        return teleportPoint == null ? 0 : CitizenDecisionPlanner.travelTicks(citizen.distanceToSqr(
                teleportPoint.getX() + 0.5, teleportPoint.getY(), teleportPoint.getZ() + 0.5));
    }
    @Override protected int durationTravelTicks() {
        if (teleportPoint == null) return 0;
        double distance = citizen.distanceToSqr(teleportPoint.getX() + 0.5, teleportPoint.getY(), teleportPoint.getZ() + 0.5);
        return distance <= 2.25 ? 0 : CitizenDecisionPlanner.travelTicks(distance);
    }
    @Override protected boolean scheduledWork() {
        return citizen.level() instanceof ServerLevel level
                && CitizenWorkSchedule.workFor(level) == CitizenWorkSchedule.Work.FARM;
    }
    @Override protected List<com.economistwars.network.CitizenProfilePayload.DecisionDetail> outcomeDetails(Evaluation evaluation) {
        return List.of(new com.economistwars.network.CitizenProfilePayload.DecisionDetail(
                "Expected output", "2 wheat; net utility " + String.format(java.util.Locale.ROOT, "%.4f", evaluation.outputUtility())));
    }

    @Override public boolean canUse() {
        if (!(citizen.level() instanceof ServerLevel level) || citizen.isTeleporting()
                || CitizenWorkSchedule.isNight(level) || !level.isBrightOutside()) return false;
        if (searchCooldown > 0) {
            searchCooldown = Math.max(0, searchCooldown - CitizenDecisionPlanner.RESCORE_TICKS);
            return false;
        }
        searchCooldown = 100 + citizen.getRandom().nextInt(40);
        if (!citizen.canCarryWheat(2)) {
            if (citizen.hasCarriedItems()) citizen.requestWorkProductDelivery();
            return false;
        }
        householdId = citizen.householdId().orElse(null);
        if (householdId == null || !HouseholdSavedData.get(level).hasMember(householdId, citizen.citizenId())) return false;
        return selectOwnedPlot(level);
    }

    @Override public boolean canContinueToUse() {
        return target != null && citizen.level() instanceof ServerLevel level
                && !CitizenWorkSchedule.isNight(level) && !citizen.workProductDeliveryRequested()
                && level.isLoaded(target) && ownsTarget(level) && citizen.canCarryWheat(2)
                && (citizen.isTeleporting() || teleportPoint != null || isInsidePlot(level));
    }

    @Override public void start() {
        startProductionWork();
        citizen.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HOE));
        if (teleportPoint != null && citizen.distanceToSqr(teleportPoint.getX() + 0.5,
                teleportPoint.getY(), teleportPoint.getZ() + 0.5) > 2.25) {
            citizen.beginTeleport((ServerLevel) citizen.level(), teleportPoint, "farm");
        }
    }

    @Override public void tick() {
        if (!(citizen.level() instanceof ServerLevel level) || target == null) return;
        if (!isInsidePlot(level)) {
            if (!citizen.isTeleporting() && teleportPoint != null) citizen.beginTeleport(level, teleportPoint, "farm");
            return;
        }
        if (!tickProductionWork(InteractionHand.MAIN_HAND)) return;
        if (!ownsTarget(level) || CitizenWorkSchedule.isNight(level)
                || !HouseholdSavedData.get(level).hasMember(householdId, citizen.citizenId())
                || !citizen.canCarryWheat(2)) {
            if (!citizen.canCarryWheat(2)) citizen.requestWorkProductDelivery();
            return;
        }
        citizen.carryWheat(2);
        citizen.setInventoryWork(CitizenWorkSchedule.Work.FARM);
        citizen.addSkillExperience(CitizenSkill.FARMING, 5);
        resetProductionWork();
        citizen.restartDecisionProgress(estimatedDurationTicks());
        if (!citizen.canCarryWheat(2)) citizen.requestWorkProductDelivery();
    }

    @Override public void stop() {
        citizen.cancelTeleport();
        if (!citizen.hasCarriedItems()) citizen.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        if (teleportPoint != null) lastTeleportPoint = teleportPoint;
        target = null;
        teleportPoint = null;
        householdId = null;
        resetProductionWork();
    }

    private boolean ownsTarget(ServerLevel level) {
        return householdId != null && (HouseholdFarmSavedData.get(level).ownsCrop(level, householdId, target)
                || LandSavedData.get(level).ownsCrop(level, householdId, target));
    }

    private boolean isInsidePlot(ServerLevel level) {
        BlockPos citizenPos = citizen.blockPosition();
        ArrayList<BlockPos> plotCells = new ArrayList<>(HouseholdFarmSavedData.get(level).crops(level, householdId));
        plotCells.addAll(LandSavedData.get(level).ownedCrops(level, householdId));
        for (BlockPos cell : plotCells) {
            if (Math.abs(citizenPos.getX() - cell.getX()) <= 3
                    && Math.abs(citizenPos.getZ() - cell.getZ()) <= 3
                    && Math.abs(citizenPos.getY() - cell.getY()) <= 2) return true;
        }
        return false;
    }

    private boolean selectOwnedPlot(ServerLevel level) {
        target = null;
        teleportPoint = null;
        if (householdId == null) return false;
        ArrayList<BlockPos> plotCells = new ArrayList<>(HouseholdFarmSavedData.get(level).crops(level, householdId));
        plotCells.addAll(LandSavedData.get(level).ownedCrops(level, householdId));
        double nearest = Double.MAX_VALUE;
        BlockPos nearestCell = null;
        for (BlockPos cell : plotCells) {
            double distance = citizen.distanceToSqr(cell.getX() + 0.5, cell.getY() + 0.5, cell.getZ() + 0.5);
            if (distance >= nearest) continue;
            nearestCell = cell;
            nearest = distance;
        }
        if (nearestCell == null) return false;
        BlockPos selectedCell = nearestCell;
        level.getChunkAt(selectedCell);
        BlockPos safePoint = CitizenTeleportPoints.randomSafePosition(level, citizen, selectedCell,
                3, 0, 18, lastTeleportPoint, candidate ->
                        Math.abs(candidate.getX() - selectedCell.getX()) <= 3
                                && Math.abs(candidate.getZ() - selectedCell.getZ()) <= 3);
        if (safePoint == null) return false;
        target = selectedCell;
        teleportPoint = safePoint;
        return true;
    }
}
