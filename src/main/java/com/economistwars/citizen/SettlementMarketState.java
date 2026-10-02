package com.economistwars.citizen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.economistwars.household.Household;
import com.economistwars.network.SettlementMarketPayload;
import java.util.*;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;

/** A barter book that is usable without its block or chunk. */
public final class SettlementMarketState {
    record Offer(String household,ItemStack offered,ItemStack requested,double askScore,double askUtility) {
        static final Codec<Offer> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("household").forGetter(Offer::household),
                ItemStack.CODEC.fieldOf("offered").forGetter(Offer::offered),
                ItemStack.CODEC.fieldOf("requested").forGetter(Offer::requested),
                Codec.DOUBLE.fieldOf("score").forGetter(Offer::askScore),
                Codec.DOUBLE.fieldOf("utility").forGetter(Offer::askUtility)).apply(i,Offer::new));
    }
    record Trade(String seller,String buyer,ItemStack sold,ItemStack paid,long time) {
        static final Codec<Trade> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("seller").forGetter(Trade::seller),Codec.STRING.fieldOf("buyer").forGetter(Trade::buyer),
                ItemStack.CODEC.fieldOf("sold").forGetter(Trade::sold),ItemStack.CODEC.fieldOf("paid").forGetter(Trade::paid),
                Codec.LONG.fieldOf("time").forGetter(Trade::time)).apply(i,Trade::new));
    }
    public static final Codec<SettlementMarketState> CODEC = RecordCodecBuilder.create(i -> i.group(
            CitizenAssetKey.CODEC.fieldOf("key").forGetter(s -> s.key),
            Offer.CODEC.listOf().fieldOf("offers").forGetter(s -> List.copyOf(s.offers)),
            Trade.CODEC.listOf().fieldOf("history").forGetter(s -> List.copyOf(s.history)),
            Codec.BOOL.optionalFieldOf("valid",true).forGetter(s -> s.valid)
    ).apply(i,SettlementMarketState::load));
    public final CitizenAssetKey key;
    boolean valid = true;
    private final List<Offer> offers = new ArrayList<>();
    private final List<Trade> history = new ArrayList<>();
    private Runnable changed = () -> {};
    public SettlementMarketState(CitizenAssetKey key) { this.key = key; }
    void onChange(Runnable changed) { this.changed = changed; }
    public void offer(UUID household,ItemStack offered,ItemStack requested,double score,double utility) {
        if (offered.isEmpty() || requested.isEmpty() || offers.stream().anyMatch(o -> o.household.equals(household.toString()))) return;
        offers.add(new Offer(household.toString(),offered.copyWithCount(1),requested.copyWithCount(1),score,utility));
        changed.run();
    }
    public void importTrade(UUID seller,UUID buyer,ItemStack sold,ItemStack paid,long time) {
        history.add(new Trade(seller.toString(),buyer.toString(),sold.copyWithCount(1),paid.copyWithCount(1),time));
        if (history.size() > 256) history.removeFirst(); changed.run();
    }
    private List<Offer> validOffers(UUID buyer,Function<UUID,Household> households) {
        return offers.stream().filter(o -> !o.household.equals(buyer.toString()) && o.askScore < 0)
                .filter(o -> { Household seller = households.apply(UUID.fromString(o.household)); return seller != null && seller.containsStoredItem(o.offered); }).toList();
    }
    public double potentialTradeBenefit(UUID household,Map<String,CitizenNeed> needs,Function<UUID,Household> households) {
        return BarterTradeChoice.bestPotentialBenefit(CitizenMarketMemory.producibleItems(), validOffers(household,households).stream()
                .map(o -> new BarterTradeChoice.TradeOffer(o.offered.getItem(),o.requested.getItem())).toList(),needs);
    }
    public boolean visit(UUID householdId,Map<String,CitizenNeed> needs,CitizenState citizen,Function<UUID,Household> households,long time) {
        if (!valid) return false;
        Household buyer = households.apply(householdId);
        if (buyer == null) return false;
        List<ItemStack> goods = buyer.storageContents();
        var choice = BarterTradeChoice.best(goods.stream().map(ItemStack::getItem).distinct().toList(),BuiltInRegistries.ITEM.stream().toList(),needs);
        choice.ifPresent(c -> goods.stream().filter(s -> s.is(c.traded())).findFirst()
                .ifPresent(stack -> offer(householdId,stack,new ItemStack(c.requested()),c.score(),ItemNeedValues.forItem(c.traded()).utility(needs))));
        List<Offer> available = validOffers(householdId,households);
        if (citizen != null) citizen.marketMemory = CitizenMarketMemory.best(available.stream()
                .map(o -> new CitizenMarketMemory.Offer(o.offered.getItem(),o.requested.getItem())).toList(),
                CitizenMarketMemory.producibleItems(),needs,key.position());
        Comparator<Offer> comparator = Comparator.<Offer>comparingInt(o -> citizen != null && citizen.marketMemory != null
                        && citizen.marketMemory.received() == o.offered.getItem() && citizen.marketMemory.requested() == o.requested.getItem() ? 0 : 1)
                .thenComparingDouble(o -> ItemNeedValues.forItem(o.requested.getItem()).utility(needs) - ItemNeedValues.forItem(o.offered.getItem()).utility(needs))
                .thenComparingDouble(Offer::askUtility).thenComparing(Offer::household);
        Offer best = available.stream().filter(o -> goods.stream().anyMatch(s -> s.is(o.requested.getItem())))
                .filter(o -> ItemNeedValues.forItem(o.offered.getItem()).utility(needs) > ItemNeedValues.forItem(o.requested.getItem()).utility(needs))
                .min(comparator).orElse(null);
        if (best == null) return false;
        Household seller = households.apply(UUID.fromString(best.household));
        if (!buyer.exchangeOne(seller,s -> s.is(best.requested.getItem()),s -> ItemStack.isSameItemSameComponents(s,best.offered))) return false;
        offers.remove(best); importTrade(seller.id(),buyer.id(),best.offered,best.requested,time); return true;
    }
    public SettlementMarketPayload snapshot() {
        return new SettlementMarketPayload(key.position(),offers.stream().map(o -> new SettlementMarketPayload.Offer(
                UUID.fromString(o.household),o.offered.copy(),o.requested.copy())).toList(),history.stream()
                .map(t -> new SettlementMarketPayload.Trade(UUID.fromString(t.seller),UUID.fromString(t.buyer),t.sold.copy(),t.paid.copy(),t.time)).toList());
    }
    private static SettlementMarketState load(CitizenAssetKey key,List<Offer> offers,List<Trade> trades,boolean valid) {
        SettlementMarketState s = new SettlementMarketState(key); s.offers.addAll(offers); s.history.addAll(trades); s.valid = valid; return s;
    }
}
