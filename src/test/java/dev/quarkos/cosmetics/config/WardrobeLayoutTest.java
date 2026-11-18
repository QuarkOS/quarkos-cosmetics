package dev.quarkos.cosmetics.config;

import dev.quarkos.cosmetics.config.WardrobeLayout.Rect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WardrobeLayoutTest {
    @Test
    void frameAndAllPanelsFitTheTargetGuiSizes() {
        // 960×540 and 480×270 cover the requested physical sizes and GUI scales.
        for (Rect screen : List.of(new Rect(0, 0, 960, 540), new Rect(0, 0, 480, 270),
                new Rect(0, 0, 640, 360), new Rect(0, 0, 320, 240), new Rect(0, 0, 1920, 1080))) {
            var frame = WardrobeLayout.frame(screen.w(), screen.h());
            checkRegions(screen, List.of(frame.header(), frame.preview(), frame.content()));
            checkRegions(frame.preview(), List.of(frame.stage()));
            assertTrue(frame.header().h() >= 24);
            assertTrue(frame.stage().w() >= 64);
            assertTrue(frame.stage().h() >= 120);

            for (boolean expanded : new boolean[] {false, true}) {
                var wardrobe = WardrobeLayout.collection(frame, expanded);
                checkRegions(frame.content(), List.of(wardrobe.toolbar(), wardrobe.grid(), wardrobe.equipped(), wardrobe.colour()));
                assertTrue(wardrobe.toolbar().h() >= 20);
                assertTrue(wardrobe.grid().h() >= 44);
                assertTrue(wardrobe.equipped().h() >= 30);
                assertTrue(expanded ? wardrobe.colour().h() >= 44 : wardrobe.colour().h() == 0);

                var gallery = WardrobeLayout.gallery(frame, expanded);
                checkRegions(frame.content(), List.of(gallery.account(), gallery.connection(), gallery.filters(), gallery.grid(), gallery.actions()));
                assertTrue(gallery.account().h() >= 20);
                assertTrue(gallery.filters().h() >= 20);
                assertTrue(gallery.grid().h() >= 44);
                assertTrue(gallery.actions().h() >= 20);
            }

            var settings = WardrobeLayout.settings(frame);
            checkRegions(frame.content(), List.of(settings.hideOthers(), settings.skinLayer(), settings.skinDistance()));
            assertTrue(settings.hideOthers().h() >= 34);
            assertTrue(settings.skinLayer().h() >= 34);
            assertTrue(settings.skinDistance().h() >= 24);
        }
    }

    private static void checkRegions(Rect parent, List<Rect> children) {
        for (int i = 0; i < children.size(); i++) {
            Rect a = children.get(i);
            assertTrue(a.x() >= parent.x() && a.y() >= parent.y() && a.right() <= parent.right() && a.bottom() <= parent.bottom(),
                    () -> a + " must fit " + parent);
            assertTrue(a.w() > 0 && a.h() >= 0);
            if (a.h() == 0) {
                continue;
            }
            for (int j = i + 1; j < children.size(); j++) {
                Rect b = children.get(j);
                assertFalse(b.h() > 0 && a.x() < b.right() && b.x() < a.right() && a.y() < b.bottom() && b.y() < a.bottom(),
                        () -> a + " overlaps " + b);
            }
        }
    }
}
