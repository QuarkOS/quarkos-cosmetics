package dev.quarkos.cosmetics.config.ui;

import dev.quarkos.cosmetics.config.WardrobeLayout.Rect;
import dev.quarkos.cosmetics.render.ModelLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
//? if >=1.21.11 {
import net.minecraft.client.renderer.RenderPipelines;
//?}

/** Catalog portraits first, cached gallery textures when a model is already available. */
public record Thumbnail(Identifier texture, int width, int height) {
    public static Thumbnail of(ModelLibrary.Resolved model) {
        if (model == null) {
            return null;
        }
        if (model.id().startsWith("quarkos:")) {
            Identifier preview = Identifier.fromNamespaceAndPath("quarkos_cosmetics",
                    "textures/ui/" + model.id().substring("quarkos:".length()) + ".png");
            if (Minecraft.getInstance().getResourceManager().getResource(preview).isPresent()) {
                return new Thumbnail(preview, 32, 32);
            }
        }
        return new Thumbnail(model.texture(), model.width(), model.height());
    }

    public void draw(GuiGraphics graphics, Rect box) {
        float scale = Math.min((float) box.w() / width, (float) box.h() / height);
        int w = Math.max(1, Math.round(width * scale));
        int h = Math.max(1, Math.round(height * scale));
        int x = box.x() + (box.w() - w) / 2;
        int y = box.y() + (box.h() - h) / 2;
        //? if >=1.21.11 {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, w, h, width, height, width, height);
        //?} else {
        /*graphics.blit(texture, x, y, w, h, 0f, 0f, width, height, width, height);
        *///?}
    }
}
