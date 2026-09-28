package com.economistwars.citizen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CitizenSkillsTest {
    @Test
    void emptySkillsStartAtZero() {
        CitizenSkills skills = CitizenSkills.empty();

        assertEquals(0, skills.experience(CitizenSkill.FARMING));
        assertEquals(0, skills.level(CitizenSkill.FARMING));
        assertEquals(0, skills.level(CitizenSkill.MINING));
        assertEquals(0, skills.level(CitizenSkill.BUILDING));
    }

    @Test
    void levelsAdvanceEveryHundredExperienceAndCapAtOneHundred() {
        CitizenSkills skills = CitizenSkills.empty();

        skills.addExperience(CitizenSkill.FARMING, 99);
        assertEquals(0, skills.level(CitizenSkill.FARMING));
        skills.addExperience(CitizenSkill.FARMING, 1);
        assertEquals(1, skills.level(CitizenSkill.FARMING));
        skills.addExperience(CitizenSkill.FARMING, Integer.MAX_VALUE);
        assertEquals(100, skills.level(CitizenSkill.FARMING));
    }

    @Test
    void nonPositiveExperienceDoesNotChangeSkill() {
        CitizenSkills skills = CitizenSkills.empty();

        skills.addExperience(CitizenSkill.BUILDING, -10);
        skills.addExperience(CitizenSkill.BUILDING, 0);

        assertEquals(0, skills.experience(CitizenSkill.BUILDING));
    }

    @Test
    void progressShowsExperienceWithinCurrentLevelAndFullAtMaximum() {
        CitizenSkills skills = CitizenSkills.empty();

        skills.addExperience(CitizenSkill.FARMING, 250);
        assertEquals(50, skills.progressPercent(CitizenSkill.FARMING));
        skills.addExperience(CitizenSkill.FARMING, 9_750);
        assertEquals(100, skills.progressPercent(CitizenSkill.FARMING));
    }
}
