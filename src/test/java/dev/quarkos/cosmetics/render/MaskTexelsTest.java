package dev.quarkos.cosmetics.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaskTexelsTest {
    @Test
    void samplesIntegerTexelsWithinTheSelectedFrame() {
        int[] strip = {1, 2, 3, 4, 5, 6, 7, 8};

        assertEquals(4, MaskTexels.framePixel(strip, 2, 2, 0, 1, 1));
        assertEquals(5, MaskTexels.framePixel(strip, 2, 2, 1, 0, 0));
        assertEquals(8, MaskTexels.framePixel(strip, 2, 2, 1, 1, 1));
        assertEquals(4, MaskTexels.framePixel(strip, 2, 2, 2, 1, 1));
        assertEquals(8, MaskTexels.framePixel(strip, 2, 2, -1, 1, 1));
        assertEquals(0, MaskTexels.framePixel(strip, 2, 2, 0, 0, 2));
        assertEquals(0, MaskTexels.framePixel(strip, 2, 2, 1, 2, 0));
        assertEquals(4, MaskTexels.framePixel(new int[] {1, 2, 3, 4}, 2, 2, 23, 1, 1));
        assertEquals(0, MaskTexels.framePixel(new int[] {1, 2, 3}, 2, 2, 0, 1, 1));
    }

    @Test
    void separatesCandleCoresAndRecessesAndKeepsTheCutout() {
        assertEquals(MaskTexels.SHELL, MaskTexels.glowKind(0x00ffeeb0));
        assertEquals(MaskTexels.RECESS, MaskTexels.glowKind(0xff871d0a));
        assertEquals(MaskTexels.CORE, MaskTexels.glowKind(0xffe99422));
        assertEquals(0, MaskTexels.cutoutColor(0x19e99422, 1f));
        assertEquals(0xffe99422, MaskTexels.cutoutColor(0x1ae99422, 1f));
        assertEquals(0xff440f05, MaskTexels.cutoutColor(0xff871d0a, 0.5f));
    }

    @Test
    void usesLevelLightForTheNeutralMaskFaces() {
        float front = MaskTexels.daylight(0f, 0f, -1f);
        float side = MaskTexels.daylight(1f, 0f, 0f);

        assertEquals(0.74f, front, 0.003f);
        assertEquals(front, MaskTexels.daylight(0f, 0f, 1f));
        assertEquals(side, MaskTexels.daylight(-1f, 0f, 0f));
        assertTrue(front > side);
        assertEquals(1f, MaskTexels.daylight(0f, 1f, 0f));
        assertEquals(0.4f, MaskTexels.daylight(0f, -1f, 0f));
    }
}
