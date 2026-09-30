package com.economistwars.citizen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CitizenNeedsTest {
    @Test
    void needsGrowAtSharedDailyRatesAndCatchUpAfterUnloading() {
        CitizenNeeds needs = new CitizenNeeds(0, 0, 0, 10);

        needs.advanceToDay(13);

        assertEquals(75, needs.eat());
        assertEquals(36, needs.entertainment());
        assertEquals(18, needs.safety());
        assertEquals(13, needs.lastUpdatedDay());
    }

    @Test
    void needsClampAtOneHundredAndNeverMoveBackwardsInTime() {
        CitizenNeeds needs = new CitizenNeeds(90, 99, 100, 4);

        needs.advanceToDay(8);
        needs.advanceToDay(2);

        assertEquals(100, needs.eat());
        assertEquals(100, needs.entertainment());
        assertEquals(100, needs.safety());
        assertEquals(8, needs.lastUpdatedDay());
    }

    @Test
    void consumedItemSatisfactionReducesNeedsWithoutGoingBelowZero() {
        CitizenNeeds needs = new CitizenNeeds(2, 8, 0, 0);

        needs.satisfy(5, 12, 1);

        assertEquals(0, needs.eat());
        assertEquals(0, needs.entertainment());
        assertEquals(0, needs.safety());
    }
}
