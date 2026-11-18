package dev.quarkos.cosmetics;

//? if forge {
import dev.quarkos.cosmetics.render.MaskGlow;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(QuarkosClient.MOD_ID)
public final class ForgeQuarkosCosmetics {
    public ForgeQuarkosCosmetics() {
        MaskGlow.boot();
        //? if >=1.21.11 {
        net.minecraftforge.client.event.RegisterPictureInPictureRendererEvent.BUS.addListener(event ->
                event.register(new dev.quarkos.cosmetics.config.ui.ThumbnailRenderer.Forge(event.getBufferSource())));
        // The entity batch never puts the candle in the main color target. Draw it after the world.
        dev.quarkos.cosmetics.render.JackOLanternMask.useDeferredGlow();
        dev.quarkos.cosmetics.harness.ClientHarness.shotAtFrameEnd();
        net.minecraftforge.client.event.AddFramePassEvent.BUS.addListener(event -> event.addPass(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(QuarkosClient.MOD_ID, "mask_glow"),
                new net.minecraftforge.client.FramePassManager.PassDefinition() {
                    @Override
                    public void extracts(net.minecraft.client.renderer.LevelTargetBundle bundle,
                                          com.mojang.blaze3d.framegraph.FramePass pass,
                                          net.minecraft.client.DeltaTracker deltaTracker) {
                        bundle.main = pass.readsAndWrites(bundle.main);
                    }

                    @Override
                    public void executes(net.minecraft.client.renderer.state.LevelRenderState state) {
                        dev.quarkos.cosmetics.render.JackOLanternMask.flush();
                        dev.quarkos.cosmetics.harness.ClientHarness.capturePending();
                    }
                }));
        RegisterKeyMappingsEvent.BUS.addListener(event -> event.register(QuarkosClient.OPEN));
        //?} else {
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(
                (RegisterKeyMappingsEvent event) -> event.register(QuarkosClient.OPEN));
        //?}
    }
}
//?}
