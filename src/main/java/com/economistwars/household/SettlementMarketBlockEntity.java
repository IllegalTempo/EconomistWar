package com.economistwars.household;

import com.economistwars.citizen.BarterTradeChoice;
import com.economistwars.citizen.CitizenEntity;
import com.economistwars.citizen.CitizenMarketMemory;
import com.economistwars.citizen.CitizenNeeds;
import com.economistwars.citizen.ItemNeedValues;
import com.economistwars.citizen.SettlementMarketSavedData;
import com.economistwars.network.SettlementMarketPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A persistent one-for-one barter book for a generated settlement. */
public final class SettlementMarketBlockEntity extends BlockEntity {
    private final List<Offer> offers = new ArrayList<>();
    private final List<SettlementMarketTradeRecord> history = new ArrayList<>();

    public SettlementMarketBlockEntity(BlockPos position, BlockState state) {
        super(SettlementMarketBlock.BLOCK_ENTITY_TYPE, position, state);
    }

    public void serverTick(ServerLevel level) {
        if (level.getGameTime() % 100 == 0) SettlementMarketSavedData.get(level).register(level, worldPosition);
    }

    public boolean visit(ServerLevel level, UUID householdId, CitizenNeeds needs) {
        return visit(level, householdId, needs, null);
    }

    public boolean visit(ServerLevel level, UUID householdId, CitizenNeeds needs, CitizenEntity citizen) {
        if (citizen != null) citizen.refreshMarketMemory(null);
        HouseholdFarmSavedData farms = HouseholdFarmSavedData.get(level);
        if (!farms.ensureStorage(level, householdId)) return false;
        HouseholdStorageBlockEntity buyer = storage(level, householdId);
        if (buyer == null) return false;
        List<ItemStack> buyerGoods = buyer.contents(householdId);
        publishOffer(level, householdId, needs, buyerGoods);

        if (citizen != null) {
            List<CitizenMarketMemory.Offer> memories = validOffers(level, householdId).stream()
                    .map(offer -> new CitizenMarketMemory.Offer(offer.offered.getItem(), offer.requested.getItem()))
                    .toList();
            citizen.refreshMarketMemory(CitizenMarketMemory.best(memories,
                    CitizenMarketMemory.producibleItems(), needs, worldPosition));
        }
        CitizenMarketMemory remembered = citizen == null ? null : citizen.marketMemory();

        OfferChoice best = null;
        for (Offer offer : List.copyOf(offers)) {
            if (offer.householdId.equals(householdId)) continue;
            HouseholdStorageBlockEntity seller = storage(level, offer.householdId);
            if (seller == null || !contains(seller.contents(offer.householdId), offer.offered)) {
                offers.remove(offer);
                setChanged();
                continue;
            }
            if (offer.askScore >= 0) continue;
            for (ItemStack buyerItem : buyerGoods) {
                if (buyerItem.isEmpty() || buyerItem.getItem() != offer.requested.getItem()) continue;
                double buyerScore = ItemNeedValues.forItem(buyerItem.getItem()).utility(needs)
                        - ItemNeedValues.forItem(offer.offered.getItem()).utility(needs);
                if (buyerScore >= 0) continue;
                boolean rememberedChoice = remembered != null
                        && offer.offered.getItem() == remembered.received()
                        && offer.requested.getItem() == remembered.requested();
                OfferChoice candidate = new OfferChoice(offer, buyerItem.copyWithCount(1), buyerScore, rememberedChoice);
                if (best == null || candidate.compareTo(best) < 0) best = candidate;
            }
        }

        if (best == null) return false;
        OfferChoice selected = best;
        HouseholdStorageBlockEntity seller = storage(level, selected.offer.householdId);
        if (seller == null || !buyer.exchangeOne(householdId, seller, selected.offer.householdId,
                stack -> stack.getItem() == selected.traded.getItem(),
                stack -> ItemStack.isSameItemSameComponents(stack, selected.offer.offered))) return false;
        history.add(new SettlementMarketTradeRecord(selected.offer.householdId, householdId,
                selected.offer.offered, selected.traded, level.getGameTime()));
        if (history.size() > 256) history.removeFirst();
        offers.remove(selected.offer);
        setChanged();
        return true;
    }

