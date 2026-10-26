package dev.quarkos.cosmetics.mixin;

import dev.quarkos.cosmetics.config.WardrobeScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void quarkos$button(CallbackInfo ci) {
        this.addRenderableWidget(Button.builder(Component.translatable("text.quarkos_cosmetics.pause"), button ->
                this.minecraft.setScreen(new WardrobeScreen(this))).bounds(this.width / 2 + 104, 8, 90, 20).build());
    }
}
