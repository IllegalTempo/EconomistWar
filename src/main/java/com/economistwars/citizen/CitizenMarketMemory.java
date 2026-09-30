package com.economistwars.citizen;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** A citizen's most useful producible-item barter offer from the last market visit. */
public record CitizenMarketMemory(Item received, Item requested, BlockPos marketPosition) {
    public static List<Item> producibleItems() {
        return List.of(Items.WHEAT, Items.COAL, Items.RAW_IRON, Items.RAW_GOLD, Items.BREAD);
    }

    public CitizenMarketMemory {
        marketPosition = marketPosition.immutable();
    }

    public double gain(CitizenNeeds needs) {
        return ItemNeedValues.forItem(received).utility(needs)
                - ItemNeedValues.forItem(requested).utility(needs);
    }

    public Saved save() {
        return new Saved(BuiltInRegistries.ITEM.getKey(received).toString(),
                BuiltInRegistries.ITEM.getKey(requested).toString(), marketPosition.asLong());
    }

    public static Optional<CitizenMarketMemory> load(Saved saved) {
        try {
            Optional<Item> received = BuiltInRegistries.ITEM.getOptional(Identifier.parse(saved.receivedId()));
            Optional<Item> requested = BuiltInRegistries.ITEM.getOptional(Identifier.parse(saved.requestedId()));
            if (received.isEmpty() || requested.isEmpty()) return Optional.empty();
            return Optional.of(new CitizenMarketMemory(received.get(), requested.get(), BlockPos.of(saved.marketPosition())));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public static CitizenMarketMemory best(List<Offer> offers, List<Item> producible,
            CitizenNeeds needs, BlockPos visitedMarket) {
        return offers.stream()
                .filter(offer -> producible.contains(offer.requested()))
                .map(offer -> new Candidate(offer.received(), offer.requested(),
                        ItemNeedValues.forItem(offer.received()).utility(needs)
                                - ItemNeedValues.forItem(offer.requested()).utility(needs)))
                .filter(candidate -> candidate.gain() > 0.0)
                .min(Comparator.comparingDouble(Candidate::gain).reversed()
                        .thenComparing(candidate -> BuiltInRegistries.ITEM.getKey(candidate.received()).toString())
                        .thenComparing(candidate -> BuiltInRegistries.ITEM.getKey(candidate.requested()).toString()))
                .map(candidate -> new CitizenMarketMemory(candidate.received(), candidate.requested(), visitedMarket))
                .orElse(null);
    }

    public record Offer(Item received, Item requested) {}
    public record Saved(String receivedId, String requestedId, long marketPosition) {}
    private record Candidate(Item received, Item requested, double gain) {}
}
