#!/usr/bin/env python3
"""Generate the driver's frame plan from the shipped catalog and authored clips."""
import argparse
import json
from math import ceil, lcm
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
ASSETS = HERE.parents[1] / "src/main/resources/assets/quarkos_cosmetics"
THRESHOLDS = HERE / "thresholds.json"
HEAD = {"hat", "ears"}
REAR = {"back", "tail"}
VIEWS = {"front": 0, "back": 180, "left": -90, "right": 90, "three-quarter": 40,
         "rear-three-quarter": 140}
ACTIONS = {"on_jump", "on_hit", "on_sneak", "on_unsneak", "walk"}


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def save_json(path, data):
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    tmp.replace(path)


def slug(item_id):
    return item_id.split(":", 1)[1]


def texture_duration(texture, resolution):
    meta = ASSETS / (texture + ".mcmeta")
    if not meta.is_file():
        return 0
    animation = read_json(meta)["animation"]
    default = animation.get("frametime", 1)
    if "frames" in animation:
        return sum(f.get("time", default) if isinstance(f, dict) else default
                   for f in animation["frames"])
    with Image.open(ASSETS / texture) as image:
        width = animation.get("width", resolution.get("width", image.width))
        height = animation.get("height", resolution.get("height", image.width))
        return image.width // width * (image.height // height) * default


def clips_for(item, tps):
    asset = read_json(ASSETS / item["model"])
    if item["model"].endswith(".bbmodel"):
        clips = [
            {"name": c["name"].rsplit(".", 1)[-1], "seconds": c["length"],
             "mode": c.get("loop", "once"), "source": "blockbench"}
            for c in asset.get("animations", []) if c.get("length", 0) > 0
        ]
    else:
        clips = []
    animation = {"quarkos:chick": "chick", "quarkos:kite_wings": "wings"}.get(item["id"])
    if animation:
        authored = read_json(ASSETS / f"geckolib/animations/{animation}.animation.json")["animations"]
        clips += [
            {"name": name.rsplit(".", 1)[-1], "seconds": c["animation_length"],
             "mode": "loop" if c.get("loop") is True else "once", "source": animation}
            for name, c in authored.items()
        ]
    textures = [item["texture"]]
    if item.get("glow"):
        textures.append(item["glow"])
    periods = [texture_duration(t, asset.get("resolution", {})) for t in textures]
    if item["id"] == "quarkos:jack_o_lantern_mask":
        # This renderer uses the separate candle strip and an explicit frame clock.
        glow_path = ASSETS / "textures/cosmetics/jack_o_lantern_mask_glow.png"
        with Image.open(glow_path) as glow:
            periods.append(glow.height // glow.width * 2)
    if any(periods):
        clips.append({"name": "texture", "seconds": lcm(*(p for p in periods if p)) / tps,
                      "mode": "loop", "source": "texture"})
    return clips


def camera_for(slot, view, pose="stand"):
    return f"{'head' if slot in HEAD else 'body'}-{view}-{pose}"


def make_plan(config, only):
    items = read_json(ASSETS / "cosmetics/catalog.json")["items"]
    ids = [i["id"] for i in items]
    if not ids or len(ids) != len(set(ids)):
        raise ValueError("Expected a nonempty inventory of unique catalog IDs")
    unknown = set(only) - {slug(i) for i in ids}
    if unknown:
        raise ValueError(f"Unknown cosmetic IDs {sorted(unknown)}")
    frames, models, cameras = [], [], {}
    tps = config["animation"]["ticks_per_second"]
    look_views = config["capture"]["look_views"]

    def add_camera(slot, view, pose):
        key = camera_for(slot, view, pose)
        if key not in cameras:
            cameras[key] = {"baseline": f"baselines/{key}", "plate": f"plates/{key}",
                            "yaw": VIEWS[view], "pose": pose, "head": slot in HEAD}
            for plate, path in cameras[key].items():
                if plate in ("baseline", "plate"):
                    frames.append({"path": path, "camera": key, "id": "", "slot": slot,
                                   "age": 0, "plate": plate == "plate"})
        return key

    for item in items:
        item_id, slot = item["id"], item["slot"]
        short = slug(item_id)
        if only and short not in only:
            continue
        model = {**item, "looks": {}, "clips": [],
                 "group": "blockato" if "/blockato/" in item["model"] or item_id == "quarkos:kite_wings" else "v1"}
        models.append(model)
        view = "rear-three-quarter" if slot in REAR or slot.startswith("shoulder") else "three-quarter"
        for look in dict.fromkeys([*look_views, view]):
            path = f"models/{short}/{look}"
            camera = add_camera(slot, look, "stand")
            model["looks"][look] = {"path": path, "camera": camera}
            frames.append({"path": path, "camera": camera, "id": item_id, "slot": slot, "age": 0})
        model["close_view"] = view

        clips = clips_for(item, tps)
        periods = {round(c["seconds"] * tps) for c in clips if c["mode"] == "loop"
                   and c["source"] in ("blockbench", "texture")}
        for clip in clips:
            name, source, mode = clip["name"], clip["source"], clip["mode"]
            action = name if source == "blockbench" and name in ACTIONS else ""
            pose = "sneak" if action == "on_sneak" else "stand"
            clip_camera = add_camera(slot, view, pose)
            ticks = clip["seconds"] * tps
            count = min(config["capture"]["sample_count"], max(2, ceil(ticks) + 1))
            ages = [round(ticks * i / (count - 1), 4) for i in range(count)]
            if mode == "loop" and ticks >= 1:
                ages[-1:-1] = [round(ticks - 0.02, 4)]
            if action:
                ages.insert(0, -1)
                if mode != "hold":
                    ages.append(ticks + 1)
            isolated = source in ("chick", "wings") or all(round(ticks) % p == 0 for p in periods if p)
            clip_frames = []
            for i, age in enumerate(ages):
                path = f"animation/{short}/{name}/{i:02}"
                clip_frames.append(path)
                frames.append({"path": path, "camera": clip_camera, "id": item_id, "slot": slot,
                               "age": age, "clip": name, "source": source, "action": action,
                               "perch": item_id == "quarkos:chick" and name == "sit" and i == 0})
            model["clips"].append({**clip, "action": action, "camera": clip_camera, "ticks": ages,
                                   "frames": clip_frames, "compare_seam": isolated and mode == "loop"})
    # Warm up the first camera before taking the references used in the report.
    frames.sort(key=lambda f: bool(f["id"]))
    warmup = {**frames[0], "path": "warmup"}
    frames.insert(0, warmup)
    paths = [f["path"] for f in frames]
    if len(paths) != len(set(paths)):
        raise ValueError("Duplicate capture path, check authored clip names")
    return {"schema": 1, "inventory": ids, "cameras": cameras, "models": models, "frames": frames}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("--only", default="")
    args = parser.parse_args()
    plan = make_plan(read_json(THRESHOLDS), args.only.split(",") if args.only else [])
    save_json(args.output, plan)
    print(f"PLAN_READY loaded={len(plan['inventory'])} selected={len(plan['models'])} frames={len(plan['frames'])}")


if __name__ == "__main__":
    main()
