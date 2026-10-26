package dev.quarkos.cosmetics.forge;

public final class EarsHook {
    private static boolean tried;

    private EarsHook() {
    }

    public static void register() {
        if (tried) {
            return;
        }
        tried = true;
        try {
            Class.forName("com.unascribed.ears.api.registry.EarsInhibitorRegistry");
            EarsBridge.register();
        } catch (Throwable ignored) {
            // Ears is optional. A missing mod leaves the wardrobe as the only ear toggle.
        }
    }
}
