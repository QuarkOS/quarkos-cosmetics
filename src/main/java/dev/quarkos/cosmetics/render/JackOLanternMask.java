package dev.quarkos.cosmetics.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
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
    /** One 80×80 candle frame, copied out of the strip. The entity sampler does not advance mcmeta. */
    private static final Identifier GLOW_FRAME = Identifier.fromNamespaceAndPath("quarkos_cosmetics", "dynamic/jack_o_lantern_glow_frame");
    /** One square of the vertical strip. The mcmeta asks for this, but entity textures upload the whole strip. */
    private static final float FRAME = 80f;
    private static final int FRAMES = 24;
    /** Holds each candle frame for this many ticks. Matches the mcmeta frametime, with interpolation off. */
    private static final int FRAME_TICKS = 2;
    private static final String[] FACES = {"north", "south", "east", "west", "up", "down"};

    private static Model model;
    private static boolean failed;
    private static boolean loggedDet;
    /**
     * Combined-shader alpha. 255 is the shell texture. 128 is any carved glow texel.
     * The mask shader keeps dark red under the world's light and leaves orange and cream full bright.
     */
    private static int maskAlpha = 255;
    private static DynamicTexture glowFrame;
    private static int uploadedFrame = -1;
    /** Night spill on the rind around the carving. Off only for the comparison shot. */
    private static boolean spill = true;

    /** Comparison shots turn this off. The cores stay full bright either way. */
    public static void setSpill(boolean on) {
        spill = on;
    }
    /** One shell texel, in bedrock pixels. The candle cubes and the face grid share this step. */
    private static final float TEXEL = 0.9224f;

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
        uploadGlowFrame(loaded, frame);
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
            submitLayers(submitter, pose, view, normals, light, loaded, frame);
        }
        //?} else {
        submitLayers(submitter, pose, view, normals, light, loaded, frame);
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
            uploadGlowFrame(heldModel, heldFrame);
            var source = net.minecraft.client.renderer.MultiBufferSource.immediate(GLOW_BYTES);
            // One mesh. Two immediate draws dropped the candle, and the entity batch lets the rind cover it.
            var consumer = source.getBuffer(MaskGlow.combined(TEXTURE, glowSampler()));
            maskAlpha = 255;
            paint(consumer, heldView, heldNormals, heldLight, heldModel, heldFrame, 0);
            maskAlpha = 128;
            paint(consumer, heldView, heldNormals, heldLight, heldModel, heldFrame, 2);
            // Same alpha as the recess. A lower alpha was dropped, so the flame never reached the shader.
            // The shader tells the candle cores from the red by the texel's own green.
            paint(consumer, heldView, heldNormals, LightTexture.FULL_BRIGHT, heldModel, heldFrame, 1);
            maskAlpha = 255;
            source.endBatch();
            org.lwjgl.opengl.GL11.glFinish();
        } finally {
            com.mojang.blaze3d.systems.RenderSystem.outputColorTextureOverride = previousColor;
            com.mojang.blaze3d.systems.RenderSystem.outputDepthTextureOverride = previousDepth;
            GLOW_BYTES.clear();
            stack.popMatrix();
            // Drop the pose after this frame. Otherwise the last mask keeps painting on later cosmetics.
            heldView = null;
            heldNormals = null;
            heldModel = null;
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

    /**
     * The glow png is an 80×1920 strip. A plain entity texture stays on frame 0, so the candle
     * frame the CPU picked is copied into an 80×80 texture and that is what the mesh samples.
     */
    private static void uploadGlowFrame(Model loaded, int frame) {
        SkinImages.Pixels glow = loaded == null ? null : loaded.glowPixels;
        if (glow == null || glow.width() < (int) FRAME || glow.height() < (int) FRAME * FRAMES) {
            return;
        }
        int index = Math.floorMod(frame, FRAMES);
        if (glowFrame != null && uploadedFrame == index) {
            return;
        }
        NativeImage image = glowFrame == null ? new NativeImage((int) FRAME, (int) FRAME, false) : glowFrame.getPixels();
        if (image == null) {
            return;
        }
        int y0 = index * (int) FRAME;
        for (int y = 0; y < (int) FRAME; y++) {
            for (int x = 0; x < (int) FRAME; x++) {
                int argb = glow.argb()[(y0 + y) * glow.width() + x];
                //? if >=1.21.11 {
                image.setPixel(x, y, argb);
                //?} else {
                /*int alpha = (argb >>> 24) & 255;
                int red = (argb >> 16) & 255;
                int green = (argb >> 8) & 255;
                int blue = argb & 255;
                int abgr = (alpha << 24) | (blue << 16) | (green << 8) | red;
                image.setPixelRGBA(x, y, abgr);
                *///?}
            }
        }
        if (glowFrame == null) {
            //? if >=1.21.11 {
            glowFrame = new DynamicTexture(() -> GLOW_FRAME.toString(), image);
            //?} else {
            glowFrame = new DynamicTexture(image);
            //?}
            Minecraft.getInstance().getTextureManager().register(GLOW_FRAME, glowFrame);
        }
        glowFrame.upload();
        uploadedFrame = index;
    }

    private static Identifier glowSampler() {
        return glowFrame == null ? GLOW : GLOW_FRAME;
    }

    /**
     * Shell texture under the world's light, then the carved glow texels on the same faces.
     * The 25 candle quads are not a face-plane overlay.
     */
    private static void submitLayers(CosmeticDrawer.Submitter submitter, PoseStack pose, Matrix4f view, Matrix3f normals,
                                     int light, Model loaded, int frame) {
        maskAlpha = 255;
        Identifier glow = glowSampler();
        submitter.submit(pose, TEXTURE, CosmeticDrawer.Pass.CUTOUT, consumer -> paint(consumer, view, normals, light, loaded, frame, 0));
        submitter.submit(pose, glow, CosmeticDrawer.Pass.CUTOUT, consumer -> paint(consumer, view, normals, light, loaded, frame, 2));
        submitter.submit(pose, glow, CosmeticDrawer.Pass.GLOW, consumer -> paint(consumer, view, normals, LightTexture.FULL_BRIGHT, loaded, frame, 1));
    }

    //? if >=1.21.11 {
    static void portrait(PoseStack pose, CosmeticDrawer.Submitter submitter, int light) {
        Model loaded = load();
        if (loaded == null) {
            return;
        }
        pose.pushPose();
        pose.mulPose(new Quaternionf().rotationZ((float) Math.PI));
        pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
        Matrix4f view = new Matrix4f(pose.last().pose());
        Matrix3f normals = new Matrix3f(pose.last().normal());
        // Use frame zero of the original strips without touching the player's animated glow texture.
        float shellHeight = loaded.shellPixels == null ? FRAME * FRAMES : loaded.shellPixels.height();
        float glowHeight = loaded.glowPixels == null ? FRAME * FRAMES : loaded.glowPixels.height();
        submitter.submit(pose, TEXTURE, CosmeticDrawer.Pass.CUTOUT,
                consumer -> paint(consumer, view, normals, light, loaded, 0, 0, shellHeight, 255));
        if (loaded.anyGlow) {
            submitter.submit(pose, GLOW, CosmeticDrawer.Pass.CUTOUT, consumer -> {
                paint(consumer, view, normals, light, loaded, 0, 2, glowHeight, 255);
                paint(consumer, view, normals, LightTexture.FULL_BRIGHT, loaded, 0, 1, glowHeight, 255);
            });
        }
        pose.popPose();
    }
    //?}

    /** kind 0 is the shell. kind 1 is the yellow and cream cores. kind 2 is the red recess and orange carved texels. */
    private static void paint(VertexConsumer consumer, Matrix4f view, Matrix3f normals, int light, Model loaded, int frame, int kind) {
        paint(consumer, view, normals, light, loaded, frame, kind, FRAME, kind == 0 ? 255 : maskAlpha);
    }

    private static void paint(VertexConsumer consumer, Matrix4f view, Matrix3f normals, int light, Model loaded,
                              int frame, int kind, float samplerHeight, int alpha) {
        for (Cube cube : loaded.cubes) {
            paintCube(consumer, view, normals, light, loaded, cube, frame, kind, samplerHeight, alpha);
        }
    }

    private static void paintCube(VertexConsumer consumer, Matrix4f view, Matrix3f normals, int light, Model loaded, Cube cube,
                                  int frame, int kind, float samplerHeight, int alpha) {
        float x0 = cube.x0;
        float y0 = cube.y0;
        float z0 = cube.z0;
        float x1 = cube.x1;
        float y1 = cube.y1;
        float z1 = cube.z1;
        // Same outward winding as the chick cubes, in bedrock space.
        face(consumer, view, normals, light, loaded, cube, frame, kind, samplerHeight, alpha, "east", x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0, 1, 0, 0);
        face(consumer, view, normals, light, loaded, cube, frame, kind, samplerHeight, alpha, "west", x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1, -1, 0, 0);
        face(consumer, view, normals, light, loaded, cube, frame, kind, samplerHeight, alpha, "north", x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0, 0, 0, -1);
        face(consumer, view, normals, light, loaded, cube, frame, kind, samplerHeight, alpha, "south", x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1, 0, 0, 1);
        face(consumer, view, normals, light, loaded, cube, frame, kind, samplerHeight, alpha, "up", x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0);
        face(consumer, view, normals, light, loaded, cube, frame, kind, samplerHeight, alpha, "down", x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0);
    }

    private static void face(VertexConsumer consumer, Matrix4f view, Matrix3f normals, int light, Model loaded, Cube cube,
                             int frame, int kind, float samplerHeight, int alpha, String name,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float nx, float ny, float nz) {
        // The 25 candle quads sit at z -6.1, in front of the carving, and their north UVs are
        // one texel each. They are not in the 60-element model. Drawing them caps the holes.
        if (cube.z0 <= -6.05f) {
            return;
        }
        Uv uv = cube.uv(name);
        if (uv == null || kind < 0 || kind > 2) {
            return;
        }
        // GeckoLib keeps an animated mcmeta texture as one 80×80 frame and swaps the pixels itself.
        // Offsetting V down the strip samples empty rows of that frame, so the flame drops out.
        // Every texel samples its own center, so a linear filter cannot pull in a clear neighbor.
        Vector3f normal = new Vector3f(nx, ny, nz);
        normals.transform(normal);
        if (normal.lengthSquared() > 0.0001f) {
            normal.normalize();
        }
        emitTexels(consumer, view, light, loaded, frame, normal, nx, ny, nz, alpha, kind, uv, samplerHeight,
                x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
    }


    /**
     * 0 is a shell texel. 1 is an orange or cream core, green at least 64: the small flame inside
     * the eyes and the mouth. 2 is any other opaque glow texel, the dark-red recess. -1 is a clear
     * hole. The cut stays on that texel's own UV.
     */
    private static int carveClass(Model loaded, int frame, float su, float sv) {
        SkinImages.Pixels glow = loaded.glowPixels;
        if (glow != null) {
            int x = Math.min(glow.width() - 1, Math.max(0, (int) Math.floor(su)));
            int y = Math.min(glow.height() - 1, Math.max(0, Math.floorMod(frame, FRAMES) * (int) FRAME + (int) Math.floor(sv)));
            int argb = glow.argb()[y * glow.width() + x];
            int a = (argb >>> 24) & 255;
            int r = (argb >> 16) & 255;
            int g = (argb >> 8) & 255;
            int b = argb & 255;
            if (a > 10) {
                if (g >= 64 && r > b) {
                    return 1;
                }
                return 2;
            }
        }
        SkinImages.Pixels shellPixels = loaded.shellPixels;
        if (shellPixels == null) {
            return 0;
        }
        int sx = Math.min(shellPixels.width() - 1, Math.max(0, (int) Math.floor(su)));
        int sy = Math.min((int) FRAME - 1, Math.max(0, (int) Math.floor(sv)));
        int shell = shellPixels.argb()[sy * shellPixels.width() + sx];
        if (((shell >>> 24) & 255) < 10) {
            return -1;
        }
        return 0;
    }

    /**
     * Split a face on texel boundaries. Each texel keeps its own UV. The shell uses the world's
     * light. Carved texels use the glow strip's own colour. Yellow and cream cores are full bright.
     */
    private static void emitTexels(VertexConsumer consumer, Matrix4f view, int light, Model loaded, int frame,
                                   Vector3f normal, float nx, float ny, float nz, int alpha, int kind, Uv uv, float samplerHeight,
                                   float x0, float y0, float z0, float x1, float y1, float z1,
                                   float x2, float y2, float z2, float x3, float y3, float z3) {
        float uSpan = uv.u2 - uv.u1;
        float vSpan = uv.v2 - uv.v1;
        if (Math.abs(uSpan) < 1.0e-4f || Math.abs(vSpan) < 1.0e-4f) {
            return;
        }
        float uMin = Math.min(uv.u1, uv.u2);
        float uMax = Math.max(uv.u1, uv.u2);
        float vMin = Math.min(uv.v1, uv.v2);
        float vMax = Math.max(uv.v1, uv.v2);
        int tx0 = (int) Math.floor(uMin);
        int tx1 = (int) Math.ceil(uMax - 1.0e-4f);
        int ty0 = (int) Math.floor(vMin);
        int ty1 = (int) Math.ceil(vMax - 1.0e-4f);
        if (tx1 <= tx0) {
            tx1 = tx0 + 1;
        }
        if (ty1 <= ty0) {
            ty1 = ty0 + 1;
        }
        for (int ty = ty0; ty < ty1; ty++) {
            float sv0 = Math.max(vMin, ty);
            float sv1 = Math.min(vMax, ty + 1f);
            if (sv1 <= sv0) {
                continue;
            }
            float va = (sv0 - uv.v1) / vSpan;
            float vb = (sv1 - uv.v1) / vSpan;
            float vLo = Math.min(va, vb);
            float vHi = Math.max(va, vb);
            for (int tx = tx0; tx < tx1; tx++) {
                float su0 = Math.max(uMin, tx);
                float su1 = Math.min(uMax, tx + 1f);
                if (su1 <= su0) {
                    continue;
                }
                float ua = (su0 - uv.u1) / uSpan;
                float ub = (su1 - uv.u1) / uSpan;
                float uLo = Math.min(ua, ub);
                float uHi = Math.max(ua, ub);
                int cls = carveClass(loaded, frame, tx + 0.5f, ty + 0.5f);
                if (kind == 0 ? cls != 0 : kind == 1 ? cls != 1 : cls != 2) {
                    continue;
                }
                float[] p00 = bilerp(uLo, vLo, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
                float[] p10 = bilerp(uHi, vLo, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
                float[] p11 = bilerp(uHi, vHi, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
                float[] p01 = bilerp(uLo, vHi, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
                int texelLight = kind == 1 ? LightTexture.FULL_BRIGHT : light;
                if (kind != 1 && spill) {
                    float cx = (p00[0] + p10[0] + p11[0] + p01[0]) * 0.25f;
                    float cy = (p00[1] + p10[1] + p11[1] + p01[1]) * 0.25f;
                    float cz = (p00[2] + p10[2] + p11[2] + p01[2]) * 0.25f;
                    texelLight = spillLight(loaded, frame, light, kind, cx, cy, cz);
                }
                float tu = (tx + 0.5f) / FRAME;
                float tv = (ty + 0.5f) / samplerHeight;
                float overlap = kind == 0 ? SHELL_OVERLAP : 0.02f;
                // The flame sits a hair in front of the red recess so the two do not fight for depth.
                float bias = kind == 1 ? 0.08f : kind == 2 ? 0.04f : 0f;
                emit(consumer, view, texelLight, normal, bias, nx, ny, nz, alpha, tu, tv, tu, tv, overlap, 0f,
                        p00[0], p00[1], p00[2], p10[0], p10[1], p10[2],
                        p11[0], p11[1], p11[2], p01[0], p01[1], p01[2]);
            }
        }
    }

    /**
     * Spill reaches this many texels past a carving quad. Three steps, so the rind is banded
     * and the outer band is gone on the dim candle frame.
     */
    private static final float SPILL_RADIUS = 1.55f;
    private static final int SPILL_STEPS = 3;
    /** Closest shell step sits this many block-light levels under the candle. Cores stay full bright. */
    private static final int SPILL_FIRST_DROP = 5;
    private static final int SPILL_STEP_DROP = 2;

    /**
     * Block light for a shell or recess texel near the eyes and mouth. Day sky light is left alone.
     * A recess is one step dimmer than the rind at the same distance. Outside the radius, the world wins.
     */
    private static int spillLight(Model loaded, int frame, int world, int kind, float x, float y, float z) {
        // Sky light level stays 15 on the surface at night. The lightmap darkens that column.
        // Block light is what still glows, so the spill is extra block light, not a sky test.
        float dist = openingDistance(loaded, x, y, z);
        if (dist == Float.MAX_VALUE) {
            return world;
        }
        float texels = dist / TEXEL;
        if (texels >= SPILL_RADIUS) {
            return world;
        }
        int step = (int) Math.floor(texels / (SPILL_RADIUS / SPILL_STEPS));
        if (step >= SPILL_STEPS) {
            step = SPILL_STEPS - 1;
        }
        int candle = 0;
        if (loaded.frameLight != null && loaded.frameLight.length > 0) {
            candle = loaded.frameLight[Math.floorMod(frame, loaded.frameLight.length)];
        }
        int drop = SPILL_FIRST_DROP + step * SPILL_STEP_DROP + (kind == 2 ? 2 : 0);
        int block = candle - drop;
        if (block <= 0) {
            return world;
        }
        if (block <= LightTexture.block(world)) {
            return world;
        }
        return LightTexture.pack(block, LightTexture.sky(world));
    }

    /**
     * In-plane only. Neighbors share a covered edge so a raster crack cannot show the dark
     * cavity. Z is not grown, so the margin is not a second surface at a new depth.
     */
    private static final float SHELL_OVERLAP = 0.05f;

    /** Distance from a point to the nearest carving box. Zero means the point is inside one. */
    private static float openingDistance(Model loaded, float x, float y, float z) {
        float[] openings = loaded.openings;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < openings.length; i += 6) {
            float dx = outside(x, openings[i], openings[i + 3]);
            float dy = outside(y, openings[i + 1], openings[i + 4]);
            float dz = outside(z, openings[i + 2], openings[i + 5]);
            float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < best) {
                best = dist;
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

    private static float[] bilerp(float u, float v,
                                  float x0, float y0, float z0, float x1, float y1, float z1,
                                  float x2, float y2, float z2, float x3, float y3, float z3) {
        float x = (1f - v) * ((1f - u) * x0 + u * x1) + v * ((1f - u) * x3 + u * x2);
        float y = (1f - v) * ((1f - u) * y0 + u * y1) + v * ((1f - u) * y3 + u * y2);
        float z = (1f - v) * ((1f - u) * z0 + u * z1) + v * ((1f - u) * z3 + u * z2);
        return new float[] {x, y, z};
    }

    private static void emit(VertexConsumer consumer, Matrix4f view, int light, Vector3f normal, float bias,
                             float nx, float ny, float nz, int alpha,
                             float u0, float v0, float u1, float v1, float overlap, float zNudge,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        // Push each corner out so neighboring quads overlap and the rasterizer cannot leave a crack.
        float[] xs = {x0, x1, x2, x3};
        float[] ys = {y0, y1, y2, y3};
        float[] zs = {z0, z1, z2, z3};
        float cx = (x0 + x1 + x2 + x3) * 0.25f;
        float cy = (y0 + y1 + y2 + y3) * 0.25f;
        float cz = (z0 + z1 + z2 + z3) * 0.25f;
        for (int i = 0; i < 4; i++) {
            float dx = xs[i] - cx;
            float dy = ys[i] - cy;
            float dz = zs[i] - cz;
            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len > 1.0e-4f && overlap > 0f) {
                float scale = overlap / len;
                xs[i] += dx * scale;
                ys[i] += dy * scale;
                // Z stays put. Growing it laid the next strip on this one and the two hatched.
            }
            zs[i] -= zNudge;
        }
        vertex(consumer, view, xs[0] + nx * bias, ys[0] + ny * bias, zs[0] + nz * bias, u0, v0, light, alpha, normal);
        vertex(consumer, view, xs[1] + nx * bias, ys[1] + ny * bias, zs[1] + nz * bias, u1, v0, light, alpha, normal);
        vertex(consumer, view, xs[2] + nx * bias, ys[2] + ny * bias, zs[2] + nz * bias, u1, v1, light, alpha, normal);
        vertex(consumer, view, xs[3] + nx * bias, ys[3] + ny * bias, zs[3] + nz * bias, u0, v1, light, alpha, normal);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f view, float x, float y, float z,
                               float u, float v, int light, int alpha, Vector3f normal) {
        consumer.addVertex(view, x, y, z)
                .setColor(255, 255, 255, alpha)
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
        /** Frame 0 of the shell. Hole colours on the carving lip are covered with flame. */
        final SkinImages.Pixels shellPixels;
        /** x0, y0, z0, x1, y1, z1 of each carving cube. */
        final float[] openings;
        /** Block light of a bright candle frame, one entry per glow frame. Rings subtract from this. */
        final int[] frameLight;

        Model(float textureWidth, float textureHeight, List<Cube> cubes, boolean anyGlow, SkinImages.Pixels glowPixels,
              SkinImages.Pixels shellPixels, float[] openings, int[] frameLight) {
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
            this.cubes = cubes;
            this.anyGlow = anyGlow;
            this.glowPixels = glowPixels;
            this.shellPixels = shellPixels;
            this.openings = openings;
            this.frameLight = frameLight;
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
            SkinImages.Pixels shellPixels = SkinImages.read(TEXTURE);
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
            float[] openings = openingsOf(cubes);
            int[] frameLight = frameLight(glowPixels);
            LOGGER.info("mask cubes {} glow faces {} glow texels {} candle steps {}", cubes.size(), lit, texels, java.util.Arrays.toString(frameLight));
            return new Model(width, height, cubes, texels > 0 && lit > 0, glowPixels, shellPixels, openings, frameLight);
        }

        private static float[] openingsOf(List<Cube> cubes) {
            int count = 0;
            for (Cube cube : cubes) {
                if (cube.z0 <= -6.05f) {
                    count++;
                }
            }
            float[] openings = new float[count * 6];
            int i = 0;
            for (Cube cube : cubes) {
                if (cube.z0 > -6.05f) {
                    continue;
                }
                openings[i++] = cube.x0;
                openings[i++] = cube.y0;
                openings[i++] = cube.z0;
                openings[i++] = cube.x1;
                openings[i++] = cube.y1;
                openings[i++] = cube.z1;
            }
            return openings;
        }

        /** Brightest opaque texel of each glow frame, as a block-light level. Frames do not blend. */
        private static int[] frameLight(SkinImages.Pixels pixels) {
            int[] levels = new int[FRAMES];
            if (pixels == null) {
                java.util.Arrays.fill(levels, 15);
                return levels;
            }
            int frame = (int) FRAME;
            for (int f = 0; f < FRAMES; f++) {
                int y0 = f * frame;
                float max = 0f;
                if (y0 + frame <= pixels.height()) {
                    for (int y = 0; y < frame; y++) {
                        for (int x = 0; x < frame && x < pixels.width(); x++) {
                            int argb = pixels.argb()[(y0 + y) * pixels.width() + x];
                            if (((argb >>> 24) & 255) == 0) {
                                continue;
                            }
                            int red = (argb >> 16) & 255;
                            int green = (argb >> 8) & 255;
                            int blue = argb & 255;
                            float luma = 0.299f * red + 0.587f * green + 0.114f * blue;
                            if (luma > max) {
                                max = luma;
                            }
                        }
                    }
                }
                int level = Math.round(max * 15f / 240f);
                if (level < 0) {
                    level = 0;
                } else if (level > 15) {
                    level = 15;
                }
                levels[f] = level;
            }
            return levels;
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
