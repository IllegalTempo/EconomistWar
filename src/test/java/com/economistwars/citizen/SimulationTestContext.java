package com.economistwars.citizen;

import java.util.*;
import com.economistwars.household.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.*;

final class SimulationTestContext implements CitizenSimulationContext {
    long clock;
    final HouseholdSavedData households = new HouseholdSavedData();
    HouseholdSavedData householdOverride;
    final HouseholdBedSavedData bedData = new HouseholdBedSavedData();
    BlockPos farm;
    MineSiteState mine;
    SettlementMarketState market;
    CitizenState citizen() {
        UUID id = households.createHousehold();
        CitizenState citizen = CitizenState.create(CitizenIdentity.create(),id,"minecraft:overworld",Vec3.ZERO,clock/24000,123);
        households.addMember(id,citizen.id()); return citizen;
    }
    public long day() { return clock/24000; }
    public long timeOfDay() { return Math.floorMod(clock,24000); }
    public long gameTime() { return clock; }
    public HouseholdSavedData households() { return householdOverride == null ? households : householdOverride; }
    public Optional<Household> household(UUID id) { return Optional.ofNullable(households().getHousehold(id)); }
    public Optional<BlockPos> home(UUID household,String dimension) { return Optional.of(BlockPos.ZERO); }
    public List<BlockPos> farmPositions(UUID household,String dimension) { return farm == null ? List.of() : List.of(farm); }
    public List<MineSiteState> mines(String dimension) { return mine == null ? List.of() : List.of(mine); }
    public List<SettlementMarketState> markets(String dimension) { return market == null ? List.of() : List.of(market); }
    public HouseholdBedSavedData beds() { return bedData; }
    public List<ItemStack> rollMiningDrops(CitizenRandom random) { random.nextInt(10); return List.of(new ItemStack(Items.COAL)); }
    public void tickLand(Household household,long day) { }
    public void releaseLand(UUID household) { }
    public int farmParcelCount(UUID household,String dimension) { return farm == null ? 0 : 1; }
    public void changed() { }
}
