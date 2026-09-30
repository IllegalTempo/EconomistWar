package com.economistwars.household;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
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
    private final List<ItemStack> items = new ArrayList<>(SLOTS);

    public HouseholdStorageBlockEntity(BlockPos position, BlockState state) {
        super(HouseholdStorageBlock.BLOCK_ENTITY_TYPE, position, state);
        for (int slot = 0; slot < SLOTS; slot++) {
            items.add(ItemStack.EMPTY);
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
        if (!belongsTo(householdId) || offered.isEmpty()) {
            return offered.copy();
        }
        ItemStack remainder = offered.copy();
        for (int slot = 0; slot < SLOTS && !remainder.isEmpty(); slot++) {
            ItemStack stored = items.get(slot);
            int stackLimit = Math.min(64, remainder.getMaxStackSize());
            if (stored.isEmpty()) {
                int count = Math.min(stackLimit, remainder.getCount());
                items.set(slot, remainder.copyWithCount(count));
                remainder.shrink(count);
            } else if (ItemStack.isSameItemSameComponents(stored, remainder) && stored.getCount() < stackLimit) {
                int count = Math.min(stackLimit - stored.getCount(), remainder.getCount());
                stored.grow(count);
                remainder.shrink(count);
            }
        }
        if (remainder.getCount() != offered.getCount()) {
            setChanged();
        }
        return remainder;
    }

    public List<ItemStack> contents(UUID householdId) {
        if (!belongsTo(householdId)) return List.of();
        return items.stream().map(ItemStack::copy).toList();
    }

    public ItemStack takeOne(UUID householdId, Predicate<ItemStack> matches) {
        if (!belongsTo(householdId)) return ItemStack.EMPTY;
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stored = items.get(slot);
            if (stored.isEmpty() || !matches.test(stored)) continue;
            ItemStack taken = stored.copyWithCount(1);
            stored.shrink(1);
            if (stored.isEmpty()) items.set(slot, ItemStack.EMPTY);
            setChanged();
            return taken;
        }
        return ItemStack.EMPTY;
    }

    /** Swaps one item each way, restoring both inventories if either insertion fails. */
    public boolean exchangeOne(UUID ownerId, HouseholdStorageBlockEntity other, UUID otherOwnerId,
            Predicate<ItemStack> ownItem, Predicate<ItemStack> otherItem) {
        if (other == null || other == this || !belongsTo(ownerId) || !other.belongsTo(otherOwnerId)) return false;
        List<ItemStack> before = contents(ownerId);
        List<ItemStack> otherBefore = other.contents(otherOwnerId);
        ItemStack offered = takeOne(ownerId, ownItem);
        ItemStack requested = other.takeOne(otherOwnerId, otherItem);
        if (offered.isEmpty() || requested.isEmpty()) {
            restore(ownerId, before);
            other.restore(otherOwnerId, otherBefore);
            return false;
        }
        if (!insert(ownerId, requested).isEmpty() || !other.insert(otherOwnerId, offered).isEmpty()) {
            restore(ownerId, before);
            other.restore(otherOwnerId, otherBefore);
            return false;
        }
        return true;
    }

    private void restore(UUID householdId, List<ItemStack> snapshot) {
        if (!belongsTo(householdId)) return;
        for (int slot = 0; slot < SLOTS; slot++) {
            items.set(slot, slot < snapshot.size() ? snapshot.get(slot).copy() : ItemStack.EMPTY);
        }
        setChanged();
    }

    public int takeWheat(UUID householdId, int requested) {
        if (!belongsTo(householdId) || requested <= 0) {
            return 0;
        }
        int taken = 0;
        for (int slot = 0; slot < SLOTS && taken < requested; slot++) {
            ItemStack stored = items.get(slot);
            if (!stored.is(Items.WHEAT)) {
                continue;
            }
            int count = Math.min(requested - taken, stored.getCount());
            if (count > 0) {
                stored.shrink(count);
                taken += count;
                if (stored.isEmpty()) {
                    items.set(slot, ItemStack.EMPTY);
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
        return items.stream().filter(stack -> stack.is(Items.WHEAT)).mapToInt(ItemStack::getCount).sum();
    }

    public int freeWheatCapacity(UUID householdId) {
        if (!belongsTo(householdId)) {
            return 0;
        }
        return items.stream().mapToInt(stack -> stack.isEmpty() || stack.is(Items.WHEAT)
                ? 64 - stack.getCount() : 0).sum();
    }

    public void abandon(UUID householdId) {
        if (!belongsTo(householdId)) {
            return;
        }
        owner = null;
        for (int slot = 0; slot < SLOTS; slot++) {
            items.set(slot, ItemStack.EMPTY);
        }
        setChanged();
    }

    public int getContainerSize() {
        return SLOTS;
    }

    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < SLOTS ? items.get(slot) : ItemStack.EMPTY;
    }

    public ItemStack removeItem(int slot, int amount) {
        if (slot < 0 || slot >= SLOTS || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = net.minecraft.world.ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= SLOTS) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = items.set(slot, ItemStack.EMPTY);
        setChanged();
        return removed;
    }

    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOTS) {
            return;
        }
        items.set(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(Math.min(Math.min(64, stack.getMaxStackSize()), stack.getCount())));
        setChanged();
    }

    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        return player.isCreative() && Container.stillValidBlockEntity(this, player);
    }

    public boolean canPlaceItem(int slot, ItemStack stack) {
        return true;
    }

    public void clearContent() {
        items.replaceAll(ignored -> ItemStack.EMPTY);
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
            java.util.Optional<ItemStack> saved = input.read("Item" + slot, ItemStack.OPTIONAL_CODEC);
            if (saved.isEmpty()) {
                saved = input.read("Wheat" + slot, ItemStack.OPTIONAL_CODEC);
            }
            ItemStack stack = saved.orElse(ItemStack.EMPTY);
            items.set(slot, stack);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (owner != null) {
            output.putString("Owner", owner.toString());
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            if (!items.get(slot).isEmpty()) {
                output.store("Item" + slot, ItemStack.OPTIONAL_CODEC, items.get(slot));
            }
        }
    }
}
