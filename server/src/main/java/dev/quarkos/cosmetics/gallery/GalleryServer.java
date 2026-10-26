package dev.quarkos.cosmetics.gallery;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.quarkos.cosmetics.gallery.auth.MojangSessions;
import dev.quarkos.cosmetics.gallery.auth.SessionService;
import dev.quarkos.cosmetics.gallery.catalog.GalleryService;
import dev.quarkos.cosmetics.gallery.catalog.ModelValidator;
import dev.quarkos.cosmetics.gallery.store.GalleryStore;
import dev.quarkos.cosmetics.gallery.store.StoredSession;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GalleryServer {
    private final GalleryConfig config;
    private final Jsons json;
    private final SessionService sessions;
    private final GalleryService gallery;
    private final byte[] indexHtml;
    private final byte[] openApi;
    private HttpServer http;
    private ExecutorService executor;

    public GalleryServer(GalleryConfig config, GalleryStore store, MojangSessions mojang, Clock clock) throws IOException {
        this.config = config;
        this.json = new Jsons(config.limits().maxUploadBytes());
        this.sessions = new SessionService(store, mojang, clock, config.sessionTtl(), config.challengeTtl());
        this.gallery = new GalleryService(store, new ModelValidator(config.limits()), config, clock);
        this.indexHtml = resource("/public/index.html");
        this.openApi = resource("/openapi.yaml");
    }

    public static GalleryServer start(GalleryConfig config, MojangSessions mojang, Clock clock) throws IOException {
        Jsons json = new Jsons(config.limits().maxUploadBytes());
        GalleryStore store = new GalleryStore(config.dataDir(), json);
        GalleryServer server = new GalleryServer(config, store, mojang, clock);
        server.start();
        return server;
    }

    public void start() throws IOException {
        http = HttpServer.create(new InetSocketAddress(config.bind(), config.port()), 0);
        http.createContext("/", this::handle);
        executor = Executors.newFixedThreadPool(8, runnable -> {
            Thread thread = new Thread(runnable, "gallery");
            thread.setDaemon(true);
            return thread;
        });
        http.setExecutor(executor);
        http.start();
    }

    public void stop() {
        if (http != null) {
            http.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    public int port() {
        return http.getAddress().getPort();
    }

    public String baseUrl() {
        return "http://" + config.bind() + ":" + port();
    }

    private void handle(HttpExchange exchange) {
        try {
            route(exchange);
        } catch (ApiException ex) {
            writeJson(exchange, ex.status(), ex.body());
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            writeJson(exchange, 500, Map.of(
                    "error", "internal",
                    "message", "The gallery failed to handle that request."
            ));
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        String path = exchange.getRequestURI().getPath();
        if ("GET".equals(method) && ("/".equals(path) || "/index.html".equals(path))) {
            write(exchange, 200, "text/html; charset=utf-8", indexHtml);
            return;
        }
        if ("GET".equals(method) && "/openapi.yaml".equals(path)) {
            write(exchange, 200, "application/yaml; charset=utf-8", openApi);
            return;
        }
        if ("GET".equals(method) && "/health".equals(path)) {
            writeJson(exchange, 200, Map.of("status", "ok", "service", "cosmetics-gallery"));
            return;
        }
        if ("GET".equals(method) && "/v1/info".equals(path)) {
            writeJson(exchange, 200, gallery.info());
            return;
        }
        if ("POST".equals(method) && "/v1/session/challenges".equals(path)) {
            byte[] raw = readLimited(exchange, 4096);
            if (raw.length > 0) {
                if (!isJson(exchange.getRequestHeaders().getFirst("Content-Type"))) {
                    throw new ApiException(415, "unsupported_media_type", "Send application/json.");
                }
                JsonNode node = json.parse(raw);
                if (node != null && node.isObject() && (node.has("accessToken") || node.has("access_token") || node.has("password"))) {
                    throw new ApiException(400, "access_token_rejected",
                            "Send the Minecraft access token only to sessionserver join. This service does not accept or store it.");
                }
                if (node != null && (!node.isObject() || node.size() > 0)) {
                    throw new ApiException(400, "validation_failed", "A challenge takes no fields.");
                }
            }
            writeJson(exchange, 201, sessions.issueChallenge());
            return;
        }
        if ("POST".equals(method) && "/v1/session".equals(path)) {
            JsonNode body = jsonBody(exchange, 4096);
            writeJson(exchange, 200, sessions.complete(body));
            return;
        }
        if ("/v1/session".equals(path) && ("GET".equals(method) || "DELETE".equals(method))) {
            StoredSession session = sessions.require(exchange.getRequestHeaders().getFirst("Authorization"));
            if ("GET".equals(method)) {
                writeJson(exchange, 200, sessions.whoami(session));
            } else {
                writeJson(exchange, 200, sessions.signOut(session));
            }
            return;
        }
        if ("GET".equals(method) && "/v1/cosmetics".equals(path)) {
            Map<String, String> query = query(exchange.getRequestURI());
            writeJson(exchange, 200, gallery.list(query.get("q"), query.get("slot"), query.get("cursor"), query.get("limit")));
            return;
        }
        if ("POST".equals(method) && "/v1/cosmetics".equals(path)) {
            StoredSession session = sessions.require(exchange.getRequestHeaders().getFirst("Authorization"));
            JsonNode body = jsonBody(exchange, config.limits().maxUploadBytes());
            Map<String, Object> created = gallery.publish(session, body);
            exchange.getResponseHeaders().set("Location", String.valueOf(created.get("href")));
            writeJson(exchange, 201, created);
            return;
        }
        if (path.startsWith("/v1/cosmetics/")) {
            String rest = path.substring("/v1/cosmetics/".length());
            if ("GET".equals(method) && !rest.contains("/")) {
                writeJson(exchange, 200, gallery.get(rest));
                return;
            }
            if ("POST".equals(method) && rest.endsWith("/reports")) {
                String id = rest.substring(0, rest.length() - "/reports".length());
                StoredSession session = sessions.require(exchange.getRequestHeaders().getFirst("Authorization"));
                writeJson(exchange, 201, gallery.report(session, id, jsonBody(exchange, 8192)));
                return;
            }
            if ("POST".equals(method) && rest.endsWith("/takedown")) {
                String id = rest.substring(0, rest.length() - "/takedown".length());
                StoredSession session = sessions.require(exchange.getRequestHeaders().getFirst("Authorization"));
                byte[] raw = readLimited(exchange, 8192);
                JsonNode body = raw.length == 0 ? null : json.parse(raw);
                if (raw.length > 0 && !isJson(exchange.getRequestHeaders().getFirst("Content-Type"))) {
                    throw new ApiException(415, "unsupported_media_type", "Send application/json.");
                }
                writeJson(exchange, 200, gallery.takedown(session, id, body));
                return;
            }
        }
        if ("PUT".equals(method) && "/v1/equipment".equals(path)) {
            StoredSession session = sessions.require(exchange.getRequestHeaders().getFirst("Authorization"));
            writeJson(exchange, 200, gallery.equip(session, jsonBody(exchange, 8192)));
            return;
        }
        if ("GET".equals(method) && "/v1/equipment".equals(path)) {
            StoredSession session = sessions.require(exchange.getRequestHeaders().getFirst("Authorization"));
            writeJson(exchange, 200, gallery.equipment(session.uuid()));
            return;
        }
        if ("GET".equals(method) && path.startsWith("/v1/equipment/")) {
            String uuid = path.substring("/v1/equipment/".length());
            if (!uuid.isBlank() && !uuid.contains("/")) {
                writeJson(exchange, 200, gallery.equipment(uuid));
                return;
            }
        }
        if (knownPath(path)) {
            writeJson(exchange, 405, Map.of("error", "method_not_allowed", "message", "That method is not allowed on this path."));
            return;
        }
        writeJson(exchange, 404, Map.of("error", "not_found", "message", "No such route."));
    }

    private boolean knownPath(String path) {
        return "/".equals(path)
                || "/index.html".equals(path)
                || "/openapi.yaml".equals(path)
                || "/health".equals(path)
                || "/v1/info".equals(path)
                || "/v1/session".equals(path)
                || "/v1/session/challenges".equals(path)
                || "/v1/cosmetics".equals(path)
                || "/v1/equipment".equals(path)
                || path.startsWith("/v1/cosmetics/")
                || path.startsWith("/v1/equipment/");
    }

    private JsonNode jsonBody(HttpExchange exchange, int maxBytes) throws IOException {
        if (!isJson(exchange.getRequestHeaders().getFirst("Content-Type"))) {
            readLimited(exchange, maxBytes);
            throw new ApiException(415, "unsupported_media_type", "Send application/json.");
        }
        byte[] raw = readLimited(exchange, maxBytes);
        JsonNode node = json.parse(raw);
        if (node == null) {
            throw new ApiException(400, "validation_failed", "A JSON body is required.");
        }
        return node;
    }

    private byte[] readLimited(HttpExchange exchange, int maxBytes) throws IOException {
        String declared = exchange.getRequestHeaders().getFirst("Content-Length");
        if (declared != null) {
            try {
                int length = Integer.parseInt(declared.trim());
                if (length > maxBytes) {
                    throw new ApiException(413, "payload_too_large", "Upload exceeds " + maxBytes + " bytes.");
                }
            } catch (NumberFormatException ex) {
                throw new ApiException(400, "bad_request", "Content-Length is not a number.");
            }
        }
        try (InputStream in = exchange.getRequestBody()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            int total = 0;
            while ((read = in.read(buffer)) >= 0) {
                total += read;
                if (total > maxBytes) {
                    throw new ApiException(413, "payload_too_large", "Upload exceeds " + maxBytes + " bytes.");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static boolean isJson(String header) {
        if (header == null) {
            return false;
        }
        String[] parts = header.split(";");
        if (!parts[0].trim().equalsIgnoreCase("application/json")) {
            return false;
        }
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].trim().toLowerCase(Locale.ROOT);
            if (part.isEmpty()) {
                continue;
            }
            if (!part.equals("charset=utf-8") && !part.equals("charset=\"utf-8\"")) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> values = new LinkedHashMap<>();
        String raw = uri.getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return values;
        }
        for (String part : raw.split("&")) {
            int split = part.indexOf('=');
            String key = urlDecode(split < 0 ? part : part.substring(0, split));
            String value = urlDecode(split < 0 ? "" : part.substring(split + 1));
            values.put(key, value);
        }
        return values;
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private void writeJson(HttpExchange exchange, int status, Object body) {
        write(exchange, status, "application/json; charset=utf-8", json.write(body));
    }

    private void write(HttpExchange exchange, int status, String contentType, byte[] body) {
        try {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        } catch (IOException ex) {
            System.err.println("Failed to write the gallery response.");
        }
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = GalleryServer.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IOException("Missing resource " + name);
            }
            return in.readAllBytes();
        }
    }
}
