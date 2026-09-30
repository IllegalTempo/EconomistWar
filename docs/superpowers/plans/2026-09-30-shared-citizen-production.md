# Shared Citizen Production Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Share production scoring and duration logic between farming and mining while treating farm output capacity as unbounded and mining stock as finite.

**Architecture:** Add an abstract `CitizenProductionGoal` that evaluates output outcomes against current needs and remembered market memory, subtracts consumed-input utility, and computes skill/travel/schedule scores and details. `CitizenFarmGoal` and `CitizenMiningGoal` provide job-specific eligibility, site data, and work execution; their existing crop and mine mutations remain concrete.

**Tech Stack:** Java, Minecraft/Fabric entity goals, existing JUnit tests.

**Spec:** `docs/superpowers/specs/2026-09-30-shared-citizen-production-design.md`

## Global Constraints

- Need utility uses the citizen's current eat, entertainment, and safety needs.
- Remembered barter substitutes the current need-utility gain for at most one matching output unit; remaining output uses direct utility.
- Expected utility includes only affordable outcomes and weights each by its probability.
- Skill speed is `1.02 ^ level` with a one-tick minimum work duration.
- Farm resource capacity is unbounded, but a ripe household crop and inventory capacity remain required.
- Mining retains finite site stock and excludes an outcome when its resource cost exceeds remaining stock.
- Do not change saved citizen fields or add save migration.

## Review Focus

- A need change must alter both actions' output utility without changing outcome probabilities.
- A remembered market offer must alter only the matching product's value and only for one unit.
- A farm must not run out of an artificial resource counter, while still requiring a ripe owned crop.
- A mine must exclude only outcomes that exceed remaining stock, preserving probability weighting.
- Skill, travel, schedule preference, and action duration must produce the same values in both the planner score and decision details.

---

### Task 1: Define Shared Production Evaluation

**Files:**
- Create: `src/main/java/com/economistwars/citizen/CitizenProductionGoal.java`
- Modify: `src/main/java/com/economistwars/citizen/CitizenDecisionPlanner.java`
- Test: `src/test/java/com/economistwars/citizen/CitizenProductionGoalTest.java`

**Interfaces:**
- Produces: abstract `CitizenProductionGoal extends Goal implements CitizenDecisionAction`.
- Produces: protected hooks `List<CitizenDecisionPlanner.OutputOutcome> productionOutcomes()`, `List<InputCost> consumedInputs()`, `CitizenSkill productionSkill()`, `int baseWorkTicks()`, `int scoreTravelTicks()`, `int durationTravelTicks()`, and `boolean scheduledWork()`.
- Produces: `static Evaluation evaluate(CitizenNeeds needs, CitizenMarketMemory memory, List<CitizenDecisionPlanner.OutputOutcome> outcomes, List<InputCost> inputs, int skillLevel, int baseWorkTicks, int scoreTravelTicks, boolean scheduledWork)`.
- Produces: `record Evaluation(double outputUtility, double inputUtility, double netUtility, int skillLevel, double skillSpeed, int workTicks, int scoreTravelTicks, double scheduleMultiplier, double score, List<CitizenProfilePayload.DecisionDetail> details)`; the abstract action uses this same result for `decisionScore()` and `decisionDetails()`.
- Produces: `record InputCost(Item item, int quantity)` and `int estimatedDurationTicks()` equal to `max(1, durationTravelTicks() + effectiveWorkTicks(baseWorkTicks(), productionSkill().level()))`.
- `durationTravelTicks()` may include discovery wait; `scoreTravelTicks()` includes only the travel term used by the established score formula.
- Consumes: existing `CitizenDecisionPlanner.expectedOutputUtility`, `CitizenDecisionPlanner.score`, and `effectiveWorkTicks` helpers.

- [ ] **Step 1: Add `CitizenProductionGoalTest` cases for shared output evaluation**

  Add named tests `outputUtilityUsesCurrentNeeds`, `rememberedTradeAppliesToOnlyOneMatchingUnit`, `unaffordableOutcomeContributesZero`, `consumedInputUtilityIsSubtracted`, and `scoreUsesSkillTravelAndScheduleInputs`. Assert the exact expected utility and score fields returned by `evaluate`.

- [ ] **Step 2: Run the focused tests to verify they fail**

  Run: `.\gradlew.bat test --tests com.economistwars.citizen.CitizenProductionGoalTest`
  Expected: FAIL because the shared production evaluation is not implemented.

- [ ] **Step 3: Implement the abstract evaluation and score details**

  Keep outcome evaluation in the shared class. Represent farm capacity by marking its outcomes affordable without a numeric stock value; keep affordability decisions for finite sites in the concrete goal. Use one evaluation result to produce the score and matching profile detail rows.

- [ ] **Step 4: Run the focused test to verify it passes**

  Run: `.\gradlew.bat test --tests com.economistwars.citizen.CitizenProductionGoalTest`
  Expected: PASS.

- [ ] **Step 5: Review the shared evaluation diff**

### Task 2: Move Farming onto the Shared Action

**Files:**
- Modify: `src/main/java/com/economistwars/citizen/CitizenFarmGoal.java`
- Test: `src/test/java/com/economistwars/citizen/CitizenFarmGoalTest.java`

