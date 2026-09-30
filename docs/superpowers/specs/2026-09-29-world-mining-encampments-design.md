# World Mining Encampments Design

## Purpose

Move mining encampments out of settlement structures and distribute them through the world. Give each encampment a persistent, finite mineral resource pool, expose its remaining resource to players, and let citizen miners find and travel to an encampment with resources remaining.

## Current behavior

- A mine worksite is included in the settlement jigsaw pool and therefore generates as part of settlements.
- `MineSiteSavedData` records worksite positions when their block entities tick.
- `CitizenMiningGoal` selects a recorded worksite within 96 blocks and awards drops from the `citizen_mining` loot table.
- Worksites currently have no resource state or visible stock display.

## Design

### World generation

- Generate the existing mine encampment template as an independent overworld jigsaw structure with its own structure set, biome tag, spacing, and separation.
- Remove the mine element from the settlement start pool so new settlements no longer contain an encampment.
- Keep settlement generation and the household mine-work schedule otherwise unchanged.
- Limit encampment generation to surface overworld biomes. The structure placement rules remain data-pack configurable.

### Resource stock

- Every encampment starts with 10,000 resource points and persists its current amount in its worksite block entity.
- Resource costs are data-driven per item identifier. Initial values are coal = 1, iron = 2, and gold = 100. Unconfigured mining drops use a default cost of 1 so the resource pool cannot be bypassed by adding a new loot item without a cost entry.
- For every item in a rolled drop stack, charge its configured cost. If the remaining pool can pay for only part of the stack, grant only the affordable item count and preserve any leftover resource points. Do not award an item that the pool cannot fully pay for.
- Consume resource only when a mining action completes and the corresponding drops are added to the citizen inventory. A full citizen inventory must not consume site resources.
- At zero resource points, the encampment remains in the world but cannot produce further mining drops.

### Visible stock

- Display `remaining/10000` above each encampment, updating when a completed mining action consumes resources.
- Use a world-space display associated with the encampment block entity so it remains attached to the site and does not require a separate display entity to be saved or cleaned up.
- Keep the display readable from nearby range and avoid rendering it through solid blocks.

### Citizen site discovery and travel

- Citizens locate generated encampment structure starts, then resolve their worksite positions and current resource amounts.
- Remove the current 96-block-only site selection as the sole way to find work. Select the nearest encampment that has usable resources and has a valid standing position.
- Citizens travel toward distant encampments using reachable intermediate navigation targets, recalculating as they move and surrounding chunks load, instead of requesting a single path across an arbitrarily large unloaded distance.
- Recheck site validity, resource amount, work schedule, and citizen inventory before starting and completing each mining action.
- Skip depleted or inaccessible sites and search for another site. If no usable generated encampment can be located, wait and retry without making a trip home solely because the site search failed.

## Persistence and compatibility

- Save resource amount with each encampment worksite block entity, with missing data defaulting to 10,000.
- Preserve already-generated settlement mines. They become valid encampments with full resource stock when their block entities load.
- New settlements omit their embedded mine. Existing settlements are not retroactively rewritten.
- Keep the existing site-position saved data compatible, extending or replacing it only as needed for locating and tracking independent structures.

## Data configuration

- Keep drop outcomes in the existing `citizen_mining` loot table.
- Add a small datapack resource-cost map keyed by item identifiers, with a configurable default cost and the initial coal, iron, and gold values above.
- Keep structure biome membership and placement spacing in standard world-generation data files.

## Failure handling

- Treat missing/invalid resource cost data as the configured default cost; reject non-positive costs during loading or fall back to the default.
- Treat missing site block entities or invalid worksite blocks as unusable sites and remove stale locations from the search registry when safe.
- When a resource pool is depleted between selection and action completion, award no unaffordable items and retry site selection.
- If a citizen cannot reach a selected site after repeated path attempts, mark it inaccessible for that search cycle and try the next candidate.

## Acceptance criteria

1. Newly generated settlements no longer contain mine encampments.
2. Independent encampments generate in configured overworld surface biomes at the configured spacing.
3. Existing settlement mines continue to function and receive default stock.
4. Every encampment shows its live remaining resource and maximum in `remaining/10000` form.
5. A completed mining action deducts each yielded item's configured cost, caps partial stacks to affordable counts, and never makes the pool negative.
6. A mining action that cannot fit its drops in citizen inventory deducts no resource.
7. Citizen miners can discover, travel to, and work at encampments beyond their initial 96-block neighborhood, while avoiding depleted or inaccessible sites.
8. Resource values, default cost, structure biomes, and structure placement spacing can be adjusted through data files.
