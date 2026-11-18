package dev.quarkos.cosmetics.mixin;

import org.spongepowered.asm.mixin.Mixin;

//? if >=1.21.11 {
import dev.quarkos.cosmetics.config.ui.PlayerPreview;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public abstract class WardrobePreviewStateMixin implements PlayerPreview.WardrobeState {
    @Unique
    private boolean quarkos$wardrobePreview;

    @Override
    public void quarkos$markWardrobePreview() {
        quarkos$wardrobePreview = true;
    }

    @Override
    public boolean quarkos$isWardrobePreview() {
        return quarkos$wardrobePreview;
    }
}
//?} else {
/*@Mixin(net.minecraft.client.Minecraft.class)
public abstract class WardrobePreviewStateMixin {
}
*///?}
