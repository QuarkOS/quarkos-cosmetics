package dev.quarkos.cosmetics.render;

/** Optional mods that change which slots this client draws. */
public final class Mods {
    private Mods() {
    }

    public static boolean tailsLoaded() {
        //? if fabric {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("tails");
        //?} else {
        return net.minecraftforge.fml.ModList.get().isLoaded("tails");
        //?}
    }
}
