package com.economistwars.household;

import com.economistwars.EconomistWars;
import com.economistwars.citizen.CitizenEntity;
import com.economistwars.citizen.CitizenEntityType;
import com.economistwars.citizen.CitizenIdentity;
import com.economistwars.citizen.CitizenSex;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class CitizenHouseholdSpawner {
    private CitizenHouseholdSpawner() {}

    public static boolean spawnCouple(ServerLevel level, BlockPos homePosition) {
        HouseholdSavedData householdData = HouseholdSavedData.get(level);
        UUID householdId = householdData.createHousehold();
        List<CitizenEntity> citizens = new ArrayList<>(2);

        try {
            spawnCitizen(level, householdData, householdId, citizens, CitizenSex.FEMALE, homePosition, -0.4);
            spawnCitizen(level, householdData, householdId, citizens, CitizenSex.MALE, homePosition, 0.4);
            HouseholdFarmSavedData.get(level).registerHome(level, householdId, homePosition);
            if (!HouseholdFarmSavedData.get(level).ensureStorage(level, householdId)) {
                throw new IllegalStateException("Could not create household storage");
            }
            LandSavedData.get(level).grantStarter(level, householdId, homePosition);
            return true;
        } catch (RuntimeException exception) {
            for (CitizenEntity citizen : citizens) {
                citizen.discard();
                householdData.removeCitizen(citizen.citizenId());
            }
            householdData.removeHousehold(householdId);
            HouseholdFarmSavedData.get(level).removeHousehold(level, householdId);
            LandSavedData.get(level).releaseHousehold(householdId);
            EconomistWars.LOGGER.error(
                    "Could not populate household home at {} in {}",
                    homePosition,
                    level.dimension().identifier(),
                    exception
            );
            return false;
        }
    }

    private static void spawnCitizen(
            ServerLevel level,
            HouseholdSavedData householdData,
            UUID householdId,
            List<CitizenEntity> citizens,
            CitizenSex sex,
            BlockPos homePosition,
            double offsetX
    ) {
        CitizenEntity citizen = new CitizenEntity(CitizenEntityType.CITIZEN, level);
        citizens.add(citizen);
        citizen.applyIdentity(CitizenIdentity.create(sex));
        citizen.setHouseholdId(householdId);
        if (!householdData.addMember(householdId, citizen.citizenId())) {
            throw new IllegalStateException("Could not register citizen household membership");
        }

        // The anchor is placed in the first air block above the home floor.
        citizen.setPos(homePosition.getX() + 0.5 + offsetX, homePosition.getY(), homePosition.getZ() + 0.5);
        if (!level.addFreshEntity(citizen)) {
            throw new IllegalStateException("Minecraft rejected a citizen entity spawn");
        }
    }
}
