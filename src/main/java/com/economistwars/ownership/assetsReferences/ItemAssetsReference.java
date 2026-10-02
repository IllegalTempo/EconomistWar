package com.economistwars.ownership.assetsReferences;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.Objects;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Records a complete owned ItemStack, including its count and all components. */
public final class ItemAssetsReference extends AssetsReference {
    private final ItemStack stack;

    public ItemAssetsReference(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty() || stack.getCount() > 99) {
            throw new IllegalArgumentException("An item asset must contain between 1 and 99 items");
        }
        this.stack = stack.copy();
    }

    public ItemAssetsReference(Item item, int amount) {
        this(new ItemStack(Objects.requireNonNull(item, "item"), amount));
    }

    public Item item() { return stack.getItem(); }
    public int amount() { return stack.getCount(); }
    public ItemStack stack() { return stack.copy(); }

    public boolean matches(ItemStack candidate) {
        return ItemStack.matches(stack, candidate);
    }

    @Override public String getType() { return "item"; }

    @Override public String serializePayload() {
        return serializePayload(JsonOps.INSTANCE);
    }

    @Override public String serializePayload(com.mojang.serialization.DynamicOps<?> ops) {
        return ItemStack.CODEC.encodeStart(registryOps(ops), stack).getOrThrow().toString();
    }

    public static ItemAssetsReference parsePayload(String payload) {
        return parsePayload(payload, JsonOps.INSTANCE);
    }

    public static ItemAssetsReference parsePayload(String payload, com.mojang.serialization.DynamicOps<?> ops) {
        try {
            ItemStack stack = ItemStack.CODEC.parse(registryOps(ops), JsonParser.parseString(payload)).result()
                    .orElseThrow(() -> new IllegalArgumentException("Invalid item asset"));
            return new ItemAssetsReference(stack);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid item asset payload", exception);
        }
    }

    private static RegistryOps<com.google.gson.JsonElement> registryOps(com.mojang.serialization.DynamicOps<?> ops) {
        if (ops instanceof RegistryOps<?> registries) return registries.withParent(JsonOps.INSTANCE);
        return RegistryOps.create(JsonOps.INSTANCE,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }
}
