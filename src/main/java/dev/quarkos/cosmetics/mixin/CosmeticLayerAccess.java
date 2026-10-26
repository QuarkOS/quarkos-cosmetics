package dev.quarkos.cosmetics.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntityRenderer.class)
public interface CosmeticLayerAccess {
    @Invoker("addLayer")
    boolean quarkos$addLayer(RenderLayer<?, ?> layer);
}
