package dev.quarkos.cosmetics.forge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.quarkos.cosmetics.render.WingLayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WingLayer.class)
public class WingElytraMixin {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void quarkos$hideBackWhileElytra(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state,
                                             float yRot, float xRot, CallbackInfo ci) {
        if (state.isFallFlying || state.chestEquipment.is(Items.ELYTRA)) {
            ci.cancel();
        }
    }
}
