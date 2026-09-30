package com.economistwars.citizen;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CitizenMarketMemoryTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void remembersHighestPositiveGainOfferForAProducibleItem() {
        CitizenNeeds needs = new CitizenNeeds(100, 0, 0, 0);
        var memory = CitizenMarketMemory.best(List.of(
                new CitizenMarketMemory.Offer(Items.APPLE, Items.WHEAT),
                new CitizenMarketMemory.Offer(Items.BREAD, Items.WHEAT)),
                List.of(Items.WHEAT), needs, BlockPos.ZERO);

        assertEquals(Items.BREAD, memory.received());
        assertEquals(Items.WHEAT, memory.requested());
        assertEquals(BlockPos.ZERO, memory.marketPosition());
    }

    @Test
    void noPositiveOrNonProducibleOfferClearsMemory() {
        CitizenNeeds needs = new CitizenNeeds(100, 0, 0, 0);
        assertNull(CitizenMarketMemory.best(List.of(
                new CitizenMarketMemory.Offer(Items.WHEAT, Items.BREAD)),
                List.of(Items.WHEAT), needs, BlockPos.ZERO));
        assertNull(CitizenMarketMemory.best(List.of(
                new CitizenMarketMemory.Offer(Items.BREAD, Items.WHEAT)),
                List.of(Items.BREAD), needs, BlockPos.ZERO));
    }

    @Test
    void rememberedGainUsesCurrentNeeds() {
        CitizenMarketMemory memory = new CitizenMarketMemory(Items.APPLE, Items.WHEAT, BlockPos.ZERO);
        assertEquals(2.0, memory.gain(new CitizenNeeds(100, 0, 0, 0)), 0.0001);
        assertEquals(-2.0, memory.gain(new CitizenNeeds(0, 100, 100, 0)), 0.0001);
    }

    @Test
    void rememberedOfferAndMarketPositionRoundTripThroughSavedData() {
        CitizenMarketMemory memory = new CitizenMarketMemory(
                Items.BREAD, Items.WHEAT, new BlockPos(23, 70, -41));

        assertEquals(memory, CitizenMarketMemory.load(memory.save()).orElseThrow());
        assertNull(CitizenMarketMemory.load(new CitizenMarketMemory.Saved("", "", 0L)).orElse(null));
    }
}
