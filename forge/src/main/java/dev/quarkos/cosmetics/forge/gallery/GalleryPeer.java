package dev.quarkos.cosmetics.forge.gallery;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

/**
 * The other client. It signs in through the gallery's Mojang join flow and
 * equips a public cosmetic. The Forge game reads that selection back.
 */
public final class GalleryPeer {
    public static final String ADA_ID = "0adaada0ada04adaada0ada0ada0ada0";
    public static final String ADA_UUID = "0adaada0-ada0-4ada-ada0-ada0ada0ada0";
    public static final String ADA_NAME = "Ada";

    private GalleryPeer() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("usage: GalleryPeer mock|equip");
        }
        switch (args[0]) {
            case "mock" -> mock(port("MOCK_PORT", 39222));
            case "equip" -> equip();
            default -> throw new IllegalArgumentException("unknown command " + args[0]);
        }
    }

    private static void mock(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/session/minecraft/join", GalleryPeer::join);
        server.createContext("/session/minecraft/hasJoined", GalleryPeer::hasJoined);
        server.start();
        System.out.println("mock-mojang http://127.0.0.1:" + port);
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void join(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private static void hasJoined(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery() == null ? "" : exchange.getRequestURI().getRawQuery();
        String username = queryValue(query, "username");
        String name = ADA_NAME.equalsIgnoreCase(username) ? ADA_NAME : username;
        String id = ADA_NAME.equalsIgnoreCase(username) ? ADA_ID : ADA_ID;
        byte[] body = ("{\"id\":\"" + id + "\",\"name\":\"" + name + "\"}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static void equip() throws Exception {
        String gallery = env("GALLERY_URL", "http://127.0.0.1:39221");
        String mock = env("MOCK_MOJANG", "http://127.0.0.1:39222");
        GalleryClient client = new GalleryClient(URI.create(gallery));
        String serverId = client.challenge();
        postJoin(mock, serverId);
        JsonObject session = client.session(ADA_NAME, serverId);
        String token = session.get("token").getAsString();
        String uuid = session.get("uuid").getAsString();
        String png = Base64.getEncoder().encodeToString(tealPng());
        String model = """
                {"name":"Gallery brim","description":"Equipped by the other local client.","slot":"hat","model":{"format":"cosmetics-model-1","textures":[{"name":"brim","png":"data:image/png;base64,%s"}],"elements":[{"name":"brim","from":[2,8,2],"to":[14,11,14]}]}}
                """.formatted(png).replace("\n", "");
        JsonObject published = client.publish(token, model);
        String id = published.get("id").getAsString();
        client.equip(token, "hat", id);
        EquipmentView seen = client.equipment(uuid);
        Path watch = Path.of(env("WATCH_FILE", "/tmp/quarkos-gallery-watch.json"));
        Files.createDirectories(watch.getParent() == null ? Path.of(".") : watch.getParent());
        String json = "{\"uuid\":\"" + uuid + "\",\"name\":\"" + seen.playerName() + "\",\"slot\":\"hat\",\"cosmeticId\":\"" + id + "\",\"cosmeticName\":\"Gallery brim\"}\n";
        Files.writeString(watch, json);
        System.out.println("equipped " + id + " on " + uuid);
        System.out.println("watch " + watch.toAbsolutePath());
    }

    private static void postJoin(String mock, String serverId) throws IOException, InterruptedException {
        String body = "{\"accessToken\":\"other-client\",\"selectedProfile\":\"" + ADA_ID + "\",\"serverId\":\"" + serverId + "\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(mock + "/session/minecraft/join"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 204 && response.statusCode() != 200) {
            throw new IOException("mock join returned " + response.statusCode());
        }
    }

    private static byte[] tealPng() throws IOException {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int stripe = (x / 4) % 2 == 0 ? 0xFF1FA7A0 : 0xFFF2E7C9;
                image.setRGB(x, y, stripe);
            }
        }
        image.setRGB(0, 0, Color.WHITE.getRGB());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static String queryValue(String query, String key) {
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String name = URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8);
            if (key.equals(name)) {
                return URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private static int port(String name, int fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Integer.parseInt(value.trim());
    }

    private static String env(String name, String fallback) {
        String property = System.getProperty(name);
        if (property != null && !property.isBlank()) {
            return property.trim();
        }
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
