package dev.quarkos.cosmetics.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
//? if >=1.21.11 {
//? if >=26.3 {
/*import net.minecraft.client.renderer.extract.LevelExtractor;
*///?} else {
import net.minecraft.client.renderer.LevelRenderer;
//?}
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Harness-only. Snaps the local player immediately before the render state is built,
 * so a falling or sleeping shot is not the pose from earlier in the tick.
 */
//? if >=26.3 {
/*@Mixin(LevelExtractor.class)
*///?} else {
@Mixin(LevelRenderer.class)
//?}
public class HarnessPoseMixin {
    @Inject(method = "extractEntity", at = @At("HEAD"))
    private void quarkos$pose(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> ci) {
        if (entity instanceof Player player && player == Minecraft.getInstance().player) {
            dev.quarkos.cosmetics.harness.ClientHarness.holdBody(player);
            snap(player);
        }
    }

    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void quarkos$poseState(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> ci) {
        if (!Boolean.getBoolean("quarkos_cosmetics.harness") || !(entity instanceof Player player) || player != Minecraft.getInstance().player) {
            return;
        }
        EntityRenderState state = ci.getReturnValue();
        if (!(state instanceof net.minecraft.client.renderer.entity.state.HumanoidRenderState human)) {
            return;
        }
        dev.quarkos.cosmetics.harness.ClientHarness.BodyPose mode = dev.quarkos.cosmetics.harness.ClientHarness.bodyPose;
        if (mode == dev.quarkos.cosmetics.harness.ClientHarness.BodyPose.SNEAK) {
            human.isCrouching = true;
            human.pose = Pose.CROUCHING;
        } else if (mode == dev.quarkos.cosmetics.harness.ClientHarness.BodyPose.SWIM) {
            human.isVisuallySwimming = true;
            human.swimAmount = 1f;
            human.pose = Pose.SWIMMING;
        } else if (mode == dev.quarkos.cosmetics.harness.ClientHarness.BodyPose.FLY) {
            human.isFallFlying = true;
            human.pose = Pose.FALL_FLYING;
        }
    }

    private static void snap(Player player) {
        if (!Boolean.getBoolean("quarkos_cosmetics.harness")) {
            return;
        }
        if ("1".equals(System.getProperty("quarkos_cosmetics.bed"))) {
            place(player, 0.5, 101.6875, 2.5);
            player.setDeltaMovement(0.0, 0.0, 0.0);
            player.setPose(Pose.SLEEPING);
            return;
        }
        String air = System.getProperty("quarkos_cosmetics.air");
        if (air == null || air.isBlank()) {
            return;
        }
        try {
            double y = Double.parseDouble(air);
            place(player, 0.5, y, 0.5);
            player.setDeltaMovement(0.0, -0.2, 0.0);
            player.fallDistance = 8f;
            player.setOnGround(false);
            player.setNoGravity(true);
            player.setPose(Pose.STANDING);
            player.setYRot(180f);
            player.yRotO = 180f;
        } catch (NumberFormatException ignored) {
            // A blank or bad harness value leaves the player where the tick put them.
        }
    }

    private static void place(Player player, double x, double y, double z) {
        player.setPos(x, y, z);
        player.xo = x;
        player.yo = y;
        player.zo = z;
        player.xOld = x;
        player.yOld = y;
        player.zOld = z;
    }
}
//?} else {
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Harness-only. Snaps the local player before the frame is drawn. */
@Mixin(GameRenderer.class)
public class HarnessPoseMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void quarkos$pose(DeltaTracker tracker, boolean renderLevel, CallbackInfo ci) {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            dev.quarkos.cosmetics.harness.ClientHarness.holdBody(player);
            snap(player);
        }
    }

    private static void snap(Player player) {
        if (!Boolean.getBoolean("quarkos_cosmetics.harness")) {
            return;
        }
        if ("1".equals(System.getProperty("quarkos_cosmetics.bed"))) {
            place(player, 0.5, 101.6875, 2.5);
            player.setDeltaMovement(0.0, 0.0, 0.0);
            player.setPose(Pose.SLEEPING);
            return;
        }
        String air = System.getProperty("quarkos_cosmetics.air");
        if (air == null || air.isBlank()) {
            return;
        }
        try {
            double y = Double.parseDouble(air);
            place(player, 0.5, y, 0.5);
            player.setDeltaMovement(0.0, -0.2, 0.0);
            player.fallDistance = 8f;
            player.setOnGround(false);
            player.setNoGravity(true);
            player.setPose(Pose.STANDING);
            player.setYRot(180f);
            player.yRotO = 180f;
        } catch (NumberFormatException ignored) {
            // A blank or bad harness value leaves the player where the tick put them.
        }
    }

    private static void place(Player player, double x, double y, double z) {
        player.setPos(x, y, z);
        player.xo = x;
        player.yo = y;
        player.zo = z;
        player.xOld = x;
        player.yOld = y;
        player.zOld = z;
    }
}
//?}
