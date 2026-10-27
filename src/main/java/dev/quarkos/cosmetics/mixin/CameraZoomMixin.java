package dev.quarkos.cosmetics.mixin;

import dev.quarkos.cosmetics.harness.ClientHarness;
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
            return ClientHarness.portraitDistance;
        }
        return zoom;
    }

    //? if >=1.21.11 {
    @Inject(method = "setup", at = @At("TAIL"))
    private void quarkos$centerBody(net.minecraft.world.level.Level level, Entity entity, boolean detached, boolean reverse, float partialTick, CallbackInfo ci) {
        quarkos$frame(entity, detached, partialTick);
    }
    //?} else {
    @Inject(method = "setup", at = @At("TAIL"))
    private void quarkos$centerBody(net.minecraft.world.level.BlockGetter level, Entity entity, boolean detached, boolean reverse, float partialTick, CallbackInfo ci) {
        quarkos$frame(entity, detached, partialTick);
    }
    //?}

    private void quarkos$frame(Entity entity, boolean detached, float partialTick) {
        if (!detached || !Boolean.getBoolean("quarkos_cosmetics.harness")) {
            return;
        }
        Vec3 pos = this.position;
        double y = pos.y - ClientHarness.portraitDrop;
        float orbit = ClientHarness.portraitOrbit;
        if (orbit != 0f && entity != null) {
            Vec3 eye = entity.getEyePosition(partialTick);
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
