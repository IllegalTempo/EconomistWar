# Economist Wars Settlement Households Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate Economist Wars settlements made of household homes, each populated by one husband and one wife.

**Architecture:** Define a new jigsaw settlement and its pools/templates as data-pack resources. Each household home template contains an invisible server-ticked home anchor block entity; it calls a shared server-side couple spawner, while persistent world data keyed by dimension and anchor position prevents a completed home from spawning again. The spawner creates one household with one male and one female citizen and rolls back the household and both entities if insertion fails.

**Tech Stack:** Minecraft Java Edition 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Java 25, Gradle 9.7, data-driven jigsaw structures, Minecraft saved data.

**Spec:** `docs/superpowers/specs/2026-09-28-settlement-households-design.md`

## Global Constraints

- Target Minecraft Java Edition 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, and Java 25.
- Every generated household home contains exactly two persistent citizens, one male and one female, in one household record.
- New structures generate only in newly generated terrain and do not modify vanilla villages.
- Persist populated home locations so completed homes do not duplicate households on chunk or world reload.
- Keep population server-side and client classes isolated.
- Preserve the `/ew household <count>` command.
- Do not add work, needs, production, trade, or household controls.

## Review Focus

- A home anchor loads after chunk generation and creates exactly one couple on the logical server; exercise with a newly generated settlement.
- Chunk and world reload preserve the home key; exercise the same populated home after reload and confirm no additional citizens appear.
- Either citizen spawn can fail; inspect rollback path to ensure neither household nor citizen membership remains.
- The couple has opposite `CitizenSex` values and both profiles report the same household ID and size two; inspect both profiles in-game.
- Dedicated-server initialization must not load client classes; keep the anchor, spawner, and structure population entirely in common/server source sets.

---

### Task 1: Add an idempotent household home marker

**Files:**
- Create: `src/main/java/com/economistwars/household/HouseholdHomeBlock.java`
- Create: `src/main/java/com/economistwars/household/HouseholdHomeBlockEntity.java`
- Create: `src/main/java/com/economistwars/household/HouseholdHomeSavedData.java`
- Modify: `src/main/java/com/economistwars/EconomistWars.java`
- Create: `src/main/resources/assets/economistwars/blockstates/household_home.json`
- Create: `src/main/resources/assets/economistwars/models/block/household_home.json`

**Interfaces:**
- `HouseholdHomeSavedData.isPopulated(ResourceKey<Level> dimension, BlockPos anchorPosition)` returns whether that home has completed population.
- `HouseholdHomeSavedData.markPopulated(ResourceKey<Level> dimension, BlockPos anchorPosition)` records a completed home and marks the data dirty.
- `HouseholdHomeBlockEntity` ticks on the server and delegates an unpopulated home to `CitizenHouseholdSpawner` (Task 2). Client ticks do nothing.

- [x] **Step 1: Register the home anchor block and block entity** with common-side registry identifiers `economistwars:household_home`; make the block invisible, non-colliding, and non-item-bearing so it can be embedded inside a house template.
- [x] **Step 2: Persist populated home keys** in `HouseholdHomeSavedData`, encoding each key as dimension identifier plus packed `BlockPos`; expose the methods in the Interfaces section and store the saved data in overworld `DataStorage`, matching `HouseholdSavedData`.
- [x] **Step 3: Add a server-only block entity ticker** that checks the persistent key and calls `CitizenHouseholdSpawner.spawnCouple(level, anchorPosition)` only when it is absent; mark the key populated only after the spawner succeeds, and retry a failed home no more than once every 200 server ticks.
- [x] **Step 4: Add a blockstate/model mapping** for the registered anchor (the render shape stays invisible) and initialize block/entity registration from `EconomistWars.onInitialize()`.

### Task 2: Spawn and roll back one husband-and-wife household

**Files:**
- Create: `src/main/java/com/economistwars/household/CitizenHouseholdSpawner.java`
- Modify: `src/main/java/com/economistwars/citizen/CitizenIdentity.java`

**Interfaces:**
- `CitizenIdentity.create(CitizenSex sex)` returns an identity with the requested sex and a matching bundled skin.
- `CitizenHouseholdSpawner.spawnCouple(ServerLevel level, BlockPos homePosition)` returns `true` only after the household and both citizens are registered and both entities have been added successfully.

