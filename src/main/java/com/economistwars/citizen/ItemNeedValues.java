package com.economistwars.citizen;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import java.util.Map;

/** How many points of each need an item satisfies. */
public record ItemNeedValues(Map<String, Integer> satisfaction) {
    public ItemNeedValues {
        satisfaction = Map.copyOf(satisfaction);
        if (satisfaction.values().stream().anyMatch(amount -> amount < 0))
            throw new IllegalArgumentException("Satisfaction must not be negative");
    }

    public ItemNeedValues(int eat, int entertainment, int safety) {
        this(Map.of(CitizenNeed.EAT, eat, CitizenNeed.ENTERTAINMENT, entertainment, CitizenNeed.SAFETY, safety));
    }

    public int amount(String name) { return satisfaction.getOrDefault(name, 0); }
    public int eat() { return amount(CitizenNeed.EAT); }
    public int entertainment() { return amount(CitizenNeed.ENTERTAINMENT); }
    public int safety() { return amount(CitizenNeed.SAFETY); }
    private static final ItemNeedValues DEFAULT = new ItemNeedValues(1, 1, 1);

    public static ItemNeedValues forItem(Item item) {
        if (item == Items.APPLE) return new ItemNeedValues(3, 0, 0);
        if (item == Items.BREAD) return new ItemNeedValues(5, 0, 0);
        return DEFAULT;
    }

    public double utility(Map<String, CitizenNeed> needs) {
        return needs.values().stream().mapToDouble(need ->
                amount(need.name()) * (need.urgency() / 100.0)).sum();
    }

    public boolean satisfiesUrgentNeed(Map<String, CitizenNeed> needs) {
        return needs.values().stream().anyMatch(need -> need.isUrgent() && amount(need.name()) > 0);
    }

    public void satisfy(Map<String, CitizenNeed> needs) {
        needs.values().forEach(need -> need.satisfy(amount(need.name())));
    }
}
