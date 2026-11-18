package dev.quarkos.cosmetics.config.ui;

import dev.quarkos.cosmetics.config.TintColor;
import dev.quarkos.cosmetics.config.WardrobeLayout.Rect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class ColourPicker {
    private record Swatch(String name, int rgb) {
    }

    private static final Swatch[] PRESETS = {
            new Swatch("Coral", 0xFF8581), new Swatch("Peach", 0xFFB27A), new Swatch("Gold", 0xFFD76B),
            new Swatch("Lime", 0xBDEA79), new Swatch("Mint", 0x79EBAB), new Swatch("Aqua", 0x79E6DF),
            new Swatch("Sky", 0x7BBFFF), new Swatch("Periwinkle", 0x8997FF), new Swatch("Lilac", 0xCA9EFF),
            new Swatch("Rose", 0xF493C6)
    };

    private final IntSupplier colour;
    private final IntConsumer change;
    private final Runnable save;
    private final Rect track;
    private final FlatButton slider;

    public ColourPicker(Rect rect, IntSupplier colour, IntConsumer change, Runnable save, Consumer<FlatButton> register) {
        this.colour = colour;
        this.change = change;
        this.save = save;
        Rect inner = new Rect(rect.x() + 6, rect.y() + 4, rect.w() - 12, 18);
        register.accept(swatch(new Rect(inner.x(), inner.y(), 54, inner.h()), new Swatch("Default", TintColor.DEFAULT), true));
        int x = inner.x() + 59;
        int width = Math.min(inner.right() - x, PRESETS.length * 24);
        Rect swatches = new Rect(x, inner.y(), width, inner.h());
        for (int i = 0; i < PRESETS.length; i++) {
            register.accept(swatch(swatches.column(i, PRESETS.length, 3), PRESETS[i], false));
        }
        Rect sliderRow = new Rect(inner.x(), inner.bottom() + 3, inner.w(), 17);
        track = new Rect(sliderRow.x() + 31, sliderRow.y() + 5, Math.min(303, sliderRow.w() - 89), 7);
        slider = new FlatButton(sliderRow, Component.literal("Hue"), button -> adjust(1), (button, graphics, font, bounds, hovered) -> {
            if (button.isFocused()) {
                UiTheme.frame(graphics, bounds, UiTheme.PANEL, UiTheme.ACCENT);
            }
            int rgb = colour.getAsInt();
            UiTheme.text(graphics, font, "Hue", new Rect(bounds.x() + 3, bounds.y(), 26, bounds.h()), UiTheme.MUTED);
            UiTheme.text(graphics, font, rgb == TintColor.DEFAULT ? "Default" : TintColor.encode(rgb),
                    new Rect(track.right() + 8, bounds.y(), 49, bounds.h()), UiTheme.MUTED);
            drawHue(graphics, rgb);
        });
        register.accept(slider);
    }

    private FlatButton swatch(Rect rect, Swatch swatch, boolean isDefault) {
        Component label = Component.literal(swatch.name());
        FlatButton button = new FlatButton(rect, label, pressed -> {
            change.accept(swatch.rgb());
            save.run();
        }, (widget, graphics, font, bounds, hovered) -> {
            boolean selected = colour.getAsInt() == swatch.rgb();
            UiTheme.frame(graphics, bounds, widget.fill(hovered), selected ? UiTheme.ACCENT : widget.border());
            if (isDefault) {
                UiTheme.centered(graphics, font, swatch.name(), bounds.inset(2), UiTheme.TEXT);
            } else {
                UiTheme.fill(graphics, bounds.inset(3), 0xFF000000 | swatch.rgb());
            }
        });
        button.setTooltip(Tooltip.create(label));
        return button;
    }

    private void drawHue(GuiGraphics graphics, int rgb) {
        int segments = 60;
        for (int i = 0; i < segments; i++) {
            int start = track.w() * i / segments;
            int end = track.w() * (i + 1) / segments;
            UiTheme.fill(graphics, new Rect(track.x() + start, track.y(), end - start, track.h()),
                    0xFF000000 | TintColor.fromHue(i * 360 / segments));
        }
        if (rgb != TintColor.DEFAULT) {
            int knob = track.x() + TintColor.hue(rgb) * (track.w() - 1) / 359;
            UiTheme.frame(graphics, new Rect(knob - 2, track.y() - 2, 5, track.h() + 4), 0xFF000000 | rgb, UiTheme.TEXT);
        }
    }

    public boolean contains(double mouseX, double mouseY) {
        return new Rect(track.x() - 3, track.y() - 4, track.w() + 6, track.h() + 8).contains(mouseX, mouseY);
    }

    public FlatButton slider() {
        return slider;
    }

    public void drag(double mouseX) {
        double fraction = Math.clamp((mouseX - track.x()) / Math.max(1, track.w() - 1), 0.0, 1.0);
        change.accept(TintColor.fromHue((int) Math.round(fraction * 359)));
    }

    public void adjust(int direction) {
        int current = colour.getAsInt();
        change.accept(TintColor.fromHue(current == TintColor.DEFAULT ? 0 : TintColor.hue(current) + direction * 5));
        save.run();
    }
}
