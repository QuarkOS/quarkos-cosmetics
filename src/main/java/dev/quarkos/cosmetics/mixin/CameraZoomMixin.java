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

/** Harness-only third-person framing. Far enough that the head, a tall hat, and the feet all stay inside the frame. */
@Mixin(Camera.class)
public abstract class CameraZoomMixin {
    @Shadow
    private Vec3 position;

    @Shadow
    private float xRot;

    @Shadow
    private float yRot;

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @ModifyVariable(method = "getMaxZoom", at = @At("HEAD"), argsOnly = true)
    private float quarkos$portraitDistance(float zoom) {
        if (Boolean.getBoolean("quarkos_cosmetics.harness")) {
            return 4.15f;
        }
        return zoom;
    }

    //? if >=1.21.11 {
    @Inject(method = "setup", at = @At("TAIL"))
    private void quarkos$centerBody(net.minecraft.world.level.Level level, Entity entity, boolean detached, boolean reverse, float partialTick, CallbackInfo ci) {
        quarkos$frame(detached, entity);
    }
    //?} else {
    @Inject(method = "setup", at = @At("TAIL"))
    private void quarkos$centerBody(net.minecraft.world.level.BlockGetter level, Entity entity, boolean detached, boolean reverse, float partialTick, CallbackInfo ci) {
        quarkos$frame(detached, entity);
    }
    //?}

    private void quarkos$frame(boolean detached, Entity entity) {
        if (!detached || !Boolean.getBoolean("quarkos_cosmetics.harness")) {
            return;
        }
        Vec3 pos = this.position;
        double y = pos.y - 0.55;
        float orbit = 0f;
        try {
            orbit = Float.parseFloat(System.getProperty("quarkos_cosmetics.orbit", "0"));
        } catch (NumberFormatException ignored) {
            orbit = 0f;
        }
        if (orbit != 0f && entity != null) {
            Vec3 eye = entity.getEyePosition();
            double dx = pos.x - eye.x;
            double dz = pos.z - eye.z;
            double rad = Math.toRadians(orbit);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            this.setPosition(eye.x + dx * cos - dz * sin, y, eye.z + dx * sin + dz * cos);
            this.setRotation(this.yRot + orbit, this.xRot);
            return;
        }
        this.setPosition(pos.x, y, pos.z);
    }
}
