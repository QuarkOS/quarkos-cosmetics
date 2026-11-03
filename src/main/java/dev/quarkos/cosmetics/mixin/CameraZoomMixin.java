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
        if (!Boolean.getBoolean("quarkos_cosmetics.harness")) {
            return;
        }
        if (ClientHarness.portraitLock) {
            this.setPosition(ClientHarness.lockX, ClientHarness.lockY, ClientHarness.lockZ);
            this.setRotation(ClientHarness.lockYaw, ClientHarness.lockPitch);
            return;
        }
        if (!detached) {
            return;
        }
        float orbit = ClientHarness.portraitOrbit;
        if (orbit == 0f) {
            Vec3 pos = this.position;
            this.setPosition(pos.x, pos.y - ClientHarness.portraitDrop, pos.z);
            return;
        }
        double x = entity.xo + (entity.getX() - entity.xo) * partialTick;
        double y = entity.yo + (entity.getY() - entity.yo) * partialTick + entity.getBbHeight() * 0.5;
        double z = entity.zo + (entity.getZ() - entity.zo) * partialTick;
        double yaw = Math.toRadians(entity.getYRot() + orbit);
        double dist = ClientHarness.portraitDistance;
        this.setPosition(x + Math.sin(yaw) * dist, y - ClientHarness.portraitDrop, z - Math.cos(yaw) * dist);
        this.setRotation(entity.getYRot() + orbit, 4f);
    }
}
