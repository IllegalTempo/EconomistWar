package com.economistwars.citizen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** One citizen's need. Larger urgency values mean a more urgent need. */
public final class CitizenNeed {
    public static final Codec<CitizenNeed> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.validate(name -> name.isBlank()
                    ? com.mojang.serialization.DataResult.error(() -> "Need name must not be blank")
                    : com.mojang.serialization.DataResult.success(name)).fieldOf("name").forGetter(CitizenNeed::name),
            Codec.intRange(0, 100).fieldOf("urgency").forGetter(CitizenNeed::urgency),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("dailyGrowth").forGetter(CitizenNeed::dailyGrowth),
            Codec.FLOAT.validate(damage -> !Float.isFinite(damage) || damage < 0
                    ? com.mojang.serialization.DataResult.error(() -> "Invalid shortage damage")
                    : com.mojang.serialization.DataResult.success(damage))
                    .fieldOf("shortageDamage").forGetter(CitizenNeed::shortageDamage),
            Codec.LONG.fieldOf("lastUpdatedDay").forGetter(CitizenNeed::lastUpdatedDay)
    ).apply(instance, CitizenNeed::new));
    public static final int MAX = 100;
    public static final String EAT = "eat";
    public static final String ENTERTAINMENT = "entertainment";
    public static final String SAFETY = "safety";

    private final String name;
    private final int dailyGrowth;
    private final float shortageDamage;
    private int urgency;
    private long lastUpdatedDay;

    /** Shortage damage is configuration; callers decide when and how to apply it. */
    public CitizenNeed(String name, int urgency, int dailyGrowth, float shortageDamage, long lastUpdatedDay) {
        this.name = Objects.requireNonNull(name);
        if (name.isBlank()) throw new IllegalArgumentException("Need name must not be blank");
        if (dailyGrowth < 0) throw new IllegalArgumentException("Daily growth must not be negative");
        if (!Float.isFinite(shortageDamage) || shortageDamage < 0)
            throw new IllegalArgumentException("Shortage damage must be finite and non-negative");
        this.urgency = Math.clamp(urgency, 0, MAX);
        this.dailyGrowth = dailyGrowth;
        this.shortageDamage = shortageDamage;
        this.lastUpdatedDay = Math.max(0, lastUpdatedDay);
    }

    public String name() { return name; }
    public int urgency() { return urgency; }
    public int dailyGrowth() { return dailyGrowth; }
    public float shortageDamage() { return shortageDamage; }
    public float damageForShortage(int shortage) { return Math.min(4.0F, Math.max(0, shortage) * shortageDamage); }
    public long lastUpdatedDay() { return lastUpdatedDay; }
    public boolean isUrgent() { return urgency >= 50; }

    public void advanceToDay(long day) {
        if (day <= lastUpdatedDay) return;
        long elapsedDays = day - lastUpdatedDay;
        if (dailyGrowth > 0) {
            long neededDays = ((long) MAX - urgency + dailyGrowth - 1) / dailyGrowth;
            urgency = elapsedDays >= neededDays ? MAX : (int) (urgency + elapsedDays * dailyGrowth);
        }
        lastUpdatedDay = day;
    }

    public void satisfy(int amount) {
        urgency = Math.max(0, urgency - Math.max(0, amount));
    }

    /** Creates fresh instances for each citizen; needs must never be shared across citizens. */
    public static Map<String, CitizenNeed> defaults(int eat, int entertainment, int safety, long day) {
        Map<String, CitizenNeed> needs = new LinkedHashMap<>();
        needs.put(EAT, new CitizenNeed(EAT, eat, 25, 1.0F, day));
        needs.put(ENTERTAINMENT, new CitizenNeed(ENTERTAINMENT, entertainment, 12, 0.0F, day));
        needs.put(SAFETY, new CitizenNeed(SAFETY, safety, 6, 0.0F, day));
        return needs;
    }

    public static int urgency(Map<String, CitizenNeed> needs, String name) {
        CitizenNeed need = needs.get(name);
        return need == null ? 0 : need.urgency();
    }
}
