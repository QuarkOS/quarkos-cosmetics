package dev.quarkos.cosmetics.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.quarkos.cosmetics.render.SkinShellRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

//? if >=1.21.11 {
import net.minecraft.client.model.Model;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.player.PlayerModelType;

@Mixin(ModelFeatureRenderer.class)
public abstract class SkinShellMixin {
    @Redirect(method = "renderModel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void quarkos$replaceOverlay(Model<?> model, PoseStack pose, VertexConsumer consumer, int light, int overlay, int color,
                                         SubmitNodeStorage.ModelSubmit<?> submit, RenderType type, VertexConsumer buffer,
                                         OutlineBufferSource outlines, MultiBufferSource.BufferSource crumbling) {
        Minecraft client = Minecraft.getInstance();
        if (model instanceof PlayerModel playerModel && submit.state() instanceof AvatarRenderState state
                && !state.isSpectator && state.skin != null && state.skin.body() != null
                && client.getEntityRenderDispatcher().getRenderer(state) instanceof AvatarRenderer<?> renderer
                && renderer.getModel() == model) {
            boolean self = client.player != null && client.level != null && client.level.getEntity(state.id) == client.player;
            SkinShellRenderer.render(playerModel, pose, consumer, light, overlay, color,
                    state.skin.body().texturePath(), state.skin.model() == PlayerModelType.SLIM, self, state.distanceToCameraSq);
        } else {
            model.renderToBuffer(pose, consumer, light, overlay, color);
        }
    }
}
//?} else {
/*import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.LivingEntity;

@Mixin(LivingEntityRenderer.class)
public abstract class SkinShellMixin {
    @Redirect(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void quarkos$replaceOverlay(EntityModel<?> model, PoseStack pose, VertexConsumer consumer, int light, int overlay, int color,
                                         LivingEntity entity, float yaw, float partialTick, PoseStack stack, MultiBufferSource buffers, int packedLight) {
        if (entity instanceof AbstractClientPlayer player && !player.isSpectator() && model instanceof PlayerModel<?> playerModel) {
            PlayerSkin skin = player.getSkin();
            if (skin != null) {
                Minecraft client = Minecraft.getInstance();
                var camera = client.gameRenderer.getMainCamera().getPosition();
                double distance = player.distanceToSqr(camera.x, camera.y, camera.z);
                SkinShellRenderer.render(playerModel, pose, consumer, light, overlay, color, skin.texture(),
                        skin.model() == PlayerSkin.Model.SLIM, client.player == player, distance);
                return;
            }
        }
        model.renderToBuffer(pose, consumer, light, overlay, color);
    }
}
*///?}
