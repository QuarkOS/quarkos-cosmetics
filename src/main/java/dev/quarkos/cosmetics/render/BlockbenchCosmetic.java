package dev.quarkos.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a GeckoLib Blockbench file that was copied in unchanged. Cubes keep the
 * file's positions and face UVs. Bones play the file's keyframes. Nothing here
 * invents a cube or a texel.
 */
public final class BlockbenchCosmetic {
    private static final Logger LOGGER = LoggerFactory.getLogger("quarkos_cosmetics");
    private static final String[] FACE_ORDER = {"east", "west", "north", "south", "up", "down"};
    private static final Map<String, Model> LOADED = new HashMap<>();
    private static final Map<String, Boolean> FAILED = new HashMap<>();

    private BlockbenchCosmetic() {
    }

    public static boolean draws(String id) {
        return modelFor(id) != null;
    }

    public static void draw(PoseStack pose, CosmeticDrawer.Submitter submitter, int light, float age,
                            ModelPart head, ModelPart body, String id) {
        Model model = load(id);
        if (model == null) {
            return;
        }
        pose.pushPose();
        (model.onBody ? body : head).translateAndRotate(pose);
        pose.mulPose(new Quaternionf().rotationZ((float) Math.PI));
        pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
        pose.translate(0f, -24f, 0f);
        Matrix4f view = new Matrix4f(pose.last().pose());
        float time = model.length <= 0f ? 0f : (age / 20f) % model.length;
        CosmeticDrawer.Pass pass = model.translucent ? CosmeticDrawer.Pass.TRANSLUCENT : CosmeticDrawer.Pass.CUTOUT_NO_CULL;
        submitter.submit(pose, model.texture, pass, consumer -> paint(consumer, view, light, 255, model, time, 0f));
        if (model.glow != null) {
            submitter.submit(pose, model.glow, CosmeticDrawer.Pass.GLOW, consumer ->
                    paint(consumer, view, LightTexture.FULL_BRIGHT, 255, model, time, 0.04f));
        }
        pose.popPose();
    }

    private static Spec modelFor(String id) {
        if ("quarkos:scarecrow_straw_hat".equals(id)) {
            return new Spec(id, "cosmetics/scarecrow_straw_hat.bbmodel",
                    "textures/cosmetics/scarecrow_straw_hat.png", null, false);
        }
        if ("quarkos:candle_skull_crown".equals(id)) {
            return new Spec(id, "cosmetics/candle_skull_crown.bbmodel",
                    "textures/cosmetics/candle_skull_crown.png",
                    "textures/cosmetics/candle_skull_crown_glow.png", false);
        }
        if ("quarkos:ghost_buddy".equals(id)) {
            return new Spec(id, "cosmetics/ghost_buddy.bbmodel",
                    "textures/cosmetics/ghost_buddy.png",
                    "textures/cosmetics/ghost_buddy_glow.png", true);
        }
        return null;
    }

    private static Model load(String id) {
        if (Boolean.TRUE.equals(FAILED.get(id))) {
            return null;
        }
        Model cached = LOADED.get(id);
        if (cached != null) {
            return cached;
        }
        Spec spec = modelFor(id);
        if (spec == null) {
            return null;
        }
        try {
            Model model = Model.parse(spec);
            LOADED.put(id, model);
            return model;
        } catch (RuntimeException ex) {
            FAILED.put(id, true);
            LOGGER.error("{} failed to load", id, ex);
            return null;
        }
    }

    /**
     * The ghost bone is authored at {@code [8, 32, 0]}, so the cubes sit on the hair.
     * Scale it around that pivot, then shift it out beside the head, above the shoulder.
     * The bbmodel bytes stay as handed over.
     */
    private static final float GHOST_SCALE = 0.75f;
    private static final float[] GHOST_SHIFT = {2.5f, -6f, -3f};

    private static void paint(VertexConsumer consumer, Matrix4f view, int light, int alpha, Model model, float time, float inflate) {
        Matrix4f posed = new Matrix4f();
        if (model.shift != null) {
            posed.translate(model.shift[0], model.shift[1], model.shift[2]);
        }
        walk(consumer, view, light, alpha, model, model.root, posed, time, inflate);
    }

