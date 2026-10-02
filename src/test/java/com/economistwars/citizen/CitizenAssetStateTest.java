package com.economistwars.citizen;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CitizenAssetStateTest {
    final CitizenAssetKey key = new CitizenAssetKey("minecraft:overworld", new BlockPos(10,64,10));
    @Test void legacyImportDoesNotRefillMine() {
        MineSiteSavedData data = new MineSiteSavedData();
        MineSiteState site = data.registerIfAbsent(key, 10000, new BoundingBox(8,64,8,12,65,12));
        assertTrue(site.consume(1000));
        assertEquals(9000, data.registerIfAbsent(key, 10000, site.bounds()).remaining());
        assertFalse(site.consume(9001));
        assertEquals(9000, site.remaining());
    }
    @Test void invalidatedMineRejectsStaleRegistration() {
        MineSiteSavedData data = new MineSiteSavedData();
        data.registerIfAbsent(key,10000,null);
        data.invalidate(key);
        data.registerIfAbsent(key,10000,null);
        assertTrue(data.find(key).isEmpty());
    }
    @Test void bedReservationHasOneOwnerAndSurvivesSaving() {
        HouseholdBedSavedData beds = new HouseholdBedSavedData();
        UUID household = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
        beds.register(household,key);
        assertEquals(key,beds.reserve(household,first).orElseThrow());
        assertTrue(beds.reserve(household,second).isEmpty());
        var ops = com.mojang.serialization.JsonOps.INSTANCE;
        beds = HouseholdBedSavedData.CODEC.parse(ops, HouseholdBedSavedData.CODEC.encodeStart(ops,beds).getOrThrow()).getOrThrow();
        assertTrue(beds.reserve(household,second).isEmpty());
        beds.release(first);
        assertEquals(key,beds.reserve(household,second).orElseThrow());
    }
}
