package dev.quarkos.cosmetics.mixin;

import dev.quarkos.cosmetics.QuarkosClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ClientTickMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void quarkos$tick(CallbackInfo ci) {
        QuarkosClient.clientTick((Minecraft) (Object) this);
    }
}
