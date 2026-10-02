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
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Import any legacy visuals before taking the citizen snapshot for this tick.
            for (var level : server.getAllLevels()) for (var entity : level.getAllEntities())
                if (entity instanceof com.economistwars.citizen.CitizenEntity citizen) citizen.importLegacy();
            var context = com.economistwars.citizen.MinecraftCitizenSimulationContext.create(server);
            var citizens = com.economistwars.citizen.CitizenSavedData.get(server.overworld());
            com.economistwars.citizen.CitizenSimulation.advance(citizens.states(),context);
            com.economistwars.citizen.CitizenPresentationManager.reconcile(server,citizens);
        });
        LOGGER.info("Economist Wars is starting");
    }
}
