package com.economistwars.household;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HouseholdStorageTest {
    @Test
    void itemOwnershipReflectsDepositsAndWithdrawalsWithoutASeparateLedger() {
        Household household = new Household(UUID.randomUUID());
        household.deposit(new ItemStack(Items.WHEAT, 12));
        var ownership = household.itemOwnerships().getFirst();
        assertEquals(household.id(), ownership.getOwner());
        var asset = (com.economistwars.ownership.assetsReferences.ItemAssetsReference) ownership.getAssetReference();
        assertEquals(12, asset.amount());
        household.requestFromStorage(Items.WHEAT, 5);
        var remaining = (com.economistwars.ownership.assetsReferences.ItemAssetsReference)
                household.itemOwnerships().getFirst().getAssetReference();
        assertEquals(7, remaining.amount());
        assertEquals(12, asset.amount(), "An ownership snapshot must not mutate with the inventory");
        household.requestFromStorage(Items.WHEAT, 7);
        assertTrue(household.itemOwnerships().isEmpty());
    }
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Bind the stack-size defaults needed by these inventory tests without loading world datapacks.
        var components = DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build();
        Items.WHEAT.builtInRegistryHolder().bindComponents(components);
        Items.BREAD.builtInRegistryHolder().bindComponents(components);
    }

    @Test
    void depositsCopyItemsAndRespectStackLimits() {
        Household household = new Household(UUID.randomUUID());
        ItemStack offered = new ItemStack(Items.WHEAT, 100);
        assertTrue(household.deposit(offered).isEmpty());
        assertEquals(100, offered.getCount());
        assertEquals(100, household.storageContents().stream().mapToInt(ItemStack::getCount).sum());
        assertTrue(household.storageContents().stream().allMatch(stack -> stack.getCount() <= stack.getMaxStackSize()));
    }

    @Test
    void dailyEconomyStateSurvivesSaving() throws Exception {
        HouseholdSavedData data = new HouseholdSavedData();
        UUID id = data.createHousehold();
        data.addMember(id, UUID.randomUUID());
        Household household = data.getHousehold(id);
        household.deposit(new ItemStack(Items.WHEAT, 40));
        household.processEconomyDay(10);
        household.processEconomyDay(11);
        household.beginMarketDay(11);

        var field = HouseholdSavedData.class.getDeclaredField("CODEC");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var codec = (com.mojang.serialization.Codec<HouseholdSavedData>) field.get(null);
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,
                net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        Household restored = codec.parse(ops, codec.encodeStart(ops, data).getOrThrow()).getOrThrow().getHousehold(id);
        assertEquals(35, restored.foodCount());
        assertEquals(104, restored.coins());
        assertFalse(restored.processEconomyDay(11));
        assertFalse(restored.beginMarketDay(11));
    }

    @Test
    void withdrawalsSkipEmptyStacksAndRejectNonpositiveAmounts() {
        Household household = new Household(UUID.randomUUID());
        household.deposit(new ItemStack(Items.WHEAT));
        household.requestFromStorage(Items.WHEAT, 1);
        household.deposit(new ItemStack(Items.BREAD, 2));
        assertTrue(household.requestFromStorage(Items.BREAD, -1).isEmpty());
        assertEquals(1, household.requestFromStorage(Items.BREAD, 1).getCount());
        assertEquals(1, household.foodCount());
    }

    @Test
    void exchangeChangesBothInventoriesOnlyWhenBothItemsExist() {
        Household buyer = new Household(UUID.randomUUID());
        Household seller = new Household(UUID.randomUUID());
        buyer.deposit(new ItemStack(Items.WHEAT, 2));
        assertFalse(buyer.exchangeOne(seller, stack -> stack.is(Items.WHEAT), stack -> stack.is(Items.BREAD)));
        assertEquals(2, buyer.storageContents().getFirst().getCount());
        seller.deposit(new ItemStack(Items.BREAD));
        assertTrue(buyer.exchangeOne(seller, stack -> stack.is(Items.WHEAT), stack -> stack.is(Items.BREAD)));
        assertEquals(2, buyer.foodCount());
        assertEquals(1, seller.foodCount());
        assertEquals(1, seller.requestFromStorage(Items.WHEAT, 1).getCount());
    }

    @Test
    @SuppressWarnings("unchecked")
    void inventoryComponentsAndCountsSurviveSaving() throws Exception {
        HouseholdSavedData data = new HouseholdSavedData();
        UUID householdId = data.createHousehold();
        UUID citizenId = UUID.randomUUID();
        data.addMember(householdId, citizenId);
        ItemStack bread = new ItemStack(Items.BREAD, 3);
        bread.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Household bread"));
        data.getHousehold(householdId).deposit(bread);
        var field = HouseholdSavedData.class.getDeclaredField("CODEC");
        field.setAccessible(true);
        var codec = (com.mojang.serialization.Codec<HouseholdSavedData>) field.get(null);
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,
                net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        HouseholdSavedData restored = codec.parse(ops, codec.encodeStart(ops, data).getOrThrow()).getOrThrow();
        assertTrue(restored.hasMember(householdId, citizenId));
        ItemStack savedBread = restored.getHousehold(householdId).storageContents().getFirst();
        assertEquals(3, savedBread.getCount());
        assertTrue(ItemStack.isSameItemSameComponents(bread, savedBread));
    }

    @Test
    void fullHouseholdReturnsUndepositedItems() {
        Household household = new Household(UUID.randomUUID());
        household.deposit(new ItemStack(Items.WHEAT, 9 * 64));
        ItemStack remainder = household.deposit(new ItemStack(Items.BREAD, 2));
        assertEquals(2, remainder.getCount());
        assertEquals(0, household.foodShortage());
        assertEquals(9, household.storageSize());
    }

    @Test
    void capacityFailureRollsBackBothSidesOfAnExchange() {
        Household buyer = new Household(UUID.randomUUID());
        Household seller = new Household(UUID.randomUUID());
        buyer.deposit(new ItemStack(Items.WHEAT, 9 * 64));
        seller.deposit(new ItemStack(Items.BREAD, 2));
        assertFalse(buyer.exchangeOne(seller, stack -> stack.is(Items.WHEAT), stack -> stack.is(Items.BREAD)));
        assertEquals(9 * 64, buyer.storageContents().stream().mapToInt(ItemStack::getCount).sum());
        assertEquals(2, seller.foodCount());
    }

    @Test
    void dailyFoodAndSurplusSalesRunOnlyOncePerDay() {
        Household household = new Household(UUID.randomUUID(), java.util.Set.of(UUID.randomUUID(), UUID.randomUUID()));
        household.deposit(new ItemStack(Items.WHEAT, 40));
        assertFalse(household.processEconomyDay(0));
        assertTrue(household.processEconomyDay(1));
        assertFalse(household.processEconomyDay(1));
        assertEquals(34, household.foodCount());
        assertEquals(104, household.coins());
        assertEquals(0, household.foodShortage());
        household.requestFromStorage(Items.WHEAT, 34);
        assertTrue(household.processEconomyDay(2));
        assertEquals(2, household.foodShortage());
    }
}
