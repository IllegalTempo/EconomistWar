# Economist Wars

Economist Wars is an early-stage Fabric mod for Minecraft Java Edition. The current playable slice establishes persistent citizens and households with rotating farming and mining work, household storage, and a food-needs loop.

Citizens are simulated as persistent backend records on the Minecraft server. Their needs, decisions, travel, production and trades continue even when their chunks are unloaded; nearby Minecraft entities display those records and forward player interactions. Simulation pauses when the server stops or the integrated server is paused. Mines, farms, markets and beds must have been generated and registered before citizens can use them; simulation does not generate unexplored terrain.

Existing worlds migrate citizen entities and mine/market data when those chunks next load. Once imported, backend records take precedence over old entity and block snapshots. Visit existing settlements and mining sites once after updating to register their data for unloaded simulation.

## Current progress

### Implemented

- Persistent backend citizens with generated names, sex, stable IDs, and one of four bundled player-style skins; entities display citizens in nearby ticking chunks.
- Persistent household records with citizen membership.
- Naturally generated Economist Wars settlements in meadow, forest, birch forest, old-growth birch forest, flower forest, dark forest, and cherry grove biomes. These biomes are separate from vanilla village biomes, so settlement placement is not tied to village locations. Each settlement has three household homes, and each home spawns a husband-and-wife pair sharing one household record. Settlements appear in newly generated chunks.
- The operator command `/ew household <count>` remains available to create a household of 1–16 citizens near the player. For example, `/ew household 4` creates a four-citizen household. The command requires gamemaster command permission.
- Interacting with a citizen opens a profile screen showing their name, sex, citizen ID, skin, household ID, and household size. Citizens without a valid household are shown as unassigned.
- Citizen identity and household membership are saved with the world and restored when it is loaded again.
- Newly generated settlements contain one large farm split into six 7×7 parcels. Each of the three starting households receives one parcel; three begin unowned. Citizens produce wheat abstractly at a parcel their household owns; crop maturity is not required. Their repeating five-day work cycle prefers farming on one day and mining on the other four.
- Mining encampments generate independently across overworld biomes instead of inside settlements. Each site has 10,000 resource points, displays its remaining stock above the work face, and pays out configurable coal, raw iron, and raw gold drops. Citizens select registered encampments, travel to them, and deplete their stock as they mine.
- Each home has sealed household storage with nine general ItemStack slots. Citizens deposit wheat and mined materials there. Survival players and hoppers cannot access it; creative players can open it for inspection and editing. Citizen deposits and withdrawals check household membership.
- Once per Minecraft day, a household consumes one stored food item per member (wheat, bread, or an item with Minecraft's food component). New settlement households start with eight bread items and 100 coins. Up to four stored wheat items above a reserve of 32 are sold for one coin each per day. Personal need consumption is an additional operation.
- A household food shortage damages each of its citizens once when the daily feeding cycle records it, with damage capped at four health points per day.
- Citizens have persistent Farming, Mining, Bakery, and Building skills. Successful farming, mining and baking cycles give five experience; each skill level speeds its work by a factor of 1.02. Building is stored for future systems.
- Citizens travel using a distance-based countdown of 10–200 server ticks, then change logical position. A visual appears at a safe location when the destination is entity-ticking. Travel and work do not force chunks to load.
- Citizens have persistent Eat, Entertainment, and Safety urgency needs that grow each Minecraft day. They consume household-stored items when a need reaches 50; apples and bread provide stronger Eat satisfaction, while other items use the generic satisfaction values.
- Each generated settlement has a central market. Citizens may visit once per day and post or buy one-for-one barter offers using household storage. Trades favor goods that better satisfy the buyer's current needs; no coins are used for these trades. Market offers and mine stock are backend records and continue to operate while their blocks are unloaded.
- Citizens compare eligible actions by net need utility per work/travel tick. The five-day work rotation gives scheduled work a 5% preference; sleep, required deliveries and urgent market visits have priority scores. Baking converts three carried wheat into one bread after 1,200 base work ticks.
- Households can buy an unowned parcel or one offered by a different household for 60 coins. A household offers land only when it owns more than one parcel and has at least 16 stored food items, so its final farm remains protected. Households seek more land when food is low or savings reach 150 coins. Land deeds, storage contents, coins, and the last day's shortage persist with the world; citizen profiles show household food, coins, and parcel count.
- Stand in a farm parcel and run `/ew land` to see its current owner or whether it is unowned.

### Not implemented yet

Farming currently covers wheat in newly generated settlement parcels; manually spawned households and homes generated before this update have no new deed. Broader production, contracts, blueprints, player-founded countries, and armies are future systems.

## Run and build

See [development troubleshooting](docs/troubleshooting.md) for fixes to world-generation issues encountered during development.

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
