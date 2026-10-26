package dev.quarkos.cosmetics.gallery;

import dev.quarkos.cosmetics.gallery.auth.HttpMojangSessions;

import javax.imageio.ImageIO;
import java.time.Clock;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        ImageIO.setUseCache(false);
        GalleryConfig config = GalleryConfig.load();
        GalleryServer server = GalleryServer.start(config, new HttpMojangSessions(config.mojangSessionRoot()), Clock.systemUTC());
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        System.out.println("cosmetics-gallery " + server.baseUrl());
        Thread.currentThread().join();
    }
}
