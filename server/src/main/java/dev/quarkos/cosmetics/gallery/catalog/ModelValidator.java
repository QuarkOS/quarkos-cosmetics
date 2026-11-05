package dev.quarkos.cosmetics.gallery.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import dev.quarkos.cosmetics.gallery.ApiException;
import dev.quarkos.cosmetics.gallery.GalleryConfig.Limits;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Checks a player-supplied cosmetic model. The format is ours: cubes and embedded PNGs.
 * It is not a Figura avatar, a .bbmodel script, or another host's catalog entry.
 */
public final class ModelValidator {
    public static final String FORMAT = "cosmetics-model-1";
    private static final Set<String> MODEL_KEYS = Set.of("format", "elements", "textures");
    private static final Set<String> ELEMENT_KEYS = Set.of("name", "from", "to", "rotation", "origin", "faces");
    private static final Set<String> FACE_KEYS = Set.of("uv", "texture");
    private static final Set<String> FACES = Set.of("north", "south", "east", "west", "up", "down");
    private static final Set<String> TEXTURE_KEYS = Set.of("name", "png");
    private static final byte[] PNG_SIGNATURE = new byte[]{
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    private final Limits limits;

    public ModelValidator(Limits limits) {
        this.limits = limits;
    }

    public Counts validate(JsonNode model) {
        List<ApiException.Violation> violations = new ArrayList<>();
        if (model == null || !model.isObject()) {
            throw ApiException.validation(List.of(new ApiException.Violation("model", "Model must be a JSON object.")));
        }
        model.fieldNames().forEachRemaining(key -> {
            if (!MODEL_KEYS.contains(key)) {
                violations.add(new ApiException.Violation("model." + key, "Unknown field."));
            }
        });
        JsonNode format = model.get("format");
        if (format == null || !format.isTextual() || !FORMAT.equals(format.asText())) {
            violations.add(new ApiException.Violation("model.format", "format must be \"" + FORMAT + "\"."));
        }
        JsonNode elements = model.get("elements");
        if (elements == null || !elements.isArray()) {
            violations.add(new ApiException.Violation("model.elements", "elements must be an array of cubes."));
            throwIfAny(violations);
            return new Counts(0, 0);
        }
        if (elements.isEmpty()) {
            violations.add(new ApiException.Violation("model.elements", "A cosmetic needs at least one cube."));
        }
        if (elements.size() > limits.maxElements()) {
            violations.add(new ApiException.Violation("model.elements",
                    elements.size() + " cubes exceeds the cap of " + limits.maxElements() + "."));
            throwIfAny(violations);
            return new Counts(elements.size(), 0);
        }
        int textures = validateTextures(model.get("textures"), violations);
        int triangles = 0;
        for (int i = 0; i < elements.size(); i++) {
            triangles += validateElement(elements.get(i), i, textures, violations);
        }
        if (triangles > limits.maxTriangles()) {
            violations.add(new ApiException.Violation("model.elements",
                    triangles + " triangles exceeds the cap of " + limits.maxTriangles() + "."));
        }
        throwIfAny(violations);
        return new Counts(elements.size(), triangles);
    }

    private int validateTextures(JsonNode textures, List<ApiException.Violation> violations) {
        if (textures == null) {
            return 0;
        }
        if (!textures.isArray()) {
            violations.add(new ApiException.Violation("model.textures", "textures must be an array."));
            return 0;
        }
        if (textures.size() > limits.maxTextures()) {
            violations.add(new ApiException.Violation("model.textures",
                    textures.size() + " textures exceeds the cap of " + limits.maxTextures() + "."));
            return textures.size();
        }
        for (int i = 0; i < textures.size(); i++) {
            validateTexture(textures.get(i), i, violations);
        }
        return textures.size();
    }

    private void validateTexture(JsonNode texture, int index, List<ApiException.Violation> violations) {
        String path = "model.textures[" + index + "]";
        if (texture == null || !texture.isObject()) {
            violations.add(new ApiException.Violation(path, "A texture must be an object with name and png."));
            return;
        }
        texture.fieldNames().forEachRemaining(key -> {
            if (!TEXTURE_KEYS.contains(key)) {
                violations.add(new ApiException.Violation(path + "." + key, "Unknown field."));
            }
        });
        JsonNode name = texture.get("name");
        if (name == null || !name.isTextual() || !name.asText().matches("[A-Za-z0-9_\\-]{1,32}")) {
            violations.add(new ApiException.Violation(path + ".name", "Texture name must be 1 to 32 letters, digits, underscores, or hyphens."));
        }
        JsonNode png = texture.get("png");
        if (png == null || !png.isTextual()) {
            violations.add(new ApiException.Violation(path + ".png", "Embed a PNG as base64. This service does not fetch texture URLs."));
            return;
        }
        String source = png.asText();
        if (source.startsWith("http://") || source.startsWith("https://") || source.startsWith("data:") && !source.toLowerCase(Locale.ROOT).startsWith("data:image/png;base64,")) {
            violations.add(new ApiException.Violation(path + ".png", "Only an embedded image/png base64 payload is accepted."));
            return;
        }
        String payload = source;
        if (source.toLowerCase(Locale.ROOT).startsWith("data:image/png;base64,")) {
            payload = source.substring(source.indexOf(',') + 1);
        }
        byte[] decoded;
        try {
            decoded = Base64.getMimeDecoder().decode(payload.replace("\n", "").replace("\r", ""));
        } catch (IllegalArgumentException ex) {
            violations.add(new ApiException.Violation(path + ".png", "PNG base64 could not be decoded."));
            return;
        }
        if (decoded.length == 0 || decoded.length > limits.maxTextureBytes()) {
            violations.add(new ApiException.Violation(path + ".png",
                    "Each PNG must be between 1 and " + limits.maxTextureBytes() + " bytes."));
            return;
        }
        try {
            checkPng(decoded);
        } catch (IllegalArgumentException ex) {
            violations.add(new ApiException.Violation(path + ".png", ex.getMessage()));
        }
    }

    private void checkPng(byte[] png) {
        if (png.length < 29) {
            throw new IllegalArgumentException("PNG is truncated.");
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (png[i] != PNG_SIGNATURE[i]) {
                throw new IllegalArgumentException("Texture is not a PNG.");
            }
        }
        int width = readInt(png, 16);
        int height = readInt(png, 20);
        int bitDepth = png[24] & 0xff;
        int colorType = png[25] & 0xff;
        if (width < 1 || height < 1 || width > limits.maxTextureEdge() || height > limits.maxTextureEdge()) {
            throw new IllegalArgumentException("PNG edge must be from 1 to " + limits.maxTextureEdge() + " pixels.");
        }
        if (bitDepth != 8 || (colorType != 2 && colorType != 3 && colorType != 6)) {
            throw new IllegalArgumentException("PNG must be 8-bit RGB, indexed, or RGBA.");
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(png)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null || image.getWidth() != width || image.getHeight() != height) {
                throw new IllegalArgumentException("PNG could not be decoded.");
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("PNG could not be decoded.");
        }
    }

    private int validateElement(JsonNode element, int index, int textureCount, List<ApiException.Violation> violations) {
        String path = "model.elements[" + index + "]";
        if (element == null || !element.isObject()) {
            violations.add(new ApiException.Violation(path, "Each element must be a cube object."));
            return 0;
        }
        element.fieldNames().forEachRemaining(key -> {
            if (!ELEMENT_KEYS.contains(key)) {
                violations.add(new ApiException.Violation(path + "." + key, "Unknown field."));
            }
        });
        if (element.has("name")) {
            JsonNode name = element.get("name");
            if (!name.isTextual() || !name.asText().matches("[A-Za-z0-9_ \\-]{1,32}")) {
                violations.add(new ApiException.Violation(path + ".name", "Cube name must be 1 to 32 letters, digits, spaces, underscores, or hyphens."));
            }
        }
        double[] from = vector(element.get("from"), path + ".from", true, violations);
        double[] to = vector(element.get("to"), path + ".to", true, violations);
        if (from != null && to != null) {
            boolean point = Math.abs(to[0] - from[0]) < 1e-6
                    && Math.abs(to[1] - from[1]) < 1e-6
                    && Math.abs(to[2] - from[2]) < 1e-6;
            if (point) {
                violations.add(new ApiException.Violation(path, "A cube needs extent on at least one axis."));
            }
        }
        if (element.has("rotation")) {
            vector(element.get("rotation"), path + ".rotation", false, violations);
        }
        if (element.has("origin")) {
            vector(element.get("origin"), path + ".origin", true, violations);
        }
        return validateFaces(element.get("faces"), path + ".faces", textureCount, violations);
    }

    private int validateFaces(JsonNode faces, String path, int textureCount, List<ApiException.Violation> violations) {
        if (faces == null) {
            return 12;
        }
        if (!faces.isObject()) {
            violations.add(new ApiException.Violation(path, "faces must be an object."));
            return 0;
        }
        int count = 0;
        List<String> faceNames = new ArrayList<>();
        faces.fieldNames().forEachRemaining(faceNames::add);
        for (String face : faceNames) {
            if (!FACES.contains(face)) {
                violations.add(new ApiException.Violation(path + "." + face, "Face must be north, south, east, west, up, or down."));
                continue;
            }
            count++;
            JsonNode value = faces.get(face);
            if (value == null || !value.isObject()) {
                violations.add(new ApiException.Violation(path + "." + face, "A face must be an object."));
                continue;
            }
            value.fieldNames().forEachRemaining(key -> {
                if (!FACE_KEYS.contains(key)) {
                    violations.add(new ApiException.Violation(path + "." + face + "." + key, "Unknown field."));
                }
            });
            if (value.has("uv")) {
                uv(value.get("uv"), path + "." + face + ".uv", violations);
            }
            if (value.has("texture")) {
                JsonNode texture = value.get("texture");
                if (!texture.isIntegralNumber()) {
                    violations.add(new ApiException.Violation(path + "." + face + ".texture", "texture is an index into textures."));
                } else {
                    int textureIndex = texture.intValue();
                    if (textureIndex < 0 || textureIndex >= textureCount) {
                        violations.add(new ApiException.Violation(path + "." + face + ".texture", "texture index is out of range."));
                    }
                }
            }
        }
        if (count == 0) {
            return 12;
        }
        return count * 2;
    }

    private double[] vector(JsonNode node, String path, boolean position, List<ApiException.Violation> violations) {
        if (node == null || !node.isArray() || node.size() != 3) {
            violations.add(new ApiException.Violation(path, "Expected three numbers."));
            return null;
        }
        double[] values = new double[3];
        for (int i = 0; i < 3; i++) {
            JsonNode component = node.get(i);
            if (component == null || !component.isNumber() || !Double.isFinite(component.doubleValue())) {
                violations.add(new ApiException.Violation(path, "Expected three finite numbers."));
                return null;
            }
            double value = component.doubleValue();
            if (position && (value < -64 || value > 64)) {
                violations.add(new ApiException.Violation(path, "Coordinates must stay within -64 and 64."));
                return null;
            }
            if (!position && (value < -360 || value > 360)) {
                violations.add(new ApiException.Violation(path, "Rotation must stay within -360 and 360 degrees."));
                return null;
            }
            values[i] = value;
        }
        return values;
    }

    private void uv(JsonNode node, String path, List<ApiException.Violation> violations) {
        if (node == null || !node.isArray() || node.size() != 4) {
            violations.add(new ApiException.Violation(path, "uv must be four numbers."));
            return;
        }
        for (int i = 0; i < 4; i++) {
            JsonNode component = node.get(i);
            if (component == null || !component.isNumber() || !Double.isFinite(component.doubleValue())) {
                violations.add(new ApiException.Violation(path, "uv must be four finite numbers."));
                return;
            }
            double value = component.doubleValue();
            if (value < -64 || value > 4096) {
                violations.add(new ApiException.Violation(path, "uv values must stay within -64 and 4096."));
                return;
            }
        }
    }

    private static void throwIfAny(List<ApiException.Violation> violations) {
        if (!violations.isEmpty()) {
            throw ApiException.validation(violations);
        }
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
                | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 2] & 0xff) << 8)
                | (bytes[offset + 3] & 0xff);
    }

    public record Counts(int elements, int triangles) {
    }
}
