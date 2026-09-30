package com.economistwars.household;

import java.util.UUID;
import net.minecraft.world.item.ItemStack;

/** Immutable snapshot of a completed one-for-one household barter. */
public record SettlementMarketTradeRecord(
        UUID sellerId,
        UUID buyerId,
        ItemStack sellerItem,
        ItemStack buyerItem,
        long gameTime
) {
    public SettlementMarketTradeRecord {
        sellerItem = sellerItem.copyWithCount(1);
        buyerItem = buyerItem.copyWithCount(1);
    }
}
