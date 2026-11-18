package dev.quarkos.cosmetics;

import com.mojang.blaze3d.platform.InputConstants;
import dev.quarkos.cosmetics.config.ClientSettings;
import dev.quarkos.cosmetics.config.WardrobeScreen;
import dev.quarkos.cosmetics.harness.ClientHarness;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
//? if <26.3 {
import org.lwjgl.glfw.GLFW;
//?}

public final class QuarkosClient {
    public static final String MOD_ID = "quarkos_cosmetics";

    //? if >=26.3 {
    /*public static final KeyMapping OPEN = new KeyMapping(
            "key.quarkos_cosmetics.open",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_K,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "wardrobe")));
    *///?}
    //? if >=1.21.11 && <26.3 {
    public static final KeyMapping OPEN = new KeyMapping(
            "key.quarkos_cosmetics.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "wardrobe")));
    //?}
    //? if <1.21.11 {
    /*public static final KeyMapping OPEN = new KeyMapping(
            "key.quarkos_cosmetics.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.quarkos_cosmetics");
    *///?}

    private static boolean ready;

    private QuarkosClient() {
    }

    public static void clientTick(Minecraft minecraft) {
        if (!ready) {
            ready = true;
            ClientSettings.load(minecraft.gameDirectory.toPath());
            EarsBridge.install();
        }
        ClientHarness.tick(minecraft);
        //? if >=26.3 {
        /*if (OPEN.consumeClick() && minecraft.gui.screen() == null) {
            minecraft.gui.setScreen(new WardrobeScreen(null));
        }
        *///?} else {
        if (OPEN.consumeClick() && minecraft.screen == null) {
            minecraft.setScreen(new WardrobeScreen(null));
        }
        //?}
    }
}
