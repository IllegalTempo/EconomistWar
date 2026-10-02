package com.economistwars.citizen;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Persistent experience values for a citizen's extensible skill set. */
public final class CitizenSkills {
    public static final com.mojang.serialization.Codec<CitizenSkills> CODEC =
            com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING,
                    com.mojang.serialization.Codec.intRange(0, 10_000)).xmap(CitizenSkills::fromSnapshot, CitizenSkills::snapshot);
    private static final int MAX_EXPERIENCE = 10_000;
    private final Map<CitizenSkill, Integer> experience = new EnumMap<>(CitizenSkill.class);

    private CitizenSkills() {
        for (CitizenSkill skill : CitizenSkill.values()) {
            experience.put(skill, 0);
        }
    }

    public static CitizenSkills empty() {
        return new CitizenSkills();
    }

    public Map<String, Integer> snapshot() {
        Map<String, Integer> result = new java.util.LinkedHashMap<>();
        for (CitizenSkill skill : CitizenSkill.values()) result.put(skill.serializedName(), experience(skill));
        return result;
    }

    public static CitizenSkills fromSnapshot(Map<String, Integer> saved) {
        CitizenSkills result = empty();
        for (CitizenSkill skill : CitizenSkill.values())
            result.experience.put(skill, Math.clamp(saved.getOrDefault(skill.serializedName(), 0), 0, MAX_EXPERIENCE));
        return result;
    }

    public int experience(CitizenSkill skill) {
        return experience.getOrDefault(skill, 0);
    }

    public int level(CitizenSkill skill) {
        return Math.min(100, experience(skill) / 100);
    }

    public int progressPercent(CitizenSkill skill) {
        if (level(skill) >= 100) {
            return 100;
        }
        return experience(skill) % 100;
    }

    public void addExperience(CitizenSkill skill, int amount) {
        if (amount <= 0) {
            return;
        }
        long updated = (long) experience(skill) + amount;
        experience.put(skill, (int) Math.min(MAX_EXPERIENCE, updated));
    }

    public void save(ValueOutput output) {
        ValueOutput skills = output.child("Skills");
        for (CitizenSkill skill : CitizenSkill.values()) {
            skills.putInt(skill.serializedName(), experience(skill));
        }
    }

    public static CitizenSkills load(ValueInput input) {
        CitizenSkills skills = empty();
        ValueInput saved = input.childOrEmpty("Skills");
        for (CitizenSkill skill : CitizenSkill.values()) {
            skills.experience.put(skill, Math.clamp(saved.getIntOr(skill.serializedName(), 0), 0, MAX_EXPERIENCE));
        }
        return skills;
    }
}
