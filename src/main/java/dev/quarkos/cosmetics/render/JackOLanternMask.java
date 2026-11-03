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
            // One mesh. Two immediate draws dropped the candle, and the entity batch lets the rind cover it.
            var consumer = source.getBuffer(MaskGlow.combined(TEXTURE, GLOW));
            paint(consumer, heldView, heldNormals, heldLight, heldModel, heldFrame, 0);
            candleAlpha = 0;
            try {
                paint(consumer, heldView, heldNormals, LightTexture.FULL_BRIGHT, heldModel, heldFrame, 1);
            } finally {
                candleAlpha = 255;
            }
            source.endBatch();
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

    /** kind 0 is the rind. kind 1 is the emissive candle. */
    private static void paint(VertexConsumer consumer, Matrix4f view, Matrix3f normals, int light, Model loaded, int frame, int kind) {
        for (Cube cube : loaded.cubes) {
            paintCube(consumer, view, normals, light, loaded, cube, frame, kind);
        }
        if (kind == 0) {
            emitCarveWalls(consumer, view, normals, light, loaded, frame);
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
            uv = cube.uv(name);
        }
        if (candleCube && (uv == null || (loaded.anyGlow && !uv.glow))) {
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
        // In front of the rind at z = -6.1, so the flame wins the depth test in the cut-out.
        // The geo origin stays at -6.1. Alpha 0 marks a candle vertex for the mask shader.
        float bias = 0f;
        float pull = kind == 0 ? 0f : 0.25f;
        int alpha = kind == 0 ? 255 : candleAlpha;
        float z0p = z0 - pull;
        float z1p = z1 - pull;
        float z2p = z2 - pull;
        float z3p = z3 - pull;
        if (candleCube) {
            float cu = (uv.u1 + uv.u2) * 0.5f / FRAME;
            float cv = (uv.v1 + uv.v2) * 0.5f / FRAME;
            // Half a texel of overlap. The carving-edge shell is a couple of pixels past the cube.
            emit(consumer, view, light, normal, bias, nx, ny, nz, alpha, cu, cv, cu, cv, CANDLE_OVERLAP,
                    nudge(x0, y0),
                    x0, y0, z0p, x1, y1, z1p, x2, y2, z2p, x3, y3, z3p);
            return;
        }
        if (kind != 0) {
            return;
        }
        if (uv == null) {
            return;
        }
        // One quad per texture texel, every corner the same block light, so the step does not blend inside the texel.
        emitTexels(consumer, view, light, loaded, frame, normal, bias, nx, ny, nz, alpha, uv,
                x0, y0, z0p, x1, y1, z1p, x2, y2, z2p, x3, y3, z3p);
    }

    /** Shell texel (13, 15), pumpkin flesh on every frame. Walls sample it instead of a new texture. */
    private static final float FLESH_U = 13.5f / 80f;
    private static final float FLESH_V = 15.5f / 80f;
    /**
     * Just in front of the rind (z = -5.996) and behind the pulled flame. The back stops short of
     * the dark inner face at z = -4.612 so the two do not fight. Span is about one texel.
     */
    private static final float WALL_FRONT = -6.12f;
    private static final float WALL_BACK = -5.05f;

    /**
     * Inner faces of each cut. A one-texel flesh rim runs from the front of the rind back into the
     * cavity, brightest against the flame and one block-light level dimmer on the inner strip.
     * Every opening cell, including the cells behind the flame, has a lit back so an angled view
     * cannot see the dark inner wall.
     */
    private static void emitCarveWalls(VertexConsumer consumer, Matrix4f view, Matrix3f normals, int light, Model loaded, int frame) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        int candles = 0;
        for (Cube cube : loaded.cubes) {
            if (cube.z0 > -6.05f) {
                continue;
            }
            candles++;
            minX = Math.min(minX, cube.x0);
            minY = Math.min(minY, cube.y0);
            maxX = Math.max(maxX, cube.x1);
            maxY = Math.max(maxY, cube.y1);
        }
        if (candles == 0) {
            return;
        }
        minX -= TEXEL;
        minY -= TEXEL;
        maxX += TEXEL;
        maxY += TEXEL;
        int nx = Math.max(1, Math.round((maxX - minX) / TEXEL));
        int ny = Math.max(1, Math.round((maxY - minY) / TEXEL));
        boolean[] open = new boolean[nx * ny];
        for (int iy = 0; iy < ny; iy++) {
            float cy = minY + (iy + 0.5f) * TEXEL;
            for (int ix = 0; ix < nx; ix++) {
                float cx = minX + (ix + 0.5f) * TEXEL;
                open[iy * nx + ix] = openingCell(loaded, cx, cy);
            }
        }
        int candle = loaded.frameLight[Math.floorMod(frame, loaded.frameLight.length)];
        float pad = 0.04f;
        for (int iy = 0; iy < ny; iy++) {
            float y0 = minY + iy * TEXEL;
            float y1 = y0 + TEXEL;
            float cy = (y0 + y1) * 0.5f;
            for (int ix = 0; ix < nx; ix++) {
                if (!open[iy * nx + ix]) {
                    continue;
                }
                float x0 = minX + ix * TEXEL;
                float x1 = x0 + TEXEL;
                int far = wallLight(light, candle, 1);
                // Flat in Z, so the overlap grows in X and Y only and hides the seam between cells.
                emitFlesh(consumer, view, normals, far, 0f, 0f, -1f, 0.04f,
                        x0, y1, WALL_BACK, x1, y1, WALL_BACK, x1, y0, WALL_BACK, x0, y0, WALL_BACK);
                // A hair inside the hole so the rim covers the shell's dark side without sitting on it.
                float inset = 0.03f;
                if (!openAt(open, nx, ny, ix - 1, iy)) {
                    emitWallX(consumer, view, normals, light, candle, x0 + inset, y0 - pad, y1 + pad, 1f);
                }
                if (!openAt(open, nx, ny, ix + 1, iy)) {
                    emitWallX(consumer, view, normals, light, candle, x1 - inset, y0 - pad, y1 + pad, -1f);
                }
                if (!openAt(open, nx, ny, ix, iy - 1)) {
                    emitWallY(consumer, view, normals, light, candle, y0 + inset, x0 - pad, x1 + pad, 1f);
                }
                if (!openAt(open, nx, ny, ix, iy + 1)) {
                    emitWallY(consumer, view, normals, light, candle, y1 - inset, x0 - pad, x1 + pad, -1f);
                }
            }
        }
    }

    private static boolean openAt(boolean[] open, int nx, int ny, int ix, int iy) {
        if (ix < 0 || iy < 0 || ix >= nx || iy >= ny) {
            return false;
        }
        return open[iy * nx + ix];
    }

    /** Candle cells, plus the one-texel gaps between them. The front rind stays outside the cut. */
    private static boolean openingCell(Model loaded, float x, float y) {
        if (insideCandle(loaded, x, y)) {
            return true;
        }
        if (frontCovers(loaded, x, y)) {
            return false;
        }
        return bestXY(loaded, x, y) <= TEXEL * 0.8f;
    }

    private static boolean insideCandle(Model loaded, float x, float y) {
        float[] openings = loaded.openings;
        for (int i = 0; i < openings.length; i += 6) {
            if (x >= openings[i] - 0.02f && x <= openings[i + 3] + 0.02f
                    && y >= openings[i + 1] - 0.02f && y <= openings[i + 4] + 0.02f) {
                return true;
            }
        }
        return false;
    }

    /** Front-shell cubes only. Deeper cavity faces are not the rind. */
    private static boolean frontCovers(Model loaded, float x, float y) {
        for (Cube cube : loaded.cubes) {
            if (cube.z0 <= -6.05f || cube.z0 > -5.5f) {
                continue;
            }
            if (x >= cube.x0 - 0.02f && x <= cube.x1 + 0.02f && y >= cube.y0 - 0.02f && y <= cube.y1 + 0.02f) {
                return true;
            }
        }
        return false;
    }

    /** A wall along X = constant. sign +1 faces into an opening on the east of this edge. */
    private static void emitWallX(VertexConsumer consumer, Matrix4f view, Matrix3f normals, int world, int candle,
                                  float x, float y0, float y1, float sign) {
        float step = (WALL_BACK - WALL_FRONT) * 0.5f;
        for (int depth = 0; depth < 2; depth++) {
            float z = WALL_FRONT + depth * step;
            float z1 = depth == 1 ? WALL_BACK : z + step;
            int lit = wallLight(world, candle, depth);
            if (sign > 0f) {
                emitFlesh(consumer, view, normals, lit, 1f, 0f, 0f, 0f,
                        x, y1, z, x, y1, z1, x, y0, z1, x, y0, z);
            } else {
                emitFlesh(consumer, view, normals, lit, -1f, 0f, 0f, 0f,
                        x, y1, z1, x, y1, z, x, y0, z, x, y0, z1);
            }
        }
    }

    /** A wall along Y = constant. sign +1 faces into an opening above this edge. */
    private static void emitWallY(VertexConsumer consumer, Matrix4f view, Matrix3f normals, int world, int candle,
                                  float y, float x0, float x1, float sign) {
        float step = (WALL_BACK - WALL_FRONT) * 0.5f;
        for (int depth = 0; depth < 2; depth++) {
            float z = WALL_FRONT + depth * step;
            float z1 = depth == 1 ? WALL_BACK : z + step;
            int lit = wallLight(world, candle, depth);
            if (sign > 0f) {
                emitFlesh(consumer, view, normals, lit, 0f, 1f, 0f, 0f,
                        x0, y, z1, x1, y, z1, x1, y, z, x0, y, z);
            } else {
                emitFlesh(consumer, view, normals, lit, 0f, -1f, 0f, 0f,
                        x0, y, z, x1, y, z, x1, y, z1, x0, y, z1);
            }
        }
    }

    /** Nearest the candle is candle minus 1. Each texel of depth drops one, and ring 1 is the floor. */
    private static int wallLight(int world, int candle, int depth) {
        int block = candle - 1 - depth;
        int floor = candle - RING_DROP;
        if (block < floor) {
            block = floor;
        }
        if (block < 0) {
            block = 0;
        } else if (block > 15) {
            block = 15;
        }
        if (block <= LightTexture.block(world)) {
            return world;
        }
        return LightTexture.pack(block, LightTexture.sky(world));
    }

    private static void emitFlesh(VertexConsumer consumer, Matrix4f view, Matrix3f normals, int light,
                                  float nx, float ny, float nz, float overlap,
                                  float x0, float y0, float z0, float x1, float y1, float z1,
                                  float x2, float y2, float z2, float x3, float y3, float z3) {
        Vector3f normal = new Vector3f(nx, ny, nz);
        normals.transform(normal);
        if (normal.lengthSquared() > 0.0001f) {
            normal.normalize();
        }
        emit(consumer, view, light, normal, 0f, nx, ny, nz, 255, FLESH_U, FLESH_V, FLESH_U, FLESH_V, overlap, 0f,
                x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
    }

    /**
     * Split a rind face on texel boundaries. A texel's step is its Euclidean distance, in
     * texel-widths, to the nearest carving. Five rings span about three texels: the first is
     * the candle frame minus 3, and each ring after that is one block-light level dimmer.
     * Farther texels keep the world's light. The candle frame picks the top level, so the
     * rings jump together when the flame steps. Every corner of a texel shares that one level.
     */
    private static void emitTexels(VertexConsumer consumer, Matrix4f view, int light, Model loaded, int frame,
                                   Vector3f normal, float bias, float nx, float ny, float nz, int alpha, Uv uv,
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
        int candle = loaded.frameLight[Math.floorMod(frame, loaded.frameLight.length)];
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
                float[] center = bilerp((uLo + uHi) * 0.5f, (vLo + vHi) * 0.5f,
                        x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
                int texelLight = ringLight(light, ringOf(loaded, center[0], center[1], center[2]), candle);
                float[] p00 = bilerp(uLo, vLo, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
                float[] p10 = bilerp(uHi, vLo, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
                float[] p11 = bilerp(uHi, vHi, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
                float[] p01 = bilerp(uLo, vHi, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
                // One sample at the texel center. A span would blend the neighbor, including clear black.
                float tu = (tx + 0.5f) / FRAME;
                float tv = (ty + 0.5f) / FRAME;
                emit(consumer, view, texelLight, normal, bias, nx, ny, nz, alpha, tu, tv, tu, tv, SHELL_OVERLAP, 0f,
                        p00[0], p00[1], p00[2], p10[0], p10[1], p10[2],
                        p11[0], p11[1], p11[2], p01[0], p01[1], p01[2]);
            }
        }
    }

    /**
     * North-face texels that sit between candle cubes. They are drawn with the nearest
     * candle texel so the eyes, nose, and mouth stay one shape.
     */
    private static void emitOpeningGaps(VertexConsumer consumer, Matrix4f view, int light, Model loaded,
                                        Vector3f normal, float bias, float nx, float ny, float nz, int alpha, Uv uv,
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
                float[] center = bilerp((uLo + uHi) * 0.5f, (vLo + vHi) * 0.5f,
                        x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
                // Judge the rim in the front plane. A deep wall can sit a texel beside the
                // candle and still be inside the carving. Near-black shell texels on that rim
                // stay black under ring light, so they take the candle instead.
                float xy = bestXY(loaded, center[0], center[1]);
                boolean lip = center[2] > -6.8f && center[2] < -4.5f
                        && xy <= TEXEL * 0.95f
                        && darkShell(loaded, tx, ty);
                if (xy > TEXEL * 0.62f
                        && !enclosedGap(loaded, center[0], center[1], center[2])
                        && !lip) {
                    continue;
                }
                Uv glow = nearestCandleUv(loaded, center[0], center[1]);
                if (glow == null) {
                    continue;
                }
                float[] p00 = bilerp(uLo, vLo, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
                float[] p10 = bilerp(uHi, vLo, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
                float[] p11 = bilerp(uHi, vHi, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
                float[] p01 = bilerp(uLo, vHi, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
                float cu = (glow.u1 + glow.u2) * 0.5f / FRAME;
                float cv = (glow.v1 + glow.v2) * 0.5f / FRAME;
                emit(consumer, view, light, normal, bias, nx, ny, nz, alpha, cu, cv, cu, cv, CANDLE_OVERLAP,
                        nudge(center[0], center[1]),
                        p00[0], p00[1], p00[2], p10[0], p10[1], p10[2],
                        p11[0], p11[1], p11[2], p01[0], p01[1], p01[2]);
            }
        }
    }

    /** Near-black opaque shell texel. Ring light cannot lift it, so the candle has to cover it. */
    private static boolean darkShell(Model loaded, int tx, int ty) {
        boolean[] dark = loaded.darkShell;
        int frame = (int) FRAME;
        if (dark == null || tx < 0 || ty < 0 || tx >= frame || ty >= frame) {
            return false;
        }
        return dark[ty * frame + tx];
    }

    /** True when a front texel is sandwiched between carving cubes the candle mesh missed. */
    private static boolean enclosedGap(Model loaded, float x, float y, float z) {
        if (z < -6.5f || z > -5.2f) {
            return false;
        }
        float[] openings = loaded.openings;
        boolean left = false;
        boolean right = false;
        boolean up = false;
        boolean down = false;
        for (int i = 0; i < openings.length; i += 6) {
            float dx = outside(x, openings[i], openings[i + 3]);
            float dy = outside(y, openings[i + 1], openings[i + 4]);
            if (dy <= TEXEL * 0.25f && dx > TEXEL * 0.2f && dx < TEXEL * 1.15f) {
                if (x < openings[i]) {
                    left = true;
                } else {
                    right = true;
                }
            }
            if (dx <= TEXEL * 0.25f && dy > TEXEL * 0.2f && dy < TEXEL * 1.15f) {
                if (y < openings[i + 1]) {
                    down = true;
                } else {
                    up = true;
                }
            }
        }
        return (left && right) || (up && down);
    }

    private static Uv nearestCandleUv(Model loaded, float x, float y) {
        Uv best = null;
        float bestD = Float.MAX_VALUE;
        for (Cube cube : loaded.cubes) {
            if (cube.z0 > -6.05f) {
                continue;
            }
            float cx = (cube.x0 + cube.x1) * 0.5f;
            float cy = (cube.y0 + cube.y1) * 0.5f;
            float d = (cx - x) * (cx - x) + (cy - y) * (cy - y);
            if (d < bestD) {
                Uv uv = cube.uv("north");
                if (uv != null) {
                    bestD = d;
                    best = uv;
                }
            }
        }
        return best;
    }

    /** How many flat rings sit around a carving, and how far the outer one reaches. */
    private static final int RINGS = 5;
    private static final float HALO = 3f;
    /** Candle quads overlap the carving edge. Each one sits a hair closer so the overlap does not stripe. */
    private static final float CANDLE_OVERLAP = 0.36f;
    /** Shell rings overlap just enough to hide a one-pixel raster crack. */
    private static final float SHELL_OVERLAP = 0.12f;
    /** Ring 1 is this many block-light levels under the candle. Each next ring drops one more. */
    private static final int RING_DROP = 3;

    /** 0 is inside a carving. 1..5 are the halo. Anything else keeps the world's light. */
    private static int ringOf(Model loaded, float x, float y, float z) {
        float[] openings = loaded.openings;
        if (openings.length == 0) {
            return RINGS + 1;
        }
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
        float texels = best / TEXEL;
        // On the carving's own column, even a deep wall texel is part of the opening.
        if (bestXY(loaded, x, y) < 0.05f || texels < 0.05f) {
            return 0;
        }
        if (texels >= HALO) {
            return RINGS + 1;
        }
        int ring = (int) Math.floor(texels / (HALO / RINGS)) + 1;
        if (ring > RINGS) {
            return RINGS;
        }
        return ring;
    }

    /** Block light for a ring, one level dimmer per step down from this candle frame. Sky light stays the world's. */
    private static int ringLight(int world, int ring, int candle) {
        // A texel inside the opening is uncovered shell. It still has to read as the carving.
        if (ring == 0) {
            ring = 1;
        }
        if (ring < 1 || ring > RINGS) {
            return world;
        }
        int block = candle - (RING_DROP + ring - 1);
        if (block < 0) {
            block = 0;
        }
        int have = LightTexture.block(world);
        if (block <= have) {
            return world;
        }
        return LightTexture.pack(block, LightTexture.sky(world));
    }

    /** Euclidean distance in the front plane to the nearest carving rectangle. */
    private static float bestXY(Model loaded, float x, float y) {
        float[] openings = loaded.openings;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < openings.length; i += 6) {
            float dx = outside(x, openings[i], openings[i + 3]);
            float dy = outside(y, openings[i + 1], openings[i + 4]);
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
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

    /** A stable per-texel depth step so overlapping candle quads do not z-fight. */
    private static float nudge(float x, float y) {
        int h = Math.floorMod((int) Math.floor(x * 8f) * 17 + (int) Math.floor(y * 8f), 8);
        return 0.012f + h * 0.004f;
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
            if (len > 1.0e-4f) {
                float scale = overlap / len;
                xs[i] += dx * scale;
                ys[i] += dy * scale;
                zs[i] += dz * scale;
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
        /** x0, y0, z0, x1, y1, z1 of each carving cube. */
        final float[] openings;
        /** Block light of a bright candle frame, one entry per glow frame. Rings subtract from this. */
        final int[] frameLight;
        /** Frame 0 of the shell. True where a texel is opaque and near-black, the carving edge. */
        final boolean[] darkShell;

        Model(float textureWidth, float textureHeight, List<Cube> cubes, boolean anyGlow, SkinImages.Pixels glowPixels,
              float[] openings, int[] frameLight, boolean[] darkShell) {
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
            this.cubes = cubes;
            this.anyGlow = anyGlow;
            this.glowPixels = glowPixels;
            this.openings = openings;
            this.frameLight = frameLight;
            this.darkShell = darkShell;
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
            float[] openings = openingsOf(cubes);
            int[] frameLight = frameLight(glowPixels);
            boolean[] darkShell = darkShell(SkinImages.read(TEXTURE));
            LOGGER.info("mask cubes {} glow faces {} glow texels {} candle steps {}", cubes.size(), lit, texels, java.util.Arrays.toString(frameLight));
            return new Model(width, height, cubes, texels > 0 && lit > 0, glowPixels, openings, frameLight, darkShell);
        }

        /** Opaque shell texels darker than the rind. Those are the carved edge, not the halo. */
        private static boolean[] darkShell(SkinImages.Pixels pixels) {
            int frame = (int) FRAME;
            boolean[] dark = new boolean[frame * frame];
            if (pixels == null) {
                return dark;
            }
            for (int y = 0; y < frame && y < pixels.height(); y++) {
                for (int x = 0; x < frame && x < pixels.width(); x++) {
                    int argb = pixels.argb()[y * pixels.width() + x];
                    int alpha = (argb >>> 24) & 255;
                    int red = (argb >> 16) & 255;
                    int green = (argb >> 8) & 255;
                    int blue = argb & 255;
                    dark[y * frame + x] = alpha > 10 && red < 48 && green < 24 && blue < 16;
                }
            }
            return dark;
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