    private static void walk(VertexConsumer consumer, Matrix4f view, int light, int alpha, Model model,
                             Bone bone, Matrix4f parent, float time, float inflate) {
        float[] rotation = bone.rotation(time);
        float[] position = bone.position(time);
        float[] scale = bone.scale(time);
        Matrix4f local = new Matrix4f(parent);
        local.translate(bone.ox, bone.oy, bone.oz);
        local.translate(position[0], position[1], position[2]);
        local.rotateXYZ((float) Math.toRadians(rotation[0]), (float) Math.toRadians(rotation[1]), (float) Math.toRadians(rotation[2]));
        float fit = "ghost".equals(bone.name) ? GHOST_SCALE : 1f;
        local.scale(scale[0] * fit, scale[1] * fit, scale[2] * fit);
        local.translate(-bone.ox, -bone.oy, -bone.oz);
        for (Cube cube : bone.cubes) {
            Matrix4f cubeMatrix = new Matrix4f(local);
            if (cube.rx != 0f || cube.ry != 0f || cube.rz != 0f) {
                cubeMatrix.translate(cube.cx, cube.cy, cube.cz);
                cubeMatrix.rotateXYZ((float) Math.toRadians(cube.rx), (float) Math.toRadians(cube.ry), (float) Math.toRadians(cube.rz));
                cubeMatrix.translate(-cube.cx, -cube.cy, -cube.cz);
            }
            Matrix4f combined = new Matrix4f(view).mul(cubeMatrix);
            Matrix3f normals = new Matrix3f(combined);
            if (Math.abs(normals.determinant()) > 1.0e-8f) {
                normals.invert().transpose();
            }
            cube.emit(consumer, combined, normals, light, alpha, model.width, model.height, inflate);
        }
        for (Bone child : bone.children) {
            walk(consumer, view, light, alpha, model, child, local, time, inflate);
        }
    }

    private record Spec(String id, String model, String texture, String glow, boolean translucent) {
    }

    private static final class Model {
        final Identifier texture;
        final Identifier glow;
        final boolean translucent;
        final boolean onBody;
        final float width;
        final float height;
        final float length;
        final Bone root;
        final float[] shift;

        Model(Identifier texture, Identifier glow, boolean translucent, boolean onBody,
              float width, float height, float length, Bone root, float[] shift) {
            this.texture = texture;
            this.glow = glow;
            this.translucent = translucent;
            this.onBody = onBody;
            this.width = width;
            this.height = height;
            this.length = length;
            this.root = root;
            this.shift = shift;
        }

        static Model parse(Spec spec) {
            var resource = Minecraft.getInstance().getResourceManager()
                    .getResource(Identifier.fromNamespaceAndPath("quarkos_cosmetics", spec.model));
            if (resource.isEmpty()) {
                throw new IllegalStateException("missing " + spec.model);
            }
            com.fasterxml.jackson.databind.JsonNode root;
            try (InputStream stream = resource.get().open()) {
                root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(stream);
            } catch (Exception ex) {
                throw new IllegalStateException("missing " + spec.model, ex);
            }
            float width = (float) root.path("resolution").path("width").asDouble(64);
            float height = (float) root.path("resolution").path("height").asDouble(64);
            Map<String, Cube> cubes = new HashMap<>();
            for (com.fasterxml.jackson.databind.JsonNode element : root.path("elements")) {
                if (!"cube".equals(element.path("type").asText("cube"))) {
                    throw new IllegalStateException(spec.id + " has a non-cube element " + element.path("name").asText());
                }
                Cube cube = Cube.read(element);
                cubes.put(element.path("uuid").asText(), cube);
            }
            if (cubes.isEmpty()) {
                throw new IllegalStateException(spec.id + " has no cubes");
            }
            com.fasterxml.jackson.databind.JsonNode outliner = root.path("outliner");
            if (!outliner.isArray() || outliner.isEmpty()) {
                throw new IllegalStateException(spec.id + " has no bones");
            }
            Bone bone = Bone.read(outliner.get(0), cubes);
            boolean onBody = "bipedBody".equals(bone.name);
            float length = 0f;
            com.fasterxml.jackson.databind.JsonNode animations = root.path("animations");
            if (animations.isArray() && !animations.isEmpty()) {
                length = (float) animations.get(0).path("length").asDouble(0);
                bone.bind(animations.get(0).path("animators"));
            }
            Identifier texture = Identifier.fromNamespaceAndPath("quarkos_cosmetics", spec.texture);
            Identifier glow = spec.glow == null ? null : Identifier.fromNamespaceAndPath("quarkos_cosmetics", spec.glow);
            float[] shift = "quarkos:ghost_buddy".equals(spec.id) ? GHOST_SHIFT : null;
            return new Model(texture, glow, spec.translucent, onBody, width, height, length, bone, shift);
        }
    }

    private static final class Bone {
        final String name;
        final float ox;
        final float oy;
        final float oz;
        final float restX;
        final float restY;
        final float restZ;
        final List<Cube> cubes = new ArrayList<>();
        final List<Bone> children = new ArrayList<>();
        List<Key> rotationKeys = List.of();
        List<Key> positionKeys = List.of();
        List<Key> scaleKeys = List.of();

