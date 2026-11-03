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
    private static final String[] FACES = {"north", "south", "east", "west", "up", "down"};

    private static Model model;
    private static boolean failed;
    private static boolean loggedDet;
    /** 0 only while the combined mask shader is drawing the candle. Eyes still wants opaque vertices. */
    private static int candleAlpha = 255;
    /** Shell vertices store falloff in red while the combined mask shader is drawing the rind. */
    private static boolean shellBleed;
    /** x0, y0, x1, y1 of each candle cube. The glow png is not rewritten; this is the opening list. */
    private static float[] candleBoxes;
    /** Width of the gaussian halo, in bedrock pixels. */
    private static final float BLEED_SIGMA = 1.55f;
    /** Front of the rind. Surfaces behind this fade as they wrap toward the back. */
    private static final float BLEED_FRONT = -5.55f;
    private static final float BLEED_Z = 0.8f;

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
        //? if >=1.21.11 {
        if (deferred) {
            // The entity batch drops the mask on some frames. Redraw it after the world, pose held from this submit.
            hold(view, normals, frame, loaded, light);
        } else {
            submitter.submit(pose, TEXTURE, CosmeticDrawer.Pass.CUTOUT, consumer -> paint(consumer, view, normals, light, loaded, frame, 0));
            submitter.submit(pose, GLOW, CosmeticDrawer.Pass.GLOW, consumer -> paint(consumer, view, normals, LightTexture.FULL_BRIGHT, loaded, frame, 1));
        }
        //?} else {
        submitter.submit(pose, TEXTURE, CosmeticDrawer.Pass.CUTOUT, consumer -> paint(consumer, view, normals, light, loaded, frame, 0));
        submitter.submit(pose, GLOW, CosmeticDrawer.Pass.GLOW, consumer -> paint(consumer, view, normals, LightTexture.FULL_BRIGHT, loaded, frame, 1));
        //?}
        pose.popPose();
    }

    //? if >=1.21.11 {
    private static boolean deferred;
    private static Matrix4f heldView;
    private static Matrix3f heldNormals;
    private static int heldFrame;
    private static int heldLight;
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
        Matrix4f viewRotation = new Matrix4f().rotation(camera.rotation().conjugate());
        stack.identity().mul(viewRotation);
        com.mojang.blaze3d.systems.RenderSystem.disableScissorForRenderTypeDraws();
        if (!loggedFlush) {
            loggedFlush = true;
            LOGGER.info("mask glow flush frame {}", heldFrame);
        }
        // The world may still be aimed at another color target. The screenshot reads the main
        // target, so the candle has to land there or whole frames come back with the rind only.
        var main = net.minecraft.client.Minecraft.getInstance().getMainRenderTarget();
        var previousColor = com.mojang.blaze3d.systems.RenderSystem.outputColorTextureOverride;
        var previousDepth = com.mojang.blaze3d.systems.RenderSystem.outputDepthTextureOverride;
        com.mojang.blaze3d.systems.RenderSystem.outputColorTextureOverride = main.getColorTextureView();
        com.mojang.blaze3d.systems.RenderSystem.outputDepthTextureOverride = main.getDepthTextureView();
        GLOW_BYTES.clear();
        try {
            var source = net.minecraft.client.renderer.MultiBufferSource.immediate(GLOW_BYTES);
            //? if fabric {
            // One mesh. The rind and the candle used to be two immediate draws, and the second one dropped out.
            var consumer = source.getBuffer(MaskGlow.combined(TEXTURE, GLOW));
            shellBleed = true;
            try {
                paint(consumer, heldView, heldNormals, heldLight, heldModel, heldFrame, 0);
            } finally {
                shellBleed = false;
            }
            candleAlpha = 0;
            try {
                paint(consumer, heldView, heldNormals, LightTexture.FULL_BRIGHT, heldModel, heldFrame, 1);
            } finally {
                candleAlpha = 255;
            }
            source.endBatch();
            //?} else {
            paint(source.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(TEXTURE)), heldView, heldNormals, heldLight, heldModel, heldFrame, 0);
            source.endBatch();
            paint(source.getBuffer(MaskGlow.type(GLOW)), heldView, heldNormals, LightTexture.FULL_BRIGHT, heldModel, heldFrame, 1);
            source.endBatch();
            //?}
            org.lwjgl.opengl.GL11.glFinish();
        } finally {
            com.mojang.blaze3d.systems.RenderSystem.outputColorTextureOverride = previousColor;
            com.mojang.blaze3d.systems.RenderSystem.outputDepthTextureOverride = previousDepth;
            GLOW_BYTES.clear();
            stack.popMatrix();
        }
    }

    private static void hold(Matrix4f view, Matrix3f normals, int frame, Model loaded, int light) {
        heldView = new Matrix4f(view);
        heldNormals = new Matrix3f(normals);
        heldFrame = frame;
        heldLight = light;
        heldModel = loaded;
    }
    //?}

    /** kind 0 is the rind. kind 1 is the emissive lip and the side walls of the carving. */
    private static void paint(VertexConsumer consumer, Matrix4f view, Matrix3f normals, int light, Model loaded, int frame, int kind) {
        if (shellBleed) {
            rememberCandles(loaded);
        }
        for (Cube cube : loaded.cubes) {
            paintCube(consumer, view, normals, light, loaded, cube, frame, kind);
        }
    }

    private static void paintCube(VertexConsumer consumer, Matrix4f view, Matrix3f normals, int light, Model loaded, Cube cube, int frame, int kind) {
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

    private static void face(VertexConsumer consumer, Matrix4f view, Matrix3f normals, int light, Model loaded, Cube cube,
                             int frame, int kind, String name,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float nx, float ny, float nz) {
        // The ~1px cubes sit at z = -6.1, in the eye, nose, and mouth openings.
        // Only their north face is the flame. The carving walls sample the same texels
        // and come out as bars beside the cut-outs.
        boolean candleCube = cube.z0 <= -6.05f;
        Uv uv;
        if (candleCube) {
            if (kind == 0 || !"north".equals(name)) {
                return;
            }
            uv = cube.uv("north");
        } else {
            if (kind != 0) {
                return;
            }
            uv = cube.uv(name);
        }
        if (uv == null || (kind != 0 && loaded.anyGlow && !uv.glow)) {
            return;
        }
        // GeckoLib keeps an animated mcmeta texture as one 80×80 frame and swaps the pixels itself.
        // Offsetting V down the strip samples empty rows of that frame, so the flame drops out.
        // Candle faces are one texel. Inset so nearest-filtering cannot step onto a clear neighbor.
        float inset = candleCube ? 0.08f : 0f;
        float u0 = (uv.u1 + inset) / FRAME;
        float u1 = (uv.u2 - inset) / FRAME;
        float v0 = (uv.v1 + inset) / FRAME;
        float v1 = (uv.v2 - inset) / FRAME;
        Vector3f normal = new Vector3f(nx, ny, nz);
        normals.transform(normal);
        if (normal.lengthSquared() > 0.0001f) {
            normal.normalize();
        }
        // In front of the rind at z = -6.1, so the flame wins the depth test in the cut-out.
        // The geo origin stays at -6.1. Alpha 0 marks a candle vertex for the mask shader.
        float bias = 0f;
        float pull = kind == 0 ? 0f : 0.25f;
        int alpha = kind == 0 ? 255 : candleAlpha;
        float z0p = z0 - pull;
        float z1p = z1 - pull;
        float z2p = z2 - pull;
        float z3p = z3 - pull;
        if (shellBleed) {
            // Large rind faces would turn the gaussian into a straight ramp. A ~1px grid keeps the curve.
            emitGrid(consumer, view, light, normal, bias, nx, ny, nz, alpha, u0, v0, u1, v1,
                    x0, y0, z0p, x1, y1, z1p, x2, y2, z2p, x3, y3, z3p);
        } else {
            emit(consumer, view, light, normal, bias, nx, ny, nz, alpha, u0, v0, u1, v1,
                    x0, y0, z0p, x1, y1, z1p, x2, y2, z2p, x3, y3, z3p);
        }
    }

    /** Distance to the nearest candle cube, as a gaussian. 1 on the lip of an opening, fading across the rind. */
    private static float bleedAt(float x, float y, float z) {
        if (candleBoxes == null || candleBoxes.length == 0) {
            return 0f;
        }
        float best = 0f;
        float denom = 2f * BLEED_SIGMA * BLEED_SIGMA;
        for (int i = 0; i < candleBoxes.length; i += 4) {
            float dx = outside(x, candleBoxes[i], candleBoxes[i + 2]);
            float dy = outside(y, candleBoxes[i + 1], candleBoxes[i + 3]);
            float dz = Math.max(0f, (z - BLEED_FRONT) * BLEED_Z);
            float g = (float) Math.exp(-(dx * dx + dy * dy + dz * dz) / denom);
            if (g > best) {
                best = g;
            }
        }
        return best;
    }

    private static float outside(float p, float a, float b) {
        if (p < a) {
            return a - p;
        }
        if (p > b) {
            return p - b;
        }
        return 0f;
    }

    private static void rememberCandles(Model loaded) {
        if (candleBoxes != null) {
            return;
        }
        int count = 0;
        for (Cube cube : loaded.cubes) {
            if (cube.z0 <= -6.05f) {
                count++;
            }
        }
        float[] boxes = new float[count * 4];
        int i = 0;
        for (Cube cube : loaded.cubes) {
            if (cube.z0 > -6.05f) {
                continue;
            }
            boxes[i++] = cube.x0;
            boxes[i++] = cube.y0;
            boxes[i++] = cube.x1;
            boxes[i++] = cube.y1;
        }
        candleBoxes = boxes;
    }

    private static void emitGrid(VertexConsumer consumer, Matrix4f view, int light, Vector3f normal, float bias,
                                 float nx, float ny, float nz, int alpha,
                                 float u0, float v0, float u1, float v1,
                                 float x0, float y0, float z0, float x1, float y1, float z1,
                                 float x2, float y2, float z2, float x3, float y3, float z3) {
        float spanU = distance(x0, y0, z0, x1, y1, z1);
        float spanV = distance(x0, y0, z0, x3, y3, z3);
        int nu = Math.min(24, Math.max(1, Math.round(spanU / 1.1f)));
        int nv = Math.min(24, Math.max(1, Math.round(spanV / 1.1f)));
        if (nu == 1 && nv == 1) {
            emit(consumer, view, light, normal, bias, nx, ny, nz, alpha, u0, v0, u1, v1,
                    x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
            return;
        }
        for (int j = 0; j < nv; j++) {
            float va = j / (float) nv;
            float vb = (j + 1) / (float) nv;
            for (int i = 0; i < nu; i++) {
                float ua = i / (float) nu;
                float ub = (i + 1) / (float) nu;
                float[] p00 = bilerp(ua, va, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
                float[] p10 = bilerp(ub, va, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
                float[] p11 = bilerp(ub, vb, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
                float[] p01 = bilerp(ua, vb, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
                emit(consumer, view, light, normal, bias, nx, ny, nz, alpha,
                        lerp(u0, u1, ua), lerp(v0, v1, va), lerp(u0, u1, ub), lerp(v0, v1, vb),
                        p00[0], p00[1], p00[2], p10[0], p10[1], p10[2],
                        p11[0], p11[1], p11[2], p01[0], p01[1], p01[2]);
            }
        }
    }

    private static float[] bilerp(float u, float v,
                                  float x0, float y0, float z0, float x1, float y1, float z1,
                                  float x2, float y2, float z2, float x3, float y3, float z3) {
        float x = (1f - v) * ((1f - u) * x0 + u * x1) + v * ((1f - u) * x3 + u * x2);
        float y = (1f - v) * ((1f - u) * y0 + u * y1) + v * ((1f - u) * y3 + u * y2);
        float z = (1f - v) * ((1f - u) * z0 + u * z1) + v * ((1f - u) * z3 + u * z2);
        return new float[] {x, y, z};
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float distance(float x0, float y0, float z0, float x1, float y1, float z1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float dz = z1 - z0;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static void emit(VertexConsumer consumer, Matrix4f view, int light, Vector3f normal, float bias,
                             float nx, float ny, float nz, int alpha,
                             float u0, float v0, float u1, float v1,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        vertex(consumer, view, x0 + nx * bias, y0 + ny * bias, z0 + nz * bias, u0, v0, light, alpha, normal);
        vertex(consumer, view, x1 + nx * bias, y1 + ny * bias, z1 + nz * bias, u1, v0, light, alpha, normal);
        vertex(consumer, view, x2 + nx * bias, y2 + ny * bias, z2 + nz * bias, u1, v1, light, alpha, normal);
        vertex(consumer, view, x3 + nx * bias, y3 + ny * bias, z3 + nz * bias, u0, v1, light, alpha, normal);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f view, float x, float y, float z,
                               float u, float v, int light, int alpha, Vector3f normal) {
        int red = 255;
        int green = 255;
        int blue = 255;
        if (shellBleed) {
            int bleed = Math.round(bleedAt(x, y, z) * 255f);
            if (bleed < 0) {
                bleed = 0;
            } else if (bleed > 255) {
                bleed = 255;
            }
            red = bleed;
            green = bleed;
            blue = bleed;
        }
        consumer.addVertex(view, x, y, z)
                .setColor(red, green, blue, alpha)
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
