package com.economistwars.household;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Real saved ItemStacks, intentionally inaccessible to players and hoppers. */
public final class HouseholdStorageBlockEntity extends BlockEntity {
    private static final int SLOTS = 9;
    private UUID owner;
    private final List<ItemStack> wheat = new ArrayList<>(SLOTS);

    public HouseholdStorageBlockEntity(BlockPos position, BlockState state) {
        super(HouseholdStorageBlock.BLOCK_ENTITY_TYPE, position, state);
        for (int slot = 0; slot < SLOTS; slot++) {
            wheat.add(ItemStack.EMPTY);
        }
    }

    public boolean claim(UUID householdId) {
        if (householdId == null || (owner != null && !owner.equals(householdId))) {
            return false;
        }
        owner = householdId;
        setChanged();
        return true;
    }

    public boolean belongsTo(UUID householdId) {
        return owner != null && owner.equals(householdId);
    }

    public ItemStack insert(UUID householdId, ItemStack offered) {
        if (!belongsTo(householdId) || !offered.is(Items.WHEAT) || offered.isEmpty()) {
            return offered.copy();
        }
        ItemStack remainder = offered.copy();
        for (int slot = 0; slot < SLOTS && !remainder.isEmpty(); slot++) {
            ItemStack stored = wheat.get(slot);
            if (stored.isEmpty()) {
                int count = Math.min(64, remainder.getCount());
                wheat.set(slot, remainder.copyWithCount(count));
                remainder.shrink(count);
            } else if (ItemStack.isSameItemSameComponents(stored, remainder) && stored.getCount() < 64) {
                int count = Math.min(64 - stored.getCount(), remainder.getCount());
                stored.grow(count);
                remainder.shrink(count);
            }
        }
        if (remainder.getCount() != offered.getCount()) {
            setChanged();
        }
        return remainder;
    }

    public int takeWheat(UUID householdId, int requested) {
        if (!belongsTo(householdId) || requested <= 0) {
            return 0;
        }
        int taken = 0;
        for (int slot = 0; slot < SLOTS && taken < requested; slot++) {
            ItemStack stored = wheat.get(slot);
            int count = Math.min(requested - taken, stored.getCount());
            if (count > 0) {
                stored.shrink(count);
                taken += count;
                if (stored.isEmpty()) {
                    wheat.set(slot, ItemStack.EMPTY);
                }
            }
        }
        if (taken > 0) {
            setChanged();
        }
        return taken;
    }

    public int wheatCount(UUID householdId) {
        if (!belongsTo(householdId)) {
            return 0;
        }
        return wheat.stream().mapToInt(ItemStack::getCount).sum();
    }

    public int freeWheatCapacity(UUID householdId) {
        return belongsTo(householdId) ? SLOTS * 64 - wheatCount(householdId) : 0;
    }

    public void abandon(UUID householdId) {
        if (!belongsTo(householdId)) {
            return;
        }
        owner = null;
        for (int slot = 0; slot < SLOTS; slot++) {
            wheat.set(slot, ItemStack.EMPTY);
        }
        setChanged();
    }

    public int getContainerSize() {
        return SLOTS;
    }

    public boolean isEmpty() {
        return wheat.stream().allMatch(ItemStack::isEmpty);
    }

    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < SLOTS ? wheat.get(slot) : ItemStack.EMPTY;
    }

    public ItemStack removeItem(int slot, int amount) {
        if (slot < 0 || slot >= SLOTS || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = net.minecraft.world.ContainerHelper.removeItem(wheat, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= SLOTS) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = wheat.set(slot, ItemStack.EMPTY);
        setChanged();
        return removed;
    }

    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOTS) {
            return;
        }
        wheat.set(slot, stack.is(Items.WHEAT) ? stack.copyWithCount(Math.min(64, stack.getCount())) : ItemStack.EMPTY);
        setChanged();
    }

    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        return player.isCreative() && Container.stillValidBlockEntity(this, player);
    }

    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.is(Items.WHEAT);
    }

    public void clearContent() {
        wheat.replaceAll(ignored -> ItemStack.EMPTY);
        setChanged();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        owner = input.getString("Owner").flatMap(value -> {
            try {
                return java.util.Optional.of(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
                return java.util.Optional.empty();
            }
        }).orElse(null);
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = input.read("Wheat" + slot, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
            wheat.set(slot, stack.is(Items.WHEAT) ? stack : ItemStack.EMPTY);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (owner != null) {
            output.putString("Owner", owner.toString());
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            if (!wheat.get(slot).isEmpty()) {
                output.store("Wheat" + slot, ItemStack.OPTIONAL_CODEC, wheat.get(slot));
            }
        }
    }
}
