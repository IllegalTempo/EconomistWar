# Economist Wars

Economist Wars is an early-stage Fabric mod for Minecraft Java Edition. The current playable slice establishes persistent citizens and households as a foundation for a future local economy.

## Current progress

### Implemented

- Persistent citizen entities with generated names, sex, stable IDs, and one of four bundled player-style skins.
- Persistent household records with citizen membership.
- Naturally generated Economist Wars settlements in plains, sunflower plains, and meadow biomes. Each settlement has three household homes, and each home spawns a husband-and-wife pair sharing one household record. Settlements appear in newly generated chunks.
- The operator command `/ew household <count>` remains available to create a household of 1–16 citizens near the player. For example, `/ew household 4` creates a four-citizen household. The command requires gamemaster command permission.
- Interacting with a citizen opens a profile screen showing their name, sex, citizen ID, skin, household ID, and household size. Citizens without a valid household are shown as unassigned.
- Citizen identity and household membership are saved with the world and restored when it is loaded again.

### Not implemented yet

Citizens do not naturally spawn in vanilla villages and do not yet have custom work, needs, production, or trading behavior. Contracts, blueprints, player-founded countries, currency, and armies are future systems.

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

The intended economy grows from connected households in settlements: citizens will farm, meet household needs, and trade surplus goods with neighbors and players. Later systems may add contracts for goods, services, and construction from blueprints, followed by player-founded countries and their economies.
