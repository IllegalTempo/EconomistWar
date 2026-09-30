package com.economistwars.citizen;

import com.economistwars.household.HouseholdFarmSavedData;
import com.economistwars.household.HouseholdSavedData;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import com.economistwars.network.CitizenProfilePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;

/** Carries any citizen work products to the household's sealed store. */
final class CitizenStoreHarvestGoal extends Goal implements CitizenDecisionAction {
    private final CitizenEntity citizen;
    private BlockPos home;
    private BlockPos arrivalPoint;
    private BlockPos lastArrivalPoint;
    private UUID householdId;
    private int retryDelay;

    CitizenStoreHarvestGoal(CitizenEntity citizen) {
        this.citizen = citizen;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override public CitizenDecisionPlanner.Action decisionAction() { return CitizenDecisionPlanner.Action.DELIVERY; }
    @Override public int estimatedDurationTicks() {
        if (arrivalPoint == null) return 1;
        int travel = citizen.distanceToSqr(arrivalPoint.getX() + 0.5, arrivalPoint.getY(), arrivalPoint.getZ() + 0.5) <= 2.25
                ? 0 : CitizenDecisionPlanner.travelTicks(citizen.distanceToSqr(
                        arrivalPoint.getX() + 0.5, arrivalPoint.getY(), arrivalPoint.getZ() + 0.5));
        return travel + 1;
    }
    @Override public double decisionScore() {
        return CitizenDecisionPlanner.URGENT_SCORE;
    }
    @Override public List<CitizenProfilePayload.DecisionDetail> decisionDetails() {
        int carriedSlots = 0;
        for (int slot = 0; slot < 36; slot++) if (!citizen.inventoryItem(slot).isEmpty()) carriedSlots++;
        boolean full = citizen.inventoryFull();
        boolean requested = citizen.workProductDeliveryRequested();
        boolean workChanged = citizen.level() instanceof ServerLevel level
                && citizen.inventoryWork() != null
                && citizen.inventoryWork() != CitizenWorkSchedule.workFor(level);
        return List.of(
                new CitizenProfilePayload.DecisionDetail("Score rule", "Fixed requested-delivery priority override"),
                new CitizenProfilePayload.DecisionDetail("Priority score", "900.0000 utility/tick"),
                new CitizenProfilePayload.DecisionDetail("Delivery requested", Boolean.toString(requested)),
                new CitizenProfilePayload.DecisionDetail("Inventory full", Boolean.toString(full)),
                new CitizenProfilePayload.DecisionDetail("Work schedule changed", Boolean.toString(workChanged)),
                new CitizenProfilePayload.DecisionDetail("Carried item stacks", Integer.toString(carriedSlots)),
                new CitizenProfilePayload.DecisionDetail("Travel plus deposit", estimatedDurationTicks() + " ticks")
        );
    }

    @Override
    public boolean canUse() {
        if (!(citizen.level() instanceof ServerLevel level) || citizen.isTeleporting()
                || !citizen.hasCarriedItems()) {
            return false;
        }
        if (retryDelay > 0) {
            retryDelay = Math.max(0, retryDelay - CitizenDecisionPlanner.RESCORE_TICKS);
            return false;
        }
        if (!citizen.workProductDeliveryRequested()
                && !shouldDeliver(citizen, CitizenWorkSchedule.workFor(level))) {
            return false;
        }
        retryDelay = 20;
        householdId = citizen.householdId().orElse(null);
        if (householdId == null || !HouseholdSavedData.get(level).hasMember(householdId, citizen.citizenId())) {
            return false;
        }
        home = HouseholdFarmSavedData.get(level).home(level, householdId).orElse(null);
        if (home != null) {
            level.getChunkAt(home);
            arrivalPoint = CitizenTeleportPoints.randomSafePosition(level, citizen, home,
                    4, 1, Double.MAX_VALUE, lastArrivalPoint, candidate -> true);
        }
        return home != null && arrivalPoint != null;
    }

    @Override
    public boolean canContinueToUse() {
        return home != null && citizen.hasCarriedItems();
    }

    @Override
    public void start() {
        if (distanceToArrival() > 2.25) {
            citizen.beginTeleport((ServerLevel) citizen.level(), arrivalPoint, "storage");
        }
    }

    @Override
    public void tick() {
        if (!(citizen.level() instanceof ServerLevel level) || citizen.isTeleporting()) return;
        if (distanceToArrival() > 2.25) {
            citizen.beginTeleport(level, arrivalPoint, "storage");
            return;
        }
        boolean deposited = false;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack carried = citizen.inventoryItem(slot);
            if (carried.isEmpty()) continue;
            ItemStack remainder = HouseholdFarmSavedData.get(level).deposit(level, householdId, carried);
            if (remainder.getCount() != carried.getCount()) {
                deposited = true;
                citizen.setInventoryItem(slot, remainder);
            }
        }
        if (deposited) citizen.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
        if (!citizen.hasCarriedItems()) {
            citizen.clearWorkProductDeliveryRequest();
            citizen.clearInventoryWork();
        }
    }

    @Override
    public void stop() {
        citizen.cancelTeleport();
        if (!citizen.hasCarriedItems()) {
            citizen.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        home = null;
        if (arrivalPoint != null) lastArrivalPoint = arrivalPoint;
        arrivalPoint = null;
        householdId = null;
    }

    private double distanceToArrival() {
        return citizen.distanceToSqr(arrivalPoint.getX() + 0.5, arrivalPoint.getY(), arrivalPoint.getZ() + 0.5);
    }

    static boolean shouldDeliver(CitizenEntity citizen, CitizenWorkSchedule.Work currentWork) {
        return citizen.hasCarriedItems() && (citizen.workProductDeliveryRequested()
                || citizen.inventoryFull()
                || citizen.inventoryWork() != null && citizen.inventoryWork() != currentWork);
    }
}
