package dev.quarkos.cosmetics.forge.gallery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

public final class GalleryCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("quarkos_cosmetics");
    private static final AtomicReference<EquipmentView> VIEW = new AtomicReference<>();
    private static volatile boolean inflight;
    private static int ticks;
    private static boolean announced;
    private static volatile boolean showNeighbor;

    private GalleryCache() {
    }

    public static EquipmentView view() {
        return VIEW.get();
    }

    public static boolean showNeighbor() {
        return showNeighbor;
    }

    public static void showNeighbor(boolean value) {
        showNeighbor = value;
    }

    public static void poll() {
        ticks++;
        if (inflight || ticks % 40 != 0) {
            return;
        }
        String uuid = watchUuid();
        if (uuid == null) {
            return;
        }
        inflight = true;
        Thread thread = new Thread(() -> {
            try {
                GalleryClient client = new GalleryClient(GalleryClient.configuredBase());
                EquipmentView fetched = client.equipment(uuid);
                VIEW.set(fetched);
                if (!announced) {
                    announced = true;
                    LOGGER.info(
                            "forge client sees {} ({}) equipped {}",
                            fetched.playerName(),
                            fetched.playerUuid(),
                            fetched.slots()
                    );
                }
            } catch (Exception e) {
                LOGGER.warn("gallery equipment fetch failed: {}", e.toString());
            } finally {
                inflight = false;
            }
        }, "quarkos-gallery");
        thread.setDaemon(true);
        thread.start();
    }

    public static String watchUuid() {
        String property = System.getProperty("cosmetics.watchUuid");
        if (property != null && !property.isBlank()) {
            return property.trim();
        }
        String file = System.getProperty("cosmetics.watchFile", "/tmp/quarkos-gallery-watch.json");
        Path path = Path.of(file);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            String json = Files.readString(path);
            int key = json.indexOf("\"uuid\"");
            if (key < 0) {
                return null;
            }
            int colon = json.indexOf(':', key);
            int quote = json.indexOf('"', colon + 1);
            int end = json.indexOf('"', quote + 1);
            if (quote < 0 || end < 0) {
                return null;
            }
            return json.substring(quote + 1, end);
        } catch (Exception e) {
            return null;
        }
    }
}
