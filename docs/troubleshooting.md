# Development troubleshooting

## Mining encampment cannot be found with `/locate`

Use `"size": 1` in `data/economistwars/worldgen/structure/mining_encampment.json`. With `"size": 0`, `/locate structure economistwars:mining_encampment` failed to find the encampment; changing the jigsaw size to `1` resolved the issue.

After changing world-generation resources, rebuild/reload them and test in newly generated terrain. A locate failure means no eligible generated start was found within the search area; compare the structure definition, structure set, biome tag, and start pool with a working structure when diagnosing similar issues.
# Backend citizen simulation

After updating an existing world, visit its old settlements and mines once. Legacy citizen and block data can only be imported when encountered; imported citizen records then run independently of loaded chunks.

Citizens use registered sites. A mine in unexplored terrain is unavailable until generated and registered. If a citizen has no eligible registered work, it can remain idle or return home. Nearby visuals require an entity-ticking chunk and a safe arrival position; an obstructed arrival can hide a visual without stopping backend work.

Server shutdown and integrated-server pause stop simulation. Changing the day clock forward catches up needs but performs one household daily feeding/sales pass; it does not replay all missed work. Moving the clock backwards does not repeat already processed daily charges.

Backend death removes household membership once. Visual unload/removal does not kill a citizen. Carried products are deposited into household storage when possible; unplaced products remain in the dead citizen's persistent record rather than spawning items in unloaded chunks.

