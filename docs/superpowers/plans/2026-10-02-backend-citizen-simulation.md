# Backend Citizen Simulation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Native execution is recommended for this plan.

**Goal:** Simulate persistent citizens independently of loaded chunks and use Minecraft entities solely for presentation and interactions.

**Architecture:** World SavedData owns citizens, mine stock, market books and bed reservations. A single server tick service executes record-based actions and household upkeep; a separate presentation manager reconciles visuals in entity-ticking chunks. Migrate legacy data only when encountered and never overwrite an existing authoritative record.

**Tech Stack:** Java 25, Minecraft 26.3, Fabric API, existing SavedData/Codec patterns, JUnit 5, Gradle wrapper.

**Spec:** `docs/superpowers/specs/2026-10-02-backend-citizen-simulation-design.md`

## Global Constraints

- Simulation advances while that server ticks, including with no nearby players.
- It does not advance while the world is closed, or while the integrated server is paused.
- Retain the current 40-tick decision rescore interval and per-tick production/travel countdowns.
- Run all shared state changes on the server thread with a stable citizen ordering.
- Simulation reads registered farms, mines, markets, homes and beds without getChunkAt, block-entity lookups, structure searches or chunk tickets.
- Travel retains the distance-based 10–200 tick countdown.
- Preserve abstract farm production rather than reintroducing crop-maturity requirements.
- Maintain the current local changes as the baseline. Do not revert unrelated edits, add external infrastructure, or introduce real-world offline progression.

## Review Focus

- A legacy entity appears after its citizen moved or died: backend position/death wins; no resurrection or item duplication. Task 1 and Task 6.
- Destination chunk is loaded but not entity-ticking, or its anchor is obstructed: defer display without loading chunks or stopping work. Task 6.
- A removed mine/bed/market returns through a stale block snapshot: explicit invalidation wins until a genuine new placement registers it. Task 2.
- A day-clock jump or rollback occurs: no replayed household charges or repeated shortage damage. Task 5.
- Household storage is full or two miners finish together: retain undelivered items and never overspend mine stock. Tasks 3–4.

## File structure and ownership

New files live in `src/main/java/com/economistwars/citizen/`; corresponding tests live in `src/test/java/com/economistwars/citizen/` unless stated otherwise. File names below are exact within those roots.

- `CitizenState.java`, `CitizenSavedData.java`, `CitizenInventory.java`, `CitizenRandom.java`: authoritative citizen records, versioned persistence, inventory operations, replayable randomness.
- `CitizenAssetKey.java`, `MineSiteState.java`, `SettlementMarketState.java`, `HouseholdBedSavedData.java`: dimension-qualified sites, stock, offers/history and bed reservations.
- `CitizenSimulationContext.java`, `CitizenSimulationAction.java`, `CitizenActionEvaluation.java`: backend dependencies and action contracts.
- `CitizenProductionAction.java`, `CitizenFarmAction.java`, `CitizenMiningAction.java`, `CitizenBakeryAction.java`, `CitizenDeliveryAction.java`, `CitizenMarketAction.java`, `CitizenSleepAction.java`, `CitizenReturnHomeAction.java`: record-based execution.
- `CitizenSimulation.java`, `MinecraftCitizenSimulationContext.java`: tick orchestration and SavedData integration; the context never resolves physical chunks.
- `CitizenPresentationManager.java`, `CitizenProfileSnapshot.java`: visual reconciliation and profile construction.
- Existing entity, block, registry, command and spawn classes become adapters. Existing goal classes are removed at cutover after their behaviour has tests.

## Task 1: Persistent citizen model and migration

**Create:** `CitizenState.java`, `CitizenSavedData.java`, `CitizenInventory.java`, `CitizenRandom.java`.
**Modify:** `CitizenSkills.java` to expose a codec/snapshot for experience without requiring entity ValueInput.
**Tests:** `CitizenStateTest.java`, `CitizenSavedDataTest.java`, `CitizenInventoryTest.java`, `CitizenRandomTest.java`.

