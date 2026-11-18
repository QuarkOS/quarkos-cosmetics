package dev.quarkos.cosmetics.config;

import java.awt.Color;
import java.util.Locale;

/** RGB multipliers. White preserves the authored texture and stands for Default. */
public final class TintColor {
    public static final int DEFAULT = 0xFFFFFF;

    private TintColor() {
    }

    public static int fromHue(int degrees) {
        return Color.HSBtoRGB(Math.floorMod(degrees, 360) / 360f, 0.55f, 1f) & DEFAULT;
    }

    public static int hue(int rgb) {
        float[] hsb = Color.RGBtoHSB((rgb >>> 16) & 255, (rgb >>> 8) & 255, rgb & 255, null);
        return Math.floorMod(Math.round(hsb[0] * 360f), 360);
    }

    public static String encode(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & DEFAULT);
    }

    public static int decode(String raw) {
        if (raw == null || !raw.matches("#[0-9a-fA-F]{6}")) {
            return DEFAULT;
        }
        return Integer.parseInt(raw.substring(1), 16);
    }
}
