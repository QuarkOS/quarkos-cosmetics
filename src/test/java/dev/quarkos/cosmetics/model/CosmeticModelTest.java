package dev.quarkos.cosmetics.model;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmeticModelTest {
    @Test
    void parsesEmbeddedPngAndBuildsAPublishBodyWithoutSecrets() throws Exception {
        CosmeticModel model = CosmeticModel.parse(sample(png()));
        assertEquals(CosmeticModel.Motion.FLAP, CosmeticModel.motion(model.elements.get(0).name()));
        assertEquals(8, model.textures.get(0).width());
        String body = model.publishBody("Lantern cap", "Paper.", Slot.HAT, "hat", png());
        assertTrue(body.contains("\"format\":\"cosmetics-model-1\""));
        assertTrue(body.contains("\"slot\":\"hat\""));
        assertFalse(body.contains("accessToken"));
        assertFalse(body.contains("http://"));
    }

    @Test
    void rejectsRemoteTexturesAndOutOfRangeCubes() {
        assertThrows(ModelException.class, () -> CosmeticModel.parse("""
                {"format":"cosmetics-model-1","textures":[{"name":"x","png":"https://example.test/a.png"}],"elements":[{"from":[0,0,0],"to":[1,1,1]}]}
                """));
        assertThrows(ModelException.class, () -> CosmeticModel.parse("""
                {"format":"cosmetics-model-1","elements":[{"from":[0,0,0],"to":[80,1,1]}]}
                """));
    }

    private static String sample(byte[] png) {
        String b64 = Base64.getEncoder().encodeToString(png);
        return """
                {"format":"cosmetics-model-1","textures":[{"name":"cloth","png":"data:image/png;base64,%s"}],"elements":[{"name":"wing_left_flap","from":[0,0,0],"to":[8,8,1],"faces":{"north":{"uv":[0,0,8,8],"texture":0}}}]}
                """.formatted(b64);
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(1, 1, 0xFFCC44FF);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
