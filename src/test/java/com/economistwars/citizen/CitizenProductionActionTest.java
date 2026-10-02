package com.economistwars.citizen;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class CitizenProductionActionTest {
    @BeforeAll static void bootstrap() { CitizenStateTest.bootstrap(); }
    @Test void farmProducesTwoWheatAfterTwelveTicksWithoutEntity() {
        SimulationTestContext context = new SimulationTestContext();
        CitizenState citizen = context.citizen(); context.farm = BlockPos.ZERO;
        CitizenSimulationAction farm = new CitizenFarmAction();
        farm.start(citizen,context,farm.evaluate(citizen,context,false));
        for (int i = 0; i < 11; i++) farm.advance(citizen,context);
        assertEquals(0,citizen.inventory.count(Items.WHEAT));
        farm.advance(citizen,context);
        assertEquals(2,citizen.inventory.count(Items.WHEAT));
        assertEquals(5,citizen.skills.experience(CitizenSkill.FARMING));
    }
    @Test void bakeConsumesThreeWheatAfter1200Ticks() {
        SimulationTestContext context = new SimulationTestContext();
        CitizenState citizen = context.citizen(); citizen.inventory.insert(List.of(new ItemStack(Items.WHEAT,3)));
        CitizenSimulationAction bake = new CitizenBakeryAction(); bake.start(citizen,context,bake.evaluate(citizen,context,false));
        for (int i = 0; i < 1199; i++) bake.advance(citizen,context);
        assertEquals(3,citizen.inventory.count(Items.WHEAT));
        bake.advance(citizen,context);
        assertEquals(0,citizen.inventory.count(Items.WHEAT)); assertEquals(1,citizen.inventory.count(Items.BREAD));
    }
    @Test void twoMinersCannotOverspendStock() {
        SimulationTestContext context = new SimulationTestContext();
        CitizenState first = context.citizen(), second = context.citizen();
        context.mine = new MineSiteState(new CitizenAssetKey(first.dimension,BlockPos.ZERO),1,new net.minecraft.world.level.levelgen.structure.BoundingBox(0,0,0,0,0,0));
        CitizenSimulationAction mine = new CitizenMiningAction();
        mine.start(first,context,mine.evaluate(first,context,false)); mine.start(second,context,mine.evaluate(second,context,false));
        for (int i = 0; i < 40; i++) { mine.advance(first,context); mine.advance(second,context); }
        assertEquals(0,context.mine.remaining());
        assertEquals(1,first.inventory.count(Items.COAL) + second.inventory.count(Items.COAL));
    }
    @Test void travelCompletesWithoutEntity() {
        CitizenState citizen = CitizenStateTest.citizen(); citizen.beginTravel(new Vec3(20,0,0));
        for (int i = 0; i < 39; i++) citizen.advanceTravel();
        assertEquals(Vec3.ZERO,citizen.position);
        citizen.advanceTravel(); assertEquals(new Vec3(20,0,0),citizen.position); assertFalse(citizen.travelling());
    }
    @Test void deliveryToFullStorageKeepsRemainder() {
        SimulationTestContext context = new SimulationTestContext(); CitizenState citizen = context.citizen();
        var household = context.household(citizen.householdId).orElseThrow();
        for (int i = 0; i < 9; i++) household.setItem(i,new ItemStack(Items.COAL,64));
        citizen.inventory.insert(List.of(new ItemStack(Items.WHEAT,2))); citizen.deliveryRequested = true;
        CitizenSimulationAction delivery = new CitizenDeliveryAction(); delivery.start(citizen,context,delivery.evaluate(citizen,context,false));
        delivery.advance(citizen,context);
        assertEquals(2,citizen.inventory.count(Items.WHEAT)); assertTrue(citizen.deliveryRequested);
    }
}
