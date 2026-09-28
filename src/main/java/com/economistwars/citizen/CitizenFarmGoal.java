package com.economistwars.citizen;

import com.economistwars.household.HouseholdFarmSavedData;
import com.economistwars.household.HouseholdSavedData;
import com.economistwars.household.LandSavedData;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Harvests mature wheat only from crop blocks claimed by this citizen's household. */
final class CitizenFarmGoal extends Goal {
    private final CitizenEntity citizen;
    private BlockPos target;
    private UUID householdId;
    private int searchCooldown;
    private int harvestTicks;

    CitizenFarmGoal(CitizenEntity citizen) {
        this.citizen = citizen;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(citizen.level() instanceof ServerLevel level) || citizen.level().isClientSide()) {
            return false;
        }
        if (searchCooldown-- > 0 || !level.isBrightOutside() || !citizen.canCarryWheat(2)) {
            return false;
        }
        searchCooldown = 100 + citizen.getRandom().nextInt(40);
        target = null;
        householdId = citizen.householdId().orElse(null);
        if (householdId == null || !HouseholdSavedData.get(level).hasMember(householdId, citizen.citizenId())) {
            return false;
        }
        double nearest = Double.MAX_VALUE;
        ArrayList<BlockPos> ownedCrops = new ArrayList<>(HouseholdFarmSavedData.get(level).crops(level, householdId));
        ownedCrops.addAll(LandSavedData.get(level).ownedCrops(level, householdId));
        for (BlockPos crop : ownedCrops) {
            if (!level.isLoaded(crop) || !isRipe(level, crop)) {
                continue;
            }
            double distance = citizen.distanceToSqr(crop.getX() + 0.5, crop.getY() + 0.5, crop.getZ() + 0.5);
            if (distance < nearest && distance < 96 * 96) {
                target = crop;
                nearest = distance;
            }
        }
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && citizen.level() instanceof ServerLevel level
                && level.isLoaded(target) && isRipe(level, target)
                && citizen.canCarryWheat(2)
                && ownsTarget(level)
                && citizen.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) < 96 * 96
                && (citizen.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) <= 5.0
                    || !citizen.getNavigation().isDone());
    }

    @Override
    public void start() {
        harvestTicks = 0;
        citizen.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HOE));
        boolean far = citizen.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) > 16 * 16;
        citizen.setSprinting(far);
        citizen.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, far ? 1.25 : 0.85);
    }

    @Override
    public void tick() {
        if (target == null || !(citizen.level() instanceof ServerLevel level)) {
            return;
        }
        double distance = citizen.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (distance > 5.0) {
            if (distance <= 16 * 16 && citizen.isSprinting()) {
                citizen.setSprinting(false);
                citizen.getNavigation().setSpeedModifier(0.85);
            }
            return;
        }
        citizen.setSprinting(false);
        harvestTicks++;
        if (harvestTicks == 1 || harvestTicks == 6) {
            citizen.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
        }
        level.destroyBlockProgress(citizen.getId(), target, Math.min(9, harvestTicks * 10 / 12));
        if (harvestTicks >= 12 && isRipe(level, target) && ownsTarget(level)
                && HouseholdSavedData.get(level).hasMember(householdId, citizen.citizenId())) {
            BlockState matureCrop = level.getBlockState(target);
            if (!level.setBlock(target, Blocks.WHEAT.defaultBlockState(), 3)) {
                return;
            }
            level.destroyBlockProgress(citizen.getId(), target, -1);
            level.levelEvent(citizen, 2001, target, Block.getId(matureCrop));
            citizen.carryWheat(2);
            citizen.addSkillExperience(CitizenSkill.FARMING, 5);
            target = null;
        }
    }

    @Override
    public void stop() {
        if (target != null && citizen.level() instanceof ServerLevel level) {
            level.destroyBlockProgress(citizen.getId(), target, -1);
        }
        citizen.getNavigation().stop();
        citizen.setSprinting(false);
        target = null;
        householdId = null;
    }

    private static boolean isRipe(ServerLevel level, BlockPos crop) {
        return level.getBlockState(crop).is(Blocks.WHEAT)
                && ((CropBlock) Blocks.WHEAT).isMaxAge(level.getBlockState(crop))
                && level.getBlockState(crop.below()).is(Blocks.FARMLAND);
    }

    private boolean ownsTarget(ServerLevel level) {
        return householdId != null && (HouseholdFarmSavedData.get(level).ownsCrop(level, householdId, target)
                || LandSavedData.get(level).ownsCrop(level, householdId, target));
    }
}
