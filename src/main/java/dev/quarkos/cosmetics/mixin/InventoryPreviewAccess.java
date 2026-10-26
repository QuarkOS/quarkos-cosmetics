package dev.quarkos.cosmetics.mixin;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the wardrobe spin the preview a full turn. The public helper only wiggles about thirty degrees. */
@Mixin(InventoryScreen.class)
public interface InventoryPreviewAccess {
    //? if >=1.21.11 {
    @Invoker("extractRenderState")
    static net.minecraft.client.renderer.entity.state.EntityRenderState quarkos$extract(LivingEntity entity) {
        throw new AssertionError();
    }
    //?}
}
