package dev.quarkos.cosmetics.mixin;

//? if >=1.21.11 {
import dev.quarkos.cosmetics.harness.ClientHarness;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Harness-only. Crouch, swim, and elytra are applied on the state the model actually reads. */
@Mixin(HumanoidModel.class)
public class PoseStateMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("HEAD"))
    private void quarkos$pose(HumanoidRenderState state, CallbackInfo ci) {
        if (!Boolean.getBoolean("quarkos_cosmetics.harness")) {
            return;
        }
        if (ClientHarness.bodyPose == ClientHarness.BodyPose.SNEAK) {
            state.isCrouching = true;
            state.pose = Pose.CROUCHING;
        } else if (ClientHarness.bodyPose == ClientHarness.BodyPose.SWIM) {
            state.isVisuallySwimming = true;
            state.swimAmount = 1f;
            state.pose = Pose.SWIMMING;
        } else if (ClientHarness.bodyPose == ClientHarness.BodyPose.FLY) {
            state.isFallFlying = true;
            state.pose = Pose.FALL_FLYING;
        }
    }
}
//?} else {
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

/** 1.21.1 applies the harness pose on the entity before the frame. */
@Mixin(Minecraft.class)
public class PoseStateMixin {
}
//?}
