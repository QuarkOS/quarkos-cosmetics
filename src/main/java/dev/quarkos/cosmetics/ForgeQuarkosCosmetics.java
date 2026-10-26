package dev.quarkos.cosmetics;

//? if forge {
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(QuarkosClient.MOD_ID)
public final class ForgeQuarkosCosmetics {
    public ForgeQuarkosCosmetics() {
        //? if >=1.21.11 {
        RegisterKeyMappingsEvent.BUS.addListener(event -> event.register(QuarkosClient.OPEN));
        //?} else {
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(
                (RegisterKeyMappingsEvent event) -> event.register(QuarkosClient.OPEN));
        //?}
    }
}
//?}
