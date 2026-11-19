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
    private static final Set<String> V1 = Set.of("quarkos:chick", "quarkos:jack_o_lantern_mask",
            "quarkos:scarecrow_straw_hat", "quarkos:candle_skull_crown", "quarkos:ghost_buddy");

    @Test
    void theV1ModelsRetainTheirShadedGeometry() throws Exception {
        JsonNode catalog;
        try (InputStream stream = resource("cosmetics/catalog.json")) {
            catalog = JSON.readTree(stream);
        }
        int count = 0;
        for (JsonNode item : catalog.path("items")) {
            String id = item.path("id").asText();
            if (!V1.contains(id)) {
                continue;
            }
            count++;
            String modelPath = item.path("model").asText();
            String texturePath = item.path("texture").asText();
            assertEquals("hat", item.path("slot").asText(), id);
            if ("quarkos:jack_o_lantern_mask".equals(id)) {
                assertArmorMask(modelPath, texturePath);
                continue;
            }
            if (id.equals("quarkos:scarecrow_straw_hat") || id.equals("quarkos:candle_skull_crown") || id.equals("quarkos:ghost_buddy")) {
                assertBlockbench(id, modelPath, texturePath);
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
                assertEquals(32, image.getWidth(), texturePath);
                assertEquals(32, image.getHeight(), texturePath);
            }
        }
        assertEquals(5, count);
    }

    /** Copied Blockbench file. The texture is the handoff png, not a redraw. */
    private static void assertBlockbench(String id, String modelPath, String texturePath) throws Exception {
        assertTrue(modelPath.endsWith(".bbmodel"), modelPath);
        JsonNode model;
        try (InputStream stream = resource(modelPath)) {
            model = JSON.readTree(stream);
        }
        assertEquals("geckolib_model", model.path("meta").path("model_format").asText(), modelPath);
        assertTrue(model.path("elements").size() >= 6, modelPath);
        try (InputStream stream = resource(texturePath)) {
            BufferedImage image = ImageIO.read(stream);
            assertEquals(64, image.getWidth(), texturePath);
            assertEquals(64, image.getHeight(), texturePath);
        }
        if ("quarkos:candle_skull_crown".equals(id)) {
            assertEquals("animation.candle_skull_crown.flicker", model.path("animations").path(0).path("name").asText());
            try (InputStream stream = resource("textures/cosmetics/candle_skull_crown_glow.png")) {
                BufferedImage image = ImageIO.read(stream);
                assertEquals(64, image.getWidth());
            }
        }
        if ("quarkos:ghost_buddy".equals(id)) {
            assertEquals("animation.ghost_buddy.idle", model.path("animations").path(0).path("name").asText());
            assertEquals("bipedBody", model.path("outliner").path(0).path("name").asText());
            try (InputStream stream = resource("textures/cosmetics/ghost_buddy_glow.png")) {
                BufferedImage image = ImageIO.read(stream);
                assertEquals(64, image.getWidth());
            }
        }
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
