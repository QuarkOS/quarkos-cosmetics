package dev.quarkos.cosmetics.gallery;

import com.sun.net.httpserver.HttpServer;
import dev.quarkos.cosmetics.gallery.auth.HttpMojangSessions;
import dev.quarkos.cosmetics.gallery.auth.MojangSessions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpMojangSessionsTest {
    private HttpServer mojang;
    private HttpMojangSessions sessions;
    private final AtomicReference<String> seen = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        mojang = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mojang.createContext("/session/minecraft/hasJoined", exchange -> {
            seen.set(exchange.getRequestURI().getRawQuery());
            String query = exchange.getRequestURI().getRawQuery();
            if (query != null && query.contains("username=Ada")) {
                byte[] body = """
                        {"id":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","name":"Ada","properties":[{"name":"textures","value":"SECRET_CAPE_URL"}]}
                        """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } else {
                exchange.sendResponseHeaders(204, -1);
            }
            exchange.close();
        });
        mojang.start();
        sessions = new HttpMojangSessions(URI.create("http://127.0.0.1:" + mojang.getAddress().getPort()));
    }

    @AfterEach
    void stop() {
        mojang.stop(0);
    }

    @Test
    void readsUuidAndNameAndDropsTextures() throws Exception {
        Optional<MojangSessions.Profile> profile = sessions.hasJoined("Ada", "abc123abc123abc123abc123abc123ab");
        assertTrue(profile.isPresent());
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", profile.get().id());
        assertEquals("Ada", profile.get().name());
        assertTrue(seen.get().contains("serverId=abc123abc123abc123abc123abc123ab"));
        assertTrue(sessions.hasJoined("Bea", "abc123abc123abc123abc123abc123ab").isEmpty());
    }
}
