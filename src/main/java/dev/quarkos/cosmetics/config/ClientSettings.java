package dev.quarkos.cosmetics.config;

import dev.quarkos.cosmetics.gallery.GalleryClient;
import dev.quarkos.cosmetics.model.Slot;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Persistent wardrobe settings. The Minecraft access token is never written. */
public final class ClientSettings {
    public static final String DEFAULT_GALLERY = "http://127.0.0.1:39221";
    private static final String[] SECRET_KEYS = {"accessToken", "access_token", "password", "token", "galleryToken"};

    private static ClientSettings current = new ClientSettings();

    public String galleryUrl = DEFAULT_GALLERY;
    public boolean hideOthers;
    public boolean skinLayers = true;
    public int skinDistance = 16;
    public String uuid = "";
    public String playerName = "";
    public final Map<Slot, String> equipped = new EnumMap<>(Slot.class);
    public final Map<Slot, Boolean> categoryOn = new EnumMap<>(Slot.class);
    public final Set<String> disabled = new LinkedHashSet<>();

    public ClientSettings() {
        for (Slot slot : Slot.values()) {
            categoryOn.put(slot, true);
            equipped.put(slot, "");
        }
        equipped.put(Slot.BACK, "quarkos:kite_wings");
    }

    public static ClientSettings get() {
        return current;
    }

    public static void install(ClientSettings settings) {
        current = settings;
    }

    public static ClientSettings load(Path gameDir) {
        ClientSettings settings = new ClientSettings();
        Path file = configFile(gameDir);
        Properties properties = new Properties();
        boolean dirty = false;
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                properties.load(reader);
            } catch (IOException ignored) {
                properties.clear();
            }
            for (String key : SECRET_KEYS) {
                if (properties.remove(key) != null) {
                    dirty = true;
                }
            }
        }
        settings.galleryUrl = resolveGalleryUrl(
                System.getProperty("cosmetics.galleryUrl"),
                System.getenv("COSMETICS_GALLERY_URL"),
                properties.getProperty("galleryUrl"),
                readGalleryUrl(gameDir.resolve("client.properties")));
        settings.hideOthers = Boolean.parseBoolean(properties.getProperty("hideOthers", "false"));
        settings.skinLayers = Boolean.parseBoolean(properties.getProperty("skinLayers", "true"));
        settings.skinDistance = parseDistance(properties.getProperty("skinDistance", "16"));
        settings.uuid = properties.getProperty("uuid", "");
        settings.playerName = properties.getProperty("playerName", "");
        for (Slot slot : Slot.values()) {
            settings.equipped.put(slot, properties.getProperty("equipped." + slot.wire, slot == Slot.BACK ? "quarkos:kite_wings" : ""));
            settings.categoryOn.put(slot, Boolean.parseBoolean(properties.getProperty("category." + slot.wire, "true")));
        }
        settings.disabled.clear();
        String disabled = properties.getProperty("disabled", "");
        if (!disabled.isBlank()) {
            for (String id : disabled.split(",")) {
                if (!id.isBlank()) {
                    settings.disabled.add(id.trim());
                }
            }
        }
        current = settings;
        if (dirty) {
            settings.save(gameDir);
        }
        return settings;
    }

    public void save(Path gameDir) {
        Properties properties = new Properties();
        properties.setProperty("galleryUrl", GalleryClient.normalizeBase(galleryUrl));
        properties.setProperty("hideOthers", Boolean.toString(hideOthers));
        properties.setProperty("skinLayers", Boolean.toString(skinLayers));
        properties.setProperty("skinDistance", Integer.toString(skinDistance));
        properties.setProperty("uuid", uuid == null ? "" : uuid);
        properties.setProperty("playerName", playerName == null ? "" : playerName);
        for (Slot slot : Slot.values()) {
            properties.setProperty("equipped." + slot.wire, equipped.getOrDefault(slot, ""));
            properties.setProperty("category." + slot.wire, Boolean.toString(categoryOn.getOrDefault(slot, true)));
        }
        properties.setProperty("disabled", String.join(",", disabled));
        Path file = configFile(gameDir);
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                properties.store(writer, "Quarkos Cosmetics. Never put a Minecraft access token in this file.");
            }
        } catch (IOException ignored) {
            // The wardrobe still works for this session if the disk is read-only.
        }
    }

    public static String resolveGalleryUrl(String property, String env, String configValue, String clientFile) {
        if (present(property)) {
            return GalleryClient.normalizeBase(property);
        }
        if (present(env)) {
            return GalleryClient.normalizeBase(env);
        }
        if (present(configValue)) {
            return GalleryClient.normalizeBase(configValue);
        }
        if (present(clientFile)) {
            return GalleryClient.normalizeBase(clientFile);
        }
        return DEFAULT_GALLERY;
    }

    public boolean shown(Slot slot, String id) {
        return id != null && !id.isBlank() && categoryOn.getOrDefault(slot, true) && !disabled.contains(id);
    }

    public static Path configFile(Path gameDir) {
        return gameDir.resolve("config").resolve("quarkos_cosmetics.properties");
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static int parseDistance(String raw) {
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 0) {
                return 16;
            }
            return Math.min(value, 128);
        } catch (NumberFormatException ex) {
            return 16;
        }
    }

    private static String readGalleryUrl(Path file) {
        if (!Files.isRegularFile(file)) {
            return "";
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        } catch (IOException ex) {
            return "";
        }
        return properties.getProperty("galleryUrl", "");
    }

    public String equippedId(Slot slot) {
        return equipped.getOrDefault(slot, "");
    }

    public void equip(Slot slot, String id) {
        equipped.put(slot, id == null ? "" : id);
    }

    public static String hyphenate(String raw) {
        String compact = raw.toLowerCase(Locale.ROOT).replace("-", "");
        if (!compact.matches("[0-9a-f]{32}")) {
            return raw;
        }
        return compact.substring(0, 8) + "-" + compact.substring(8, 12) + "-" + compact.substring(12, 16) + "-"
                + compact.substring(16, 20) + "-" + compact.substring(20);
    }
}
