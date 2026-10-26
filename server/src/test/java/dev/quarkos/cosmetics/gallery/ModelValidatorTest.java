package dev.quarkos.cosmetics.gallery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.quarkos.cosmetics.gallery.catalog.ModelValidator;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelValidatorTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ModelValidator validator = new ModelValidator(GalleryConfig.Limits.defaults());

    @Test
    void acceptsACubeAndCountsTriangles() throws Exception {
        ModelValidator.Counts counts = validator.validate(MAPPER.readTree("""
                {"format":"cosmetics-model-1","elements":[{"from":[0,0,0],"to":[4,1,4],"faces":{"up":{},"down":{}}}]}
                """));
        assertEquals(1, counts.elements());
        assertEquals(4, counts.triangles());
    }

    @Test
    void rejectsOversizedGeometryAndForeignFields() {
        ModelValidator tight = new ModelValidator(new GalleryConfig.Limits(262144, 64, 4, 4, 128, 65536, 24, 48));
        ApiException ex = assertThrows(ApiException.class, () -> tight.validate(MAPPER.readTree("""
                {"format":"cosmetics-model-1","elements":[{"from":[0,0,0],"to":[1,1,1]}]}
                """)));
        assertTrue(ex.body().toString().contains("triangles"));

        ApiException range = assertThrows(ApiException.class, () -> validator.validate(MAPPER.readTree("""
                {"format":"cosmetics-model-1","elements":[{"from":[0,0,0],"to":[80,1,1],"faces":{"up":{}}}]}
                """)));
        assertEquals(400, range.status());

        ApiException script = assertThrows(ApiException.class, () -> validator.validate(MAPPER.readTree("""
                {"format":"cosmetics-model-1","script":"print(1)","elements":[{"from":[0,0,0],"to":[1,1,1]}]}
                """)));
        assertTrue(script.violations().stream().anyMatch(v -> v.path().equals("model.script")));
    }

    @Test
    void checksEmbeddedPngsAndRejectsRemoteTextures() throws Exception {
        String png = Base64.getEncoder().encodeToString(tinyPng());
        ModelValidator.Counts counts = validator.validate(MAPPER.readTree("""
                {"format":"cosmetics-model-1","textures":[{"name":"hat","png":"data:image/png;base64,%s"}],
                "elements":[{"from":[0,0,0],"to":[1,1,1],"faces":{"north":{"texture":0}}}]}
                """.formatted(png)));
        assertEquals(2, counts.triangles());

        ApiException remote = assertThrows(ApiException.class, () -> validator.validate(MAPPER.readTree("""
                {"format":"cosmetics-model-1","textures":[{"name":"hat","png":"https://example.invalid/hat.png"}],
                "elements":[{"from":[0,0,0],"to":[1,1,1]}]}
                """)));
        assertTrue(remote.violations().stream().anyMatch(v -> v.path().endsWith(".png")));

        byte[] huge = new byte[29];
        huge[0] = (byte) 0x89;
        huge[1] = 0x50;
        huge[2] = 0x4e;
        huge[3] = 0x47;
        huge[4] = 0x0d;
        huge[5] = 0x0a;
        huge[6] = 0x1a;
        huge[7] = 0x0a;
        huge[16] = 0;
        huge[17] = 0;
        huge[18] = 0x27;
        huge[19] = 0x0f;
        huge[20] = 0;
        huge[21] = 0;
        huge[22] = 0;
        huge[23] = 1;
        huge[24] = 8;
        huge[25] = 2;
        String encoded = Base64.getEncoder().encodeToString(huge);
        ObjectNode model = (ObjectNode) MAPPER.readTree("""
                {"format":"cosmetics-model-1","textures":[{"name":"hat","png":""}],"elements":[{"from":[0,0,0],"to":[1,1,1]}]}
                """);
        ((ObjectNode) model.get("textures").get(0)).put("png", encoded);
        ApiException edge = assertThrows(ApiException.class, () -> validator.validate(model));
        assertTrue(edge.violations().stream().anyMatch(v -> v.message().contains("edge")));
    }

    private static byte[] tinyPng() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
