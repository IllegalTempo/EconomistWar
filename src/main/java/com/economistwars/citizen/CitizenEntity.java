package com.economistwars.citizen;

import com.economistwars.network.CitizenNetworking;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.damagesource.DamageSource;

/** Visual and interaction adapter. The server's CitizenState is the sole simulation authority. */
public final class CitizenEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> ID = SynchedEntityData.defineId(CitizenEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> NAME = SynchedEntityData.defineId(CitizenEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SEX = SynchedEntityData.defineId(CitizenEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SKIN = SynchedEntityData.defineId(CitizenEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> HOUSEHOLD = SynchedEntityData.defineId(CitizenEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> ACTION = SynchedEntityData.defineId(CitizenEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> TRAVEL = SynchedEntityData.defineId(CitizenEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TRAVEL_PROGRESS = SynchedEntityData.defineId(CitizenEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTION_PROGRESS = SynchedEntityData.defineId(CitizenEntity.class,EntityDataSerializers.INT);
    private CitizenState legacy;
    private boolean applying;
    public CitizenEntity(EntityType<? extends PathfinderMob> type,Level level) {
        super(type,level); setNoGravity(true);
        if (!level.isClientSide()) applyIdentity(CitizenIdentity.create());
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(ID,""); b.define(NAME,"Citizen"); b.define(SEX,"FEMALE");
        b.define(SKIN,"economistwars:textures/entity/citizen/female_1.png"); b.define(HOUSEHOLD,"");
        b.define(ACTION,"idle"); b.define(TRAVEL,0); b.define(TRAVEL_PROGRESS,0); b.define(ACTION_PROGRESS,0);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,20).add(Attributes.MOVEMENT_SPEED,.25).add(Attributes.FOLLOW_RANGE,96);
    }
    @Override protected void registerGoals() { }
    @Override public void travel(Vec3 vector) { setDeltaMovement(Vec3.ZERO); }
    @Override public boolean isPersistenceRequired() { return true; }
    public UUID citizenId() { try { return UUID.fromString(entityData.get(ID)); } catch (IllegalArgumentException e) { return getUUID(); } }
    public String citizenName() { return entityData.get(NAME); }
    public CitizenSex sex() { return CitizenSex.fromSavedValue(entityData.get(SEX)); }
    public Identifier skin() { return Identifier.parse(entityData.get(SKIN)); }
    public Optional<UUID> householdId() { try { return Optional.of(UUID.fromString(entityData.get(HOUSEHOLD))); } catch (IllegalArgumentException e) { return Optional.empty(); } }
    public void setHouseholdId(UUID id) { entityData.set(HOUSEHOLD,id == null ? "" : id.toString()); }
    public boolean isTeleporting() { return entityData.get(TRAVEL)>0; }
    public int teleportProgress() { return entityData.get(TRAVEL_PROGRESS); }
    public String currentDecision() { return entityData.get(ACTION); }
    public int decisionProgress() { return entityData.get(ACTION_PROGRESS); }
    public void applyIdentity(CitizenIdentity identity) {
        entityData.set(ID,identity.id().toString()); entityData.set(NAME,identity.name());
        entityData.set(SEX,identity.sex().name()); entityData.set(SKIN,identity.skin().toString());
        setCustomName(Component.literal(identity.name())); setCustomNameVisible(true);
    }
    public void bind(UUID id) { entityData.set(ID,id.toString()); setUUID(id); legacy = null; }
    /** Called by spawn commands after assigning identity, household and initial position. */
    public CitizenState createBackendRecord() {
        if (!(level() instanceof ServerLevel server)) throw new IllegalStateException("Server only");
        CitizenState initial = CitizenState.create(new CitizenIdentity(citizenId(),citizenName(),sex(),skin()),householdId().orElse(null),
                server.dimension().identifier().toString(),position(),server.getOverworldClockTime()/24000,citizenId().getLeastSignificantBits());
        CitizenState record = CitizenSavedData.get(server).registerIfAbsent(initial); bind(record.id()); applySnapshot(record); return record;
    }
    public void importLegacy() {
        if (legacy == null || !(level() instanceof ServerLevel server)) return;
        CitizenState record = CitizenSavedData.get(server).registerIfAbsent(legacy); legacy = null;
        entityData.set(ID,record.id().toString()); applySnapshot(record);
    }
    public void applySnapshot(CitizenState state) {
        applying = true;
        try {
            applyIdentity(state.identity); setHouseholdId(state.householdId);
            entityData.set(ACTION,state.actionName()); entityData.set(TRAVEL,state.travelRemaining);
            entityData.set(TRAVEL_PROGRESS,state.travelTotal == 0 ? 0 : (state.travelTotal-state.travelRemaining)*10/state.travelTotal);
            entityData.set(ACTION_PROGRESS,state.actionDuration == 0 ? 0 : (int)Math.clamp((long)state.actionElapsed*10/state.actionDuration,0,10));
            setHealth(state.health); setItemInHand(InteractionHand.MAIN_HAND,CitizenProfileSnapshot.tool(state));
            boolean sleeping = state.action == CitizenDecisionPlanner.Action.SLEEP && !state.travelling() && state.reservedBed != null;
            // Sleeping pose is presentation only; vanilla bed occupancy is not the backend reservation.
            if (sleeping) { setSleepingPos(state.reservedBed); setPose(Pose.SLEEPING); }
            else { clearSleepingPos(); setPose(Pose.STANDING); }
            if (state.travelling()) {
                String destination = switch (state.action == null ? CitizenDecisionPlanner.Action.RETURN_HOME : state.action) {
                    case FARM -> "farm"; case MINE -> "mining_encampment"; case DELIVERY -> "storage"; case MARKET -> "market"; default -> "home";
                };
                setCustomName(Component.translatable("citizen.economistwars.teleporting",Component.translatable("citizen.economistwars.destination."+destination)));
            }
        } finally { applying = false; }
    }
    @Override public void tick() {
        if (level() instanceof ServerLevel server) {
            importLegacy();
            CitizenState state = CitizenSavedData.get(server).find(citizenId()).orElse(null);
            if (state == null || !state.alive) { discard(); return; }
            applySnapshot(state);
        }
        super.tick(); setDeltaMovement(Vec3.ZERO);
    }
    @Override public boolean hurtServer(ServerLevel server,DamageSource source,float amount) {
        CitizenState state = CitizenSavedData.get(server).find(citizenId()).orElse(null);
        if (applying || state == null || !state.alive || isRemoved()) return false;
        setHealth(state.health); float before = getHealth();
        boolean accepted = super.hurtServer(server,source,amount);
        if (accepted && state.alive) CitizenSimulation.damage(state,Math.max(0,before-getHealth()),MinecraftCitizenSimulationContext.create(server.getServer()));
        return accepted;
    }
    @Override public void die(DamageSource source) {
        if (!applying && level() instanceof ServerLevel server) CitizenSavedData.get(server).find(citizenId())
                .ifPresent(state -> CitizenSimulation.damage(state,state.health,MinecraftCitizenSimulationContext.create(server.getServer())));
        super.die(source);
    }
    @Override protected void dropEquipment(ServerLevel server) { /* displayed tools are not owned items */ }
    @Override protected InteractionResult mobInteract(Player player,InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            importLegacy(); CitizenSavedData.get(serverPlayer.level()).find(citizenId()).filter(s -> s.alive).ifPresent(state ->
                    CitizenNetworking.sendProfile(serverPlayer,CitizenProfileSnapshot.create(state,MinecraftCitizenSimulationContext.create(serverPlayer.level().getServer()))));
        }
        return InteractionResult.SUCCESS;
    }
    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out); out.putString("CitizenId",citizenId().toString()); out.putInt("CitizenBackendVersion",1);
        if (legacy != null) out.store("PendingLegacyCitizen",CitizenState.CODEC,legacy);
    }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        entityData.set(ID,in.getString("CitizenId").orElse(getUUID().toString()));
        legacy = in.read("PendingLegacyCitizen",CitizenState.CODEC).orElse(null);
        if (in.getInt("CitizenBackendVersion").orElse(0) >= 1 || legacy != null) return;
        UUID household = null; try { household = UUID.fromString(in.getString("HouseholdId").orElse("")); } catch (IllegalArgumentException ignored) { }
        Identifier skin = Identifier.tryParse(in.getString("CitizenSkin").orElse("economistwars:textures/entity/citizen/female_1.png"));
        if (skin == null) skin = Identifier.parse("economistwars:textures/entity/citizen/female_1.png");
        legacy = CitizenState.create(new CitizenIdentity(citizenId(),in.getString("CitizenName").orElse("Citizen"),
                CitizenSex.fromSavedValue(in.getString("CitizenSex").orElse("FEMALE")),skin),household,
                level().dimension().identifier().toString(),position(),0,citizenId().getLeastSignificantBits());
        legacy.health = getHealth(); legacy.alive = getHealth()>0;
        legacy.skills = CitizenSkills.load(in);
        in.read("CitizenNeeds",CitizenNeed.CODEC.listOf()).ifPresent(needs -> needs.forEach(n -> legacy.needs.put(n.name(),n)));
        for (int i = 0; i < 36; i++) legacy.inventory.set(i,in.read("CitizenInventory"+i,ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        try { legacy.inventoryWork = CitizenWorkSchedule.Work.valueOf(in.getString("InventoryWork").orElse("")); } catch (IllegalArgumentException ignored) { }
        legacy.lastMarketVisitDay = in.getLong("LastMarketVisitDay").orElse(-1L);
        legacy.lastShortageDamageDay = in.getLong("LastShortageDamageDay").orElse(-1L);
        legacy.marketMemory = CitizenMarketMemory.load(new CitizenMarketMemory.Saved(in.getString("MarketMemoryReceived").orElse(""),
                in.getString("MarketMemoryRequested").orElse(""),in.getLong("MarketMemoryPosition").orElse(0L))).orElse(null);
        int remaining = Math.clamp(in.getInt("TeleportTicks").orElse(0),0,200);
        var destination = in.getLong("TeleportDestination");
        if (remaining>0 && destination.isPresent()) {
            BlockPos p = BlockPos.of(destination.get()); legacy.travelOrigin = legacy.position;
            legacy.travelDestination = new Vec3(p.getX()+.5,p.getY(),p.getZ()+.5); legacy.travelRemaining = remaining;
            legacy.travelTotal = Math.clamp(in.getInt("TeleportDuration").orElse(remaining),remaining,200);
            legacy.rescoreTicks = remaining;
        }
    }
}
