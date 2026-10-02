package com.economistwars.citizen;

import java.util.Map;

import java.util.List;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarterTradeChoiceTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void choosesTheCompatibleTradeWithTheLowestTradedMinusRequestedUtility() {
        Map<String, CitizenNeed> needs = CitizenNeed.defaults(100, 0, 0, 0);

        var choice = BarterTradeChoice.best(
                List.of(Items.BREAD, Items.STONE), List.of(Items.APPLE, Items.BREAD), needs);

        assertTrue(choice.isPresent());
        assertEquals(Items.STONE, choice.get().traded());
        assertEquals(Items.BREAD, choice.get().requested());
        assertEquals(-4.0, choice.get().score(), 0.0001);
    }

    @Test
    void rejectsTradesThatDoNotImproveTheBuyersUtility() {
        Map<String, CitizenNeed> needs = CitizenNeed.defaults(100, 0, 0, 0);

        assertTrue(BarterTradeChoice.best(
                List.of(Items.BREAD), List.of(Items.APPLE), needs).isEmpty());
    }

    @Test
    void marketOpportunityRequiresAStoredRequestedItemAndPositiveNeedUtilityGain() {
        Map<String, CitizenNeed> hungry = CitizenNeed.defaults(100, 0, 0, 0);
        var offers = List.of(new BarterTradeChoice.TradeOffer(Items.BREAD, Items.STONE));

        assertEquals(4.0, BarterTradeChoice.bestPotentialBenefit(
                List.of(Items.STONE), offers, hungry), 0.0001);
        assertEquals(0.0, BarterTradeChoice.bestPotentialBenefit(
                List.of(Items.APPLE), offers, hungry), 0.0001);
        assertEquals(0.0, BarterTradeChoice.bestPotentialBenefit(
                List.of(Items.STONE), offers, CitizenNeed.defaults(0, 0, 0, 0)), 0.0001);
    }
}
