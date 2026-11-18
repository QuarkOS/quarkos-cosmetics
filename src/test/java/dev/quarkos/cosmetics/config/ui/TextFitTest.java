package dev.quarkos.cosmetics.config.ui;

import org.junit.jupiter.api.Test;

import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextFitTest {
    private static final ToIntFunction<String> MEASURE = text -> text.codePoints().map(codePoint -> codePoint == 'W' ? 3 : 1).sum();

    @Test
    void measuresGlyphsAndTheEllipsisWithinOneLine() {
        assertEquals("Hat", TextFit.ellipsis("Hat", 3, MEASURE));
        assertEquals("Wide…", TextFit.ellipsis("Wide name", 7, MEASURE));
        assertEquals("…", TextFit.ellipsis("Wardrobe", 2, MEASURE));
        assertEquals("", TextFit.ellipsis(null, 20, MEASURE));
        assertEquals("", TextFit.ellipsis("Hat", 0, MEASURE));
        assertEquals("Hat Shop", TextFit.ellipsis("Hat\nShop", 12, MEASURE));
    }

    @Test
    void keepsWholeGraphemes() {
        assertEquals("A\u0301…", TextFit.ellipsis("A\u0301BBB", 3, MEASURE));
        assertEquals("🐥…", TextFit.ellipsis("🐥Hat", 2, MEASURE));
    }
}
