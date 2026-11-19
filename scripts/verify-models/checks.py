"""Measure the visible and model-space fit of a worn cosmetic."""
import numpy as np

from plan import ASSETS, HEAD, read_json, slug


def change(a, b, threshold):
    return np.any(np.maximum(a, b) - np.minimum(a, b) >= threshold, axis=-1)


def bounds(mask):
    ys, xs = np.nonzero(mask)
    if not len(xs):
        return None
    return int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1


def gap_px(a, b, limit):
    """Count empty diagonal-adjacent pixels between silhouettes, up to limit."""
    if not a.any() or not b.any():
        return None
    if (a & b).any():
        return 0
    for distance in range(1, limit + 2):
        p = np.pad(a, 1)
        a = np.logical_or.reduce([
            p[i:i + a.shape[0], j:j + a.shape[1]] for i in range(3) for j in range(3)
        ])
        if (a & b).any():
            return distance - 1
    return limit + 1


def flag(reasons, check, category, value, threshold, unit, evidence):
    if isinstance(value, (float, np.floating)):
        value = round(float(value), 3)
    reasons.append({"check": check, "class": category, "measured": value,
                    "threshold": threshold, "unit": unit, "evidence": evidence})


def upper(reasons, check, category, value, threshold, unit, evidence):
    if value > threshold + 1e-5:
        flag(reasons, check, category, value, f"<= {threshold}", unit, evidence)


def band(reasons, check, category, value, threshold, unit, evidence):
    if not threshold[0] - 1e-5 <= value <= threshold[1] + 1e-5:
        flag(reasons, check, category, value, f"{threshold[0]} to {threshold[1]}", unit, evidence)


def properties(model):
    name = slug(model["id"])
    slot = model["slot"]
    return {
        "halo": "halo" in name,
        "wing": "wings" in name,
        "horn": any(s in name for s in ("horn", "antlers")),
        "headband": any(s in name for s in ("headband", "earmuffs", "headphones", "goggles")),
        "helmet": any(s in name for s in ("mask", "helmet", "goggles")),
        "wide": any(s in name for s in ("witch", "wizard", "straw")),
        "shoulder": slot.startswith("shoulder"),
        "floater": any(s in name for s in ("floating", "balloon", "drone", "bee", "ghost")),
        "paired": slot == "ears" or "wings" in name or "horns" in name or "antlers" in name,
    }


def opaque_quads(quads):
    return [q for q in quads if q[2] not in ("EMISSIVE", "GLOW")]


def sampled_surface(quads):
    """Sample textured surfaces for a model-space intrusion or mounting check."""
    f = np.linspace(0, 1, 13)
    weights = np.stack([(1 - f[:, None]) * (1 - f), f[:, None] * (1 - f),
                        f[:, None] * f, (1 - f[:, None]) * f], axis=-1).reshape((-1, 4))
    for xyz, uv, _, tex in opaque_quads(quads):
        uv_pixels = (weights @ uv) % 1.0 * [tex.shape[1], tex.shape[0]]
        x, y = uv_pixels.astype(int).T
        opaque = tex[y, x, 3] >= 26
        if np.any(opaque):
            yield weights[opaque] @ xyz


