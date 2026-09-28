# Citizen Skills and Scrollable Profile Design

## Goal

Give every citizen a persistent, extensible set of skills whose experience is earned by performing related work. Implement Farming experience first, while establishing Mining and Building as stored skills for future systems. Extend the citizen profile screen so additional information remains usable as the profile grows.

## Scope

This slice includes:

- `FARMING`, `MINING`, and `BUILDING` skill identifiers.
- Persistent XP per skill on each citizen, defaulting safely for older saves.
- A server-side API for reading skill XP/level and awarding XP.
- Farming XP awarded only after a mature wheat crop is successfully harvested and replanted.
- Mining and Building data stored but not yet advanced by gameplay.
- Skill levels included in the authoritative citizen profile payload.
- A scrollable, resize-tolerant profile screen that presents current and future fields through a vertically laid-out content region.

This slice does not add mining, building, new jobs, tool requirements, skill perks, multiplayer skill editing, or client-authoritative progression.

## Skill model

`CitizenSkill` is an enum-like identifier with stable serialized names. `CitizenSkills` owns a map from skill identifier to non-negative XP. Missing, malformed, or negative saved values become zero; unknown future skill identifiers are ignored when loading rather than invalidating the citizen.

Levels are derived, not stored separately: `level = min(100, xp / 100)`. XP awards clamp at a safe integer maximum before level calculation. The public mutation API accepts a skill and a positive XP amount, marks the citizen state dirty through normal entity persistence, and is callable only from server-side gameplay code.

The initial farming reward is five XP for a successful mature wheat harvest. The reward is applied after the crop has been replaced with wheat and the citizen receives its carried wheat, so failed block updates or invalid ownership do not grant experience. Replanting is part of the same successful farming action and does not award a second reward.

## Persistence and networking

`CitizenEntity` serializes the skill map alongside existing identity fields. Loading older citizens with no skill section creates zeroed Farming, Mining, and Building skills. The existing server-authored `CitizenProfilePayload` gains the three skill levels (and may include XP values if the UI needs them); the codec remains ordered and all values are bounded integer fields.

The server constructs the profile snapshot from the citizen's authoritative skill state. The client does not query world data or calculate levels.

## Profile UI

`CitizenProfileScreen` keeps the current non-pausing and Escape-close behavior, but replaces the fixed-height panel layout with a scrollable content viewport:

- Panel width adapts to the window while preserving readable margins.
- The viewport is bounded by top/bottom padding and never renders content beneath the title or outside the panel.
- The field list is laid out from data, including the current household/economy fields and a Skills section.
- Mouse wheel scrolling and keyboard scrolling move the content offset within its calculated range.
- Long values wrap within the content width.
- Resizing recomputes panel and viewport dimensions without losing the profile snapshot.
- The UI remains usable when more fields are added later; no fixed total height is assumed.

The screen shows Farming, Mining, and Building levels in the first skills section. XP totals are not required in the first presentation unless the existing layout can include them without reducing readability.

## Error handling and compatibility

- XP is never awarded on the client.
- Invalid skill data cannot produce negative XP or a level above 100.
- Citizens from older saves remain loadable with baseline skills.
- A malformed or oversized network value is bounded by the codec's normal integer handling and server snapshot construction.
- Profile rendering must tolerate long IDs and wrapped translated text without clipping the panel.

## Testing and acceptance

Automated tests cover:

- Zero/default skill state.
- XP-to-level conversion, including the level-100 cap.
- Positive XP accumulation and ignoring non-positive awards.
- Farming reward application only on a successful harvest path where practical through the existing farming seam.

Manual acceptance covers:

- Create or load a citizen and confirm Farming, Mining, and Building begin at level zero.
- Let a citizen harvest a mature owned wheat crop and confirm Farming XP/level increases once.
- Reopen the profile and confirm the authoritative values are shown.
- Resize the client window and scroll through all current profile sections.
- Confirm Mining and Building remain unchanged until their future systems exist.

## Files

- Create `src/main/java/com/economistwars/citizen/CitizenSkill.java`.
- Create `src/main/java/com/economistwars/citizen/CitizenSkills.java`.
- Modify `src/main/java/com/economistwars/citizen/CitizenEntity.java`.
- Modify `src/main/java/com/economistwars/citizen/CitizenFarmGoal.java`.
- Modify `src/main/java/com/economistwars/network/CitizenProfilePayload.java`.
- Modify `src/client/java/com/economistwars/citizen/CitizenProfileScreen.java`.
- Modify `src/main/resources/assets/economistwars/lang/en_us.json`.
- Add focused tests under `src/test/java` for the pure skill model.
