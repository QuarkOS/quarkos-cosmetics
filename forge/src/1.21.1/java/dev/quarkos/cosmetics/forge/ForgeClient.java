package dev.quarkos.cosmetics.forge;

import dev.quarkos.cosmetics.config.CosmeticsScreen;
import dev.quarkos.cosmetics.forge.gallery.GalleryCache;
import dev.quarkos.cosmetics.forge.harness.ForgeHarness;
import dev.quarkos.cosmetics.forge.render.ForgeCosmeticLayer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.platform.InputConstants;

public final class ForgeClient {
    public static final KeyMapping OPEN = new KeyMapping(
            "key.quarkos_cosmetics.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.quarkos_cosmetics"
    );

    private ForgeClient() {
    }

    public static void start() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(ForgeClient::keys);
        bus.addListener(ForgeClient::layers);
        MinecraftForge.EVENT_BUS.addListener(ForgeClient::tick);
        MinecraftForge.registerConfigScreen(CosmeticsScreen::new);
        EarsHook.register();
    }

    private static void keys(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
    }

    private static void layers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            var renderer = event.getPlayerSkin(skin);
            if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer player) {
                player.addLayer(new ForgeCosmeticLayer(player));
            }
        }
    }

    private static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        SettingsStore.loadOnce();
        GalleryCache.poll();
        while (OPEN.consumeClick()) {
            minecraft.setScreen(new CosmeticsScreen(minecraft.screen));
        }
        SettingsStore.saveIfChanged();
        ForgeHarness.tick(minecraft);
    }
}
