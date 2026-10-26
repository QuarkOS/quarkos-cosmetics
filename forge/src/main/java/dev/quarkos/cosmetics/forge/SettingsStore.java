package dev.quarkos.cosmetics.forge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.quarkos.cosmetics.config.CosmeticsScreen;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SettingsStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean loaded;
    private static Saved last = Saved.capture();

    private SettingsStore() {
    }

    public static void loadOnce() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            Saved saved = GSON.fromJson(reader, Saved.class);
            if (saved != null) {
                saved.apply();
                last = Saved.capture();
            }
        } catch (IOException ignored) {
            // A broken local file keeps the screen defaults.
        }
    }

    public static void saveIfChanged() {
        Saved current = Saved.capture();
        if (current.equals(last)) {
            return;
        }
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(current, writer);
            }
            last = current;
        } catch (IOException ignored) {
            // The wardrobe still works if the config folder is not writable.
        }
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("quarkos_cosmetics.json");
    }

    private static final class Saved {
        boolean wings = true;
        boolean hat;
        boolean ears;
        boolean tail;
        boolean leftShoulder;
        boolean rightShoulder;
        boolean hideOthers;
        boolean skinLayers = true;

        static Saved capture() {
            Saved saved = new Saved();
            saved.wings = CosmeticsScreen.wings;
            saved.hat = CosmeticsScreen.hat;
            saved.ears = CosmeticsScreen.ears;
            saved.tail = CosmeticsScreen.tail;
            saved.leftShoulder = CosmeticsScreen.leftShoulder;
            saved.rightShoulder = CosmeticsScreen.rightShoulder;
            saved.hideOthers = CosmeticsScreen.hideOthers;
            saved.skinLayers = CosmeticsScreen.skinLayers;
            return saved;
        }

        void apply() {
            CosmeticsScreen.wings = wings;
            CosmeticsScreen.hat = hat;
            CosmeticsScreen.ears = ears;
            CosmeticsScreen.tail = tail;
            CosmeticsScreen.leftShoulder = leftShoulder;
            CosmeticsScreen.rightShoulder = rightShoulder;
            CosmeticsScreen.hideOthers = hideOthers;
            CosmeticsScreen.skinLayers = skinLayers;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Saved saved)) {
                return false;
            }
            return wings == saved.wings
                    && hat == saved.hat
                    && ears == saved.ears
                    && tail == saved.tail
                    && leftShoulder == saved.leftShoulder
                    && rightShoulder == saved.rightShoulder
                    && hideOthers == saved.hideOthers
                    && skinLayers == saved.skinLayers;
        }
    }
}
