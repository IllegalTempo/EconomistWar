package com.economistwars.citizen;

import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemNeedValuesTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void applesAndBreadHaveTheirConfiguredEatValues() {
        assertEquals(new ItemNeedValues(3, 0, 0), ItemNeedValues.forItem(Items.APPLE));
        assertEquals(new ItemNeedValues(5, 0, 0), ItemNeedValues.forItem(Items.BREAD));
    }

    @Test
    void otherItemsUseTheGenericFallback() {
        assertEquals(new ItemNeedValues(1, 1, 1), ItemNeedValues.forItem(Items.STONE));
    }

    @Test
    void utilityUsesNeedUrgencyAsAPercentage() {
        CitizenNeeds needs = new CitizenNeeds(50, 25, 100, 0);

        assertEquals(2.5, ItemNeedValues.forItem(Items.BREAD).utility(needs), 0.0001);
    }
}