**Interfaces:**
- Consumes: `CitizenProductionGoal` hooks from Task 1.
- Produces: farm outcomes with two wheat at probability `1.0`, unbounded resource capacity, farming skill, 12 base work ticks, farm travel, and the existing `1.05` schedule multiplier when farming is preferred.

- [ ] **Step 1: Add `CitizenFarmGoalTest` cases for farm production inputs**

  Add named tests `farmOutputHasNoFiniteStockGate`, `farmStillRequiresRipeOwnedCrop`, and `farmScoreUsesNeedTradeSkillTravelAndScheduleInputs`. Assert that the farm outcome is always marked affordable by resource capacity, while eligibility still requires the existing crop and carry-capacity checks.

- [ ] **Step 2: Run the focused test to verify it fails**

  Run: `.\gradlew.bat test --tests com.economistwars.citizen.CitizenFarmGoalTest`
  Expected: FAIL because the farm goal still owns separate score logic.

- [ ] **Step 3: Extend `CitizenProductionGoal` in `CitizenFarmGoal`**

  Preserve existing ripe-crop ownership, carry-capacity, daylight, and safe-arrival checks. Keep crop replacement, inventory updates, and farming XP in `CitizenFarmGoal`.

- [ ] **Step 4: Run the focused test to verify it passes**

  Run: `.\gradlew.bat test --tests com.economistwars.citizen.CitizenFarmGoalTest`
  Expected: PASS.

- [ ] **Step 5: Review the farm migration diff**

### Task 3: Move Mining onto the Shared Action

**Files:**
- Modify: `src/main/java/com/economistwars/citizen/CitizenMiningGoal.java`
- Test: `src/test/java/com/economistwars/citizen/CitizenMiningGoalTest.java`

**Interfaces:**
- Consumes: `CitizenProductionGoal` hooks from Task 1.
- Produces: mining outcomes for coal/raw iron/raw gold at probabilities `0.7`, `0.2`, and `0.1`, each gated by its configured resource cost and current finite worksite stock.

- [ ] **Step 1: Add `CitizenMiningGoalTest` cases for finite mining outcomes**

  Add named tests `miningOutcomesUseTheirDropProbabilities`, `miningUtilityChangesWithNeedsAndRememberedTrade`, and `siteStockExcludesUnaffordableDropOutcomes`. Assert the 0.7/0.2/0.1 weighted result and each boundary where stock equals or falls below a drop cost.

- [ ] **Step 2: Run the focused test to verify it fails**

  Run: `.\gradlew.bat test --tests com.economistwars.citizen.CitizenMiningGoalTest`
  Expected: FAIL because mining still owns separate score logic.

- [ ] **Step 3: Extend `CitizenProductionGoal` in `CitizenMiningGoal`**

  Preserve site discovery, finite stock consumption, loot generation, inventory delivery, mining XP, and discovery wait duration. Keep discovery wait out of the score travel term if the established planner formula does so.

- [ ] **Step 4: Run the focused test to verify it passes**

  Run: `.\gradlew.bat test --tests com.economistwars.citizen.CitizenMiningGoalTest`
  Expected: PASS.

- [ ] **Step 5: Review the mining migration diff**

### Task 4: Verify Planner and Profile Integration

**Files:**
- Modify: `src/main/java/com/economistwars/citizen/CitizenDecisionGoal.java`
- Modify: `src/main/java/com/economistwars/citizen/CitizenEntity.java` only if candidate evaluation snapshot plumbing is needed
- Modify: `src/main/java/com/economistwars/network/CitizenProfilePayload.java` only if the shared evaluation needs additional fields
- Test: existing planner tests and the new production tests

**Interfaces:**
- Consumes: shared evaluation details from Tasks 1–3.
- Produces: the same shared score and detail values in planner candidates and the Decisions tab.

- [ ] **Step 1: Add an integration assertion for matching planner and detail scores**

  Add `productionDecisionDetailsUseTheCandidateEvaluation` and assert each production candidate's final planner score and formula detail equal the shared evaluation result for the same inputs.

- [ ] **Step 2: Run focused decision tests after adding the integration assertion**

  Run: `.\gradlew.bat test --tests com.economistwars.citizen.CitizenDecisionPlannerTest --tests com.economistwars.citizen.CitizenProductionGoalTest --tests com.economistwars.citizen.CitizenFarmGoalTest --tests com.economistwars.citizen.CitizenMiningGoalTest`
  Expected: PASS after the shared evaluations are integrated.

- [ ] **Step 3: Remove duplicated farm and mining score/detail calculations**

  Ensure the planner uses each production goal's shared evaluation once per candidate snapshot and the Decisions tab receives that evaluation's details.

- [ ] **Step 4: Run focused decision tests**

  Run: `.\gradlew.bat test --tests com.economistwars.citizen.CitizenDecisionPlannerTest --tests com.economistwars.citizen.CitizenProductionGoalTest --tests com.economistwars.citizen.CitizenFarmGoalTest --tests com.economistwars.citizen.CitizenMiningGoalTest`
  Expected: PASS.

- [ ] **Step 5: Review the planner integration diff**
