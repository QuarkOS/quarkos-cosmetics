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
                if ("textures/cosmetics/kite_wings.png".equals(texturePath)) {
                    assertEquals(64, image.getWidth(), texturePath);
                    assertEquals(32, image.getHeight(), texturePath);
                    continue;
                }
                assertEquals(16, image.getWidth(), texturePath);
                assertEquals(16, image.getHeight(), texturePath);
                int wanted = Integer.parseInt(marker, 16);
                assertTrue(contains(image, wanted), texturePath + " is missing marker " + marker);
            }
        }
        assertEquals(16, count);
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
