#!/usr/bin/env python3
"""Write original cosmetic textures and cosmetics-model-1 files. No imported art."""

import json
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/quarkos_cosmetics"
TEX = ROOT / "textures/cosmetics"
MOD = ROOT / "cosmetics"


def chunk(tag: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)


def write_png(path: Path, width: int, height: int, paint) -> None:
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            raw.extend(paint(x, y))
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    path.write_bytes(png)


def hex_rgb(value: str):
    value = value.lstrip("#")
    return tuple(int(value[i : i + 2], 16) for i in (0, 2, 4))


def texture(path: Path, marker: str) -> None:
    base = hex_rgb(marker)
    light = tuple(min(255, c + 48) for c in base)
    dark = tuple(max(0, c - 55) for c in base)

    def paint(x, y):
        if x < 1 or y < 1 or x > 14 or y > 14:
            return (*dark, 255)
        if (x + y) % 6 == 0:
            return (*light, 255)
        if 6 <= x <= 9 and 6 <= y <= 9:
            return (*light, 255)
        return (*base, 255)

    write_png(path, 16, 16, paint)


def element(name, box):
    x0, y0, z0, x1, y1, z1 = box
    return {
        "name": name,
        "from": [x0, y0, z0],
        "to": [x1, y1, z1],
        "origin": [(x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2],
        "rotation": [0, 0, 0],
        "faces": {
            face: {"uv": [0, 0, 16, 16], "texture": 0}
            for face in ("north", "south", "east", "west", "up", "down")
        },
    }


ITEMS = [
    ("lantern_cap", "Lantern cap", "A folded paper lantern for the top of the head.", "hat", "FF3366", [
        ("cap", (5, 9, 5, 11, 15, 11)),
        ("brim", (2, 8, 2, 14, 10, 14)),
    ]),
    ("paper_boat", "Paper boat", "A wide paper boat that sits on the hair.", "hat", "FFB15A", [
        ("hull", (2, 8, 3, 14, 11, 13)),
        ("keel", (6, 11, 5, 10, 14, 11)),
    ]),
    ("crescent_circlet", "Crescent circlet", "A thin brass crescent, open at the front.", "hat", "F4E2B0", [
        ("band", (3, 9, 3, 13, 11, 13)),
        ("crescent", (6, 11, 2, 10, 16, 5)),
    ]),
    ("fox_tips", "Fox tips", "Short russet tips, not a copy of any ears mod.", "ears", "33DD88", [
        ("tip_left", (2, 10, 5, 5, 18, 8)),
        ("tip_right", (11, 10, 5, 14, 18, 8)),
    ]),
    ("moth_tufts", "Moth tufts", "Soft tufts that sit above the temples.", "ears", "7DDFB0", [
        ("tuft_left", (1, 9, 4, 5, 15, 9)),
        ("tuft_right", (11, 9, 4, 15, 15, 9)),
    ]),
    ("ribbon_bows", "Ribbon bows", "Two paper bows tied at the sides of the head.", "ears", "F2A3C7", [
        ("bow_left", (1, 8, 4, 6, 13, 9)),
        ("bow_right", (10, 8, 4, 15, 13, 9)),
    ]),
    ("ink_wren", "Ink wren", "A little ink-blue wren perched on the left shoulder.", "shoulder_left", "3388FF", [
        ("body", (5, 9, 5, 12, 14, 11)),
        ("beak", (11, 11, 7, 14, 13, 9)),
    ]),
    ("moss_pebble", "Moss pebble", "A mossy pebble strapped to the left shoulder.", "shoulder_left", "5E8C4A", [
        ("pebble", (4, 8, 4, 12, 14, 12)),
    ]),
    ("pebble_moth", "Pebble moth", "A moth with slow flapping wings on the right shoulder.", "shoulder_right", "FFCC22", [
        ("body", (6, 9, 6, 10, 13, 10)),
        ("wing_left_flap", (1, 10, 5, 6, 15, 7)),
        ("wing_right_flap", (10, 10, 5, 15, 15, 7)),
    ]),
    ("copper_bell", "Copper bell", "A small copper bell hung at the right shoulder.", "shoulder_right", "E08A3C", [
        ("bell", (6, 6, 6, 11, 12, 11)),
        ("clapper", (8, 4, 8, 9, 7, 9)),
    ]),
    ("kite_wings", "Kite wings", "Idle kite wings that flap while you stand.", "back", "CC44FF", [
        ("wing_left_flap", (8, 2, 10, 22, 16, 12)),
        ("wing_right_flap", (-6, 2, 10, 8, 16, 12)),
        ("spine", (7, 4, 9, 9, 14, 12)),
    ]),
    ("folded_cloak", "Folded cloak", "A short cloak gathered at the shoulder blades.", "back", "6C4A8A", [
        ("cloak", (3, 0, 10, 13, 14, 13)),
    ]),
    ("paper_fan", "Paper fan", "A paper fan that sways on the back.", "back", "F2C14E", [
        ("fan_idle", (2, 2, 10, 14, 16, 12)),
        ("rib", (7, 1, 11, 9, 15, 13)),
    ]),
    ("ribbon_tail", "Ribbon tail", "A long ribbon that idles behind the legs.", "tail", "22E0E0", [
        ("ribbon_idle", (6, -8, 10, 10, 8, 13)),
    ]),
    ("fern_plume", "Fern plume", "A fern plume that nods while you walk.", "tail", "3FAE6A", [
        ("plume_idle", (5, -6, 9, 11, 8, 14)),
    ]),
    ("bead_chain", "Bead chain", "Glass beads on a short chain.", "tail", "F0D48A", [
        ("bead_a_idle", (6, 2, 10, 10, 6, 14)),
        ("bead_b_idle", (6, -2, 10, 10, 2, 14)),
        ("bead_c", (6, -6, 10, 10, -2, 14)),
    ]),
]


def main() -> None:
    TEX.mkdir(parents=True, exist_ok=True)
    MOD.mkdir(parents=True, exist_ok=True)
    catalog = []
    for item_id, name, description, slot, marker, boxes in ITEMS:
        texture(TEX / f"{item_id}.png", marker)
        model = {"format": "cosmetics-model-1", "elements": [element(n, b) for n, b in boxes]}
        (MOD / f"{item_id}.json").write_text(json.dumps(model, indent=2) + "\n")
        catalog.append({
            "id": f"quarkos:{item_id}",
            "name": name,
            "description": description,
            "slot": slot,
            "texture": f"textures/cosmetics/{item_id}.png",
            "model": f"cosmetics/{item_id}.json",
            "marker": marker,
        })
    (MOD / "catalog.json").write_text(json.dumps({"items": catalog}, indent=2) + "\n")
    write_png(ROOT / "icon.png", 128, 128, icon_pixel)
    print(f"wrote {len(catalog)} cosmetics")


def icon_pixel(x, y):
    # Amber lantern mark on an ink ground. Original, not a Mojang texture.
    cx, cy = x - 64, y - 70
    in_lantern = abs(cx) < 28 and -36 < cy < 28 and (abs(cx) < 18 or abs(cy) < 20)
    brim = abs(cy + 8) < 8 and abs(cx) < 46 and abs(cx) > 10
    cap = abs(cx) < 16 and -48 < cy < -28
    if x < 6 or y < 6 or x > 121 or y > 121:
        return (28, 24, 20, 255)
    if in_lantern or brim or cap:
        shade = 224 if (x // 8 + y // 8) % 2 == 0 else 190
        return (shade, 110, 52, 255)
    return (28, 24, 20, 255)


if __name__ == "__main__":
    main()
