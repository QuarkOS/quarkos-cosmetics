package dev.quarkos.cosmetics.config.ui;

import dev.quarkos.cosmetics.config.WardrobeLayout.Rect;

/** Whole-row scrolling keeps every visible card fully clickable. */
public record GridMetrics(Rect viewport, int count, int columns, int cardHeight, int pageRows) {
    public static final int GAP = 6;
    private static final int SCROLL_GUTTER = 10;

    public static GridMetrics fit(Rect viewport, int count, int minCardWidth, int preferredHeight, int maxColumns) {
        int width = viewport.w() - SCROLL_GUTTER;
        int columns = Math.clamp((width + GAP) / (minCardWidth + GAP), 1, maxColumns);
        int height = Math.clamp(preferredHeight, 1, Math.max(1, viewport.h()));
        int pageRows = Math.max(1, (viewport.h() + GAP) / (height + GAP));
        return new GridMetrics(viewport, count, columns, height, pageRows);
    }

    public int rows() {
        return count == 0 ? 0 : (count - 1) / columns + 1;
    }

    public int maxScroll() {
        return Math.max(0, rows() - pageRows);
    }

    public int clamp(int row) {
        return Math.clamp(row, 0, maxScroll());
    }

    public int first(int row) {
        return clamp(row) * columns;
    }

    public int end(int row) {
        return Math.min(count, first(row) + pageRows * columns);
    }

    public int reveal(int index, int row) {
        if (index < 0 || index >= count) {
            return clamp(row);
        }
        int cardRow = index / columns;
        return clamp(Math.clamp(row, Math.max(0, cardRow - pageRows + 1), cardRow));
    }

    public Rect card(int index, int row) {
        Rect strip = new Rect(viewport.x(), viewport.y() + (index / columns - clamp(row)) * (cardHeight + GAP),
                viewport.w() - SCROLL_GUTTER, cardHeight);
        return strip.column(index % columns, columns, GAP);
    }

    public Rect scrollbar() {
        return new Rect(viewport.right() - 4, viewport.y(), 4, viewport.h());
    }

    public Rect thumb(int row) {
        Rect track = scrollbar();
        if (maxScroll() == 0) {
            return track;
        }
        int size = Math.min(track.h(), Math.max(12, Math.round((float) track.h() * pageRows / rows())));
        int offset = Math.round((float) (track.h() - size) * clamp(row) / maxScroll());
        return new Rect(track.x(), track.y() + offset, track.w(), size);
    }

    public int rowAt(double thumbTop) {
        int travel = scrollbar().h() - thumb(0).h();
        return travel <= 0 ? 0 : clamp((int) Math.round((thumbTop - viewport.y()) * maxScroll() / travel));
    }
}
