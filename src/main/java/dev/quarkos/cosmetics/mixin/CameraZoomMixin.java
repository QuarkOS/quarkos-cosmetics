package dev.quarkos.cosmetics.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Pulls the harness camera in so a third-person shot shows the whole player, not a distant speck. */
@Mixin(Camera.class)
public abstract class CameraZoomMixin {
    @ModifyVariable(method = "getMaxZoom", at = @At("HEAD"), argsOnly = true)
    private float quarkos$portraitDistance(float zoom) {
        if (Boolean.getBoolean("quarkos_cosmetics.harness")) {
            return 3.4f;
        }
        return zoom;
    }
}
