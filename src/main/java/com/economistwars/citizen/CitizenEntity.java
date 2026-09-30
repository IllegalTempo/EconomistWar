package com.economistwars.citizen;

import com.economistwars.EconomistWars;
import com.economistwars.household.Household;
import com.economistwars.household.HouseholdSavedData;
import com.economistwars.household.HouseholdFarmSavedData;
import com.economistwars.household.LandSavedData;
import com.economistwars.network.CitizenNetworking;
import com.economistwars.network.CitizenProfilePayload;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class CitizenEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> CITIZEN_ID = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> CITIZEN_NAME = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> CITIZEN_SEX = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> CITIZEN_SKIN = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> HOUSEHOLD_ID = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> TELEPORT_TICKS = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TELEPORT_PROGRESS = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DECISION_PROGRESS = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> TELEPORT_TARGET = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> CURRENT_DECISION = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.STRING);
    private static final int MIN_TELEPORT_TICKS = 10;
    private static final int MAX_TELEPORT_TICKS = 200;
    private static final int TELEPORT_PROGRESS_SEGMENTS = 10;
    private CitizenSkills skills = CitizenSkills.empty();
    private CitizenNeeds needs = new CitizenNeeds();
    private int needConsumptionCooldown;
    private long lastMarketVisitDay = -1;
    private CitizenMarketMemory marketMemory;
    private double currentDecisionScore;
    private double currentWorkSpeedMultiplier = 1.0;
    private int currentEstimatedWorkTicks;
    private final CitizenDecisionProgress decisionProgress = new CitizenDecisionProgress();
    private List<CitizenProfilePayload.DecisionScore> decisionScores = List.of();
    private boolean workProductDeliveryRequested;
    private final ItemStack[] inventory = new ItemStack[36];
    private CitizenWorkSchedule.Work inventoryWork;
    private BlockPos teleportDestination;
    private int teleportTotalTicks;
    private int lastTeleportProgress = -1;
    private float bodyTurnTargetYaw;
    private int bodyTurnTicksUntilNext;

    public CitizenEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        Arrays.fill(inventory, ItemStack.EMPTY);
        if (!level.isClientSide()) {
            applyIdentity(CitizenIdentity.create());
            if (level instanceof ServerLevel serverLevel) {
                needs = new CitizenNeeds(0, 0, 0, serverLevel.getOverworldClockTime() / 24_000L);
            }
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
        builder.define(TELEPORT_TICKS, 0);
        builder.define(TELEPORT_PROGRESS, 0);
        builder.define(DECISION_PROGRESS, 0);
        builder.define(TELEPORT_TARGET, "");
        builder.define(CURRENT_DECISION, "idle");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 96.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new CitizenDecisionGoal(this));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0f));
    }

    public boolean isTeleporting() {
        return entityData.get(TELEPORT_TICKS) > 0;
    }

    public int teleportProgress() {
        return entityData.get(TELEPORT_PROGRESS);
    }

    public String currentDecision() {
        return entityData.get(CURRENT_DECISION);
    }

    public int decisionProgress() {
        return entityData.get(DECISION_PROGRESS);
    }

    void setCurrentDecision(String decision) {
        setCurrentDecision(decision, 0.0, 1.0, 0);
    }

    void setCurrentDecision(String decision, double score, double speedMultiplier, int estimatedWorkTicks) {
        setCurrentDecision(decision, score, speedMultiplier, estimatedWorkTicks, 0);
    }

    void setCurrentDecision(String decision, double score, double speedMultiplier,
            int estimatedWorkTicks, int durationTicks) {
        boolean changed = !entityData.get(CURRENT_DECISION).equals(decision);
        entityData.set(CURRENT_DECISION, decision);
        currentDecisionScore = Double.isFinite(score) ? score : 0.0;
        currentWorkSpeedMultiplier = Double.isFinite(speedMultiplier) && speedMultiplier > 0.0
                ? speedMultiplier : 1.0;
        currentEstimatedWorkTicks = Math.max(0, estimatedWorkTicks);
        if (changed) decisionProgress.start(durationTicks);
        else if (decisionProgress.durationTicks() == 0 && durationTicks > 0) decisionProgress.start(durationTicks);
        syncDecisionProgress();
    }

    int decisionProgressTicks() { return decisionProgress.elapsedTicks(); }
    int decisionDurationTicks() { return decisionProgress.durationTicks(); }
    void advanceDecisionProgress() {
        decisionProgress.advance();
        syncDecisionProgress();
    }
    void restartDecisionProgress(int durationTicks) {
        decisionProgress.start(durationTicks);
        syncDecisionProgress();
    }

    private void syncDecisionProgress() {
        entityData.set(DECISION_PROGRESS, decisionProgress.percent() / TELEPORT_PROGRESS_SEGMENTS);
    }
    void setDecisionScores(List<CitizenDecisionPlanner.Candidate> candidates,
            CitizenDecisionPlanner.Action selectedAction) {
        decisionScores = candidates.stream().map(candidate -> new CitizenProfilePayload.DecisionScore(
                candidate.action().name().toLowerCase(java.util.Locale.ROOT),
                candidate.eligible(), candidate.action() == selectedAction,
                candidate.eligible() && Double.isFinite(candidate.score()) ? candidate.score() : 0.0,
                candidate.details()
        )).toList();
    }

    public void beginTeleport(ServerLevel level, BlockPos destination, String targetName) {
        if (isTeleporting()) return;
        BlockPos destinationPos = destination.immutable();
        level.getChunkAt(destinationPos);
        double distance = Math.sqrt(distanceToSqr(destinationPos.getX() + 0.5,
                destinationPos.getY(), destinationPos.getZ() + 0.5));
        int duration = Math.clamp((int) Math.ceil(distance * 2.0), MIN_TELEPORT_TICKS, MAX_TELEPORT_TICKS);
        teleportDestination = destinationPos;
        teleportTotalTicks = duration;
        lastTeleportProgress = 0;
        entityData.set(TELEPORT_TARGET, targetName);
        entityData.set(TELEPORT_TICKS, duration);
        entityData.set(TELEPORT_PROGRESS, 0);
        setDeltaMovement(Vec3.ZERO);
        setCustomName(teleportDisplayName(targetName));
        setCustomNameVisible(true);
    }

    public void cancelTeleport() {
        entityData.set(TELEPORT_TICKS, 0);
        entityData.set(TELEPORT_PROGRESS, 0);
        entityData.set(TELEPORT_TARGET, "");
        teleportDestination = null;
        teleportTotalTicks = 0;
        lastTeleportProgress = -1;
        setCustomName(Component.literal(citizenName()));
        setCustomNameVisible(true);
    }

    private static Component teleportDisplayName(String targetName) {
        String targetKey = switch (targetName) {
            case "farm" -> "citizen.economistwars.destination.farm";
            case "mining_encampment" -> "citizen.economistwars.destination.mining_encampment";
            case "storage" -> "citizen.economistwars.destination.storage";
            case "market" -> "citizen.economistwars.destination.market";
            default -> "citizen.economistwars.destination.home";
        };
        return Component.translatable("citizen.economistwars.teleporting", Component.translatable(targetKey));
    }

    @Override
    public void travel(Vec3 travelVector) {
        // Citizens only change position through their distance-timed teleport trips.
        setDeltaMovement(Vec3.ZERO);
    }

    public boolean canCarryWheat(int amount) {
        return canCarryItem(new ItemStack(Items.WHEAT, amount));
    }

    public boolean canCarryItem(ItemStack offered) {
        return offered != null && !offered.isEmpty() && canCarryItems(List.of(offered));
    }

    public boolean canCarryItems(List<ItemStack> offered) {
        ItemStack[] simulated = Arrays.copyOf(inventory, inventory.length);
        for (ItemStack stack : offered) {
            if (stack == null || stack.isEmpty()) continue;
            int remaining = stack.getCount();
            for (ItemStack slot : simulated) {
                if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)) {
                    remaining -= Math.max(0, slot.getMaxStackSize() - slot.getCount());
                }
            }
            for (ItemStack slot : simulated) {
                if (slot.isEmpty()) remaining -= stack.getMaxStackSize();
                if (remaining <= 0) break;
            }
            if (remaining > 0) return false;
            insertIntoSlots(simulated, stack);
        }
        return true;
    }

    public boolean hasCarriedItems() {
        for (ItemStack stack : inventory) if (!stack.isEmpty()) return true;
        return false;
    }

    public List<ItemStack> inventoryContents() {
        ArrayList<ItemStack> copy = new ArrayList<>(inventory.length);
        for (ItemStack stack : inventory) copy.add(stack.copy());
        return List.copyOf(copy);
    }

    public ItemStack inventoryItem(int slot) { return inventory[slot]; }

    public void setInventoryItem(int slot, ItemStack stack) {
        inventory[slot] = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        if (!hasCarriedItems()) inventoryWork = null;
    }

    public boolean inventoryFull() {
        for (ItemStack stack : inventory) {
            if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize()) return false;
        }
        return true;
    }

    public CitizenWorkSchedule.Work inventoryWork() { return inventoryWork; }

    public void setInventoryWork(CitizenWorkSchedule.Work work) { inventoryWork = work; }

    public void clearInventoryWork() { inventoryWork = null; }

    public boolean workProductDeliveryRequested() {
        return workProductDeliveryRequested;
    }

    public void requestWorkProductDelivery() {
        workProductDeliveryRequested = true;
    }

    public void clearWorkProductDeliveryRequest() {
        workProductDeliveryRequested = false;
    }

    public void setCarriedItems(ItemStack remainder) {
        Arrays.fill(inventory, ItemStack.EMPTY);
        if (remainder != null && !remainder.isEmpty()) {
            insertIntoSlots(inventory, remainder);
        }
        setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    }

    public void carryItem(ItemStack offered) {
        if (!canCarryItem(offered)) {
            throw new IllegalStateException("Citizen cannot carry the offered items");
        }
        carryItems(List.of(offered));
    }

    public void carryItems(List<ItemStack> offered) {
        if (!canCarryItems(offered)) throw new IllegalStateException("Citizen inventory cannot carry the offered items");
        if (!hasCarriedItems() && offered.stream().anyMatch(stack -> stack != null && !stack.isEmpty())) {
            inventoryWork = CitizenWorkSchedule.workFor((ServerLevel) level());
        }
        for (ItemStack stack : offered) insertIntoSlots(inventory, stack);
        setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    }

    private static void insertIntoSlots(ItemStack[] slots, ItemStack offered) {
        if (offered == null || offered.isEmpty()) return;
        ItemStack remaining = offered.copy();
        for (int i = 0; i < slots.length && !remaining.isEmpty(); i++) {
            ItemStack slot = slots[i];
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, remaining)) {
                int moved = Math.min(remaining.getCount(), slot.getMaxStackSize() - slot.getCount());
                if (moved > 0) {
                    ItemStack merged = slot.copy();
                    merged.grow(moved);
                    slots[i] = merged;
                    remaining.shrink(moved);
                }
            }
        }
        for (int i = 0; i < slots.length && !remaining.isEmpty(); i++) {
            if (slots[i].isEmpty()) {
                int moved = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                slots[i] = remaining.copyWithCount(moved);
                remaining.shrink(moved);
            }
        }
        if (!remaining.isEmpty()) throw new IllegalStateException("Citizen inventory overflow");
    }

    public void carryWheat(int amount) {
        if (!canCarryWheat(amount)) {
            throw new IllegalStateException("Citizen cannot carry another wheat harvest");
        }
        carryItems(List.of(new ItemStack(Items.WHEAT, amount)));
    }

    public boolean hasCarriedWheat() {
        return Arrays.stream(inventory).anyMatch(stack -> stack.is(Items.WHEAT) && !stack.isEmpty());
    }

    public boolean canBakeBreadFromWheat() {
        return inventoryAfterBakingBread() != null;
    }

    public boolean bakeBreadFromWheat() {
        ItemStack[] bakedInventory = inventoryAfterBakingBread();
        if (bakedInventory == null) return false;
        System.arraycopy(bakedInventory, 0, inventory, 0, inventory.length);
        return true;
    }

    private ItemStack[] inventoryAfterBakingBread() {
        ItemStack[] simulated = Arrays.stream(inventory).map(ItemStack::copy).toArray(ItemStack[]::new);
        int wheatToConsume = 3;
        for (int slot = 0; slot < simulated.length && wheatToConsume > 0; slot++) {
            ItemStack stack = simulated[slot];
            if (stack.isEmpty() || !stack.is(Items.WHEAT)) continue;
            int consumed = Math.min(wheatToConsume, stack.getCount());
            wheatToConsume -= consumed;
            simulated[slot] = consumed == stack.getCount()
                    ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - consumed);
        }
        if (wheatToConsume > 0) return null;
        try {
            insertIntoSlots(simulated, new ItemStack(Items.BREAD));
        } catch (IllegalStateException noInventorySpace) {
            return null;
        }
        return simulated;
    }

    public void setCarriedWheat(ItemStack remainder) {
        setCarriedItems(remainder);
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
        if (!level().isClientSide() && level() instanceof ServerLevel serverLevel) {
            needs.advanceToDay(serverLevel.getOverworldClockTime() / 24_000L);
            if (needConsumptionCooldown-- <= 0) {
                needConsumptionCooldown = 20;
                consumeForUrgentNeed(serverLevel);
            }
        }
        if (!level().isClientSide() && isSleeping() && level() instanceof ServerLevel serverLevel
                && !CitizenWorkSchedule.isNight(serverLevel)) {
            stopSleeping();
        }
        if (!level().isClientSide()) tickRandomBodyTurn();
        if (!level().isClientSide() && isTeleporting()) {
            int remaining = entityData.get(TELEPORT_TICKS) - 1;
            entityData.set(TELEPORT_TICKS, remaining);
            setDeltaMovement(Vec3.ZERO);
            int filledSegments = Math.clamp(
                    (teleportTotalTicks - remaining) * TELEPORT_PROGRESS_SEGMENTS / teleportTotalTicks,
                    0, TELEPORT_PROGRESS_SEGMENTS);
            if (filledSegments != lastTeleportProgress) {
                lastTeleportProgress = filledSegments;
                entityData.set(TELEPORT_PROGRESS, filledSegments);
            }
            if (remaining <= 0 && teleportDestination != null && level() instanceof ServerLevel serverLevel) {
                serverLevel.getChunkAt(teleportDestination);
                teleportTo(teleportDestination.getX() + 0.5, teleportDestination.getY(), teleportDestination.getZ() + 0.5);
                teleportDestination = null;
                teleportTotalTicks = 0;
                lastTeleportProgress = -1;
                entityData.set(TELEPORT_TARGET, "");
                entityData.set(TELEPORT_PROGRESS, 0);
                setCustomName(Component.literal(citizenName()));
                setCustomNameVisible(true);
            }
        }
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

    public CitizenNeeds needs() {
        return needs;
    }

    private void consumeForUrgentNeed(ServerLevel level) {
        UUID household = householdId().orElse(null);
        if (household == null || !HouseholdSavedData.get(level).hasMember(household, citizenId())) return;
        BlockPos storagePos = HouseholdFarmSavedData.get(level).storagePosition(level, household).orElse(null);
        if (storagePos == null || !level.isLoaded(storagePos)
                || !(level.getBlockEntity(storagePos) instanceof com.economistwars.household.HouseholdStorageBlockEntity storage)) return;
        ItemStack selected = ItemStack.EMPTY;
        double selectedUtility = Double.NEGATIVE_INFINITY;
        for (ItemStack candidate : storage.contents(household)) {
            ItemNeedValues values = ItemNeedValues.forItem(candidate.getItem());
            if (!values.satisfiesUrgentNeed(needs)) continue;
            double utility = values.utility(needs);
            if (utility > selectedUtility) {
                selected = candidate;
                selectedUtility = utility;
            }
        }
        if (selected.isEmpty()) return;
        ItemStack selectedCopy = selected.copy();
        ItemStack consumed = storage.takeOne(household,
                stored -> ItemStack.isSameItemSameComponents(stored, selectedCopy));
        if (!consumed.isEmpty()) {
            ItemNeedValues values = ItemNeedValues.forItem(consumed.getItem());
            needs.satisfy(values.eat(), values.entertainment(), values.safety());
        }
    }

    public void recordMarketVisit(long day) {
        lastMarketVisitDay = Math.max(lastMarketVisitDay, day);
    }

    public long lastMarketVisitDay() {
        return lastMarketVisitDay;
    }

    public CitizenMarketMemory marketMemory() { return marketMemory; }

    public void refreshMarketMemory(CitizenMarketMemory memory) { marketMemory = memory; }

    private void tickRandomBodyTurn() {
        if (bodyTurnTicksUntilNext-- <= 0) {
            bodyTurnTargetYaw = Mth.wrapDegrees(getYRot() + getRandom().nextFloat() * 360.0F - 180.0F);
            bodyTurnTicksUntilNext = 80 + getRandom().nextInt(121);
        }
        float nextYaw = Mth.approachDegrees(getYRot(), bodyTurnTargetYaw, 2.0F);
        setYRot(nextYaw);
        setYBodyRot(nextYaw);
        setYHeadRot(nextYaw);
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
        output.putInt("NeedEat", needs.eat());
        output.putInt("NeedEntertainment", needs.entertainment());
        output.putInt("NeedSafety", needs.safety());
        output.putLong("NeedsUpdatedDay", needs.lastUpdatedDay());
        output.putLong("LastMarketVisitDay", lastMarketVisitDay);
        if (marketMemory != null) {
            CitizenMarketMemory.Saved savedMemory = marketMemory.save();
            output.putString("MarketMemoryReceived", savedMemory.receivedId());
            output.putString("MarketMemoryRequested", savedMemory.requestedId());
            output.putLong("MarketMemoryPosition", savedMemory.marketPosition());
        }
        output.putString("InventoryWork", inventoryWork == null ? "" : inventoryWork.name());
        output.putInt("TeleportTicks", entityData.get(TELEPORT_TICKS));
        output.putInt("TeleportDuration", teleportTotalTicks);
        output.putString("TeleportTarget", entityData.get(TELEPORT_TARGET));
        if (teleportDestination != null) output.putLong("TeleportDestination", teleportDestination.asLong());
        for (int i = 0; i < inventory.length; i++) output.store("CitizenInventory" + i, ItemStack.OPTIONAL_CODEC, inventory[i]);
        skills.save(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        Arrays.fill(inventory, ItemStack.EMPTY);
        for (int i = 0; i < inventory.length; i++) {
            inventory[i] = input.read("CitizenInventory" + i, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        }
        // Older citizen saves stored work products in the offhand.
        ItemStack legacyCargo = getOffhandItem().copy();
        if (!legacyCargo.isEmpty() && !hasCarriedItems()) insertIntoSlots(inventory, legacyCargo);
        setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        try { inventoryWork = CitizenWorkSchedule.Work.valueOf(input.getString("InventoryWork").orElse("")); }
        catch (IllegalArgumentException exception) { inventoryWork = null; }
        int teleportTicks = Math.clamp(input.getInt("TeleportTicks").orElse(0), 0, MAX_TELEPORT_TICKS);
        String teleportTarget = input.getString("TeleportTarget").orElse("");
        teleportTotalTicks = teleportTicks > 0
                ? Math.clamp(input.getInt("TeleportDuration").orElse(teleportTicks), teleportTicks, MAX_TELEPORT_TICKS) : 0;
        teleportDestination = teleportTicks > 0
                ? input.getLong("TeleportDestination").map(BlockPos::of).orElse(null) : null;
        entityData.set(TELEPORT_TICKS, teleportDestination == null ? 0 : teleportTicks);
        entityData.set(TELEPORT_TARGET, teleportDestination == null ? "" : teleportTarget);
        entityData.set(CITIZEN_ID, input.getString("CitizenId").orElse(getUUID().toString()));
        entityData.set(CITIZEN_NAME, input.getString("CitizenName").orElse("Citizen"));
        entityData.set(CITIZEN_SEX, input.getString("CitizenSex").orElse(CitizenSex.FEMALE.name()));
        entityData.set(CITIZEN_SKIN, input.getString("CitizenSkin").orElse("economistwars:textures/entity/citizen/female_1.png"));
        entityData.set(HOUSEHOLD_ID, input.getString("HouseholdId").orElse(""));
        long currentDay = level() instanceof ServerLevel serverLevel
                ? serverLevel.getOverworldClockTime() / 24_000L : 0;
        needs = new CitizenNeeds(
                input.getInt("NeedEat").orElse(0),
                input.getInt("NeedEntertainment").orElse(0),
                input.getInt("NeedSafety").orElse(0),
                input.getLong("NeedsUpdatedDay").orElse(currentDay));
        needs.advanceToDay(currentDay);
        lastMarketVisitDay = input.getLong("LastMarketVisitDay").orElse(-1L);
        marketMemory = CitizenMarketMemory.load(new CitizenMarketMemory.Saved(
                input.getString("MarketMemoryReceived").orElse(""),
                input.getString("MarketMemoryRequested").orElse(""),
                input.getLong("MarketMemoryPosition").orElse(0L))).orElse(null);
        skills = CitizenSkills.load(input);
        if (inventoryWork == null && hasCarriedItems()) {
            inventoryWork = hasCarriedWheat() ? CitizenWorkSchedule.Work.FARM : CitizenWorkSchedule.Work.MINE;
        }
        lastTeleportProgress = isTeleporting() ? -1 : 0;
        int progress = isTeleporting() ? (teleportTotalTicks - teleportTicks)
                * TELEPORT_PROGRESS_SEGMENTS / teleportTotalTicks : 0;
        entityData.set(TELEPORT_PROGRESS, progress);
        setCustomName(isTeleporting()
                ? teleportDisplayName(teleportTarget)
                : Component.literal(citizenName()));
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
                    currentDecision(),
                    currentDecisionScore,
                    decisionScores,
                    decisionProgressTicks(),
                    decisionDurationTicks(),
                    needs.eat(),
                    needs.entertainment(),
                    needs.safety(),
                    currentWorkSpeedMultiplier,
                    currentEstimatedWorkTicks,
                    marketMemory == null ? ItemStack.EMPTY : new ItemStack(marketMemory.received()),
                    marketMemory == null ? ItemStack.EMPTY : new ItemStack(marketMemory.requested()),
                    marketMemory != null,
                    marketMemory == null ? 0 : marketMemory.marketPosition().getX(),
                    marketMemory == null ? 0 : marketMemory.marketPosition().getY(),
                    marketMemory == null ? 0 : marketMemory.marketPosition().getZ(),
                    marketMemory == null ? 0.0 : marketMemory.gain(needs),
                    Arrays.stream(CitizenSkill.values())
                            .map(skill -> new CitizenProfilePayload.SkillProgress(
                                    skill.serializedName(), skills.experience(skill)))
                            .toList(),
                    inventoryContents(),
                    getMainHandItem().copy()
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