**Interfaces:**
- `CitizenSavedData.get(ServerLevel level) -> CitizenSavedData`; `find(UUID id) -> Optional<CitizenState>`; `registerIfAbsent(CitizenState initial) -> CitizenState`; `states() -> List<CitizenState>` in stable UUID order.
- `CitizenState.create(CitizenIdentity identity, UUID householdId, String dimension, Vec3 position, long day, long randomSeed) -> CitizenState`.
- `CitizenState.CODEC` stores schema version, identity, nullable household, dimension/position, health/alive, needs, skill experience, 36-slot inventory, inventory-work marker, delivery request, market memory, action state and timers, travel, last daily timestamps and random state.
- Action state uses `CitizenDecisionPlanner.Action` or idle, plus phase, target, reserved bed, work elapsed, pending mining drops and rescore countdown. Travel stores origin, destination, total and remaining ticks independently of visuals.
- `CitizenInventory.canInsert(List<ItemStack>) -> boolean`; `insert(List<ItemStack>) -> boolean` atomic; `bakeBread() -> boolean` atomic; `contents() -> List<ItemStack>` defensive copies.
- `CitizenRandom.nextInt(int bound) -> int` and persisted `state() -> long`; use a documented fixed PRNG algorithm and rejection sampling rather than java.util.Random internals.
- Backend death keeps the citizen record with `alive=false`; registration cannot replace that tombstone.

- [ ] Write tests: `roundTripKeepsActionTravelInventoryAndRandomState` asserts exact equality of fields and the next random sequence; `legacyRegistrationIsIdempotent` asserts the original record wins; `deadRecordCannotBeReplaced` asserts it stays dead; `bakingIsAtomicWhenOutputCannotFit` asserts inventory is unchanged on failure.
- [ ] Run `./gradlew.bat test --tests '*CitizenStateTest' --tests '*CitizenSavedDataTest' --tests '*CitizenInventoryTest' --tests '*CitizenRandomTest'` and confirm the new APIs cause failure before implementation.
- [ ] Implement codecs, safe malformed-action fallback to idle, and inventory/random operations. Do not attach entity writes or tick hooks yet.
- [ ] Repeat the focused command; require passing tests and correct registered ItemStack codec round trips using the existing MinecraftTestBootstrap.
- [ ] Review the scoped diff and record this task as complete; any commit must exclude pre-existing user changes.

## Task 2: Chunk-independent world assets

**Create:** `CitizenAssetKey.java`, `MineSiteState.java`, `SettlementMarketState.java`, `HouseholdBedSavedData.java`.
**Modify:** `MineSiteSavedData.java`, `SettlementMarketSavedData.java`, `MineWorksiteBlockEntity.java`, `MineWorksiteBlock.java`, `household/SettlementMarketBlockEntity.java`, `household/SettlementMarketBlock.java`, `household/HouseholdHomeBlockEntity.java`, `household/HouseholdSavedData.java`, `household/FarmPlotBlockEntity.java`.
**Tests:** `MineSiteStateTest.java`, `SettlementMarketStateTest.java`, `HouseholdBedSavedDataTest.java`, existing `SettlementMarketSavedDataTest.java`.

**Interfaces:**
- `CitizenAssetKey(String dimension, BlockPos position)` is the stable registry key.
- Mine registry: `find(CitizenAssetKey) -> Optional<MineSiteState>`; `registerIfAbsent(CitizenAssetKey, int stock, BoundingBox bounds) -> MineSiteState`; `invalidate(CitizenAssetKey) -> void`. State exposes `remaining() -> int` and `consume(int amount) -> boolean`.
- Market registry: `find(CitizenAssetKey) -> Optional<SettlementMarketState>`; `registerIfAbsent(CitizenAssetKey, SettlementMarketState legacy) -> SettlementMarketState`; `invalidate(CitizenAssetKey) -> void`. State contains existing offers, history and migration version; maximum history length remains 256.
- Bed registry: `register(UUID householdId, CitizenAssetKey bed) -> void`; `reserve(UUID householdId, UUID citizenId) -> Optional<CitizenAssetKey>`; `release(UUID citizenId) -> void`; `invalidate(CitizenAssetKey) -> void`. Store dimension-qualified home and arrival anchors with bed records.
- Block adapters register/import using already loaded world data. Removal callbacks invalidate records only for real block replacement/destruction, never for chunk unload. Explicit new placement may replace an invalidation tombstone; legacy import may not.

