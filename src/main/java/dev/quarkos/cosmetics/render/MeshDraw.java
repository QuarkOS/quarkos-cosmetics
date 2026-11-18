package dev.quarkos.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.quarkos.cosmetics.model.CosmeticModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

/** Draws a cosmetics-model-1 cube in a pose matrix captured before the stack is popped. */
public final class MeshDraw {
    private static final SkinShell.Face[] SKIN_FACES = SkinShell.Face.values();

    private MeshDraw() {
    }

    public static void model(VertexConsumer consumer, Matrix4f view, Matrix3f normals, int light,
                             CosmeticModel model, int textureWidth, int textureHeight, float age, int tint) {
        int width = Math.max(1, textureWidth);
        int height = Math.max(1, textureHeight);
        for (CosmeticModel.Element element : model.elements) {
            Matrix4f local = new Matrix4f();
            float ox = element.origin()[0] - 8f;
            float oy = element.origin()[1] - 8f;
            float oz = element.origin()[2] - 8f;
            local.translate(ox, oy, oz);
            local.rotateXYZ(
                    (float) Math.toRadians(element.rotation()[0] + GeckoMotion.idleDegrees(element.name(), age) + GeckoMotion.flapPitch(element.name(), age)),
                    (float) Math.toRadians(element.rotation()[1]),
                    (float) Math.toRadians(element.rotation()[2] + GeckoMotion.flapDegrees(element.name(), age)));
            local.translate(-ox, -oy, -oz);
            Matrix4f combined = new Matrix4f(view).mul(local);
            Matrix3f turned = new Matrix3f(local);
            float x0 = element.from()[0] - 8f;
            float y0 = element.from()[1] - 8f;
            float z0 = element.from()[2] - 8f;
            float x1 = element.to()[0] - 8f;
            float y1 = element.to()[1] - 8f;
            float z1 = element.to()[2] - 8f;
            boolean all = element.faces().isEmpty();
            if (all || element.faces().containsKey("south")) {
                face(consumer, combined, normals, turned, light, tint, element, "south", width, height, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1);
            }
            if (all || element.faces().containsKey("north")) {
                face(consumer, combined, normals, turned, light, tint, element, "north", width, height, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1);
            }
            if (all || element.faces().containsKey("east")) {
                face(consumer, combined, normals, turned, light, tint, element, "east", width, height, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0);
            }
            if (all || element.faces().containsKey("west")) {
                face(consumer, combined, normals, turned, light, tint, element, "west", width, height, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0);
            }
            if (all || element.faces().containsKey("up")) {
                face(consumer, combined, normals, turned, light, tint, element, "up", width, height, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0);
            }
            if (all || element.faces().containsKey("down")) {
                face(consumer, combined, normals, turned, light, tint, element, "down", width, height, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0);
            }
        }
    }

    private static void face(VertexConsumer consumer, Matrix4f view, Matrix3f normals, Matrix3f turned, int light, int tint,
                             CosmeticModel.Element element, String face, int width, int height,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float nx, float ny, float nz) {
        float u0 = 0f;
        float v0 = 0f;
        float u1 = 1f;
        float v1 = 1f;
        CosmeticModel.Face mapped = element.faces().get(face);
        if (mapped != null) {
            u0 = mapped.uv()[0] / width;
            v0 = mapped.uv()[1] / height;
            u1 = mapped.uv()[2] / width;
            v1 = mapped.uv()[3] / height;
        }
        Vector3f normal = new Vector3f(nx, ny, nz);
        turned.transform(normal);
        normals.transform(normal);
        if (normal.lengthSquared() > 0.0001f) {
            normal.normalize();
        }
        // The format is Y-up and the attachment reflects Y. Keep the UV on each
        // corner and reverse the order so the exterior gets front-face lighting.
        vertex(consumer, view, x0, y0, z0, u0, v1, light, normal, tint);
        vertex(consumer, view, x3, y3, z3, u0, v0, light, normal, tint);
        vertex(consumer, view, x2, y2, z2, u1, v0, light, normal, tint);
        vertex(consumer, view, x1, y1, z1, u1, v1, light, normal, tint);
    }

    static void skin(VertexConsumer consumer, PoseStack.Pose pose, int light, int overlay, int color,
                     List<SkinShell.Voxel> voxels) {
        Vector3f[] normals = new Vector3f[SKIN_FACES.length];
        for (SkinShell.Face face : SKIN_FACES) {
            normals[face.ordinal()] = pose.normal().transform(new Vector3f(face.x, face.y, face.z)).normalize();
        }
        Matrix4f view = pose.pose();
        Vector3f position = new Vector3f();
        for (SkinShell.Voxel voxel : voxels) {
            float x0 = voxel.minX() / 16f;
            float y0 = voxel.minY() / 16f;
            float z0 = voxel.minZ() / 16f;
            float x1 = voxel.maxX() / 16f;
            float y1 = voxel.maxY() / 16f;
            float z1 = voxel.maxZ() / 16f;
            float u = (voxel.u() + 0.5f) / SkinShell.SKIN_SIZE;
            float v = (voxel.v() + 0.5f) / SkinShell.SKIN_SIZE;
            for (SkinShell.Face face : SKIN_FACES) {
                if (!voxel.exposes(face)) {
                    continue;
                }
                Vector3f normal = normals[face.ordinal()];
                for (int i = 0; i < 4; i++) {
                    int corner = face.corner(i);
                    view.transformPosition((corner & 1) == 0 ? x0 : x1, (corner & 2) == 0 ? y0 : y1,
                            (corner & 4) == 0 ? z0 : z1, position);
                    consumer.addVertex(position.x, position.y, position.z, color, u, v, overlay, light,
                            normal.x, normal.y, normal.z);
                }
            }
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f view, float x, float y, float z,
                               float u, float v, int light, Vector3f normal, int tint) {
        consumer.addVertex(view, x, y, z)
                .setColor((tint >>> 16) & 255, (tint >>> 8) & 255, tint & 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(normal.x, normal.y, normal.z);
    }
}
