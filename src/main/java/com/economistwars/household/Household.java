package com.economistwars.household;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;

public final class Household {
    private final UUID id;
    private final LinkedHashSet<UUID> members;

    Household(UUID id) {
        this(id, new LinkedHashSet<>());
    }

    Household(UUID id, Set<UUID> members) {
        this.id = id;
        this.members = new LinkedHashSet<>(members);
        this.householdStorage = new ArrayList<ItemStack>();
    }
    public List<ItemStack> householdStorage;
    public UUID id() {
        return id;
    }

    public Set<UUID> members() {
        return Set.copyOf(members);
    }

    boolean add(UUID citizenId) {
        return members.add(citizenId);
    }

    boolean remove(UUID citizenId) {
        return members.remove(citizenId);
    }

    boolean isEmpty() {
        return members.isEmpty();
    }
    public ItemStack requestFromStorage(Item item, int amount) {
        for (ItemStack stack: householdStorage)
        {
            if(stack.getItem().equals(item))
            {
                int availableAmount = stack.getCount();
                if(availableAmount >= amount)
                {
                    stack.setCount(availableAmount - amount);
                    return new ItemStack(item, amount);
                }
                else
                {
                    stack.setCount(0);
                    return new ItemStack(item, availableAmount);
                }
            }
        }
        return new ItemStack(Items.AIR, 0);
    }
    public ItemStack requestFromStorage(DataComponentType<?> dataComponentType, int amount) {
        for (ItemStack stack: householdStorage)
        {
            if(stack.has(dataComponentType))
            {
                int availableAmount = stack.getCount();
                if(availableAmount >= amount)
                {
                    stack.setCount(availableAmount - amount);
                    return new ItemStack(stack.getItem(), amount);
                }
                else
                {
                    stack.setCount(0);
                    return new ItemStack(stack.getItem(), availableAmount);
                }
            }
        }
        return new ItemStack(Items.AIR, 0);
    }
    public void AddToStorage(ItemStack item, int amount) {
        for (ItemStack stack: householdStorage)
        {
            if(stack.getItem().equals(item.getItem()))
            {
                stack.setCount(stack.getCount() + amount);
                return;
            }
        }
        ItemStack newStack = new ItemStack(item.getItem(), amount);
        householdStorage.add(newStack);
    }
}

