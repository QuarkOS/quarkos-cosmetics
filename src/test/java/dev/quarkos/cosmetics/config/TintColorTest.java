package dev.quarkos.cosmetics.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TintColorTest {
    @Test
    void hueWrapsAndKeepsChannelsInRgbOrder() {
        assertEquals(0xFF7373, TintColor.fromHue(0));
        assertEquals(0x73FF73, TintColor.fromHue(120));
        assertEquals(0x7373FF, TintColor.fromHue(240));
        assertEquals(TintColor.fromHue(0), TintColor.fromHue(360));
        assertEquals(TintColor.fromHue(359), TintColor.fromHue(-1));
        for (int degrees : new int[] {0, 35, 120, 184, 240, 300, 359}) {
            assertEquals(degrees, TintColor.hue(TintColor.fromHue(degrees)));
        }
    }

    @Test
    void serializesRgbAndFallsBackToTheIdentityColour() {
        assertEquals("#007BBC", TintColor.encode(0x007BBC));
        assertEquals(0x007BBC, TintColor.decode("#007bbc"));
        assertEquals("#000000", TintColor.encode(0));
        assertEquals(0, TintColor.decode("#000000"));
        assertEquals(TintColor.DEFAULT, TintColor.decode(null));
        assertEquals(TintColor.DEFAULT, TintColor.decode("#bbcc"));
        assertEquals(TintColor.DEFAULT, TintColor.decode("#00nope"));
        assertEquals(TintColor.DEFAULT, TintColor.decode("#FFFFFFFF"));
    }
}
