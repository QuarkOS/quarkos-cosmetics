package dev.quarkos.cosmetics.harness;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelVisibilityTest {
    @TempDir
    Path directory;

    @Test
    void distinguishesAnInvisibleModelFromAVisibleOneInSavedShots() throws Exception {
        var baseline = new BufferedImage(960, 540, BufferedImage.TYPE_INT_RGB);
        var equipped = new BufferedImage(960, 540, BufferedImage.TYPE_INT_RGB);
        var baselinePath = directory.resolve("baseline.png");
        var modelPath = directory.resolve("model.png");
        ImageIO.write(baseline, "png", baselinePath.toFile());
        for (int y = 100; y < 106; y++) {
            for (int x = 0; x < 4; x++) {
                equipped.setRGB(x, y, 0xffffff);
                equipped.setRGB(480 + x, y, 0x070707);
            }
        }
        ImageIO.write(equipped, "png", modelPath.toFile());

        var invisible = ModelVisibility.compare(baselinePath, modelPath);
        assertFalse(invisible.visible());
        assertEquals(0, invisible.changedPx());
        assertEquals(0, invisible.width());
        assertEquals(0, invisible.height());

        for (int y = 100; y < 106; y++) {
            for (int x = 480; x < 484; x++) {
                equipped.setRGB(x, y, 0x080000);
            }
        }
        ImageIO.write(equipped, "png", modelPath.toFile());

        var visible = ModelVisibility.compare(baselinePath, modelPath);
        assertTrue(visible.visible());
        assertEquals(24, visible.changedPx());
        assertEquals(4, visible.width());
        assertEquals(6, visible.height());
    }
}
