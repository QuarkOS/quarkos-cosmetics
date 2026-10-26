package dev.quarkos.cosmetics.forge;

import dev.quarkos.cosmetics.config.CosmeticsScreen;
import dev.quarkos.cosmetics.forge.harness.ForgeHarness;
import dev.quarkos.cosmetics.forge.gallery.GalleryCache;
import dev.quarkos.cosmetics.forge.render.ForgeCosmeticLayer;
import dev.quarkos.cosmetics.render.WingLayer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

public final class ForgeClient {
    public static final KeyMapping OPEN = new KeyMapping(
            "key.quarkos_cosmetics.open",
            GLFW.GLFW_KEY_K,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("quarkos_cosmetics", "wardrobe"))
    );

    private ForgeClient() {
    }

    public static void start() {
        MinecraftForge.registerConfigScreen(CosmeticsScreen::new);
        RegisterKeyMappingsEvent.BUS.addListener(event -> event.register(OPEN));
        EntityRenderersEvent.AddLayers.BUS.addListener(ForgeClient::layers);
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> tick());
        EarsHook.register();
    }

    private static void layers(EntityRenderersEvent.AddLayers event) {
        for (PlayerModelType type : event.getModelTypes()) {
            if (event.getPlayerRenderer(type) instanceof AvatarRenderer<?> player) {
                player.addLayer(new WingLayer(player));
                player.addLayer(new ForgeCosmeticLayer(player));
            }
            if (event.getMannequinRenderer(type) instanceof AvatarRenderer<?> mannequin) {
                mannequin.addLayer(new WingLayer(mannequin));
                mannequin.addLayer(new ForgeCosmeticLayer(mannequin));
            }
        }
    }

    private static void tick() {
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
