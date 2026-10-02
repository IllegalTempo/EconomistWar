package com.economistwars.citizen;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class CitizenSimulationParityTest {
    @BeforeAll static void bootstrap() { CitizenStateTest.bootstrap(); }
    @Test void fullDayHasSameResultsWithVisualsAlwaysNeverOrSometimesPresent() {
        SimulationTestContext[] contexts = {new SimulationTestContext(),new SimulationTestContext(),new SimulationTestContext()};
        CitizenState first = contexts[0].citizen(); first.needs = CitizenNeed.defaults(80,0,0,0);
        CitizenState[] citizens = {first,CitizenSimulationPersistenceTest.copy(first),CitizenSimulationPersistenceTest.copy(first)};
        CitizenPresentationManagerTest.Access[] visuals = {new CitizenPresentationManagerTest.Access(),new CitizenPresentationManagerTest.Access(),new CitizenPresentationManagerTest.Access()};
        for (int variant = 1; variant < 3; variant++) {
            var encoded = codec().encodeStart(CitizenSimulationPersistenceTest.OPS,contexts[0].households).getOrThrow();
            var loaded = codec().parse(CitizenSimulationPersistenceTest.OPS,encoded).getOrThrow();
            // Preserve the real household registry and membership in each independent simulation.
            contexts[variant].householdOverride = loaded;
        }
        for (var c : contexts) c.farm = new BlockPos(20,0,0);
        for (int tick = 0; tick <= 24000; tick++) for (int variant = 0; variant < 3; variant++) {
            contexts[variant].clock = tick;
            CitizenSimulation.advance(List.of(citizens[variant]),contexts[variant]);
            visuals[variant].ticking = variant == 0 || variant == 2 && tick%200<100;
            CitizenPresentationManager.reconcile(List.of(citizens[variant]),visuals[variant]);
        }
        var ops = CitizenSimulationPersistenceTest.OPS;
        for (int i = 1; i < 3; i++) {
            assertEquals(CitizenState.CODEC.encodeStart(ops,citizens[0]).getOrThrow(),CitizenState.CODEC.encodeStart(ops,citizens[i]).getOrThrow());
            assertEquals(codec().encodeStart(ops,contexts[0].households()).getOrThrow(),codec().encodeStart(ops,contexts[i].households()).getOrThrow());
        }
        assertTrue(citizens[0].skills.experience(CitizenSkill.FARMING)>0);
    }
    @SuppressWarnings("unchecked") static com.mojang.serialization.Codec<com.economistwars.household.HouseholdSavedData> codec() {
        try { var f = com.economistwars.household.HouseholdSavedData.class.getDeclaredField("CODEC"); f.setAccessible(true); return (com.mojang.serialization.Codec<com.economistwars.household.HouseholdSavedData>)f.get(null); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
}
