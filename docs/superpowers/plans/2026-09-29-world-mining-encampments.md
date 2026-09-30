# World Mining Encampments Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate mining encampments independently in the overworld, give each one a visible persistent 10,000-point resource pool, and let citizen miners discover and travel to active sites.

**Architecture:** Move the existing mine template into its own jigsaw structure and structure set. Persist each site's stock in its worksite block entity, load item costs from datapack JSON, and render its stock above the block. Update the mining goal to find generated sites beyond its current 96-block radius and travel by successive local waypoints while checking stock and carrying capacity before awarding drops.

**Tech Stack:** Java 25, Minecraft 26.3, Fabric API, vanilla datapack world-generation and loot JSON, existing `SavedData` and block entity patterns.

**Spec:** `docs/superpowers/specs/2026-09-29-world-mining-encampments-design.md`

## Global Constraints

- New encampments generate in configured overworld surface biomes.
- New settlements omit the embedded mine; existing settlements are not retroactively rewritten.
- Each site starts with 10,000 resource points and never drops below zero.
- Initial per-item costs are coal = 1, iron = 2, gold = 100; unconfigured items cost 1.
- Each item in a rolled stack is charged separately; only the affordable count is yielded.
- Resource is consumed only when the output fits in the citizen inventory and is awarded.
- Depleted or inaccessible sites are skipped; missing stock data defaults to 10,000.
- Do not add or run automated tests unless the user asks. Compile server and client source after implementation.

## Review Focus

- A stack whose full quantity costs more than the remaining stock yields only the affordable count and leaves the correct remainder.
- New loot items without a cost entry use the default cost of 1.
- A citizen inventory that cannot accept the payable drops does not consume site resource.
- Old site block entities load with full stock, while already-generated settlement mine sites remain usable.
- A distant site that is unloaded or cannot be reached does not leave a citizen navigating indefinitely or prevent trying another site.

---

### Task 1: Move the encampment into independent world generation

**Files:**
- Modify: `src/main/resources/data/economistwars/worldgen/template_pool/settlement/mine.json`
- Modify: `src/main/resources/data/economistwars/structure/settlement/start.nbt` (regenerate from the template script)
- Modify: `tools/generate_settlement_templates.py`
- Modify: `README.md`
- Create: `src/main/resources/data/economistwars/worldgen/template_pool/mining_encampment/start.json`
- Create: `src/main/resources/data/economistwars/worldgen/structure/mining_encampment.json`
- Create: `src/main/resources/data/economistwars/worldgen/structure_set/mining_encampment.json`
- Create: `src/main/resources/data/economistwars/tags/worldgen/biome/has_structure/mining_encampment.json`
- Create: `src/main/resources/data/economistwars/tags/worldgen/structure/mining_encampment.json`
- Move: `src/main/resources/data/economistwars/structure/settlement/mine.nbt` to `src/main/resources/data/economistwars/structure/mining_encampment/start.nbt`

**Interfaces:**
- Produces: structure key `economistwars:mining_encampment`, biome tag `economistwars:has_structure/mining_encampment`, and a template pool whose start element is the existing mine template.

- [ ] **Step 1: Define the independent structure data**

Add a jigsaw structure and random-spread structure set for the mining encampment. Use surface height projection, biome tag `#minecraft:is_overworld`, placement step `surface_structures`, spacing 24 chunks, separation 8 chunks, and a unique salt. Point the new start pool at `economistwars:mining_encampment/start`, which points to the moved `structure/mining_encampment/start.nbt` template.

- [ ] **Step 2: Remove the mine from settlement starts**

Make the settlement's `mine` pool empty while preserving its file and key if the settlement start template references it. New settlements must no longer place the mine.

- [ ] **Step 3: Validate the world-generation JSON**

Parse each changed/new JSON file and compare the structure, structure set, tag and pool shapes with the existing settlement definitions.

---

### Task 2: Add configurable resource costs and stock accounting

**Files:**
- Create: `src/main/java/com/economistwars/citizen/MiningResourceValues.java`
- Create: `src/main/resources/data/economistwars/mining_resource_values.json`
- Modify: `src/main/java/com/economistwars/citizen/MineWorksiteBlockEntity.java`
- Modify: `src/main/java/com/economistwars/citizen/MineWorksiteBlock.java`

**Interfaces:**
- `MiningResourceValues.cost(ItemStack item) -> int`: positive cost per item; unknown items use 1.
- `MiningResourceValues.affordableDrops(List<ItemStack> drops, int available) -> MiningYield`: returns item stacks clipped to what `available` can pay, plus total resource cost.
- `MineWorksiteBlockEntity.resourceRemaining() -> int` and `resourceCapacity() -> int` expose site state.
- `MineWorksiteBlockEntity.consume(int amount) -> boolean` subtracts only a positive amount that is no greater than current stock.
- Resource state is saved with the block entity; missing saved state defaults to 10,000.

- [ ] **Step 1: Add the datapack cost resource**

