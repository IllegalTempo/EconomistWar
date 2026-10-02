package com.economistwars.citizen;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class CitizenSimulationPersistenceTest {
    static { CitizenStateTest.bootstrap(); }
    @BeforeAll static void bootstrap() { CitizenStateTest.bootstrap(); }
    static final net.minecraft.resources.RegistryOps<com.google.gson.JsonElement> OPS = net.minecraft.resources.RegistryOps.create(
            com.mojang.serialization.JsonOps.INSTANCE,net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
    static CitizenState copy(CitizenState s) { return CitizenState.CODEC.parse(OPS,CitizenState.CODEC.encodeStart(OPS,s).getOrThrow()).getOrThrow(); }
    @Test void saveDuringFarmTravelResumesWithoutRestartingOrDuplicatingOutput() {
        SimulationTestContext c = new SimulationTestContext(); c.farm = new BlockPos(30,0,0);
        CitizenState original = c.citizen(); original.needs = CitizenNeed.defaults(80,0,0,0);
        for (int i = 0; i < 15; i++) CitizenSimulation.advance(List.of(original),c);
        assertTrue(original.travelling()); CitizenState restored = copy(original);
        for (int i = 0; i < 100; i++) { CitizenSimulation.advance(List.of(original),c); CitizenSimulation.advance(List.of(restored),c); }
        assertEquals(original.position,restored.position);
        assertEquals(original.inventory.count(Items.WHEAT),restored.inventory.count(Items.WHEAT));
        assertEquals(original.skills.experience(CitizenSkill.FARMING),restored.skills.experience(CitizenSkill.FARMING));
        assertEquals(original.workTicks,restored.workTicks);
    }
    @Test void saveDuringMiningWorkKeepsProgressAndRandomOutcome() {
        SimulationTestContext c = new SimulationTestContext(); CitizenState original = c.citizen();
        c.mine = new MineSiteState(new CitizenAssetKey(original.dimension,BlockPos.ZERO),10000,new net.minecraft.world.level.levelgen.structure.BoundingBox(0,0,0,0,0,0));
        original.needs = CitizenNeed.defaults(80,0,0,0);
        for (int i = 0; i < 17; i++) CitizenSimulation.advance(List.of(original),c);
        assertEquals(17,original.workTicks); CitizenState restored = copy(original);
        for (int i = 0; i < 23; i++) { CitizenSimulation.advance(List.of(original),c); CitizenSimulation.advance(List.of(restored),c); }
        assertEquals(1,original.inventory.count(Items.COAL)); assertEquals(1,restored.inventory.count(Items.COAL));
        assertEquals(original.random.state(),restored.random.state());
    }
    @Test void invalidActionLoadsAsIdleWithoutClaimingAProduct() {
        CitizenState s = CitizenStateTest.citizen(); s.action = CitizenDecisionPlanner.Action.MINE; s.workTicks = 39;
        var json = CitizenState.CODEC.encodeStart(OPS,s).getOrThrow().getAsJsonObject();
        json.getAsJsonObject("state").addProperty("action","invalid-action");
        CitizenState loaded = CitizenState.CODEC.parse(OPS,json).getOrThrow();
        assertNull(loaded.action); assertEquals(0,loaded.workTicks); assertEquals(0,loaded.inventory.count(Items.COAL));
    }
    @Test void simulationDoesNotManufactureNewRecordsForUnboundVisuals() {
        CitizenSavedData data = new CitizenSavedData(); CitizenState state = CitizenStateTest.citizen();
        data.registerIfAbsent(state); state.position = new net.minecraft.world.phys.Vec3(20,0,0);
        CitizenState stale = CitizenState.create(state.identity,state.householdId,state.dimension,net.minecraft.world.phys.Vec3.ZERO,0,1);
        assertEquals(state.position,data.registerIfAbsent(stale).position);
    }
}
