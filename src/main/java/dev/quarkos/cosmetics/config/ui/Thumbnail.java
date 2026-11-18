package dev.quarkos.cosmetics.config.ui;

import dev.quarkos.cosmetics.config.WardrobeLayout.Rect;
import dev.quarkos.cosmetics.render.ModelLibrary;
import dev.quarkos.cosmetics.render.ThumbnailMesh;
import net.minecraft.client.Minecraft;
//? if >=26.3 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}
import net.minecraft.resources.Identifier;
//? if >=1.21.11 {
import net.minecraft.client.renderer.RenderPipelines;
//?} else {
/*import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
*///?}

/** Card art uses the cosmetic mesh when the GUI renderer can display it. */
public record Thumbnail(ModelLibrary.Resolved model, Identifier portrait) {
    private static final int PORTRAIT_SIZE = 512;

    public static Thumbnail of(ModelLibrary.Resolved model) {
        if (model == null) {
            return null;
        }
        if (model.id().startsWith("quarkos:")) {
            Identifier preview = Identifier.fromNamespaceAndPath("quarkos_cosmetics",
                    "textures/ui/" + model.id().substring("quarkos:".length()) + ".png");
            if (Minecraft.getInstance().getResourceManager().getResource(preview).isPresent()) {
                return new Thumbnail(model, preview);
            }
        }
        return new Thumbnail(model, null);
    }

    //? if >=26.3 {
    /*public boolean draw(GuiGraphicsExtractor graphics, Rect box) {
    *///?} else {
    public boolean draw(GuiGraphics graphics, Rect box) {
    //?}
        if (box.w() <= 0 || box.h() <= 0) {
            return false;
        }
        //? if >=1.21.11 {
        if (ThumbnailRenderer.available()) {
            ThumbnailMesh mesh = ThumbnailMesh.of(model);
            if (mesh != null) {
                ThumbnailRenderer.submit(graphics, mesh, box);
                return true;
            }
        }
        //?} else {
        /*// Gallery models only have a mesh and a UV atlas. Draw those inside the card.
        if (portrait == null) {
            ThumbnailMesh mesh = ThumbnailMesh.of(model);
            if (mesh != null) {
                drawImmediate(graphics, box, mesh);
                return true;
            }
        }
        *///?}
        if (portrait == null) {
            return false;
        }
        int size = Math.min(box.w(), box.h());
        int x = box.x() + (box.w() - size) / 2;
        int y = box.y() + (box.h() - size) / 2;
        // The portrait textures use Minecraft's default nearest sampling.
        //? if >=1.21.11 {
        graphics.blit(RenderPipelines.GUI_TEXTURED, portrait, x, y, 0f, 0f, size, size,
                PORTRAIT_SIZE, PORTRAIT_SIZE, PORTRAIT_SIZE, PORTRAIT_SIZE);
        //?} else {
        /*graphics.blit(portrait, x, y, size, size, 0f, 0f, PORTRAIT_SIZE, PORTRAIT_SIZE, PORTRAIT_SIZE, PORTRAIT_SIZE);
        *///?}
        return true;
    }

    //? if <1.21.11 {
    /*private static void drawImmediate(GuiGraphics graphics, Rect box, ThumbnailMesh mesh) {
        PoseStack pose = graphics.pose();
        graphics.enableScissor(box.x(), box.y(), box.right(), box.bottom());
        pose.pushPose();
        try {
            float scale = mesh.scaleToFit(box.w(), box.h());
            pose.translate(box.x() + box.w() / 2f, box.y() + box.h() / 2f, 50f);
            pose.scale(scale, scale, -scale);
            Lighting.setupForEntityInInventory();
            graphics.drawManaged(() -> mesh.render(pose, graphics.bufferSource()));
        } finally {
            pose.popPose();
            Lighting.setupFor3DItems();
            graphics.disableScissor();
        }
    }
    *///?}
}
