package dev.quarkos.cosmetics.config.ui;

import dev.quarkos.cosmetics.config.WardrobeLayout.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Uses vanilla focus, keyboard activation and narration with custom drawing. */
public final class FlatButton extends Button {
    @FunctionalInterface
    public interface Painter {
        void paint(FlatButton button, GuiGraphics graphics, Font font, Rect bounds, boolean hovered);
    }

    private final Painter painter;

    public FlatButton(Rect bounds, Component label, OnPress onPress) {
        this(bounds, label, onPress, FlatButton::paintDefault);
    }

    public FlatButton(Rect bounds, Component label, OnPress onPress, Painter painter) {
        super(bounds.x(), bounds.y(), bounds.w(), bounds.h(), label, onPress, DEFAULT_NARRATION);
        this.painter = painter;
    }

    private static void paintDefault(FlatButton button, GuiGraphics graphics, Font font, Rect bounds, boolean hovered) {
        UiTheme.frame(graphics, bounds, button.fill(hovered), button.border());
        UiTheme.centered(graphics, font, button.getMessage().getString(), bounds.inset(4),
                button.active ? UiTheme.TEXT : UiTheme.DISABLED);
    }

    public int border() {
        return active && isFocused() ? UiTheme.ACCENT : UiTheme.BORDER;
    }

    public int fill(boolean hovered) {
        return !active ? UiTheme.PANEL : hovered ? UiTheme.HOVER : UiTheme.CONTROL;
    }

    //? if >=1.21.11 {
    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        paint(graphics, mouseX, mouseY);
    }
    //?} else {
    /*@Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        paint(graphics, mouseX, mouseY);
    }
    *///?}

    private void paint(GuiGraphics graphics, int mouseX, int mouseY) {
        Rect bounds = new Rect(getX(), getY(), getWidth(), getHeight());
        painter.paint(this, graphics, Minecraft.getInstance().font, bounds, active && bounds.contains(mouseX, mouseY));
    }
}
