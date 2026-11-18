package dev.quarkos.cosmetics.config.ui;

import dev.quarkos.cosmetics.config.WardrobeLayout.Rect;
import dev.quarkos.cosmetics.model.Slot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class UiTheme {
    public static final int BACKGROUND = 0xFF242424;
    public static final int PANEL = 0xFF2C2C2C;
    public static final int STAGE = 0xFF272727;
    public static final int CONTROL = 0xFF353535;
    public static final int HOVER = 0xFF3E3E3E;
    public static final int BORDER = 0xFF444444;
    public static final int TEXT = 0xFFEDEDED;
    public static final int MUTED = 0xFFA8A8A8;
    public static final int DISABLED = 0xFF929292;
    public static final int ACCENT = 0xFF4C9AFF;
    private static final int ACCENT_INK = 0xFF142130;

    private UiTheme() {
    }

    public static void fill(GuiGraphics graphics, Rect rect, int colour) {
        if (rect.w() > 0 && rect.h() > 0) {
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), colour);
        }
    }

    /** Trim one pixel from each corner. */
    public static void tile(GuiGraphics graphics, Rect rect, int colour) {
        fill(graphics, new Rect(rect.x() + 1, rect.y(), rect.w() - 2, rect.h()), colour);
        fill(graphics, new Rect(rect.x(), rect.y() + 1, 1, rect.h() - 2), colour);
        fill(graphics, new Rect(rect.right() - 1, rect.y() + 1, 1, rect.h() - 2), colour);
    }

    public static void frame(GuiGraphics graphics, Rect rect, int background, int border) {
        tile(graphics, rect, border);
        fill(graphics, rect.inset(1), background);
    }

    public static void text(GuiGraphics graphics, Font font, String label, Rect rect, int colour) {
        String line = TextFit.ellipsis(label, rect.w(), font::width);
        graphics.drawString(font, line, rect.x(), rect.y() + (rect.h() - font.lineHeight) / 2, colour, false);
    }

    public static void centered(GuiGraphics graphics, Font font, String label, Rect rect, int colour) {
        String line = TextFit.ellipsis(label, rect.w(), font::width);
        graphics.drawString(font, line, rect.x() + (rect.w() - font.width(line)) / 2,
                rect.y() + (rect.h() - font.lineHeight) / 2, colour, false);
    }

    public static void slotIcon(GuiGraphics graphics, Slot slot, int x, int y, int colour) {
        if (slot == Slot.EARS) {
            graphics.fill(x + 1, y + 1, x + 3, y + 7, colour);
            graphics.fill(x + 3, y + 3, x + 5, y + 8, colour);
            graphics.fill(x + 9, y + 1, x + 11, y + 7, colour);
            graphics.fill(x + 7, y + 3, x + 9, y + 8, colour);
            graphics.fill(x + 4, y + 7, x + 8, y + 9, colour);
        } else {
            graphics.fill(x + 3, y + 2, x + 9, y + 7, colour);
            graphics.fill(x + 2, y + 4, x + 10, y + 7, colour);
            graphics.fill(x, y + 8, x + 12, y + 10, colour);
        }
    }

    public static void check(GuiGraphics graphics, int x, int y) {
        tile(graphics, new Rect(x, y, 12, 12), ACCENT);
        graphics.fill(x + 2, y + 5, x + 4, y + 7, ACCENT_INK);
        graphics.fill(x + 4, y + 7, x + 6, y + 9, ACCENT_INK);
        graphics.fill(x + 6, y + 5, x + 8, y + 7, ACCENT_INK);
        graphics.fill(x + 8, y + 3, x + 10, y + 5, ACCENT_INK);
    }

    public static void emptyIcon(GuiGraphics graphics, int x, int y) {
        frame(graphics, new Rect(x, y, 22, 22), STAGE, BORDER);
        graphics.fill(x + 6, y + 10, x + 16, y + 12, MUTED);
    }
}
