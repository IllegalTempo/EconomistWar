# Citizen Skills and Scrollable Profile Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist extensible citizen skills, award Farming XP from successful wheat harvests, and make the citizen profile screen scrollable and adaptable to future information.

**Architecture:** Keep skill identifiers and XP/level calculation in a pure common-side `CitizenSkill`/`CitizenSkills` model. `CitizenEntity` owns and persists that model; farming goals award XP only after successful harvest completion. The server includes authoritative skill levels in the existing profile payload, while the client renders a data-driven scrollable viewport.

**Tech Stack:** Minecraft Java Edition 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Java 25, Gradle 9.7, JUnit Jupiter 5.13.4.

**Spec:** `docs/superpowers/specs/2026-09-28-citizen-skills-design.md`

## Global Constraints

- Skill XP is awarded only by server-side gameplay code.
- Initial skills are `FARMING`, `MINING`, and `BUILDING` with stable serialized names.
- Levels are derived as `min(100, xp / 100)`; XP is non-negative and bounded.
- A successful mature wheat harvest awards exactly 5 Farming XP.
- Mining and Building are stored but receive no XP in this slice.
- Older citizens with no skill data load with zero XP for all initial skills.
- The profile remains non-pausing, Escape-closeable, server-authoritative, and resize-tolerant.
- Existing uncommitted work must not be reset, reformatted, or overwritten.

## Review Focus

- Missing skill data in an older save loads as zero for every initial skill; cover in the `CitizenSkills` load test.
- Negative, zero, very large, and unknown skill values cannot create negative XP or levels above 100; cover in the model tests.
- A failed or unauthorized harvest awards no Farming XP, while a successful harvest awards exactly 5; cover the farming reward seam or verify the guarded call in the farming-goal test.
- Profile payload field ordering stays symmetric between encode and decode; cover with a codec round-trip test if the existing test runtime supports Minecraft codecs.
- Small windows, long IDs, and enough fields to exceed panel height remain scrollable without clipping; cover with manual client acceptance and a focused layout helper test if practical.

### Task 1: Implement the persistent skill model

**Files:**
- Create: `src/main/java/com/economistwars/citizen/CitizenSkill.java`
- Create: `src/main/java/com/economistwars/citizen/CitizenSkills.java`
- Modify: `build.gradle` (retain the existing JUnit 5 test setup)
- Test: `src/test/java/com/economistwars/citizen/CitizenSkillsTest.java`

**Interfaces:**
- `CitizenSkill` exposes `FARMING`, `MINING`, and `BUILDING` with stable serialized IDs.
- `CitizenSkills.empty()` returns a zeroed skill set.
- `int experience(CitizenSkill skill)` returns non-negative XP.
- `int level(CitizenSkill skill)` returns `Math.min(100, experience(skill) / 100)`.
- `void addExperience(CitizenSkill skill, int amount)` ignores non-positive amounts and clamps overflow safely.
- `void save(ValueOutput output)` and `static CitizenSkills load(ValueInput input)` persist/load the skill map with safe defaults.

- [ ] **Step 1: Write failing pure-model tests** for zero defaults, 99/100/10000 XP level boundaries, positive accumulation, ignored non-positive awards, and malformed/missing serialized values.
- [ ] **Step 2: Run the focused test and verify it fails** because `CitizenSkill`/`CitizenSkills` are not implemented.
- [ ] **Step 3: Implement the enum and skill model** with a stable ID map, bounded XP, derived levels, and tolerant `ValueInput`/`ValueOutput` persistence.
- [ ] **Step 4: Run the focused test and verify it passes** with no failures.
- [ ] **Step 5: Commit** with `feat: add persistent citizen skills`.

### Task 2: Attach skills to citizens and award Farming XP

**Files:**
- Modify: `src/main/java/com/economistwars/citizen/CitizenEntity.java`
- Modify: `src/main/java/com/economistwars/citizen/CitizenFarmGoal.java`
- Test: `src/test/java/com/economistwars/citizen/CitizenFarmGoalTest.java` or extend the model test with the reward seam.

**Interfaces:**
- `CitizenEntity.skills()` returns the entity-owned `CitizenSkills` instance.
- `CitizenEntity.addSkillExperience(CitizenSkill skill, int amount)` delegates to the model and is ignored on the client.
- Citizen save/load methods include the skill data without changing existing identity defaults.
- `CitizenFarmGoal` awards `CitizenSkill.FARMING` by calling `citizen.addSkillExperience(CitizenSkill.FARMING, 5)` only after the mature crop is successfully replaced and wheat is carried.

