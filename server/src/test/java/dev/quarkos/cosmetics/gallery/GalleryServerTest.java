package dev.quarkos.cosmetics.gallery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.quarkos.cosmetics.gallery.auth.MojangSessions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GalleryServerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ADA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String BEA = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String CAM = "cccccccccccccccccccccccccccccccc";

    @TempDir
    Path temp;

    private ScriptedMojang mojang;
    private ManualClock clock;
    private GalleryServer server;
    private HttpClient http;

    @BeforeEach
    void start() throws Exception {
        mojang = new ScriptedMojang();
        clock = new ManualClock();
        server = GalleryServer.start(config(temp, 2, 24, 262144, 768), mojang, clock);
        http = HttpClient.newHttpClient();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void publishesBrowsesEquipsAndTakesDown() throws Exception {
        Response info = send("GET", "/v1/info", null, null);
        assertEquals(200, info.status);
        assertTrue(info.json.get("free").asBoolean());
        assertEquals("uuid", info.json.get("playerKey").asText());
        assertEquals("hat", info.json.get("slots").get(0).asText());
        assertEquals("ears", info.json.get("slots").get(1).asText());
        assertFalse(info.json.get("slots").toString().contains("cape"));

        Response html = send("GET", "/", null, null);
        assertEquals(200, html.status);
        assertTrue(html.raw.contains("/v1/session/challenges"));
        assertTrue(html.raw.contains("/v1/cosmetics"));
        assertTrue(html.raw.contains("/v1/equipment"));
        assertTrue(html.raw.contains("/reports"));
        assertTrue(html.raw.contains("/takedown"));
        assertTrue(html.raw.contains(">ears</option>"));
        assertFalse(html.raw.toLowerCase().contains("<style"));
        assertFalse(html.raw.contains("accessToken"));

        Response spec = send("GET", "/openapi.yaml", null, null);
        assertEquals(200, spec.status);
        assertTrue(spec.raw.contains("/v1/session/challenges"));
        assertTrue(spec.raw.contains("hasJoined"));

        String ada = signIn("Ada", ADA);
        String bea = signIn("Bea", BEA);
        String cam = signIn("Cam", CAM);

        Response missing = send("POST", "/v1/session",
                "{\"username\":\"Ada\",\"serverId\":\"0123456789abcdef0123456789abcdef\"}", null);
        assertEquals(400, missing.status);
        assertEquals("unknown_server_id", missing.json.get("error").asText());

        Response foreign = send("POST", "/v1/session",
                "{\"username\":\"Ada\",\"serverId\":\"0123456789abcdef0123456789abcdef01234567\"}", null);
        assertEquals(400, foreign.status);

        Response tokenRejected = send("POST", "/v1/session",
                "{\"username\":\"Ada\",\"serverId\":\"0123456789abcdef0123456789abcdef\",\"accessToken\":\"secret-access-token\"}", null);
        assertEquals(400, tokenRejected.status);
        assertEquals("access_token_rejected", tokenRejected.json.get("error").asText());

        Response unauth = send("POST", "/v1/cosmetics", modelBody("Hat", "hat"), null);
        assertEquals(401, unauth.status);

        Response created = send("POST", "/v1/cosmetics", modelBody("Straw hat", "hat"), ada);
        assertEquals(201, created.status);
        assertEquals(ADA_HYPHEN, created.json.get("author").get("uuid").asText());
        String hat = created.json.get("id").asText();

        Response wings = send("POST", "/v1/cosmetics", modelBody("Paper wings", "back"), ada);
        assertEquals(201, wings.status);
        String wingId = wings.json.get("id").asText();

        Response ears = send("POST", "/v1/cosmetics", modelBody("Fox ears", "ears"), ada);
        assertEquals(201, ears.status);
        assertEquals("ears", ears.json.get("slot").asText());

        Response listed = send("GET", "/v1/cosmetics?slot=hat&q=straw", null, null);
        assertEquals(200, listed.status);
        assertEquals(1, listed.json.get("items").size());
        assertEquals(hat, listed.json.get("items").get(0).get("id").asText());
        assertFalse(listed.json.get("items").get(0).has("model"));

        Response detail = send("GET", "/v1/cosmetics/" + hat, null, null);
        assertEquals(200, detail.status);
        assertEquals("cosmetics-model-1", detail.json.get("model").get("format").asText());

        Response likes = send("POST", "/v1/cosmetics",
                "{\"name\":\"Nope\",\"slot\":\"hat\",\"likes\":1,\"model\":{\"format\":\"cosmetics-model-1\",\"elements\":[]}}", ada);
        assertEquals(400, likes.status);

        Response equipped = send("PUT", "/v1/equipment",
                "{\"slot\":\"hat\",\"cosmeticId\":\"" + hat + "\"}", ada);
        assertEquals(200, equipped.status);
        assertEquals(hat, equipped.json.get("slots").get("hat").asText());

        Response lookedUp = send("GET", "/v1/equipment/" + ADA, null, null);
        assertEquals(200, lookedUp.status);
        assertEquals(hat, lookedUp.json.get("slots").get("hat").asText());

        Response wrongSlot = send("PUT", "/v1/equipment",
                "{\"slot\":\"tail\",\"cosmeticId\":\"" + hat + "\"}", ada);
        assertEquals(400, wrongSlot.status);

        Response ownReport = send("POST", "/v1/cosmetics/" + hat + "/reports",
                "{\"reason\":\"spam\"}", ada);
        assertEquals(403, ownReport.status);

        Response report = send("POST", "/v1/cosmetics/" + hat + "/reports",
                "{\"reason\":\"stolen\",\"detail\":\"copied\"}", bea);
        assertEquals(201, report.status);
        assertEquals("public", report.json.get("galleryStatus").asText());

        Response again = send("POST", "/v1/cosmetics/" + hat + "/reports",
                "{\"reason\":\"spam\"}", bea);
        assertEquals(409, again.status);

        Response second = send("POST", "/v1/cosmetics/" + hat + "/reports",
                "{\"reason\":\"inappropriate\"}", cam);
        assertEquals(201, second.status);
        assertEquals("taken_down", second.json.get("galleryStatus").asText());

        Response hidden = send("GET", "/v1/cosmetics/" + hat, null, null);
        assertEquals(404, hidden.status);
        Response after = send("GET", "/v1/equipment/" + ADA_HYPHEN, null, null);
        assertTrue(after.json.get("slots").get("hat").isNull());

        Response worn = send("PUT", "/v1/equipment",
                "{\"slot\":\"hat\",\"cosmeticId\":\"" + hat + "\"}", bea);
        assertEquals(409, worn.status);

        Response removed = send("POST", "/v1/cosmetics/" + wingId + "/takedown",
                "{\"note\":\"replaced\"}", ada);
        assertEquals(200, removed.status);
        assertEquals("taken_down", removed.json.get("status").asText());

        Response stranger = send("POST", "/v1/cosmetics/" + wingId + "/takedown", "{}", bea);
        assertEquals(404, stranger.status);

        assertNoSecret(temp, "secret-access-token");
        assertNoSecret(temp, "SECRET_CAPE_URL");
    }

    @Test
    void twoClientsPublishEquipAndReadEachOther() throws Exception {
        String ada = signIn("Ada", ADA);
        String bea = signIn("Bea", BEA);

        Response adaHat = send("POST", "/v1/cosmetics", modelBody("Straw hat", "hat"), ada);
        assertEquals(201, adaHat.status);
        Response beaEars = send("POST", "/v1/cosmetics", modelBody("Fox ears", "ears"), bea);
        assertEquals(201, beaEars.status);
        String hatId = adaHat.json.get("id").asText();
        String earsId = beaEars.json.get("id").asText();

        assertEquals(200, send("PUT", "/v1/equipment",
                "{\"slot\":\"hat\",\"cosmeticId\":\"" + hatId + "\"}", ada).status);
        assertEquals(200, send("PUT", "/v1/equipment",
                "{\"slot\":\"ears\",\"cosmeticId\":\"" + earsId + "\"}", bea).status);

        Response adaGear = send("GET", "/v1/equipment/" + ADA, null, null);
        Response beaGear = send("GET", "/v1/equipment/" + BEA_HYPHEN, null, null);
        assertEquals(200, adaGear.status);
        assertEquals(200, beaGear.status);
        assertEquals(hatId, adaGear.json.get("slots").get("hat").asText());
        assertTrue(adaGear.json.get("slots").get("ears").isNull());
        assertEquals(earsId, beaGear.json.get("slots").get("ears").asText());
        assertTrue(beaGear.json.get("slots").get("hat").isNull());

        Response hat = send("GET", "/v1/cosmetics/" + adaGear.json.get("slots").get("hat").asText(), null, null);
        Response ears = send("GET", "/v1/cosmetics/" + beaGear.json.get("slots").get("ears").asText(), null, null);
        assertEquals(200, hat.status);
        assertEquals(200, ears.status);
        assertEquals("cosmetics-model-1", hat.json.get("model").get("format").asText());
        assertEquals("Straw hat", hat.json.get("name").asText());
        assertEquals("ears", ears.json.get("slot").asText());
        assertEquals("Fox ears", ears.json.get("name").asText());
        assertEquals("cosmetics-model-1", ears.json.get("model").get("format").asText());
    }

    @Test
    void serverIdIsOneTimeAndExpires() throws Exception {
        Response challenge = send("POST", "/v1/session/challenges", null, null);
        String serverId = challenge.json.get("serverId").asText();
        mojang.allow("Ada", serverId, ADA);
        Response first = send("POST", "/v1/session",
                "{\"username\":\"Ada\",\"serverId\":\"" + serverId + "\"}", null);
        assertEquals(200, first.status);
        Response replay = send("POST", "/v1/session",
                "{\"username\":\"Ada\",\"serverId\":\"" + serverId + "\"}", null);
        assertEquals(400, replay.status);

        Response next = send("POST", "/v1/session/challenges", null, null);
        clock.now = clock.now.plus(Duration.ofMinutes(2));
        Response expired = send("POST", "/v1/session",
                "{\"username\":\"Ada\",\"serverId\":\"" + next.json.get("serverId").asText() + "\"}", null);
        assertEquals(400, expired.status);
        assertEquals("expired_server_id", expired.json.get("error").asText());
    }

    @Test
    void enforcesUploadAndPolyCaps() throws Exception {
        server.stop();
        server = GalleryServer.start(config(temp.resolve("tight"), 5, 1, 2048, 4), mojang, clock);
        String ada = signIn("Ada", ADA);
        String fat = "{\"name\":\"Fat\",\"slot\":\"hat\",\"model\":" + "\"" + "x".repeat(3000) + "\"}";
        Response tooBig = send("POST", "/v1/cosmetics", fat, ada);
        assertEquals(413, tooBig.status);

        Response tooMany = send("POST", "/v1/cosmetics", modelBody("Cube", "hat"), ada);
        assertEquals(400, tooMany.status);
        assertTrue(tooMany.raw.contains("triangles"));

        server.stop();
        server = GalleryServer.start(config(temp.resolve("cap"), 5, 1, 262144, 768), mojang, clock);
        ada = signIn("Ada", ADA);
        assertEquals(201, send("POST", "/v1/cosmetics", modelBody("One", "hat"), ada).status);
        Response second = send("POST", "/v1/cosmetics", modelBody("Two", "tail"), ada);
        assertEquals(409, second.status);
    }

    private String signIn(String name, String rawUuid) throws Exception {
        Response challenge = send("POST", "/v1/session/challenges", null, null);
        assertEquals(201, challenge.status);
        String serverId = challenge.json.get("serverId").asText();
        assertEquals(32, serverId.length());
        mojang.allow(name, serverId, rawUuid);
        Response session = send("POST", "/v1/session",
                "{\"username\":\"" + name + "\",\"serverId\":\"" + serverId + "\"}", null);
        assertEquals(200, session.status);
        assertFalse(session.json.has("accessToken"));
        Response who = send("GET", "/v1/session", null, session.json.get("token").asText());
        assertEquals(Ids.hyphenateUuid(rawUuid), who.json.get("uuid").asText());
        return session.json.get("token").asText();
    }

    private Response send(String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else if ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method)) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.GET();
        }
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        String raw = response.body() == null ? "" : response.body();
        JsonNode json = null;
        if (raw.startsWith("{") || raw.startsWith("[")) {
            try {
                json = MAPPER.readTree(raw);
            } catch (Exception ignored) {
                json = null;
            }
        }
        return new Response(response.statusCode(), raw, json);
    }

    private static String modelBody(String name, String slot) {
        return "{\"name\":\"" + name + "\",\"description\":\"handmade\",\"slot\":\"" + slot + "\",\"model\":{"
                + "\"format\":\"cosmetics-model-1\",\"elements\":[{\"name\":\"cube\",\"from\":[0,0,0],\"to\":[8,8,8],"
                + "\"faces\":{\"north\":{},\"south\":{},\"east\":{},\"west\":{},\"up\":{},\"down\":{}}}]}}";
    }

    private static GalleryConfig config(Path data, int reports, int perPlayer, int maxBytes, int triangles) {
        return new GalleryConfig(
                "127.0.0.1",
                0,
                data,
                URI.create("http://127.0.0.1:9"),
                new GalleryConfig.Limits(maxBytes, 64, triangles, 4, 128, 65536, perPlayer, 48),
                reports,
                Duration.ofHours(12),
                Duration.ofSeconds(90)
        );
    }

    private static void assertNoSecret(Path root, String secret) throws Exception {
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                assertFalse(text.contains(secret), file.toString());
            }
        }
    }

    private static final String ADA_HYPHEN = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String BEA_HYPHEN = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    private record Response(int status, String raw, JsonNode json) {
    }

    private static final class ScriptedMojang implements MojangSessions {
        private final Map<String, Profile> joined = new HashMap<>();

        void allow(String username, String serverId, String rawUuid) {
            joined.put(username + "\n" + serverId, new Profile(rawUuid, username));
        }

        @Override
        public Optional<Profile> hasJoined(String username, String serverId) {
            return Optional.ofNullable(joined.get(username + "\n" + serverId));
        }
    }

    private static final class ManualClock extends Clock {
        private Instant now = Instant.parse("2026-10-26T12:00:00Z");

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
