package dev.quarkos.cosmetics.config.ui;

import dev.quarkos.cosmetics.config.WardrobeLayout.Rect;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GridMetricsTest {
    @Test
    void allCardsAreReachableIncludingTheLastPartialRow() {
        Rect viewport = new Rect(12, 48, 300, 122);
        var grid = GridMetrics.fit(viewport, 161, 74, 58, 3);
        assertEquals(3, grid.columns());
        assertEquals(2, grid.pageRows());
        assertEquals(54, grid.rows());
        assertEquals(52, grid.maxScroll());
        assertEquals(0, grid.first(-20));
        assertEquals(161, grid.end(100));

        BitSet seen = new BitSet();
        for (int row = 0; row <= grid.maxScroll(); row++) {
            for (int i = grid.first(row); i < grid.end(row); i++) {
                Rect card = grid.card(i, row);
                assertTrue(card.x() >= viewport.x() && card.y() >= viewport.y());
                assertTrue(card.right() <= grid.scrollbar().x() && card.bottom() <= viewport.bottom());
                assertTrue(card.w() >= 74 && card.h() >= 58);
                if (i + 1 < grid.end(row) && (i + 1) % grid.columns() != 0) {
                    assertTrue(card.right() < grid.card(i + 1, row).x());
                }
                seen.set(i);
            }
        }
        assertEquals(161, seen.cardinality());
    }

    @Test
    void scrollbarAndRevealClampAtBothEnds() {
        Rect viewport = new Rect(10, 12, 246, 72);
        var grid = GridMetrics.fit(viewport, 22, 74, 58, 3);
        assertEquals(3, grid.columns());
        assertEquals(1, grid.pageRows());
        assertEquals(viewport.y(), grid.thumb(-1).y());
        assertEquals(viewport.bottom(), grid.thumb(100).bottom());
        assertEquals(0, grid.rowAt(-1));
        assertEquals(grid.maxScroll(), grid.rowAt(1000));
        assertEquals(grid.maxScroll(), grid.reveal(21, 0));
        assertEquals(0, grid.reveal(0, grid.maxScroll()));

        var empty = GridMetrics.fit(viewport, 0, 74, 58, 3);
        assertEquals(0, empty.end(0));
        assertEquals(0, empty.maxScroll());
        assertEquals(empty.scrollbar(), empty.thumb(0));
        assertEquals(0, empty.rowAt(1000));
    }

    @Test
    void shortViewportStillShowsOneWholeRow() {
        var grid = GridMetrics.fit(new Rect(7, 10, 220, 46), 6, 74, 58, 3);
        assertEquals(2, grid.columns());
        assertEquals(1, grid.pageRows());
        assertEquals(46, grid.card(0, 0).h());
        assertEquals(4, grid.first(grid.maxScroll()));
        assertEquals(6, grid.end(grid.maxScroll()));
    }
}
