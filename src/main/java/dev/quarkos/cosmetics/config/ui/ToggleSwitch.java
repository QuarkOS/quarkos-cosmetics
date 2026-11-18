package dev.quarkos.cosmetics.config.ui;

import dev.quarkos.cosmetics.config.WardrobeLayout.Rect;
//? if >=26.3 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class ToggleSwitch {
    private ToggleSwitch() {
    }

    public static FlatButton create(Rect bounds, String label, BooleanSupplier on, Consumer<Boolean> change) {
        return new FlatButton(bounds, narration(label, on.getAsBoolean()), button -> {
            change.accept(!on.getAsBoolean());
            button.setMessage(narration(label, on.getAsBoolean()));
        }, (button, graphics, font, rect, hovered) -> {
            if (hovered || button.isFocused()) {
                UiTheme.frame(graphics, rect, button.fill(hovered), button.border());
            }
            int x = rect.right() - 31;
            int y = rect.y() + (rect.h() - 12) / 2;
            boolean checked = button.active && on.getAsBoolean();
            pill(graphics, x, y, 26, checked ? UiTheme.ACCENT : UiTheme.BORDER);
            int knob = checked ? x + 15 : x + 3;
            UiTheme.tile(graphics, new Rect(knob, y + 2, 8, 8), button.active ? UiTheme.TEXT : UiTheme.DISABLED);
            UiTheme.text(graphics, font, label, new Rect(rect.x() + 5, rect.y(), rect.w() - 40, rect.h()),
                    button.active ? UiTheme.TEXT : UiTheme.DISABLED);
        });
    }

    private static Component narration(String label, boolean on) {
        return Component.literal(label + (on ? ", on" : ", off"));
    }

    //? if >=26.3 {
    /*private static void pill(GuiGraphicsExtractor graphics, int x, int y, int width, int colour) {
    *///?} else {
    private static void pill(GuiGraphics graphics, int x, int y, int width, int colour) {
    //?}
        graphics.fill(x + 4, y, x + width - 4, y + 12, colour);
        graphics.fill(x + 2, y + 1, x + width - 2, y + 11, colour);
        graphics.fill(x, y + 3, x + width, y + 9, colour);
    }
}
