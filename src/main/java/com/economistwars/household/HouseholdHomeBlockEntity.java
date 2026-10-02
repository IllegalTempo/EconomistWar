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
        if (level.getGameTime()%100 == 0) refreshBeds(level);
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
            refreshBeds(level);
        }
    }

    private void refreshBeds(ServerLevel level) {
        var beds = com.economistwars.citizen.HouseholdBedSavedData.get(level);
        java.util.UUID ownerId = null;
        for (var household : HouseholdSavedData.get(level).households())
            if (household.home(level.dimension()).filter(worldPosition::equals).isPresent()) { ownerId = household.id(); break; }
        if (ownerId == null) return;
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-5,-2,-5),worldPosition.offset(5,3,5))) {
            if (!level.hasChunkAt(p)) continue;
            var key = new com.economistwars.citizen.CitizenAssetKey(level.dimension().identifier().toString(),p.immutable());
            var state = level.getBlockState(p);
            if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock
                    && state.getValue(net.minecraft.world.level.block.BedBlock.PART)
                    == net.minecraft.world.level.block.state.properties.BedPart.FOOT) beds.register(ownerId,key);
            else beds.invalidate(key);
        }
    }

}

