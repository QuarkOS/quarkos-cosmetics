package dev.quarkos.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.quarkos.cosmetics.config.TintColor;
import dev.quarkos.cosmetics.model.Worn;
import dev.quarkos.cosmetics.render.BlockbenchModel.Bone;
import dev.quarkos.cosmetics.render.BlockbenchModel.Clip;
import dev.quarkos.cosmetics.render.BlockbenchModel.Cube;
import dev.quarkos.cosmetics.render.BlockbenchModel.Face;
import dev.quarkos.cosmetics.render.BlockbenchModel.Root;
import dev.quarkos.cosmetics.render.BlockbenchModel.Side;
import dev.quarkos.cosmetics.render.BlockbenchModel.Tracks;
import dev.quarkos.cosmetics.render.BlockbenchModel.Vec;
import dev.quarkos.cosmetics.render.CosmeticDrawer.Pass;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;

import static dev.quarkos.cosmetics.render.BlockbenchModel.sample;

//? if >=26.3 {
/*import static net.minecraft.util.LightCoordsUtil.FULL_BRIGHT;
*///?} else {
import static net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
//?}

/** Renders the original Blockbench cubes against the player parts named by the model roots. */
final class BlockbenchCosmetic {
    private static final Vec GHOST_SHIFT = new Vec(2.5f, -6f, -3f);
    private static final float GHOST_SCALE = 0.75f;

    private BlockbenchCosmetic() {
    }

    static void draw(PoseStack pose, CosmeticDrawer.Submitter submitter, int light, float age, ModelPart playerRoot,
                     Function<Worn.Anchor, ModelPart> part, ModelLibrary.Resolved resolved, int tint,
                     float offsetX, float offsetY, float offsetZ) {
        PackedCatalog.Item item = resolved.builtin();
        BlockbenchModel model = item.blockbench();
        boolean legacy = item.entry().legacyBlockbench();
        boolean ghost = "quarkos:ghost_buddy".equals(resolved.id());
        Vec offset = item.entry().offset();
        Identifier glow = item.glow() == null ? null : ModelLibrary.location(item.glow());

        for (Root root : model.roots()) {
            pose.pushPose();
            if (!legacy && playerRoot != null) {
                playerRoot.translateAndRotate(pose);
            }
            ModelPart anchor = part.apply(root.anchor());
            if (anchor != null) {
                anchor.translateAndRotate(pose);
            }
            if (legacy) {
                // Match the v1 Blockbench portraits and the in-world ghost fit.
                //? if >=26.3 {
                /*pose.rotate(new Quaternionf().rotationZ((float) Math.PI));
                *///?} else {
                pose.mulPose(new Quaternionf().rotationZ((float) Math.PI));
                //?}
                pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
                Vec shift = ghost ? GHOST_SHIFT : Vec.ZERO;
                pose.translate(shift.x(), shift.y() - 24f, shift.z());
            } else {
                pose.scale(1f / 16f, -1f / 16f, 1f / 16f);
                Vec pivot = root.bone().pivot();
                pose.translate(offset.x() + offsetX - pivot.x(), offset.y() + offsetY - pivot.y(), offset.z() + offsetZ - pivot.z());
            }

            List<PosedCube> posed = new ArrayList<>();
            EnumSet<Pass> passes = EnumSet.noneOf(Pass.class);
            walk(root.bone(), model.idle(), new Matrix4f(pose.last().pose()), age, legacy, ghost, posed, passes);
            if (legacy) {
                Pass pass = ghost ? Pass.TRANSLUCENT : Pass.CUTOUT_NO_CULL;
                submitter.submit(pose, resolved.texture(), pass,
                        consumer -> paint(consumer, posed, light, tint, item.frames(), 0, pass, true));
                if (glow != null) {
                    submitter.submit(pose, glow, Pass.GLOW,
                            consumer -> paint(consumer, posed, FULL_BRIGHT, TintColor.DEFAULT, item.glowFrames(), 0, Pass.GLOW, true));
                }
            } else {
                for (Pass pass : passes) {
                    boolean emissive = pass == Pass.EMISSIVE;
                    submitter.submit(pose, emissive ? glow : resolved.texture(), pass, consumer ->
                            paint(consumer, posed, emissive ? FULL_BRIGHT : light, emissive ? TintColor.DEFAULT : tint,
                                    emissive ? item.glowFrames() : item.frames(), age, pass, false));
                }
            }
            pose.popPose();
        }
    }

