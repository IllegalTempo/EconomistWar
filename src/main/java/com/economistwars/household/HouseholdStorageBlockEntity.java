package com.economistwars.household;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Physical access point for the inventory owned and saved by a household. */
public final class HouseholdStorageBlockEntity extends BlockEntity {
    private UUID owner;

    public HouseholdStorageBlockEntity(BlockPos position, BlockState state) {
        super(HouseholdStorageBlock.BLOCK_ENTITY_TYPE, position, state);
    }

    public Optional<UUID> ownerId() {
        return Optional.ofNullable(owner);
    }

    public boolean claim(UUID householdId) {
        if (householdId == null || (owner != null && !owner.equals(householdId))) return false;
        if (!householdId.equals(owner)) {
            owner = householdId;
            setChanged();
        }
        return true;
    }

    public void abandon(UUID householdId) {
        if (!householdId.equals(owner)) return;
        owner = null;
        setChanged();
    }

    public boolean stillValid(Player player) {
        return player.isCreative() && Container.stillValidBlockEntity(this, player);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        owner = input.getString("Owner").flatMap(value -> {
            try {
                return Optional.of(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (owner != null) output.putString("Owner", owner.toString());
    }
}
