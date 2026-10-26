package dev.quarkos.cosmetics.forge.gallery;

import dev.quarkos.cosmetics.config.CosmeticsScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class GallerySeenScreen extends Screen {
    private final Screen parent;

    public GallerySeenScreen(Screen parent) {
        super(Component.literal("Gallery"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        EquipmentView view = GalleryCache.view();
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);
        if (view == null) {
            graphics.drawCenteredString(this.font, "Waiting for the other client.", this.width / 2, 48, 0xFFF2E7C9);
            return;
        }
        CosmeticModel hat = view.worn("hat");
        String worn = hat == null ? "nothing" : hat.name() + " on hat";
        graphics.drawCenteredString(this.font, view.playerName() + " is wearing " + worn, this.width / 2, 48, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, view.playerUuid(), this.width / 2, 64, 0xFFC8C0B0);
        graphics.drawCenteredString(this.font, "Read from GET /v1/equipment", this.width / 2, 84, 0xFFC8C0B0);
        graphics.drawCenteredString(this.font, GalleryClient.configuredBase().toString(), this.width / 2, 100, 0xFF8FD0C8);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
