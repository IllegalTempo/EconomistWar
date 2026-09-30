package com.economistwars.citizen;

import java.util.List;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitizenDecisionPlannerTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void strongerMatchingSkillCanOutweighTheDefaultWorkSchedule() {
        var candidates = List.of(
                new CitizenDecisionPlanner.Candidate(CitizenDecisionPlanner.Action.FARM, true,
                        CitizenDecisionPlanner.score(2.0, 12, 0, 0, true)),
                new CitizenDecisionPlanner.Candidate(CitizenDecisionPlanner.Action.MINE, true,
                        CitizenDecisionPlanner.score(2.0, 40, 100, 0, false)));

        assertEquals(CitizenDecisionPlanner.Action.MINE,
                CitizenDecisionPlanner.choose(candidates, null).orElseThrow());
    }

    @Test
    void urgentActionOverridesCommittedWorkAndIneligibleActionsAreIgnored() {
        var candidates = List.of(
                new CitizenDecisionPlanner.Candidate(CitizenDecisionPlanner.Action.FARM, true,
                        CitizenDecisionPlanner.score(2.0, 12, 10, 0, true)),
                new CitizenDecisionPlanner.Candidate(CitizenDecisionPlanner.Action.DELIVERY, true,
                        CitizenDecisionPlanner.URGENT_SCORE),
                new CitizenDecisionPlanner.Candidate(CitizenDecisionPlanner.Action.SLEEP, false,
                        CitizenDecisionPlanner.URGENT_SCORE * 2));

        assertEquals(CitizenDecisionPlanner.Action.DELIVERY,
                CitizenDecisionPlanner.choose(candidates, CitizenDecisionPlanner.Action.FARM).orElseThrow());
    }

    @Test
    void currentActionIsHeldAgainstSmallScoreChangesAndNoCandidatesMeansIdle() {
        var candidates = List.of(
                new CitizenDecisionPlanner.Candidate(CitizenDecisionPlanner.Action.FARM, true, 1.0),
                new CitizenDecisionPlanner.Candidate(CitizenDecisionPlanner.Action.MINE, true, 1.09));

        assertEquals(CitizenDecisionPlanner.Action.FARM,
                CitizenDecisionPlanner.choose(candidates, CitizenDecisionPlanner.Action.FARM).orElseThrow());
        assertTrue(CitizenDecisionPlanner.choose(List.of(
                new CitizenDecisionPlanner.Candidate(CitizenDecisionPlanner.Action.MINE, false, 100)), null).isEmpty());
        assertEquals(CitizenDecisionPlanner.Action.MINE, CitizenDecisionPlanner.choose(List.of(
                new CitizenDecisionPlanner.Candidate(CitizenDecisionPlanner.Action.FARM, true, 1.0),
                new CitizenDecisionPlanner.Candidate(CitizenDecisionPlanner.Action.MINE, true, 1.10)),
                CitizenDecisionPlanner.Action.FARM).orElseThrow());
    }

    @Test
    void skillChangesEffectiveWorkDurationCompoundedByTwoPercentPerLevel() {
        assertEquals(12, CitizenDecisionPlanner.effectiveWorkTicks(12, 0));
        assertEquals(2, CitizenDecisionPlanner.effectiveWorkTicks(12, 100));
        assertEquals(1, CitizenDecisionPlanner.effectiveWorkTicks(1, 100));
        assertEquals(20, CitizenDecisionPlanner.effectiveWorkTicks(40, 36));
    }

    @Test
    void scoresNetUtilityPerEffectiveWorkAndTravelTick() {
        assertEquals(0.05, CitizenDecisionPlanner.score(2.0, 40, 0, 0, false), 0.0001);
        assertEquals(0.025, CitizenDecisionPlanner.score(2.0, 40, 0, 40, false), 0.0001);
        assertEquals(0.0525, CitizenDecisionPlanner.score(2.0, 40, 0, 0, true), 0.0001);
    }

    @Test
    void onlyRememberedProductGetsOneTradeValueAndOtherOutputKeepsDirectUtility() {
        CitizenNeeds needs = new CitizenNeeds(100, 0, 0, 0);
        double oneTrade = CitizenDecisionPlanner.adjustedOutputUtility(
                Items.WHEAT, 2, Items.BREAD, Items.WHEAT, needs);
        assertEquals(5.0, oneTrade, 0.0001);
        assertEquals(2.0, CitizenDecisionPlanner.adjustedOutputUtility(
                Items.WHEAT, 2, Items.BREAD, Items.BREAD, needs), 0.0001);
        assertEquals(4.0, CitizenDecisionPlanner.adjustedOutputUtility(
                Items.WHEAT, 2, Items.BREAD, Items.WHEAT,
                new CitizenNeeds(0, 100, 100, 0)), 0.0001);
    }

    @Test
    void travelTicksUseExistingClampedDistanceDuration() {
        assertEquals(10, CitizenDecisionPlanner.teleportTicks(0));
        assertEquals(40, CitizenDecisionPlanner.teleportTicks(20));
        assertEquals(200, CitizenDecisionPlanner.teleportTicks(1000));
    }

    @Test
    void miningWeightsLootAndRejectsDropsTheResourceStockCannotPayFor() {
        CitizenNeeds needs = new CitizenNeeds(100, 100, 100, 0);
        assertEquals(3.0, CitizenDecisionPlanner.miningOutputUtility(
                needs, null, null, 3, 1, 1, 1), 0.0001);
        assertEquals(0.6, CitizenDecisionPlanner.miningOutputUtility(
                needs, null, null, 1, 2, 1, 3), 0.0001);
        assertEquals(0.0, CitizenDecisionPlanner.miningOutputUtility(
                needs, null, null, 0, 1, 1, 1), 0.0001);
    }
}
