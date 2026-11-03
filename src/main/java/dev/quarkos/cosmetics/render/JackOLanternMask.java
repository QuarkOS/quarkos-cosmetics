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
import java.util.List;
import java.util.Locale;

/**
 * GeckoLib armor mask. The head bone follows the player head. Bedrock +Y is up;
 * a 180° roll stands it on the head. The scale is uniform and positive, so the
 * pose determinant stays positive and culling keeps the outward faces.
 */
public final class JackOLanternMask {
    public static final String ID = "quarkos:jack_o_lantern_mask";
    private static final Logger LOGGER = LoggerFactory.getLogger("quarkos_cosmetics");
    private static final Identifier GEO = Identifier.fromNamespaceAndPath("quarkos_cosmetics", "geo/jack_o_lantern_mask.geo.json");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("quarkos_cosmetics", "textures/cosmetics/jack_o_lantern_mask.png");
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath("quarkos_cosmetics", "textures/cosmetics/jack_o_lantern_mask_glow.png");
    /** One square of the vertical strip. The mcmeta asks for this, but entity textures upload the whole strip. */
    private static final float FRAME = 80f;
    private static final int FRAMES = 24;
    /** Holds each candle frame for this many ticks. Matches the mcmeta frametime, with interpolation off. */
    private static final int FRAME_TICKS = 2;
    /** Lip and side walls sit on the rind. A short nudge clears the z-fight. */
    private static final float GLOW_BIAS = 0.6f;
    private static final String[] FACES = {"north", "south", "east", "west", "up", "down"};

    private static Model model;
    private static boolean failed;
    private static boolean loggedDet;

    private JackOLanternMask() {
    }

    public static void draw(PoseStack pose, CosmeticDrawer.Submitter submitter, int light, ModelPart head,
                            float offsetX, float offsetY, float offsetZ) {
        Model loaded = load();
        if (loaded == null) {
            return;
        }
        int tick = Minecraft.getInstance().player == null ? 0 : Minecraft.getInstance().player.tickCount;
        pose.pushPose();
        head.translateAndRotate(pose);
        // Call order is outer-first. The vertex sees the pixel translate, then the
        // uniform scale, then the roll. A negative Y scale would flip the winding.
        pose.mulPose(new Quaternionf().rotationZ((float) Math.PI));
        pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
        pose.translate(-offsetX, -24f + offsetY, offsetZ);
        Matrix4f view = new Matrix4f(pose.last().pose());
        int frame = Math.floorMod(tick / FRAME_TICKS, FRAMES);
        if (!loggedDet) {
            loggedDet = true;
            LOGGER.info("mask pose det {} candle frame {} of {}", String.format(Locale.ROOT, "%.8g", view.determinant()), frame, FRAMES);
        }
        Matrix3f normals = new Matrix3f(view);
        if (Math.abs(normals.determinant()) > 0.0000001f) {
            normals.invert().transpose();
        }
        submitter.submit(pose, TEXTURE, CosmeticDrawer.Pass.CUTOUT, consumer -> paint(consumer, view, normals, light, loaded, frame, 0));
        // Eyes is ordered after cutout, so the candle is in the same frame as the rind.
        submitter.submit(pose, GLOW, CosmeticDrawer.Pass.GLOW, consumer -> paint(consumer, view, normals, LightTexture.FULL_BRIGHT, loaded, frame, 1));
        //? if >=1.21.11 {
        // Draw it again after the world so a later shell pass cannot cover the openings.
        if (deferred) {
            hold(view, normals, frame, loaded);
        }
        //?}
        pose.popPose();
    }

    //? if >=1.21.11 {
    private static boolean deferred;
    private static Matrix4f heldView;
    private static Matrix3f heldNormals;
    private static int heldFrame;
    private static Model heldModel;
    private static boolean loggedFlush;
    /** Big enough for every lit face. The 8 KB builder overflowed and the mesh was dropped. */
    private static final com.mojang.blaze3d.vertex.ByteBufferBuilder GLOW_BYTES = new com.mojang.blaze3d.vertex.ByteBufferBuilder(1048576);

