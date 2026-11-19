package dev.quarkos.cosmetics.mixin;

import dev.quarkos.cosmetics.harness.ClientHarness;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class HarnessMouseMixin {
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void quarkos$disableReleaseMouseGrab(CallbackInfo info) {
        if (ClientHarness.releaseCaptureEnabled()) {
            info.cancel();
        }
    }
}
