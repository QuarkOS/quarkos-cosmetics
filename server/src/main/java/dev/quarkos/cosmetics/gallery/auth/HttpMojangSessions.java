package dev.quarkos.cosmetics.gallery.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.quarkos.cosmetics.gallery.ApiException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

public final class HttpMojangSessions implements MojangSessions {
    private final HttpClient http;
    private final URI root;
    private final ObjectMapper mapper;

    public HttpMojangSessions(URI mojangSessionRoot) {
        this(mojangSessionRoot, HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(3))
                .build(), new ObjectMapper());
    }

    HttpMojangSessions(URI mojangSessionRoot, HttpClient http, ObjectMapper mapper) {
        this.root = mojangSessionRoot;
        this.http = http;
        this.mapper = mapper;
    }

    @Override
    public Optional<Profile> hasJoined(String username, String serverId) throws IOException {
        String query = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&serverId=" + URLEncoder.encode(serverId, StandardCharsets.UTF_8);
        URI uri = URI.create(root + "/session/minecraft/hasJoined?" + query);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .header("User-Agent", "cosmetics-gallery/1.0.0")
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(502, "mojang_unavailable", "The Mojang session check was interrupted.");
        }
        int status = response.statusCode();
        if (status == 204 || status == 404) {
            return Optional.empty();
        }
        if (status == 429) {
            throw new ApiException(429, "mojang_rate_limited", "Mojang rate limited the session check. Try again shortly.");
        }
        if (status != 200) {
            throw new ApiException(502, "mojang_unavailable", "Mojang session server returned " + status + ".");
        }
        JsonNode node = mapper.readTree(response.body());
        if (node == null || !node.isObject()) {
            throw new ApiException(502, "mojang_unavailable", "Mojang returned an unexpected session profile.");
        }
        JsonNode id = node.get("id");
        JsonNode name = node.get("name");
        if (id == null || !id.isTextual() || name == null || !name.isTextual()) {
            throw new ApiException(502, "mojang_unavailable", "Mojang returned a session profile without an id and name.");
        }
        return Optional.of(new Profile(id.asText(), name.asText()));
    }
}
