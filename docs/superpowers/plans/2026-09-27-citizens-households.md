# Citizens and Households Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the initial playable Economist Wars Fabric mod slice with persistent named citizens and households.

**Architecture:** A dedicated citizen entity owns stable identity and presentation fields; world saved data owns household membership keyed by stable IDs. A command creates a household and citizens atomically, while entity interaction displays a concise summary. Client rendering uses a player-style model with a bundled skin selected once and persisted.

**Tech Stack:** Minecraft Java Edition 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Java 25, Gradle 9.7, Fabric Loom 1.18.1.

**Spec:** `docs/superpowers/specs/2026-09-27-citizens-households-design.md`

## Global Constraints

- Target the README's stated Minecraft Java Edition 26.3, Fabric Loader, Fabric API, and Java 25.
- This is a new project: the repository currently contains only `README.md` and is not a Git repository.
- Do not implement village generation, farming, trade, contracts, blueprints, or countries in this slice.
- Do not depend on Figma; this slice has no UI that needs a design mockup.
- Do not add or run automated tests; verify by building and a manual in-game smoke pass.

## Review Focus

- Entity serialization can load a citizen whose household record is absent; keep the citizen visible and report/repair orphan membership without assigning an unrelated household.
- Citizen removal must not leave stale member IDs; ensure cleanup runs once and tolerates already-missing records.
- Household creation can fail after some entities are added; creation must roll back the whole operation.
- Citizen identities and selected skins must remain stable after save/reload.
- Dedicated server loading must not resolve client-only rendering classes; isolate client registrations.

---

## File Map

- `settings.gradle` — Gradle plugin repositories and project name.
- `build.gradle` — Loom, Java toolchain, dependencies, resource processing, and run configs.
- `gradle.properties` — verified Minecraft, loader, API, Loom, and mod version properties.
- `src/main/resources/fabric.mod.json` — mod metadata and entrypoint declarations.
- `src/main/java/com/economistwars/EconomistWars.java` — common initialization and registrations.
- `src/main/java/com/economistwars/citizen/CitizenEntity.java` — entity identity fields, persistence, interaction, and household cleanup hook.
- `src/main/java/com/economistwars/citizen/CitizenEntityType.java` — entity type registration and attributes.
- `src/main/java/com/economistwars/household/Household.java` — household ID and member IDs value type.
- `src/main/java/com/economistwars/household/HouseholdSavedData.java` — world-persistent household records and membership operations.
- `src/main/java/com/economistwars/citizen/CitizenIdentity.java` — deterministic one-time name, sex, and skin assignment.
- `src/main/java/com/economistwars/command/CitizenCommands.java` — validated household creation command and rollback behavior.
- `src/client/java/com/economistwars/EconomistWarsClient.java` — client-only renderer registration.
- `src/client/java/com/economistwars/citizen/CitizenRenderer.java` — player-style model and persisted skin texture selection.
- `src/main/resources/assets/economistwars/textures/entity/citizen/` — bundled skins.
- `src/main/resources/assets/economistwars/` — names and interaction translations.

Use Java package `com.economistwars` and mod ID `economistwars`.

## Task 1: Scaffold and verify the Fabric project

**Files:** Gradle wrapper/configuration, `src/main/resources/fabric.mod.json`, `src/main/java/com/economistwars/EconomistWars.java`, `src/client/java/com/economistwars/EconomistWarsClient.java`, resource directories.

- [x] Check official Fabric project generator and metadata for Minecraft 26.3-compatible Loom, Loader, and Fabric API values; use Java 25.
- [x] Create a minimal Fabric project with mod ID `economistwars`, split common/client sources, and a working client run configuration.
- [x] Build with Gradle 9.7.
- [x] Launch the development client and confirm the mod appears in the loaded mod list.

## Task 2: Add household persistence and identity generation

**Files:** `src/main/java/com/economistwars/household/Household.java`, `src/main/java/com/economistwars/household/HouseholdSavedData.java`, `src/main/java/com/economistwars/citizen/CitizenIdentity.java`, common initialization.

