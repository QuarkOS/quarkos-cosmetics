package dev.quarkos.cosmetics.render;

final class MaskTexels {
    static final int SHELL = 0;
    static final int CORE = 1;
    static final int RECESS = 2;
    // A normalized alpha of 0.1 is the vanilla entity cutout threshold.
    private static final int CUTOUT_ALPHA = 26;
    private static final float LIGHT_LENGTH = (float) Math.sqrt(0.2f * 0.2f + 1f + 0.7f * 0.7f);

    private MaskTexels() {
    }

    static int framePixel(int[] pixels, int width, int frameHeight, int frame, int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= frameHeight) {
            return 0;
        }
        int frames = pixels.length / width / frameHeight;
        if (frames == 0) {
            return 0;
        }
        int row = Math.floorMod(frame, frames) * frameHeight + y;
        return pixels[row * width + x];
    }

    static int glowKind(int argb) {
        if ((argb >>> 24) <= 10) {
            return SHELL;
        }
        int red = (argb >>> 16) & 255;
        int green = (argb >>> 8) & 255;
        int blue = argb & 255;
        return green >= 64 && red > blue ? CORE : RECESS;
    }

    /** Vanilla's default level diffuse lighting on the mask in its neutral pose. */
    static float daylight(float nx, float ny, float nz) {
        float first = Math.max(0f, (0.2f * nx + ny - 0.7f * nz) / LIGHT_LENGTH);
        float second = Math.max(0f, (-0.2f * nx + ny + 0.7f * nz) / LIGHT_LENGTH);
        return Math.min(1f, 0.4f + 0.6f * (first + second));
    }

    static int cutoutColor(int argb, float shade) {
        if ((argb >>> 24) < CUTOUT_ALPHA) {
            return 0;
        }
        int red = Math.round(((argb >>> 16) & 255) * shade);
        int green = Math.round(((argb >>> 8) & 255) * shade);
        int blue = Math.round((argb & 255) * shade);
        return 0xff000000 | (red << 16) | (green << 8) | blue;
    }
}