- [ ] Write tests: `legacyImportDoesNotRefillMine` asserts a mine depleted from 10000 to 9000 stays at 9000; `staleMarketSnapshotCannotReplaceOffers` preserves newer history; `bedReservationHasOneOwner` prevents two citizens reserving one bed; `invalidatedSiteRejectsLegacyRegistration` keeps the tombstone; save/load preserves each record.
- [ ] Run `./gradlew.bat test --tests '*MineSiteStateTest' --tests '*SettlementMarketStateTest' --tests '*HouseholdBedSavedDataTest' --tests '*SettlementMarketSavedDataTest'`; verify new assertions fail.
- [ ] Implement registry codecs and block adapters. Existing entity goals must consume registry-backed stock/book through block adapters until cutover; do not introduce a second stock authority. Register home beds even when a home is already populated.
- [ ] Run the focused tests and `./gradlew.bat compileJava compileClientJava`; require success.
- [ ] Review scoped changes and record completion.

## Task 3: Backend production, travel and delivery

**Create:** `CitizenSimulationContext.java`, `CitizenSimulationAction.java`, `CitizenActionEvaluation.java`, `CitizenProductionAction.java`, `CitizenFarmAction.java`, `CitizenMiningAction.java`, `CitizenBakeryAction.java`, `CitizenDeliveryAction.java`.
**Modify:** `CitizenProductionGoal.java` and `CitizenDecisionPlanner.java` only to extract shared evaluation without changing the existing numeric rules.
**Tests:** `CitizenProductionActionTest.java`, `CitizenTravelTest.java`, `CitizenDeliveryActionTest.java`; test fixture `SimulationTestContext.java`.

**Interfaces:**
- Context supplies `day() -> long`, `timeOfDay() -> long`, `household(UUID) -> Optional<Household>`, `farmPositions(UUID, String) -> List<BlockPos>`, `mines(String) -> List<MineSiteState>`, `market(CitizenAssetKey) -> Optional<SettlementMarketState>`, `beds() -> HouseholdBedSavedData`, `rollMiningDrops(CitizenRandom) -> List<ItemStack>`, `miningCost(ItemStack) -> int`, `changed() -> void`. Add no Level, chunk, entity or block-entity accessor.
- `CitizenActionEvaluation` holds eligibility, score, duration, destination and the existing profile decision details.
- Action contract: `kind() -> CitizenDecisionPlanner.Action`; `evaluate(CitizenState, CitizenSimulationContext, boolean current) -> CitizenActionEvaluation`; `start(CitizenState, CitizenSimulationContext, CitizenActionEvaluation) -> void`; `advance(CitizenState, CitizenSimulationContext) -> void`; `stop(CitizenState, CitizenSimulationContext) -> void`. Evaluation is side-effect-free.
- Travel is progressed once by the simulation, not separately by each action. Actions award output only after arrival and sufficient work ticks.

- [ ] Write tests: `farmProducesTwoWheatAfterTwelveTicks` asserts 2 wheat and 5 farming XP; `bakeConsumesThreeWheatAfter1200Ticks` asserts 1 bread and 5 bakery XP; `twoMinersCannotOverspendStock` asserts nonnegative stock and paid-only output; `travelCompletesWithoutEntity` asserts clamped countdown and destination; `deliveryToFullStorageKeepsRemainder` asserts item conservation.
- [ ] Run `./gradlew.bat test --tests '*CitizenProductionActionTest' --tests '*CitizenTravelTest' --tests '*CitizenDeliveryActionTest'` and confirm failure before implementing.
- [ ] Implement production scoring with 1.02^level speed and 1.05 schedule preference; farming requires owned registered land and daylight schedule, with no crop lookup. Store mining rolls before committing stock/output and retain them across save/reload. Use the configured loot distribution and resource cost rules; make loot randomness consume the persisted citizen random stream.
- [ ] Repeat focused tests plus `--tests '*CitizenDecisionPlannerTest'`; require passing numeric parity assertions.
- [ ] Review scoped changes and record completion.

## Task 4: Backend market, sleep and home actions

**Create:** `CitizenMarketAction.java`, `CitizenSleepAction.java`, `CitizenReturnHomeAction.java`.
**Modify:** `SettlementMarketState.java`, `household/SettlementMarketBlockEntity.java` to share the backend offer engine; `CitizenMarketMemory.java` only where record access requires it.
**Tests:** `CitizenMarketActionTest.java`, `CitizenSleepActionTest.java`.

**Interfaces:** Consume Task 3 action/context contracts. `SettlementMarketState.visit(UUID householdId, Map<String,CitizenNeed> needs, CitizenState citizen, CitizenSimulationContext context) -> boolean` publishes offers, records remembered trade and performs at most one atomic trade. `potentialTradeBenefit(UUID, Map<String,CitizenNeed>, CitizenSimulationContext) -> double` evaluates available offers against the existing producible-item set. No block lookup.