    /** The shell and the candle share one custom-geometry map, so the shell can paint over the flame. Draw the flame after entities instead. */
    public static void useDeferredGlow() {
        deferred = true;
    }

    public static void flush() {
        if (heldView == null || heldModel == null) {
            return;
        }
        // Entity positions are already camera-relative. The shader still multiplies by the
        // camera rotation, and that rotation is not on the stack once the entity batch ends.
        org.joml.Matrix4fStack stack = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        stack.pushMatrix();
        net.minecraft.client.Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        // Pull the candle toward the camera in view space. The cavity back walls sit behind
        // the rind, and eyes does not write depth, so they have to win the test on their own.
        Matrix4f viewRotation = new Matrix4f().rotation(camera.rotation().conjugate());
        stack.identity().mul(viewRotation);
        com.mojang.blaze3d.systems.RenderSystem.disableScissorForRenderTypeDraws();
        if (!loggedFlush) {
            loggedFlush = true;
            LOGGER.info("mask glow flush frame {}", heldFrame);
        }
        GLOW_BYTES.clear();
        var source = net.minecraft.client.renderer.MultiBufferSource.immediate(GLOW_BYTES);
        var glowType = MaskGlow.type(GLOW);
        paint(source.getBuffer(glowType), heldView, heldNormals, LightTexture.FULL_BRIGHT, heldModel, heldFrame, 1);
        source.endBatch();
        GLOW_BYTES.clear();
        stack.popMatrix();
        heldView = null;
    }

    private static void hold(Matrix4f view, Matrix3f normals, int frame, Model loaded) {
        heldView = new Matrix4f(view);
        heldNormals = new Matrix3f(normals);
        heldFrame = frame;
        heldModel = loaded;
    }
    //?}

    /** kind 0 is the rind. kind 1 is the emissive lip and the side walls of the carving. */
    private static void paint(VertexConsumer consumer, Matrix4f view, Matrix3f normals, int light, Model loaded, int frame, int kind) {
        for (Cube cube : loaded.cubes) {
            float x0 = cube.x0;
            float y0 = cube.y0;
            float z0 = cube.z0;
            float x1 = cube.x1;
            float y1 = cube.y1;
            float z1 = cube.z1;
            // Same outward winding as the chick cubes, in bedrock space.
            face(consumer, view, normals, light, loaded, cube, frame, kind, "east", x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0, 1, 0, 0);
            face(consumer, view, normals, light, loaded, cube, frame, kind, "west", x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1, -1, 0, 0);
            face(consumer, view, normals, light, loaded, cube, frame, kind, "north", x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0, 0, 0, -1);
            face(consumer, view, normals, light, loaded, cube, frame, kind, "south", x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1, 0, 0, 1);
            face(consumer, view, normals, light, loaded, cube, frame, kind, "up", x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0);
            face(consumer, view, normals, light, loaded, cube, frame, kind, "down", x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0);
        }
    }

