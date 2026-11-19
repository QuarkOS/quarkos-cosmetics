package dev.quarkos.cosmetics.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChickPerchTest {
    private static final float EPSILON = 0.00001f;

    @Test
    void scalesTheMeshToTwoThirdsOfTheHeadWidth() {
        assertEquals(16f / 3f, 6f * ChickPerch.scaleForWidth(6f), EPSILON);
        assertEquals(16f / 3f, 5f * ChickPerch.scaleForWidth(5f), EPSILON);
    }

    @Test
    void placesStandingFeetOnTheSkullOrHatLayer() {
        float scale = ChickPerch.scaleForWidth(6f);
        float floor = -0.4f;
        float footY = floor * scale + ChickPerch.anchorOffset(floor, scale);

        assertEquals(8f, ChickPerch.headSurfaceY(false, false) + footY, EPSILON);
        assertEquals(8.5f, ChickPerch.headSurfaceY(false, true) + footY, EPSILON);
        assertEquals(9f, ChickPerch.headSurfaceY(true, true) + footY, EPSILON);
    }

    @Test
    void bringsTuckedFeetDownToTheBellyWithoutLoweringTheBellyThroughThePerch() {
        float scale = ChickPerch.scaleForWidth(6f);
        float bellyY = 0.1f;
        float soleY = 1.2f;
        float baseY = 8.5f + ChickPerch.anchorOffset(bellyY, scale);
        float plantedSoleY = soleY + ChickPerch.footOffset(bellyY, soleY, soleY, 1f);

        assertEquals(8.5f, baseY + bellyY * scale, EPSILON);
        assertEquals(8.5f, baseY + plantedSoleY * scale, EPSILON);
    }

    @Test
    void keepsTheRaisedFootDuringAStride() {
        float contactY = 0.05f;
        float leftY = 0.2f;
        float rightY = 0.8f;

        assertEquals(contactY, leftY + ChickPerch.footOffset(contactY, leftY, leftY, 0f), EPSILON);
        assertEquals(contactY + 0.6f, rightY + ChickPerch.footOffset(contactY, leftY, rightY, 0f), EPSILON);
    }
}
