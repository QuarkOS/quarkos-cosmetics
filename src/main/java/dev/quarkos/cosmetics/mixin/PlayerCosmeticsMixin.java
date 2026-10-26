package dev.quarkos.cosmetics.mixin;

import dev.quarkos.cosmetics.render.CosmeticLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class PlayerCosmeticsMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void quarkos$layer(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        //? if >=1.21.11 {
        ((CosmeticLayerAccess) this).quarkos$addLayer(new CosmeticLayer((AvatarRenderer<?>) (Object) this));
        //?} else {
        ((CosmeticLayerAccess) this).quarkos$addLayer(new CosmeticLayer((net.minecraft.client.renderer.entity.player.PlayerRenderer) (Object) this));
        //?}
    }
}
