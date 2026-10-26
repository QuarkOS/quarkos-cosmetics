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
        button.onPress(new net.minecraft.client.input.InputWithModifiers() {
            @Override
            public int input() {
                return 0;
            }

            @Override
            public int modifiers() {
                return 0;
            }
        });
    }

    public static void save(Minecraft client, Path path) {
        Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
            try {
                image.writeToFile(path);
                LOGGER.info("wrote {}", path);
            } catch (Exception e) {
                LOGGER.error("failed to write {}", path, e);
            } finally {
                image.close();
            }
        });
    }
}
