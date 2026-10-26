package dev.quarkos.cosmetics.forge.gallery;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * JDK HTTP client for the cosmetics gallery. The base URL has no {@code /v1}
 * suffix; every route below starts with {@code /v1}.
 */
public final class GalleryClient {
    private static final String[] SLOTS = {
            "hat", "ears", "shoulder_left", "shoulder_right", "back", "tail"
    };

    private final HttpClient http;
    private final URI base;

    public GalleryClient(URI base) {
        this(base, HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(3))
                .build());
    }

    public GalleryClient(URI base, HttpClient http) {
        String text = base.toString();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith("/v1")) {
            text = text.substring(0, text.length() - 3);
        }
        this.base = URI.create(text);
        this.http = http;
    }

    public static URI configuredBase() {
        String value = System.getProperty("cosmetics.galleryUrl");
        if (blank(value)) {
            value = System.getenv("COSMETICS_GALLERY_URL");
        }
        if (blank(value)) {
            value = readProperties();
        }
        if (blank(value)) {
            value = "http://127.0.0.1:39221";
        }
        return URI.create(value.trim());
    }

    public URI base() {
        return base;
    }

    public EquipmentView equipment(String uuid) throws IOException, InterruptedException {
        JsonObject body = get("/v1/equipment/" + uuid).getAsJsonObject();
        String playerUuid = text(body, "playerUuid");
        String playerName = body.has("playerName") && !body.get("playerName").isJsonNull()
                ? body.get("playerName").getAsString() : "";
        JsonObject slots = body.getAsJsonObject("slots");
        Map<String, String> equipped = new LinkedHashMap<>();
        Map<String, CosmeticModel> models = new LinkedHashMap<>();
        if (slots != null) {
            for (String slot : SLOTS) {
                if (!slots.has(slot) || slots.get(slot).isJsonNull()) {
                    continue;
                }
                String id = slots.get(slot).getAsString();
                if (id == null || id.isBlank()) {
                    continue;
                }
                equipped.put(slot, id);
                if (!models.containsKey(id)) {
                    models.put(id, cosmetic(id));
                }
            }
        }
        return new EquipmentView(playerUuid, playerName, equipped, models);
    }

    public CosmeticModel cosmetic(String id) throws IOException, InterruptedException {
        JsonObject body = get("/v1/cosmetics/" + id).getAsJsonObject();
        JsonObject model = body.getAsJsonObject("model");
        List<CosmeticModel.Cube> cubes = new ArrayList<>();
        if (model != null && model.has("elements") && model.get("elements").isJsonArray()) {
            for (JsonElement element : model.getAsJsonArray("elements")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject cube = element.getAsJsonObject();
                float[] from = vec(cube.get("from"));
                float[] to = vec(cube.get("to"));
                if (from == null || to == null) {
                    continue;
                }
                cubes.add(new CosmeticModel.Cube(from[0], from[1], from[2], to[0], to[1], to[2]));
            }
        }
        byte[] png = png(model);
        return new CosmeticModel(text(body, "id"), text(body, "name"), text(body, "slot"), cubes, png);
    }

    public String challenge() throws IOException, InterruptedException {
        JsonObject body = send("POST", "/v1/session/challenges", null, null).getAsJsonObject();
        return text(body, "serverId");
    }

    public JsonObject session(String username, String serverId) throws IOException, InterruptedException {
        String json = "{\"username\":" + quote(username) + ",\"serverId\":" + quote(serverId) + "}";
        return send("POST", "/v1/session", json, null).getAsJsonObject();
    }

    public JsonObject publish(String token, String json) throws IOException, InterruptedException {
        return send("POST", "/v1/cosmetics", json, token).getAsJsonObject();
    }

    public JsonObject equip(String token, String slot, String cosmeticId) throws IOException, InterruptedException {
        String json = "{\"slot\":" + quote(slot) + ",\"cosmeticId\":" + quote(cosmeticId) + "}";
        return send("PUT", "/v1/equipment", json, token).getAsJsonObject();
    }

    private JsonElement get(String path) throws IOException, InterruptedException {
        return send("GET", path, null, null);
    }

    private JsonElement send(String method, String path, String json, String token) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .header("User-Agent", "quarkos_cosmetics-forge/1.0.0");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (json == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        }
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IOException(method + " " + path + " returned " + status + " " + response.body());
        }
        if (response.body() == null || response.body().isBlank()) {
            return new JsonObject();
        }
        return JsonParser.parseString(response.body());
    }

    private static byte[] png(JsonObject model) {
        if (model == null || !model.has("textures") || !model.get("textures").isJsonArray()) {
            return new byte[0];
        }
        JsonArray textures = model.getAsJsonArray("textures");
        if (textures.isEmpty() || !textures.get(0).isJsonObject()) {
            return new byte[0];
        }
        JsonObject texture = textures.get(0).getAsJsonObject();
        if (!texture.has("png")) {
            return new byte[0];
        }
        String source = texture.get("png").getAsString();
        int comma = source.indexOf(',');
        if (source.toLowerCase(Locale.ROOT).startsWith("data:image/png;base64,") && comma >= 0) {
            source = source.substring(comma + 1);
        }
        try {
            return Base64.getMimeDecoder().decode(source.replace("\n", "").replace("\r", ""));
        } catch (IllegalArgumentException ex) {
            return new byte[0];
        }
    }

    private static float[] vec(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return null;
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() < 3) {
            return null;
        }
        return new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
    }

    private static String text(JsonObject object, String field) {
        if (object == null || !object.has(field) || object.get(field).isJsonNull()) {
            return "";
        }
        return object.get(field).getAsString();
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String readProperties() {
        Path[] files = {
                Path.of("client.properties"),
                Path.of("config", "quarkos_cosmetics-client.properties")
        };
        for (Path file : files) {
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try {
                for (String line : Files.readAllLines(file)) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("galleryUrl=")) {
                        return trimmed.substring("galleryUrl=".length()).trim();
                    }
                }
            } catch (IOException ignored) {
                return null;
            }
        }
        return null;
    }
}
