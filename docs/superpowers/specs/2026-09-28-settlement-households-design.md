# Economist Wars Settlement Households

## Goal

Add a new Economist Wars village-like settlement structure to world generation. Each household home in the settlement is populated by one husband and one wife, linked in one persistent household record.

## Player experience

- Naturally generated settlements appear in newly generated terrain.
- A settlement contains multiple household homes.
- Each home is occupied by a husband-and-wife household: two persistent citizen entities sharing one household ID.
- Interacting with either citizen continues to show the existing citizen profile, including their shared household ID and household size of two.
- Existing `/ew household <count>` command behavior remains available for testing and manual citizen creation.

## World generation

- Register a new Economist Wars settlement structure and its generation configuration; do not modify vanilla village structure pools.
- Build the settlement from reusable vanilla-style structure templates and jigsaw pieces, including distinct home pieces that identify household spawn locations.
- Place settlements only in suitable biomes and at normal structure spacing so they are uncommon landmarks rather than frequent clusters.
- Generation affects newly generated chunks only. Existing worlds and chunks are not retrofitted.

## Household population and persistence

- Each generated home receives exactly one household with exactly two citizens.
- The citizens use the current identity generation, rendering, and entity persistence systems. The home does not prescribe sex-specific skins; one citizen is male and one is female, with their generated name and skin retained.
- Add persistent world data for populated home locations (dimension and block position) so a home is never populated twice when a chunk reloads or world data is saved and restored.
- Household registration, citizen creation, and entity insertion must roll back together if either citizen cannot be spawned. Do not leave a half-populated home or orphan household record.
- Population must run on the logical server. Client-only classes must remain isolated from common/world-generation code.

## Scope boundaries

- Do not add work, needs, production, trade, or household controls.
- Do not change vanilla villages or make existing chunks generate new structures.
- Do not add a configurable household size in this slice; every home contains one couple.
- Keep the operator household command as a development/manual tool.

## Documentation

Update README to describe the new settlement, its household homes, couple population, and that structures appear in newly generated terrain.

## Acceptance

- The mod builds against the existing Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, and Java 25 targets.
- A new world can generate the Economist Wars settlement in an allowed biome, with multiple household homes.
- Every generated home is populated by exactly two citizens, one male and one female, sharing one household ID; the profile screen reports household size two.
- Chunk reloads and world save/reload do not add duplicate citizens or households to an already populated home.
- Failed population leaves no partial household or citizen membership behind.
- A dedicated server can load the mod without resolving client-only classes.
- README accurately describes natural settlement generation and the household population.

## Implementation investigation

Before implementation, verify the supported Minecraft 26.3/Fabric API world-generation registration path and the safest server-side hook for populating a home exactly once. Keep the home identifier stable across chunk reloads and avoid scanning every block in loaded chunks each tick.
