#!/usr/bin/env python3
"""Build side-by-side pictures from two exported release reports."""
import argparse
from pathlib import Path

from PIL import Image, ImageDraw

from plan import read_json, save_json, slug


def comparison(before, after, old, new, output):
    views = ["front", "back", "left", "right", "three-quarter", "close"]
    width, height = 390, 285
    image = Image.new("RGB", (width * 2, height * len(views) + 25), "#eeeae2")
    draw = ImageDraw.Draw(image)
    for column, (name, root, model) in enumerate((("BEFORE", before, old), ("AFTER", after, new))):
        x = column * width
        draw.text((x + 7, 7), f"{name} {slug(model['id'])}", fill="black")
        for row, view in enumerate(views):
            y = row * height + 25
            draw.text((x + 7, y), view, fill="black")
            with Image.open(root / model["screenshots"][view]) as shot:
                shot.thumbnail((width - 10, height - 18), Image.Resampling.LANCZOS)
                image.paste(shot, (x + (width - shot.width) // 2, y + 15))
    path = output / (slug(new["id"]) + ".png")
    image.save(path)
    if old["animation_strip"] and new["animation_strip"]:
        strips = []
        for root, model in ((before, old), (after, new)):
            with Image.open(root / model["animation_strip"]) as shot:
                strips.append(shot.copy())
        width = max(shot.width for shot in strips)
        height = sum(shot.height + 22 for shot in strips)
        strip = Image.new("RGB", (width, height), "#eeeae2")
        draw = ImageDraw.Draw(strip)
        y = 0
        for label, shot in zip(("BEFORE", "AFTER"), strips):
            draw.text((5, y + 4), f"{label} {slug(new['id'])}", fill="black")
            strip.paste(shot, (0, y + 22))
            y += shot.height + 22
        strip.save(output / (slug(new["id"]) + "-animation.png"))
    return path.name


def action_comparison(before, after, old, new, output):
    clips = [clip for clip in new["animation"].values() if clip["action"]]
    if not clips:
        return None
    tile_w, tile_h = 370, 300
    sheet = Image.new("RGB", (tile_w * 2, tile_h * len(clips)), "#eeeae2")
    draw = ImageDraw.Draw(sheet)
    for row, clip in enumerate(clips):
        i = len(clip["frames"]) - 1 if clip["mode"] == "hold" else len(clip["frames"]) // 2
        before_clip = old["animation"][clip["name"]]
        for col, (label, directory, frame) in enumerate((("BEFORE", before, before_clip["frames"][i]),
                                                         ("AFTER", after, clip["frames"][i]))):
            x, y = col * tile_w, row * tile_h
            draw.text((x + 8, y + 6), f"{label} {clip['name']} at tick {clip['ticks'][i]:g}", fill="black")
            with Image.open(directory / frame) as source:
                source.thumbnail((tile_w - 16, tile_h - 30), Image.Resampling.LANCZOS)
                sheet.paste(source, (x + (tile_w - source.width) // 2, y + 22))
    path = output / (slug(new["id"]) + "-actions.png")
    sheet.save(path)
    return path.name


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("before", type=Path)
    parser.add_argument("after", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("ids", nargs="+")
    args = parser.parse_args()
    a = read_json(args.before / "report.json")
    b = read_json(args.after / "report.json")
    before = {slug(m["id"]): m for m in a["models"]}
    after = {slug(m["id"]): m for m in b["models"]}
    if before.keys() != after.keys() or not set(args.ids) <= after.keys():
        raise ValueError("Expected matching inventories and known model IDs")
    args.output.mkdir(parents=True, exist_ok=True)
    rows = {}
    for short in args.ids:
        old, new = before[short], after[short]
        rows[short] = {"looks": comparison(args.before, args.after, old, new, args.output),
                       "actions": action_comparison(args.before, args.after, old, new, args.output)}
    save_json(args.output / "index.json", {"before": a["commit"], "after": b["commit"], "images": rows})


if __name__ == "__main__":
    main()
