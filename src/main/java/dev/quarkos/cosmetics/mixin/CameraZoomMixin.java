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
            String configured = System.getProperty("quarkos_cosmetics.zoom");
            if (configured != null && !configured.isBlank()) {
                try {
                    return Float.parseFloat(configured);
                } catch (NumberFormatException ignored) {
                    return 4.15f;
                }
            }
            return 4.15f;
        }
        return zoom;
    }

    //? if >=1.21.11 {
    @Inject(method = "setup", at = @At("TAIL"))
    private void quarkos$centerBody(net.minecraft.world.level.Level level, Entity entity, boolean detached, boolean reverse, float partialTick, CallbackInfo ci) {
        quarkos$frame(detached, entity, partialTick);
    }
    //?} else {
    @Inject(method = "setup", at = @At("TAIL"))
    private void quarkos$centerBody(net.minecraft.world.level.BlockGetter level, Entity entity, boolean detached, boolean reverse, float partialTick, CallbackInfo ci) {
        quarkos$frame(detached, entity, partialTick);
    }
    //?}

    private void quarkos$frame(boolean detached, Entity entity, float partialTick) {
        if (!Boolean.getBoolean("quarkos_cosmetics.harness")) {
            return;
        }
        String front = System.getProperty("quarkos_cosmetics.front");
        if (front != null && !front.isBlank() && entity != null) {
            if ("three".equals(front)) {
                // Northwest of the face, about 45° off the look direction, so the beak stays in view.
                this.setPosition(-1.48, 102.55, -1.48);
                this.setRotation(-45f, -6f);
            } else if ("shoulder".equals(front)) {
                // North of the player, looking south. The beak on −Z points at the lens.
                this.setPosition(0.72, 102.40, -2.45);
                this.setRotation(5f, 2f);
            } else {
                // Due north of the player, looking south at the face.
                this.setPosition(0.5, 102.55, -2.30);
                this.setRotation(0f, -6f);
            }
            return;
        }
        String side = System.getProperty("quarkos_cosmetics.side");
        if (side != null && !side.isBlank() && entity != null) {
            if ("shoulder".equals(side)) {
                // Close, just west of the player, looking east. Beak runs across the frame.
                this.setPosition(-2.15, 102.32, 0.85);
                this.setRotation(262f, 0f);
            } else {
                // West of the player, looking east. Profile, so head depth and chick depth share the screen width.
                this.setPosition(-3.6, 102.5, 0.5);
                this.setRotation(270f, 2f);
            }
            return;
        }
        String sit = System.getProperty("quarkos_cosmetics.sit");
        if (sit != null && !sit.isBlank() && entity != null) {
            if ("three".equals(sit)) {
                // Southeast of the chest. The upright face and the tipped head stay in view.
                this.setPosition(1.05, 102.30, 2.90);
                this.setRotation(147f, 14f);
            } else if ("light".equals(sit)) {
                // The chick on the chest and a chicken standing east of the bed, in one daylight.
                this.setPosition(2.05, 102.70, 4.05);
                this.setRotation(178f, 28f);
            } else if ("face".equals(sit)) {
                // South of the chest, far enough that the near plane misses the player and the chick.
                // The beak points south, toward the lens.
                this.setPosition(0.55, 102.90, 4.20);
                this.setRotation(180f, 20f);
            } else {
                // Close, west of the chest, looking east. Upright profile, beak toward the pillow.
                this.setPosition(-0.42, 102.16, 2.22);
                this.setRotation(270f, 12f);
            }
            return;
        }
        if ("1".equals(System.getProperty("quarkos_cosmetics.flat")) && entity != null) {
            // South of the pillow, looking north along the mattress, so a chick lying
            // across the chest reads low and flat instead of end-on.
            this.setPosition(1.7, 102.55, 3.25);
            this.setRotation(152f, 24f);
            return;
        }
        if ("1".equals(System.getProperty("quarkos_cosmetics.chest")) && entity != null) {
            // South of the bed: the walk in, then the chick sitting upright on the chest.
            this.setPosition(1.15, 102.7, 3.55);
            this.setRotation(168f, 22f);
            return;
        }
        if ("1".equals(System.getProperty("quarkos_cosmetics.bed")) && entity != null) {
            // East of the pillow, looking down the length of the mattress so the whole body and the chick stay in frame.
            this.setPosition(3.1, 103.0, 2.6);
            this.setRotation(105f, 21f);
            return;
        }
        if ("1".equals(System.getProperty("quarkos_cosmetics.fall")) && entity instanceof net.minecraft.world.entity.player.Player player) {
            // Beside the falling player, near chest height. Gravity moves them; this only watches.
            double ay = player.yo + (player.getY() - player.yo) * partialTick;
            this.setPosition(2.6, ay + 1.35, 4.6);
            this.setRotation(158f, 6f);
            return;
        }
        String air = System.getProperty("quarkos_cosmetics.air");
        if (air != null && !air.isBlank()) {
            try {
                double ay = Double.parseDouble(air);
                if (entity instanceof net.minecraft.world.entity.player.Player player) {
                    // Move the body before the frustum is built, or the grounded player is never extracted.
                    player.setPos(0.5, ay, 0.5);
                    player.xo = 0.5;
                    player.yo = ay;
                    player.zo = 0.5;
                    player.xOld = 0.5;
                    player.yOld = ay;
                    player.zOld = 0.5;
                    player.setOnGround(false);
                    player.fallDistance = 8f;
                }
                // Beside the shoulder, near head height, so the chick and the boots stay inside the sky.
                this.setPosition(2.2, ay + 1.15, 5.8);
                this.setRotation(162f, 4f);
                return;
            } catch (NumberFormatException ignored) {
                // Fall through to the ordinary third-person frame.
            }
        }
        if (!detached) {
            return;
        }
        Vec3 pos = this.position;
        double drop = 0.55;
        try {
            drop = Double.parseDouble(System.getProperty("quarkos_cosmetics.drop", "0.55"));
        } catch (NumberFormatException ignored) {
            drop = 0.55;
        }
        double y = pos.y - drop;
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
