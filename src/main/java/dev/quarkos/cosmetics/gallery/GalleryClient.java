package dev.quarkos.cosmetics.gallery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.quarkos.cosmetics.model.CosmeticModel;
import dev.quarkos.cosmetics.model.ModelException;
import dev.quarkos.cosmetics.model.Slot;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Talks only to this mod's gallery. The Minecraft access token is posted to
 * {@code mojangJoinUrl} from the challenge and is never sent to the gallery.
 */
public final class GalleryClient {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String baseUrl;
    private final HttpClient http;

    public GalleryClient(String baseUrl) {
        this(baseUrl, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    public GalleryClient(String baseUrl, HttpClient http) {
        this.baseUrl = normalizeBase(baseUrl);
        this.http = http;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public static String normalizeBase(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith("/v1")) {
            value = value.substring(0, value.length() - 3);
        }
        return value;
    }

    public JsonNode info() throws GalleryException, IOException {
        return send("GET", "/v1/info", null, null).body;
    }

    public Challenge createChallenge() throws GalleryException, IOException {
        Response response = send("POST", "/v1/session/challenges", null, "");
        JsonNode body = response.body;
        return new Challenge(
                required(body, "serverId"),
                required(body, "expiresAt"),
                required(body, "mojangJoinUrl"));
    }

    /**
     * Posts the access token only to the challenge's Mojang join URL.
     * The token is not logged and is not included in any gallery request.
     */
    public void joinMojang(Challenge challenge, String accessToken, UUID profileId) throws GalleryException, IOException {
        if (accessToken == null || accessToken.isBlank()) {
            throw new GalleryException(401, "unauthorized", "Sign-in needs a Minecraft session.");
        }
        ObjectNode body = JSON.createObjectNode();
        body.put("accessToken", accessToken);
        body.put("selectedProfile", profileId.toString().replace("-", "").toLowerCase(Locale.ROOT));
        body.put("serverId", challenge.serverId());
        HttpRequest request = HttpRequest.newBuilder(URI.create(challenge.mojangJoinUrl()))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "quarkos_cosmetics/1.0.0")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = sendRaw(request);
        int status = response.statusCode();
        if (status != 204 && status != 200) {
            throw new GalleryException(status, "mojang_join_failed", "Mojang join returned " + status + ".");
        }
    }

    public Session completeSession(String username, String serverId) throws GalleryException, IOException {
        ObjectNode body = JSON.createObjectNode();
        body.put("username", username);
        body.put("serverId", serverId);
        JsonNode json = send("POST", "/v1/session", null, body.toString()).body;
        return new Session(
                required(json, "token"),
                required(json, "tokenType"),
                required(json, "uuid"),
                required(json, "name"),
                required(json, "expiresAt"));
    }

    public Session signIn(String username, String accessToken, UUID profileId) throws GalleryException, IOException {
        Challenge challenge = createChallenge();
        joinMojang(challenge, accessToken, profileId);
        return completeSession(username, challenge.serverId());
    }

    public Page list(String query, Slot slot, String cursor, int limit) throws GalleryException, IOException {
        StringBuilder path = new StringBuilder("/v1/cosmetics?limit=").append(Math.max(1, Math.min(50, limit)));
        if (query != null && !query.isBlank()) {
            path.append("&q=").append(encode(query));
        }
        if (slot != null) {
            path.append("&slot=").append(encode(slot.wire));
        }
        if (cursor != null && !cursor.isBlank()) {
            path.append("&cursor=").append(encode(cursor));
        }
        JsonNode body = send("GET", path.toString(), null, null).body;
        List<Listed> items = new ArrayList<>();
        JsonNode array = body.get("items");
        if (array != null && array.isArray()) {
            for (JsonNode item : array) {
                items.add(readListed(item));
            }
        }
        JsonNode next = body.get("nextCursor");
        String nextCursor = next == null || next.isNull() ? null : next.asText();
        return new Page(items, nextCursor);
    }

    public Detail cosmetic(String id) throws GalleryException, IOException, ModelException {
        JsonNode body = send("GET", "/v1/cosmetics/" + encode(id), null, null).body;
        JsonNode modelNode = body.get("model");
        if (modelNode == null || modelNode.isNull()) {
            throw new GalleryException(502, "not_found", "The gallery cosmetic did not include a model.");
        }
        return new Detail(readListed(body), CosmeticModel.parse(modelNode.toString()));
    }

    public Listed publish(String token, String json) throws GalleryException, IOException {
        JsonNode body = send("POST", "/v1/cosmetics", token, json).body;
        return readListed(body);
    }

    public Equipment equip(String token, Slot slot, String cosmeticId) throws GalleryException, IOException {
        ObjectNode body = JSON.createObjectNode();
        body.put("slot", slot.wire);
        if (cosmeticId == null) {
            body.putNull("cosmeticId");
        } else {
            body.put("cosmeticId", cosmeticId);
        }
        return readEquipment(send("PUT", "/v1/equipment", token, body.toString()).body);
    }

    public Equipment equipment(String uuid) throws GalleryException, IOException {
        return readEquipment(send("GET", "/v1/equipment/" + encode(uuid), null, null).body);
    }

    public Report report(String token, String id, String reason, String detail) throws GalleryException, IOException {
        ObjectNode body = JSON.createObjectNode();
        body.put("reason", reason);
        if (detail != null && !detail.isBlank()) {
            body.put("detail", detail);
        }
        JsonNode json = send("POST", "/v1/cosmetics/" + encode(id) + "/reports", token, body.toString()).body;
        return new Report(required(json, "id"), required(json, "cosmeticId"), required(json, "status"), required(json, "galleryStatus"));
    }

    private static Listed readListed(JsonNode item) throws GalleryException {
        JsonNode author = item.get("author");
        String authorName = author != null && author.has("name") ? author.get("name").asText("") : "";
        return new Listed(
                required(item, "id"),
                required(item, "name"),
                item.path("description").asText(""),
                required(item, "slot"),
                authorName);
    }

    private static Equipment readEquipment(JsonNode body) throws GalleryException {
        JsonNode slots = body.get("slots");
        if (slots == null || !slots.isObject()) {
            throw new GalleryException(502, "validation_failed", "Equipment did not include slots.");
        }
        String[] values = new String[Slot.values().length];
        for (Slot slot : Slot.values()) {
            JsonNode value = slots.get(slot.wire);
            values[slot.ordinal()] = value == null || value.isNull() ? null : value.asText();
        }
        JsonNode name = body.get("playerName");
        return new Equipment(
                required(body, "playerUuid"),
                name == null || name.isNull() ? null : name.asText(),
                values);
    }

    private Response send(String method, String path, String token, String json) throws GalleryException, IOException {
        if (json != null && !json.isEmpty()) {
            rejectSecrets(json);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("User-Agent", "quarkos_cosmetics/1.0.0");
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        if ("GET".equals(method)) {
            builder.GET();
        } else if (json == null || json.isEmpty()) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(json));
        }
        HttpResponse<String> response = sendRaw(builder.build());
        int status = response.statusCode();
        JsonNode body = null;
        if (response.body() != null && !response.body().isBlank()) {
            try {
                body = JSON.readTree(response.body());
            } catch (IOException ex) {
                if (status >= 400) {
                    throw new GalleryException(status, "not_json", "The gallery returned " + status + ".");
                }
                throw ex;
            }
        }
        if (status < 200 || status >= 300) {
            String error = body != null && body.has("error") ? body.get("error").asText("error") : "error";
            String message = body != null && body.has("message") ? body.get("message").asText("The gallery returned " + status + ".") : "The gallery returned " + status + ".";
            throw new GalleryException(status, error, message);
        }
        if (body == null) {
            body = JSON.createObjectNode();
        }
        return new Response(status, body);
    }

