package dev.quarkos.cosmetics.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackedCosmeticTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void everyPackedCosmeticIsAShadedMultiCube() throws Exception {
        JsonNode catalog;
        try (InputStream stream = resource("cosmetics/catalog.json")) {
            catalog = JSON.readTree(stream);
        }
        int count = 0;
        for (JsonNode item : catalog.path("items")) {
            count++;
            String modelPath = item.path("model").asText();
            String texturePath = item.path("texture").asText();
            String marker = item.path("marker").asText();
            if ("quarkos:jack_o_lantern_mask".equals(item.path("id").asText())) {
                assertArmorMask(modelPath, texturePath);
                continue;
            }
            JsonNode model;
            try (InputStream stream = resource(modelPath)) {
                model = JSON.readTree(stream);
            }
            assertEquals("cosmetics-model-1", model.path("format").asText(), modelPath);
            assertTrue(model.path("elements").size() >= 6, modelPath);
            Set<String> names = new HashSet<>();
            boolean tapered = false;
            float minX = 64;
            float maxX = -64;
            float minY = 64;
            float maxY = -64;
            for (JsonNode element : model.path("elements")) {
                names.add(element.path("name").asText());
                float[] from = vec(element.get("from"));
                float[] to = vec(element.get("to"));
                minX = Math.min(minX, from[0]);
                maxX = Math.max(maxX, to[0]);
                minY = Math.min(minY, from[1]);
                maxY = Math.max(maxY, to[1]);
                float dx = Math.abs(to[0] - from[0]);
                float dy = Math.abs(to[1] - from[1]);
                float dz = Math.abs(to[2] - from[2]);
                if (dx < 8 && dy < 8 && dz < 8) {
                    tapered = true;
                }
                assertTrue(element.path("faces").size() == 6, element.path("name").asText());
            }
            assertTrue(tapered, modelPath);
            assertTrue(maxX - minX < 32 && maxY - minY < 36, modelPath + " is too large for a worn cosmetic");
            assertTrue(names.size() >= 4, modelPath);
            try (InputStream stream = resource(texturePath)) {
                BufferedImage image = ImageIO.read(stream);
                assertEquals(16, image.getWidth(), texturePath);
                assertEquals(16, image.getHeight(), texturePath);
                int wanted = Integer.parseInt(marker, 16);
                assertTrue(contains(image, wanted), texturePath + " is missing marker " + marker);
            }
        }
        assertEquals(17, count);
    }

    /** GeckoLib armor geo, one head bone, 24-frame strip. The png bytes are the handoff, not a 16×16 swatch. */
    private static void assertArmorMask(String modelPath, String texturePath) throws Exception {
        JsonNode model;
        try (InputStream stream = resource(modelPath)) {
            model = JSON.readTree(stream);
        }
        JsonNode geometry = model.path("minecraft:geometry").path(0);
        assertEquals("geometry.jack_o_lantern_mask", geometry.path("description").path("identifier").asText());
        assertEquals(80, geometry.path("description").path("texture_width").asInt());
        assertEquals(80, geometry.path("description").path("texture_height").asInt());
        JsonNode head = null;
        for (JsonNode bone : geometry.path("bones")) {
            if ("armorHead".equals(bone.path("name").asText())) {
                head = bone;
            }
        }
        assertTrue(head != null, "armorHead bone");
        assertEquals("bipedHead", head.path("parent").asText());
        // 60 shell cubes, plus one cube per lit texel of the five candle north faces.
        assertEquals(85, head.path("cubes").size());
        try (InputStream stream = resource(texturePath)) {
            BufferedImage image = ImageIO.read(stream);
            assertEquals(80, image.getWidth(), texturePath);
            assertEquals(1920, image.getHeight(), texturePath);
        }
        try (InputStream stream = resource("textures/cosmetics/jack_o_lantern_mask.png.mcmeta")) {
            JsonNode meta = JSON.readTree(stream);
            assertEquals(2, meta.path("animation").path("frametime").asInt());
            assertEquals(false, meta.path("animation").path("interpolate").asBoolean());
        }
        try (InputStream stream = resource("textures/cosmetics/jack_o_lantern_mask_glow.png.mcmeta")) {
            JsonNode meta = JSON.readTree(stream);
            assertEquals(2, meta.path("animation").path("frametime").asInt());
            assertEquals(false, meta.path("animation").path("interpolate").asBoolean());
        }
    }

    private static boolean contains(BufferedImage image, int rgb) {
        int wanted = 0xFF000000 | rgb;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == wanted) {
                    return true;
                }
            }
        }
        return false;
    }

    private static float[] vec(JsonNode node) {
        return new float[]{(float) node.get(0).asDouble(), (float) node.get(1).asDouble(), (float) node.get(2).asDouble()};
    }

    private static InputStream resource(String path) {
        InputStream stream = PackedCosmeticTest.class.getResourceAsStream("/assets/quarkos_cosmetics/" + path);
        if (stream == null) {
            throw new IllegalStateException("missing " + path);
        }
        return stream;
    }
}
