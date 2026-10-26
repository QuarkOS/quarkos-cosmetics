package dev.quarkos.cosmetics.forge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.quarkos.cosmetics.config.CosmeticsScreen;
import dev.quarkos.cosmetics.forge.anim.GeckoFlap;
import dev.quarkos.cosmetics.forge.gallery.CosmeticModel;
import dev.quarkos.cosmetics.forge.gallery.EquipmentView;
import dev.quarkos.cosmetics.forge.gallery.GalleryCache;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

public final class ForgeCosmeticLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier KITE = Identifier.fromNamespaceAndPath(
            "quarkos_cosmetics", "textures/cosmetics/kite_wings.png");
    private static final Identifier CLOTH = Identifier.fromNamespaceAndPath(
            "quarkos_cosmetics", "textures/cosmetics/cloth.png");
    private final Map<String, Identifier> galleryTextures = new HashMap<>();

    public ForgeCosmeticLayer(AvatarRenderer<?> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        if (state.isSpectator) {
            return;
        }
        boolean elytra = state.isFallFlying || state.chestEquipment.is(Items.ELYTRA);
        boolean armor = !elytra && !state.chestEquipment.isEmpty();
        float flap = GeckoFlap.degrees(state.ageInTicks);
        float nudge = armor ? 1.6f : 0f;
        PlayerModel model = (PlayerModel) this.getParentModel();
        if (CosmeticsScreen.hat) {
            solid(pose, collector, light, model.head, 0xC4A15A, -5.5f, 7.2f, -5.5f, 5.5f, 8.4f, 5.5f);
            solid(pose, collector, light, model.head, 0x8A6A32, -2.6f, 8.4f, -2.6f, 2.6f, 14.2f, 2.6f);
            solid(pose, collector, light, model.head, 0xFFE38A, -1.4f, 9.4f, -1.4f, 1.4f, 13.2f, 1.4f);
            solid(pose, collector, light, model.head, 0x4E473E, -0.45f, 14.2f, -0.45f, 0.45f, 17.2f, 0.45f);
        }
        if (CosmeticsScreen.ears && !CosmeticsScreen.wings) {
            solid(pose, collector, light, model.head, 0xE07A2F, -5.2f, 7.5f, -1.2f, -1.4f, 12.2f, 2.2f);
            solid(pose, collector, light, model.head, 0xE07A2F, -4.4f, 12.2f, -0.6f, -2.1f, 16.4f, 1.4f);
            solid(pose, collector, light, model.head, 0xFFF1D6, -4.3f, 8.2f, 1.1f, -2.2f, 13.4f, 2.15f);
            solid(pose, collector, light, model.head, 0xE07A2F, 1.4f, 7.5f, -1.2f, 5.2f, 12.2f, 2.2f);
            solid(pose, collector, light, model.head, 0xE07A2F, 2.1f, 12.2f, -0.6f, 4.4f, 16.4f, 1.4f);
            solid(pose, collector, light, model.head, 0xFFF1D6, 2.2f, 8.2f, 1.1f, 4.3f, 13.4f, 2.15f);
        }
        if (CosmeticsScreen.tail) {
            pose.pushPose();
            model.body.translateAndRotate(pose);
            pose.mulPose(Axis.ZP.rotationDegrees(flap * 0.35f));
            pose.scale(1f / 16f, -1f / 16f, 1f / 16f);
            collector.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(CLOTH), (captured, consumer) -> {
                Boards.box(consumer, captured, light, -2.4f, -15f, 2.4f, -0.3f, -1f, 3.8f, 0xC2, 0x3B, 0x4A);
                Boards.box(consumer, captured, light, 0.3f, -17f, 2.6f, 2.4f, -1.5f, 4f, 0xF4, 0xE2, 0xC4);
            });
            pose.popPose();
        }
        if (CosmeticsScreen.leftShoulder) {
            solid(pose, collector, light, model.leftArm, 0x1C2438, -1.2f - nudge, -1.2f, -1.4f, 2.8f, 2.6f, 2.2f);
            solid(pose, collector, light, model.leftArm, 0x1C2438, 1.6f, 1.2f, -1f, 4.2f, 4.2f, 1.6f);
            solid(pose, collector, light, model.leftArm, 0xE0B15A, 3.6f, 2.1f, -0.2f, 5.4f, 2.9f, 0.8f);
            solid(pose, collector, light, model.leftArm, 0x3E4C6E, -1.4f, 0.2f, 1.4f, 2.2f, 2.4f, 2.8f);
        }
        if (CosmeticsScreen.rightShoulder) {
            solid(pose, collector, light, model.rightArm, 0x6E6256, -1f, -0.4f, -0.6f, 1f + nudge, 1.6f, 3.2f);
            solid(pose, collector, light, model.rightArm, 0xD9D0C1, -6.2f, 0.2f, -0.8f, -0.8f, 1.1f, 4.2f);
            solid(pose, collector, light, model.rightArm, 0xD9D0C1, 0.8f, 0.2f, -0.8f, 6.2f + nudge, 1.1f, 4.2f);
            solid(pose, collector, light, model.rightArm, 0x8A7B68, -4.2f, 0.35f, 0.4f, -2.2f, 0.95f, 2.2f);
            solid(pose, collector, light, model.rightArm, 0x8A7B68, 2.2f, 0.35f, 0.4f, 4.2f, 0.95f, 2.2f);
        }
        if (CosmeticsScreen.skinLayers) {
            solid(pose, collector, light, model.body, 0xF2D2B6, -4.6f, -12.4f, -2.6f, 4.6f, 0.4f, 2.6f);
        }
        EquipmentView view = GalleryCache.view();
        if (view == null || CosmeticsScreen.hideOthers || !GalleryCache.showNeighbor()) {
            return;
        }
        CosmeticModel worn = view.worn("hat");
        if (worn == null || worn.cubes().isEmpty()) {
            return;
        }
        Identifier texture = galleryTexture(worn);
        if (texture == null) {
            return;
        }
        pose.pushPose();
        pose.translate(2.15f, 0f, 0.2f);
        pose.scale(1f / 16f, -1f / 16f, 1f / 16f);
        collector.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(CLOTH), (captured, consumer) -> {
            Boards.box(consumer, captured, light, -4f, 0f, -2f, -0.5f, 12f, 2f, 0x3A, 0x6E, 0xA5);
            Boards.box(consumer, captured, light, 0.5f, 0f, -2f, 4f, 12f, 2f, 0x3A, 0x6E, 0xA5);
            Boards.box(consumer, captured, light, -4f, 12f, -2f, 4f, 24f, 2f, 0xE7, 0xC8, 0xA0);
            Boards.box(consumer, captured, light, -4f, 24f, -4f, 4f, 32f, 4f, 0xF0, 0xC8, 0xA8);
        });
        collector.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(texture), (captured, consumer) -> {
            for (CosmeticModel.Cube cube : worn.cubes()) {
                Boards.box(consumer, captured, light,
                        cube.x0() - 8f, cube.y0() + 24f, cube.z0() - 8f,
                        cube.x1() - 8f, cube.y1() + 24f, cube.z1() - 8f);
            }
        });
        pose.popPose();
    }

    private void solid(PoseStack pose, SubmitNodeCollector collector, int light, ModelPart part, int rgb,
                       float x0, float y0, float z0, float x1, float y1, float z1) {
        int red = (rgb >> 16) & 255;
        int green = (rgb >> 8) & 255;
        int blue = rgb & 255;
        pose.pushPose();
        part.translateAndRotate(pose);
        pose.scale(1f / 16f, -1f / 16f, 1f / 16f);
        collector.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(CLOTH), (captured, consumer) ->
                Boards.box(consumer, captured, light, x0, y0, z0, x1, y1, z1, red, green, blue));
        pose.popPose();
    }

    private void boxes(PoseStack pose, SubmitNodeCollector collector, int light, ModelPart part, Geometry geometry) {
        pose.pushPose();
        part.translateAndRotate(pose);
        pose.scale(1f / 16f, -1f / 16f, 1f / 16f);
        collector.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(KITE), geometry::draw);
        pose.popPose();
    }

    @FunctionalInterface
    private interface Geometry {
        void draw(PoseStack.Pose pose, VertexConsumer consumer);
    }

    private Identifier galleryTexture(CosmeticModel model) {
        Identifier existing = galleryTextures.get(model.id());
        if (existing != null) {
            return existing;
        }
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(model.png()));
            DynamicTexture texture = new DynamicTexture(() -> "gallery-" + model.id(), image);
            Identifier id = Identifier.fromNamespaceAndPath("quarkos_cosmetics", "gallery/" + model.id());
            Minecraft.getInstance().getTextureManager().register(id, texture);
            galleryTextures.put(model.id(), id);
            return id;
        } catch (Exception e) {
            return null;
        }
    }
}
