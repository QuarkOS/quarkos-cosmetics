package dev.quarkos.cosmetics.mixin;

import dev.quarkos.cosmetics.harness.ClientHarness;
//? if <26.3 {
import net.minecraft.client.DeltaTracker;
//?}
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Harness-only. The wardrobe shot has to include the screen, which is drawn after the world. */
@Mixin(GameRenderer.class)
public class GuiCaptureMixin {
    //? if >=26.3 {
    /*@Inject(method = "render()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
    private void quarkos$afterWorld(CallbackInfo ci) {
        ClientHarness.capturePending();
    }

    @Inject(method = "render()V", at = @At("TAIL"))
    private void quarkos$afterGui(CallbackInfo ci) {
        ClientHarness.captureGuiPending();
    }
    *///?} else {
    @Inject(method = "render", at = @At("TAIL"))
    private void quarkos$afterGui(DeltaTracker tracker, boolean renderLevel, CallbackInfo ci) {
        ClientHarness.captureGuiPending();
    }
    //?}
}
