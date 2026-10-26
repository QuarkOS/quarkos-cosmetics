package dev.quarkos.cosmetics.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.quarkos.cosmetics.model.CosmeticModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Draws a cosmetics-model-1 cube in a pose matrix captured before the stack is popped. */
public final class MeshDraw {
    private MeshDraw() {
    }

    public static void model(VertexConsumer consumer, Matrix4f view, Matrix3f normals, int light,
                             CosmeticModel model, int textureWidth, int textureHeight, float age) {
        int width = Math.max(1, textureWidth);
        int height = Math.max(1, textureHeight);
        for (CosmeticModel.Element element : model.elements) {
            Matrix4f local = new Matrix4f();
            float ox = element.origin()[0] - 8f;
            float oy = element.origin()[1] - 8f;
            float oz = element.origin()[2] - 8f;
            local.translate(ox, oy, oz);
            local.rotateXYZ(
                    (float) Math.toRadians(element.rotation()[0] + GeckoMotion.idleDegrees(element.name(), age)),
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
                face(consumer, combined, normals, turned, light, element, "south", width, height, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1);
            }
            if (all || element.faces().containsKey("north")) {
                face(consumer, combined, normals, turned, light, element, "north", width, height, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1);
            }
            if (all || element.faces().containsKey("east")) {
                face(consumer, combined, normals, turned, light, element, "east", width, height, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0);
            }
            if (all || element.faces().containsKey("west")) {
                face(consumer, combined, normals, turned, light, element, "west", width, height, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0);
            }
            if (all || element.faces().containsKey("up")) {
                face(consumer, combined, normals, turned, light, element, "up", width, height, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0);
            }
            if (all || element.faces().containsKey("down")) {
                face(consumer, combined, normals, turned, light, element, "down", width, height, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0);
            }
        }
    }

    private static void face(VertexConsumer consumer, Matrix4f view, Matrix3f normals, Matrix3f turned, int light,
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
        vertex(consumer, view, x0, y0, z0, u0, v1, light, normal);
        vertex(consumer, view, x1, y1, z1, u1, v1, light, normal);
        vertex(consumer, view, x2, y2, z2, u1, v0, light, normal);
        vertex(consumer, view, x3, y3, z3, u0, v0, light, normal);
    }

    public static void voxel(VertexConsumer consumer, Matrix4f view, Matrix3f normals, int light,
                             float x, float y, float z, float size, float u, float v, int textureWidth, int textureHeight) {
        float u0 = u / textureWidth;
        float v0 = v / textureHeight;
        float u1 = (u + 1f) / textureWidth;
        float v1 = (v + 1f) / textureHeight;
        float x1 = x + size;
        float y1 = y + size;
        float z1 = z + size;
        face(consumer, view, normals, light, u0, v0, u1, v1, x, y, z1, x1, y, z1, x1, y1, z1, x, y1, z1, 0, 0, 1);
        face(consumer, view, normals, light, u0, v0, u1, v1, x1, y, z, x, y, z, x, y1, z, x1, y1, z, 0, 0, -1);
        face(consumer, view, normals, light, u0, v0, u1, v1, x1, y, z1, x1, y, z, x1, y1, z, x1, y1, z1, 1, 0, 0);
        face(consumer, view, normals, light, u0, v0, u1, v1, x, y, z, x, y, z1, x, y1, z1, x, y1, z, -1, 0, 0);
        face(consumer, view, normals, light, u0, v0, u1, v1, x, y1, z1, x1, y1, z1, x1, y1, z, x, y1, z, 0, 1, 0);
        face(consumer, view, normals, light, u0, v0, u1, v1, x, y, z, x1, y, z, x1, y, z1, x, y, z1, 0, -1, 0);
    }

    private static void face(VertexConsumer consumer, Matrix4f view, Matrix3f normals, int light,
                             float u0, float v0, float u1, float v1,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float nx, float ny, float nz) {
        Vector3f normal = new Vector3f(nx, ny, nz).mul(normals);
        if (normal.lengthSquared() > 0.0001f) {
            normal.normalize();
        }
        vertex(consumer, view, x0, y0, z0, u0, v1, light, normal);
        vertex(consumer, view, x1, y1, z1, u1, v1, light, normal);
        vertex(consumer, view, x2, y2, z2, u1, v0, light, normal);
        vertex(consumer, view, x3, y3, z3, u0, v0, light, normal);
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
}
