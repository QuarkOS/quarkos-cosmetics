package dev.quarkos.cosmetics.config.ui;

import dev.quarkos.cosmetics.config.WardrobeLayout.Rect;
//? if >=26.3 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

//? if >=1.21.11 {
import dev.quarkos.cosmetics.mixin.InventoryPreviewAccess;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
//?} else {
/*import net.minecraft.client.gui.screens.inventory.InventoryScreen;
*///?}

public final class PlayerPreview {
    private static final float START_YAW = -22f;
    private static final float START_PITCH = 0.06f;
    private final boolean studio;
    private final boolean legacy;
    private float yaw;
    private float pitch;
    private long lastFrame;
    private long resumeAt;
    private boolean dragging;

    public PlayerPreview(boolean studio, boolean legacy) {
        this.studio = studio;
        this.legacy = legacy;
        reset();
    }

    public void reset() {
        yaw = legacy ? 158f : START_YAW;
        pitch = START_PITCH;
        resumeAt = System.nanoTime() + 1_500_000_000L;
    }

    /** Studio captures skip idle rotation, so this angle stays fixed for the shot. */
    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public void beginDrag() {
        dragging = true;
    }

    public void drag(double dx, double dy) {
        yaw = (yaw + (float) dx * 1.6f) % 360f;
        pitch = Math.clamp(pitch + (float) dy * 0.007f, -0.32f, 0.32f);
    }

    public void endDrag() {
        dragging = false;
        resumeAt = System.nanoTime() + 1_500_000_000L;
    }

    //? if >=26.3 {
    /*public void draw(GuiGraphicsExtractor graphics, Rect stage, LivingEntity player) {
    *///?} else {
    public void draw(GuiGraphics graphics, Rect stage, LivingEntity player) {
    //?}
        long now = System.nanoTime();
        if (!studio && !dragging && now >= resumeAt && lastFrame != 0) {
            float seconds = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
            yaw = (yaw + seconds * 12f) % 360f;
        }
        lastFrame = now;

        int scale = Math.max(12, Math.min(stage.w() * 2 / 3, Math.round(stage.h() * 0.35f)));
        Quaternionf camera = new Quaternionf().rotateX(pitch);
        //? if >=1.21.11 {
        // Keep the inventory preview clear of a leftover GUI scissor in either loader.
        for (int guard = 0; guard < 8 && previewScissorOpen(graphics); guard++) {
            graphics.disableScissor();
        }
        var state = InventoryPreviewAccess.quarkos$extract(player);
        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = 180f + yaw;
            living.yRot = legacy ? yaw : 0f;
            living.xRot = 0f;
            float entityScale = living.scale == 0f ? 1f : living.scale;
            living.boundingBoxWidth /= entityScale;
            living.boundingBoxHeight /= entityScale;
            living.scale = 1f;
            living.shadowRadius = 0f;
        }
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI).mul(camera);
        Vector3f translation = new Vector3f(0f, state.boundingBoxHeight / 2f + 0.0625f, 0f);
        //? if >=26.3 {
        /*graphics.entity(state, scale, translation, pose, camera, stage.x(), stage.y(), stage.right(), stage.bottom());
        *///?} else {
        graphics.submitEntityRenderState(state, scale, translation, pose, camera, stage.x(), stage.y(), stage.right(), stage.bottom());
        //?}
        //?} else {
        /*Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI).mul(camera).rotateY((float) Math.toRadians(legacy ? yaw : -yaw));
        Vector3f translation = new Vector3f(0f, player.getBbHeight() / 2f + 0.0625f, 0f);
        float x = stage.x() + stage.w() / 2f;
        float y = stage.y() + stage.h() / 2f;
        float body = player.yBodyRot;
        float head = player.yHeadRot;
        float oldBody = player.yBodyRotO;
        float oldHead = player.yHeadRotO;
        float playerPitch = player.getXRot();
        float oldPitch = player.xRotO;
        graphics.enableScissor(stage.x(), stage.y(), stage.right(), stage.bottom());
        try {
            if (!legacy) {
                player.yBodyRot = player.yBodyRotO = 180f;
                player.yHeadRot = player.yHeadRotO = 180f;
                player.setXRot(0f);
                player.xRotO = 0f;
            }
            InventoryScreen.renderEntityInInventory(graphics, x, y, scale, translation, pose, camera, player);
        } finally {
            player.yBodyRot = body;
            player.yHeadRot = head;
            player.yBodyRotO = oldBody;
            player.yHeadRotO = oldHead;
            player.setXRot(playerPitch);
            player.xRotO = oldPitch;
            graphics.disableScissor();
        }
        *///?}
    }

    //? if >=1.21.11 {
    //? if >=26.3 {
    /*private static boolean previewScissorOpen(GuiGraphicsExtractor graphics) {
    *///?} else {
    private static boolean previewScissorOpen(GuiGraphics graphics) {
    //?}
        //? if forge {
        /*return graphics.getScissorStack().peek() != null;
        *///?} else {
        return graphics.scissorStack.peek() != null;
        //?}
    }
    //?}
}
