package dev.quarkos.cosmetics.config;

/** Layout in logical GUI pixels. Each view shares the same header and preview. */
public final class WardrobeLayout {
    private static final int INSET = 10;
    private static final int GAP = 8;

    private WardrobeLayout() {
    }

    public record Rect(int x, int y, int w, int h) {
        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }

        public Rect inset(int amount) {
            int dx = Math.min(amount, w / 2);
            int dy = Math.min(amount, h / 2);
            return new Rect(x + dx, y + dy, w - 2 * dx, h - 2 * dy);
        }

        public Rect column(int index, int count, int gap) {
            int usable = w - gap * (count - 1);
            int start = usable * index / count;
            int end = usable * (index + 1) / count;
            return new Rect(x + start + index * gap, y, end - start, h);
        }
    }

    public record Frame(Rect header, Rect preview, Rect stage, Rect content) {
    }

    public record Collection(Rect toolbar, Rect grid, Rect equipped, Rect colour) {
    }

    public record Gallery(Rect account, Rect connection, Rect filters, Rect grid, Rect actions) {
    }

    public record Settings(Rect hideOthers, Rect skinLayer, Rect skinDistance) {
    }

    public static Frame frame(int width, int height) {
        int margin = width < 560 ? 10 : 16;
        int w = Math.min(936, width - margin * 2);
        int h = Math.min(480, height - margin * 2);
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        Rect header = new Rect(x, y, w, 30);
        int bodyY = header.bottom() + GAP;
        int bodyH = y + h - bodyY;
        int previewW = Math.min(254, w * 29 / 100);
        Rect preview = new Rect(x, bodyY, previewW, bodyH);
        Rect stage = new Rect(x + INSET, bodyY + 29, previewW - INSET * 2, bodyH - 60);
        Rect content = new Rect(preview.right() + GAP, bodyY, w - previewW - GAP, bodyH);
        return new Frame(header, preview, stage, content);
    }

    public static Collection collection(Frame frame, boolean coloursOpen) {
        Rect inner = frame.content().inset(INSET);
        Rect toolbar = new Rect(inner.x(), inner.y(), inner.w(), 22);
        int colourHeight = coloursOpen ? 46 : 0;
        int equippedY = inner.bottom() - 32 - colourHeight;
        Rect equipped = new Rect(inner.x(), equippedY, inner.w(), 32);
        Rect colour = new Rect(inner.x(), equipped.bottom(), inner.w(), colourHeight);
        int gridY = toolbar.bottom() + GAP;
        Rect grid = new Rect(inner.x(), gridY, inner.w(), equipped.y() - GAP - gridY);
        return new Collection(toolbar, grid, equipped, colour);
    }

    public static Gallery gallery(Frame frame, boolean settingsOpen) {
        Rect inner = frame.content().inset(INSET);
        Rect account = new Rect(inner.x(), inner.y(), inner.w(), 22);
        int connectionHeight = settingsOpen ? 22 : 0;
        Rect connection = new Rect(inner.x(), account.bottom() + 6, inner.w(), connectionHeight);
        int filtersY = settingsOpen ? connection.bottom() + 6 : connection.y();
        Rect filters = new Rect(inner.x(), filtersY, inner.w(), 22);
        Rect actions = new Rect(inner.x(), inner.bottom() - 22, inner.w(), 22);
        int gridY = filters.bottom() + GAP;
        Rect grid = new Rect(inner.x(), gridY, inner.w(), actions.y() - GAP - gridY);
        return new Gallery(account, connection, filters, grid, actions);
    }

    public static Settings settings(Frame frame) {
        Rect inner = frame.content().inset(INSET);
        Rect hideOthers = new Rect(inner.x(), inner.y() + 20, inner.w(), 38);
        Rect skinLayer = new Rect(inner.x(), hideOthers.bottom() + GAP, inner.w(), 38);
        Rect skinDistance = new Rect(inner.x(), skinLayer.bottom() + 4, inner.w(), 34);
        return new Settings(hideOthers, skinLayer, skinDistance);
    }
}
