package com.economistwars.citizen;

import com.economistwars.household.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/** Record-only dependencies: deliberately exposes no chunks, entities or block entities. */
public interface CitizenSimulationContext {
    long day(); long timeOfDay(); long gameTime();
    HouseholdSavedData households();
    Optional<Household> household(UUID id);
    Optional<BlockPos> home(UUID household,String dimension);
    List<BlockPos> farmPositions(UUID household,String dimension);
    List<MineSiteState> mines(String dimension);
    List<SettlementMarketState> markets(String dimension);
    HouseholdBedSavedData beds();
    List<ItemStack> rollMiningDrops(CitizenRandom random);
    void tickLand(Household household,long day);
    void releaseLand(UUID household);
    int farmParcelCount(UUID household,String dimension);
    void changed();
    default boolean night() { return timeOfDay() >= 13000 && timeOfDay() < 23000; }
    default boolean assigned(CitizenState citizen) {
        return citizen.householdId != null && households().hasMember(citizen.householdId,citizen.id());
    }
}
