package dev.quarkos.cosmetics.gallery;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public record GalleryConfig(
        String bind,
        int port,
        Path dataDir,
        URI mojangSessionRoot,
        Limits limits,
        int reportThreshold,
        Duration sessionTtl,
        Duration challengeTtl
) {
    public GalleryConfig {
        if (bind == null || bind.isBlank()) {
            throw new IllegalArgumentException("bind is required");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        if (dataDir == null) {
            throw new IllegalArgumentException("dataDir is required");
        }
        if (mojangSessionRoot == null || mojangSessionRoot.getScheme() == null) {
            throw new IllegalArgumentException("mojangSessionRoot must be an absolute URI");
        }
        if (reportThreshold < 1) {
            throw new IllegalArgumentException("reportThreshold must be at least 1");
        }
        String root = mojangSessionRoot.toString();
        if (root.endsWith("/")) {
            mojangSessionRoot = URI.create(root.substring(0, root.length() - 1));
        }
    }

    public static GalleryConfig defaults(Path dataDir) {
        return new GalleryConfig(
                "127.0.0.1",
                39221,
                dataDir,
                URI.create("https://sessionserver.mojang.com"),
                Limits.defaults(),
                5,
                Duration.ofHours(12),
                Duration.ofSeconds(90)
        );
    }

    public static GalleryConfig load() throws IOException {
        Map<String, String> env = new LinkedHashMap<>(System.getenv());
        String configured = env.getOrDefault("GALLERY_CONFIG", "gallery.properties");
        Path file = Path.of(configured);
        return load(env, Files.isRegularFile(file) ? file : null);
    }

    public static GalleryConfig load(Map<String, String> env, Path configFile) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        if (configFile != null) {
            Properties properties = new Properties();
            try (InputStream in = Files.newInputStream(configFile)) {
                properties.load(in);
            }
            for (String name : properties.stringPropertyNames()) {
                values.put(name, properties.getProperty(name).trim());
            }
        }
        putEnv(values, env, "bind", "GALLERY_BIND");
        putEnv(values, env, "port", "GALLERY_PORT");
        putEnv(values, env, "data", "GALLERY_DATA");
        putEnv(values, env, "mojangSessionRoot", "GALLERY_MOJANG_SESSION_ROOT");
        putEnv(values, env, "maxUploadBytes", "GALLERY_MAX_UPLOAD_BYTES");
        putEnv(values, env, "maxElements", "GALLERY_MAX_ELEMENTS");
        putEnv(values, env, "maxTriangles", "GALLERY_MAX_TRIANGLES");
        putEnv(values, env, "maxTextures", "GALLERY_MAX_TEXTURES");
        putEnv(values, env, "maxTextureEdge", "GALLERY_MAX_TEXTURE_EDGE");
        putEnv(values, env, "maxTextureBytes", "GALLERY_MAX_TEXTURE_BYTES");
        putEnv(values, env, "maxPublicPerPlayer", "GALLERY_MAX_PUBLIC_PER_PLAYER");
        putEnv(values, env, "maxRecordsPerPlayer", "GALLERY_MAX_RECORDS_PER_PLAYER");
        putEnv(values, env, "reportThreshold", "GALLERY_REPORT_THRESHOLD");

        Limits defaults = Limits.defaults();
        Limits limits = new Limits(
                integer(values, "maxUploadBytes", defaults.maxUploadBytes()),
                integer(values, "maxElements", defaults.maxElements()),
                integer(values, "maxTriangles", defaults.maxTriangles()),
                integer(values, "maxTextures", defaults.maxTextures()),
                integer(values, "maxTextureEdge", defaults.maxTextureEdge()),
                integer(values, "maxTextureBytes", defaults.maxTextureBytes()),
                integer(values, "maxPublicPerPlayer", defaults.maxPublicPerPlayer()),
                integer(values, "maxRecordsPerPlayer", defaults.maxRecordsPerPlayer())
        );
        return new GalleryConfig(
                values.getOrDefault("bind", "127.0.0.1"),
                integer(values, "port", 39221),
                Path.of(values.getOrDefault("data", "data")),
                URI.create(values.getOrDefault("mojangSessionRoot", "https://sessionserver.mojang.com")),
                limits,
                integer(values, "reportThreshold", 5),
                Duration.ofHours(12),
                Duration.ofSeconds(90)
        );
    }

    private static void putEnv(Map<String, String> values, Map<String, String> env, String key, String envName) {
        String value = env.get(envName);
        if (value != null && !value.isBlank()) {
            values.put(key, value.trim());
        }
    }

    private static int integer(Map<String, String> values, String key, int fallback) {
        String raw = values.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
    }

    public record Limits(
            int maxUploadBytes,
            int maxElements,
            int maxTriangles,
            int maxTextures,
            int maxTextureEdge,
            int maxTextureBytes,
            int maxPublicPerPlayer,
            int maxRecordsPerPlayer
    ) {
        public Limits {
            requirePositive("maxUploadBytes", maxUploadBytes);
            requirePositive("maxElements", maxElements);
            requirePositive("maxTriangles", maxTriangles);
            requirePositive("maxTextures", maxTextures);
            requirePositive("maxTextureEdge", maxTextureEdge);
            requirePositive("maxTextureBytes", maxTextureBytes);
            requirePositive("maxPublicPerPlayer", maxPublicPerPlayer);
            requirePositive("maxRecordsPerPlayer", maxRecordsPerPlayer);
        }

        public static Limits defaults() {
            return new Limits(262144, 64, 768, 4, 128, 65536, 24, 48);
        }

        public Map<String, Object> toMap(int reportThreshold) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("maxUploadBytes", maxUploadBytes);
            map.put("maxElements", maxElements);
            map.put("maxTriangles", maxTriangles);
            map.put("maxTextures", maxTextures);
            map.put("maxTextureEdge", maxTextureEdge);
            map.put("maxTextureBytes", maxTextureBytes);
            map.put("maxPublicPerPlayer", maxPublicPerPlayer);
            map.put("maxRecordsPerPlayer", maxRecordsPerPlayer);
            map.put("reportThreshold", reportThreshold);
            return map;
        }

        private static void requirePositive(String name, int value) {
            if (value < 1) {
                throw new IllegalArgumentException(name + " must be at least 1");
            }
        }
    }
}