    private static void face(VertexConsumer consumer, Matrix4f view, Matrix3f normals, int light, Model loaded, Cube cube,
                             int frame, int kind, String name,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float nx, float ny, float nz) {
        Uv uv = cube.uv(name);
        if (uv == null || (kind != 0 && loaded.anyGlow && !uv.glow)) {
            return;
        }
        // The candle picture is five large north faces on the back wall of the carving.
        // A full quad of that mostly empty rect does not survive the eyes pass, so each
        // lit texel becomes its own quad on the front of the opening.
        if (kind == 1 && "north".equals(name)) {
            candleTexels(consumer, view, normals, light, loaded, cube, frame, uv);
            return;
        }
        // The geo file's texture height is one frame. The png is the full strip, and SimpleTexture
        // uploads that strip as-is, so V has to land on this tick's frame or the candle samples empty rows.
        float u0 = uv.u1 / FRAME;
        float u1 = uv.u2 / FRAME;
        float v0 = (uv.v1 + frame * FRAME) / (FRAME * FRAMES);
        float v1 = (uv.v2 + frame * FRAME) / (FRAME * FRAMES);
        Vector3f normal = new Vector3f(nx, ny, nz);
        normals.transform(normal);
        if (normal.lengthSquared() > 0.0001f) {
            normal.normalize();
        }
        float bias = kind == 1 ? GLOW_BIAS : 0f;
        // Bedrock -Z is toward the viewer once the mask is on the head. The carving's
        // lit edges sit on the rind, so they also step forward or a level camera hides them.
        float pull = kind == 1 ? 0.55f : 0f;
        emit(consumer, view, light, normal, bias, nx, ny, nz, u0, v0, u1, v1,
                x0, y0, z0 - pull, x1, y1, z1 - pull, x2, y2, z2 - pull, x3, y3, z3 - pull);
        if (kind == 1) {
            vertex(consumer, view, x0 + nx * bias, y0 + ny * bias, z0 - pull + nz * bias, u0, v0, light, normal);
            vertex(consumer, view, x3 + nx * bias, y3 + ny * bias, z3 - pull + nz * bias, u0, v1, light, normal);
            vertex(consumer, view, x2 + nx * bias, y2 + ny * bias, z2 - pull + nz * bias, u1, v1, light, normal);
            vertex(consumer, view, x1 + nx * bias, y1 + ny * bias, z1 - pull + nz * bias, u1, v0, light, normal);
        }
    }

    /** Front of the rind. The back-wall candle is moved here so it sits in the holes. */
    private static final float FLAME_Z = -6.55f;
    private static boolean loggedCandles;

    /** One quad per opaque texel of a back-wall face, placed on the front of the carving. */
    private static void candleTexels(VertexConsumer consumer, Matrix4f view, Matrix3f normals, int light, Model loaded,
                                    Cube cube, int frame, Uv uv) {
        if (loaded.glowPixels == null || Math.abs(uv.u2 - uv.u1) < 0.01f || Math.abs(uv.v2 - uv.v1) < 0.01f) {
            return;
        }
        int tx0 = (int) Math.floor(Math.min(uv.u1, uv.u2));
        int tx1 = (int) Math.ceil(Math.max(uv.u1, uv.u2));
        int ty0 = (int) Math.floor(Math.min(uv.v1, uv.v2));
        int ty1 = (int) Math.ceil(Math.max(uv.v1, uv.v2));
        int width = loaded.glowPixels.width();
        int[] argb = loaded.glowPixels.argb();
        Vector3f normal = new Vector3f(0f, 0f, -1f);
        normals.transform(normal);
        if (normal.lengthSquared() > 0.0001f) {
            normal.normalize();
        }
        int lit = 0;
        for (int ty = ty0; ty < ty1; ty++) {
            for (int tx = tx0; tx < tx1; tx++) {
                if (tx < 0 || ty < 0 || tx >= width || ty >= width) {
                    continue;
                }
                if (((argb[ty * width + tx] >>> 24) & 255) == 0) {
                    continue;
                }
                float fu0 = (tx - uv.u1) / (uv.u2 - uv.u1);
                float fu1 = (tx + 1f - uv.u1) / (uv.u2 - uv.u1);
                float fv0 = (ty - uv.v1) / (uv.v2 - uv.v1);
                float fv1 = (ty + 1f - uv.v1) / (uv.v2 - uv.v1);
                float wx0 = cube.x0 + fu0 * (cube.x1 - cube.x0);
                float wx1 = cube.x0 + fu1 * (cube.x1 - cube.x0);
                float wyHigh = cube.y1 + fv0 * (cube.y0 - cube.y1);
                float wyLow = cube.y1 + fv1 * (cube.y0 - cube.y1);
                float u0 = tx / FRAME;
                float u1 = (tx + 1f) / FRAME;
                float v0 = (ty + frame * FRAME) / (FRAME * FRAMES);
                float v1 = (ty + 1f + frame * FRAME) / (FRAME * FRAMES);
                emit(consumer, view, light, normal, 0f, 0f, 0f, 0f, u0, v0, u1, v1,
                        wx0, wyHigh, FLAME_Z, wx1, wyHigh, FLAME_Z, wx1, wyLow, FLAME_Z, wx0, wyLow, FLAME_Z);
                vertex(consumer, view, wx0, wyHigh, FLAME_Z, u0, v0, light, normal);
                vertex(consumer, view, wx0, wyLow, FLAME_Z, u0, v1, light, normal);
                vertex(consumer, view, wx1, wyLow, FLAME_Z, u1, v1, light, normal);
                vertex(consumer, view, wx1, wyHigh, FLAME_Z, u1, v0, light, normal);
                lit++;
            }
        }
        if (!loggedCandles && lit > 0) {
            loggedCandles = true;
            LOGGER.info("mask candle texels {}", lit);
        }
    }

