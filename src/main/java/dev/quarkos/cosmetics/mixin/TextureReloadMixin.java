package dev.quarkos.cosmetics.mixin;

import dev.quarkos.cosmetics.render.JackOLanternMask;
import dev.quarkos.cosmetics.render.ModelLibrary;
import dev.quarkos.cosmetics.render.SkinShellRenderer;
import dev.quarkos.cosmetics.render.ThumbnailMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(TextureManager.class)
public abstract class TextureReloadMixin {
    @Inject(method = "reload", at = @At("RETURN"))
    private void quarkos$invalidateGeometry(CallbackInfoReturnable<CompletableFuture<Void>> callback) {
        callback.getReturnValue().thenRunAsync(() -> {
            SkinShellRenderer.clearCache();
            JackOLanternMask.clearCache();
            ModelLibrary.clearPacked();
            ThumbnailMesh.clearCache();
        }, Minecraft.getInstance());
    }
}
