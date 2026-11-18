package dev.quarkos.cosmetics.mixin;

import org.spongepowered.asm.mixin.Mixin;

//? if >=1.21.11 {
import net.minecraft.client.Minecraft;

@Mixin(Minecraft.class)
public abstract class LegacySkinUploadMixin {
}
//?} else {
/*import com.mojang.blaze3d.platform.NativeImage;
import dev.quarkos.cosmetics.render.SkinImages;
import net.minecraft.client.renderer.texture.HttpTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HttpTexture.class)
public abstract class LegacySkinUploadMixin implements SkinImages.LegacyTexture {
    @Shadow @Final
    private boolean processLegacySkin;

    @Unique
    private volatile SkinImages.Pixels quarkos$skin;

    @Inject(method = "upload", at = @At("HEAD"))
    private void quarkos$captureSkin(NativeImage image, CallbackInfo callback) {
        if (processLegacySkin && image.getWidth() == 64 && image.getHeight() == 64) {
            quarkos$skin = SkinImages.from(image);
        }
    }

    @Override
    public SkinImages.Pixels quarkos$skinPixels() {
        return quarkos$skin;
    }
}
*///?}
