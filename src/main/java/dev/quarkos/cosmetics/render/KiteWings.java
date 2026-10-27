package dev.quarkos.cosmetics.render;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the delivered Blockbench kite wings. Geometry, animation, and texture
 * bytes stay as authored. Pivots are absolute and the rest pose is open.
 * Keyframe rotations are added in that same space, so idle folds the panels
 * down to the waist and flap returns them to the open wing.
 */
public final class KiteWings {
    private static final Logger LOGGER = LoggerFactory.getLogger("quarkos_cosmetics");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("quarkos_cosmetics", "textures/cosmetics/kite_wings.png");
    private static final String GEO = "/assets/quarkos_cosmetics/geckolib/models/wings.geo.json";
    private static final String ANIMATION = "/assets/quarkos_cosmetics/geckolib/animations/wings.animation.json";

    private static Model model;
    private static boolean failed;
    private static boolean announced;

    private KiteWings() {
    }

    public static void draw(PoseStack pose, CosmeticDrawer.Submitter submitter, int light, float age, boolean flap,
                            ModelPart body, float offsetX, float offsetY, float offsetZ) {
        Model loaded = load();
        if (loaded == null) {
            return;
        }
        String forced = System.getProperty("quarkos_cosmetics.wingPose", "");
        boolean open = "open".equals(forced);
        boolean flying = flap || "fly".equals(forced);
        String clip = flying || open ? "flap" : "idle";
        Clip animation = loaded.clips.get(clip);
        double time = 0;
        if (animation != null && !open) {
            time = animation.length <= 0.0001 ? 0 : (age / 20.0) % animation.length;
        }
        if (!announced) {
            announced = true;
            LOGGER.info("kite wings playing {} at {}s", clip, time);
        }
        pose.pushPose();
        body.translateAndRotate(pose);
        pose.scale(1f / 16f, -1f / 16f, 1f / 16f);
        pose.translate(offsetX, offsetY, offsetZ);
        // Body origin is the shoulder line. Bedrock y=24 is that same line.
        pose.translate(0f, -24f, 0f);
        Matrix4f view = new Matrix4f(pose.last().pose());
        Matrix3f normals = new Matrix3f(pose.last().normal());
        double sample = time;
        submitter.submit(pose, TEXTURE, false, consumer -> render(consumer, view, normals, light, loaded, animation, sample));
        pose.popPose();
    }

    private static void render(VertexConsumer consumer, Matrix4f view, Matrix3f normals, int light,
                               Model loaded, Clip clip, double time) {
        Map<String, Matrix4f> worlds = new LinkedHashMap<>();
        Map<String, Matrix3f> turns = new LinkedHashMap<>();
        for (Bone bone : loaded.bones.values()) {
            world(loaded, bone, clip, time, worlds, turns);
        }
        for (Bone bone : loaded.bones.values()) {
            Matrix4f local = worlds.get(bone.name);
            Matrix3f turn = turns.get(bone.name);
            if (local == null || bone.cubes.isEmpty()) {
                continue;
            }
            Matrix4f combined = new Matrix4f(view).mul(local);
            for (Cube cube : bone.cubes) {
                cube(consumer, combined, normals, turn, light, loaded, cube);
            }
        }
    }

    private static Matrix4f world(Model loaded, Bone bone, Clip clip, double time,
                                  Map<String, Matrix4f> worlds, Map<String, Matrix3f> turns) {
        Matrix4f cached = worlds.get(bone.name);
        if (cached != null) {
            return cached;
        }
        Matrix4f matrix = new Matrix4f();
        if (bone.parent != null) {
            Bone parent = loaded.bones.get(bone.parent);
            if (parent != null) {
                matrix.set(world(loaded, parent, clip, time, worlds, turns));
            }
        }
        float[] position = clip == null ? new float[3] : clip.position(bone.name, time);
        float[] rotation = clip == null ? new float[3] : clip.rotation(bone.name, time);
        float rx = (float) Math.toRadians(bone.rotation[0] + rotation[0]);
        float ry = (float) Math.toRadians(bone.rotation[1] + rotation[1]);
        float rz = (float) Math.toRadians(bone.rotation[2] + rotation[2]);
        matrix.translate(position[0], position[1], position[2]);
        matrix.translate(bone.pivot[0], bone.pivot[1], bone.pivot[2]);
        matrix.rotateZ(rz).rotateY(ry).rotateX(rx);
        matrix.translate(-bone.pivot[0], -bone.pivot[1], -bone.pivot[2]);
        worlds.put(bone.name, matrix);
        turns.put(bone.name, new Matrix3f(matrix));
        return matrix;
    }

