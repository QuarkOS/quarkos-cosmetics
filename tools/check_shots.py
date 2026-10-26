#!/usr/bin/env python3
"""Count marker-colored pixels in harness screenshots."""

import sys
from pathlib import Path

from PIL import Image

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else "/cursor/stores/bc-1243ce51-36c9-58c7-8fa4-153844455530/media/fabric")

# name, rgb, minimum count. A maximum is set with a leading minus in the last field via pairs below.
CHECKS = [
    ("wardrobe-screen.png", None, None),
    ("gallery-panel.png", None, None),
    ("hat-lantern-cap.png", (0xFF, 0x33, 0x66), 8),
    ("ears-fox-tips.png", (0x33, 0xDD, 0x88), 8),
    ("shoulder-left-ink-wren.png", (0x33, 0x88, 0xFF), 8),
    ("shoulder-right-pebble-moth.png", (0xFF, 0xCC, 0x22), 8),
    ("back-kite-wings.png", (0xCC, 0x44, 0xFF), 8),
    ("back-kite-wings-flap.png", (0xCC, 0x44, 0xFF), 8),
    ("back-hidden-while-elytra.png", (0xCC, 0x44, 0xFF), 0),
    ("tail-ribbon.png", (0x22, 0xE0, 0xE0), 8),
    ("pose-sneak.png", (0xCC, 0x44, 0xFF), 8),
    ("pose-swimming.png", (0xCC, 0x44, 0xFF), 8),
    ("pose-elytra-flight.png", (0x6C, 0x4A, 0x8A), 0),
    ("skin-shell.png", (0xFF, 0x00, 0xFF), 8),
    ("hat-over-helmet.png", (0xFF, 0x33, 0x66), 8),
]


def close(pixel, color):
    return all(abs(pixel[i] - color[i]) <= 36 for i in range(3))


def count(path, color):
    image = Image.open(path).convert("RGB")
    return sum(1 for pixel in image.getdata() if close(pixel, color))


def main():
    failed = 0
    for name, color, minimum in CHECKS:
        path = ROOT / name
        if not path.is_file() or path.stat().st_size < 1000:
            print(f"FAIL {name} missing")
            failed += 1
            continue
        if color is None:
            print(f"OK   {name} {path.stat().st_size} bytes")
            continue
        found = count(path, color)
        if minimum == 0:
            ok = found < 8
            rule = f"<{8}"
        else:
            ok = found >= minimum
            rule = f">={minimum}"
        print(f"{'OK  ' if ok else 'FAIL'} {name} {color[0]:02X}{color[1]:02X}{color[2]:02X} count={found} need {rule}")
        failed += not ok
    wings = ROOT / "back-kite-wings.png"
    flap = ROOT / "back-kite-wings-flap.png"
    if wings.is_file() and flap.is_file():
        a = Image.open(wings).convert("RGB")
        b = Image.open(flap).convert("RGB")
        if a.size == b.size:
            diff = sum(1 for p, q in zip(a.getdata(), b.getdata()) if p != q)
            ok = diff > 30
            print(f"{'OK  ' if ok else 'FAIL'} flap frame difference {diff}")
            failed += not ok
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
