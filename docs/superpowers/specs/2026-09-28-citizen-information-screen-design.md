# Citizen Information Screen

## Goal

When a player interacts with a citizen, open a Minecraft-native screen that presents that citizen's profile and household information.

## Player experience

- Interacting with a citizen opens a screen instead of sending the current chat summary.
- The screen displays the citizen's name, sex, citizen ID, skin identifier, household ID, and household size.
- A citizen without a valid household is shown as unassigned, with household size shown as zero or not applicable.
- The screen can be closed with the normal Minecraft screen controls and does not pause multiplayer play.

## Architecture and data flow

- The server remains authoritative for household membership and size.
- On interaction, the server creates a profile snapshot from the citizen's persisted identity and the household saved data.
- A typed server-to-client payload carries the snapshot to the interacting player. The client opens the native screen using that received snapshot; it does not query server saved data or infer household size locally.
- The screen only presents profile data. It adds no editing, trading, or household management actions.
- Register payload codecs and receivers through Fabric's supported networking API for the project's Minecraft and Fabric API versions.

## README

Update the First playable slice section to explain that interacting with a citizen opens the profile screen and list the information it displays. Remove the now-obsolete description of interaction as a chat summary.

## Acceptance

- A client interacting with a citizen receives a server-authored snapshot and opens the profile screen.
- The screen displays all existing persisted citizen identity fields and current household ID and size accurately.
- Unassigned citizens display a clear unassigned state.
- The player can close the screen normally, and multiplayer gameplay continues without being paused.
- The project builds for the existing Minecraft, Fabric, and Java versions.
- README accurately describes the implemented interaction and visible fields.

## Scope boundaries

- Do not add profile editing, citizen inventory, jobs, needs, trade, contracts, or household management controls.
- No Figma mockup is required for this native Minecraft screen.
