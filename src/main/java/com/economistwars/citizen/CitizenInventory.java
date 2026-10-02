package com.economistwars.citizen;

import java.util.Arrays;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Atomic, capacity-checked personal inventory independent of an entity. */
public final class CitizenInventory {
    private ItemStack[] slots = new ItemStack[36];
    public CitizenInventory() { Arrays.fill(slots, ItemStack.EMPTY); }
    public List<ItemStack> contents() { return Arrays.stream(slots).map(ItemStack::copy).toList(); }
    public ItemStack get(int slot) { return slots[slot].copy(); }
    public void set(int slot, ItemStack item) { slots[slot] = item.copy(); }
    public boolean isEmpty() { return Arrays.stream(slots).allMatch(ItemStack::isEmpty); }
    public boolean full() { return Arrays.stream(slots).allMatch(s -> !s.isEmpty() && s.getCount() >= s.getMaxStackSize()); }
    public int count(Item item) { return Arrays.stream(slots).filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum(); }
    public boolean canInsert(List<ItemStack> items) { return inserted(items) != null; }
    public boolean insert(List<ItemStack> items) {
        ItemStack[] changed = inserted(items);
        if (changed == null) return false;
        slots = changed;
        return true;
    }
    private ItemStack[] inserted(List<ItemStack> items) {
        ItemStack[] copy = Arrays.stream(slots).map(ItemStack::copy).toArray(ItemStack[]::new);
        for (ItemStack offered : items) if (!insertInto(copy, offered)) return null;
        return copy;
    }
    private static boolean insertInto(ItemStack[] copy, ItemStack offered) {
        ItemStack left = offered.copy();
        for (ItemStack slot : copy) {
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, left)) {
                int amount = Math.min(left.getCount(), Math.max(0, slot.getMaxStackSize() - slot.getCount()));
                slot.grow(amount); left.shrink(amount);
            }
        }
        for (int i = 0; i < copy.length && !left.isEmpty(); i++) if (copy[i].isEmpty()) {
            int amount = Math.min(left.getCount(), left.getMaxStackSize());
            copy[i] = left.copyWithCount(amount); left.shrink(amount);
        }
        return left.isEmpty();
    }
    public boolean canBakeBread() { return baked() != null; }
    public boolean bakeBread() {
        ItemStack[] baked = baked();
        if (baked == null) return false;
        slots = baked;
        return true;
    }
    private ItemStack[] baked() {
        ItemStack[] copy = Arrays.stream(slots).map(ItemStack::copy).toArray(ItemStack[]::new);
        int needed = 3;
        for (ItemStack stack : copy) if (stack.is(Items.WHEAT)) {
            int consumed = Math.min(needed, stack.getCount());
            stack.shrink(consumed); needed -= consumed;
        }
        return needed == 0 && insertInto(copy, new ItemStack(Items.BREAD)) ? copy : null;
    }
}
