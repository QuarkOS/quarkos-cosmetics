package dev.quarkos.cosmetics.harness;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

record ModelVisibility(int changedPx, int width, int height, int minimumPx) {
    private static final int CHANNEL_DELTA = 8;

    boolean visible() {
        return changedPx >= minimumPx;
    }

    static ModelVisibility compare(Path baseline, Path equipped) throws IOException {
        BufferedImage before = ImageIO.read(baseline.toFile());
        BufferedImage after = ImageIO.read(equipped.toFile());
        if (before == null || after == null || before.getWidth() != after.getWidth() || before.getHeight() != after.getHeight()) {
            throw new IOException("Model screenshots must be readable and have matching dimensions");
        }

        int width = before.getWidth();
        int height = before.getHeight();
        // The studio camera centers the player. Exclude distant terrain at the sides.
        int margin = width / 6;
        int span = width - 2 * margin;
        int[] beforeRow = new int[span];
        int[] afterRow = new int[span];
        int minX = span, maxX = -1, minY = height, maxY = -1, changes = 0;
        for (int y = 0; y < height; y++) {
            before.getRGB(margin, y, span, 1, beforeRow, 0, span);
            after.getRGB(margin, y, span, 1, afterRow, 0, span);
            for (int x = 0; x < span; x++) {
                int a = beforeRow[x], b = afterRow[x];
                if (delta(a, b, 16) >= CHANNEL_DELTA || delta(a, b, 8) >= CHANNEL_DELTA || delta(a, b, 0) >= CHANNEL_DELTA) {
                    changes++;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = y;
                }
            }
        }
        int minimum = (int) Math.max(8, (long) width * height / 30_000);
        return changes == 0 ? new ModelVisibility(0, 0, 0, minimum)
                : new ModelVisibility(changes, maxX - minX + 1, maxY - minY + 1, minimum);
    }

    private static int delta(int before, int after, int shift) {
        return Math.abs(((before >>> shift) & 255) - ((after >>> shift) & 255));
    }
}
