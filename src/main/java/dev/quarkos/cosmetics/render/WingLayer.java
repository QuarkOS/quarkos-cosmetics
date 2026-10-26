package dev.quarkos.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.quarkos.cosmetics.config.CosmeticsScreen;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

public final class WingLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            "quarkos_cosmetics", "textures/cosmetics/kite_wings.png");

    public WingLayer(AvatarRenderer<?> parent) {
        super(parent);
    }

    private static boolean logged;

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        if (state.isSpectator || !CosmeticsScreen.wings) {
            return;
        }
        if (!logged) {
            logged = true;
            org.slf4j.LoggerFactory.getLogger("quarkos_cosmetics").info("drawing kite wings");
        }
        PlayerModel model = (PlayerModel) this.getParentModel();
        float flap = (float) Math.sin(state.ageInTicks * 0.28f) * 32f;
        wing(pose, collector, light, model.body, flap, false);
        wing(pose, collector, light, model.body, -flap, true);
        ears(pose, collector, light, model.head);
    }

    private static void wing(PoseStack pose, SubmitNodeCollector collector, int light, ModelPart body, float flap, boolean right) {
        pose.pushPose();
        body.translateAndRotate(pose);
        pose.scale(1f / 16f, -1f / 16f, 1f / 16f);
        if (right) {
            pose.scale(-1f, 1f, 1f);
        }
        pose.mulPose(Axis.ZP.rotationDegrees(flap));
        collector.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(TEXTURE), (captured, consumer) -> {
            board(consumer, captured, light, 3f, -4f, 3f, 22f, 14f, 5f);
            board(consumer, captured, light, 6f, -16f, 3.4f, 16f, -2f, 4.6f);
        });
        pose.popPose();
    }

    private static void ears(PoseStack pose, SubmitNodeCollector collector, int light, ModelPart head) {
        pose.pushPose();
        head.translateAndRotate(pose);
        pose.scale(1f / 16f, -1f / 16f, 1f / 16f);
        collector.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(TEXTURE), (captured, consumer) -> {
            board(consumer, captured, light, -5f, 8f, -1f, -1f, 16f, 2f);
            board(consumer, captured, light, 1f, 8f, -1f, 5f, 16f, 2f);
        });
        pose.popPose();
    }

    private static void board(VertexConsumer consumer, PoseStack.Pose pose, int light,
                              float x0, float y0, float z0, float x1, float y1, float z1) {
        quad(consumer, pose, light, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0f, 0f, 1f);
        quad(consumer, pose, light, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0f, 0f, -1f);
        quad(consumer, pose, light, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0f, 1f, 0f);
        quad(consumer, pose, light, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0f, -1f, 0f);
        quad(consumer, pose, light, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1f, 0f, 0f);
        quad(consumer, pose, light, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1f, 0f, 0f);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, int light,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float nx, float ny, float nz) {
        vertex(consumer, pose, x0, y0, z0, 0f, 1f, light, nx, ny, nz);
        vertex(consumer, pose, x1, y1, z1, 1f, 1f, light, nx, ny, nz);
        vertex(consumer, pose, x2, y2, z2, 1f, 0f, light, nx, ny, nz);
        vertex(consumer, pose, x3, y3, z3, 0f, 0f, light, nx, ny, nz);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z,
                               float u, float v, int light, float nx, float ny, float nz) {
        consumer.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
