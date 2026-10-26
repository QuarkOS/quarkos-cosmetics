package dev.quarkos.cosmetics;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod("quarkos_cosmetics")
public final class ForgeQuarkosCosmetics {
    public ForgeQuarkosCosmetics() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            dev.quarkos.cosmetics.forge.ForgeClient.start();
        }
    }
}
