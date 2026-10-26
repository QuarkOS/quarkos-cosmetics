package dev.quarkos.cosmetics;

import dev.quarkos.cosmetics.harness.ClientHarness;
import dev.quarkos.cosmetics.render.WingLayer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;

public final class FabricQuarkosCosmetics implements ClientModInitializer {
    public static final String MOD_ID = "quarkos_cosmetics";

    @Override
    public void onInitializeClient() {
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
            if (renderer instanceof AvatarRenderer<?> avatar) {
                helper.register(new WingLayer(avatar));
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(ClientHarness::tick);
    }
}