    static void portrait(PoseStack pose, CosmeticDrawer.Submitter submitter, int light, ModelLibrary.Resolved resolved) {
        draw(pose, submitter, light, 0, null, anchor -> null, resolved, TintColor.DEFAULT, 0, 0, 0);
    }

    private static void walk(Bone bone, List<Clip> idle, Matrix4f parent, float age, boolean legacy, boolean ghost,
                             List<PosedCube> posed, EnumSet<Pass> passes) {
        Vec rotation = bone.rotation();
        Vec position = Vec.ZERO;
        Vec scale = Vec.ONE;
        for (Clip clip : idle) {
            Tracks tracks = clip.bones().get(bone.uuid());
            if (tracks != null) {
                float time = clip.time(age);
                Vec turn = sample(tracks.rotation(), time, legacy ? rotation : Vec.ZERO, legacy);
                rotation = legacy ? turn : rotation.add(turn);
                position = position.add(sample(tracks.position(), time, Vec.ZERO, legacy));
                scale = scale.multiply(sample(tracks.scale(), time, Vec.ONE, legacy));
            }
        }
        Vec pivot = bone.pivot();
        Matrix4f local = new Matrix4f(parent).translate(pivot.x() + position.x(), pivot.y() + position.y(), pivot.z() + position.z());
        turn(local, rotation, legacy);
        float fit = ghost && "ghost".equals(bone.name()) ? GHOST_SCALE : 1f;
        local.scale(scale.x() * fit, scale.y() * fit, scale.z() * fit).translate(-pivot.x(), -pivot.y(), -pivot.z());

        for (Cube cube : bone.cubes()) {
            Matrix4f matrix = new Matrix4f(local);
            Vec center = cube.pivot();
            matrix.translate(center.x(), center.y(), center.z());
            turn(matrix, cube.rotation(), legacy);
            matrix.translate(-center.x(), -center.y(), -center.z());
            Matrix3f normals = new Matrix3f(matrix);
            if (Math.abs(normals.determinant()) > 1.0e-8f) {
                normals.invert().transpose();
            }
            boolean twoSidedPlane = cube.faces().size() == 2 && (cube.from().x() == cube.to().x()
                    || cube.from().y() == cube.to().y() || cube.from().z() == cube.to().z());
            posed.add(new PosedCube(cube, matrix, normals, twoSidedPlane));
            if (!legacy) {
                for (Face face : cube.faces().values()) {
                    passes.add(basePass(face, twoSidedPlane));
                    if (face.glowing()) {
                        passes.add(Pass.EMISSIVE);
                    }
                }
            }
        }
        for (Bone child : bone.children()) {
            walk(child, idle, local, age, legacy, ghost, posed, passes);
        }
    }

    private static void turn(Matrix4f matrix, Vec degrees, boolean legacy) {
        float x = (float) Math.toRadians(degrees.x());
        float y = (float) Math.toRadians(degrees.y());
        float z = (float) Math.toRadians(degrees.z());
        if (legacy) {
            matrix.rotateXYZ(x, y, z);
        } else {
            matrix.rotateZYX(z, y, x);
        }
    }

    private record PosedCube(Cube cube, Matrix4f view, Matrix3f normals, boolean twoSidedPlane) {
    }

    private static Pass basePass(Face face, boolean twoSidedPlane) {
        if (face.translucent()) {
            return twoSidedPlane ? Pass.TRANSLUCENT_CULL : Pass.TRANSLUCENT;
        }
        return twoSidedPlane ? Pass.CUTOUT : Pass.CUTOUT_NO_CULL;
    }

    private static void paint(VertexConsumer consumer, List<PosedCube> posed, int light, int rgb,
                              TextureFrames frames, float age, Pass pass, boolean legacy) {
        TextureFrames.Frame frame = frames.at(age);
        for (PosedCube element : posed) {
            emit(consumer, element, light, rgb, frames, frame, pass, legacy);
        }
    }

