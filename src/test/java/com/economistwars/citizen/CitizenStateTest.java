package com.economistwars.citizen;

import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CitizenStateTest {
    @BeforeAll static void bootstrap() {
        MinecraftTestBootstrap.ensureBootstrapped();
        var components = net.minecraft.core.component.DataComponentMap.builder()
                .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 64).build();
        for (var item : List.of(Items.COAL, Items.WHEAT, Items.BREAD, Items.RAW_IRON, Items.RAW_GOLD, Items.APPLE, Items.IRON_HOE, Items.IRON_PICKAXE)) item.builtInRegistryHolder().bindComponents(components);
    }
    static CitizenState citizen() {
        return CitizenState.create(CitizenIdentity.create(), UUID.randomUUID(), "minecraft:overworld", Vec3.ZERO, 0, 123);
    }
    @Test void roundTripKeepsActionTravelInventoryAndRandomState() {
        CitizenState state = citizen();
        state.inventory.insert(List.of(new ItemStack(Items.WHEAT, 3)));
        state.action = CitizenDecisionPlanner.Action.MINE;
        state.workTicks = 17;
        state.beginTravel(new Vec3(20, 64, 30));
        state.random.nextInt(10);
        var ops = net.minecraft.resources.RegistryOps.create(JsonOps.INSTANCE,
                net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        var encoded = CitizenState.CODEC.encodeStart(ops, state).getOrThrow();
        CitizenState loaded = CitizenState.CODEC.parse(ops, encoded).getOrThrow();
        assertEquals(state.id(), loaded.id());
        assertEquals(17, loaded.workTicks);
        assertEquals(CitizenDecisionPlanner.Action.MINE, loaded.action);
        assertEquals(state.travelRemaining, loaded.travelRemaining);
        assertEquals(new Vec3(20, 64, 30), loaded.travelDestination);
        assertEquals(3, loaded.inventory.count(Items.WHEAT));
        assertEquals(state.random.nextInt(1000), loaded.random.nextInt(1000));
    }
    @Test void legacyRegistrationIsIdempotentAndCannotReplaceDeadRecord() {
        CitizenSavedData data = new CitizenSavedData();
        CitizenState first = citizen();
        data.registerIfAbsent(first);
        first.alive = false;
        CitizenState stale = CitizenState.create(first.identity, first.householdId, first.dimension, new Vec3(5,0,0), 0, 4);
        assertSame(first, data.registerIfAbsent(stale));
        assertFalse(data.find(first.id()).orElseThrow().alive);
    }
    @Test void bakingIsAtomicWhenThereIsNotEnoughWheat() {
        CitizenInventory inventory = new CitizenInventory();
        inventory.insert(List.of(new ItemStack(Items.WHEAT, 2)));
        assertFalse(inventory.bakeBread());
        assertEquals(2, inventory.count(Items.WHEAT));
        assertEquals(0, inventory.count(Items.BREAD));
    }
    @Test void insertionIsAtomicWhenInventoryCannotFit() {
        CitizenInventory inventory = new CitizenInventory();
        for (int slot = 0; slot < 36; slot++) inventory.set(slot, new ItemStack(Items.COAL, 64));
        assertFalse(inventory.insert(List.of(new ItemStack(Items.WHEAT))));
        assertEquals(2304, inventory.count(Items.COAL));
        assertEquals(0, inventory.count(Items.WHEAT));
    }
    @Test void persistentRandomRestartsAtSameSequence() {
        CitizenRandom random = new CitizenRandom(54);
        random.nextInt(17);
        CitizenRandom loaded = new CitizenRandom(random.state());
        for (int i = 0; i < 100; i++) {
            int result = random.nextInt(7);
            assertTrue(result >= 0 && result < 7);
            assertEquals(result, loaded.nextInt(7));
        }
    }
}

