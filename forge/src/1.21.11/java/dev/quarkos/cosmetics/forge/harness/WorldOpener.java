package dev.quarkos.cosmetics.forge.harness;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

public final class WorldOpener {
    private WorldOpener() {
    }

    public static void open(Minecraft client) {
        CreateWorldScreen.openFresh(client, () -> client.setScreen(new TitleScreen()));
    }
}
