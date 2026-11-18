package dev.quarkos.cosmetics.mixin;

import dev.quarkos.cosmetics.harness.ClientHarness;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//? if >=1.21.11 {
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
//?} else {
/*import net.minecraft.client.resources.PlayerSkin;
*///?}

@Mixin(AbstractClientPlayer.class)
public abstract class HarnessSkinMixin {
    @Unique
    private static final Identifier QUARKOS_LAYER_TEST_TEXTURE =
            Identifier.fromNamespaceAndPath("quarkos_cosmetics", "textures/test/layer_test_skin.png");
    @Unique
    private static final PlayerSkin QUARKOS_LAYER_TEST_SKIN =
            //? if >=1.21.11 {
            new PlayerSkin(new ClientAsset.ResourceTexture(QUARKOS_LAYER_TEST_TEXTURE, QUARKOS_LAYER_TEST_TEXTURE),
                    null, null, PlayerModelType.WIDE, true);
            //?} else {
            /*new PlayerSkin(QUARKOS_LAYER_TEST_TEXTURE, null, null, null, PlayerSkin.Model.WIDE, true);
            *///?}

    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void quarkos$layerTestSkin(CallbackInfoReturnable<PlayerSkin> callback) {
        if (Boolean.getBoolean("quarkos_cosmetics.harness") && ClientHarness.usesLayerTestSkin()
                && (Object) this == Minecraft.getInstance().player) {
            callback.setReturnValue(QUARKOS_LAYER_TEST_SKIN);
        }
    }
}
