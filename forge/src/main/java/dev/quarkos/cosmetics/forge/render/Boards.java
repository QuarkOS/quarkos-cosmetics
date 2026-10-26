package dev.quarkos.cosmetics.forge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

public final class Boards {
    private Boards() {
    }

    public static void box(VertexConsumer consumer, PoseStack.Pose pose, int light,
                           float x0, float y0, float z0, float x1, float y1, float z1) {
        box(consumer, pose, light, x0, y0, z0, x1, y1, z1, 255, 255, 255);
    }

    public static void box(VertexConsumer consumer, PoseStack.Pose pose, int light,
                           float x0, float y0, float z0, float x1, float y1, float z1,
                           int red, int green, int blue) {
        quad(consumer, pose, light, red, green, blue, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0f, 0f, 1f);
        quad(consumer, pose, light, red, green, blue, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0f, 0f, -1f);
        quad(consumer, pose, light, red, green, blue, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0f, 1f, 0f);
        quad(consumer, pose, light, red, green, blue, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0f, -1f, 0f);
        quad(consumer, pose, light, red, green, blue, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1f, 0f, 0f);
        quad(consumer, pose, light, red, green, blue, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1f, 0f, 0f);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, int light,
                             int red, int green, int blue,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float nx, float ny, float nz) {
        vertex(consumer, pose, x0, y0, z0, 0f, 1f, light, red, green, blue, nx, ny, nz);
        vertex(consumer, pose, x1, y1, z1, 1f, 1f, light, red, green, blue, nx, ny, nz);
        vertex(consumer, pose, x2, y2, z2, 1f, 0f, light, red, green, blue, nx, ny, nz);
        vertex(consumer, pose, x3, y3, z3, 0f, 0f, light, red, green, blue, nx, ny, nz);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z,
                               float u, float v, int light, int red, int green, int blue,
                               float nx, float ny, float nz) {
        consumer.addVertex(pose, x, y, z)
                .setColor(red, green, blue, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
