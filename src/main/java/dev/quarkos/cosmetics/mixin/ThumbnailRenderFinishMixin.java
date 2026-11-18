package dev.quarkos.cosmetics.mixin;

//? if >=1.21.11 && forge {
/*import dev.quarkos.cosmetics.config.ui.ThumbnailRenderer;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(GuiRenderer.class)
public abstract class ThumbnailRenderFinishMixin {
    @Shadow @Final
    private Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> pictureInPictureRenderers;

    @Inject(method = "incrementFrameNumber", at = @At("TAIL"))
    private void quarkos$releaseUnusedCardTextures(CallbackInfo callback) {
        if (pictureInPictureRenderers.get(ThumbnailRenderer.State.class) instanceof ThumbnailRenderer.Forge renderer) {
            renderer.endFrame();
        }
    }
}
*///?} else {
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Minecraft.class)
public abstract class ThumbnailRenderFinishMixin {
}
//?}
