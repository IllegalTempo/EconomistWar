package com.economistwars.citizen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettlementMarketSavedDataTest {
    private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION, Identifier.parse("minecraft:overworld"));
    private static final ResourceKey<Level> NETHER = ResourceKey.create(Registries.DIMENSION, Identifier.parse("minecraft:the_nether"));
    @Test
    void nearestMarketRespectsRangeAndDimension() {
        SettlementMarketSavedData markets = new SettlementMarketSavedData();
        BlockPos home = new BlockPos(0, 64, 0);
        assertTrue(markets.nearestPosition(OVERWORLD, home, 128).isEmpty());
        markets.register(NETHER, home);
        markets.register(OVERWORLD, new BlockPos(128, 64, 0));
        assertEquals(new BlockPos(128, 64, 0), markets.nearestPosition(OVERWORLD, home, 128).orElseThrow());
        assertTrue(markets.nearestPosition(OVERWORLD, home, 127).isEmpty());
        markets.register(OVERWORLD, new BlockPos(10, 64, 0));
        assertEquals(new BlockPos(10, 64, 0), markets.nearestPosition(OVERWORLD, home, 128).orElseThrow());
    }
}
