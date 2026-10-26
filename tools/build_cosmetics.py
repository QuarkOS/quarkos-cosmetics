#!/usr/bin/env python3
"""Build tapered cosmetics-model-1 meshes and shaded 16x16 textures.

Model space matches the client: (8, 8, 8) is the body-part pivot, +Y is up,
+Z is the player's back, and one unit is one player pixel. Head top is y=16.
Body back is z=10. Rotations stay inside -64..64 degrees because the gallery
rejects anything outside that box.
"""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
COSMETICS = ROOT / "src/main/resources/assets/quarkos_cosmetics/cosmetics"
TEXTURES = ROOT / "src/main/resources/assets/quarkos_cosmetics/textures/cosmetics"
PREVIEWS = Path("/tmp/cosmetic-previews")

FACES = ("north", "south", "east", "west", "up", "down")


def clamp(value: int) -> int:
    return max(0, min(255, value))


def mix(color: tuple[int, int, int], toward: tuple[int, int, int], amount: float) -> tuple[int, int, int]:
    return tuple(clamp(int(color[i] + (toward[i] - color[i]) * amount)) for i in range(3))


def shade(color: tuple[int, int, int], delta: int) -> tuple[int, int, int]:
    return tuple(clamp(channel + delta) for channel in color)


class Atlas:
    def __init__(self) -> None:
        self.image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        self.x = 0
        self.y = 0
        self.row_h = 0
        self.tiles: dict[str, tuple[int, int, int, int]] = {}

    def alloc(self, name: str, width: int, height: int) -> tuple[int, int, int, int]:
        if name in self.tiles:
            return self.tiles[name]
        if self.x + width > 16:
            self.x = 0
            self.y += self.row_h
            self.row_h = 0
        if self.y + height > 16:
            raise RuntimeError(f"16x16 atlas cannot fit {name} ({width}x{height}) at {self.x},{self.y}")
        box = (self.x, self.y, self.x + width, self.y + height)
        self.x += width
        self.row_h = max(self.row_h, height)
        self.tiles[name] = box
        return box

    def paint(self, name: str, width: int, height: int, base: tuple[int, int, int], marker: tuple[int, int, int] | None = None) -> tuple[int, int, int, int]:
        box = self.alloc(name, width, height)
        x0, y0, x1, y1 = box
        for y in range(height):
            for x in range(width):
                tone = base
                if y == 0 or x == 0:
                    tone = shade(base, 28)
                elif y == height - 1 or x == width - 1:
                    tone = shade(base, -32)
                elif (x + y) % 3 == 0:
                    tone = shade(base, 12)
                elif (x * 2 + y) % 5 == 0:
                    tone = shade(base, -14)
                self.image.putpixel((x0 + x, y0 + y), tone + (255,))
        exact = marker if marker is not None else base
        if width >= 2 and height >= 2:
            mx = x0 + width // 2 - 1
            my = y0 + max(0, height // 2 - 1)
            for dy in range(2):
                for dx in range(2):
                    if mx + dx < x1 and my + dy < y1:
                        self.image.putpixel((mx + dx, my + dy), exact + (255,))
        return box

    def stamp(self, name: str, pixels: list[str]) -> tuple[int, int, int, int]:
        height = len(pixels)
        width = max(len(row) for row in pixels)
        box = self.alloc(name, width, height)
        x0, y0, _, _ = box
        for y, row in enumerate(pixels):
            for x, cell in enumerate(row):
                if cell == ".":
                    continue
                self.image.putpixel((x0 + x, y0 + y), PALETTE[cell] + (255,))
        return box

    def uv(self, name: str) -> list[int]:
        x0, y0, x1, y1 = self.tiles[name]
        return [x0, y0, x1, y1]

    def save(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        self.image.save(path)


# Single-character stamps. Letters are local to a texture's stamp call.
PALETTE: dict[str, tuple[int, int, int]] = {}


def use_palette(colors: dict[str, tuple[int, int, int]]) -> None:
    PALETTE.clear()
    PALETTE.update(colors)


class Mesh:
    def __init__(self, atlas: Atlas) -> None:
        self.atlas = atlas
        self.elements: list[dict] = []

    def cube(
        self,
        name: str,
        x0: float,
        y0: float,
        z0: float,
        x1: float,
        y1: float,
        z1: float,
        tile: str,
        origin: tuple[float, float, float] | None = None,
        rotation: tuple[float, float, float] = (0, 0, 0),
        faces: dict[str, str] | None = None,
    ) -> None:
        if x0 > x1:
            x0, x1 = x1, x0
        if y0 > y1:
            y0, y1 = y1, y0
        if z0 > z1:
            z0, z1 = z1, z0
        if abs(x1 - x0) < 0.05 and abs(y1 - y0) < 0.05 and abs(z1 - z0) < 0.05:
            raise RuntimeError(f"{name} has no extent")
        for value in (x0, y0, z0, x1, y1, z1, *rotation, *((origin or (0, 0, 0)))):
            if value < -64 or value > 64:
                raise RuntimeError(f"{name} coordinate {value} is outside -64..64")
        if origin is None:
            origin = ((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2)
        face_tiles = {face: tile for face in FACES}
        if faces:
            face_tiles.update(faces)
        self.elements.append(
            {
                "name": name,
                "from": [round(x0, 2), round(y0, 2), round(z0, 2)],
                "to": [round(x1, 2), round(y1, 2), round(z1, 2)],
                "origin": [round(origin[0], 2), round(origin[1], 2), round(origin[2], 2)],
                "rotation": [round(rotation[0], 2), round(rotation[1], 2), round(rotation[2], 2)],
                "faces": {
                    face: {"uv": self.atlas.uv(face_tiles[face]), "texture": 0}
                    for face in FACES
                },
            }
        )

    def save(self, path: Path) -> None:
        if not 6 <= len(self.elements) <= 64:
            raise RuntimeError(f"{path.name} has {len(self.elements)} cubes")
        path.write_text(json.dumps({"format": "cosmetics-model-1", "elements": self.elements}, indent=2) + "\n")


def save_pair(stem: str, atlas: Atlas, mesh: Mesh) -> None:
    atlas.save(TEXTURES / f"{stem}.png")
    mesh.save(COSMETICS / f"{stem}.json")
    preview(stem, mesh, atlas)


def preview(stem: str, mesh: Mesh, atlas: Atlas) -> None:
    image = Image.new("RGB", (360, 280), (28, 30, 34))
    # Ghost of the player head and torso so scale is obvious.
    ghost = Image.new("RGBA", image.size, (0, 0, 0, 0))
    draw_box(ghost, (4, 8, 4), (12, 16, 12), (70, 78, 92))
    draw_box(ghost, (4, -4, 6), (12, 8, 10), (60, 68, 80))
    image.paste(ghost, mask=ghost)
    cubes = []
    for element in mesh.elements:
        depth = element["from"][2] + element["to"][2]
        cubes.append((depth, element))
    for _, element in sorted(cubes, key=lambda item: item[0]):
        tile = element["faces"]["south"]["uv"]
        color = atlas.image.getpixel(((tile[0] + tile[2]) // 2, (tile[1] + tile[3]) // 2))
        draw_box(image, element["from"], element["to"], color[:3])
    PREVIEWS.mkdir(parents=True, exist_ok=True)
    image.save(PREVIEWS / f"{stem}.png")


def draw_box(image: Image.Image, start: list[float], end: list[float], color: tuple[int, int, int]) -> None:
    points = []
    for x in (start[0], end[0]):
        for y in (start[1], end[1]):
            for z in (start[2], end[2]):
                points.append(project(x, y, z))
    xs = [point[0] for point in points]
    ys = [point[1] for point in points]
    x0, x1 = int(min(xs)), int(max(xs))
    y0, y1 = int(min(ys)), int(max(ys))
    fill = shade(color, -8)
    edge = shade(color, 36)
    for y in range(max(0, y0), min(image.height, y1 + 1)):
        for x in range(max(0, x0), min(image.width, x1 + 1)):
            image.putpixel((x, y), fill if 0 < x - x0 < x1 - x0 and 0 < y - y0 < y1 - y0 else edge)


def project(x: float, y: float, z: float) -> tuple[float, float]:
    sx = 150 + (x - z) * 7.2
    sy = 168 - y * 7.4 + (x + z) * 2.1
    return sx, sy


def build_lantern() -> None:
    marker = (255, 51, 102)
    atlas = Atlas()
    atlas.paint("paper", 4, 6, (232, 186, 122))
    atlas.paint("paper_dark", 4, 6, (176, 112, 62))
    atlas.paint("gold", 3, 4, (232, 184, 74))
    atlas.paint("glow", 4, 4, (255, 92, 78), marker)
    atlas.paint("rib", 2, 6, (92, 58, 36))
    mesh = Mesh(atlas)
    # Brim is a ring, not a solid slab.
    mesh.cube("brim_front", 3.2, 16.0, 3.1, 12.8, 16.7, 5.6, "paper_dark")
    mesh.cube("brim_back", 3.2, 16.0, 10.4, 12.8, 16.7, 12.9, "paper_dark")
    mesh.cube("brim_left", 3.2, 16.0, 5.4, 5.4, 16.7, 10.6, "paper")
    mesh.cube("brim_right", 10.6, 16.0, 5.4, 12.8, 16.7, 10.6, "paper")
    mesh.cube("shade_lower", 5.3, 16.7, 5.3, 10.7, 20.4, 10.7, "paper")
    mesh.cube("shade_mid", 5.9, 20.2, 5.9, 10.1, 23.1, 10.1, "paper")
    mesh.cube("shade_upper", 6.5, 22.9, 6.5, 9.5, 25.0, 9.5, "paper_dark")
    mesh.cube("cap", 6.9, 24.8, 6.9, 9.1, 26.0, 9.1, "gold")
    mesh.cube("finial", 7.5, 25.8, 7.5, 8.5, 27.5, 8.5, "gold")
    mesh.cube("glow", 6.7, 18.0, 4.85, 9.3, 21.2, 5.45, "glow", faces={"north": "glow", "south": "glow"})
    mesh.cube("rib_left", 5.15, 16.9, 7.6, 5.55, 23.4, 8.4, "rib")
    mesh.cube("rib_right", 10.45, 16.9, 7.6, 10.85, 23.4, 8.4, "rib")
    save_pair("lantern_cap", atlas, mesh)


def build_paper_boat() -> None:
    atlas = Atlas()
    atlas.paint("hull", 4, 5, (214, 196, 160))
    atlas.paint("hull_dark", 3, 4, (156, 124, 86))
    atlas.paint("sail", 4, 6, (244, 236, 214))
    atlas.paint("mast", 2, 5, (120, 86, 52))
    atlas.paint("mark", 2, 2, (255, 177, 90))
    mesh = Mesh(atlas)
    mesh.cube("keel", 4.4, 16.0, 5.2, 11.6, 16.7, 11.4, "hull_dark")
    mesh.cube("hull_mid", 3.6, 16.6, 4.6, 12.4, 18.2, 11.8, "hull")
    mesh.cube("bow", 5.2, 16.7, 3.2, 10.8, 18.0, 5.0, "hull")
    mesh.cube("stern", 5.0, 16.8, 11.4, 11.0, 18.4, 13.0, "hull_dark")
    mesh.cube("cabin", 6.4, 18.0, 7.2, 9.6, 19.6, 10.2, "hull_dark")
    mesh.cube("mast", 7.7, 18.2, 7.6, 8.3, 23.4, 8.4, "mast")
    mesh.cube("sail_idle", 8.2, 19.2, 6.6, 8.7, 23.0, 10.2, "sail", origin=(8.2, 19.2, 8.0), rotation=(-18, 0, 0))
    mesh.cube("sail_back_idle", 7.3, 19.6, 7.8, 7.8, 22.6, 10.6, "sail", origin=(8.2, 19.2, 8.0), rotation=(16, 0, 0))
    mesh.cube("flag", 8.2, 22.6, 7.2, 9.8, 23.6, 8.0, "mark")
    save_pair("paper_boat", atlas, mesh)


def build_circlet() -> None:
    atlas = Atlas()
    atlas.paint("brass", 3, 3, (212, 176, 96))
    atlas.paint("brass_dark", 2, 3, (140, 104, 48))
    atlas.paint("moon", 3, 4, (244, 226, 176))
    atlas.paint("gem", 2, 2, (244, 226, 176))
    mesh = Mesh(atlas)
    # Open crescent sitting on the hair, charm at the brow.
    mesh.cube("band_left", 3.4, 15.2, 4.6, 5.0, 16.2, 10.6, "brass")
    mesh.cube("band_right", 11.0, 15.2, 4.6, 12.6, 16.2, 10.6, "brass")
    mesh.cube("band_back", 4.8, 15.4, 10.2, 11.2, 16.3, 11.6, "brass_dark")
    mesh.cube("band_front_left", 4.6, 15.5, 3.8, 6.6, 16.4, 5.0, "brass")
    mesh.cube("band_front_right", 9.4, 15.5, 3.8, 11.4, 16.4, 5.0, "brass")
    mesh.cube("crescent_outer", 6.4, 16.2, 3.3, 9.6, 18.8, 4.3, "moon")
    mesh.cube("crescent_inner", 7.2, 16.6, 3.05, 8.8, 18.2, 3.7, "brass_dark")
    mesh.cube("charm", 7.4, 15.0, 3.15, 8.6, 16.2, 4.15, "gem")
    save_pair("crescent_circlet", atlas, mesh)


def ear(mesh: Mesh, side: str, origin_x: float, tilt: float) -> None:
    origin = (origin_x, 15.4, 7.2)
    sign = 1 if origin_x < 8 else -1
    # Tapered outer ear, then a smaller inner-ear shell in front of it.
    spans = (
        ("base", 0.0, 1.7, 1.55, 1.15),
        ("mid", 1.5, 3.5, 1.15, 0.9),
        ("tip", 3.3, 5.5, 0.72, 0.55),
    )
    for name, y0, y1, half_x, half_z in spans:
        mesh.cube(
            f"{side}_{name}",
            origin_x - half_x,
            15.4 + y0,
            7.2 - half_z,
            origin_x + half_x,
            15.4 + y1,
            7.2 + half_z,
            "outer" if name != "tip" else "outer_dark",
            origin=origin,
            rotation=(0, 0, tilt),
        )
    mesh.cube(
        f"{side}_inner",
        origin_x - 0.85,
        16.0,
        5.55,
        origin_x + 0.85,
        18.6,
        6.55,
        "inner",
        origin=origin,
        rotation=(0, 0, tilt),
    )
    mesh.cube(
        f"{side}_inner_tip",
        origin_x - 0.45,
        18.3,
        5.85,
        origin_x + 0.45,
        20.0,
        6.55,
        "inner_deep",
        origin=origin,
        rotation=(0, 0, tilt),
    )
    mesh.cube(
        f"{side}_tuft",
        origin_x - 0.35 * sign,
        20.2,
        6.7,
        origin_x + 0.55,
        21.3,
        7.5,
        "outer",
        origin=origin,
        rotation=(0, 0, tilt),
    )


def build_fox() -> None:
    marker = (51, 221, 136)
    atlas = Atlas()
    atlas.paint("outer", 4, 6, (196, 106, 48))
    atlas.paint("outer_dark", 3, 4, (122, 58, 28))
    atlas.paint("inner", 3, 5, (244, 176, 176))
    atlas.paint("inner_deep", 2, 3, (196, 104, 120))
    atlas.paint("band", 3, 3, marker)
    mesh = Mesh(atlas)
    ear(mesh, "left", 4.3, 16)
    ear(mesh, "right", 11.7, -16)
    mesh.cube("band_left", 3.3, 15.1, 6.2, 5.5, 16.0, 8.2, "band")
    mesh.cube("band_right", 10.5, 15.1, 6.2, 12.7, 16.0, 8.2, "band")
    save_pair("fox_tips", atlas, mesh)


def build_moth_tufts() -> None:
    atlas = Atlas()
    atlas.paint("fluff", 4, 4, (176, 214, 176))
    atlas.paint("fluff_dark", 3, 3, (92, 140, 112))
    atlas.paint("antenna", 2, 4, (70, 86, 64))
    atlas.paint("mark", 2, 2, (125, 223, 176))
    mesh = Mesh(atlas)
    for side, x, tilt in (("left", 3.8, 14), ("right", 12.2, -14)):
        origin = (x, 15.6, 6.4)
        mesh.cube(f"{side}_base", x - 1.3, 15.2, 5.4, x + 1.3, 17.0, 7.8, "fluff", origin=origin, rotation=(0, 0, tilt))
        mesh.cube(f"{side}_mid", x - 0.9, 16.8, 5.7, x + 0.9, 18.6, 7.4, "fluff", origin=origin, rotation=(0, 0, tilt))
        mesh.cube(f"{side}_tip", x - 0.45, 18.4, 6.0, x + 0.45, 19.8, 7.0, "fluff_dark", origin=origin, rotation=(0, 0, tilt))
        mesh.cube(f"{side}_antenna", x - 0.15, 17.6, 4.8, x + 0.2, 20.4, 5.2, "antenna", origin=origin, rotation=(-20, 0, tilt))
    mesh.cube("spot_left", 3.1, 15.6, 5.15, 4.3, 16.6, 5.55, "mark")
    mesh.cube("spot_right", 11.7, 15.6, 5.15, 12.9, 16.6, 5.55, "mark")
    save_pair("moth_tufts", atlas, mesh)


def bow(mesh: Mesh, side: str, x: float) -> None:
    origin = (x, 14.6, 4.6)
    tilt = 18 if x < 8 else -18
    mesh.cube(f"{side}_knot", x - 0.55, 14.2, 4.1, x + 0.55, 15.4, 5.3, "knot")
    mesh.cube(f"{side}_loop_a", x - 0.4, 14.8, 4.2, x + 1.5, 16.6, 5.0, "ribbon", origin=origin, rotation=(0, 0, tilt))
    mesh.cube(f"{side}_loop_b", x - 1.5, 14.8, 4.3, x + 0.4, 16.5, 5.1, "ribbon", origin=origin, rotation=(0, 0, -tilt))
    mesh.cube(f"{side}_tail_a_idle", x - 0.35, 12.2, 4.4, x + 0.45, 14.4, 5.2, "ribbon_dark", origin=origin, rotation=(18, 0, 8))
    mesh.cube(f"{side}_tail_b_idle", x - 0.2, 12.4, 5.0, x + 0.55, 14.3, 5.7, "ribbon", origin=origin, rotation=(24, 0, -10))


def build_bows() -> None:
    atlas = Atlas()
    atlas.paint("ribbon", 4, 5, (232, 140, 176))
    atlas.paint("ribbon_dark", 3, 4, (168, 78, 116))
    atlas.paint("knot", 2, 2, (242, 163, 199))
    mesh = Mesh(atlas)
    bow(mesh, "left", 3.6)
    bow(mesh, "right", 12.4)
    save_pair("ribbon_bows", atlas, mesh)


def build_wren() -> None:
    marker = (51, 136, 255)
    atlas = Atlas()
    atlas.paint("ink", 4, 5, (36, 70, 130))
    atlas.paint("ink_dark", 3, 4, (18, 36, 72))
    atlas.paint("chest", 3, 3, marker)
    atlas.paint("beak", 2, 2, (232, 176, 64))
    atlas.paint("leg", 2, 3, (92, 64, 40))
    use_palette({"w": (244, 244, 236), "d": (16, 18, 24), "k": (48, 64, 96)})
    atlas.stamp("eye", ["wd", "dk"])
    mesh = Mesh(atlas)
    # Feet rest on the left-arm shoulder (arm top is model y=10).
    idle = (9.4, 12.2, 7.6)
    mesh.cube("body_idle", 8.2, 11.0, 6.6, 11.2, 13.2, 9.0, "ink", origin=idle)
    mesh.cube("breast_idle", 8.0, 11.3, 5.9, 10.4, 12.8, 6.8, "chest", origin=idle)
    mesh.cube("head_idle", 7.5, 12.8, 5.8, 10.0, 14.8, 8.0, "ink_dark", origin=idle)
    mesh.cube("beak", 8.0, 13.2, 4.9, 9.4, 13.9, 5.9, "beak")
    mesh.cube("eye_left", 7.35, 13.5, 6.3, 7.85, 14.15, 7.1, "eye", faces={"west": "eye", "north": "eye"})
    mesh.cube("eye_right", 9.65, 13.5, 6.3, 10.15, 14.15, 7.1, "eye", faces={"east": "eye", "north": "eye"})
    mesh.cube("wing_left_flap", 10.9, 11.4, 6.9, 12.2, 12.8, 8.6, "ink_dark", origin=(10.9, 12.0, 7.6), rotation=(0, 0, -36))
    mesh.cube("wing_right_flap", 7.0, 11.4, 6.9, 8.3, 12.8, 8.6, "ink_dark", origin=(8.3, 12.0, 7.6), rotation=(0, 0, 36))
    mesh.cube("tail_a", 8.6, 11.2, 8.8, 10.6, 12.2, 11.2, "ink", origin=idle, rotation=(28, 0, 0))
    mesh.cube("tail_b", 9.0, 11.5, 10.4, 10.2, 12.0, 12.0, "ink_dark", origin=idle, rotation=(36, 0, 0))
    mesh.cube("leg_left", 8.7, 10.15, 7.1, 9.15, 11.2, 7.55, "leg")
    mesh.cube("leg_right", 10.15, 10.15, 7.1, 10.6, 11.2, 7.55, "leg")
    mesh.cube("foot_left", 8.45, 10.05, 6.3, 9.35, 10.4, 7.3, "leg")
    mesh.cube("foot_right", 9.95, 10.05, 6.3, 10.85, 10.4, 7.3, "leg")
    save_pair("ink_wren", atlas, mesh)


def build_moss() -> None:
    atlas = Atlas()
    atlas.paint("stone", 4, 4, (120, 124, 112))
    atlas.paint("stone_dark", 3, 3, (72, 76, 68))
    atlas.paint("moss", 4, 3, (94, 140, 74))
    atlas.paint("strap", 2, 3, (92, 64, 40))
    atlas.paint("mark", 2, 2, (94, 140, 74))
    mesh = Mesh(atlas)
    mesh.cube("pebble_lower", 6.6, 10.3, 6.2, 10.6, 12.0, 9.4, "stone_dark")
    mesh.cube("pebble_mid", 6.2, 11.6, 5.9, 10.9, 13.6, 9.6, "stone")
    mesh.cube("pebble_top", 7.0, 13.2, 6.4, 10.2, 14.4, 9.0, "stone")
    mesh.cube("moss_cap", 6.6, 13.6, 6.2, 10.6, 14.8, 9.2, "moss")
    mesh.cube("moss_side", 6.3, 12.2, 5.7, 8.2, 13.6, 6.5, "mark")
    mesh.cube("strap", 7.6, 10.6, 5.6, 9.2, 11.3, 9.8, "strap")
    save_pair("moss_pebble", atlas, mesh)


def build_moth() -> None:
    marker = (255, 204, 34)
    atlas = Atlas()
    atlas.paint("body", 3, 5, (92, 78, 64))
    atlas.paint("wing", 4, 5, (214, 196, 150))
    atlas.paint("spot", 3, 3, marker)
    atlas.paint("antenna", 2, 4, (48, 42, 36))
    use_palette({"w": (250, 250, 240), "d": (24, 18, 16), "k": (80, 60, 40)})
    atlas.stamp("eye", ["wd", "dk"])
    atlas.paint("leg", 2, 3, (64, 52, 42))
    mesh = Mesh(atlas)
    # Right-arm shoulder. Arm top is y=10, arm occupies about x=5..9.
    idle = (7.0, 12.0, 7.4)
    mesh.cube("body_idle", 6.3, 10.8, 6.8, 7.7, 13.4, 8.6, "body", origin=idle)
    mesh.cube("head_idle", 6.15, 13.1, 6.5, 7.55, 14.5, 8.2, "body", origin=idle)
    mesh.cube("eye_left", 5.95, 13.5, 6.7, 6.35, 14.05, 7.3, "eye", faces={"west": "eye"})
    mesh.cube("eye_right", 7.35, 13.5, 6.7, 7.75, 14.05, 7.3, "eye", faces={"east": "eye"})
    mesh.cube("antenna_left", 6.2, 14.2, 6.2, 6.45, 16.2, 6.5, "antenna", origin=(6.3, 14.2, 6.4), rotation=(-24, 0, 18))
    mesh.cube("antenna_right", 7.25, 14.2, 6.2, 7.5, 16.2, 6.5, "antenna", origin=(7.4, 14.2, 6.4), rotation=(-24, 0, -18))
    mesh.cube("wing_left_flap", 7.6, 11.4, 6.6, 11.2, 13.6, 7.5, "wing", origin=(7.6, 12.2, 7.2), rotation=(0, 18, -42))
    mesh.cube("wing_right_flap", 2.8, 11.4, 6.6, 6.4, 13.6, 7.5, "wing", origin=(6.4, 12.2, 7.2), rotation=(0, -18, 42))
    mesh.cube("wing_lower_left_flap", 7.5, 10.6, 7.3, 10.4, 12.2, 8.1, "spot", origin=(7.5, 11.6, 7.6), rotation=(0, 12, -28))
    mesh.cube("wing_lower_right_flap", 3.6, 10.6, 7.3, 6.5, 12.2, 8.1, "spot", origin=(6.5, 11.6, 7.6), rotation=(0, -12, 28))
    mesh.cube("leg_a", 6.4, 10.15, 7.2, 6.75, 11.0, 7.55, "leg")
    mesh.cube("leg_b", 7.15, 10.15, 7.2, 7.5, 11.0, 7.55, "leg")
    mesh.cube("foot_a", 6.25, 10.05, 6.5, 6.9, 10.35, 7.4, "leg")
    mesh.cube("foot_b", 7.0, 10.05, 6.5, 7.65, 10.35, 7.4, "leg")
    save_pair("pebble_moth", atlas, mesh)


def build_bell() -> None:
    atlas = Atlas()
    atlas.paint("copper", 4, 5, (196, 112, 52))
    atlas.paint("copper_dark", 3, 4, (122, 64, 32))
    atlas.paint("shine", 2, 3, (232, 170, 96))
    atlas.paint("mark", 2, 2, (224, 138, 60))
    mesh = Mesh(atlas)
    mesh.cube("strap", 6.8, 13.2, 6.6, 8.4, 14.2, 8.6, "copper_dark")
    mesh.cube("crown", 6.7, 12.2, 6.7, 8.5, 13.4, 8.5, "copper")
    mesh.cube("waist", 6.3, 11.0, 6.3, 8.9, 12.4, 8.9, "copper")
    mesh.cube("lip", 5.8, 10.2, 5.8, 9.4, 11.2, 9.4, "copper_dark")
    mesh.cube("flare", 5.5, 9.7, 5.5, 9.7, 10.4, 9.7, "shine")
    mesh.cube("clapper", 7.2, 10.0, 7.2, 8.0, 11.0, 8.0, "mark")
    save_pair("copper_bell", atlas, mesh)


def wing(mesh: Mesh, side: str, origin: tuple[float, float, float], tilt: float, direction: float) -> None:
    # direction +1 builds toward +X. Folded by tilt around Z, about 14px long.
    ox, oy, oz = origin
    name = "wing_right_flap" if "right" in side else "wing_left_flap"
    steps = (
        (0.4, 3.4, -0.6, 2.4, "membrane"),
        (3.1, 7.2, -1.5, 2.8, "membrane"),
        (6.8, 10.6, -1.0, 2.2, "membrane_dark"),
        (10.2, 14.0, -0.4, 1.2, "tip"),
    )
    for index, (x0, x1, y0, y1, tile) in enumerate(steps):
        mesh.cube(
            name if index else f"{name}_root",
            ox + direction * x0,
            oy + y0,
            oz - 0.35,
            ox + direction * x1,
            oy + y1,
            oz + 0.45,
            tile,
            origin=origin,
            rotation=(0, 0, tilt),
        )
    mesh.cube(
        f"{name}_strut",
        ox + direction * 0.6,
        oy - 0.2,
        oz - 0.2,
        ox + direction * 12.4,
        oy + 0.5,
        oz + 0.25,
        "strut",
        origin=origin,
        rotation=(0, 0, tilt),
    )


def build_wings() -> None:
    marker = (204, 68, 255)
    atlas = Atlas()
    atlas.paint("membrane", 4, 6, (168, 96, 214))
    atlas.paint("membrane_dark", 3, 5, (112, 52, 156))
    atlas.paint("tip", 3, 3, (214, 160, 236))
    atlas.paint("strut", 2, 4, (74, 42, 96))
    atlas.paint("spine", 3, 6, (58, 36, 78))
    atlas.paint("jewel", 3, 3, marker)
    mesh = Mesh(atlas)
    mesh.cube("spine", 7.25, -3.2, 10.25, 8.75, 8.2, 11.35, "spine")
    mesh.cube("jewel", 7.45, 3.4, 11.2, 8.55, 5.4, 11.85, "jewel")
    # 1.2x a 12px torso, folded down against the back instead of stuck out flat.
    wing(mesh, "left", (8.35, 5.6, 10.9), -50, 1)
    wing(mesh, "right", (7.65, 5.6, 10.9), 50, -1)
    mesh.cube("kite_tail_idle", 7.6, -6.4, 10.6, 8.4, -2.6, 11.3, "tip", origin=(8.0, -2.4, 10.8), rotation=(18, 0, 0))
    save_pair("kite_wings", atlas, mesh)


def build_cloak() -> None:
    atlas = Atlas()
    atlas.paint("cloth", 4, 6, (92, 58, 122))
    atlas.paint("cloth_dark", 3, 5, (58, 36, 78))
    atlas.paint("fold", 2, 5, (124, 86, 150))
    atlas.paint("mark", 2, 2, (108, 74, 138))
    mesh = Mesh(atlas)
    hem = (8.0, 6.5, 10.6)
    mesh.cube("collar", 5.2, 6.4, 10.1, 10.8, 8.4, 11.6, "cloth_dark")
    mesh.cube("panel_left", 5.4, 1.2, 10.3, 8.2, 6.8, 11.5, "cloth", origin=hem, rotation=(8, 0, 6))
    mesh.cube("panel_right", 7.8, 1.0, 10.3, 10.6, 6.8, 11.6, "cloth", origin=hem, rotation=(8, 0, -6))
    mesh.cube("fold_ridge", 7.4, 1.4, 11.2, 8.6, 6.6, 11.8, "fold")
    mesh.cube("hem_left_idle", 5.6, -1.4, 10.5, 8.0, 1.6, 11.4, "cloth_dark", origin=hem, rotation=(16, 0, 8))
    mesh.cube("hem_right_idle", 8.0, -1.6, 10.5, 10.4, 1.4, 11.5, "cloth_dark", origin=hem, rotation=(16, 0, -8))
    mesh.cube("clasp", 7.3, 6.8, 11.35, 8.7, 8.0, 11.9, "mark")
    save_pair("folded_cloak", atlas, mesh)


def build_fan() -> None:
    atlas = Atlas()
    atlas.paint("paper", 4, 5, (236, 196, 96))
    atlas.paint("paper_dark", 3, 4, (176, 124, 48))
    atlas.paint("rib", 2, 5, (110, 72, 40))
    atlas.paint("mark", 2, 2, (242, 193, 78))
    mesh = Mesh(atlas)
    origin = (8.0, 4.0, 10.7)
    mesh.cube("pivot", 7.4, 3.2, 10.3, 8.6, 4.8, 11.5, "rib")
    folds = ((-28, -1.6), (-12, -0.6), (0, 0.0), (12, 0.6), (28, 1.6))
    for index, (yaw, shift) in enumerate(folds):
        mesh.cube(
            f"rib_{index}_idle",
            7.7 + shift,
            4.4,
            10.5,
            8.3 + shift,
            11.6,
            11.15,
            "paper" if index % 2 == 0 else "paper_dark",
            origin=origin,
            rotation=(0, yaw, 0),
        )
    mesh.cube("guard", 7.5, 10.8, 10.4, 8.5, 11.8, 11.3, "mark", origin=origin)
    save_pair("paper_fan", atlas, mesh)


def build_ribbon() -> None:
    marker = (34, 224, 224)
    atlas = Atlas()
    atlas.paint("ribbon", 4, 6, marker)
    atlas.paint("ribbon_dark", 3, 5, (16, 140, 148))
    atlas.paint("edge", 2, 4, (170, 250, 246))
    mesh = Mesh(atlas)
    knot = (8.0, 1.2, 10.7)
    mesh.cube("knot", 6.8, 0.4, 10.2, 9.2, 2.2, 11.6, "ribbon_dark")
    segments = (
        (0.2, -1.4, 10.8, 1.15, "ribbon"),
        (-1.2, -3.2, 11.3, 0.95, "ribbon"),
        (-3.0, -5.2, 11.8, 0.75, "edge"),
        (-5.0, -7.4, 12.2, 0.55, "ribbon_dark"),
        (-7.2, -9.2, 12.5, 0.38, "ribbon"),
    )
    for index, (y0, y1, z, half, tile) in enumerate(segments):
        mesh.cube(
            f"ribbon_{index}_idle",
            8.0 - half,
            y1,
            z,
            8.0 + half,
            y0,
            z + 0.7,
            tile,
            origin=knot,
            rotation=(10 + index * 4, 0, 0),
        )
    mesh.cube("fork_left_idle", 6.9, -9.8, 12.4, 7.9, -7.6, 13.1, "edge", origin=knot, rotation=(28, 0, 12))
    mesh.cube("fork_right_idle", 8.1, -9.8, 12.4, 9.1, -7.6, 13.1, "edge", origin=knot, rotation=(28, 0, -12))
    save_pair("ribbon_tail", atlas, mesh)


def build_fern() -> None:
    atlas = Atlas()
    atlas.paint("stem", 2, 5, (48, 110, 58))
    atlas.paint("leaf", 4, 4, (72, 168, 86))
    atlas.paint("leaf_dark", 3, 3, (36, 112, 64))
    atlas.paint("mark", 2, 2, (63, 174, 106))
    mesh = Mesh(atlas)
    origin = (8.0, 1.0, 10.6)
    mesh.cube("stem_idle", 7.6, -6.2, 10.5, 8.4, 1.6, 11.3, "stem", origin=origin, rotation=(12, 0, 0))
    pairs = ((0.2, 1.3), (-1.6, 1.05), (-3.4, 0.8), (-5.2, 0.55))
    for index, (y, reach) in enumerate(pairs):
        tile = "leaf" if index % 2 == 0 else "leaf_dark"
        mesh.cube(f"leaf_left_{index}_idle", 8.2, y, 10.6, 8.2 + reach + 1.4, y + 0.7, 11.5, tile, origin=origin, rotation=(8, 0, -22))
        mesh.cube(f"leaf_right_{index}_idle", 7.8 - reach - 1.4, y - 0.2, 10.7, 7.8, y + 0.5, 11.6, tile, origin=origin, rotation=(8, 0, 22))
    mesh.cube("tip_idle", 7.5, -7.6, 10.7, 8.5, -5.8, 11.6, "mark", origin=origin, rotation=(20, 0, 0))
    save_pair("fern_plume", atlas, mesh)


def build_beads() -> None:
    atlas = Atlas()
    atlas.paint("glass", 3, 3, (232, 210, 140))
    atlas.paint("glass_dark", 3, 3, (176, 140, 72))
    atlas.paint("cord", 2, 4, (96, 72, 48))
    atlas.paint("mark", 2, 2, (240, 212, 138))
    mesh = Mesh(atlas)
    origin = (8.0, 1.4, 10.5)
    mesh.cube("clasp", 7.2, 0.6, 10.2, 8.8, 2.0, 11.4, "cord")
    beads = ((-0.2, 0.7, "glass"), (-2.0, 0.6, "glass_dark"), (-3.6, 0.55, "mark"), (-5.1, 0.48, "glass"), (-6.5, 0.4, "glass_dark"))
    for index, (y, radius, tile) in enumerate(beads):
        mesh.cube(
            f"bead_{index}_idle",
            8 - radius,
            y - radius,
            11.0,
            8 + radius,
            y + radius,
            11.0 + radius * 2,
            tile,
            origin=origin,
            rotation=(8 + index * 3, 0, 0),
        )
    mesh.cube("cord_idle", 7.75, -6.8, 10.8, 8.25, 0.8, 11.3, "cord", origin=origin)
    save_pair("bead_chain", atlas, mesh)


def main() -> None:
    use_palette({})
    build_lantern()
    build_paper_boat()
    build_circlet()
    build_fox()
    build_moth_tufts()
    build_bows()
    build_wren()
    build_moss()
    build_moth()
    build_bell()
    build_wings()
    build_cloak()
    build_fan()
    build_ribbon()
    build_fern()
    build_beads()
    print("wrote", len(list(COSMETICS.glob('*.json'))) - 1, "models")


if __name__ == "__main__":
    main()