- [ ] Write tests: `marketTradesWithoutLoadedBlock` asserts one item moves each direction and one history record; `failedTradeRestoresBothInventories` asserts unchanged contents; `marketVisitOncePerDay` prevents a second visit; `sleepUsesBackendBedReservation` asserts sleep score 1800 during 13000–22999 and reservation release at 23000; `rememberedOfferChangesProductionValue` preserves existing remembered-product maths.
- [ ] Run `./gradlew.bat test --tests '*CitizenMarketActionTest' --tests '*CitizenSleepActionTest'`; verify failures.
- [ ] Implement market score 900 when maximum urgency >=80, delivery and night-home scores 900, and existing return-home safety score. Sleeping mutates only backend reservation/action state. Preserve one published offer per household and current barter selection rules.
- [ ] Run focused tests plus `--tests '*BarterTradeChoiceTest' --tests '*CitizenMarketMemoryTest'`; require success.
- [ ] Review scoped changes and record completion.

## Task 5: Global simulation and daily economy

**Create:** `CitizenSimulation.java`, `MinecraftCitizenSimulationContext.java`.
**Modify:** `CitizenWorkSchedule.java` to add pure clock overloads; `household/Household.java`, `ownership/OwnershipSavedData.java` to expose record-only daily land decisions; keep existing delegating entry points until cutover.
**Tests:** `CitizenSimulationTest.java`, `CitizenDailyEconomyTest.java`.

**Interfaces:** `CitizenSimulation.advance(Collection<CitizenState> citizens, CitizenSimulationContext context) -> void`; `CitizenSimulation.damage(UUID id, float amount, CitizenSimulationContext context) -> boolean` for validated damage; `MinecraftCitizenSimulationContext.create(MinecraftServer server) -> CitizenSimulationContext`. Backend death releases beds, resolves carried inventory once into household storage where possible, records unplaced remainder in the tombstone, removes membership, and releases land only when the household becomes empty.

- [ ] Write tests: `allCitizensAdvanceWithZeroVisuals` asserts production, travel and needs progress; `twoMembersConsumeOnlyOneDailyHouseholdPass` asserts one food item per member; `clockRollbackDoesNotRepeatCharges` asserts unchanged coins/food/damage; `forwardClockJumpUsesOneEconomyPassAndElapsedNeedGrowth` pins the approved jump rule; `rescoreRetainsCurrentBelowTenPercentImprovement` pins 40 ticks and 10% hysteresis; `deathCleanupRunsOnce` asserts no duplicated inventory transfer.
- [ ] Run `./gradlew.bat test --tests '*CitizenSimulationTest' --tests '*CitizenDailyEconomyTest'`; verify failures.
- [ ] Implement stable UUID ordering, housekeeping, urgent consumption cadence (retain the current cooldown semantics), shortage damage capped at 4 health/day, action selection and advancement. Mark SavedData dirty when persistent fields change. Implement record-based nearest-site and land queries without loading chunks. Do not register the live tick callback yet.
- [ ] Repeat focused tests and inspect the backend context/action dependency paths for chunk, structure-search or block-entity access; require none.
- [ ] Review scoped changes and record completion.

## Task 6: Cut over entities, spawning and interactions

**Create:** `CitizenPresentationManager.java`, `CitizenProfileSnapshot.java`.
**Modify:** `EconomistWars.java`, `CitizenEntity.java`, `CitizenTeleportPoints.java`, `command/CitizenCommands.java`, `household/CitizenHouseholdSpawner.java`, `household/HouseholdHomeBlockEntity.java`, `network/CitizenNetworking.java`, client citizen renderer/profile files only as needed for snapshots.
**Remove after cutover:** `CitizenDecisionGoal.java`, `CitizenDecisionAction.java`, `CitizenProductionGoal.java`, `CitizenFarmGoal.java`, `CitizenMiningGoal.java`, `CitizenBakeryGoal.java`, `CitizenStoreHarvestGoal.java`, `CitizenMarketGoal.java`, `CitizenSleepGoal.java`, `CitizenReturnHomeGoal.java` once no references remain.
**Tests:** `CitizenPresentationManagerTest.java`, `CitizenMigrationTest.java`, existing `network/CitizenProfilePayloadTest.java`.