    /** Best positive utility gain the household could get by exchanging one stored item now. */
    public double potentialTradeBenefit(ServerLevel level, UUID householdId, CitizenNeeds needs) {
        HouseholdStorageBlockEntity buyer = storage(level, householdId);
        List<Item> stored = buyer == null ? List.of()
                : buyer.contents(householdId).stream().filter(stack -> !stack.isEmpty())
                        .map(ItemStack::getItem).distinct().toList();
        return potentialTradeBenefit(level, householdId, needs, stored);
    }

    public double potentialTradeBenefit(ServerLevel level, UUID householdId, CitizenNeeds needs, List<Item> producible) {
        List<BarterTradeChoice.TradeOffer> availableOffers = validOffers(level, householdId).stream()
                .map(offer -> new BarterTradeChoice.TradeOffer(offer.offered.getItem(), offer.requested.getItem()))
                .toList();
        return BarterTradeChoice.bestPotentialBenefit(producible, availableOffers, needs);
    }

    private List<Offer> validOffers(ServerLevel level, UUID householdId) {
        ArrayList<Offer> valid = new ArrayList<>();
        for (Offer offer : List.copyOf(offers)) {
            if (offer.householdId.equals(householdId) || offer.askScore >= 0.0) continue;
            HouseholdStorageBlockEntity seller = storage(level, offer.householdId);
            if (seller != null && contains(seller.contents(offer.householdId), offer.offered)) valid.add(offer);
        }
        return List.copyOf(valid);
    }

    public SettlementMarketPayload snapshot() {
        List<SettlementMarketPayload.Offer> currentOffers = offers.stream()
                .map(offer -> new SettlementMarketPayload.Offer(
                        offer.householdId, offer.offered, offer.requested))
                .toList();
        List<SettlementMarketPayload.Trade> completedTrades = history.stream()
                .map(trade -> new SettlementMarketPayload.Trade(trade.sellerId(), trade.buyerId(),
                        trade.sellerItem(), trade.buyerItem(), trade.gameTime()))
                .toList();
        return new SettlementMarketPayload(worldPosition, currentOffers, completedTrades);
    }

