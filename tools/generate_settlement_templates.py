"""Generate the two compact structure templates used by the settlement jigsaw."""

from __future__ import annotations

import gzip
import struct
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/data/economistwars/structure/settlement"
DATA_VERSION = 5023  # Minecraft 26.3, read from its bundled village templates.


def utf(value: str) -> bytes:
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def tag(tag_type: int, name: str, payload: bytes) -> bytes:
    return bytes((tag_type,)) + utf(name) + payload


def string_tag(name: str, value: str) -> bytes:
    return tag(8, name, utf(value))


def int_tag(name: str, value: int) -> bytes:
    return tag(3, name, struct.pack(">i", value))


def int_array_tag(name: str, values: tuple[int, ...]) -> bytes:
    return tag(11, name, struct.pack(">i", len(values)) + struct.pack(">" + "i" * len(values), *values))


def compound_payload(children: list[bytes]) -> bytes:
    return b"".join(children) + b"\x00"


def compound_tag(name: str, children: list[bytes]) -> bytes:
    return tag(10, name, compound_payload(children))


def list_tag(name: str, element_type: int, values: list[bytes]) -> bytes:
    return tag(9, name, bytes((element_type,)) + struct.pack(">i", len(values)) + b"".join(values))


def state(name: str, properties: dict[str, str] | None = None) -> bytes:
    children = [string_tag("Name", name)]
    if properties:
        children.append(compound_tag("Properties", [string_tag(key, value) for key, value in properties.items()]))
    return compound_payload(children)


def block(pos: tuple[int, int, int], state_index: int, nbt: list[bytes] | None = None) -> bytes:
    children = [list_tag("pos", 3, [struct.pack(">i", coordinate) for coordinate in pos]), int_tag("state", state_index)]
    if nbt is not None:
        children.append(compound_tag("nbt", nbt))
    return compound_payload(children)


def structure(size: tuple[int, int, int], palette: list[bytes], blocks: list[bytes]) -> bytes:
    root = compound_payload([
        list_tag("size", 3, [struct.pack(">i", coordinate) for coordinate in size]),
        list_tag("entities", 10, []),
        list_tag("blocks", 10, blocks),
        list_tag("palette", 10, palette),
        int_tag("DataVersion", DATA_VERSION),
    ])
    return b"\x0a" + utf("") + root


class Template:
    def __init__(self) -> None:
        self.palette: list[bytes] = []
        self.palette_ids: dict[tuple[str, tuple[tuple[str, str], ...]], int] = {}
        self.blocks: list[bytes] = []
        self.block_indexes: dict[tuple[int, int, int], int] = {}

    def add(self, pos: tuple[int, int, int], name: str, properties: dict[str, str] | None = None,
            nbt: list[bytes] | None = None) -> None:
        property_key = tuple(sorted((properties or {}).items()))
        key = (name, property_key)
        if key not in self.palette_ids:
            self.palette_ids[key] = len(self.palette)
            self.palette.append(state(name, dict(property_key) if property_key else None))
        encoded = block(pos, self.palette_ids[key], nbt)
        if pos in self.block_indexes:
            self.blocks[self.block_indexes[pos]] = encoded
        else:
            self.block_indexes[pos] = len(self.blocks)
            self.blocks.append(encoded)

    def write(self, name: str, size: tuple[int, int, int]) -> None:
        OUTPUT.mkdir(parents=True, exist_ok=True)
        path = OUTPUT / f"{name}.nbt"
        path.write_bytes(gzip.compress(structure(size, self.palette, self.blocks), mtime=0))
        print(f"Wrote {path.relative_to(ROOT)} ({len(self.blocks)} blocks)")


def jigsaw(name: str, target: str, pool: str) -> list[bytes]:
    return [
        string_tag("id", "minecraft:jigsaw"),
        string_tag("name", name),
        string_tag("target", target),
        string_tag("pool", pool),
        string_tag("final_state", "minecraft:air"),
        string_tag("joint", "rollable"),
    ]


def create_start() -> None:
    template = Template()
    # Jigsaw connectors sit on the settlement's ground layer (relative Y=0),
    # like the connectors in vanilla village meeting-point templates.
    size = 35
    for x in range(size):
        for y in range(1, 6):
            for z in range(size):
                template.add((x, y, z), "minecraft:air")
    for x in range(size):
        for z in range(size):
            material = "minecraft:coarse_dirt" if x == 17 or z == 17 else "minecraft:grass_block"
            template.add((x, 0, z), material)
    template.add((17, 1, 17), "minecraft:oak_planks")
    template.add((17, 2, 17), "minecraft:cobblestone")
    template.add((17, 3, 17), "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})

    for connector_x, connector_z in ((7, 11), (27, 11), (17, 31)):
        template.add(
            (connector_x, 0, connector_z),
            "minecraft:jigsaw",
            {"orientation": "north_up"},
            jigsaw("economistwars:street", "economistwars:house", "economistwars:settlement/homes"),
        )
    template.write("start", (size, 6, size))


def add_home(template: Template, origin_x: int, origin_z: int) -> None:
    # Explicit air clears the interior and roof volume on uneven terrain.
    for x in range(9):
        for y in range(1, 5):
            for z in range(9):
                template.add((origin_x + x, y, origin_z + z), "minecraft:air")
    for x in range(9):
        for z in range(9):
            template.add((origin_x + x, 0, origin_z + z), "minecraft:oak_planks")
            template.add((origin_x + x, 4, origin_z + z), "minecraft:spruce_planks")

    for y in range(1, 4):
        for offset in range(9):
            for x, z in ((0, offset), (8, offset), (offset, 0), (offset, 8)):
                if z == 8 and x == 4:
                    continue  # Leave the south-facing doorway open.
                is_corner = (x in (0, 8) and z in (0, 8))
                if is_corner:
                    template.add((origin_x + x, y, origin_z + z), "minecraft:oak_log", {"axis": "y"})
                elif y == 2 and ((x in (0, 8) and z in (3, 5)) or (z in (0, 8) and x in (3, 5))):
                    template.add((origin_x + x, y, origin_z + z), "minecraft:glass")
                else:
                    template.add((origin_x + x, y, origin_z + z), "minecraft:oak_planks")

    anchor_nbt = [
        string_tag("id", "economistwars:household_home"),
        int_tag("x", origin_x + 4),
        int_tag("y", 1),
        int_tag("z", origin_z + 4),
    ]
    template.add((origin_x + 4, 1, origin_z + 4), "economistwars:household_home", nbt=anchor_nbt)


def create_home() -> None:
    template = Template()
    add_home(template, 0, 0)
    template.add((4, 0, 8), "minecraft:jigsaw", {"orientation": "south_up"},
                 jigsaw("economistwars:house", "minecraft:empty", "minecraft:empty"))
    template.write("home", (9, 6, 9))


if __name__ == "__main__":
    create_start()
    create_home()
