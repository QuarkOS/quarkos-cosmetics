package dev.quarkos.cosmetics.gallery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import dev.quarkos.cosmetics.model.CosmeticModel;
import dev.quarkos.cosmetics.model.Slot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GalleryClientTest {
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer gallery;
    private HttpServer mojang;
    private final Map<String, String> joins = new ConcurrentHashMap<>();
    private final Map<String, Stored> cosmetics = new ConcurrentHashMap<>();
    private final Map<String, String[]> equipment = new ConcurrentHashMap<>();
    private final AtomicInteger secrets = new AtomicInteger();

    @AfterEach
    void stop() {
        if (gallery != null) {
            gallery.stop(0);
        }
        if (mojang != null) {
            mojang.stop(0);
        }
    }

    @Test
    void twoClientsPublishEquipAndReadEachOther() throws Exception {
        start();
        GalleryClient ada = new GalleryClient(galleryUrl());
        GalleryClient bea = new GalleryClient(galleryUrl());
        GalleryClient.Session adaSession = ada.signIn("Ada", "token-ada", java.util.UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        GalleryClient.Session beaSession = bea.signIn("Bea", "token-bea", java.util.UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", adaSession.uuid());
        assertEquals("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", beaSession.uuid());

        String hat = publishable("Straw hat", "A wide paper brim.", Slot.HAT);
        String tail = publishable("Ribbon", "A cloth ribbon.", Slot.TAIL);
        GalleryClient.Listed adaHat = ada.publish(adaSession.token(), hat);
        GalleryClient.Listed beaTail = bea.publish(beaSession.token(), tail);
        ada.equip(adaSession.token(), Slot.HAT, adaHat.id());
        bea.equip(beaSession.token(), Slot.TAIL, beaTail.id());

        GalleryClient.Equipment seenByAda = ada.equipment(beaSession.uuid());
        GalleryClient.Equipment seenByBea = bea.equipment(adaSession.uuid());
        assertEquals(beaTail.id(), seenByAda.id(Slot.TAIL));
        assertEquals(adaHat.id(), seenByBea.id(Slot.HAT));
        assertFalse(ada.cosmetic(seenByAda.id(Slot.TAIL)).model().elements.isEmpty());
        assertFalse(bea.cosmetic(seenByBea.id(Slot.HAT)).model().elements.isEmpty());
        assertEquals(0, secrets.get());
    }

    @Test
    void reportAndBrowseStayOnTheGallery() throws Exception {
        start();
        GalleryClient ada = new GalleryClient(galleryUrl());
        GalleryClient.Session session = ada.signIn("Ada", "token-ada", java.util.UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        GalleryClient.Listed listed = ada.publish(session.token(), publishable("Lantern", "Paper.", Slot.HAT));
        GalleryClient.Page page = ada.list("lantern", Slot.HAT, null, 20);
        assertEquals(listed.id(), page.items().get(0).id());
        GalleryClient.Report report = ada.report(session.token(), listed.id(), "spam", "Test report.");
        assertEquals("open", report.status());
        assertEquals(0, secrets.get());
    }

    @Test
    void liveGalleryTwoClientsPublishEquipAndReadEachOther() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("quarkos.liveGallery"));
        String base = System.getProperty("cosmetics.galleryUrl", "http://127.0.0.1:39221");
        GalleryClient ada = new GalleryClient(base);
        GalleryClient bea = new GalleryClient(base);
        GalleryClient.Challenge adaChallenge = ada.createChallenge();
        GalleryClient.Challenge beaChallenge = bea.createChallenge();
        GalleryClient.Session adaSession = ada.completeSession("Ada", adaChallenge.serverId());
        GalleryClient.Session beaSession = bea.completeSession("Bea", beaChallenge.serverId());
        GalleryClient.Listed adaHat = ada.publish(adaSession.token(), publishable("Live straw", "Paper.", Slot.HAT));
        GalleryClient.Listed beaTail = bea.publish(beaSession.token(), publishable("Live ribbon", "Cloth.", Slot.TAIL));
        ada.equip(adaSession.token(), Slot.HAT, adaHat.id());
        bea.equip(beaSession.token(), Slot.TAIL, beaTail.id());
        assertEquals(beaTail.id(), ada.equipment(beaSession.uuid()).id(Slot.TAIL));
        assertEquals(adaHat.id(), bea.equipment(adaSession.uuid()).id(Slot.HAT));
        assertFalse(ada.cosmetic(beaTail.id()).model().elements.isEmpty());
        assertFalse(bea.cosmetic(adaHat.id()).model().elements.isEmpty());
        GalleryClient.Page page = ada.list("straw", Slot.HAT, null, 20);
        assertFalse(page.items().isEmpty());
        assertEquals("open", ada.report(adaSession.token(), beaTail.id(), "spam", "Live check.").status());
    }

    @Test
    void baseUrlDropsATrailingVersionPrefix() {
        assertEquals("http://127.0.0.1:39221", GalleryClient.normalizeBase("http://127.0.0.1:39221/v1/"));
        assertEquals("http://127.0.0.1:39221", GalleryClient.normalizeBase("http://127.0.0.1:39221/"));
    }

    private void start() throws IOException {
        mojang = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mojang.createContext("/session/minecraft/join", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode node = json.readTree(body);
            joins.put(node.get("serverId").asText(), node.get("selectedProfile").asText());
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        mojang.start();
        gallery = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        gallery.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (body.contains("accessToken") || body.contains("access_token") || body.contains("password")) {
                secrets.incrementAndGet();
                write(exchange, 400, "{\"error\":\"access_token_rejected\",\"message\":\"no\"}");
                return;
            }
            try {
                route(exchange, path, body);
            } catch (Exception ex) {
                write(exchange, 500, "{\"error\":\"error\",\"message\":\"" + ex.getMessage() + "\"}");
            }
        });
        gallery.start();
    }

    private void route(com.sun.net.httpserver.HttpExchange exchange, String path, String body) throws IOException {
        if ("/v1/session/challenges".equals(path)) {
            String id = String.format("%032x", System.nanoTime() & 0xffffffffffffL);
            joins.put("challenge:" + id, "open");
            write(exchange, 201, json.writeValueAsString(Map.of(
                    "serverId", id,
                    "expiresAt", "2026-10-27T00:00:00Z",
                    "mojangJoinUrl", "http://127.0.0.1:" + mojang.getAddress().getPort() + "/session/minecraft/join")));
            return;
        }
        if ("/v1/session".equals(path) && "POST".equals(exchange.getRequestMethod())) {
            JsonNode node = json.readTree(body);
            String serverId = node.get("serverId").asText();
            String profile = joins.get(serverId);
            if (profile == null) {
                write(exchange, 401, "{\"error\":\"unauthorized\",\"message\":\"Mojang did not confirm that join.\"}");
                return;
            }
            String name = node.get("username").asText();
            String uuid = "Ada".equals(name)
                    ? "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
                    : "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
            write(exchange, 200, json.writeValueAsString(Map.of(
                    "token", "gallery-" + name,
                    "tokenType", "Bearer",
                    "uuid", uuid,
                    "name", name,
                    "expiresAt", "2026-10-27T00:00:00Z")));
            return;
        }
        if ("/v1/cosmetics".equals(path) && "GET".equals(exchange.getRequestMethod())) {
            ObjectNode page = json.createObjectNode();
            var items = page.putArray("items");
            cosmetics.values().forEach(stored -> items.add(summary(stored)));
            page.putNull("nextCursor");
            write(exchange, 200, page.toString());
            return;
        }
        if ("/v1/cosmetics".equals(path) && "POST".equals(exchange.getRequestMethod())) {
            JsonNode node = json.readTree(body);
            String id = java.util.UUID.randomUUID().toString().replace("-", "");
            String token = exchange.getRequestHeaders().getFirst("Authorization");
            String author = token != null && token.contains("Ada") ? "Ada" : "Bea";
            String uuid = "Ada".equals(author)
                    ? "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
                    : "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
            Stored stored = new Stored(id, node.get("name").asText(), node.path("description").asText(""), node.get("slot").asText(), author, uuid, node.get("model"));
            cosmetics.put(id, stored);
            write(exchange, 201, summary(stored).toString());
            return;
        }
        if (path.startsWith("/v1/cosmetics/") && path.endsWith("/reports")) {
            String id = path.substring("/v1/cosmetics/".length(), path.length() - "/reports".length());
            write(exchange, 201, json.writeValueAsString(Map.of(
                    "id", "report",
                    "cosmeticId", id,
                    "status", "open",
                    "galleryStatus", "public")));
            return;
        }
        if (path.startsWith("/v1/cosmetics/")) {
            String id = path.substring("/v1/cosmetics/".length());
            Stored stored = cosmetics.get(id);
            ObjectNode detail = summary(stored);
            detail.set("model", stored.model);
            write(exchange, 200, detail.toString());
            return;
        }
        if ("/v1/equipment".equals(path) && "PUT".equals(exchange.getRequestMethod())) {
            JsonNode node = json.readTree(body);
            String token = exchange.getRequestHeaders().getFirst("Authorization");
            String uuid = token != null && token.contains("Ada")
                    ? "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
                    : "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
            String[] slots = equipment.computeIfAbsent(uuid, key -> new String[Slot.values().length]);
            Slot slot = Slot.parse(node.get("slot").asText()).orElseThrow();
            slots[slot.ordinal()] = node.get("cosmeticId").isNull() ? null : node.get("cosmeticId").asText();
            write(exchange, 200, equipmentJson(uuid));
            return;
        }
        if (path.startsWith("/v1/equipment/")) {
            String uuid = path.substring("/v1/equipment/".length());
            write(exchange, 200, equipmentJson(uuid));
            return;
        }
        write(exchange, 404, "{\"error\":\"not_found\",\"message\":\"no\"}");
    }

    private ObjectNode summary(Stored stored) {
        ObjectNode node = json.createObjectNode();
        node.put("id", stored.id);
        node.put("name", stored.name);
        node.put("description", stored.description);
        node.put("slot", stored.slot);
        ObjectNode author = node.putObject("author");
        author.put("uuid", stored.uuid);
        author.put("name", stored.author);
        node.put("elements", 1);
        node.put("triangles", 12);
        node.put("createdAt", "2026-10-26T00:00:00Z");
        node.put("href", "/v1/cosmetics/" + stored.id);
        return node;
    }

    private String equipmentJson(String uuid) throws IOException {
        String[] slots = equipment.getOrDefault(uuid, new String[Slot.values().length]);
        ObjectNode node = json.createObjectNode();
        node.put("playerUuid", uuid);
        node.put("playerName", uuid.startsWith("aaaa") ? "Ada" : "Bea");
        node.putNull("updatedAt");
        ObjectNode slotNode = node.putObject("slots");
        for (Slot slot : Slot.values()) {
            String id = slots[slot.ordinal()];
            if (id == null) {
                slotNode.putNull(slot.wire);
            } else {
                slotNode.put(slot.wire, id);
            }
        }
        return node.toString();
    }

    private void write(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private String galleryUrl() {
        return "http://127.0.0.1:" + gallery.getAddress().getPort();
    }

    private String publishable(String name, String description, Slot slot) throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFFFF3366);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", png));
        String b64 = Base64.getEncoder().encodeToString(png.toByteArray());
        return """
                {"name":"%s","description":"%s","slot":"%s","model":{"format":"cosmetics-model-1","textures":[{"name":"cloth","png":"data:image/png;base64,%s"}],"elements":[{"name":"brim","from":[2,8,2],"to":[14,10,14],"origin":[8,9,8],"rotation":[0,0,0],"faces":{"up":{"uv":[0,0,8,8],"texture":0}}}]}}
                """.formatted(name, description, slot.wire, b64);
    }

    private record Stored(String id, String name, String description, String slot, String author, String uuid, JsonNode model) {
    }
}
