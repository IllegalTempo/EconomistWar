# Citizen Information Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Open a native Minecraft profile screen when a player interacts with a citizen.

**Architecture:** The server creates a snapshot from persisted citizen identity and authoritative household saved data, then sends it as one typed clientbound play payload. The client displays that snapshot in a non-pausing native screen; the README documents the interaction and fields.

**Tech Stack:** Minecraft Java 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Java 25, Gradle 9.7.

**Spec:** `docs/superpowers/specs/2026-09-28-citizen-information-screen-design.md`

## Global Constraints

- Target Minecraft Java Edition 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, and JDK 25.
- The server is authoritative for citizen identity and household size.
- Display name, sex, citizen ID, skin identifier, household ID, and household size.
- An unassigned citizen displays a clear unassigned state and household size zero.
- The screen is informational, closes normally, and does not pause gameplay.
- Do not add profile editing, inventory, jobs, needs, trade, contracts, or household controls.
- Update README.md to describe the screen and displayed information.

## Review Focus

- Missing household: show unassigned and size zero; verify by opening a citizen without a household if the test setup permits.
- Stale or absent client state: screen must use server snapshot instead of client-side SavedData.
- Screen lifecycle: verify Escape closes it and gameplay remains unpaused.
- Long names or UUIDs: ensure labels remain legible at the standard window size.
- Payload registration: verify local integrated-server use through a manual client run.

---

### Task 1: Add authoritative citizen profile payload and send it on interaction

**Files:**
- Create: `src/main/java/com/economistwars/network/CitizenProfilePayload.java`
- Create: `src/main/java/com/economistwars/network/CitizenNetworking.java`
- Modify: `src/main/java/com/economistwars/EconomistWars.java`
- Modify: `src/main/java/com/economistwars/citizen/CitizenEntity.java`

**Interfaces:**
- `CitizenProfilePayload` is an immutable `CustomPacketPayload` record containing `String name`, `String sex`, `String citizenId`, `String skinId`, `String householdId`, and `int householdSize`.
- `CitizenNetworking.initialize()` registers the clientbound play payload type and codec once during common initialization.
- On server interaction, `CitizenEntity` resolves `HouseholdSavedData` and sends the snapshot to the interacting `ServerPlayer` with `ServerPlayNetworking.send`.

- [ ] **Step 1: Define a typed payload and codec** using Minecraft 26.3 `CustomPacketPayload`, `StreamCodec`, and `PayloadTypeRegistry.clientboundPlay()` conventions. Encode strings and household size using supported byte codecs.
- [ ] **Step 2: Register the payload** from `EconomistWars.onInitialize()` before it can be sent.
- [ ] **Step 3: Replace the chat summary interaction** with a server-authored snapshot sent only to the interacting player. Use the household record for current size; send empty household ID and zero size when unassigned or stale.
- [ ] **Step 4: Build the mod** with JDK 25 using `.tools/gradle-9.7.0/bin/gradle.bat build`; expected result is `BUILD SUCCESSFUL`.

### Task 2: Render the profile screen and document the experience

**Files:**
- Create: `src/client/java/com/economistwars/citizen/CitizenProfileScreen.java`
- Modify: `src/client/java/com/economistwars/EconomistWarsClient.java`
- Modify: `src/main/resources/assets/economistwars/lang/en_us.json`
- Modify: `README.md`

**Interfaces:**
- `CitizenProfileScreen(CitizenProfilePayload profile)` displays the immutable snapshot without querying world or server state.
- Client initialization registers a receiver for `CitizenProfilePayload.TYPE` and schedules opening the screen on the client thread.

- [ ] **Step 1: Implement the screen** with a title, labeled rows for all six profile fields, a clear translated unassigned household label, responsive centered layout, and normal Escape close behavior. Keep `isPauseScreen()` false.
- [ ] **Step 2: Register the clientbound payload receiver** in `EconomistWarsClient.onInitializeClient()` and open the screen on the Minecraft client thread.
- [ ] **Step 3: Add translation keys** for the screen title, field labels, and unassigned value in `en_us.json`.
- [ ] **Step 4: Update README.md** First playable slice description to say that interacting with a citizen opens the profile screen and list name, sex, citizen ID, skin, household ID, and household size.
- [ ] **Step 5: Build and launch the client** with JDK 25 using Gradle `build` and `runClient`. Manually create a household, interact with a citizen, inspect the displayed values, close with Escape, and confirm the world continues running.

## Self-review

- Spec coverage: server snapshot, authoritative household count, all listed fields, unassigned state, screen lifecycle, and README update each have explicit steps.
- Step specificity: each step has an exact file or interface and checkable expected behavior.
- Type consistency: the payload record fields and screen constructor use the same snapshot contract.
- Review focus: edge conditions are listed; local integrated-server manual verification covers the project's available run path.
- Proportion: two implementation tasks map to the common networking and client presentation/documentation responsibilities.
