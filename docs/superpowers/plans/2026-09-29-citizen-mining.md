# Citizen Mining Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a five-day citizen work cycle with one farming day and four mining days, mining at a public settlement cave, and household deposits of abstract mineral items.

**Architecture:** Reuse citizen navigation, AI goal priorities, swing/block-progress visuals, carried offhand stacks, and household home locations. Introduce a mod-defined work-site block in a mine added to settlement generation; generalize storage insertion while keeping food and market accounting wheat-only. Derive work-day selection from the overworld day count, so the schedule requires no new saved state.

**Tech Stack:** Java 25, Fabric, Minecraft 26.3, Gradle wrapper, existing citizen goals and saved data, jigsaw structure templates.

**Spec:** `docs/superpowers/specs/2026-09-29-citizen-mining-design.md`

## Global Constraints

- Keep mining abstract: no cave or work-face blocks are consumed or changed by citizen work.
- Use a five-day repeating cycle: one farming day and four mining days.
- Keep wheat-only household feeding and market behavior wheat-only.
- Preserve existing household wheat storage and citizen skill data compatibility.
- Newly generated settlements receive the public mine; existing settlements do not gain one automatically.
- Tests are outside the approved scope. Verify by compiling and inspecting the affected runtime/resource paths.

## Review Focus

- Existing storage slots saved as wheat still load; non-wheat items must not count as food or market surplus.
- A partially or fully full household inventory returns uninserted items to the citizen without deleting them.
- A missing, broken, or unloaded work site must not cause a citizen to lose carried resources or continuously path to an invalid location.
- The cycle boundary selects exactly one farming day in every five Minecraft days, including across world reloads.
- The generated mine marker is reachable and remains intact after a completed citizen work action.

---

### Task 1: Add a mod-defined public mine site to generated settlements

**Files:**
- Create: `src/main/java/com/economistwars/citizen/MineWorksiteBlock.java`
- Create: `src/main/java/com/economistwars/citizen/MineWorksiteBlockEntity.java`
- Create: `src/main/java/com/economistwars/citizen/MineSiteSavedData.java`
- Modify: `src/main/java/com/economistwars/EconomistWars.java`
- Create or modify: `src/main/resources/assets/economistwars/blockstates/mine_worksite.json`
- Create or modify: `src/main/resources/assets/economistwars/models/block/mine_worksite.json`
- Modify: `src/main/resources/assets/economistwars/lang/en_us.json`
- Modify: `src/main/resources/data/economistwars/worldgen/template_pool/settlement/start.json`
- Modify: `src/main/resources/data/economistwars/worldgen/structure/settlement.json` only if required by jigsaw size/connectivity
- Modify: `src/main/resources/data/economistwars/structure/settlement/start.nbt`
- Create: `src/main/resources/data/economistwars/structure/settlement/mine.nbt`

**Interfaces:**
- Produces: registered `MineWorksiteBlock.BLOCK`, a mod block with a block entity that records its position for efficient mining-site lookup.
- Produces: `MineSiteSavedData.register(ServerLevel level, BlockPos position)` and `MineSiteSavedData.positions(ServerLevel level)`, recording marker positions and returning those for the requested dimension; callers must skip unloaded or no-longer-valid marker positions.
- Produces: a generated, navigable mine site containing at least one work marker, discoverable without scanning a large volume of blocks each AI search.

- [ ] **Step 1: Register the work-site marker block**

Register `economistwars:mine_worksite` and its block entity following `FarmPlotBlock`'s registry-key pattern. Give it a visible mineral-vein appearance, no loot, and a blockstate/model resource. Its server ticker registers its position once with `MineSiteSavedData`; saved positions remain dimension-scoped and are filtered against loaded state before use. Initialize it in `EconomistWars.onInitialize()`.

- [ ] **Step 2: Add the mine to the settlement template graph**

Add one mine template-pool element to the settlement start pool and add a jigsaw connector in `start.nbt` that attaches that mine template exactly once. Put the marker at an exposed, reachable work face inside the cave. Keep the existing three home connectors and their household population flow intact. Increase the settlement jigsaw size only as needed for the additional mine piece.

- [ ] **Step 3: Inspect structure and registration resources**

Inspect the resulting NBT and JSON resources for valid identifiers, a connected mine piece, and at least one marker position. Confirm existing settlement pools still include their current start and home entries.

---

### Task 2: Generalize carried-item deposits and household storage

**Files:**
- Modify: `src/main/java/com/economistwars/household/HouseholdStorageBlockEntity.java`
- Modify: `src/main/java/com/economistwars/household/HouseholdFarmSavedData.java`
- Modify: `src/main/java/com/economistwars/citizen/CitizenEntity.java`
- Modify: `src/main/java/com/economistwars/citizen/CitizenStoreHarvestGoal.java`

