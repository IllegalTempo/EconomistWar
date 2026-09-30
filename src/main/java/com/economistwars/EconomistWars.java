package com.economistwars;

import net.fabricmc.api.ModInitializer;
import com.economistwars.citizen.CitizenEntityType;
import com.economistwars.citizen.MineWorksiteBlock;
import com.economistwars.citizen.MiningResourceValues;
import com.economistwars.command.CitizenCommands;
import com.economistwars.household.HouseholdHomeBlock;
import com.economistwars.household.FarmPlotBlock;
import com.economistwars.household.HouseholdStorageBlock;
import com.economistwars.household.SettlementMarketBlock;
import com.economistwars.network.CitizenNetworking;
import com.economistwars.item.EconomistWarsCreativeTab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EconomistWars implements ModInitializer {
    public static final String MOD_ID = "economistwars";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        CitizenNetworking.initialize();
        CitizenEntityType.initialize();
        CitizenEntityType.registerAttributes();
        MineWorksiteBlock.initialize();
        MiningResourceValues.initialize();
        HouseholdHomeBlock.initialize();
        FarmPlotBlock.initialize();
        HouseholdStorageBlock.initialize();
        SettlementMarketBlock.initialize();
        EconomistWarsCreativeTab.initialize();
        CitizenCommands.register();
        LOGGER.info("Economist Wars is starting");
    }
}
