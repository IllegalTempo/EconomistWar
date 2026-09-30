package com.economistwars.citizen;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** How many points of each need an item satisfies. */
public record ItemNeedValues(int eat, int entertainment, int safety) {
    private static final ItemNeedValues DEFAULT = new ItemNeedValues(1, 1, 1);

    public static ItemNeedValues forItem(Item item) {
        if (item == Items.APPLE) return new ItemNeedValues(3, 0, 0);
        if (item == Items.BREAD) return new ItemNeedValues(5, 0, 0);
        return DEFAULT;
    }

    public double utility(CitizenNeeds needs) {
        return eat * needs.eat() / 100.0
                + entertainment * needs.entertainment() / 100.0
                + safety * needs.safety() / 100.0;
    }

    public boolean satisfiesUrgentNeed(CitizenNeeds needs) {
        return needs.eat() >= 50 && eat > 0
                || needs.entertainment() >= 50 && entertainment > 0
                || needs.safety() >= 50 && safety > 0;
    }
}
