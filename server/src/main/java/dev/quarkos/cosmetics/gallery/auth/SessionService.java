package dev.quarkos.cosmetics.gallery.auth;

import com.fasterxml.jackson.databind.JsonNode;
import dev.quarkos.cosmetics.gallery.ApiException;
import dev.quarkos.cosmetics.gallery.Ids;
import dev.quarkos.cosmetics.gallery.store.GalleryStore;
import dev.quarkos.cosmetics.gallery.store.StoredSession;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues a one-time serverId and trades a confirmed Mojang join for a gallery session.
 * A serverId this process did not issue is rejected. The Minecraft access token is never accepted.
 */
public final class SessionService {
    static final int MAX_OPEN_CHALLENGES = 1000;
    private static final Set<String> SESSION_FIELDS = Set.of("username", "serverId");

    private final GalleryStore store;
    private final MojangSessions mojang;
    private final Clock clock;
    private final Duration sessionTtl;
    private final Duration challengeTtl;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Instant> challenges = new ConcurrentHashMap<>();

    public SessionService(GalleryStore store, MojangSessions mojang, Clock clock, Duration sessionTtl, Duration challengeTtl) {
        this.store = store;
        this.mojang = mojang;
        this.clock = clock;
        this.sessionTtl = sessionTtl;
        this.challengeTtl = challengeTtl;
    }

    public Map<String, Object> issueChallenge() {
        sweep();
        if (challenges.size() >= MAX_OPEN_CHALLENGES) {
            throw new ApiException(429, "too_many_challenges", "Too many open sign-in challenges. Try again shortly.");
        }
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String serverId = HexFormat.of().formatHex(bytes);
        Instant expiresAt = now().plus(challengeTtl);
        challenges.put(serverId, expiresAt);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serverId", serverId);
        body.put("expiresAt", expiresAt.toString());
        body.put("mojangJoinUrl", "https://sessionserver.mojang.com/session/minecraft/join");
        return body;
    }

    public Map<String, Object> complete(JsonNode body) throws IOException {
        if (body == null || !body.isObject()) {
            throw new ApiException(400, "validation_failed", "Sign-in requires a JSON object with username and serverId.");
        }
        rejectForeignFields(body);
        String username = text(body, "username");
        String serverId = text(body, "serverId");
        if (!Ids.USERNAME.matcher(username).matches()) {
            throw new ApiException(400, "validation_failed", "Username must be 3 to 16 letters, digits, or underscores.");
        }
        if (!Ids.SERVER_ID.matcher(serverId).matches()) {
            throw new ApiException(400, "unknown_server_id",
                    "This serverId was not issued here. Request one from this service. Do not reuse another service's serverId.");
        }
        Instant expiry = challenges.get(serverId);
        if (expiry == null) {
            throw new ApiException(400, "unknown_server_id",
                    "This serverId is not an open challenge from this service.");
        }
        if (!expiry.isAfter(now())) {
            challenges.remove(serverId, expiry);
            throw new ApiException(400, "expired_server_id", "That serverId expired. Request another.");
        }
        Optional<MojangSessions.Profile> profile = mojang.hasJoined(username, serverId);
        if (profile.isEmpty()) {
            throw new ApiException(401, "unauthorized",
                    "Mojang did not confirm that join. Call sessionserver join from the game, then try this serverId again.");
        }
        MojangSessions.Profile confirmed = profile.get();
        if (!username.equalsIgnoreCase(confirmed.name())) {
            throw new ApiException(401, "unauthorized", "Mojang confirmed a different player than the username sent here.");
        }
        if (!challenges.remove(serverId, expiry)) {
            throw new ApiException(400, "unknown_server_id", "This serverId was already used.");
        }
        String uuid = Ids.hyphenateMojangId(confirmed.id());
        Instant sessionExpiry = now().plus(sessionTtl);
        StoredSession session = store.createSession(uuid, confirmed.name(), sessionExpiry);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", session.token());
        response.put("tokenType", "Bearer");
        response.put("uuid", session.uuid());
        response.put("name", session.name());
        response.put("expiresAt", session.expiresAt());
        return response;
    }

    public StoredSession require(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ApiException(401, "unauthorized", "Sign in with a gallery session.");
        }
        String token = authorization.substring("Bearer ".length());
        if (!token.equals(token.trim()) || !token.matches("[A-Za-z0-9_-]{20,128}")) {
            throw new ApiException(401, "unauthorized", "Sign in with a gallery session.");
        }
        StoredSession session = store.findSession(token, now()).orElseThrow(
                () -> new ApiException(401, "unauthorized", "That gallery session is missing or expired."));
        return session;
    }

    public Map<String, Object> whoami(StoredSession session) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("uuid", session.uuid());
        body.put("name", session.name());
        body.put("expiresAt", session.expiresAt());
        return body;
    }

    public Map<String, Object> signOut(StoredSession session) {
        store.deleteSession(session.token());
        return Map.of("status", "signed_out");
    }

    private void rejectForeignFields(JsonNode body) {
        Iterator<String> names = body.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if ("accessToken".equals(name) || "access_token".equals(name) || "password".equals(name)) {
                throw new ApiException(400, "access_token_rejected",
                        "Send the Minecraft access token only to sessionserver join. This service does not accept or store it.");
            }
            if (!SESSION_FIELDS.contains(name)) {
                throw new ApiException(400, "validation_failed", "Unknown field.");
            }
        }
    }

    private static String text(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || !node.isTextual()) {
            throw new ApiException(400, "validation_failed", field + " is required.");
        }
        return node.asText();
    }

    private void sweep() {
        Instant current = now();
        challenges.entrySet().removeIf(entry -> !entry.getValue().isAfter(current));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
