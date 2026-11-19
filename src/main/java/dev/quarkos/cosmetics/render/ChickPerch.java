package dev.quarkos.cosmetics.render;

final class ChickPerch {
    static final float HEAD_WIDTH_PX = 8f;

    private ChickPerch() {
    }

    static float headSurfaceY(boolean helmet, boolean hatLayer) {
        return 8f + (helmet ? 1f : hatLayer ? 0.5f : 0f);
    }

    static float scaleForWidth(float meshWidth) {
        return (HEAD_WIDTH_PX * 2f / 3f) / meshWidth;
    }

    static float anchorOffset(float contactY, float scale) {
        return -contactY * scale;
    }

    static float footOffset(float contactY, float lowestSoleY, float soleY, float planted) {
        return contactY - lowestSoleY + (lowestSoleY - soleY) * planted;
    }
}
