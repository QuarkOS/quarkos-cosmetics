package dev.quarkos.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.Items;

//? if >=1.21.11 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;

public final class CosmeticLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public CosmeticLayer(AvatarRenderer<?> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        if (state.isSpectator) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(state.id);
        boolean self = minecraft.player != null && entity == minecraft.player;
        boolean elytra = state.isFallFlying || state.chestEquipment.is(Items.ELYTRA);
        boolean helmet = !state.headEquipment.isEmpty();
        boolean chest = !state.chestEquipment.isEmpty() && !state.chestEquipment.is(Items.ELYTRA);
        boolean legs = !state.legsEquipment.isEmpty();
        boolean wingsFlap = entity instanceof net.minecraft.world.entity.player.Player player
                && (player.isSprinting() || player.isFallFlying() || player.getAbilities().flying);
        Chick.bind(entity instanceof net.minecraft.world.entity.player.Player player ? player : null, helmet, state.showHat);
        PlayerModel model = this.getParentModel();
        CosmeticDrawer.Submitter submitter = (stack, texture, pass, draw) -> {
            var type = switch (pass) {
                case EYES -> RenderTypes.eyes(texture);
                case GLOW -> MaskGlow.type(texture);
                case TRANSLUCENT -> RenderTypes.entityTranslucent(texture);
                //? if >=26.3 {
                /*case CUTOUT -> RenderTypes.entityCutoutCull(texture);
                case CUTOUT_NO_CULL -> RenderTypes.entityCutout(texture);
                *///?} else {
                case CUTOUT -> RenderTypes.entityCutout(texture);
                case CUTOUT_NO_CULL -> RenderTypes.entityCutoutNoCull(texture);
                //?}
            };
            collector.submitCustomGeometry(stack, type, (entry, consumer) -> draw.accept(consumer));
        };
        CosmeticAttach.draw(pose, submitter, light, state.ageInTicks, model.head, model.body, model.leftArm, model.rightArm,
                CosmeticAttach.placements(entity == null ? null : entity.getUUID(), self, elytra, helmet, chest, legs), wingsFlap, self);
    }
}
//?} else {
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;

public final class CosmeticLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public CosmeticLayer(PlayerRenderer parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isSpectator()) {
            return;
        }
        ItemStack chestItem = player.getItemBySlot(EquipmentSlot.CHEST);
        boolean elytra = player.isFallFlying() || chestItem.is(Items.ELYTRA);
        boolean helmet = !player.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
        boolean chest = !chestItem.isEmpty() && !chestItem.is(Items.ELYTRA);
        boolean legs = !player.getItemBySlot(EquipmentSlot.LEGS).isEmpty();
        boolean self = Minecraft.getInstance().player == player;
        boolean wingsFlap = player.isSprinting() || player.isFallFlying() || player.getAbilities().flying;
        Chick.bind(player, helmet, player.isModelPartShown(PlayerModelPart.HAT));
        PlayerModel<AbstractClientPlayer> model = this.getParentModel();
        CosmeticDrawer.Submitter submitter = (stack, texture, pass, draw) -> {
            RenderType type = switch (pass) {
                case EYES -> RenderType.eyes(texture);
                case GLOW -> MaskGlow.type(texture);
                case TRANSLUCENT -> RenderType.entityTranslucent(texture);
                case CUTOUT -> RenderType.entityCutout(texture);
                case CUTOUT_NO_CULL -> RenderType.entityCutoutNoCull(texture);
            };
            draw.accept(buffer.getBuffer(type));
        };
        CosmeticAttach.draw(pose, submitter, light, ageInTicks, model.head, model.body, model.leftArm, model.rightArm,
                CosmeticAttach.placements(player.getUUID(), self, elytra, helmet, chest, legs), wingsFlap, self);
    }
}
//?}
