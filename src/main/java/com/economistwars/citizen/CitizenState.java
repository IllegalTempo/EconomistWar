package com.economistwars.citizen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.economistwars.network.CitizenProfilePayload;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Authoritative citizen. Mutable only on the server simulation thread. */
public final class CitizenState {
    public static final Codec<CitizenState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("state").forGetter(CitizenState::metadata),
            CitizenNeed.CODEC.listOf().fieldOf("needs").forGetter(s -> List.copyOf(s.needs.values())),
            CitizenSkills.CODEC.fieldOf("skills").forGetter(s -> s.skills),
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("inventory").forGetter(s -> s.inventory.contents()),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("pendingDrops", List.of()).forGetter(s -> s.pendingDrops)
    ).apply(i, CitizenState::load));

    CitizenIdentity identity;
    UUID householdId;
    String dimension;
    Vec3 position;
    float health = 20;
    boolean alive = true;
    Map<String, CitizenNeed> needs;
    CitizenSkills skills = CitizenSkills.empty();
    CitizenInventory inventory = new CitizenInventory();
    CitizenRandom random;
    CitizenWorkSchedule.Work inventoryWork;
    boolean deliveryRequested;
    CitizenMarketMemory marketMemory;
    CitizenDecisionPlanner.Action action;
    BlockPos target;
    BlockPos reservedBed;
    int workTicks, rescoreTicks, actionElapsed, actionDuration;
    int consumptionCooldown;
    long lastMarketVisitDay = -1, lastShortageDamageDay = -1;
    Vec3 travelOrigin, travelDestination;
    int travelRemaining, travelTotal;
    List<ItemStack> pendingDrops = List.of();
    boolean dropsRolled;
    // Derived presentation data; refreshed from evaluation after load.
    double decisionScore, speedMultiplier = 1;
    int estimatedWorkTicks;
    List<CitizenProfilePayload.DecisionScore> decisionScores = List.of();

    public static CitizenState create(CitizenIdentity identity, UUID householdId, String dimension,
            Vec3 position, long day, long randomSeed) {
        CitizenState s = new CitizenState();
        s.identity = identity; s.householdId = householdId; s.dimension = dimension; s.position = position;
        s.needs = CitizenNeed.defaults(0, 0, 0, day); s.random = new CitizenRandom(randomSeed);
        return s;
    }
    public UUID id() { return identity.id(); }
    public boolean travelling() { return travelRemaining > 0 && travelDestination != null; }
    public void beginTravel(Vec3 destination) {
        travelOrigin = position; travelDestination = destination;
        travelTotal = CitizenDecisionPlanner.travelTicks(position.distanceToSqr(destination));
        travelRemaining = travelTotal;
    }
    public void advanceTravel() {
        if (travelling() && --travelRemaining == 0) {
            position = travelDestination; travelDestination = null; travelOrigin = null;
        }
    }
    public void cancelTravel() { travelRemaining = 0; travelDestination = null; travelOrigin = null; }
    public String actionName() { return action == null ? "idle" : action.name().toLowerCase(Locale.ROOT); }

    private Map<String, String> metadata() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("version", "1"); m.put("id", id().toString()); m.put("name", identity.name());
        m.put("sex", identity.sex().name()); m.put("skin", identity.skin().toString());
        m.put("household", householdId == null ? "" : householdId.toString());
        m.put("dimension", dimension); vector(m, "position", position);
        m.put("health", Float.toString(health)); m.put("alive", Boolean.toString(alive));
        m.put("random", Long.toString(random.state())); m.put("action", actionName());
        m.put("inventoryWork", inventoryWork == null ? "" : inventoryWork.name());
        m.put("delivery", Boolean.toString(deliveryRequested));
        m.put("target", target == null ? "" : Long.toString(target.asLong()));
        m.put("bed", reservedBed == null ? "" : Long.toString(reservedBed.asLong()));
        m.put("work", Integer.toString(workTicks)); m.put("rescore", Integer.toString(rescoreTicks));
        m.put("elapsed", Integer.toString(actionElapsed)); m.put("duration", Integer.toString(actionDuration));
        m.put("consumption", Integer.toString(consumptionCooldown));
        m.put("marketDay", Long.toString(lastMarketVisitDay)); m.put("damageDay", Long.toString(lastShortageDamageDay));
        m.put("travelRemaining", Integer.toString(travelRemaining)); m.put("travelTotal", Integer.toString(travelTotal));
        if (travelDestination != null) vector(m, "destination", travelDestination);
        if (travelOrigin != null) vector(m, "origin", travelOrigin);
        m.put("rolled", Boolean.toString(dropsRolled));
        if (marketMemory != null) {
            var memory = marketMemory.save(); m.put("received", memory.receivedId());
            m.put("requested", memory.requestedId()); m.put("marketPosition", Long.toString(memory.marketPosition()));
        }
        return m;
    }
    private static void vector(Map<String,String> m, String key, Vec3 v) {
        m.put(key + "X", Double.toString(v.x)); m.put(key + "Y", Double.toString(v.y)); m.put(key + "Z", Double.toString(v.z));
    }
    private static Vec3 vector(Map<String,String> m, String key) {
        return new Vec3(Double.parseDouble(m.getOrDefault(key + "X", "0")),
                Double.parseDouble(m.getOrDefault(key + "Y", "0")), Double.parseDouble(m.getOrDefault(key + "Z", "0")));
    }
    private static int number(Map<String,String> m, String key) { return Math.max(0, Integer.parseInt(m.getOrDefault(key, "0"))); }
    private static BlockPos block(Map<String,String> m, String key) {
        String value = m.getOrDefault(key, ""); return value.isEmpty() ? null : BlockPos.of(Long.parseLong(value));
    }
    private static CitizenState load(Map<String,String> m, List<CitizenNeed> needs, CitizenSkills skills,
            List<ItemStack> inventory, List<ItemStack> drops) {
        CitizenIdentity identity = new CitizenIdentity(UUID.fromString(m.get("id")), m.get("name"),
                CitizenSex.fromSavedValue(m.get("sex")), Identifier.parse(m.get("skin")));
        String household = m.getOrDefault("household", "");
        CitizenState s = create(identity, household.isEmpty() ? null : UUID.fromString(household),
                m.get("dimension"), vector(m, "position"), 0, Long.parseLong(m.get("random")));
        needs.forEach(n -> s.needs.put(n.name(), n)); s.skills = skills;
        for (int i = 0; i < Math.min(36, inventory.size()); i++) s.inventory.set(i, inventory.get(i));
        s.health = Math.clamp(Float.parseFloat(m.getOrDefault("health", "20")), 0, 20);
        s.alive = Boolean.parseBoolean(m.getOrDefault("alive", "true")) && s.health > 0;
        s.lastMarketVisitDay = Long.parseLong(m.getOrDefault("marketDay", "-1"));
        s.lastShortageDamageDay = Long.parseLong(m.getOrDefault("damageDay", "-1"));
        s.consumptionCooldown = number(m, "consumption");
        s.deliveryRequested = Boolean.parseBoolean(m.getOrDefault("delivery", "false"));
        try { s.inventoryWork = CitizenWorkSchedule.Work.valueOf(m.getOrDefault("inventoryWork", "")); }
        catch (IllegalArgumentException ignored) { }
        s.marketMemory = CitizenMarketMemory.load(new CitizenMarketMemory.Saved(m.getOrDefault("received", ""),
                m.getOrDefault("requested", ""), Long.parseLong(m.getOrDefault("marketPosition", "0")))).orElse(null);
        try {
            String action = m.getOrDefault("action", "idle");
            s.action = action.equals("idle") ? null : CitizenDecisionPlanner.Action.valueOf(action.toUpperCase(Locale.ROOT));
            s.target = block(m, "target"); s.reservedBed = block(m, "bed");
            s.workTicks = number(m, "work"); s.rescoreTicks = number(m, "rescore");
            s.actionElapsed = number(m, "elapsed"); s.actionDuration = number(m, "duration");
            s.travelTotal = Math.clamp(number(m, "travelTotal"), 0, 200);
            s.travelRemaining = Math.clamp(number(m, "travelRemaining"), 0, s.travelTotal);
            if (s.travelRemaining > 0 && m.containsKey("destinationX")) {
                s.travelDestination = vector(m, "destination"); s.travelOrigin = vector(m, "origin");
            }
            s.pendingDrops = drops.stream().map(ItemStack::copy).toList(); s.dropsRolled = Boolean.parseBoolean(m.get("rolled"));
        } catch (IllegalArgumentException ignored) {
            s.action = null; s.target = null; s.reservedBed = null; s.workTicks = 0;
            s.cancelTravel(); s.pendingDrops = List.of(); s.dropsRolled = false;
        }
        return s;
    }
}