        Bone(String name, float ox, float oy, float oz, float restX, float restY, float restZ) {
            this.name = name;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.restX = restX;
            this.restY = restY;
            this.restZ = restZ;
        }

        float[] rotation(float time) {
            float[] sampled = sample(rotationKeys, time);
            return sampled == null ? new float[]{restX, restY, restZ} : sampled;
        }

        float[] position(float time) {
            float[] sampled = sample(positionKeys, time);
            return sampled == null ? new float[]{0f, 0f, 0f} : sampled;
        }

        float[] scale(float time) {
            float[] sampled = sample(scaleKeys, time);
            return sampled == null ? new float[]{1f, 1f, 1f} : sampled;
        }

        void bind(com.fasterxml.jackson.databind.JsonNode animators) {
            animators.fields().forEachRemaining(entry -> {
                com.fasterxml.jackson.databind.JsonNode animator = entry.getValue();
                if (name.equals(animator.path("name").asText())) {
                    rotationKeys = keys(animator, "rotation");
                    positionKeys = keys(animator, "position");
                    scaleKeys = keys(animator, "scale");
                }
            });
            for (Bone child : children) {
                child.bind(animators);
            }
        }

        static Bone read(com.fasterxml.jackson.databind.JsonNode node, Map<String, Cube> cubes) {
            com.fasterxml.jackson.databind.JsonNode origin = node.path("origin");
            com.fasterxml.jackson.databind.JsonNode rotation = node.path("rotation");
            Bone bone = new Bone(
                    node.path("name").asText(""),
                    (float) origin.path(0).asDouble(),
                    (float) origin.path(1).asDouble(),
                    (float) origin.path(2).asDouble(),
                    (float) rotation.path(0).asDouble(),
                    (float) rotation.path(1).asDouble(),
                    (float) rotation.path(2).asDouble());
            for (com.fasterxml.jackson.databind.JsonNode child : node.path("children")) {
                if (child.isTextual()) {
                    Cube cube = cubes.get(child.asText());
                    if (cube == null) {
                        throw new IllegalStateException("missing cube " + child.asText());
                    }
                    bone.cubes.add(cube);
                } else {
                    bone.children.add(read(child, cubes));
                }
            }
            return bone;
        }
    }

    private static final class Cube {
        final float x0;
        final float y0;
        final float z0;
        final float x1;
        final float y1;
        final float z1;
        final float cx;
        final float cy;
        final float cz;
        final float rx;
        final float ry;
        final float rz;
        final Map<String, float[]> faces;

        Cube(float x0, float y0, float z0, float x1, float y1, float z1,
             float cx, float cy, float cz, float rx, float ry, float rz, Map<String, float[]> faces) {
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
            this.faces = faces;
        }

        void emit(VertexConsumer consumer, Matrix4f view, Matrix3f normals, int light, int alpha,
                  float width, float height, float inflate) {
            float ax0 = x0 - inflate;
            float ay0 = y0 - inflate;
            float az0 = z0 - inflate;
            float ax1 = x1 + inflate;
            float ay1 = y1 + inflate;
            float az1 = z1 + inflate;
            quad(consumer, view, normals, light, alpha, faces.get("east"), width, height, ax1, ay1, az0, ax1, ay1, az1, ax1, ay0, az1, ax1, ay0, az0, 1, 0, 0);
            quad(consumer, view, normals, light, alpha, faces.get("west"), width, height, ax0, ay1, az1, ax0, ay1, az0, ax0, ay0, az0, ax0, ay0, az1, -1, 0, 0);
            quad(consumer, view, normals, light, alpha, faces.get("north"), width, height, ax0, ay1, az0, ax1, ay1, az0, ax1, ay0, az0, ax0, ay0, az0, 0, 0, -1);
            quad(consumer, view, normals, light, alpha, faces.get("south"), width, height, ax1, ay1, az1, ax0, ay1, az1, ax0, ay0, az1, ax1, ay0, az1, 0, 0, 1);
            quad(consumer, view, normals, light, alpha, faces.get("up"), width, height, ax0, ay1, az1, ax1, ay1, az1, ax1, ay1, az0, ax0, ay1, az0, 0, 1, 0);
            quad(consumer, view, normals, light, alpha, faces.get("down"), width, height, ax0, ay0, az0, ax1, ay0, az0, ax1, ay0, az1, ax0, ay0, az1, 0, -1, 0);
        }

