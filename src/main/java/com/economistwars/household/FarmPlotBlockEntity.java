package com.economistwars.household;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class FarmPlotBlockEntity extends BlockEntity {
    private boolean registered;

    public FarmPlotBlockEntity(BlockPos position, BlockState state) {
        super(FarmPlotBlock.BLOCK_ENTITY_TYPE, position, state);
    }

    public void serverTick(ServerLevel level) {
        if (!registered) {
            com.economistwars.ownership.OwnershipSavedData.get(level).registerParcel(level, worldPosition);
            registered = true;
        }
    }
}
