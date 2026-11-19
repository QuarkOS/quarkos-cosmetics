package dev.quarkos.cosmetics.config.ui;

import dev.quarkos.cosmetics.config.WardrobeLayout.Rect;
//? if >=26.3 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

import java.util.List;

public final class CardGrid {
    private final GridMetrics metrics;
    private final List<FlatButton> cards;
    private int row;
    private double grabOffset;

    public CardGrid(GridMetrics metrics, List<FlatButton> cards, int row) {
        this.metrics = metrics;
        this.cards = List.copyOf(cards);
        place(row);
    }

    public int row() {
        return row;
    }

    public boolean wheel(double x, double y, double vertical) {
        if (metrics.maxScroll() == 0 || !metrics.viewport().contains(x, y) || vertical == 0) {
            return false;
        }
        int steps = (int) Math.max(1, Math.round(Math.abs(vertical)));
        place(row + (vertical > 0 ? -steps : steps));
        return true;
    }

    public boolean page(int direction) {
        if (metrics.maxScroll() == 0) {
            return false;
        }
        place(row + direction * metrics.pageRows());
        return true;
    }

    public boolean nextPage() {
        return row < metrics.maxScroll() && page(1);
    }

    public boolean grab(double x, double y) {
        Rect track = metrics.scrollbar();
        Rect hit = new Rect(track.x() - 3, track.y(), track.w() + 3, track.h());
        if (metrics.maxScroll() == 0 || !hit.contains(x, y)) {
            return false;
        }
        Rect thumb = metrics.thumb(row);
        grabOffset = y >= thumb.y() && y < thumb.bottom() ? y - thumb.y() : thumb.h() / 2.0;
        drag(y);
        return true;
    }

    public void drag(double mouseY) {
        place(metrics.rowAt(mouseY - grabOffset));
    }

    //? if >=26.3 {
    /*public void drawScrollbar(GuiGraphicsExtractor graphics) {
    *///?} else {
    public void drawScrollbar(GuiGraphics graphics) {
    //?}
        if (metrics.maxScroll() > 0) {
            UiTheme.fill(graphics, metrics.scrollbar(), UiTheme.STAGE);
            UiTheme.tile(graphics, metrics.thumb(row), UiTheme.MUTED);
        }
    }

    private void place(int nextRow) {
        row = metrics.clamp(nextRow);
        int first = metrics.first(row);
        int end = metrics.end(row);
        for (int i = 0; i < cards.size(); i++) {
            FlatButton button = cards.get(i);
            button.visible = i >= first && i < end;
            button.active = button.visible;
            if (button.visible) {
                Rect bounds = metrics.card(i, row);
                button.setX(bounds.x());
                button.setY(bounds.y());
                button.setWidth(bounds.w());
            }
        }
    }
}