    private static void emit(VertexConsumer consumer, PosedCube posed, int light, int rgb,
                             TextureFrames frames, TextureFrames.Frame frame, Pass pass, boolean legacy) {
        Cube cube = posed.cube();
        Matrix4f view = posed.view();
        Matrix3f normals = posed.normals();
        boolean twoSidedPlane = posed.twoSidedPlane();
        float inflate = (legacy ? 0 : cube.inflate()) + (pass == Pass.GLOW || pass == Pass.EMISSIVE ? 0.04f : 0);
        float ax0 = cube.from().x() - inflate, ay0 = cube.from().y() - inflate, az0 = cube.from().z() - inflate;
        float ax1 = cube.to().x() + inflate, ay1 = cube.to().y() + inflate, az1 = cube.to().z() + inflate;
        quad(consumer, view, normals, light, rgb, cube.faces().get(Side.EAST), frames, frame, legacy, pass, twoSidedPlane, ax1, ay1, az0, ax1, ay1, az1, ax1, ay0, az1, ax1, ay0, az0, 1, 0, 0);
        quad(consumer, view, normals, light, rgb, cube.faces().get(Side.WEST), frames, frame, legacy, pass, twoSidedPlane, ax0, ay1, az1, ax0, ay1, az0, ax0, ay0, az0, ax0, ay0, az1, -1, 0, 0);
        quad(consumer, view, normals, light, rgb, cube.faces().get(Side.NORTH), frames, frame, legacy, pass, twoSidedPlane, ax0, ay1, az0, ax1, ay1, az0, ax1, ay0, az0, ax0, ay0, az0, 0, 0, -1);
        quad(consumer, view, normals, light, rgb, cube.faces().get(Side.SOUTH), frames, frame, legacy, pass, twoSidedPlane, ax1, ay1, az1, ax0, ay1, az1, ax0, ay0, az1, ax1, ay0, az1, 0, 0, 1);
        quad(consumer, view, normals, light, rgb, cube.faces().get(Side.UP), frames, frame, legacy, pass, twoSidedPlane, ax0, ay1, az1, ax1, ay1, az1, ax1, ay1, az0, ax0, ay1, az0, 0, 1, 0);
        quad(consumer, view, normals, light, rgb, cube.faces().get(Side.DOWN), frames, frame, legacy, pass, twoSidedPlane, ax0, ay0, az0, ax1, ay0, az0, ax1, ay0, az1, ax0, ay0, az1, 0, -1, 0);
    }

    private static void quad(VertexConsumer consumer, Matrix4f view, Matrix3f normals, int light, int rgb, Face face,
                             TextureFrames frames, TextureFrames.Frame frame, boolean legacy, Pass pass, boolean twoSidedPlane,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float nx, float ny, float nz) {
        if (face == null || !legacy && (pass == Pass.EMISSIVE ? !face.glowing() : pass != basePass(face, twoSidedPlane))) {
            return;
        }
        Vector3f normal = new Vector3f(nx, ny, nz);
        normals.transform(normal);
        if (normal.lengthSquared() > 1.0e-8f) {
            normal.normalize();
        }
        float u0 = (frame.x() + face.u0() * frames.frameWidth()) / frames.width();
        float v0 = (frame.y() + face.v0() * frames.frameHeight()) / frames.height();
        float u1 = (frame.x() + face.u1() * frames.frameWidth()) / frames.width();
        float v1 = (frame.y() + face.v1() * frames.frameHeight()) / frames.height();
        vertex(consumer, view, x0, y0, z0, u0, v0, light, rgb, normal);
        if (legacy) {
            vertex(consumer, view, x1, y1, z1, u1, v0, light, rgb, normal);
            vertex(consumer, view, x2, y2, z2, u1, v1, light, rgb, normal);
            vertex(consumer, view, x3, y3, z3, u0, v1, light, rgb, normal);
        } else {
            vertex(consumer, view, x3, y3, z3, u0, v1, light, rgb, normal);
            vertex(consumer, view, x2, y2, z2, u1, v1, light, rgb, normal);
            vertex(consumer, view, x1, y1, z1, u1, v0, light, rgb, normal);
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f view, float x, float y, float z,
                               float u, float v, int light, int rgb, Vector3f normal) {
        consumer.addVertex(view, x, y, z)
                .setColor((rgb >>> 16) & 255, (rgb >>> 8) & 255, rgb & 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(normal.x, normal.y, normal.z);
    }
}