**Interfaces:**
- Produces: `HouseholdStorageBlockEntity.insert(UUID householdId, ItemStack offered)` accepts any non-empty item stack and returns its uninserted remainder.
- Produces: `HouseholdFarmSavedData.deposit(ServerLevel level, UUID householdId, ItemStack offered)` deposits any valid item stack without including non-wheat items in `Farm.food`.
- Produces: citizen carried-item helpers that identify any non-empty offhand stack for storage routing.

- [ ] **Step 1: Make storage slots item-generic and retain old saves**

Rename the internal wheat-only slot list to reflect general items. Save/load generalized slot contents with Minecraft's ItemStack codec; when the new key is absent, read the old `Wheat0` through `Wheat8` key for that slot. Keep `wheatCount`, `takeWheat`, starter-food capacity and wheat filtering restricted to wheat. Allow `setItem` and `canPlaceItem` to handle general items.

- [ ] **Step 2: Deposit arbitrary citizen cargo**

Generalize the saved-data deposit parameter and insertion validation. Recompute `farm.food` from `storage.wheatCount(householdId)` only. In the citizen storage goal, route any non-empty offhand stack to the home and replace it with the returned remainder after a successful insertion. Keep its existing wheat-harvest behavior unchanged.

- [ ] **Step 3: Review storage compatibility and capacity paths**

Inspect loading of the legacy wheat slot keys, stacking of different item types, wheat count/take behavior when ore shares the inventory, and full-inventory remainder handling. Verify no storage initialization or feeding path treats minerals as food.

---

### Task 3: Add the five-day farm/mining schedule and mining work goal

**Files:**
- Create: `src/main/java/com/economistwars/citizen/CitizenMiningGoal.java`
- Create: `src/main/java/com/economistwars/citizen/CitizenWorkSchedule.java`
- Modify: `src/main/java/com/economistwars/citizen/CitizenFarmGoal.java`
- Modify: `src/main/java/com/economistwars/citizen/CitizenEntity.java`

**Interfaces:**
- Consumes: `MineWorksiteBlock.BLOCK`, generic citizen cargo support, `HouseholdFarmSavedData.home()` and `.deposit()`.
- Produces: `CitizenMiningGoal`, an AI goal that seeks a nearby loaded mine marker, performs an abstract timed mining action, carries mineral items, and gains Mining experience once per successful gathering action.
- Produces: `CitizenWorkSchedule.workFor(ServerLevel level)`, returning `FARM` when `Math.floorMod(level.getOverworldClockTime() / 24000, 5) == 0` and `MINE` on days 1–4.

- [ ] **Step 1: Gate farming to the farming day**

Implement `CitizenWorkSchedule.workFor(ServerLevel)` and gate `CitizenFarmGoal.canUse()` on `workFor(level) == FARM`. Preserve existing crop ownership, ripeness, carrying-capacity, and daylight conditions.

- [ ] **Step 2: Implement the citizen mining goal**

In `CitizenMiningGoal.canUse()`, require `workFor(level) == MINE`, valid household membership, and an empty offhand so new work never starts while a prior delivery is pending. Query `MineSiteSavedData.positions(level)`, skip unloaded or positions no longer containing `MineWorksiteBlock.BLOCK`, and choose the nearest valid marker within 96 blocks. Travel to it using the current sprint-near-destination pattern. At interaction range, perform a 40-tick action with a pickaxe swing every 10 ticks and increasing `destroyBlockProgress`; do not call `destroyBlock` or change the marker. On completion, carry one raw-iron item and add five Mining experience.

- [ ] **Step 3: Register goal priorities and cargo handoff**

Register the generalized storage goal ahead of work goals when cargo is present, register mining below panic/storage and alongside farming, and ensure `CitizenWorkSchedule` prevents farming and mining from running on the same day. Reuse the existing offhand stack; no new cargo persistence format is needed.

- [ ] **Step 4: Review schedule and unavailable-site behavior**

Inspect the day-zero, day-four, day-five, and reload paths. Confirm no target is retained after the site becomes unavailable, mining progress clears in `stop()`, and a citizen carrying minerals can still return home when mining is not scheduled.

---

### Task 4: Update project documentation and compile

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: completed work schedule, generated mine cave, abstract mineral storage behavior.
- Produces: project progress documentation that describes the mine, five-day work cycle, abstract resource gathering, and household storage deposits.

- [ ] **Step 1: Update README feature status**

Add the public mine and five-day schedule to implemented progress, explain that citizens collect abstract minerals and store them at home, and update future-work notes so they no longer list basic mining as unimplemented.

- [ ] **Step 2: Compile the mod**

Run `./gradlew build` (Windows: `gradlew.bat build`) with the configured JDK 25. Expected: successful Java and resource compilation with no missing block/model/structure identifiers.

- [ ] **Step 3: Review final changes**

Inspect the full diff for unrelated README edits, accidental wheat-accounting changes, modified cave blocks, or new data files that are not referenced by the settlement pools.
