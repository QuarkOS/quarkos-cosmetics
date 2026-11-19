package dev.quarkos.cosmetics.render;

import com.fasterxml.jackson.databind.JsonNode;
import dev.quarkos.cosmetics.model.Worn.Anchor;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

record BlockbenchModel(List<Root> roots, List<Clip> idle, Map<BlockbenchMotion.Action, ActionClip> actions, int cubes) {
    record Vec(float x, float y, float z) {
        static final Vec ZERO = new Vec(0, 0, 0);
        static final Vec ONE = new Vec(1, 1, 1);

        static Vec read(JsonNode node, Vec fallback) {
            if (node.isMissingNode() || node.isNull()) {
                return fallback;
            }
            if (!node.isArray() || node.size() != 3) {
                throw new IllegalArgumentException("expected a three-component vector");
            }
            return new Vec(number(node.get(0)), number(node.get(1)), number(node.get(2)));
        }

        Vec add(Vec other) {
            return new Vec(x + other.x, y + other.y, z + other.z);
        }

        Vec multiply(Vec other) {
            return new Vec(x * other.x, y * other.y, z * other.z);
        }

        Vec lerp(Vec other, float amount) {
            return new Vec(x + (other.x - x) * amount, y + (other.y - y) * amount, z + (other.z - z) * amount);
        }
    }

    record Root(Anchor anchor, Bone bone) {
    }

    record Bone(String uuid, String name, Vec pivot, Vec rotation, List<Cube> cubes, List<Bone> children) {
    }

    enum Side {
        EAST, WEST, NORTH, SOUTH, UP, DOWN
    }

    record Face(float u0, float v0, float u1, float v1, boolean translucent, boolean glowing) {
    }

    record Cube(Vec from, Vec to, Vec pivot, Vec rotation, float inflate, Map<Side, Face> faces) {
    }

    record Clip(String name, float length, Map<String, Tracks> bones) {
        float time(float ageTicks) {
            return length > 0 ? (ageTicks / 20f) % length : 0;
        }
    }

    record ActionClip(Clip clip, boolean hold) {
    }

    record Tracks(List<Key> rotation, List<Key> position, List<Key> scale) {
    }

    record Key(float time, Vec value, boolean smooth) {
    }

    static BlockbenchModel read(JsonNode json, BufferedImage texture, TextureFrames frames,
                                BufferedImage glow, TextureFrames glowFrames, boolean legacy) {
        if (!"geckolib_model".equals(json.path("meta").path("model_format").asText())) {
            throw new IllegalArgumentException("expected a GeckoLib Blockbench model");
        }
        int width = json.path("resolution").path("width").asInt();
        int height = json.path("resolution").path("height").asInt();
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("missing Blockbench UV resolution");
        }

        Map<String, Cube> cubes = new HashMap<>();
        for (JsonNode element : json.path("elements")) {
            if (!"cube".equals(element.path("type").asText("cube"))) {
                throw new IllegalArgumentException("unsupported element " + element.path("name").asText());
            }
            Cube cube = readCube(element, width, height, texture, frames, glow, glowFrames, legacy);
            if (cubes.put(requiredText(element, "uuid"), cube) != null) {
                throw new IllegalArgumentException("duplicate cube " + element.path("uuid").asText());
            }
        }

        Set<String> used = new HashSet<>();
        List<Root> roots = new ArrayList<>();
        for (JsonNode node : json.path("outliner")) {
            int before = used.size();
            Bone bone = readBone(node, cubes, used);
            if (used.size() > before) {
                roots.add(new Root(anchor(bone.name), bone));
            }
        }
        if (used.isEmpty() || used.size() != cubes.size()) {
            throw new IllegalArgumentException("outliner must reference every cube exactly once");
        }

