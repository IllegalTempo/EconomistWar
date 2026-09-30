package com.economistwars.citizen;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.Item;

/** Selects the buyer's best one-for-one offer from goods it can trade. */
public final class BarterTradeChoice {
    private BarterTradeChoice() {}

    public static Optional<Choice> best(List<Item> tradeable, List<Item> requested, CitizenNeeds needs) {
        Choice best = null;
        for (Item traded : tradeable) {
            for (Item wanted : requested) {
                double score = ItemNeedValues.forItem(traded).utility(needs)
                        - ItemNeedValues.forItem(wanted).utility(needs);
                if (score < 0 && (best == null || score < best.score())) {
                    best = new Choice(traded, wanted, score);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Returns the largest positive need-utility gain from matching stored goods to published offers. */
    public static double bestPotentialBenefit(List<Item> storedGoods, List<TradeOffer> offers, CitizenNeeds needs) {
        double best = 0.0;
        for (TradeOffer offer : offers) {
            if (!storedGoods.contains(offer.requested())) continue;
            double benefit = ItemNeedValues.forItem(offer.offered()).utility(needs)
                    - ItemNeedValues.forItem(offer.requested()).utility(needs);
            if (benefit > best) best = benefit;
        }
        return best;
    }

    public record Choice(Item traded, Item requested, double score) {}
    public record TradeOffer(Item offered, Item requested) {}
}
