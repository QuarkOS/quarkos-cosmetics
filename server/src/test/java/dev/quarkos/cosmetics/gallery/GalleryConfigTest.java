package dev.quarkos.cosmetics.gallery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GalleryConfigTest {
    @TempDir
    Path temp;

    @Test
    void environmentOverridesTheFile() throws Exception {
        Path file = temp.resolve("gallery.properties");
        Files.writeString(file, "port=4000\nbind=127.0.0.1\n");
        GalleryConfig config = GalleryConfig.load(Map.of("GALLERY_PORT", "39221"), file);
        assertEquals(39221, config.port());
        assertEquals("127.0.0.1", config.bind());
        assertEquals(64, config.limits().maxElements());
    }
}