**Interfaces:** `CitizenPresentationManager.reconcile(MinecraftServer server, CitizenSavedData data) -> void`; `CitizenEntity.bind(UUID citizenId) -> void`; `CitizenEntity.applySnapshot(CitizenState state) -> void`; `CitizenProfileSnapshot.create(CitizenState state, CitizenSimulationContext context) -> CitizenProfilePayload`. Separate lifecycle decisions from Minecraft APIs behind a small presentation access interface that tests can fake; exposes only already-ticking checks, bounded safe placement, existing visuals, spawn/remove and snapshot application.

- [ ] Write tests: `unloadRemovesOnlyVisual` preserves state/member; `oneVisualPerCitizen` removes duplicates; `nonTickingOrObstructedDestinationDefersVisual` performs no chunk request; `staleVisualCannotMoveCitizenBack` restores backend position; `legacyImportRunsOnce` preserves newer state; `deadLegacyVisualCannotResurrect` prevents respawn; `profileReflectsBackendProgress` pins payload values.
- [ ] Run `./gradlew.bat test --tests '*CitizenPresentationManagerTest' --tests '*CitizenMigrationTest' --tests '*CitizenProfilePayloadTest'`; verify failures.
- [ ] Implement presentation and minimal-ID entity persistence. Import old entity data before binding, using the existing legacy field names. Spawn commands and homes create backend records first; failure rollback removes only records created by that operation. Distinguish visual removal from backend death, replacing existing shouldDestroy-based membership removal.
- [ ] Route accepted player/environment damage exactly once after vanilla damage validation, then mirror backend health/death. Guard snapshot application against damage callbacks. Forward profile/inventory interactions to backend state, never entity-owned inventories.
- [ ] Register a single END_SERVER_TICK callback: advance backend first, reconcile presentation second. Remove entity upkeep and decision goals in this same cutover. Resolve the installed Minecraft entity-ticking checks from local mapped sources; checking loaded chunks alone is insufficient.
- [ ] Run focused tests and `./gradlew.bat compileJava compileClientJava`; require success. Search callers to ensure no old simulation goals or duplicate tick paths remain.
- [ ] Review scoped changes and record completion.

## Task 7: End-to-end parity, documentation and final verification

**Create tests:** `CitizenSimulationParityTest.java`, `CitizenSimulationPersistenceTest.java`.
**Modify:** `README.md`, `docs/troubleshooting.md`; repair existing tests only where the intended API changed, preserving their behavioural assertions.

- [ ] Write parity tests from identical records/random seeds: run a full day with fake visuals always present, never present, and repeatedly reconciled; assert equal citizen/household/site/market snapshots. Write save-resume tests splitting execution during mining work, travel and barter preparation; assert equal final state to uninterrupted execution and no duplicated rewards.
- [ ] Run `./gradlew.bat test --tests '*CitizenSimulationParityTest' --tests '*CitizenSimulationPersistenceTest'`; verify the tests distinguish deliberately double-ticked or reset-progress fixtures before accepting them.
- [ ] Update documentation to describe backend ownership, registered-site discovery, closed-world pause and lazy migration limits. Remove obsolete README claims about sprinting, wheat-only feeding and initial wheat supplies.
- [ ] Run `./gradlew.bat test` followed by `./gradlew.bat build`; require exit code 0 and successful test/build reports. Diagnose any baseline failures separately rather than deleting tests to make the build pass.
- [ ] If interactive Minecraft is available, run a smoke world: register a farm and mine, leave their entity-ticking range for one day, return and confirm backend output, depleted stock, current profile, and no duplicate citizens; restart during travel and confirm continuation. Clearly report if this manual check cannot be run.
- [ ] Review the final diff against the approved spec, confirm original unrelated changes remain, and report verification and migration limitations. Scope any eventual commit to this feature without staging unrelated user edits.

## Execution handoff

Recommend native execution in the current session: the tasks share citizen, action and registry contracts and benefit from one implementer carrying those interfaces through the cutover. The user's existing uncommitted changes are the implementation baseline; do not start from a clean remote checkout that omits them. Use executing-plans after plan review, with focused checks at each boundary. Independent review may be requested as a final subtask when authorized by the chosen execution workflow.

Plan self-review completed: spec requirements map to Tasks 1–7; clock policy, migration, scarcity, presentation independence, damage and death handling each have a test owner. No implementation changes have been made.
