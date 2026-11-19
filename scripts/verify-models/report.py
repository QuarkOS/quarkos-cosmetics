#!/usr/bin/env python3
"""Turn the release frame plan, client log, and raw images into portable evidence."""
import argparse
from collections import Counter
from pathlib import Path
import re
import textwrap
from zipfile import ZipFile

import numpy as np
from PIL import Image, ImageDraw

from checks import (authored_motion_checks, band, bounds, change, fit_checks, flag, gap_px, properties,
                    separated_headband_gap, silhouette_checks, upper)
from plan import ASSETS, HEAD, THRESHOLDS, read_json, save_json, slug
from raster import camera_for, mannequin, read_emission, render


def frame_path(root, key):
    return root / (key + ".png")


def load_rgb(root, key):
    with Image.open(frame_path(root, key)) as image:
        return image.convert("RGB")


def save_preview(image, root, name, size):
    image = image.copy()
    image.thumbnail(size, Image.Resampling.LANCZOS)
    path = root / name
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)
    return path.relative_to(root).as_posix()


class View:
    def __init__(self, raw, camera, pixels):
        self.channel_delta = pixels["channel_delta"]
        before = load_rgb(raw, camera["baseline"])
        plate = load_rgb(raw, camera["plate"])
        if before.size != plate.size:
            raise ValueError(f"Baseline size differs from clean plate for {camera['baseline']}")
        w, h = before.size
        self.resolution = before.size
        actor = change(np.asarray(before), np.asarray(plate), self.channel_delta)
        actor[:, :w // 3] = False
        actor[:, w - w // 3:] = False
        actor_box = bounds(actor)
        if actor_box is None:
            raise ValueError(f"Player silhouette missing in {camera['baseline']}")
        x0, y0, x1, y1 = actor_box
        self.head_width = (y1 - y0) / 4
        if self.head_width < 20 or x1 - x0 > 2.2 * self.head_width and camera["pose"] == "stand":
            raise ValueError(f"Unexpected player silhouette {actor_box} in {camera['baseline']}")
        center = (x0 + x1) / 2
        crop = (max(0, int(center - self.head_width * 3.1)), max(0, int(y0 - self.head_width * 2.2)),
                min(w, int(center + self.head_width * 3.1)), min(h, int(y1 + self.head_width * 0.2)))
        self.crop = crop
        self.center = int(center - crop[0])
        self.top = y0 - crop[1]
        self.bottom = y1 - crop[1]
        self.actor = actor[crop[1]:crop[3], crop[0]:crop[2]]
        self.baseline = np.asarray(before.crop(crop))
        self.minimum_visible = max(1, round(pixels["visible_at_1920x1080_px"] * w * h / (1920 * 1080)))

    def read(self, raw, key):
        image = load_rgb(raw, key)
        if image.size != self.resolution:
            raise ValueError(f"Frame {key} changes resolution")
        cropped = image.crop(self.crop)
        array = np.asarray(cropped)
        model = change(self.baseline, array, self.channel_delta)
        # A camera or studio lighting drift also changes the clear sky at the margins.
        edge = np.concatenate((model[:, :4], model[:, -4:]), axis=1)
        return cropped, array, model, float(np.mean(edge))

    def region(self, model_slot):
        head_width = self.head_width
        actor = self.actor.copy()
        if model_slot in HEAD:
            actor[int(self.top + 1.05 * head_width):] = False
        elif model_slot == "tail":
            actor[:int(self.top + 2.0 * head_width)] = False
            actor[int(self.top + 3.1 * head_width):] = False
        else:
            actor[:int(self.top + .82 * head_width)] = False
            actor[int(self.top + 2.7 * head_width):] = False
        return actor

    def close(self, image, slot, cosmetic_box, margin):
        width, height = image.size
        y = self.top + self.head_width
        if slot == "tail":
            y += 1.6 * self.head_width
        elif slot not in HEAD:
            y += .65 * self.head_width
        anchor = (self.center - self.head_width, y - self.head_width,
                  self.center + self.head_width, y + .65 * self.head_width)
        x0, y0, x1, y1 = cosmetic_box or anchor
        tight = (max(0, int(min(x0, anchor[0]) - margin)), max(0, int(min(y0, anchor[1]) - margin)),
                 min(width, int(max(x1, anchor[2]) + margin)), min(height, int(max(y1, anchor[3]) + margin)))
        return image.crop(tight)


def motion(raw, output, model, views, config, reasons):
    pixels, rules = config["pixels"], config["animation"]
    frames, strips = {}, []
    for clip in model["clips"]:
        view = views[clip["camera"]]
        arrays, areas, previews = [], [], []
        for key in clip["frames"]:
            image, array, mask, noise = view.read(raw, key)
            if noise > pixels["background_noise_fraction"]:
                raise ValueError(f"Camera or light drift {noise:.3f} in {key}")
            arrays.append(array)
            areas.append(int(mask.sum()))
            close = view.close(image, model["slot"], bounds(mask), pixels["bbox_margin_px"])
            previews.append(save_preview(close, output, key + ".png", (360, 260)))

        diffs = [int(change(a, b, pixels["channel_delta"]).sum()) for a, b in zip(arrays, arrays[1:])]
        extent = max(1, max(areas))
        steps = np.maximum(1, np.diff(clip["ticks"]))
        start = 1 if clip["action"] == "on_unsneak" else 0
        jump = max(float(diff / (extent * step)) for diff, step in zip(diffs[start:], steps[start:]))
        if max(diffs) < pixels["minimum_motion_px"]:
            flag(reasons, "authored_motion_invisible", "mod-side", max(diffs),
                 f">= {pixels['minimum_motion_px']}", "px", clip["name"])
        if clip["source"] != "texture":
            upper(reasons, "motion_jump", "art", jump, pixels["jump_fraction_per_tick"], "fraction/tick", clip["name"])
        seam = None
        if clip["compare_seam"]:
            seam = diffs[-1] / extent
        if clip["action"] and clip["mode"] == "once":
            seam = int(change(arrays[0], arrays[-1], pixels["channel_delta"]).sum()) / extent
        if seam is not None and clip["source"] != "texture":
            upper(reasons, "clip_does_not_return_to_rest", "art", seam,
                  pixels["loop_seam_fraction"], "changed fraction", clip["name"])

        seconds = clip["seconds"]
        if clip["source"] == "blockbench" and clip["mode"] == "loop":
            if "idle" in clip["name"]:
                band(reasons, "idle_duration", "art", seconds, rules["idle_seconds_range"], "s", clip["name"])
            if not any(key in slug(model["id"]) for key in ("propeller", "drone")):
                upper(reasons, "loop_rate", "art", 1 / seconds, rules["maximum_loop_hz"], "Hz", clip["name"])
        strips.append((clip, previews))
        frames[clip["name"]] = {**clip, "frames": previews, "changed_px": diffs, "peak_jump_fraction_per_tick": round(jump, 4),
                                "rest_difference_fraction": round(seam, 4) if seam is not None else None}
    if strips:
        columns = max(len(previews) for _, previews in strips)
        strip = Image.new("RGB", (columns * 144, len(strips) * 138), "#eeeae2")
        draw = ImageDraw.Draw(strip)
        for row, (clip, previews) in enumerate(strips):
            y = row * 138
            draw.text((5, y + 3), f"{clip['name']} ({clip['mode']})", fill="black")
            for col, (path, tick) in enumerate(zip(previews, clip["ticks"])):
                with Image.open(output / path) as shot:
                    shot.thumbnail((140, 100), Image.Resampling.LANCZOS)
                    strip.paste(shot, (col * 144 + (144 - shot.width) // 2, y + 20))
                draw.text((col * 144 + 5, y + 121), f"tick {tick:g}", fill="black")
        path = f"models/{slug(model['id'])}/animation.png"
        strip.save(output / path)
        return frames, path
    return frames, None


def look_checks(raw, output, model, views, config, geometry, reasons):
    name = slug(model["id"])
    slot, group, kind = model["slot"], model["group"], properties(model)
    p, u = config["pixels"], config["units"]
    preview = config["capture"]["preview_width_px"]
    looks, stats, distances = {}, {}, []
    for label, info in model["looks"].items():
        view = views[info["camera"]]
        image, _, cosmetic, noise = view.read(raw, info["path"])
        if noise > p["background_noise_fraction"]:
            raise ValueError(f"Camera or light drift {noise:.3f} in {info['path']}")
        box = bounds(cosmetic)
        count = int(cosmetic.sum())
        looks[label] = save_preview(image, output, info["path"] + ".png", (preview, preview))
        stats[label] = {"changed_px": count, "bbox_px": box, "baseline": info["camera"]}
        print(f"MODEL_VISIBLE id={model['id']} view={label} changedPx={count} "
              f"bboxPx={box[2] - box[0]}x{box[3] - box[1]}" if box else
              f"MODEL_VISIBLE id={model['id']} view={label} changedPx=0 bboxPx=0x0")
        if label == model["close_view"]:
            close = view.close(image, slot, box, p["bbox_margin_px"])
            looks["close"] = save_preview(close, output, f"models/{name}/close.png", (preview, preview))
        if count >= view.minimum_visible:
            max_gap = round(view.head_width * p["seat_gap_head_width_fraction"])
            distance = gap_px(view.region(slot), cosmetic, max(32, max_gap * 4))
            stats[label]["seat_gap_px"] = distance
            if distance is not None:
                distances.append((distance, max_gap, label))
        if (label == "front" and slot in HEAD and group != "v1" and not kind["helmet"]
                and not kind["headband"]):
            y0 = int(view.top + .40 * view.head_width)
            y1 = int(view.top + .88 * view.head_width)
            x0 = int(view.center - .30 * view.head_width)
            x1 = int(view.center + .30 * view.head_width)
            eyes = view.actor[y0:y1, x0:x1]
            overlap = (eyes & cosmetic[y0:y1, x0:x1]).sum() / max(1, eyes.sum())
            upper(reasons, "unexpected_eye_or_face_overlay", "mod-side", overlap,
                  p["front_overlay_fraction_max"], "fraction", label)
        if label == "front" and kind["horn"] and "headband" in name:
            above_eyes = np.arange(cosmetic.shape[0])[:, None] < view.top + .5 * view.head_width
            gap = separated_headband_gap(cosmetic, above_eyes, p["minimum_component_area_fraction"])
            if gap is not None:
                upper(reasons, "detached_headband_parts", "art", gap,
                      round(view.head_width * p["headband_gap_head_width_fraction"]), "px", label)
        paired_view = "back" if kind["wing"] else "front"
        if group != "v1" and kind["paired"] and label == paired_view:
            split = view.center
            if kind["wing"]:
                cosmetic = cosmetic.copy()
                cosmetic[:, int(split - .45 * view.head_width):int(split + .45 * view.head_width)] = False
            sides = int(cosmetic[:, :split].sum()), int(cosmetic[:, split:].sum())
            ratio = min(sides) / max(1, max(sides))
            if ratio < p["pair_area_ratio_min"]:
                flag(reasons, "missing_or_asymmetric_half", "art", ratio, f">= {p['pair_area_ratio_min']}", "area ratio", label)

    largest = max(stats, key=lambda label: stats[label]["changed_px"])
    threshold = views[stats[largest]["baseline"]].minimum_visible
    if stats[largest]["changed_px"] < threshold:
        flag(reasons, "invisible_or_near_empty", "mod-side", stats[largest]["changed_px"], f">= {threshold}", "px", largest)
    if group != "v1" and not kind["floater"] and not kind["halo"] and distances:
        distance = max(distances, key=lambda result: result[0] - result[1])
        if geometry.get("seat_clearance_u", 0) > u["seat_contact_max"]:
            upper(reasons, "mount_clearance", "mod-side", geometry["seat_clearance_u"],
                  u["seat_contact_max"], "u", distance[2])
        if distance[0] > distance[1] and geometry.get("seat_clearance_u", 0) > u["seat_contact_max"]:
            upper(reasons, "floating_seat", "mod-side", distance[0], distance[1], "px", distance[2])
    return looks, stats


def text(reason):
    return (f"{reason['check'].replace('_', ' ')} measured {reason['measured']} {reason['unit']} "
            f"(limit {reason['threshold']}, {reason['evidence']})")


def write_sheet(output, models, name, tile, page_size=20):
    columns = 5
    if not models:
        sheet = Image.new("RGB", (440, 80), "#eeeae2")
        ImageDraw.Draw(sheet).text((12, 30), "No flagged cosmetics", fill="black")
        sheet.save(output / (name + ".png"))
        return
    for start in range(0, len(models), page_size):
        page = models[start:start + page_size]
        rows = (len(page) + columns - 1) // columns
        sheet = Image.new("RGB", (columns * tile, rows * (tile + 42)), "#eeeae2")
        draw = ImageDraw.Draw(sheet)
        for i, model in enumerate(page):
            x, y = (i % columns) * tile, (i // columns) * (tile + 42)
            with Image.open(output / model["screenshots"]["close"]) as image:
                image.thumbnail((tile - 10, tile - 5), Image.Resampling.LANCZOS)
                sheet.paste(image, (x + (tile - image.width) // 2, y + 5))
            draw.text((x + 5, y + tile + 2), slug(model["id"]), fill="black")
            if model["reasons"]:
                lines = textwrap.wrap(model["reasons"][0]["check"].replace("_", " "), 31)
                draw.multiline_text((x + 5, y + tile + 15), "\n".join(lines), fill="#802f27")
        suffix = f"-{start // page_size + 1:02}" if len(models) > page_size else ""
        sheet.save(output / (name + suffix + ".png"))


def finalize(output, data, tile):
    models = data["models"]
    counts = Counter(model["status"] for model in models)
    art = [m for m in models if any(r["class"] == "art" for r in m["reasons"])]
    mod = [m for m in models if any(r["class"] == "mod-side" for r in m["reasons"])]
    flags = [m for m in models if m["status"] == "FLAG"]
    data["summary"] = {"total": len(models), "pass": counts["PASS"], "flag": counts["FLAG"],
                       "art": len(art), "mod_side": len(mod),
                       "reviewed": sum(m["visual_review"] == "reviewed" for m in models),
                       "v1": sum(m["group"] == "v1" for m in models)}
    data["flagged_for_modelato"] = [m["id"] for m in art]
    data["mod_side"] = [m["id"] for m in mod]
    save_json(output / "report.json", data)

    out = ["# Cosmetics model verification", "",
           f"Quark commit `{data['commit']}`. Minecraft {data['version']}. {data['label']}.", "",
           f"{len(models)} captured. {counts['PASS']} PASS. {counts['FLAG']} FLAG. "
           f"{data['summary']['reviewed']} visually reviewed.", ""]
    if data["summary"]["reviewed"] != len(models):
        out += ["PASS and FLAG are automated results pending image review.", ""]
    if data["chick_perch"]:
        out += ["## Chick head perch", "", data["chick_perch"], ""]
    for heading, selected, category in (("Flagged for Modelato (art)", art, "art"), ("Mod-side", mod, "mod-side")):
        out += [f"## {heading}", ""]
        for m in selected:
            reasons = ". ".join(text(r) for r in m["reasons"] if r["class"] == category)
            out.append(f"- `{m['id']}`. {reasons}. [Close-up]({m['screenshots']['close']})")
        if not selected:
            out.append("None.")
        out.append("")
    out += ["## Models", "", "| ID | Name | Slot | Result | Class | Looks | Animation |",
            "| --- | --- | --- | --- | --- | --- | --- |"]
    for m in models:
        images = " · ".join(f"[{label}]({path})" for label, path in m["screenshots"].items())
        animation = f"[Strip]({m['animation_strip']})" if m["animation_strip"] else "Static"
        out.append(f"| `{m['id']}` | {m['name']} | {m['slot']} | {m['status']} | "
                   f"{', '.join(m['class'])} | {images} | {animation} |")
    out += ["", "Per-view baseline links, sampled ticks, changed pixels, and review notes are in [report.json](report.json).", ""]
    if data.get("wardrobe"):
        out += ["## Wardrobe", ""]
        out.extend(f"- [{Path(path).stem}]({path})" for path in data["wardrobe"])
        out.append("")
    (output / "REPORT.md").write_text("\n".join(out), encoding="utf-8")

    # A review can shrink the flagged set. Replace only generated sheets in this run.
    sheets = output / "sheets"
    sheets.mkdir(exist_ok=True)
    for old in sheets.glob("flagged-models*.png"):
        old.unlink()
    write_sheet(sheets.parent, models, "sheets/all-models", tile)
    write_sheet(sheets.parent, flags, "sheets/flagged-models", tile)
    print(f"REPORT_READY models={len(models)} pass={counts['PASS']} flag={counts['FLAG']}")


def generate(run, config):
    plan, request = (read_json(run / name) for name in ("plan.json", "request.json"))
    raw, output = run / "raw", run / "evidence"
    paths = [frame_path(raw, frame["path"]) for frame in plan["frames"]]
    missing = [p.relative_to(raw) for p in paths if not p.is_file()]
    if missing:
        raise ValueError(f"Missing {len(missing)} capture frames, first {missing[0]}")
    pixels = config["pixels"]
    views = {name: View(raw, camera, pixels) for name, camera in plan["cameras"].items()}
    exported_baselines = {}
    for name, camera in plan["cameras"].items():
        path = f"baselines/{name}.png"
        exported_baselines[name] = save_preview(load_rgb(raw, camera["baseline"]).crop(views[name].crop),
                                                output, path, (640, 640))
    geometry = read_emission(raw / "geometry.txt")

    log = (run / "logs/client.log").read_text(encoding="utf-8", errors="replace")
    finished = re.search(r"RELEASE_CAPTURE_DONE frames=(\d+)", log)
    if not finished or int(finished[1]) != len(plan["frames"]):
        raise ValueError("The client capture count differs from the frame plan")
    perch = re.search(r"CHICK_PERCH slot=hat gapPx=([-0-9.]+) widthRatio=([0-9.]+)[^\r\n]+", log)
    verify_perch = any(m["id"] == "quarkos:chick" for m in plan["models"])
    if verify_perch and not perch:
        raise ValueError("CHICK_PERCH head measurement is missing")
    data = {"schema": 1, "commit": request["commit"], "version": request["version"],
            "label": request["label"] or "Release capture", "chick_perch": perch[0] if perch else None,
            "catalog_count": len(plan["inventory"]), "raw_frame_count": len(paths),
            "capture_resolution": list(next(iter(views.values())).resolution),
            "baselines": exported_baselines, "models": []}
    wardrobe = sorted(raw.glob("wardrobe-*-page-*.png"))
    if len(plan["models"]) == len(plan["inventory"]):
        slots = {i["slot"] for i in plan["models"]}
        if any(not (raw / f"wardrobe-{slot}-page-1.png").is_file() for slot in slots):
            raise ValueError("Wardrobe capture is missing a populated slot")
    data["wardrobe"] = [save_preview(load_rgb(raw, shot.stem), output, f"wardrobe/{shot.name}", (960, 540))
                        for shot in wardrobe]

    basis = list(mannequin())
    for item in plan["models"]:
        reasons = []
        quads = geometry.get(item["id"])
        if (item["model"].endswith(".bbmodel") or item["id"] == "quarkos:kite_wings") and not quads:
            raise ValueError(f"No geometry emitted for {item['id']}")
        metrics = fit_checks(item, quads, config["units"], reasons)
        silhouette_checks(item, config["units"], reasons)
        authored_motion_checks(item, config["animation"], reasons)
        looks, looks_metrics = look_checks(raw, output, item, views, config, metrics, reasons)
        if quads:
            look = item["looks"][item["close_view"]]
            cam = plan["cameras"][look["camera"]]
            view = camera_for(cam["yaw"], cam["head"])
            image = render(basis + quads, view)
            difference = change(render(basis, view), image, pixels["channel_delta"])
            metrics["offline_visible_px"] = int(difference.sum())
            looks["offline"] = save_preview(Image.fromarray(image), output,
                                             f"models/{slug(item['id'])}/offline.png", (448, 288))
        animations, strip = motion(raw, output, item, views, config, reasons)
        if item["id"] == "quarkos:chick":
            p = config["perch"]
            upper(reasons, "head_perch_gap", "mod-side", abs(float(perch[1])), p["head_gap_max_px"], "px", "CHICK_PERCH")
            band(reasons, "head_perch_width", "mod-side", float(perch[2]), p["width_ratio_range"], "head widths", "CHICK_PERCH")
        model = {k: item[k] for k in ("id", "name", "slot", "group")}
        model.update({"status": "FLAG" if reasons else "PASS", "class": sorted({r["class"] for r in reasons}),
                      "reasons": reasons, "visual_review": "pending", "screenshots": looks,
                      "looks": looks_metrics, "geometry": metrics, "animation": animations, "animation_strip": strip})
        data["models"].append(model)
    finalize(output, data, config["capture"]["tile_px"])


def review(output, review_path, tile):
    report = read_json(output / "report.json")
    reviewed = read_json(review_path)
    wanted = set(reviewed["seen"])
    known = {m["id"] for m in report["models"]}
    if not wanted <= known:
        raise ValueError(f"Review contains IDs outside this run: {wanted - known}")
    for model in report["models"]:
        if model["id"] not in wanted:
            continue
        model["visual_review"] = "reviewed"
        decisions = reviewed.get("notes", {}).get(model["id"], {})
        if decisions:
            model["visual_notes"] = decisions.get("note", "")
            dismissed = set(decisions.get("dismiss", []))
            measured = model["reasons"] + model.get("dismissed_reasons", [])
            model["dismissed_reasons"] = [r for r in measured if r["check"] in dismissed]
            model["reasons"] = [r for r in measured if r["check"] not in dismissed]
            model["class"] = sorted({r["class"] for r in model["reasons"]})
            model["status"] = "FLAG" if model["reasons"] else "PASS"
    finalize(output, report, tile)


def archive(output, path):
    read_json(output / "report.json")
    if path.resolve().is_relative_to(output.resolve()):
        raise ValueError("Place the archive beside the evidence directory")
    with ZipFile(path, "x") as bundle:
        for item in sorted(output.rglob("*")):
            if item.is_file():
                bundle.write(item, item.relative_to(output))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--review", type=Path)
    mode.add_argument("--archive", type=Path)
    args = parser.parse_args()
    config = read_json(THRESHOLDS)
    if args.archive:
        archive(args.directory, args.archive)
    elif args.review:
        review(args.directory, args.review, config["capture"]["tile_px"])
    else:
        generate(args.directory, config)


if __name__ == "__main__":
    main()
