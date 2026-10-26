package dev.quarkos.cosmetics.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Stable wardrobe regions. A later reskin should keep these ids and only change
 * {@link Palette} or the rectangles.
 */
public final class WardrobeLayout {
    public static final String HEADER = "header";
    public static final String PREVIEW = "preview";
    public static final String TABS = "tabs";
    public static final String LIST = "list";
    public static final String DETAIL = "detail";
    public static final String GALLERY = "gallery";
    public static final String SETTINGS = "settings";

    public record Rect(String id, int x, int y, int w, int h) {
    }

    public static final class Palette {
        public static final int INK = 0xF0181614;
        public static final int PANEL = 0xE028241C;
        public static final int LINE = 0xFF8A5A32;
        public static final int AMBER = 0xFFE08A3C;
        public static final int PAPER = 0xFFF4E2B0;
        public static final int MUTED = 0xFFB7A48A;

        private Palette() {
        }
    }

    private WardrobeLayout() {
    }

    public static boolean narrow(int width) {
        return width < 640;
    }

    public static List<Rect> regions(int width, int height) {
        List<Rect> regions = new ArrayList<>();
        int margin = 8;
        regions.add(new Rect(HEADER, margin, 6, width - margin * 2, 22));
        if (narrow(width)) {
            int y = 32;
            regions.add(new Rect(PREVIEW, margin, y, width - margin * 2, 108));
            y += 112;
            regions.add(new Rect(TABS, margin, y, width - margin * 2, 22));
            y += 26;
            regions.add(new Rect(LIST, margin, y, width - margin * 2, 72));
            y += 76;
            regions.add(new Rect(DETAIL, margin, y, width - margin * 2, 44));
            y += 48;
            regions.add(new Rect(GALLERY, margin, y, width - margin * 2, Math.max(72, height - y - 78)));
            regions.add(new Rect(SETTINGS, margin, height - 70, width - margin * 2, 62));
            return regions;
        }
        int previewW = Math.min(168, width / 5);
        regions.add(new Rect(PREVIEW, margin, 34, previewW, height - 78));
        int x = margin + previewW + 8;
        int right = width - margin;
        int bodyW = right - x;
        regions.add(new Rect(TABS, x, 34, bodyW, 22));
        int listW = Math.max(120, bodyW / 2 - 4);
        regions.add(new Rect(LIST, x, 62, listW, height - 168));
        regions.add(new Rect(DETAIL, x, height - 100, listW, 28));
        regions.add(new Rect(GALLERY, x + listW + 8, 62, bodyW - listW - 8, height - 132));
        regions.add(new Rect(SETTINGS, x, height - 66, bodyW, 58));
        return regions;
    }

    public static Rect find(List<Rect> regions, String id) {
        for (Rect rect : regions) {
            if (rect.id.equals(id)) {
                return rect;
            }
        }
        return new Rect(id, 0, 0, 10, 10);
    }
}
