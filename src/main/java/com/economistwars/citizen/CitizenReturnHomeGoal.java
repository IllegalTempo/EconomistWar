package com.economistwars.citizen;

import com.economistwars.household.HouseholdFarmSavedData;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

/** Keeps settlement citizens close to their household's home between harvests. */
final class CitizenReturnHomeGoal extends Goal {
    private final CitizenEntity citizen;
    private BlockPos home;
    private int retryDelay;

    CitizenReturnHomeGoal(CitizenEntity citizen) {
        this.citizen = citizen;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(citizen.level() instanceof ServerLevel level) || retryDelay-- > 0) {
            return false;
        }
        retryDelay = 60;
        home = citizen.householdId()
                .flatMap(id -> HouseholdFarmSavedData.get(level).home(level, id))
                .orElse(null);
        return home != null && citizen.distanceToSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5) > 64;
    }

    @Override
    public boolean canContinueToUse() {
        return home != null && !citizen.getNavigation().isDone()
                && citizen.distanceToSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5) > 4;
    }

    @Override
    public void start() {
        boolean far = citizen.distanceToSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5) > 16 * 16;
        citizen.setSprinting(far);
        citizen.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, far ? 1.25 : 0.8);
    }

    @Override
    public void tick() {
        if (citizen.isSprinting()
                && citizen.distanceToSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5) <= 16 * 16) {
            citizen.setSprinting(false);
            citizen.getNavigation().setSpeedModifier(0.8);
        }
    }

    @Override
    public void stop() {
        citizen.getNavigation().stop();
        citizen.setSprinting(false);
        home = null;
    }
}