- [x] Implement stable household IDs and member IDs with add/remove/query operations; mark world data dirty on every mutation.
- [x] Implement world saved-data serialization with safe handling for missing/invalid records and duplicate member references.
- [x] Implement one-time citizen identity generation: stable random name, sex value, deterministic skin ID chosen from that sex's bundled skin set, and stable citizen ID.
- [x] Build with Gradle 9.7.

## Task 3: Register the persistent citizen entity

**Files:** `src/main/java/com/economistwars/citizen/CitizenEntity.java`, `src/main/java/com/economistwars/citizen/CitizenEntityType.java`, `src/main/java/com/economistwars/EconomistWars.java`.

- [x] Register the citizen entity type and required attributes using the verified 26.3 API.
- [x] Implement fields for citizen ID, name, sex, skin ID, and household ID, including entity save/load methods.
- [x] Implement interaction to show the name, sex, and household summary using translated text.
- [x] On removal, remove this citizen ID from the household record idempotently; remove the household if empty.
- [x] Build with Gradle 9.7.

## Task 4: Add client rendering and bundled skins

**Files:** `src/client/java/com/economistwars/EconomistWarsClient.java`, `src/client/java/com/economistwars/citizen/CitizenRenderer.java`, `src/main/resources/assets/economistwars/textures/entity/citizen/`, client resource translations.

- [x] Register the renderer only from the client entrypoint.
- [x] Render the custom entity with the player-style model and resolve texture from its persisted skin ID.
- [x] Add a small bundled set of valid skin textures for each sex choice; ensure every generated skin ID resolves to an asset.
- [x] Build with Gradle 9.7 and launch the development client; confirm client initialization and resource loading. In-world rendering remains for manual acceptance.

## Task 5: Add atomic household creation command

**Files:** `src/main/java/com/economistwars/command/CitizenCommands.java`, common initialization, translations.

- [x] Register an operator-level command that accepts a citizen count and creates one household near the invoking player.
- [x] Reject invalid counts and non-player invocation before changing world data.
- [x] Create household and entities as one operation; if any spawn fails, discard already spawned entities and remove the household record.
- [x] Report created household ID and count to the invoking player.
- [x] Build with Gradle 9.7. Valid, invalid, and console invocations remain for manual acceptance.

## Task 6: Manual acceptance pass and documentation

**Files:** `README.md` and any fixes from the acceptance pass.

- [x] Document prerequisites, development run command, citizen creation command syntax, and current slice boundaries in `README.md`.
- [ ] In a development world, create a household, verify the requested citizens appear with names and player-style skins, and interact with a citizen to inspect details.
- [ ] Save and reload the world; confirm citizen identity, skin, household identity, and membership remain stable.
- [ ] Remove a citizen and confirm household membership is cleaned up; create an empty household scenario if exposed by the command and confirm cleanup behavior.
- [x] Run Gradle 9.7 `build`; result: `BUILD SUCCESSFUL`.

## Self-review

- **Spec coverage:** Scaffold/version setup (Task 1); household saved data and identity generation (Task 2); entity persistence and interaction (Task 3); player-style appearance and skins (Task 4); validated atomic creation (Task 5); build, save/reload, cleanup, and user documentation (Task 6).
- **Step clarity:** Each implementation step names a file/component and a checkable behavior; API calls intentionally follow the verified 26.3 mappings discovered during Task 1.
- **Type consistency:** Household records consistently use stable household and citizen identifiers. The entity owns its household ID; the saved-data store is the authority for membership.
- **Review focus:** Orphan handling and stale membership are covered in Tasks 2–3; atomic rollback in Task 5; stable identity/skin in Tasks 2–4 and 6; client/server boundary in Tasks 1 and 4.
- **Proportion:** Six tasks map to the distinct deliverables in the spec, without adding downstream economy systems.

## References

- [Fabric developer guides](https://docs.fabricmc.net/develop/)
- [Fabric project creation](https://docs.fabricmc.net/develop/getting-started/creating-a-project)
- [Fabric Loom](https://docs.fabricmc.net/develop/loom/)

