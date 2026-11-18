package dev.quarkos.cosmetics;

//? if fabric {
import dev.quarkos.cosmetics.render.MaskGlow;
import net.fabricmc.api.ClientModInitializer;
//? if >=26.3 {
/*import dev.quarkos.cosmetics.config.ui.ThumbnailRenderer;
import dev.quarkos.cosmetics.harness.ClientHarness;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
*///?} else {
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
//?}

public final class FabricQuarkosCosmetics implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MaskGlow.boot();
        //? if >=26.3 {
        /*PictureInPictureRendererRegistry.register(context -> new ThumbnailRenderer());
        ClientHarness.shotAtFrameEnd();
        ClientTickEvents.END_CLIENT_TICK.register(QuarkosClient::clientTick);
        KeyMappingHelper.registerKeyMapping(QuarkosClient.OPEN);
        *///?}
        //? if >=1.21.11 && <26.3 {
        net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry.register(context ->
                new dev.quarkos.cosmetics.config.ui.ThumbnailRenderer(context.vertexConsumers()));
        dev.quarkos.cosmetics.render.JackOLanternMask.useDeferredGlow();
        dev.quarkos.cosmetics.harness.ClientHarness.shotAtFrameEnd();
        // After the shell is in the framebuffer, so the candle is not covered by the rind.
        net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.END_MAIN.register(context -> {
            dev.quarkos.cosmetics.render.JackOLanternMask.flush();
            dev.quarkos.cosmetics.harness.ClientHarness.capturePending();
        });
        //?}
        //? if <26.3 {
        KeyBindingHelper.registerKeyBinding(QuarkosClient.OPEN);
        //?}
    }
}
//?}
