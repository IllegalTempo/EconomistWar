package com.economistwars.citizen;

import java.util.*;
import com.economistwars.household.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class SettlementMarketStateTest {
    @BeforeAll static void bootstrap() { CitizenStateTest.bootstrap(); }
    @Test void marketTradesWithoutLoadedBlockAndPersistsHistory() {
        HouseholdSavedData households = new HouseholdSavedData();
        Household seller = households.getHousehold(households.createHousehold());
        Household buyer = households.getHousehold(households.createHousehold());
        seller.deposit(new ItemStack(Items.BREAD)); buyer.deposit(new ItemStack(Items.WHEAT,2));
        SettlementMarketState market = new SettlementMarketState(new CitizenAssetKey("minecraft:overworld",BlockPos.ZERO));
        market.offer(seller.id(),new ItemStack(Items.BREAD),new ItemStack(Items.WHEAT),-1,0);
        CitizenState citizen = CitizenStateTest.citizen(); citizen.householdId = buyer.id();
        citizen.needs = CitizenNeed.defaults(80,0,0,0);
        assertTrue(market.visit(buyer.id(),citizen.needs,citizen,households::getHousehold,100));
        assertEquals(1,buyer.requestFromStorage(Items.BREAD,1).getCount());
        assertEquals(1,seller.requestFromStorage(Items.WHEAT,1).getCount());
        assertEquals(1,market.snapshot().history().size());
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,
                net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        var loaded = SettlementMarketState.CODEC.parse(ops,SettlementMarketState.CODEC.encodeStart(ops,market).getOrThrow()).getOrThrow();
        assertEquals(1,loaded.snapshot().history().size());
    }
    @Test void staleMarketSnapshotCannotReplaceOffers() {
        SettlementMarketSavedData data = new SettlementMarketSavedData();
        CitizenAssetKey key = new CitizenAssetKey("minecraft:overworld",BlockPos.ZERO);
        SettlementMarketState first = new SettlementMarketState(key);
        first.offer(UUID.randomUUID(),new ItemStack(Items.BREAD),new ItemStack(Items.WHEAT),-1,0);
        data.registerIfAbsent(key,first);
        data.registerIfAbsent(key,new SettlementMarketState(key));
        assertEquals(1,data.find(key).orElseThrow().snapshot().offers().size());
    }
}