    private static void cube(VertexConsumer consumer, Matrix4f view, Matrix3f poseNormals, Matrix3f turn, int light,
                             Model loaded, Cube cube) {
        float inf = cube.inflate;
        float x0 = cube.ox - inf;
        float x1 = cube.ox + cube.sx + inf;
        float y0 = cube.oy - inf;
        float y1 = cube.oy + cube.sy + inf;
        float z0 = cube.oz - inf;
        float z1 = cube.oz + cube.sz + inf;
        boolean mirror = cube.mirror;
        face(consumer, view, poseNormals, turn, light, loaded, cube, mirror, mirror ? "negX" : "posX",
                x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0);
        face(consumer, view, poseNormals, turn, light, loaded, cube, mirror, mirror ? "posX" : "negX",
                x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0);
        face(consumer, view, poseNormals, turn, light, loaded, cube, mirror, "negZ",
                x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1);
        face(consumer, view, poseNormals, turn, light, loaded, cube, mirror, "posZ",
                x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1);
        face(consumer, view, poseNormals, turn, light, loaded, cube, mirror, "posY",
                x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0);
        face(consumer, view, poseNormals, turn, light, loaded, cube, mirror, "negY",
                x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0);
    }

    private static void face(VertexConsumer consumer, Matrix4f view, Matrix3f poseNormals, Matrix3f turn, int light,
                             Model loaded, Cube cube, boolean mirror, String side,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float nx, float ny, float nz) {
        double w = Math.floor(cube.sx);
        double h = Math.floor(cube.sy);
        double d = Math.floor(cube.sz);
        double u;
        double v;
        double uw;
        double vh;
        switch (side) {
            case "posX" -> {
                u = cube.u + d + w;
                v = cube.v + d;
                uw = d;
                vh = h;
            }
            case "negX" -> {
                u = cube.u;
                v = cube.v + d;
                uw = d;
                vh = h;
            }
            case "negZ" -> {
                u = cube.u + d;
                v = cube.v + d;
                uw = w;
                vh = h;
            }
            case "posZ" -> {
                u = cube.u + d + w + d;
                v = cube.v + d;
                uw = w;
                vh = h;
            }
            case "posY" -> {
                u = cube.u + d;
                v = cube.v;
                uw = w;
                vh = d;
            }
            default -> {
                u = cube.u + d + w;
                v = cube.v + d;
                uw = w;
                vh = -d;
            }
        }
        float u0 = (float) (u / loaded.textureWidth);
        float v0 = (float) (v / loaded.textureHeight);
        float u1 = (float) ((u + uw) / loaded.textureWidth);
        float v1 = (float) ((v + vh) / loaded.textureHeight);
        if (!mirror) {
            float swap = u0;
            u0 = u1;
            u1 = swap;
        }
        Vector3f normal = new Vector3f(nx, ny, nz);
        if (turn != null) {
            turn.transform(normal);
        }
        poseNormals.transform(normal);
        if (normal.lengthSquared() > 0.0001f) {
            normal.normalize();
        }
        vertex(consumer, view, x0, y0, z0, u0, v0, light, normal);
        vertex(consumer, view, x1, y1, z1, u1, v0, light, normal);
        vertex(consumer, view, x2, y2, z2, u1, v1, light, normal);
        vertex(consumer, view, x3, y3, z3, u0, v1, light, normal);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f view, float x, float y, float z,
                               float u, float v, int light, Vector3f normal) {
        consumer.addVertex(view, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(normal.x, normal.y, normal.z);
    }

    private static Model load() {
        if (model != null || failed) {
            return model;
        }
        try {
            model = Model.parse();
        } catch (RuntimeException ex) {
            failed = true;
            LOGGER.error("kite wings failed to load", ex);
        }
        return model;
    }

    private static final class Model {
        final float textureWidth;
        final float textureHeight;
        final Map<String, Bone> bones;
        final Map<String, Clip> clips;

        Model(float textureWidth, float textureHeight, Map<String, Bone> bones, Map<String, Clip> clips) {
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
            this.bones = bones;
            this.clips = clips;
        }

        static Model parse() {
            JsonNode geo = tree(GEO).path("minecraft:geometry").path(0);
            JsonNode description = geo.path("description");
            Map<String, Bone> bones = new LinkedHashMap<>();
            for (JsonNode node : geo.path("bones")) {
                Bone bone = Bone.read(node);
                bones.put(bone.name, bone);
            }
            Map<String, Clip> clips = new LinkedHashMap<>();
            JsonNode animations = tree(ANIMATION).path("animations");
            animations.fieldNames().forEachRemaining(name -> clips.put(name, Clip.read(name, animations.get(name))));
            return new Model(description.path("texture_width").floatValue(), description.path("texture_height").floatValue(), bones, clips);
        }

        private static JsonNode tree(String path) {
            try (InputStream stream = KiteWings.class.getResourceAsStream(path)) {
                if (stream == null) {
                    throw new IllegalStateException("missing " + path);
                }
                return JSON.readTree(stream);
            } catch (java.io.IOException ex) {
                throw new IllegalStateException("could not read " + path, ex);
            }
        }
    }

    private static final class Bone {
        final String name;
        final String parent;
        final float[] pivot;
        final float[] rotation;
        final List<Cube> cubes;

        Bone(String name, String parent, float[] pivot, float[] rotation, List<Cube> cubes) {
            this.name = name;
            this.parent = parent;
            this.pivot = pivot;
            this.rotation = rotation;
            this.cubes = cubes;
        }

        static Bone read(JsonNode node) {
            List<Cube> cubes = new ArrayList<>();
            boolean boneMirror = node.path("mirror").asBoolean(false);
            for (JsonNode cube : node.path("cubes")) {
                cubes.add(Cube.read(cube, boneMirror));
            }
            String parent = node.hasNonNull("parent") ? node.get("parent").asText() : null;
            return new Bone(node.path("name").asText(), parent, vec(node.get("pivot")), vec(node.get("rotation")), cubes);
        }
    }

    private static final class Cube {
        final float ox;
        final float oy;
        final float oz;
        final float sx;
        final float sy;
        final float sz;
        final float u;
        final float v;
        final float inflate;
        final boolean mirror;

        Cube(float ox, float oy, float oz, float sx, float sy, float sz, float u, float v, float inflate, boolean mirror) {
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.u = u;
            this.v = v;
            this.inflate = inflate;
            this.mirror = mirror;
        }

        static Cube read(JsonNode node, boolean boneMirror) {
            float[] origin = vec(node.get("origin"));
            float[] size = vec(node.get("size"));
            float[] uv = vec2(node.get("uv"));
            return new Cube(origin[0], origin[1], origin[2], size[0], size[1], size[2], uv[0], uv[1],
                    node.path("inflate").floatValue(), node.path("mirror").asBoolean(boneMirror));
        }
    }

    private static final class Clip {
        final double length;
        final Map<String, Channel> bones;

        Clip(double length, Map<String, Channel> bones) {
            this.length = length;
            this.bones = bones;
        }

        float[] rotation(String bone, double time) {
            Channel channel = bones.get(bone);
            return channel == null ? new float[3] : channel.rotation.sample(time);
        }

        float[] position(String bone, double time) {
            Channel channel = bones.get(bone);
            return channel == null ? new float[3] : channel.position.sample(time);
        }

        static Clip read(String name, JsonNode node) {
            Map<String, Channel> bones = new LinkedHashMap<>();
            JsonNode boneNode = node.path("bones");
            boneNode.fieldNames().forEachRemaining(bone -> bones.put(bone, Channel.read(boneNode.get(bone))));
            return new Clip(node.path("animation_length").asDouble(), bones);
        }
    }

    private static final class Channel {
        final Track rotation;
        final Track position;

        Channel(Track rotation, Track position) {
            this.rotation = rotation;
            this.position = position;
        }

        static Channel read(JsonNode node) {
            return new Channel(Track.read(node.get("rotation")), Track.read(node.get("position")));
        }
    }

    private static final class Track {
        final float[] times;
        final float[][] values;

        Track(float[] times, float[][] values) {
            this.times = times;
            this.values = values;
        }

        float[] sample(double time) {
            if (times.length == 0) {
                return new float[3];
            }
            if (time <= times[0]) {
                return values[0];
            }
            for (int i = 0; i < times.length - 1; i++) {
                if (time <= times[i + 1]) {
                    float span = times[i + 1] - times[i];
                    float progress = span <= 0.000001f ? 1f : (float) ((time - times[i]) / span);
                    float[] start = values[i];
                    float[] end = values[i + 1];
                    return new float[] {
                            start[0] + (end[0] - start[0]) * progress,
                            start[1] + (end[1] - start[1]) * progress,
                            start[2] + (end[2] - start[2]) * progress
                    };
                }
            }
            return values[values.length - 1];
        }

        static Track read(JsonNode node) {
            if (node == null || !node.isObject()) {
                return new Track(new float[0], new float[0][]);
            }
            List<Float> times = new ArrayList<>();
            List<float[]> values = new ArrayList<>();
            node.fieldNames().forEachRemaining(key -> {
                times.add(Float.parseFloat(key));
                values.add(vec(node.get(key)));
            });
            Integer[] order = new Integer[times.size()];
            for (int i = 0; i < order.length; i++) {
                order[i] = i;
            }
            java.util.Arrays.sort(order, java.util.Comparator.comparingDouble(index -> times.get(index)));
            float[] sortedTimes = new float[order.length];
            float[][] sortedValues = new float[order.length][];
            for (int i = 0; i < order.length; i++) {
                sortedTimes[i] = times.get(order[i]);
                sortedValues[i] = values.get(order[i]);
            }
            return new Track(sortedTimes, sortedValues);
        }
    }

    private static float[] vec(JsonNode node) {
        if (node == null || !node.isArray()) {
            return new float[3];
        }
        return new float[] {node.path(0).floatValue(), node.path(1).floatValue(), node.path(2).floatValue()};
    }

    private static float[] vec2(JsonNode node) {
        if (node == null || !node.isArray()) {
            return new float[2];
        }
        return new float[] {node.path(0).floatValue(), node.path(1).floatValue()};
    }
}
