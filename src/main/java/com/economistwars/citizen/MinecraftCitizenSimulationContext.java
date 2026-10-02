package com.economistwars.citizen;

import com.economistwars.household.*;
import com.economistwars.ownership.OwnershipSavedData;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

/** Connects backend actions to SavedData and server registries, never to physical chunks. */
public final class MinecraftCitizenSimulationContext implements CitizenSimulationContext {
    private final MinecraftServer server;
    private final HouseholdSavedData households;
    private final OwnershipSavedData ownership;
    private final CitizenSavedData citizens;
    private final MineSiteSavedData mines;
    private final SettlementMarketSavedData markets;
    private final HouseholdBedSavedData beds;
    private MinecraftCitizenSimulationContext(MinecraftServer server) {
        this.server = server; var level = server.overworld();
        households = HouseholdSavedData.get(level); ownership = OwnershipSavedData.get(level);
        citizens = CitizenSavedData.get(level); mines = MineSiteSavedData.get(level);
        markets = SettlementMarketSavedData.get(level); beds = HouseholdBedSavedData.get(level);
    }
    public static MinecraftCitizenSimulationContext create(MinecraftServer server) { return new MinecraftCitizenSimulationContext(server); }
    public static ResourceKey<Level> dimension(String name) { return ResourceKey.create(Registries.DIMENSION,Identifier.parse(name)); }
    public long day() { return server.overworld().getOverworldClockTime()/24000; }
    public long timeOfDay() { return Math.floorMod(server.overworld().getOverworldClockTime(),24000); }
    public long gameTime() { return server.overworld().getGameTime(); }
    public HouseholdSavedData households() { return households; }
    public Optional<Household> household(UUID id) { return Optional.ofNullable(households.getHousehold(id)); }
    public Optional<BlockPos> home(UUID household,String dimension) { return household(household).flatMap(h -> h.home(dimension(dimension))); }
    public List<BlockPos> farmPositions(UUID household,String dimension) { return ownership.farmPositions(dimension(dimension),household); }
    public List<MineSiteState> mines(String dimension) { return mines.sites(dimension); }
    public List<SettlementMarketState> markets(String dimension) { return markets.markets(dimension); }
    public HouseholdBedSavedData beds() { return beds; }
    public List<ItemStack> rollMiningDrops(CitizenRandom random) {
        var key = ResourceKey.create(Registries.LOOT_TABLE,Identifier.fromNamespaceAndPath("economistwars","citizen_mining"));
        var params = new LootParams.Builder(server.overworld()).create(LootContextParamSets.EMPTY);
        return server.reloadableRegistries().getLootTable(key).getRandomItems(params,net.minecraft.util.RandomSource.create(random.nextLong()))
                .stream().filter(s -> !s.isEmpty()).map(ItemStack::copy).toList();
    }
    public void tickLand(Household household,long day) {
        for (var level : server.getAllLevels()) if (household.home(level.dimension()).isPresent()) ownership.tickHousehold(level,household,day);
    }
    public void releaseLand(UUID household) { ownership.releaseHousehold(household); }
    public int farmParcelCount(UUID household,String dimension) { return ownership.farmParcelCount(dimension(dimension),household); }
    public void changed() { citizens.setDirty(); }
}
