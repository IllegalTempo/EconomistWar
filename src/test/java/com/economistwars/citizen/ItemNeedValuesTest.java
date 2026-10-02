package com.economistwars.citizen;

import java.util.Map;

import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ItemNeedValuesTest {
    @Test
    void customNeedsParticipateInUtilityUrgencyAndSatisfaction() {
        var needs = CitizenNeed.defaults(0, 0, 0, 0);
        CitizenNeed thirst = new CitizenNeed("thirst", 80, 20, 2.0F, 0);
        needs.put(thirst.name(), thirst);
        ItemNeedValues water = new ItemNeedValues(java.util.Map.of("thirst", 40));

        assertEquals(32.0, water.utility(needs), 0.0001);
        assertTrue(water.satisfiesUrgentNeed(needs));
        water.satisfy(needs);
        assertEquals(40, thirst.urgency());
        assertFalse(water.satisfiesUrgentNeed(needs));
        assertEquals(0, CitizenNeed.urgency(needs, CitizenNeed.EAT));
    }

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
        Map<String, CitizenNeed> needs = CitizenNeed.defaults(50, 25, 100, 0);

        assertEquals(2.5, ItemNeedValues.forItem(Items.BREAD).utility(needs), 0.0001);
    }
}
