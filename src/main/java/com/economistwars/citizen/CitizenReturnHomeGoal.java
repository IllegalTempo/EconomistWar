package com.economistwars.citizen;

import com.economistwars.household.HouseholdFarmSavedData;
import com.economistwars.network.CitizenProfilePayload;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

/** Keeps settlement citizens close to their household's home between harvests. */
final class CitizenReturnHomeGoal extends Goal implements CitizenDecisionAction {
    private final CitizenEntity citizen;
    private BlockPos home;
    private BlockPos arrivalPoint;
    private BlockPos lastArrivalPoint;
    private int retryDelay;

    CitizenReturnHomeGoal(CitizenEntity citizen) {
        this.citizen = citizen;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override public CitizenDecisionPlanner.Action decisionAction() { return CitizenDecisionPlanner.Action.RETURN_HOME; }
    @Override public int estimatedDurationTicks() {
        if (arrivalPoint == null) return 1;
        return CitizenDecisionPlanner.travelTicks(citizen.distanceToSqr(
                arrivalPoint.getX() + 0.5, arrivalPoint.getY(), arrivalPoint.getZ() + 0.5));
    }
    @Override public double decisionScore() {
        if (citizen.level() instanceof ServerLevel level && CitizenWorkSchedule.isNight(level)) {
            return CitizenDecisionPlanner.URGENT_SCORE;
        }
        if (arrivalPoint == null) return 0.0;
        double safetyValue = 1.0 + citizen.needs().safety() / 100.0;
        int travelTicks = CitizenDecisionPlanner.travelTicks(citizen.distanceToSqr(
                arrivalPoint.getX() + 0.5, arrivalPoint.getY(), arrivalPoint.getZ() + 0.5));
        return CitizenDecisionPlanner.score(safetyValue, 1, 0, travelTicks, false);
    }
    @Override public List<CitizenProfilePayload.DecisionDetail> decisionDetails() {
        boolean night = citizen.level() instanceof ServerLevel level && CitizenWorkSchedule.isNight(level);
        int safetyNeed = citizen.needs().safety();
        double safetyValue = 1.0 + safetyNeed / 100.0;
        int travelTicks = arrivalPoint == null ? 0 : CitizenDecisionPlanner.travelTicks(citizen.distanceToSqr(
                arrivalPoint.getX() + 0.5, arrivalPoint.getY(), arrivalPoint.getZ() + 0.5));
        return List.of(
                new CitizenProfilePayload.DecisionDetail("Safety need", safetyNeed + "/100"),
                new CitizenProfilePayload.DecisionDetail("Safety utility", String.format(java.util.Locale.ROOT, "1 + %d/100 = %.4f", safetyNeed, safetyValue)),
                new CitizenProfilePayload.DecisionDetail("Base operation time", "1 tick"),
                new CitizenProfilePayload.DecisionDetail("Travel ticks", Integer.toString(travelTicks)),
                new CitizenProfilePayload.DecisionDetail("Night return-home priority", night ? "active" : "inactive"),
                new CitizenProfilePayload.DecisionDetail("Score formula", night ? "900.0000 utility/tick priority override"
                        : String.format(java.util.Locale.ROOT, "%.4f ÷ (1 + %d) = %.4f utility/tick", safetyValue, travelTicks, decisionScore()))
        );
    }

    @Override
    public boolean canUse() {
        if (!(citizen.level() instanceof ServerLevel level) || citizen.isTeleporting()) return false;
        boolean night = CitizenWorkSchedule.isNight(level);
        if (!night && citizen.hasCarriedItems()
                && !CitizenStoreHarvestGoal.shouldDeliver(citizen, CitizenWorkSchedule.workFor(level))) return false;
        if (retryDelay > 0 && !night) {
            retryDelay = Math.max(0, retryDelay - CitizenDecisionPlanner.RESCORE_TICKS);
            return false;
        }
        if (night) retryDelay = 0;
        retryDelay = 60;
        home = citizen.householdId()
                .flatMap(id -> HouseholdFarmSavedData.get(level).home(level, id))
                .orElse(null);
        if (home == null) return false;
        level.getChunkAt(home);
        arrivalPoint = CitizenTeleportPoints.randomSafePosition(level, citizen, home,
                4, 1, Double.MAX_VALUE, lastArrivalPoint, candidate -> true);
        if (arrivalPoint == null) return false;
        return citizen.distanceToSqr(arrivalPoint.getX() + 0.5, arrivalPoint.getY(), arrivalPoint.getZ() + 0.5) > 64;
    }

    @Override
    public boolean canContinueToUse() {
        return home != null && (citizen.isTeleporting()
                || citizen.distanceToSqr(arrivalPoint.getX() + 0.5, arrivalPoint.getY(), arrivalPoint.getZ() + 0.5) > 2.25);
    }

    @Override
    public void start() {
        if (home != null && citizen.level() instanceof ServerLevel level) {
            citizen.beginTeleport(level, arrivalPoint, "home");
        }
    }

    @Override
    public void tick() {
        if (home != null && !citizen.isTeleporting()
                && citizen.distanceToSqr(arrivalPoint.getX() + 0.5, arrivalPoint.getY(), arrivalPoint.getZ() + 0.5) > 2.25
                && citizen.level() instanceof ServerLevel level) {
            citizen.beginTeleport(level, arrivalPoint, "home");
        }
    }

    @Override
    public void stop() {
        citizen.cancelTeleport();
        if (arrivalPoint != null) lastArrivalPoint = arrivalPoint;
        home = null;
        arrivalPoint = null;
    }
}
