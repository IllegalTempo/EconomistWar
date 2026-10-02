package com.economistwars.citizen;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CitizenNeedTest {
    @org.junit.jupiter.api.Test
    void shortageDamageUsesConfiguredRateAndDailyCap() {
        CitizenNeed need = new CitizenNeed("eat",100,25,1.5F,0);
        org.junit.jupiter.api.Assertions.assertEquals(0.0F, need.damageForShortage(-1));
        org.junit.jupiter.api.Assertions.assertEquals(3.0F, need.damageForShortage(2));
        org.junit.jupiter.api.Assertions.assertEquals(4.0F, need.damageForShortage(10));
        org.junit.jupiter.api.Assertions.assertEquals(0.0F, new CitizenNeed("safety",100,1,0,0).damageForShortage(5));
    }
    @Test
    void customNeedDefinitionAndStateSurviveSerialization() {
        CitizenNeed need = new CitizenNeed("thirst", 80, 20, 2.0F, 10);
        var encoded = CitizenNeed.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, need)
                .getOrThrow();
        CitizenNeed restored = CitizenNeed.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encoded)
                .getOrThrow();
        assertEquals(need.name(), restored.name());
        assertEquals(need.urgency(), restored.urgency());
        assertEquals(need.dailyGrowth(), restored.dailyGrowth());
        assertEquals(need.shortageDamage(), restored.shortageDamage());
        assertEquals(need.lastUpdatedDay(), restored.lastUpdatedDay());
    }

    @Test
    void customNeedsGrowCatchUpAndNeverMoveBackwards() {
        CitizenNeed thirst = new CitizenNeed("thirst", 0, 20, 2.0F, 10);
        thirst.advanceToDay(13);
        assertEquals(60, thirst.urgency());
        thirst.advanceToDay(2);
        assertEquals(60, thirst.urgency());
        assertEquals(13, thirst.lastUpdatedDay());
        thirst.advanceToDay(Long.MAX_VALUE);
        assertEquals(100, thirst.urgency());
    }

    @Test
    void satisfactionAndInitialValuesAreClamped() {
        CitizenNeed need = new CitizenNeed("thirst", 150, 20, 2.0F, -1);
        assertEquals(100, need.urgency());
        assertEquals(0, need.lastUpdatedDay());
        need.satisfy(-10);
        assertEquals(100, need.urgency());
        need.satisfy(200);
        assertEquals(0, need.urgency());
    }

    @Test
    void zeroGrowthAndShortageDamageAreSupported() {
        CitizenNeed need = new CitizenNeed("shelter", 50, 0, 0.0F, 0);
        need.advanceToDay(Long.MAX_VALUE);
        assertEquals(50, need.urgency());
        assertEquals(0.0F, need.shortageDamage());
        assertThrows(IllegalArgumentException.class,
                () -> new CitizenNeed("", 0, 1, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new CitizenNeed("thirst", 0, -1, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new CitizenNeed("thirst", 0, 1, Float.NaN, 0));
    }

    @Test
    void collectionsHaveIndependentNeedsAndPreserveExistingRates() {
        var needs = CitizenNeed.defaults(0, 0, 0, 10);
        var other = CitizenNeed.defaults(0, 0, 0, 10);
        needs.values().forEach(need -> need.advanceToDay(13));
        assertEquals(75, CitizenNeed.urgency(needs, CitizenNeed.EAT));
        assertEquals(36, CitizenNeed.urgency(needs, CitizenNeed.ENTERTAINMENT));
        assertEquals(18, CitizenNeed.urgency(needs, CitizenNeed.SAFETY));
        assertEquals(0, CitizenNeed.urgency(other, CitizenNeed.EAT));
        assertEquals(0, CitizenNeed.urgency(needs, "missing"));
    }
}
