package dev.quarkos.cosmetics.config.ui;

import dev.quarkos.cosmetics.config.WardrobeLayout.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Keeps Minecraft's text editing, clipboard support and narration. */
public final class FlatEditBox extends EditBox {
    private static final int PAD = 6;
    private final Rect frame;

    public FlatEditBox(Font font, Rect bounds, Component label) {
        super(font, bounds.x() + PAD, bounds.y() + (bounds.h() - font.lineHeight) / 2,
                bounds.w() - 2 * PAD, bounds.h() - (bounds.h() - font.lineHeight) / 2, label);
        frame = bounds;
        setBordered(false);
        setTextColor(UiTheme.TEXT);
        setTextColorUneditable(UiTheme.DISABLED);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return visible && frame.contains(mouseX, mouseY);
    }

    //? if <1.21.11 {
    /*@Override
    protected boolean clicked(double mouseX, double mouseY) {
        return active && isMouseOver(mouseX, mouseY);
    }
    *///?}

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int fill = isMouseOver(mouseX, mouseY) ? UiTheme.CONTROL : UiTheme.STAGE;
        UiTheme.frame(graphics, frame, fill, isFocused() ? UiTheme.ACCENT : UiTheme.BORDER);
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
    }
}