        List<Clip> idle = new ArrayList<>();
        var actions = new EnumMap<BlockbenchMotion.Action, ActionClip>(BlockbenchMotion.Action.class);
        for (JsonNode animation : json.path("animations")) {
            String mode = animation.path("loop").asText();
            String name = requiredText(animation, "name");
            var action = legacy ? null : BlockbenchMotion.Action.fromClip(name);
            if (!"loop".equals(mode) && action == null) {
                continue;
            }
            Map<String, Tracks> tracks = new HashMap<>();
            animation.path("animators").fields().forEachRemaining(entry -> {
                JsonNode animator = entry.getValue();
                tracks.put(entry.getKey(), new Tracks(keys(animator, "rotation", Vec.ZERO),
                        keys(animator, "position", Vec.ZERO), keys(animator, "scale", Vec.ONE)));
            });
            var clip = new Clip(name, number(animation.path("length")), Map.copyOf(tracks));
            if (action == null) {
                idle.add(clip);
            } else {
                actions.put(action, new ActionClip(clip, "hold".equals(mode)));
            }
        }
        return new BlockbenchModel(List.copyOf(roots), List.copyOf(idle), Map.copyOf(actions), used.size());
    }

    private static Anchor anchor(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "head", "bipedhead", "armorhead" -> Anchor.HEAD;
            case "body", "bipedbody", "armorbody" -> Anchor.BODY;
            case "leftarm", "bipedleftarm", "armorleftarm" -> Anchor.LEFT_ARM;
            case "rightarm", "bipedrightarm", "armorrightarm" -> Anchor.RIGHT_ARM;
            default -> throw new IllegalArgumentException("unknown armor root " + name);
        };
    }

    private static Bone readBone(JsonNode node, Map<String, Cube> cubes, Set<String> used) {
        List<Cube> elements = new ArrayList<>();
        List<Bone> children = new ArrayList<>();
        for (JsonNode child : node.path("children")) {
            if (child.isTextual()) {
                String key = child.textValue();
                Cube cube = cubes.get(key);
                if (cube == null || !used.add(key)) {
                    throw new IllegalArgumentException("missing or repeated cube " + key);
                }
                elements.add(cube);
            } else {
                children.add(readBone(child, cubes, used));
            }
        }
        return new Bone(requiredText(node, "uuid"), requiredText(node, "name"),
                Vec.read(node.path("origin"), Vec.ZERO), Vec.read(node.path("rotation"), Vec.ZERO),
                List.copyOf(elements), List.copyOf(children));
    }

    private static Cube readCube(JsonNode node, int width, int height, BufferedImage texture, TextureFrames frames,
                                 BufferedImage glow, TextureFrames glowFrames, boolean legacy) {
        Vec from = Vec.read(node.path("from"), null);
        Vec to = Vec.read(node.path("to"), null);
        if (from == null || to == null || from.x > to.x || from.y > to.y || from.z > to.z) {
            throw new IllegalArgumentException("invalid cube dimensions for " + node.path("name").asText());
        }
        Vec size = new Vec(to.x - from.x, to.y - from.y, to.z - from.z);
        Map<Side, Face> faces = new EnumMap<>(Side.class);
        for (Side side : Side.values()) {
            JsonNode face = node.path("faces").path(side.name().toLowerCase(Locale.ROOT));
            JsonNode uv = face.path("uv");
            if (!legacy && (face.isMissingNode() || face.path("texture").isNull() || face.path("texture").asInt(0) < 0)) {
                continue;
            }
            if (!uv.isArray() || uv.size() != 4) {
                throw new IllegalArgumentException("missing UV for " + node.path("name").asText() + " " + side);
            }
            float u0 = number(uv.get(0)), v0 = number(uv.get(1)), u1 = number(uv.get(2)), v1 = number(uv.get(3));
            if (!legacy && (u0 == u1 || v0 == v1 || !hasArea(side, size))) {
                continue;
            }
            // Blockbench starts the UV rectangle at the opposite edge of our quad.
            boolean flipV = !legacy && (side == Side.UP || side == Side.DOWN);
            boolean flipU = !legacy && !flipV;
            Face coords = new Face((flipU ? u1 : u0) / width, (flipV ? v1 : v0) / height,
                    (flipU ? u0 : u1) / width, (flipV ? v0 : v1) / height, false, false);
            int alpha = legacy ? 0 : alpha(texture, frames, coords);
            if (!legacy && alpha == 0) {
                continue;
            }
            boolean glowing = glow != null && (legacy || alpha(glow, glowFrames, coords) != 0);
            faces.put(side, new Face(coords.u0, coords.v0, coords.u1, coords.v1, alpha == 2, glowing));
        }
        return new Cube(from, to, Vec.read(node.path("origin"), from.add(to).multiply(new Vec(.5f, .5f, .5f))),
                Vec.read(node.path("rotation"), Vec.ZERO), (float) node.path("inflate").asDouble(), Map.copyOf(faces));
    }

    private static boolean hasArea(Side side, Vec size) {
        return switch (side) {
            case EAST, WEST -> size.y > 0 && size.z > 0;
            case UP, DOWN -> size.x > 0 && size.z > 0;
            case NORTH, SOUTH -> size.x > 0 && size.y > 0;
        };
    }

    // 0 = empty, 1 = cutout or solid, 2 = contains partially transparent texels.
    private static int alpha(BufferedImage image, TextureFrames frames, Face face) {
        int x0 = Math.max(0, (int) Math.floor(Math.min(face.u0, face.u1) * frames.frameWidth()));
        int y0 = Math.max(0, (int) Math.floor(Math.min(face.v0, face.v1) * frames.frameHeight()));
        int x1 = Math.min(frames.frameWidth(), (int) Math.ceil(Math.max(face.u0, face.u1) * frames.frameWidth()));
        int y1 = Math.min(frames.frameHeight(), (int) Math.ceil(Math.max(face.v0, face.v1) * frames.frameHeight()));
        int result = 0;
        for (TextureFrames.Frame frame : frames.sequence()) {
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    int alpha = image.getRGB(frame.x() + x, frame.y() + y) >>> 24;
                    if (alpha > 0 && alpha < 255) {
                        return 2;
                    }
                    if (alpha == 255) {
                        result = 1;
                    }
                }
            }
        }
        return result;
    }

    private static List<Key> keys(JsonNode animator, String channel, Vec fallback) {
        List<Key> keys = new ArrayList<>();
        for (JsonNode frame : animator.path("keyframes")) {
            if (channel.equals(frame.path("channel").asText())) {
                JsonNode point = frame.path("data_points").path(0);
                Vec value = new Vec(component(point.path("x"), fallback.x), component(point.path("y"), fallback.y),
                        component(point.path("z"), fallback.z));
                keys.add(new Key(number(frame.path("time")), value, "catmullrom".equals(frame.path("interpolation").asText())));
            }
        }
        keys.sort(Comparator.comparingDouble(Key::time));
        return List.copyOf(keys);
    }

    private static float component(JsonNode node, float fallback) {
        return node.isMissingNode() ? fallback : number(node);
    }

    private static float number(JsonNode node) {
        float value = Float.parseFloat(node.asText());
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("non-finite coordinate");
        }
        return value;
    }

    private static String requiredText(JsonNode node, String key) {
        String value = node.path(key).asText("");
        if (value.isBlank()) {
            throw new IllegalArgumentException("missing " + key);
        }
        return value;
    }

    static Vec sample(List<Key> keys, float time, Vec fallback, boolean legacy) {
        if (keys.isEmpty()) {
            return fallback;
        }
        for (int i = 0; i < keys.size(); i++) {
            Key after = keys.get(i);
            if (time <= after.time) {
                if (i == 0 || time == after.time) {
                    return after.value;
                }
                Key before = keys.get(i - 1);
                float t = (time - before.time) / (after.time - before.time);
                Vec a = before.value;
                Vec b = after.value;
                if (!legacy && (before.smooth || after.smooth)) {
                    Vec prev = keys.get(Math.max(0, i - 2)).value;
                    Vec next = keys.get(Math.min(i + 1, keys.size() - 1)).value;
                    return new Vec(spline(prev.x, a.x, b.x, next.x, t), spline(prev.y, a.y, b.y, next.y, t),
                            spline(prev.z, a.z, b.z, next.z, t));
                }
                return new Vec(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
            }
        }
        return keys.getLast().value;
    }

    private static float spline(float p, float a, float b, float n, float t) {
        return a + .5f * t * ((b - p) + t * ((2 * p - 5 * a + 4 * b - n) + t * (3 * (a - b) + n - p)));
    }
}
