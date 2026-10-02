package com.economistwars.household;

import com.economistwars.citizen.*;
import com.economistwars.network.SettlementMarketPayload;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Loaded-world adapter for the authoritative backend barter book. */
public final class SettlementMarketBlockEntity extends BlockEntity {
    private SettlementMarketState legacy;
    public SettlementMarketBlockEntity(BlockPos position,BlockState state) {
        super(SettlementMarketBlock.BLOCK_ENTITY_TYPE,position,state);
    }
    private SettlementMarketState state(ServerLevel level) {
        CitizenAssetKey key = new CitizenAssetKey(level.dimension().identifier().toString(),worldPosition);
        SettlementMarketState imported = legacy;
        if (imported == null) imported = new SettlementMarketState(key);
        // Legacy records have their dimension filled in after a Level has been assigned.
        if (!imported.key.equals(key)) imported = SettlementMarketState.CODEC.parse(
                net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,level.registryAccess()),
                withKey(level,imported,key)).getOrThrow();
        SettlementMarketState result = SettlementMarketSavedData.get(level).registerIfAbsent(key,imported);
        legacy = null;
        return result;
    }
    private com.google.gson.JsonElement withKey(ServerLevel level,SettlementMarketState imported,CitizenAssetKey key) {
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,level.registryAccess());
        var json = SettlementMarketState.CODEC.encodeStart(ops,imported).getOrThrow().getAsJsonObject();
        json.add("key",CitizenAssetKey.CODEC.encodeStart(ops,key).getOrThrow()); return json;
    }
    public void serverTick(ServerLevel level) {
        SettlementMarketSavedData.get(level).register(level,worldPosition); state(level);
    }
    public double potentialTradeBenefit(ServerLevel level,UUID household,Map<String,CitizenNeed> needs,List<net.minecraft.world.item.Item> ignored) {
        return state(level).potentialTradeBenefit(household,needs,HouseholdSavedData.get(level)::getHousehold);
    }
    public double potentialTradeBenefit(ServerLevel level,UUID household,Map<String,CitizenNeed> needs) {
        return potentialTradeBenefit(level,household,needs,List.of());
    }
    public boolean visit(ServerLevel level,UUID household,Map<String,CitizenNeed> needs) {
        return state(level).visit(household,needs,null,HouseholdSavedData.get(level)::getHousehold,level.getGameTime());
    }
    public boolean visit(ServerLevel level,UUID household,Map<String,CitizenNeed> needs,CitizenEntity citizen) {
        CitizenState record = CitizenSavedData.get(level).find(citizen.citizenId()).orElse(null);
        return state(level).visit(household,needs,record,HouseholdSavedData.get(level)::getHousehold,level.getGameTime());
    }
    public SettlementMarketPayload snapshot() {
        return level instanceof ServerLevel server ? state(server).snapshot()
                : new SettlementMarketPayload(worldPosition,List.of(),List.of());
    }
    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("BackendVersion",1);
        if (legacy != null) output.store("LegacyBook",SettlementMarketState.CODEC,legacy);
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        legacy = input.read("LegacyBook",SettlementMarketState.CODEC).orElse(null);
        if (legacy != null || input.getInt("BackendVersion").orElse(0) >= 1) return;
        legacy = new SettlementMarketState(new CitizenAssetKey("minecraft:overworld",worldPosition));
        for (int i = 0; i < Math.clamp(input.getInt("OfferCount").orElse(0),0,256); i++) {
            try {
                UUID household = UUID.fromString(input.getString("OfferHousehold"+i).orElse(""));
                ItemStack offered = input.read("OfferOffered"+i,ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
                ItemStack requested = input.read("OfferRequested"+i,ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
                legacy.offer(household,offered,requested,Double.parseDouble(input.getString("OfferAskScore"+i).orElse("0")),
                        Double.parseDouble(input.getString("OfferAskUtility"+i).orElse("0")));
            } catch (IllegalArgumentException ignored) { }
        }
        for (int i = 0; i < Math.clamp(input.getInt("TradeHistoryCount").orElse(0),0,256); i++) {
            try {
                legacy.importTrade(UUID.fromString(input.getString("TradeSeller"+i).orElse("")),
                        UUID.fromString(input.getString("TradeBuyer"+i).orElse("")),
                        input.read("TradeSellerItem"+i,ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY),
                        input.read("TradeBuyerItem"+i,ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY),input.getLong("TradeGameTime"+i).orElse(0L));
            } catch (IllegalArgumentException ignored) { }
        }
    }
}