    private void publishOffer(ServerLevel level, UUID householdId, CitizenNeeds needs, List<ItemStack> goods) {
        if (offers.stream().anyMatch(offer -> offer.householdId.equals(householdId))) return;
        List<Item> tradeable = goods.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::getItem).distinct().toList();
        if (tradeable.isEmpty()) return;
        Optional<BarterTradeChoice.Choice> choice = BarterTradeChoice.best(
                tradeable, BuiltInRegistries.ITEM.stream().toList(), needs);
        if (choice.isEmpty()) return;
        ItemStack offered = goods.stream().filter(stack -> stack.getItem() == choice.get().traded())
                .findFirst().orElse(ItemStack.EMPTY);
        if (offered.isEmpty()) return;
        double askScore = choice.get().score();
        double askUtility = ItemNeedValues.forItem(offered.getItem()).utility(needs);
        offers.add(new Offer(householdId, offered.copyWithCount(1), new ItemStack(choice.get().requested()), askScore, askUtility));
        offers.sort(Comparator.comparingDouble((Offer offer) -> offer.askUtility)
                .thenComparingDouble(offer -> offer.askScore)
                .thenComparing(offer -> offer.householdId.toString()));
        setChanged();
    }

    private HouseholdStorageBlockEntity storage(ServerLevel level, UUID householdId) {
        HouseholdFarmSavedData farms = HouseholdFarmSavedData.get(level);
        BlockPos position = farms.storagePosition(level, householdId).orElse(null);
        if (position == null) return null;
        if (!level.isLoaded(position)) level.getChunkAt(position);
        return level.getBlockEntity(position) instanceof HouseholdStorageBlockEntity storage
                && storage.belongsTo(householdId) ? storage : null;
    }

    private static boolean contains(List<ItemStack> stacks, ItemStack target) {
        return stacks.stream().anyMatch(stack -> ItemStack.isSameItemSameComponents(stack, target));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("OfferCount", offers.size());
        for (int index = 0; index < offers.size(); index++) {
            Offer offer = offers.get(index);
            output.putString("OfferHousehold" + index, offer.householdId.toString());
            output.store("OfferOffered" + index, ItemStack.OPTIONAL_CODEC, offer.offered);
            output.store("OfferRequested" + index, ItemStack.OPTIONAL_CODEC, offer.requested);
            output.putString("OfferAskScore" + index, Double.toString(offer.askScore));
            output.putString("OfferAskUtility" + index, Double.toString(offer.askUtility));
        }
        output.putInt("TradeHistoryCount", history.size());
        for (int index = 0; index < history.size(); index++) {
            SettlementMarketTradeRecord trade = history.get(index);
            output.putString("TradeSeller" + index, trade.sellerId().toString());
            output.putString("TradeBuyer" + index, trade.buyerId().toString());
            output.store("TradeSellerItem" + index, ItemStack.OPTIONAL_CODEC, trade.sellerItem());
            output.store("TradeBuyerItem" + index, ItemStack.OPTIONAL_CODEC, trade.buyerItem());
            output.putLong("TradeGameTime" + index, trade.gameTime());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        offers.clear();
        int count = Math.clamp(input.getInt("OfferCount").orElse(0), 0, 256);
        for (int index = 0; index < count; index++) {
            Optional<String> ownerValue = input.getString("OfferHousehold" + index);
            Optional<ItemStack> offered = input.read("OfferOffered" + index, ItemStack.OPTIONAL_CODEC);
            Optional<ItemStack> requested = input.read("OfferRequested" + index, ItemStack.OPTIONAL_CODEC);
            if (ownerValue.isEmpty() || offered.isEmpty() || requested.isEmpty()
                    || offered.get().isEmpty() || requested.get().isEmpty()) continue;
            try {
                offers.add(new Offer(UUID.fromString(ownerValue.get()), offered.get(), requested.get(),
                        parseDouble(input.getString("OfferAskScore" + index).orElse("0")),
                        parseDouble(input.getString("OfferAskUtility" + index).orElse("0"))));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed offers without discarding the remaining market book.
            }
        }
        history.clear();
        int historyCount = Math.clamp(input.getInt("TradeHistoryCount").orElse(0), 0, 256);
        for (int index = 0; index < historyCount; index++) {
            Optional<String> sellerValue = input.getString("TradeSeller" + index);
            Optional<String> buyerValue = input.getString("TradeBuyer" + index);
            Optional<ItemStack> sellerItem = input.read("TradeSellerItem" + index, ItemStack.OPTIONAL_CODEC);
            Optional<ItemStack> buyerItem = input.read("TradeBuyerItem" + index, ItemStack.OPTIONAL_CODEC);
            if (sellerValue.isEmpty() || buyerValue.isEmpty() || sellerItem.isEmpty() || buyerItem.isEmpty()
                    || sellerItem.get().isEmpty() || buyerItem.get().isEmpty()) continue;
            try {
                history.add(new SettlementMarketTradeRecord(UUID.fromString(sellerValue.get()),
                        UUID.fromString(buyerValue.get()), sellerItem.get(), buyerItem.get(),
                        input.getLong("TradeGameTime" + index).orElse(0L)));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed records without discarding the remaining market history.
            }
        }
    }

    private static double parseDouble(String value) {
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) ? parsed : 0.0;
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private record Offer(UUID householdId, ItemStack offered, ItemStack requested, double askScore, double askUtility) {}

    private record OfferChoice(Offer offer, ItemStack traded, double score, boolean remembered) implements Comparable<OfferChoice> {
        @Override
        public int compareTo(OfferChoice other) {
            int byMemory = Boolean.compare(other.remembered, remembered);
            if (byMemory != 0) return byMemory;
            int byScore = Double.compare(score, other.score);
            if (byScore != 0) return byScore;
            int byAsk = Double.compare(offer.askUtility, other.offer.askUtility);
            if (byAsk != 0) return byAsk;
            return offer.householdId.toString().compareTo(other.offer.householdId.toString());
        }
    }
}
