package com.economistwars.citizen;

import com.economistwars.EconomistWars;
import com.economistwars.household.Household;
import com.economistwars.household.HouseholdSavedData;
import com.economistwars.household.HouseholdFarmSavedData;
import com.economistwars.household.LandSavedData;
import com.economistwars.network.CitizenNetworking;
import com.economistwars.network.CitizenProfilePayload;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.chat.Component;

public final class CitizenEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> CITIZEN_ID = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> CITIZEN_NAME = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> CITIZEN_SEX = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> CITIZEN_SKIN = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> HOUSEHOLD_ID = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.STRING);
    private CitizenSkills skills = CitizenSkills.empty();

    public CitizenEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        if (!level.isClientSide()) {
            applyIdentity(CitizenIdentity.create());
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CITIZEN_ID, "");
        builder.define(CITIZEN_NAME, "Citizen");
        builder.define(CITIZEN_SEX, CitizenSex.FEMALE.name());
        builder.define(CITIZEN_SKIN, "economistwars:textures/entity/citizen/female_1.png");
        builder.define(HOUSEHOLD_ID, "");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 96.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new CitizenPanicGoal(this));
        goalSelector.addGoal(2, new CitizenStoreHarvestGoal(this));
        goalSelector.addGoal(3, new CitizenFarmGoal(this));
        goalSelector.addGoal(5, new CitizenReturnHomeGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    public boolean canCarryWheat(int amount) {
        ItemStack held = getOffhandItem();
        return (held.isEmpty() || held.is(Items.WHEAT)) && held.getCount() + amount <= 64;
    }

    public void carryWheat(int amount) {
        ItemStack held = getOffhandItem();
        if (!canCarryWheat(amount)) {
            throw new IllegalStateException("Citizen cannot carry another wheat harvest");
        }
        setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WHEAT, held.getCount() + amount));
    }

    public boolean hasCarriedWheat() {
        return getOffhandItem().is(Items.WHEAT) && !getOffhandItem().isEmpty();
    }

    public void setCarriedWheat(ItemStack remainder) {
        setItemInHand(InteractionHand.OFF_HAND, remainder);
    }

    public CitizenSkills skills() {
        return skills;
    }

    public void addSkillExperience(CitizenSkill skill, int amount) {
        if (!level().isClientSide()) {
            skills.addExperience(skill, amount);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && level() instanceof ServerLevel serverLevel && tickCount % 100 == 0) {
            householdId().ifPresent(id -> {
                Household household = HouseholdSavedData.get(serverLevel).getHousehold(id);
                if (household != null && household.members().contains(citizenId())) {
                    HouseholdFarmSavedData farms = HouseholdFarmSavedData.get(serverLevel);
                    int shortage = farms.feedHousehold(serverLevel, id, household.members().size());
                    if (shortage > 0) {
                        applyShortageDamage(serverLevel, id, household, shortage);
                    }
                    LandSavedData.get(serverLevel).tickHousehold(serverLevel, id, household.members().size(), farms);
                }
            });
        }
    }

    private static void applyShortageDamage(ServerLevel level, UUID householdId, Household household, int shortage) {
        float damage = HouseholdFarmSavedData.shortageDamage(shortage);
        AABB loadedWorld = new AABB(-30_000_000, -2_048, -30_000_000, 30_000_000, 2_048, 30_000_000);
        for (CitizenEntity citizen : level.getEntitiesOfClass(CitizenEntity.class, loadedWorld,
                entity -> household.members().contains(entity.citizenId())
                        && entity.householdId().filter(householdId::equals).isPresent())) {
            citizen.hurtServer(level, level.damageSources().starve(), damage);
        }
    }

    public void applyIdentity(CitizenIdentity identity) {
        entityData.set(CITIZEN_ID, identity.id().toString());
        entityData.set(CITIZEN_NAME, identity.name());
        entityData.set(CITIZEN_SEX, identity.sex().name());
        entityData.set(CITIZEN_SKIN, identity.skin().toString());
        setCustomName(Component.literal(identity.name()));
        setCustomNameVisible(true);
    }

    public UUID citizenId() {
        try {
            return UUID.fromString(entityData.get(CITIZEN_ID));
        } catch (IllegalArgumentException exception) {
            return getUUID();
        }
    }

    public String citizenName() {
        return entityData.get(CITIZEN_NAME);
    }

    public CitizenSex sex() {
        return CitizenSex.fromSavedValue(entityData.get(CITIZEN_SEX));
    }

    public Identifier skin() {
        try {
            Identifier skin = Identifier.parse(entityData.get(CITIZEN_SKIN));
            String expectedSex = sex() == CitizenSex.FEMALE ? "female" : "male";
            if (skin.getNamespace().equals(EconomistWars.MOD_ID)
                    && skin.getPath().matches("textures/entity/citizen/" + expectedSex + "_[12]\\.png")) {
                return skin;
            }
        } catch (IllegalArgumentException exception) {
            // Use a bundled skin if a saved value is malformed.
        }
        String defaultSkin = (sex() == CitizenSex.FEMALE ? "female" : "male") + "_1.png";
        return Identifier.fromNamespaceAndPath(EconomistWars.MOD_ID, "textures/entity/citizen/" + defaultSkin);
    }

    public Optional<UUID> householdId() {
        try {
            String value = entityData.get(HOUSEHOLD_ID);
            return value.isBlank() ? Optional.empty() : Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public void setHouseholdId(UUID householdId) {
        entityData.set(HOUSEHOLD_ID, householdId == null ? "" : householdId.toString());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("CitizenId", entityData.get(CITIZEN_ID));
        output.putString("CitizenName", entityData.get(CITIZEN_NAME));
        output.putString("CitizenSex", entityData.get(CITIZEN_SEX));
        output.putString("CitizenSkin", entityData.get(CITIZEN_SKIN));
        output.putString("HouseholdId", entityData.get(HOUSEHOLD_ID));
        skills.save(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(CITIZEN_ID, input.getString("CitizenId").orElse(getUUID().toString()));
        entityData.set(CITIZEN_NAME, input.getString("CitizenName").orElse("Citizen"));
        entityData.set(CITIZEN_SEX, input.getString("CitizenSex").orElse(CitizenSex.FEMALE.name()));
        entityData.set(CITIZEN_SKIN, input.getString("CitizenSkin").orElse("economistwars:textures/entity/citizen/female_1.png"));
        entityData.set(HOUSEHOLD_ID, input.getString("HouseholdId").orElse(""));
        skills = CitizenSkills.load(input);
        setCustomName(Component.literal(citizenName()));
        setCustomNameVisible(true);
        if (!level().isClientSide() && level() instanceof ServerLevel serverLevel) {
            householdId().ifPresent(householdId -> {
                if (!HouseholdSavedData.get(serverLevel).hasMember(householdId, citizenId())) {
                    EconomistWars.LOGGER.warn("Citizen {} referenced missing household {}; leaving citizen unassigned", citizenId(), householdId);
                    setHouseholdId(null);
                }
            });
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            HouseholdSavedData householdData = HouseholdSavedData.get(serverPlayer.level());
            UUID householdId = householdId().orElse(null);
            Household household = householdId == null ? null : householdData.getHousehold(householdId);
            if (household == null || !householdData.hasMember(householdId, citizenId())) {
                householdId = null;
                household = null;
            }
            CitizenNetworking.sendProfile(serverPlayer, new CitizenProfilePayload(
                    citizenName(),
                    sex().name(),
                    citizenId().toString(),
                    skin().toString(),
                    householdId == null ? "" : householdId.toString(),
                    household == null ? 0 : household.members().size(),
                    household == null ? 0 : HouseholdFarmSavedData.get(serverPlayer.level()).food(householdId),
                    household == null ? 0 : HouseholdFarmSavedData.get(serverPlayer.level()).shortage(householdId),
                    household == null ? 0 : HouseholdFarmSavedData.get(serverPlayer.level()).coins(householdId),
                    household == null ? 0 : LandSavedData.get(serverPlayer.level()).parcelCount(householdId),
                    skills.level(CitizenSkill.FARMING),
                    skills.level(CitizenSkill.MINING),
                    skills.level(CitizenSkill.BUILDING),
                    skills.experience(CitizenSkill.FARMING),
                    skills.experience(CitizenSkill.MINING),
                    skills.experience(CitizenSkill.BUILDING)
            ));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && !level().isClientSide() && level() instanceof ServerLevel serverLevel) {
            UUID oldHousehold = householdId().orElse(null);
            HouseholdSavedData households = HouseholdSavedData.get(serverLevel);
            households.removeCitizen(citizenId());
            if (oldHousehold != null && households.getHousehold(oldHousehold) == null) {
                HouseholdFarmSavedData.get(serverLevel).removeHousehold(serverLevel, oldHousehold);
                LandSavedData.get(serverLevel).releaseHousehold(oldHousehold);
            }
        }
        super.remove(reason);
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }
}
