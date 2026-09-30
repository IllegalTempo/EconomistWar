# Development troubleshooting

## Mining encampment cannot be found with `/locate`

Use `"size": 1` in `data/economistwars/worldgen/structure/mining_encampment.json`. With `"size": 0`, `/locate structure economistwars:mining_encampment` failed to find the encampment; changing the jigsaw size to `1` resolved the issue.

After changing world-generation resources, rebuild/reload them and test in newly generated terrain. A locate failure means no eligible generated start was found within the search area; compare the structure definition, structure set, biome tag, and start pool with a working structure when diagnosing similar issues.
