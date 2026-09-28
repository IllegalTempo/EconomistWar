package com.economistwars.citizen;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Persistent experience values for a citizen's extensible skill set. */
public final class CitizenSkills {
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
