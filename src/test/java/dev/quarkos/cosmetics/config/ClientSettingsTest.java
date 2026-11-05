package dev.quarkos.cosmetics.config;

import dev.quarkos.cosmetics.model.Slot;
import dev.quarkos.cosmetics.model.Worn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientSettingsTest {
    @TempDir
    Path gameDir;

    @Test
    void stripsSecretsAndRestoresEquipment() throws Exception {
        Path file = ClientSettings.configFile(gameDir);
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                galleryUrl=http://127.0.0.1:39221/v1/
                hideOthers=true
                skinLayers=false
                skinDistance=32
                uuid=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa
                equipped.hat=quarkos:lantern_cap
                category.tail=false
                disabled=quarkos:paper_boat
                accessToken=should-not-survive
                password=nope
                """);
        ClientSettings settings = ClientSettings.load(gameDir);
        assertEquals("http://127.0.0.1:39221", settings.galleryUrl);
        assertTrue(settings.hideOthers);
        assertFalse(settings.skinLayers);
        assertEquals(32, settings.skinDistance);
        assertEquals("hat", settings.chickPerch);
        assertEquals("quarkos:lantern_cap", settings.equippedId(Slot.HAT));
        assertFalse(settings.categoryOn.get(Slot.TAIL));
        assertTrue(settings.disabled.contains("quarkos:paper_boat"));
        String saved = Files.readString(ClientSettings.configFile(gameDir));
        assertFalse(saved.contains("accessToken"));
        assertFalse(saved.contains("should-not-survive"));
        assertFalse(saved.contains("password"));
        assertTrue(saved.contains("chickPerch=hat"));
    }

    @Test
    void propertyThenEnvThenFile() {
        assertEquals("http://example.test", ClientSettings.resolveGalleryUrl("http://example.test/v1", "http://env", "http://file", "http://client"));
        assertEquals("http://env", ClientSettings.resolveGalleryUrl(" ", "http://env/", "http://file", ""));
        assertEquals("http://file", ClientSettings.resolveGalleryUrl(null, null, "http://file", "http://client"));
        assertEquals(ClientSettings.DEFAULT_GALLERY, ClientSettings.resolveGalleryUrl(null, null, null, null));
    }

    @Test
    void elytraHidesBackAndTailsKeepsTheSlotEmpty() {
        Map<Slot, String> equipped = new EnumMap<>(Slot.class);
        for (Slot slot : Slot.values()) {
            equipped.put(slot, "");
        }
        equipped.put(Slot.BACK, "quarkos:kite_wings");
        equipped.put(Slot.TAIL, "quarkos:bead_chain");
        equipped.put(Slot.HAT, "quarkos:lantern_cap");
        var hidden = Worn.plan(equipped, Set.of(), Map.of(), true, true, true, false, false);
        assertTrue(hidden.stream().noneMatch(item -> item.slot() == Slot.BACK));
        assertTrue(hidden.stream().noneMatch(item -> item.slot() == Slot.TAIL));
        assertEquals(3f, hidden.stream().filter(item -> item.slot() == Slot.HAT).findFirst().orElseThrow().offsetY());
        var shown = Worn.plan(equipped, Set.of("quarkos:lantern_cap"), Map.of(Slot.TAIL, false), false, false, false, true, true);
        assertTrue(shown.stream().anyMatch(item -> item.slot() == Slot.BACK && item.offsetZ() == 2f));
        assertTrue(shown.stream().noneMatch(item -> item.slot() == Slot.HAT || item.slot() == Slot.TAIL));
    }
}
