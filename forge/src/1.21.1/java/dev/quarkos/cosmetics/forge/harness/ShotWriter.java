package dev.quarkos.cosmetics.forge.harness;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class ShotWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger("quarkos_cosmetics");

    private ShotWriter() {
    }

    public static void press(Button button) {
        button.onPress();
    }

    public static void save(Minecraft client, Path path) {
        try {
            var image = Screenshot.takeScreenshot(client.getMainRenderTarget());
            image.writeToFile(path);
            image.close();
            LOGGER.info("wrote {}", path);
        } catch (Exception e) {
            LOGGER.error("failed to write {}", path, e);
        }
    }
}
