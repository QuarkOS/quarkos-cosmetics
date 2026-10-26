package dev.quarkos.cosmetics.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class CosmeticsScreen extends Screen {
    public static boolean wings = true;
    public static boolean hat = false;
    public static boolean ears = false;
    public static boolean tail = false;
    public static boolean leftShoulder = false;
    public static boolean rightShoulder = false;
    public static boolean hideOthers = false;
    public static boolean skinLayers = true;

    private final Screen parent;

    public CosmeticsScreen(Screen parent) {
        super(Component.translatable("screen.quarkos_cosmetics.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 110;
        int y = 36;
        addRenderableWidget(slot(x, y, "Back", wings ? "Kite wings" : "Empty", () -> wings = !wings));
        addRenderableWidget(slot(x, y + 22, "Hat", hat ? "Lantern cap" : "Empty", () -> hat = !hat));
        addRenderableWidget(slot(x, y + 44, "Ears", ears ? "Fox ears" : "Empty", () -> ears = !ears));
        addRenderableWidget(slot(x, y + 66, "Tail", tail ? "Ribbon tail" : "Empty", () -> tail = !tail));
        addRenderableWidget(slot(x, y + 88, "Left shoulder", leftShoulder ? "Ink wren" : "Empty", () -> leftShoulder = !leftShoulder));
        addRenderableWidget(slot(x, y + 110, "Right shoulder", rightShoulder ? "Pebble moth" : "Empty", () -> rightShoulder = !rightShoulder));
        addRenderableWidget(toggle(x, y + 136, hideOthers ? "Other players: hidden" : "Other players: shown", () -> hideOthers = !hideOthers));
        addRenderableWidget(toggle(x, y + 158, skinLayers ? "3D skin layer: on" : "3D skin layer: off", () -> skinLayers = !skinLayers));
        addRenderableWidget(Button.builder(Component.translatable("text.quarkos_cosmetics.done"), button -> onClose())
                .bounds(x, this.height - 28, 220, 20)
                .build());
    }

    private Button slot(int x, int y, String slot, String equipped, Runnable toggle) {
        return Button.builder(Component.literal(slot + ": " + equipped), button -> {
            toggle.run();
            rebuildWidgets();
        }).bounds(x, y, 220, 20).build();
    }

    private Button toggle(int x, int y, String label, Runnable toggle) {
        return Button.builder(Component.literal(label), button -> {
            toggle.run();
            rebuildWidgets();
        }).bounds(x, y, 220, 20).build();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, "Local equip. Gallery stays on our own server.", this.width / 2, this.height - 44, 0xFFC8C0B0);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
