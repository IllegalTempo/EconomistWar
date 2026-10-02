package com.economistwars.citizen;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class CitizenSimulationTest {
    @BeforeAll static void bootstrap() { CitizenStateTest.bootstrap(); }
    @Test void allCitizensAdvanceWithZeroVisuals() {
        SimulationTestContext c = new SimulationTestContext(); c.farm = new BlockPos(20,0,0);
        CitizenState citizen = c.citizen(); citizen.needs = CitizenNeed.defaults(80,0,0,0);
        for (int i = 0; i < 60; i++) CitizenSimulation.advance(List.of(citizen),c);
        assertEquals(new Vec3(20.5,0,.5),citizen.position);
        assertTrue(citizen.inventory.count(Items.WHEAT) > 0);
        assertTrue(citizen.skills.experience(CitizenSkill.FARMING) > 0);
    }
    @Test void twoMembersConsumeOnlyOneDailyHouseholdPassAndRollbackDoesNotReplay() {
        SimulationTestContext c = new SimulationTestContext(); CitizenState first = c.citizen(), second = c.citizen();
        c.households.removeCitizen(second.id()); second.householdId = first.householdId; c.households.addMember(first.householdId,second.id());
        var household = c.household(first.householdId).orElseThrow(); household.deposit(new ItemStack(Items.BREAD,10));
        CitizenSimulation.advance(List.of(first,second),c);
        c.clock = 24000; CitizenSimulation.advance(List.of(first,second),c); assertEquals(8,household.foodCount());
        CitizenSimulation.advance(List.of(first,second),c); assertEquals(8,household.foodCount());
        c.clock = 0; CitizenSimulation.advance(List.of(first,second),c); assertEquals(8,household.foodCount());
    }
    @Test void forwardClockJumpUsesOneEconomyPassAndElapsedNeedGrowth() {
        SimulationTestContext c = new SimulationTestContext(); CitizenState citizen = c.citizen();
        var household = c.household(citizen.householdId).orElseThrow(); household.deposit(new ItemStack(Items.BREAD,10));
        CitizenSimulation.advance(List.of(citizen),c); citizen.consumptionCooldown = 20;
        c.clock = 3*24000; CitizenSimulation.advance(List.of(citizen),c);
        assertEquals(9,household.foodCount()); assertEquals(75,citizen.needs.get(CitizenNeed.EAT).urgency());
    }
    @Test void sleepUsesBackendBedReservationAndReleasesAtDawn() {
        SimulationTestContext c = new SimulationTestContext(); CitizenState citizen = c.citizen();
        c.bedData.register(citizen.householdId,new CitizenAssetKey(citizen.dimension,BlockPos.ZERO));
        c.clock = 13000; CitizenSimulation.advance(List.of(citizen),c);
        assertEquals(CitizenDecisionPlanner.Action.SLEEP,citizen.action);
        assertEquals(1800,citizen.decisionScore);
        c.clock = 23000; CitizenSimulation.advance(List.of(citizen),c);
        assertNotEquals(CitizenDecisionPlanner.Action.SLEEP,citizen.action);
        assertTrue(c.bedData.available(citizen.householdId,UUID.randomUUID(),citizen.dimension).isPresent());
    }
    @Test void deathCleanupRunsOnceAndRetainsTombstone() {
        SimulationTestContext c = new SimulationTestContext(); CitizenState citizen = c.citizen();
        CitizenState other = c.citizen(); c.households.removeCitizen(other.id()); other.householdId = citizen.householdId; c.households.addMember(citizen.householdId,other.id());
        citizen.inventory.insert(List.of(new ItemStack(Items.WHEAT,2)));
        assertTrue(CitizenSimulation.damage(citizen,20,c)); assertFalse(CitizenSimulation.damage(citizen,20,c));
        assertFalse(citizen.alive); assertFalse(c.households.hasMember(citizen.householdId,citizen.id()));
        assertEquals(2,c.household(other.householdId).orElseThrow().foodCount());
    }
}
