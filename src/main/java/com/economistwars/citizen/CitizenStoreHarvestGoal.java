package com.economistwars.citizen;

import com.economistwars.household.HouseholdFarmSavedData;
import com.economistwars.household.HouseholdSavedData;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;

/** Carries the actual harvested ItemStack to the household's sealed store. */
final class CitizenStoreHarvestGoal extends Goal {
    private final CitizenEntity citizen;
    private BlockPos home;
    private UUID householdId;
    private int retryDelay;

    CitizenStoreHarvestGoal(CitizenEntity citizen) {
        this.citizen = citizen;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(citizen.level() instanceof ServerLevel level) || !citizen.hasCarriedWheat() || retryDelay-- > 0) {
            return false;
        }
        retryDelay = 20;
        householdId = citizen.householdId().orElse(null);
        if (householdId == null || !HouseholdSavedData.get(level).hasMember(householdId, citizen.citizenId())) {
            return false;
        }
        home = HouseholdFarmSavedData.get(level).home(level, householdId).orElse(null);
        return home != null;
    }

    @Override
    public boolean canContinueToUse() {
        return home != null && citizen.hasCarriedWheat()
                && (distanceToHome() <= 9 || !citizen.getNavigation().isDone());
    }

    @Override
    public void start() {
        boolean far = distanceToHome() > 16 * 16;
        citizen.setSprinting(far);
        citizen.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, far ? 1.25 : 0.85);
    }

    @Override
    public void tick() {
        if (distanceToHome() > 9 || !(citizen.level() instanceof ServerLevel level)) {
            if (distanceToHome() <= 16 * 16 && citizen.isSprinting()) {
                citizen.setSprinting(false);
                citizen.getNavigation().setSpeedModifier(0.85);
            }
            return;
        }
        citizen.setSprinting(false);
        ItemStack carried = citizen.getOffhandItem();
        ItemStack remainder = HouseholdFarmSavedData.get(level).deposit(level, householdId, carried);
        if (remainder.getCount() != carried.getCount()) {
            citizen.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
            citizen.setCarriedWheat(remainder);
        }
    }

    @Override
    public void stop() {
        citizen.getNavigation().stop();
        citizen.setSprinting(false);
        if (!citizen.hasCarriedWheat()) {
            citizen.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        home = null;
        householdId = null;
    }

    private double distanceToHome() {
        return citizen.distanceToSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
    }
}
