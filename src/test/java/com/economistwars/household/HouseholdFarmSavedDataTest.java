package com.economistwars.household;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HouseholdFarmSavedDataTest {
    @Test
    void shortageDamageScalesWithMissingFoodAndHasADailyCap() {
        assertEquals(0.0F, HouseholdFarmSavedData.shortageDamage(0));
        assertEquals(2.0F, HouseholdFarmSavedData.shortageDamage(2));
        assertEquals(4.0F, HouseholdFarmSavedData.shortageDamage(20));
    }
}
