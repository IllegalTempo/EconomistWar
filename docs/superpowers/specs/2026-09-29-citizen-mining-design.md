# Citizen Mining Design

## Goal

Give settlement citizens a mining work loop. A settlement has one public mining cave; citizens collect abstract mineral items there, show mining activity through movement and block-breaking animation, and deposit gathered materials into their household storage. Mining must not consume or modify the cave's terrain.

## Existing context

- Citizens use prioritized Minecraft AI goals. Farming already provides a travel, timed work animation, and carried-item pattern.
- Citizen mining skill and profile display are already persisted, but the skill has no work loop.
- Settlements are generated with jigsaw structure templates and three homes.
- Household storage has nine saved item slots but only accepts wheat. Feeding and market logic depend on wheat-specific count and withdrawal operations.

## Design

### Public mine site

Add one dedicated mine feature to newly generated settlement structure templates, shared by all households in the settlement. Its layout includes an accessible cave chamber and a defined work face. Citizens can identify the work face through a mod-defined work-site marker so mining does not depend on vanilla ore blocks or scanning arbitrary terrain. The site is public; household ownership applies to collected goods, not access to the cave. Minecraft navigation and entity animations remain in use, but vanilla ore blocks do not define mining work or output.

### Citizen mining loop

Use a five-day repeating work cycle. On one day per cycle, citizens may farm their household's claimed crops; on the other four days, they may mine at the settlement's public mine. This limits harvesting to once per five Minecraft days per citizen while making mining the regular work between harvest days. The cycle is derived from the overworld day count so it needs no additional per-citizen schedule data.

Add a mining goal to citizen AI. A citizen with a valid household and room to carry resources locates a loaded public mine site within the existing settlement-scale search distance, travels to its work face, and performs a short timed work action. The citizen swings a mining tool and shows block-breaking progress/particles on the work face. The marker and cave blocks remain unchanged. On completion, the citizen receives a small bounded stack of abstract mineral drops, gains Mining experience, and later returns to the household home to deposit the carried items.

Carried minerals use the citizen's existing offhand item stack so the current AI priorities can route a citizen home before another work goal starts. Storage capacity or invalid household/site state causes the goal to stop or retry without losing items. Existing farm harvesting and delivery remain operational.

### General household storage

Allow storage slots to accept ordinary item stacks and save/load them using the existing slot persistence format, retaining compatibility with worlds that have wheat saved under the current keys. Keep wheat-specific APIs and behavior wheat-only: food totals, daily feeding, surplus sales, and starter-food setup continue to count or consume wheat and ignore minerals. Add a general insertion path for citizen deposits that merges compatible stacks and returns any remainder.

### Skill and animation

Increase Mining experience only when a mining work action successfully produces a stack for the citizen to carry. Use a short repeated swing and destroy-progress animation on the fixed work face, clearing progress whenever the goal stops. Mining remains abstract: no blocks or ore veins are depleted.

## Persistence and compatibility

- The public site is part of the settlement's generated structure, so it needs no per-tick site data or depletion state.
- Resource stacks remain in citizen offhand and household storage NBT using Minecraft ItemStack serialization.
- Existing wheat slots, food totals, household ownership, and persisted Farming/Mining/Building skill fields remain readable.
- Existing settlements generated before the feature will not gain a cave automatically; newly generated settlements contain the mine site.

## Scope and verification

Implementation is limited to the five-day farm/mining work schedule, the citizen mining goal and carried resource support, generic household-storage insertion while preserving wheat accounting, settlement template changes, and a concise README update. Verify compilation and inspect the generated template resources and compatibility paths. Tests are not part of this change unless explicitly requested.

## Open implementation detail

Choose the smallest reliable mod-defined marker representation compatible with the existing structure templates, then bind the mining goal to it. Keep that representation visually coherent as part of the public cave.