Store JSON shaped as `{ "default": 1, "items": { "minecraft:coal": 1, "minecraft:raw_iron": 2, "minecraft:raw_gold": 100 } }` at `data/economistwars/mining_resource_values.json`. Load it through a server data reload listener and replace invalid or non-positive entries with the default.

- [ ] **Step 2: Implement affordable drop calculation**

Walk loot stacks and item counts in rolled order. For each stack, grant at most `available / itemCost` items; decrement the local budget and return the actual cost spent. Do not mutate the original loot stacks.

- [ ] **Step 3: Persist per-encampment stock**

Add capacity 10,000 and saved remaining stock to `MineWorksiteBlockEntity`. Keep `consume` bounded and mark the block entity changed only when stock changes.

- [ ] **Step 4: Register the resource reload listener**

Register the datapack reload listener during mod initialization. Reloading data must update costs for subsequent mining actions without resetting site stock.

---

### Task 3: Render the live resource counter above each site

**Files:**
- Create: `src/client/java/com/economistwars/citizen/MineWorksiteBlockEntityRenderer.java`
- Modify: `src/client/java/com/economistwars/EconomistWarsClient.java`
- Modify: `src/main/java/com/economistwars/citizen/MineWorksiteBlockEntity.java`

**Interfaces:**
- The renderer reads the block entity's remaining and capacity values and displays `remaining/10000` above the site.
- Client rendering registration remains in `EconomistWarsClient`.

- [ ] **Step 1: Expose stock state to the client**

Synchronize the remaining count from server to client whenever the block entity stock changes, using the project's Minecraft 26.3 block entity update pattern.

- [ ] **Step 2: Register and implement the renderer**

Render a compact world-space text label above the worksite. Respect normal render distance and occlusion; do not spawn a separate persistent text entity.

---

### Task 4: Discover distant sites and travel to them

**Files:**
- Modify: `src/main/java/com/economistwars/citizen/MineSiteSavedData.java`
- Modify: `src/main/java/com/economistwars/citizen/MineWorksiteBlockEntity.java`
- Modify: `src/main/java/com/economistwars/citizen/CitizenMiningGoal.java`

**Interfaces:**
- `MineSiteSavedData.register(ServerLevel level, BlockPos position)` remains idempotent and tracks world-generated worksite positions.
- Site lookup returns loaded, valid sites with remaining resources and a safe adjacent standing position.
- `CitizenMiningGoal` stores a selected site and periodically recalculates a local waypoint until it reaches the site.

- [ ] **Step 1: Keep the registry correct and compatible**

Retain old saved positions, register new structure sites when their worksite block entities load, and prune loaded positions whose block is no longer a worksite. Do not force-load chunks while pruning.

- [ ] **Step 2: Expand site discovery beyond 96 blocks**

Use the world's generated structure starts/structure locator to find encampments beyond the local registry. Resolve candidates to actual worksite positions and merge them with registered sites without duplicate targets.

- [ ] **Step 3: Select a usable candidate**

Choose the nearest non-depleted site with a clear standing position. Skip unloaded, stale, depleted, or unavailable candidates and retry search on the existing cooldown.

- [ ] **Step 4: Add waypoint travel**

For a distant candidate, navigate to a reachable intermediate point toward the site, then recalculate as the citizen and loaded area advance. Stop after repeated failures for a site and try another candidate.

- [ ] **Step 5: Recheck the target throughout mining**

Before work and at completion, ensure the site still exists, the citizen is on a mine-work day, inventory has room for the payable drops, and stock remains available.

---

### Task 5: Integrate resource consumption with mining output

**Files:**
- Modify: `src/main/java/com/economistwars/citizen/CitizenMiningGoal.java`
- Modify: `src/main/resources/data/economistwars/loot_table/citizen_mining.json`

**Interfaces:**
- Mining uses `MiningResourceValues.affordableDrops` and the selected `MineWorksiteBlockEntity` stock API.
- Existing `citizen_mining` loot table remains the source of item types/counts.

- [ ] **Step 1: Make payable loot the pending work result**

After rolling loot, compute its payable output against current stock. Permit partial stacks when stock cannot afford the entire original drop.

- [ ] **Step 2: Make output and resource debit atomic within the server tick**

Verify inventory capacity before mutation. Add payable output, then debit exactly its computed cost; if the site changed or debit fails, award no items and retry site selection. Ensure zero-payable results do not consume stock.

- [ ] **Step 3: Keep mining loot configurable**

Keep item/count outcomes in the loot table and ensure the initial table includes coal, iron, and gold entries for the configured resource values.

---

### Task 6: Compile and inspect the completed integration

**Files:**
- Review all files changed by Tasks 1–5.

**Interfaces:**
- No new interfaces; this is the final integration check.

- [ ] **Step 1: Validate datapack JSON**

Parse all changed and new JSON files, including the loot table, biome tag, structure, structure set, template pool, and resource cost map.

- [ ] **Step 2: Compile server and client sources**

Run `gradle compileJava compileClientJava` using Java 25. Expected result: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Review the final diff against acceptance criteria**

Confirm every acceptance criterion in the spec maps to a changed component, and report any behavior that requires an in-game check unavailable from compilation.
