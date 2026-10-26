package dev.quarkos.cosmetics;

import dev.quarkos.cosmetics.config.ClientSettings;
import dev.quarkos.cosmetics.model.Slot;

import java.lang.reflect.Proxy;

/** Hides Ears features that occupy a slot this mod is already drawing. Ears stays an optional compile-only API. */
public final class EarsBridge {
    private static boolean installed;

    private EarsBridge() {
    }

    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        try {
            Class<?> inhibitor = Class.forName("com.unascribed.ears.api.iface.EarsInhibitor");
            Class<?> registry = Class.forName("com.unascribed.ears.api.registry.EarsInhibitorRegistry");
            Object proxy = Proxy.newProxyInstance(inhibitor.getClassLoader(), new Class<?>[]{inhibitor}, (unused, method, args) -> {
                if ("shouldInhibit".equals(method.getName()) && args != null && args.length > 0 && args[0] != null) {
                    return inhibit(args[0].toString());
                }
                return false;
            });
            registry.getMethod("register", String.class, inhibitor).invoke(null, "quarkos_cosmetics", proxy);
        } catch (Throwable ignored) {
            // Ears is not installed. The wardrobe still works.
        }
    }

    private static boolean inhibit(String feature) {
        ClientSettings settings = ClientSettings.get();
        String name = feature.toUpperCase(java.util.Locale.ROOT);
        if (name.contains("EARS")) {
            return settings.shown(Slot.EARS, settings.equippedId(Slot.EARS));
        }
        if (name.contains("TAIL")) {
            return settings.shown(Slot.TAIL, settings.equippedId(Slot.TAIL));
        }
        if (name.contains("WINGS")) {
            return settings.shown(Slot.BACK, settings.equippedId(Slot.BACK));
        }
        return false;
    }
}