- [ ] **Step 1: Write a failing test** proving the farming completion seam awards exactly 5 XP once and does not award XP when the crop update fails or ownership/member validation fails.
- [ ] **Step 2: Run the focused test and verify it fails** because citizens have no skill state/reward call.
- [ ] **Step 3: Add citizen-owned skill state and persistence** in `CitizenEntity`, loading missing skill fields as zero and preserving skills across entity save/load.
- [ ] **Step 4: Add the guarded Farming XP award** immediately after the existing successful harvest actions in `CitizenFarmGoal`; do not award from client ticks, search, navigation, or swing animation.
- [ ] **Step 5: Run focused tests and verify they pass**; then run the full available test task.
- [ ] **Step 6: Commit** with `feat: award farming experience to citizens`.

### Task 3: Add authoritative skill values to citizen profiles

**Files:**
- Modify: `src/main/java/com/economistwars/network/CitizenProfilePayload.java`
- Modify: `src/main/java/com/economistwars/citizen/CitizenEntity.java`
- Test: `src/test/java/com/economistwars/network/CitizenProfilePayloadTest.java` if codec tests are supported by the project runtime.

**Interfaces:**
- Extend `CitizenProfilePayload` with `int farmingLevel`, `int miningLevel`, and `int buildingLevel` after the existing household/economy fields.
- Encode/decode the three values in exactly the same order using bounded VarInts.
- Server profile construction reads levels from `CitizenEntity.skills()`; the client never derives or queries them.

- [ ] **Step 1: Write a failing payload round-trip test** for the three skill levels and existing profile fields, or document the runtime limitation if the Minecraft codec cannot be instantiated in unit tests.
- [ ] **Step 2: Run the focused test and verify it fails** because the payload has no skill fields.
- [ ] **Step 3: Extend the payload record and codec** with the three skill levels and update every constructor call.
- [ ] **Step 4: Populate authoritative skill values** in the server interaction snapshot from the citizen's skill model.
- [ ] **Step 5: Run the focused/full tests and verify** encode/decode symmetry or record the blocked codec test separately.
- [ ] **Step 6: Commit** with `feat: include citizen skills in profiles`.

### Task 4: Make the profile screen scrollable and future-proof

**Files:**
- Modify: `src/client/java/com/economistwars/citizen/CitizenProfileScreen.java`
- Modify: `src/main/resources/assets/economistwars/lang/en_us.json`
- Test: `src/test/java/com/economistwars/citizen/CitizenProfileLayoutTest.java` if a pure layout helper is extracted.

**Interfaces:**
- Preserve `CitizenProfileScreen(CitizenProfilePayload profile)` and `isPauseScreen() == false`.
- Keep profile fields in a data-driven list, adding a Skills section with Farming, Mining, and Building levels.
- Maintain a clamped content scroll offset based on viewport height and calculated content height.

- [ ] **Step 1: Write a failing layout test** for content taller than the viewport, clamped scroll bounds, and a small-window panel width/viewport calculation if a pure helper is extracted.
- [ ] **Step 2: Run the focused test and verify it fails** against the current fixed-height renderer.
- [ ] **Step 3: Replace fixed total-height rendering** with a responsive panel, title/header area, bounded viewport, wrapped field layout, and a calculated maximum scroll offset.
- [ ] **Step 4: Add mouse-wheel and keyboard scrolling** that adjusts only the clamped content offset; preserve normal Escape close and non-pausing behavior.
- [ ] **Step 5: Add translations** for the Skills section and three skill labels, and render the new payload values.
- [ ] **Step 6: Run the full test task/build** and verify no failures; if the environment still cannot resolve Loom, record the exact blocker.
- [ ] **Step 7: Commit** with `feat: add citizen skills profile section`.

### Task 5: Manual acceptance and documentation

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/plans/2026-09-28-citizen-skills.md` (check completed steps only)

- [ ] **Step 1: Update README** to describe skill progression from related work, Farming XP from wheat harvests, and Mining/Building as future skill sources.
- [ ] **Step 2: Build with the project JDK 25** using `.tools/gradle-9.7.0/bin/gradle.bat build`.
- [ ] **Step 3: Launch a development client** and verify a citizen starts with level-zero skills, gains Farming XP once per successful harvest, and displays the values in the profile.
- [ ] **Step 4: Resize and scroll the profile** at a small window size; confirm all current fields remain reachable and long IDs wrap without clipping.
- [ ] **Step 5: Reload the world** and verify skill XP/levels persist while Mining and Building remain unchanged.
- [ ] **Step 6: Commit** with `docs: document citizen skill progression`.

## Self-review

- Spec coverage: skill identifiers/model, persistence, Farming reward timing, authoritative payload, scrollable UI, compatibility, tests, and manual acceptance each map to a task.
- Type consistency: `CitizenSkill` and `CitizenSkills` are defined in Task 1, attached to `CitizenEntity` in Task 2, then consumed by the payload in Task 3 and UI in Task 4.
- Scope: no mining/building behavior, perks, jobs, or trading is introduced; only the shared foundation and Farming progression are included.
- Verification caveat: the current workspace may not resolve Fabric Loom offline, so the plan requires reporting that limitation rather than claiming a passing build without fresh output.
