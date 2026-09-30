package com.economistwars.citizen;

import com.economistwars.household.HouseholdFarmSavedData;
import com.economistwars.household.HouseholdSavedData;
import com.economistwars.household.SettlementMarketBlockEntity;
import com.economistwars.household.SettlementMarketBlock;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import com.economistwars.network.CitizenProfilePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

/** Takes each citizen to its nearest settlement market once per Minecraft day. */
final class CitizenMarketGoal extends Goal implements CitizenDecisionAction {
    private final CitizenEntity citizen;
    private UUID householdId;
    private BlockPos market;
    private BlockPos arrival;
    private long visitDay;
    private boolean completed;

    CitizenMarketGoal(CitizenEntity citizen) {
        this.citizen = citizen;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override public CitizenDecisionPlanner.Action decisionAction() { return CitizenDecisionPlanner.Action.MARKET; }
    @Override public int estimatedDurationTicks() {
        int travel = arrival == null ? 0 : citizen.distanceToSqr(
                arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5) <= 2.25
                ? 0 : CitizenDecisionPlanner.travelTicks(citizen.distanceToSqr(
                        arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5));
        return travel + 1;
    }
    @Override public double decisionScore() {
        if (!(citizen.level() instanceof ServerLevel level) || householdId == null || market == null) return 0.0;
        double benefit = level.getBlockEntity(market) instanceof SettlementMarketBlockEntity entity
                ? entity.potentialTradeBenefit(level, householdId, citizen.needs(), CitizenMarketMemory.producibleItems()) : 0.0;
        int urgency = Math.max(citizen.needs().eat(), Math.max(citizen.needs().entertainment(), citizen.needs().safety()));
        CitizenMarketMemory memory = citizen.marketMemory();
        if (urgency >= 80) return CitizenDecisionPlanner.URGENT_SCORE;
        BlockPos home = com.economistwars.household.HouseholdFarmSavedData.get(level)
                .home(level, householdId).orElse(null);
        if (home == null) return 0.0;
        int outboundTicks = CitizenDecisionPlanner.travelTicks(citizen.distanceToSqr(
                market.getX() + 0.5, market.getY(), market.getZ() + 0.5));
        int returnTicks = CitizenDecisionPlanner.travelTicks(market.distToCenterSqr(
                home.getX() + 0.5, home.getY(), home.getZ() + 0.5));
        return CitizenDecisionPlanner.score(benefit, 1, 0, outboundTicks + returnTicks, false);
    }
    @Override public List<CitizenProfilePayload.DecisionDetail> decisionDetails() {
        double benefit = citizen.level() instanceof ServerLevel level && householdId != null && market != null
                && level.getBlockEntity(market) instanceof SettlementMarketBlockEntity entity
                ? entity.potentialTradeBenefit(level, householdId, citizen.needs(), CitizenMarketMemory.producibleItems()) : 0.0;
        int urgency = Math.max(citizen.needs().eat(), Math.max(citizen.needs().entertainment(), citizen.needs().safety()));
        CitizenMarketMemory memory = citizen.marketMemory();
        int outbound = market == null ? 0 : CitizenDecisionPlanner.travelTicks(citizen.distanceToSqr(
                market.getX() + 0.5, market.getY(), market.getZ() + 0.5));
        int returning = 0;
        if (market != null && citizen.level() instanceof ServerLevel level && householdId != null) {
            BlockPos home = HouseholdFarmSavedData.get(level).home(level, householdId).orElse(null);
            if (home != null) returning = CitizenDecisionPlanner.travelTicks(market.distToCenterSqr(
                    home.getX() + 0.5, home.getY(), home.getZ() + 0.5));
        }
        return List.of(
                new CitizenProfilePayload.DecisionDetail("Potential barter net utility", String.format(java.util.Locale.ROOT, "%.4f", benefit)),
                new CitizenProfilePayload.DecisionDetail("Last-visit remembered target", memory == null ? "none"
                        : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(memory.received()) + " for "
                                + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(memory.requested())),
                new CitizenProfilePayload.DecisionDetail("Maximum current need", urgency + "/100 (urgent override at 80)"),
                new CitizenProfilePayload.DecisionDetail("Travel to market", outbound + " ticks"),
                new CitizenProfilePayload.DecisionDetail("Market to home", returning + " ticks"),
                new CitizenProfilePayload.DecisionDetail("Operation time", "1 tick"),
                new CitizenProfilePayload.DecisionDetail("Score formula", urgency >= 80 ? "900 priority override" : "max(0, net barter utility) ÷ (1 + outbound + return ticks)")
        );
    }

    @Override
    public boolean canUse() {
        if (!(citizen.level() instanceof ServerLevel level) || citizen.isTeleporting() || completed
                || CitizenWorkSchedule.isNight(level)) return false;
        visitDay = level.getOverworldClockTime() / 24_000L;
        if (citizen.lastMarketVisitDay() >= visitDay) return false;
        householdId = citizen.householdId().orElse(null);
        if (householdId == null || !HouseholdSavedData.get(level).hasMember(householdId, citizen.citizenId())) return false;
        BlockPos home = HouseholdFarmSavedData.get(level).home(level, householdId).orElse(null);
        if (home == null) return false;
        market = SettlementMarketSavedData.get(level).positions(level).stream()
                .min((first, second) -> Double.compare(first.distToCenterSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5),
                        second.distToCenterSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5)))
                .orElse(null);
        if (market == null || market.distToCenterSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5) > 128 * 128) return false;
        level.getChunkAt(market);
        arrival = CitizenTeleportPoints.randomSafePosition(level, citizen, market,
                4, 1, 36, null, candidate -> true);
        return arrival != null;
    }

    @Override
    public boolean canContinueToUse() {
        return !completed && market != null && arrival != null && citizen.isAlive();
    }

    @Override
    public void start() {
        completed = false;
        if (citizen.level() instanceof ServerLevel level) citizen.beginTeleport(level, arrival, "market");
    }

    @Override
    public void tick() {
        if (citizen.isTeleporting() || !(citizen.level() instanceof ServerLevel level)) return;
        if (citizen.distanceToSqr(arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5) > 2.25) {
            citizen.beginTeleport(level, arrival, "market");
            return;
        }
        if (level.getBlockEntity(market) instanceof SettlementMarketBlockEntity marketEntity
                && level.getBlockState(market).is(SettlementMarketBlock.BLOCK)) {
            marketEntity.visit(level, householdId, citizen.needs(), citizen);
        } else {
            citizen.refreshMarketMemory(null);
        }
        citizen.recordMarketVisit(visitDay);
        completed = true;
    }

    @Override
    public void stop() {
        citizen.cancelTeleport();
        completed = false;
        householdId = null;
        market = null;
        arrival = null;
    }

}
