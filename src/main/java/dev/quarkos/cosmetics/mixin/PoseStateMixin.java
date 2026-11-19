package dev.quarkos.cosmetics.mixin;

//? if >=1.21.11 {
import dev.quarkos.cosmetics.harness.ClientHarness;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Harness-only. Crouch, swim, and elytra are applied on the state the model actually reads. */
@Mixin(HumanoidModel.class)
public class PoseStateMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("HEAD"))
    private void quarkos$pose(HumanoidRenderState state, CallbackInfo ci) {
        if (!Boolean.getBoolean("quarkos_cosmetics.harness")) {
            return;
        }
        if (ClientHarness.takingModelShots()) {
            state.walkAnimationSpeed = 0f;
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

    @ModifyArg(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/AnimationUtils;bobModelPart(Lnet/minecraft/client/model/geom/ModelPart;FF)V"),
            index = 1)
    private float quarkos$modelShotBob(float age) {
        return ClientHarness.takingModelShots() ? 12f : age;
    }
}
//?} else {
import dev.quarkos.cosmetics.harness.ClientHarness;
import net.minecraft.client.model.AnimationUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(AnimationUtils.class)
public class PoseStateMixin {
    @ModifyVariable(method = "bobModelPart", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static float quarkos$modelShotBob(float age) {
        return ClientHarness.takingModelShots() ? 12f : age;
    }
}
//?}
