# Economist Wars

Economist Wars is an early-stage Fabric mod for Minecraft Java Edition. The current playable slice establishes persistent citizens and households with a first farming and food-needs loop.

## Current progress

### Implemented

- Persistent citizen entities with generated names, sex, stable IDs, and one of four bundled player-style skins.
- Persistent household records with citizen membership.
- Naturally generated Economist Wars settlements in plains, sunflower plains, and meadow biomes. Each settlement has three household homes, and each home spawns a husband-and-wife pair sharing one household record. Settlements appear in newly generated chunks.
- The operator command `/ew household <count>` remains available to create a household of 1–16 citizens near the player. For example, `/ew household 4` creates a four-citizen household. The command requires gamemaster command permission.
- Interacting with a citizen opens a profile screen showing their name, sex, citizen ID, skin, household ID, and household size. Citizens without a valid household are shown as unassigned.
- Citizen identity and household membership are saved with the world and restored when it is loaded again.
- Newly generated settlements contain one large farm split into six 7×7 parcels. Each of the three starting households receives one parcel; three begin unowned. Citizens harvest ripe wheat only from parcels their household owns, replant it, and return toward home when they stray too far.
- Each home has a sealed household storage block with nine ItemStack slots. Citizens hold a hoe, swing and show a crop-breaking effect while harvesting, carry two real wheat items per crop, and return home to deposit them. Survival players and hoppers cannot access it; creative players can open it for inspection and editing. Citizen deposits and withdrawals still check the household owner.
- Once per Minecraft day, an active household consumes one stored wheat item per member. New farm households start with eight wheat items and 100 coins. Up to four stored wheat items above a reserve of 32 are sold for one coin each per day.
- A household food shortage damages each of its citizens once when the daily feeding cycle records it, with damage capped at four health points per day.
- Citizens have persistent Farming, Mining, and Building skills. Farming experience currently increases by five for each successful mature wheat harvest; Mining and Building are stored for future systems.
- Citizens sprint on long trips to the farm or home and while fleeing danger; they slow to a walk near their destination.
- Citizens can buy an unowned parcel or one offered by a different household for 60 coins. A household offers land only when it owns more than one parcel and has at least 16 stored wheat items, so its final farm remains protected. Citizens seek more land when food is low or savings reach 150 coins. Land deeds, storage contents, coins, and the last day's shortage persist with the world; citizen profiles show the household's wheat, coins, and parcel count.
- Stand in a farm parcel and run `/ew land` to see its current owner or whether it is unowned.

### Not implemented yet

Citizens do not naturally spawn in vanilla villages. Farming currently covers wheat in newly generated settlement parcels; manually spawned households and homes generated before this update have no new deed. Other work, production, broader trading, contracts, blueprints, player-founded countries, and armies are future systems.

## Run and build

Open the project in IntelliJ IDEA and set the Gradle JVM to JDK 25. The Gradle wrapper also selects the project-local JDK at `.tools/jdk-25.0.4.1+1` when it is present; otherwise, set `JAVA_HOME` to a JDK 25 installation.

```shell
./gradlew build
./gradlew runClient
```

On Windows, use `gradlew.bat` in place of `./gradlew`.

The built mod JAR is written to `build/libs/economist-wars-0.1.0.jar`.

## Development versions

- Minecraft Java Edition 26.3
- Fabric Loader 0.19.5
- Fabric API 0.161.0+26.3
- Java 25
- Gradle 9.7 through the checked-in wrapper

## Long-term direction

The intended economy grows from connected households in settlements: citizens farm, meet household needs, and eventually trade surplus goods with neighbors and players. Later systems may add contracts for goods, services, and construction from blueprints, followed by player-founded countries and their economies.
