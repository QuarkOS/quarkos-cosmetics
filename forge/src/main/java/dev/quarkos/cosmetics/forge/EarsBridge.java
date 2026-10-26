package dev.quarkos.cosmetics.forge;

import com.unascribed.ears.api.EarsFeatureType;
import com.unascribed.ears.api.registry.EarsInhibitorRegistry;
import dev.quarkos.cosmetics.config.CosmeticsScreen;

final class EarsBridge {
    private EarsBridge() {
    }

    static void register() {
        EarsInhibitorRegistry.register("quarkos_cosmetics", (type, peer) -> {
            if (type == EarsFeatureType.EARS) {
                return CosmeticsScreen.ears;
            }
            if (type == EarsFeatureType.TAIL) {
                return CosmeticsScreen.tail;
            }
            if (type == EarsFeatureType.WINGS) {
                return CosmeticsScreen.wings;
            }
            return false;
        });
    }
}
