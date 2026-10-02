# Backend Citizen Simulation Design

Status: approved by the user on 2026-10-02; implementation plan pending review.

## Intended outcome

Citizens are persistent server-side simulation records. Minecraft entities are visual representations and interaction adapters. A citizen continues making decisions, travelling, producing, consuming, and trading while its chunk is unloaded. Loading or unloading a visual entity must not change simulation results or household membership.

Assumptions: backend means the integrated or dedicated Minecraft server. Simulation advances while that server ticks, including with no nearby players. It does not advance while the world is closed, or while the integrated server is paused. Newly generated or otherwise registered citizens participate; this does not generate the whole unexplored world.

## Approach

Use world SavedData and one server simulation service as the authority. This fits the existing household and ownership persistence and avoids adding an external service.

Alternatives considered:

- Force citizen chunks to tick: retains the entity-owned architecture and incurs chunk costs; does not meet the requirement that entities are only visuals.
- Calculate elapsed work when an entity loads: cheaper to introduce, but cannot model interacting households, scarce mine stock, or trades as they happen.

## Authoritative records

Introduce CitizenSavedData indexed by citizen UUID. Each CitizenState contains identity, household ID, dimension, logical position, health/alive state, needs, skills, inventory, market memory, selected action, action progress, travel origin/destination/countdown, scheduling timestamps, and persistent random state.

Move simulation fields out of CitizenEntity. Entity save data identifies the citizen and supports legacy import; it is not a second writable copy of citizen inventory or needs.

HouseholdSavedData remains authoritative for shared inventory, membership, home and economy. OwnershipSavedData remains authoritative for land. Extend mine and market registries beyond positions: mine records hold remaining resource points and work bounds; market records hold offers and trade history. Register household beds and logical arrival anchors when homes load or generate.

Loaded worksite and market block entities become adapters to these records. Runtime block removal invalidates the corresponding registered asset. Registering an existing asset must not reset depleted stock or replace newer offers with a stale block snapshot.

## Simulation clock and decisions

Register one Fabric server tick callback. Process each live citizen once per server tick, independent of entity availability. Retain the current 40-tick decision rescore interval and per-tick production/travel countdowns. Run all shared state changes on the server thread with a stable citizen ordering.

Daily needs and work schedules use the overworld day clock. Action durations use server ticks. Process household feeding, wheat sales and land decisions once per household per day from the service, rather than once through each entity. A forward day-clock jump increases needs for elapsed days but performs one daily economy pass, matching the current catch-up behaviour; normal consecutive server days each receive their own pass. Moving the day clock backwards must not replay already processed days.

Replace Minecraft Goal execution with simulation actions offering eligibility, evaluation, start, advance and stop operations over records. Reuse the existing scoring maths, skill multipliers, output distributions, market-memory adjustment and switching threshold. Keep sleep, delivery, market, bakery, farm, mine and return-home behaviour. Preserve abstract farm production rather than reintroducing crop-maturity requirements.

Consumption, production completion, mining stock deduction, trades and shortage damage happen exactly once in the service. Presentation never calls those operations independently.

## World-independent work and travel

Simulation reads registered farms, mines, markets, homes and beds without getChunkAt, block-entity lookups, structure searches or chunk tickets. Sites become available through normal world generation/registration. Seeking a mine selects among registered sites; discovering arbitrary ungenerated sites is outside this change.

Travel retains the distance-based 10–200 tick countdown. On completion, change the record's logical location even if the destination chunk is unloaded. A sleep action reserves a registered household bed in backend state; unavailable beds make sleeping ineligible.

Physical collision checks apply only when displaying an entity in an already entity-ticking chunk. If the logical anchor is obstructed, choose a nearby safe visual position without changing the logical work target or forcing a chunk load. If no safe position exists, defer display while simulation continues.

## Entity lifecycle and player interactions

A presentation manager ensures at most one entity per live citizen, materializing it only in an already entity-ticking destination chunk. It reconciles entities on chunk load and as logical positions change. Removing a visual because its chunk unloads or its logical position moves elsewhere does not kill the citizen or remove household membership.

CitizenEntity mirrors identity, tool, sleep appearance, health, action and progress. It does not register decision goals or run economic upkeep. Right-click profiles read the authoritative record. Player attacks forward accepted damage to the service; death is resolved once in backend state, including inventory disposition and existing household/land cleanup. Environmental entity damage follows the same route while the visual is active; no unloaded environmental-hazard model is introduced.

Player interaction and simulation updates run on the server thread. Client renderers and profile payloads continue using snapshots. A stale or duplicate entity must not overwrite a record or perform a second interaction.

## Save compatibility and migration

Add explicit save schema versions. A newly spawned citizen creates its record before any visual. Import legacy citizen fields on the first load of an old entity when its UUID has no record. Once a record exists it wins over legacy entity state, including position, health and inventory.

Import legacy mine stock, work bounds, market offers/history and home bed anchors when their chunks first load, without overwriting already migrated records. Existing unloaded entities and block assets cannot be migrated until their data is encountered; document this limitation for old worlds. New worlds register complete records at generation time.

Persist action phase, elapsed work, pending mining outcome, travel progress and random state so reload neither resets work nor rerolls already selected rewards. Keep dead-citizen tombstones to prevent an old saved visual from resurrecting a citizen. A malformed action resumes safely as idle without awarding output; missing household/site records make the action ineligible and trigger a new decision.

## Verification and acceptance

- Identical initial records and random state yield identical simulation state with visuals present, absent, or repeatedly loaded/unloaded.
- Farm, mine, bakery, travel and barter progress with no loaded citizen entity.
- Simulation paths never load chunks or require block entities.
- Concurrent miners cannot overspend stock; trades and deliveries conserve items and respect capacity.
- Household daily processing occurs once, regardless of citizen count or visual count.
- Save/reload preserves action and travel progress without duplicating products, consumption, or damage.
- Legacy migration is idempotent; stale visuals cannot replace authoritative records or revive dead citizens.
- Entity disappearance preserves membership; backend death removes membership exactly once.
- Profiles and progress indicators agree with the backend record.
- Run existing and new focused JUnit tests, the Gradle build, and an in-game load/unload smoke check. Report any check that cannot be performed.

## Implementation boundaries

Implement in stages: authoritative records and migration; world-asset records; backend actions and ticking; visual lifecycle and interaction adapters; regression verification and README updates. Maintain the current local changes as the baseline. Do not revert unrelated edits, add external infrastructure, or introduce real-world offline progression.