    private HttpResponse<String> sendRaw(HttpRequest request) throws GalleryException {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GalleryException(0, "interrupted", "The gallery request was interrupted.");
        } catch (IOException ex) {
            throw new GalleryException(0, "unreachable", "The gallery is not reachable at " + baseUrl + ".");
        }
    }

    private static void rejectSecrets(String json) throws GalleryException {
        if (json.contains("\"accessToken\"") || json.contains("\"access_token\"") || json.contains("\"password\"")) {
            throw new GalleryException(400, "access_token_rejected", "Refusing to send an access token to the gallery.");
        }
    }

    private static String required(JsonNode node, String field) throws GalleryException {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new GalleryException(502, "validation_failed", "The gallery response omitted " + field + ".");
        }
        return value.asText();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record Response(int status, JsonNode body) {
    }

    public record Challenge(String serverId, String expiresAt, String mojangJoinUrl) {
    }

    public record Session(String token, String tokenType, String uuid, String name, String expiresAt) {
    }

    public record Listed(String id, String name, String description, String slot, String author) {
    }

    public record Page(List<Listed> items, String nextCursor) {
    }

    public record Detail(Listed listed, CosmeticModel model) {
    }

    public record Equipment(String playerUuid, String playerName, String[] slots) {
        public String id(Slot slot) {
            return slots[slot.ordinal()];
        }
    }

    public record Report(String id, String cosmeticId, String status, String galleryStatus) {
    }
}
