package dev.quarkos.cosmetics.gallery;

import com.fasterxml.jackson.databind.JsonNode;
import dev.quarkos.cosmetics.gallery.auth.MojangSessions;
import dev.quarkos.cosmetics.gallery.auth.SessionService;
import dev.quarkos.cosmetics.gallery.catalog.GalleryService;
import dev.quarkos.cosmetics.gallery.catalog.ModelValidator;
import dev.quarkos.cosmetics.gallery.store.GalleryStore;
import dev.quarkos.cosmetics.gallery.store.StoredSession;
import io.javalin.Javalin;
import io.javalin.compression.CompressionStrategy;
import io.javalin.http.Context;
import io.javalin.http.HttpResponseException;
import org.eclipse.jetty.http.MimeTypes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.List;
import java.util.Map;

public final class GalleryServer {
    private static final String WROTE = "gallery.wrote";

    private final GalleryConfig config;
    private final Jsons json;
    private final SessionService sessions;
    private final GalleryService gallery;
    private final byte[] indexHtml;
    private final byte[] openApi;
    private Javalin app;

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

    public void start() {
        int uploadCap = config.limits().maxUploadBytes();
        app = Javalin.create(cfg -> {
            cfg.startup.showJavalinBanner = false;
            cfg.startup.startupWatcherEnabled = false;
            cfg.http.generateEtags = false;
            cfg.http.prefer405over404 = true;
            cfg.http.strictContentTypes = false;
            cfg.http.defaultContentType = "application/json; charset=utf-8";
            cfg.http.maxRequestSize = Math.max(8192L, uploadCap);
            cfg.http.compressionStrategy = CompressionStrategy.NONE;
            cfg.router.ignoreTrailingSlashes = false;
            cfg.router.treatMultipleSlashesAsSingleSlash = false;
            cfg.router.caseInsensitiveRoutes = false;
            cfg.jetty.host = config.bind();
            cfg.jetty.port = config.port();

            cfg.routes.exception(ApiException.class, (ex, ctx) -> writeJson(ctx, ex.status(), ex.body()));
            cfg.routes.exception(Exception.class, (ex, ctx) -> {
                ex.printStackTrace(System.err);
                writeJson(ctx, 500, Map.of(
                        "error", "internal",
                        "message", "The gallery failed to handle that request."
                ));
            });
            cfg.routes.error(404, ctx -> {
                if (knownPath(ctx.path())) {
                    writeJson(ctx, 405, Map.of(
                            "error", "method_not_allowed",
                            "message", "That method is not allowed on this path."
                    ));
                } else {
                    writeJson(ctx, 404, Map.of("error", "not_found", "message", "No such route."));
                }
            });
            cfg.routes.error(405, ctx -> writeJson(ctx, 405, Map.of(
                    "error", "method_not_allowed",
                    "message", "That method is not allowed on this path."
            )));

            cfg.routes.get("/", ctx -> write(ctx, 200, "text/html; charset=utf-8", indexHtml));
            cfg.routes.get("/index.html", ctx -> write(ctx, 200, "text/html; charset=utf-8", indexHtml));
            cfg.routes.get("/openapi.yaml", ctx -> write(ctx, 200, "application/yaml; charset=utf-8", openApi));
            cfg.routes.get("/health", ctx -> writeJson(ctx, 200, Map.of("status", "ok", "service", "cosmetics-gallery")));
            cfg.routes.get("/v1/info", ctx -> writeJson(ctx, 200, gallery.info()));
            cfg.routes.post("/v1/session/challenges", this::challenges);
            cfg.routes.post("/v1/session", ctx -> writeJson(ctx, 200, sessions.complete(jsonBody(ctx, 4096))));
            cfg.routes.get("/v1/session", ctx -> {
                StoredSession session = sessions.require(ctx.header("Authorization"));
                writeJson(ctx, 200, sessions.whoami(session));
            });
            cfg.routes.delete("/v1/session", ctx -> {
                StoredSession session = sessions.require(ctx.header("Authorization"));
                writeJson(ctx, 200, sessions.signOut(session));
            });
            cfg.routes.get("/v1/cosmetics", ctx -> writeJson(ctx, 200, gallery.list(
                    query(ctx, "q"), query(ctx, "slot"), query(ctx, "cursor"), query(ctx, "limit"))));
            cfg.routes.post("/v1/cosmetics", ctx -> {
                StoredSession session = sessions.require(ctx.header("Authorization"));
                Map<String, Object> created = gallery.publish(session, jsonBody(ctx, uploadCap));
                ctx.header("Location", String.valueOf(created.get("href")));
                writeJson(ctx, 201, created);
            });
            cfg.routes.get("/v1/cosmetics/{id}", ctx -> writeJson(ctx, 200, gallery.get(ctx.pathParam("id"))));
            cfg.routes.post("/v1/cosmetics/{id}/reports", ctx -> {
                StoredSession session = sessions.require(ctx.header("Authorization"));
                writeJson(ctx, 201, gallery.report(session, ctx.pathParam("id"), jsonBody(ctx, 8192)));
            });
            cfg.routes.post("/v1/cosmetics/{id}/takedown", ctx -> {
                StoredSession session = sessions.require(ctx.header("Authorization"));
                writeJson(ctx, 200, gallery.takedown(session, ctx.pathParam("id"), optionalJson(ctx, 8192)));
            });
            cfg.routes.put("/v1/equipment", ctx -> {
                StoredSession session = sessions.require(ctx.header("Authorization"));
                writeJson(ctx, 200, gallery.equip(session, jsonBody(ctx, 8192)));
            });
            cfg.routes.get("/v1/equipment", ctx -> {
                StoredSession session = sessions.require(ctx.header("Authorization"));
                writeJson(ctx, 200, gallery.equipment(session.uuid()));
            });
            cfg.routes.get("/v1/equipment/{uuid}", ctx -> writeJson(ctx, 200, gallery.equipment(ctx.pathParam("uuid"))));
        });
        app.start(config.bind(), config.port());
    }

