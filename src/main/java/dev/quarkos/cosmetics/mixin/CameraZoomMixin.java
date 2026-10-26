package dev.quarkos.cosmetics.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Harness-only third-person framing: closer zoom, and a drop so the body — not the eyes — sits in frame. */
@Mixin(Camera.class)
public abstract class CameraZoomMixin {
    @Shadow
    private Vec3 position;

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @ModifyVariable(method = "getMaxZoom", at = @At("HEAD"), argsOnly = true)
    private float quarkos$portraitDistance(float zoom) {
        if (Boolean.getBoolean("quarkos_cosmetics.harness")) {
            return 2.55f;
        }
        return zoom;
    }

    //? if >=1.21.11 {
    @Inject(method = "setup", at = @At("TAIL"))
    private void quarkos$centerBody(net.minecraft.world.level.Level level, Entity entity, boolean detached, boolean reverse, float partialTick, CallbackInfo ci) {
        quarkos$drop(detached);
    }
    //?} else {
    @Inject(method = "setup", at = @At("TAIL"))
    private void quarkos$centerBody(net.minecraft.world.level.BlockGetter level, Entity entity, boolean detached, boolean reverse, float partialTick, CallbackInfo ci) {
        quarkos$drop(detached);
    }
    //?}

    private void quarkos$drop(boolean detached) {
        if (!detached || !Boolean.getBoolean("quarkos_cosmetics.harness")) {
            return;
        }
        Vec3 pos = this.position;
        this.setPosition(pos.x, pos.y - 0.42, pos.z);
    }
}
