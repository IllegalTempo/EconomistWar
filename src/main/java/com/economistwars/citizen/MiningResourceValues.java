package com.economistwars.citizen;

import com.economistwars.EconomistWars;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Datapack-configurable resource-point costs for each mining drop item. */
public final class MiningResourceValues {
    private static final Identifier DATA_ID = Identifier.fromNamespaceAndPath(EconomistWars.MOD_ID, "mining_resource_values.json");
    private static final Identifier LISTENER_ID = Identifier.fromNamespaceAndPath(EconomistWars.MOD_ID, "mining_resource_values");
    private static volatile int defaultCost = 1;
    private static volatile Map<Item, Integer> costs = Map.of();

    private MiningResourceValues() {}

    public static void initialize() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return LISTENER_ID;
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                loadCosts(manager);
            }
        });
    }

    public static int cost(ItemStack stack) {
        return stack.isEmpty() ? defaultCost : costs.getOrDefault(stack.getItem(), defaultCost);
    }

    public static MiningYield affordableDrops(List<ItemStack> drops, int available) {
        int remaining = Math.max(0, available);
        int spent = 0;
        ArrayList<ItemStack> payable = new ArrayList<>();
        for (ItemStack drop : drops) {
            if (drop == null || drop.isEmpty() || remaining == 0) continue;
            int cost = cost(drop);
            int count = Math.min(drop.getCount(), remaining / cost);
            if (count > 0) {
                payable.add(drop.copyWithCount(count));
                int itemCost = count * cost;
                remaining -= itemCost;
                spent += itemCost;
            }
        }
        return new MiningYield(List.copyOf(payable), spent);
    }

    private static void loadCosts(ResourceManager manager) {
        int loadedDefault = 1;
        Map<Item, Integer> loadedCosts = new HashMap<>();
        try {
            var resource = manager.getResource(DATA_ID);
            if (resource.isPresent()) {
                JsonObject root = JsonParser.parseString(resource.get().readAllAsString()).getAsJsonObject();
                loadedDefault = positiveInt(root.get("default"), 1);
                JsonElement itemElement = root.get("items");
                if (itemElement != null && itemElement.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> entry : itemElement.getAsJsonObject().entrySet()) {
                        Identifier itemId = Identifier.tryParse(entry.getKey());
                        int value = positiveInt(entry.getValue(), loadedDefault);
                        if (itemId == null || value <= 0) continue;
                        BuiltInRegistries.ITEM.getOptional(itemId).ifPresent(item -> loadedCosts.put(item, value));
                    }
                }
            }
        } catch (IOException | RuntimeException exception) {
            EconomistWars.LOGGER.error("Could not load mining resource costs; using defaults", exception);
            loadedDefault = 1;
            loadedCosts.clear();
        }
        defaultCost = loadedDefault;
        costs = Map.copyOf(loadedCosts);
        EconomistWars.LOGGER.info("Loaded {} mining resource item costs (default {})", costs.size(), defaultCost);
    }

    private static int positiveInt(JsonElement value, int fallback) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return fallback;
        try {
            int parsed = value.getAsInt();
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public record MiningYield(List<ItemStack> drops, int resourceCost) {}
}