    public void stop() {
        if (app != null) {
            app.stop();
        }
    }

    public int port() {
        return app.port();
    }

    public String baseUrl() {
        return "http://" + config.bind() + ":" + port();
    }

    private void challenges(Context ctx) throws IOException {
        byte[] raw = readLimited(ctx, 4096);
        if (raw.length > 0) {
            if (!isJson(ctx.contentType())) {
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
        writeJson(ctx, 201, sessions.issueChallenge());
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

    private JsonNode jsonBody(Context ctx, int maxBytes) throws IOException {
        if (!isJson(ctx.contentType())) {
            readLimited(ctx, maxBytes);
            throw new ApiException(415, "unsupported_media_type", "Send application/json.");
        }
        byte[] raw = readLimited(ctx, maxBytes);
        JsonNode node = json.parse(raw);
        if (node == null) {
            throw new ApiException(400, "validation_failed", "A JSON body is required.");
        }
        return node;
    }

    private JsonNode optionalJson(Context ctx, int maxBytes) throws IOException {
        byte[] raw = readLimited(ctx, maxBytes);
        if (raw.length == 0) {
            return null;
        }
        if (!isJson(ctx.contentType())) {
            throw new ApiException(415, "unsupported_media_type", "Send application/json.");
        }
        return json.parse(raw);
    }

    private byte[] readLimited(Context ctx, int maxBytes) throws IOException {
        long declared = ctx.contentLength();
        if (declared > maxBytes) {
            throw new ApiException(413, "payload_too_large", "Upload exceeds " + maxBytes + " bytes.");
        }
        try (InputStream in = ctx.bodyInputStream()) {
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
        } catch (HttpResponseException ex) {
            if (ex.getStatus() == 413) {
                throw new ApiException(413, "payload_too_large", "Upload exceeds " + maxBytes + " bytes.");
            }
            throw ex;
        }
    }

    private static String query(Context ctx, String name) {
        List<String> values = ctx.queryParams(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(values.size() - 1);
    }

    /**
     * Jetty parses the media type. Only {@code application/json}, with UTF-8 if a charset is present.
     */
    private static boolean isJson(String header) {
        if (header == null || header.isBlank()) {
            return false;
        }
        String mime = MimeTypes.getContentTypeWithoutCharset(header);
        if (mime == null || !mime.equalsIgnoreCase("application/json")) {
            return false;
        }
        String charset = MimeTypes.getCharsetFromContentType(header);
        return charset == null || charset.equalsIgnoreCase("utf-8");
    }

    private void writeJson(Context ctx, int status, Object body) {
        write(ctx, status, "application/json; charset=utf-8", json.write(body));
    }

    private void write(Context ctx, int status, String contentType, byte[] body) {
        if (Boolean.TRUE.equals(ctx.attribute(WROTE))) {
            return;
        }
        ctx.attribute(WROTE, Boolean.TRUE);
        ctx.status(status);
        ctx.contentType(contentType);
        ctx.header("Cache-Control", "no-store");
        ctx.header("X-Content-Type-Options", "nosniff");
        ctx.result(body);
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