- [x] **Step 1: Add sex-specific identity creation** so a caller can select `MALE` or `FEMALE` while preserving the existing random `create()` behavior used elsewhere.
- [x] **Step 2: Implement the couple spawner** to create a household, create one male and one female citizen with unique identities, assign their household ID, add both member IDs, and place them inside the home with enough separation to avoid entity overlap.
- [x] **Step 3: Make the spawn operation all-or-nothing**: if entity creation, membership registration, or either `ServerLevel.addFreshEntity` call fails, discard any created entities, remove their citizen membership, remove the household, and return `false`.
- [x] **Step 4: Log a failed home population with its dimension and position** without sending internal spawn errors to players.

### Task 3: Add the generated settlement and household home templates

**Files:**
- Create: `src/main/resources/data/economistwars/worldgen/structure/settlement.json`
- Create: `src/main/resources/data/economistwars/worldgen/structure_set/settlement.json`
- Create: `src/main/resources/data/economistwars/worldgen/template_pool/settlement/start.json`
- Create: `src/main/resources/data/economistwars/worldgen/template_pool/settlement/homes.json`
- Create: `src/main/resources/data/economistwars/tags/worldgen/biome/has_structure/settlement.json`
- Create: `src/main/resources/data/economistwars/structure/settlement/start.nbt`
- Create: `src/main/resources/data/economistwars/structure/settlement/home.nbt`
- Create: `tools/generate_settlement_templates.py`

**Interfaces:**
- The start template provides three household-home connectors; the home template provides the matching connector and one `economistwars:household_home` anchor block.
- The structure references the `economistwars:has_structure/settlement` biome tag and the `economistwars:settlement` placement set; template pools reference the stable template IDs `economistwars:settlement/start` and `economistwars:settlement/home`.

- [x] **Step 1: Generate the start and home templates** with `tools/generate_settlement_templates.py`; include a clearing layer, a central green, three home connectors in the start, one matching connector per home, and exactly one home anchor per home template.
- [x] **Step 2: Define jigsaw template pools and structure data** so the start connects to three homes and each generated home piece receives one anchor; use a terminating fallback so the settlement cannot expand indefinitely. The structure depth is 2, matching the minimum used by vanilla jigsaw structures.
- [x] **Step 3: Define structure placement** with a conservative spacing/separation and a biome tag for temperate, grassy Overworld biomes; do not add the structure to vanilla village pools.
- [ ] **Step 4: Confirm every embedded home anchor creates a block entity** and that jigsaw `final_state` blocks are not mistaken for household homes; only the anchor block in a home template counts. Template contents are present, but runtime block entity creation still needs an in-game check.

### Task 4: Document and manually accept the playable slice

**Files:**
- Modify: `README.md`

- [x] **Step 1: Update the implemented/not implemented sections** to describe the generated Economist Wars settlement, one husband-and-wife pair per home, and the new-chunks-only limitation; retain the command as a manual tool.
- [x] **Step 2: Build the mod** with the project JDK 25 and Gradle 9.7 wrapper; require `BUILD SUCCESSFUL`.
- [ ] **Step 3: In a new development world, locate/generate a settlement** and confirm it contains multiple homes, each with two citizens sharing a household ID.
- [ ] **Step 4: Reload the populated chunks and world** and confirm the original homes remain populated once, citizen identity/skin/household data persists, and profiles report size two.
- [ ] **Step 5: Exercise the existing `/ew household <count>` command** to confirm manual household creation still works as before.

## Self-review

- **Spec coverage:** New structure and suitable biome placement are covered by Task 3; multiple homes by the three home connectors in its start template; two opposite-sex citizens and atomic rollback by Task 2; home-location persistence and server-only initialization by Task 1; documentation and reload acceptance by Task 4.
- **Step specificity:** Every step names a file or exact behavior. Resource folders use the singular `structure`, `structure_set`, and `template_pool` paths confirmed in the project's cached Minecraft 26.3 data.
- **Type consistency:** `CitizenIdentity.create(CitizenSex)` feeds `CitizenHouseholdSpawner.spawnCouple(ServerLevel, BlockPos)`, called by the home block entity; persistent keys use dimension plus packed position.
- **Review focus:** First-generation placement, reload duplication, partial spawn rollback, couple identity, and dedicated-server boundaries each have an explicit review or manual acceptance step.
- **Proportion:** Four tasks divide persistence/anchor behavior, household creation, structure assets, and documentation/acceptance.
