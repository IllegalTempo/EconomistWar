# Citizens and Households: First Playable Slice

## Goal

Create the initial playable foundation for Economist Wars, a Minecraft Java Edition Fabric mod. Players can spawn named citizens, see them in the world as player-like characters, and inspect their basic identity and household membership. This establishes persistent citizen and household data for later farming, trade, contracts, blueprints, and country systems.

## Constraints

- Target the README's stated Minecraft Java Edition 26.3, Fabric Loader, Fabric API, and Java 25. Verify compatible current versions and mappings before scaffolding; if the stated game version is not available in the ecosystem, report the compatibility constraint before selecting a substitute.
- This is a new project: the repository currently contains only `README.md` and is not a Git repository.
- Do not implement village generation, farming, trade, contracts, blueprints, or countries in this slice.
- Do not depend on Figma; this slice has no UI that needs a design mockup.

## Player experience

- A command creates a household with a configurable number of citizens at or near the invoking player's location.
- Each citizen receives a persistent random name, sex value, skin choice based on sex, and household membership.
- Citizens appear in the world with a player-style model and can be interacted with to show their identity and household details.
- Citizens and household membership survive save/reload.
- Invalid command input produces a useful error rather than partially creating data.

## Architecture

### Citizen entity

Implement a dedicated persistent entity with a player-like model/render path and ordinary world entity identity. Citizen state includes a stable identifier, generated name, sex, skin identifier, and household identifier. Keep identity generation separate from rendering and entity persistence so later systems can consume citizen data without depending on visuals.

### Household data

Store households as world-level persistent data, keyed by stable identifiers, with member citizen identifiers. This makes household identity survive entity unloads and allows future household needs, inventories, and migration data to be added without overloading the entity. Maintain membership consistently when citizens are created or removed.

### Creation and interaction

Register one administrative/debug command for creating households and citizens near the player. Validate count and permissions before creating anything. Citizen interaction presents a compact identity and household summary using the available in-game messaging or interaction UI; avoid a custom screen in this slice.

### Assets and identity

Provide a small bundled set of usable citizen skins, selected deterministically from the generated sex and a per-citizen random seed. Store the selected skin identifier so it does not change after reload. Names and sex are assigned once at creation and persisted. Skin selection is a presentation rule, not a gameplay rule.

## Persistence and failure handling

- Persist citizen fields through entity serialization and household records through world saved data.
- Treat missing or invalid optional fields in older saves with safe defaults where possible; never silently attach a citizen to an unrelated household.
- On entity removal, clean up its household membership. Empty households may be removed.
- Creation should validate all inputs before mutating the world. If entity creation fails partway, remove any created entities and household records from that operation.

## Testing and acceptance

- Project builds against the verified target game, loader, API, mappings, and Java toolchain.
- The command creates the requested household and citizen count with valid, unique persistent identities.
- Citizens render with the expected player-like model and assigned skin and show identity/household details on interaction.
- Save/reload preserves citizen identity, skin selection, household identity, and membership.
- Invalid counts and failed creation do not leave orphaned or partially created household data.

## Follow-on boundaries

Future slices can add generated houses, citizen jobs and farming, household consumption, local exchange, citizen/player contracts, blueprint placement contracts, and player-founded countries. These systems should depend on stable citizen and household identifiers and not on the debug command or rendering implementation.
