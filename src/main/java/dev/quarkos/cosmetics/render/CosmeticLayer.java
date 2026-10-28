package dev.quarkos.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

import java.util.List;

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
        CosmeticDrawer.Submitter submitter = (stack, texture, translucent, draw) -> {
            var type = translucent ? RenderTypes.entityTranslucent(texture) : RenderTypes.entityCutoutNoCull(texture);
            collector.submitCustomGeometry(stack, type, (entry, consumer) -> draw.accept(consumer));
        };
        CosmeticAttach.draw(pose, submitter, light, state.ageInTicks, model.head, model.body, model.leftArm, model.rightArm,
                CosmeticAttach.placements(entity == null ? null : entity.getUUID(), self, elytra, helmet, chest, legs), wingsFlap);
        if (CosmeticAttach.showSkin(self, state.distanceToCameraSq) && state.skin != null && state.skin.body() != null) {
            Identifier skin = state.skin.body().texturePath();
            SkinImages.Pixels pixels = SkinImages.read(skin);
            if (pixels != null) {
                List<SkinShell.Voxel> voxels = SkinShell.build(pixels.width(), pixels.height(), pixels.argb(),
                        state.showHat, state.showJacket, state.showRightSleeve, state.showLeftSleeve, state.showRightPants, state.showLeftPants);
                CosmeticDrawer.shellAll(pose, submitter, light, model.head, model.body, model.leftArm, model.rightArm, model.leftLeg, model.rightLeg, skin, voxels);
            }
        }
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
        CosmeticDrawer.Submitter submitter = (stack, texture, translucent, draw) -> {
            RenderType type = translucent ? RenderType.entityTranslucent(texture) : RenderType.entityCutoutNoCull(texture);
            draw.accept(buffer.getBuffer(type));
        };
        CosmeticAttach.draw(pose, submitter, light, ageInTicks, model.head, model.body, model.leftArm, model.rightArm,
                CosmeticAttach.placements(player.getUUID(), self, elytra, helmet, chest, legs), wingsFlap);
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double distance = player.distanceToSqr(camera.x, camera.y, camera.z);
        if (CosmeticAttach.showSkin(self, distance) && player.getSkin() != null && player.getSkin().texture() != null) {
            Identifier skin = player.getSkin().texture();
            SkinImages.Pixels pixels = SkinImages.read(skin);
            if (pixels != null) {
                List<SkinShell.Voxel> voxels = SkinShell.build(pixels.width(), pixels.height(), pixels.argb(),
                        player.isModelPartShown(PlayerModelPart.HAT),
                        player.isModelPartShown(PlayerModelPart.JACKET),
                        player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE),
                        player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE),
                        player.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG),
                        player.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG));
                CosmeticDrawer.shellAll(pose, submitter, light, model.head, model.body, model.leftArm, model.rightArm, model.leftLeg, model.rightLeg, skin, voxels);
            }
        }
    }
}
//?}