def fit_checks(model, quads, u, reasons):
    if not quads:
        return {}
    kind, slot, name = properties(model), model["slot"], slug(model["id"])
    q = opaque_quads(quads)
    if not q:
        return {}
    points = np.concatenate([v for v, *_ in q])
    low, high = points.min(axis=0), points.max(axis=0)
    size, center = high - low, (high + low) / 2
    w, h, d = np.round(size, 3)
    checks = {"bbox_u": [low.round(3).tolist(), high.round(3).tolist()]}
    if model["group"] == "v1":
        return checks
    evidence = "renderer vertices and offline mannequin"
    samples = list(sampled_surface(quads))
    if not samples:
        return checks
    x, y, z = np.concatenate(samples).T
    head_top, head_radius = 4 * u["head_width"] + 0.5, u["head_width"] / 2 + 0.5

    if kind["halo"]:
        band(reasons, "halo_width", "art", w, u["halo_width_range"], "u", evidence)
        checks["head_clearance_u"] = round(float(low[1] - head_top), 3)
        band(reasons, "halo_height", "mod-side", low[1] - head_top, u["halo_gap_range"], "u", evidence)
    elif kind["wing"]:
        band(reasons, "wing_span", "art", w, u["wing_span_range"], "u", evidence)
        middle = y[(abs(x) <= u["wing_root_half_width"]) & (z >= 1.75)]
        if len(middle):
            closest = float(middle[np.argmin(abs(middle - np.mean(u["wing_root_y_range"])))])
            checks["wing_root_y_u"] = round(closest, 3)
            band(reasons, "wing_root_y", "mod-side", closest, u["wing_root_y_range"], "u", evidence)
        else:
            upper(reasons, "wing_root_x", "art", np.min(abs(x)), u["wing_root_half_width"], "u", evidence)
    elif slot == "back":
        upper(reasons, "back_width", "art", w, u["back_width_max"], "u", evidence)
        upper(reasons, "back_depth", "art", high[2] - 2.25, u["back_depth_max"], "u", evidence)
    elif kind["shoulder"]:
        for axis, value, maximum in zip(("width", "height", "depth"), (w, h, d), u["buddy_size_max"]):
            upper(reasons, f"shoulder_size_{axis}", "art", value, maximum, "u", evidence)
        anchor = 6.25 if slot == "shoulder_left" else -6.25
        upper(reasons, "shoulder_center", "mod-side", abs(center[0] - anchor), u["shoulder_center_max"], "u", evidence)
    elif slot in HEAD:
        if kind["headband"]:
            maximum = u["head_hugging_width_max"]
        elif kind["horn"] or slot == "ears":
            maximum = u["ear_width_max"]
        else:
            maximum = u["wide_hat_width_max"] if kind["wide"] else u["hat_width_max"]
        upper(reasons, "head_width", "art", w, maximum, "u", evidence)

    if slot in HEAD and not kind["floater"] and "goggles" not in name:
        upper(reasons, "head_center", "mod-side", abs(center[0]), u["head_center_max"], "u", evidence)
    if slot in ("back", "body", "tail"):
        upper(reasons, "body_center", "mod-side", abs(center[0]), u["body_center_max"], "u", evidence)

    if "goggles" in name:
        # Use the frontmost face spanning the central part of the head. The HUD sits off to one side.
        fronts = [v for v, *_ in q if v[:, 0].min() < -2 and v[:, 0].max() > 2 and np.ptp(v[:, 2]) < 0.1]
        if fronts:
            lens = min(fronts, key=lambda v: np.mean(v[:, 2]))
            lens_y = float(np.mean(lens[:, 1]))
            checks["lens_y_u"] = round(lens_y, 3)
            upper(reasons, "visor_eye_offset", "mod-side", abs(lens_y - u["goggle_eye_y"]),
                  u["goggle_eye_offset_max"], "u", evidence)
        central = z[(abs(x) < 3.5) & (y >= 25) & (y < head_top) & (z < 0)]
        if central.size:
            clearance = -head_radius - central.max()
            if clearance < u["goggle_face_clearance_min"] - 1e-5:
                flag(reasons, "visor_face_clearance", "mod-side", clearance, f">= {u['goggle_face_clearance_min']}", "u", evidence)
    elif slot in HEAD and not kind["helmet"] and not kind["headband"]:
        # Filter to the central eyes and mouth. Brims and band seats can meet the shell above it.
        in_face = z[(abs(x) < 3.75) & (y > 25) & (y < 29) & (z > -head_radius) & (z < -2)]
        if in_face.size:
            upper(reasons, "face_penetration", "mod-side", in_face.max() + head_radius,
                  u["face_penetration_max"], "u", evidence)

    if not kind["floater"] and not kind["halo"]:
        if slot in HEAD:
            distance = np.maximum.reduce([abs(x) - head_radius, 24 - y, y - head_top,
                                          abs(z) - head_radius, np.zeros_like(x)])
        elif kind["shoulder"]:
            distance = np.maximum.reduce([abs(x - anchor) - 2, 12 - y, y - 24,
                                          abs(z) - 2.25, np.zeros_like(x)])
        else:
            distance = np.maximum.reduce([abs(x) - 4.25, 12 - y, y - 24,
                                          abs(z) - 2.25, np.zeros_like(x)])
        checks["seat_clearance_u"] = round(float(distance.min()), 3)
    return checks


def silhouette_checks(model, u, reasons):
    if not model["model"].endswith(".bbmodel") or model["group"] == "v1":
        return
    elements = read_json(ASSETS / model["model"])["elements"]
    name = slug(model["id"])
    source = "Blockbench source and side view"
    unrotated = [e for e in elements if not any(e.get("rotation", []))]
    if "unicorn" in name:
        segments = sum(e["name"].startswith("horn") for e in unrotated)
        upper(reasons, "unrotated_horn_segments", "art", segments, u["unrotated_curve_segments_max"], "segments", source)
    if name == "santa_hat":
        segments = sum(e["name"].startswith(("flop", "droop")) and any(e.get("rotation", [])) for e in elements)
        minimum = u["santa_flop_segments_min"]
        if segments < minimum:
            flag(reasons, "santa_flop_segments", "art", segments, f">= {minimum}", "segments", source)
    if any(part in name for part in ("wizard", "party_hat", "traffic_cone", "pizza")):
        slabs = [e for e in unrotated if all(a < b for a, b in zip(e["from"], e["to"]))
                 and abs(e["from"][0] + e["to"][0]) < 1 and e["from"][1] >= 30
                 and e["to"][0] - e["from"][0] >= 2 and e["to"][2] - e["from"][2] >= 2]
        levels = len({round(e["from"][1], 1) for e in slabs})
        upper(reasons, "stair_bands", "art", levels, u["stair_bands_max"], "levels", source)