    private static void emit(VertexConsumer consumer, Matrix4f view, int light, Vector3f normal, float bias,
                             float nx, float ny, float nz,
                             float u0, float v0, float u1, float v1,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        vertex(consumer, view, x0 + nx * bias, y0 + ny * bias, z0 + nz * bias, u0, v0, light, normal);
        vertex(consumer, view, x1 + nx * bias, y1 + ny * bias, z1 + nz * bias, u1, v0, light, normal);
        vertex(consumer, view, x2 + nx * bias, y2 + ny * bias, z2 + nz * bias, u1, v1, light, normal);
        vertex(consumer, view, x3 + nx * bias, y3 + ny * bias, z3 + nz * bias, u0, v1, light, normal);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f view, float x, float y, float z,
                               float u, float v, int light, Vector3f normal) {
        consumer.addVertex(view, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.pack(0, 10))
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
            LOGGER.error("jack-o'-lantern mask failed to load", ex);
        }
        return model;
    }

    private static final class Model {
        final float textureWidth;
        final float textureHeight;
        final List<Cube> cubes;
        /** False when the glow strip could not be read. The pass then draws every face and lets alpha discard. */
        final boolean anyGlow;
        /** Frame 0 of the glow strip. Lit texels are the same rectangle on every frame. */
        final SkinImages.Pixels glowPixels;

        Model(float textureWidth, float textureHeight, List<Cube> cubes, boolean anyGlow, SkinImages.Pixels glowPixels) {
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
            this.cubes = cubes;
            this.anyGlow = anyGlow;
            this.glowPixels = glowPixels;
        }

        static Model parse() {
            com.fasterxml.jackson.databind.JsonNode root;
            var resource = Minecraft.getInstance().getResourceManager().getResource(GEO);
            if (resource.isEmpty()) {
                throw new IllegalStateException("missing " + GEO);
            }
            try (InputStream stream = resource.get().open()) {
                root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(stream);
            } catch (Exception ex) {
                throw new IllegalStateException("missing " + GEO, ex);
            }
            com.fasterxml.jackson.databind.JsonNode geometry = root.path("minecraft:geometry").path(0);
            com.fasterxml.jackson.databind.JsonNode description = geometry.path("description");
            float width = (float) description.path("texture_width").asDouble(80);
            float height = (float) description.path("texture_height").asDouble(80);
            SkinImages.Pixels glowPixels = SkinImages.read(GLOW);
            boolean[] glow = glowMask(glowPixels);
            List<Cube> cubes = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode bone : geometry.path("bones")) {
                if (!"armorHead".equals(bone.path("name").asText())) {
                    continue;
                }
                for (com.fasterxml.jackson.databind.JsonNode cube : bone.path("cubes")) {
                    cubes.add(Cube.read(cube, glow, (int) width));
                }
            }
            if (cubes.isEmpty()) {
                throw new IllegalStateException("armorHead has no cubes");
            }
            int lit = 0;
            int texels = 0;
            for (boolean on : glow) {
                if (on) {
                    texels++;
                }
            }
            for (Cube cube : cubes) {
                for (Uv face : cube.faces) {
                    if (face != null && face.glow) {
                        lit++;
                    }
                }
            }
            LOGGER.info("mask cubes {} glow faces {} glow texels {}", cubes.size(), lit, texels);
            return new Model(width, height, cubes, texels > 0 && lit > 0, glowPixels);
        }

        /** Frame 0 of the glow strip. The lit mask is the same rectangle on every frame. */
        private static boolean[] glowMask(SkinImages.Pixels pixels) {
            if (pixels == null) {
                return new boolean[0];
            }
            boolean[] mask = new boolean[pixels.width() * Math.min(pixels.width(), pixels.height())];
            int frame = Math.min(pixels.width(), pixels.height());
            for (int y = 0; y < frame; y++) {
                for (int x = 0; x < pixels.width(); x++) {
                    int argb = pixels.argb()[y * pixels.width() + x];
                    mask[y * pixels.width() + x] = ((argb >>> 24) & 255) > 0;
                }
            }
            return mask;
        }
    }

    private static final class Uv {
        final float u1;
        final float v1;
        final float u2;
        final float v2;
        final boolean glow;

        Uv(float u1, float v1, float u2, float v2, boolean glow) {
            this.u1 = u1;
            this.v1 = v1;
            this.u2 = u2;
            this.v2 = v2;
            this.glow = glow;
        }
    }

    private static final class Cube {
        final float x0;
        final float y0;
        final float z0;
        final float x1;
        final float y1;
        final float z1;
        final Uv[] faces = new Uv[FACES.length];

        Cube(float x0, float y0, float z0, float x1, float y1, float z1) {
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
        }

        Uv uv(String name) {
            for (int i = 0; i < FACES.length; i++) {
                if (FACES[i].equals(name)) {
                    return faces[i];
                }
            }
            return null;
        }

        static Cube read(com.fasterxml.jackson.databind.JsonNode node, boolean[] glow, int frame) {
            float[] origin = vec(node.get("origin"));
            float[] size = vec(node.get("size"));
            Cube cube = new Cube(origin[0], origin[1], origin[2], origin[0] + size[0], origin[1] + size[1], origin[2] + size[2]);
            com.fasterxml.jackson.databind.JsonNode uv = node.path("uv");
            for (int i = 0; i < FACES.length; i++) {
                com.fasterxml.jackson.databind.JsonNode face = uv.path(FACES[i]);
                if (!face.isObject()) {
                    continue;
                }
                float u = (float) face.path("uv").path(0).asDouble();
                float v = (float) face.path("uv").path(1).asDouble();
                float uw = (float) face.path("uv_size").path(0).asDouble();
                float vh = (float) face.path("uv_size").path(1).asDouble();
                cube.faces[i] = new Uv(u, v, u + uw, v + vh, lit(glow, frame, u, v, u + uw, v + vh));
            }
            return cube;
        }

        private static boolean lit(boolean[] glow, int frame, float u1, float v1, float u2, float v2) {
            if (glow.length == 0 || frame <= 0) {
                return false;
            }
            int x0 = Math.max(0, (int) Math.floor(Math.min(u1, u2)));
            int y0 = Math.max(0, (int) Math.floor(Math.min(v1, v2)));
            int x1 = Math.min(frame - 1, (int) Math.ceil(Math.max(u1, u2)) - 1);
            int y1 = Math.min(frame - 1, (int) Math.ceil(Math.max(v1, v2)) - 1);
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    if (glow[y * frame + x]) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static float[] vec(com.fasterxml.jackson.databind.JsonNode node) {
            return new float[] {
                    (float) node.get(0).asDouble(),
                    (float) node.get(1).asDouble(),
                    (float) node.get(2).asDouble()
            };
        }
    }
}