        static Cube read(com.fasterxml.jackson.databind.JsonNode element) {
            float x0 = (float) element.path("from").path(0).asDouble();
            float y0 = (float) element.path("from").path(1).asDouble();
            float z0 = (float) element.path("from").path(2).asDouble();
            float x1 = (float) element.path("to").path(0).asDouble();
            float y1 = (float) element.path("to").path(1).asDouble();
            float z1 = (float) element.path("to").path(2).asDouble();
            if (x1 < x0 || y1 < y0 || z1 < z0) {
                throw new IllegalStateException(element.path("name").asText() + " has a reversed cube");
            }
            com.fasterxml.jackson.databind.JsonNode origin = element.path("origin");
            com.fasterxml.jackson.databind.JsonNode rotation = element.path("rotation");
            Map<String, float[]> faces = new HashMap<>();
            for (String name : FACE_ORDER) {
                com.fasterxml.jackson.databind.JsonNode uv = element.path("faces").path(name).path("uv");
                if (!uv.isArray() || uv.size() != 4) {
                    throw new IllegalStateException(element.path("name").asText() + " is missing " + name + " uv");
                }
                faces.put(name, new float[]{
                        (float) uv.get(0).asDouble(),
                        (float) uv.get(1).asDouble(),
                        (float) uv.get(2).asDouble(),
                        (float) uv.get(3).asDouble()
                });
            }
            return new Cube(x0, y0, z0, x1, y1, z1,
                    (float) origin.path(0).asDouble((x0 + x1) * 0.5),
                    (float) origin.path(1).asDouble((y0 + y1) * 0.5),
                    (float) origin.path(2).asDouble((z0 + z1) * 0.5),
                    (float) rotation.path(0).asDouble(),
                    (float) rotation.path(1).asDouble(),
                    (float) rotation.path(2).asDouble(),
                    faces);
        }
    }

    private static void quad(VertexConsumer consumer, Matrix4f view, Matrix3f normals, int light, int alpha, float[] uv,
                             float width, float height,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float nx, float ny, float nz) {
        if (uv == null || width <= 0f || height <= 0f) {
            return;
        }
        Vector3f normal = new Vector3f(nx, ny, nz);
        normals.transform(normal);
        if (normal.lengthSquared() > 1.0e-8f) {
            normal.normalize();
        }
        float u0 = uv[0] / width;
        float v0 = uv[1] / height;
        float u1 = uv[2] / width;
        float v1 = uv[3] / height;
        vertex(consumer, view, x0, y0, z0, u0, v0, light, alpha, normal);
        vertex(consumer, view, x1, y1, z1, u1, v0, light, alpha, normal);
        vertex(consumer, view, x2, y2, z2, u1, v1, light, alpha, normal);
        vertex(consumer, view, x3, y3, z3, u0, v1, light, alpha, normal);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f view, float x, float y, float z,
                               float u, float v, int light, int alpha, Vector3f normal) {
        consumer.addVertex(view, x, y, z)
                .setColor(255, 255, 255, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(normal.x, normal.y, normal.z);
    }

    private record Key(float time, float x, float y, float z) {
    }

    private static List<Key> keys(com.fasterxml.jackson.databind.JsonNode animator, String channel) {
        List<Key> list = new ArrayList<>();
        for (com.fasterxml.jackson.databind.JsonNode frame : animator.path("keyframes")) {
            if (!channel.equals(frame.path("channel").asText())) {
                continue;
            }
            com.fasterxml.jackson.databind.JsonNode point = frame.path("data_points").path(0);
            list.add(new Key(
                    (float) frame.path("time").asDouble(),
                    parse(point.path("x").asText("0")),
                    parse(point.path("y").asText("0")),
                    parse(point.path("z").asText("0"))));
        }
        list.sort((a, b) -> Float.compare(a.time, b.time));
        return list;
    }

    private static float parse(String text) {
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("bad keyframe " + text);
        }
    }

    private static float[] sample(List<Key> keys, float time) {
        if (keys.isEmpty()) {
            return null;
        }
        Key prev = keys.get(0);
        Key next = keys.get(keys.size() - 1);
        for (Key key : keys) {
            if (key.time <= time) {
                prev = key;
            }
            if (key.time >= time) {
                next = key;
                break;
            }
        }
        float span = next.time - prev.time;
        if (next.time < time) {
            span = (keys.get(keys.size() - 1).time - prev.time) + keys.get(0).time;
            next = keys.get(0);
        }
        float t = span <= 1.0e-4f ? 0f : (time - prev.time) / span;
        if (t < 0f) {
            t = 0f;
        } else if (t > 1f) {
            t = 1f;
        }
        return new float[]{
                prev.x + (next.x - prev.x) * t,
                prev.y + (next.y - prev.y) * t,
                prev.z + (next.z - prev.z) * t
        };
    }
}
