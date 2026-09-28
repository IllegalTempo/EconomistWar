package com.economistwars.household;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class HouseholdHomeBlockEntity extends BlockEntity {
    private long nextAttemptAt;

    public HouseholdHomeBlockEntity(BlockPos position, BlockState state) {
        super(HouseholdHomeBlock.BLOCK_ENTITY_TYPE, position, state);
    }

    public void serverTick(ServerLevel level) {
        HouseholdHomeSavedData savedData = HouseholdHomeSavedData.get(level);
        if (savedData.isPopulated(level.dimension(), worldPosition)) {
            return;
        }

        long gameTime = level.getGameTime();
        if (gameTime < nextAttemptAt) {
            return;
        }
        nextAttemptAt = gameTime + 200;

        if (CitizenHouseholdSpawner.spawnCouple(level, worldPosition)) {
            savedData.markPopulated(level.dimension(), worldPosition);
        }
    }
}
