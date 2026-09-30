# Unified Citizen Simulation for Loaded and Unloaded Chunks

## Summary

Make one server-side simulation authoritative for every citizen, whether its chunk is loaded or not. It will use the same action priorities, rules, timing, and outcomes in both cases. Loaded citizen entities will display the simulation’s location, action, and progress; they will no longer run a separate AI decision loop.

## Implementation Changes

- Add persistent citizen simulation records and a server-tick scheduler. Track each citizen’s identity, household, needs, skills, inventory, health, market memory, current action, progress, and simulated location. Process scheduled updates and elapsed game ticks without loading chunks.
- Refactor the existing actions—sleep, delivery, market, baking, farming, mining, and returning home—into simulation handlers that share the existing planner’s priorities and action logic. Keep world-dependent outcomes in saved state so handlers do not inspect or load unloaded blocks.
- Make household storage and mine resources available to the simulation without their block entities being loaded. When their chunks are loaded, block entities reflect the saved state.
- Turn loaded citizen entities into visual projections of simulation state. Show travel and work where possible, but only move through already-loaded chunks; simulation progress continues independently if a destination is unloaded.
- Add an internal action-handler interface so future actions can join the same decision and execution pipeline without creating a second loaded-only behavior path.

## Test Plan

- Verify identical decisions and outcomes for the same citizen state with its entity loaded and unloaded.
- Cover each current action’s eligibility, progress, completion, and resource or need changes, including skill effects, market visits, daily feeding, and mining depletion.
- Verify save/reload resumes action progress once, applies elapsed game ticks once, and does not duplicate production, trades, feeding, damage, or deaths.
- Verify unloaded simulation does not request chunks, and loaded visuals do not force-load travel destinations.
- Build on a new world; existing-world citizen migration is out of scope.

## Assumptions

- Simulation time advances with Minecraft server game ticks; time while the server is stopped does not accrue.
- The first version targets new worlds, as requested.
- Existing action rules and priorities remain the source of truth; only their execution moves into the unified simulation.
