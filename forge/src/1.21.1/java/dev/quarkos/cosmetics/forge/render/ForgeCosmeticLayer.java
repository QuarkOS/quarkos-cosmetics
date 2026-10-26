package dev.quarkos.cosmetics.forge.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.quarkos.cosmetics.config.CosmeticsScreen;
import dev.quarkos.cosmetics.forge.anim.GeckoFlap;
import dev.quarkos.cosmetics.forge.gallery.CosmeticModel;
import dev.quarkos.cosmetics.forge.gallery.EquipmentView;
import dev.quarkos.cosmetics.forge.gallery.GalleryCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

public final class ForgeCosmeticLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation KITE = ResourceLocation.fromNamespaceAndPath(
            "quarkos_cosmetics", "textures/cosmetics/kite_wings.png");
    private final Map<String, ResourceLocation> galleryTextures = new HashMap<>();

    public ForgeCosmeticLayer(PlayerRenderer parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isSpectator() || player.isInvisible()) {
            return;
        }
        boolean elytra = player.isFallFlying() || player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
        boolean armor = !elytra && !player.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
        float flap = GeckoFlap.degrees(ageInTicks);
        float nudge = armor ? 1.6f : 0f;
        PlayerModel<AbstractClientPlayer> model = this.getParentModel();
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(KITE));
        if (CosmeticsScreen.wings && !elytra) {
            wing(pose, consumer, light, model.body, flap, false);
            wing(pose, consumer, light, model.body, -flap, true);
        }
        if (CosmeticsScreen.hat) {
            boxes(pose, consumer, light, model.head, (captured, vertices) ->
                    Boards.box(vertices, captured, light, -6f, 8f, -6f, 6f, 11f, 6f));
        }
        if (CosmeticsScreen.ears) {
            boxes(pose, consumer, light, model.head, (captured, vertices) -> {
                Boards.box(vertices, captured, light, -5f, 8f, -1f, -1f, 15f, 2f);
                Boards.box(vertices, captured, light, 1f, 8f, -1f, 5f, 15f, 2f);
            });
        }
        if (CosmeticsScreen.tail) {
            pose.pushPose();
            model.body.translateAndRotate(pose);
            pose.mulPose(Axis.ZP.rotationDegrees(flap * 0.25f));
            pose.scale(1f / 16f, -1f / 16f, 1f / 16f);
            Boards.box(consumer, pose.last(), light, -1.5f, -12f, 2f, 1.5f, -2f, 5f);
            pose.popPose();
        }
        if (CosmeticsScreen.leftShoulder) {
            boxes(pose, consumer, light, model.leftArm, (captured, vertices) ->
                    Boards.box(vertices, captured, light, -2f - nudge, -2f, -2f, 2f, 2f, 2f));
        }
        if (CosmeticsScreen.rightShoulder) {
            boxes(pose, consumer, light, model.rightArm, (captured, vertices) ->
                    Boards.box(vertices, captured, light, -2f, -2f, -2f, 2f + nudge, 2f, 2f));
        }
        if (CosmeticsScreen.skinLayers) {
            boxes(pose, consumer, light, model.body, (captured, vertices) ->
                    Boards.box(vertices, captured, light, -4.8f, -12.6f, -2.8f, 4.8f, 0.6f, 2.8f));
        }
        EquipmentView view = GalleryCache.view();
        if (view == null || CosmeticsScreen.hideOthers) {
            return;
        }
        CosmeticModel worn = view.worn("hat");
        if (worn == null || worn.cubes().isEmpty()) {
            return;
        }
        ResourceLocation texture = galleryTexture(worn);
        if (texture == null) {
            return;
        }
        VertexConsumer gallery = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        pose.pushPose();
        pose.translate(1.35f, 0f, 0f);
        pose.scale(1f / 16f, -1f / 16f, 1f / 16f);
        Boards.box(gallery, pose.last(), light, -4f, 24f, -4f, 4f, 32f, 4f);
        Boards.box(gallery, pose.last(), light, -4f, 12f, -2f, 4f, 24f, 2f);
        for (CosmeticModel.Cube cube : worn.cubes()) {
            Boards.box(gallery, pose.last(), light,
                    cube.x0() - 8f, cube.y0() + 16f, cube.z0() - 8f,
                    cube.x1() - 8f, cube.y1() + 16f, cube.z1() - 8f);
        }
        pose.popPose();
    }

    private static void wing(PoseStack pose, VertexConsumer consumer, int light, ModelPart body, float flap, boolean right) {
        pose.pushPose();
        body.translateAndRotate(pose);
        pose.scale(1f / 16f, -1f / 16f, 1f / 16f);
        if (right) {
            pose.scale(-1f, 1f, 1f);
        }
        pose.mulPose(Axis.ZP.rotationDegrees(flap));
        Boards.box(consumer, pose.last(), light, 3f, -4f, 3f, 22f, 14f, 5f);
        Boards.box(consumer, pose.last(), light, 6f, -16f, 3.4f, 16f, -2f, 4.6f);
        pose.popPose();
    }

    private static void boxes(PoseStack pose, VertexConsumer consumer, int light, ModelPart part, Geometry geometry) {
        pose.pushPose();
        part.translateAndRotate(pose);
        pose.scale(1f / 16f, -1f / 16f, 1f / 16f);
        geometry.draw(pose.last(), consumer);
        pose.popPose();
    }

    private ResourceLocation galleryTexture(CosmeticModel model) {
        ResourceLocation existing = galleryTextures.get(model.id());
        if (existing != null) {
            return existing;
        }
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(model.png()));
            DynamicTexture texture = new DynamicTexture(image);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("quarkos_cosmetics", "gallery/" + model.id());
            Minecraft.getInstance().getTextureManager().register(id, texture);
            galleryTextures.put(model.id(), id);
            return id;
        } catch (Exception e) {
            return null;
        }
    }

    @FunctionalInterface
    private interface Geometry {
        void draw(PoseStack.Pose pose, VertexConsumer consumer);
    }
}
