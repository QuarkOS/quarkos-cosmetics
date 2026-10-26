package dev.quarkos.cosmetics.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * cosmetics-model-1, the gallery's cube format. Element names ending in
 * {@code _flap} or {@code _idle} are animated by this client. The gallery
 * rejects unknown fields, so animation is not a separate JSON property.
 */
public final class CosmeticModel {
    public static final String FORMAT = "cosmetics-model-1";
    public static final int MAX_ELEMENTS = 64;
    public static final int MAX_TRIANGLES = 768;
    public static final int MAX_TEXTURES = 4;
    public static final int MAX_EDGE = 128;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String[] FACES = {"north", "south", "east", "west", "up", "down"};

    public final List<Texture> textures;
    public final List<Element> elements;

    public CosmeticModel(List<Texture> textures, List<Element> elements) {
        this.textures = List.copyOf(textures);
        this.elements = List.copyOf(elements);
    }

    public static CosmeticModel parse(String json) throws ModelException {
        final JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (IOException ex) {
            throw new ModelException("model", "The model is not JSON.");
        }
        if (root == null || !root.isObject()) {
            throw new ModelException("model", "The model must be an object.");
        }
        if (!FORMAT.equals(text(root, "format"))) {
            throw new ModelException("model.format", "format must be cosmetics-model-1.");
        }
        List<Texture> textures = new ArrayList<>();
        JsonNode textureNode = root.get("textures");
        if (textureNode != null && !textureNode.isNull()) {
            if (!textureNode.isArray()) {
                throw new ModelException("model.textures", "textures must be an array.");
            }
            if (textureNode.size() > MAX_TEXTURES) {
                throw new ModelException("model.textures", "At most 4 textures.");
            }
            for (int i = 0; i < textureNode.size(); i++) {
                textures.add(readTexture(textureNode.get(i), i));
            }
        }
        JsonNode elementNode = root.get("elements");
        if (elementNode == null || !elementNode.isArray() || elementNode.isEmpty()) {
            throw new ModelException("model.elements", "At least one cube is required.");
        }
        if (elementNode.size() > MAX_ELEMENTS) {
            throw new ModelException("model.elements", "At most 64 cubes.");
        }
        List<Element> elements = new ArrayList<>();
        int triangles = 0;
        for (int i = 0; i < elementNode.size(); i++) {
            Element element = readElement(elementNode.get(i), i, textures.size());
            elements.add(element);
            triangles += element.faces.isEmpty() ? 12 : element.faces.size() * 2;
        }
        if (triangles > MAX_TRIANGLES) {
            throw new ModelException("model.elements", triangles + " triangles exceeds the cap of 768.");
        }
        return new CosmeticModel(textures, elements);
    }

    public String publishBody(String name, String description, Slot slot, String textureName, byte[] png) throws ModelException {
        if (name == null || name.isBlank() || name.length() > 48) {
            throw new ModelException("name", "Name must be 1 to 48 characters.");
        }
        if (description != null && description.length() > 280) {
            throw new ModelException("description", "Description must be at most 280 characters.");
        }
        if (png == null || png.length == 0 || png.length > 65536) {
            throw new ModelException("model.textures", "The PNG must be present and at most 64 KiB.");
        }
        ObjectNode root = JSON.createObjectNode();
        root.put("name", name);
        root.put("description", description == null ? "" : description);
        root.put("slot", slot.wire);
        ObjectNode model = root.putObject("model");
        model.put("format", FORMAT);
        ArrayNode textures = model.putArray("textures");
        ObjectNode texture = textures.addObject();
        texture.put("name", textureName);
        texture.put("png", "data:image/png;base64," + Base64.getEncoder().encodeToString(png));
        ArrayNode elements = model.putArray("elements");
        for (Element element : this.elements) {
            ObjectNode node = elements.addObject();
            node.put("name", element.name);
            putVec(node.putArray("from"), element.from);
            putVec(node.putArray("to"), element.to);
            putVec(node.putArray("origin"), element.origin);
            putVec(node.putArray("rotation"), element.rotation);
            ObjectNode faces = node.putObject("faces");
            for (var entry : element.faces.entrySet()) {
                ObjectNode face = faces.putObject(entry.getKey());
                ArrayNode uv = face.putArray("uv");
                for (float value : entry.getValue().uv) {
                    uv.add(value);
                }
                face.put("texture", entry.getValue().texture);
            }
        }
        return root.toString();
    }

    private static void putVec(ArrayNode array, float[] values) {
        for (float value : values) {
            array.add(value);
        }
    }

    private static Texture readTexture(JsonNode node, int index) throws ModelException {
        String path = "model.textures[" + index + "]";
        if (node == null || !node.isObject()) {
            throw new ModelException(path, "A texture must be an object.");
        }
        String name = text(node, "name");
        if (name == null || name.isBlank()) {
            throw new ModelException(path + ".name", "A texture needs a name.");
        }
        String png = text(node, "png");
        if (png == null) {
            throw new ModelException(path + ".png", "A texture needs an embedded PNG.");
        }
        String lower = png.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            throw new ModelException(path + ".png", "Remote texture URLs are rejected.");
        }
        String base64 = png;
        int comma = png.indexOf(',');
        if (lower.startsWith("data:image/png;base64,") && comma >= 0) {
            base64 = png.substring(comma + 1);
        }
        final byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new ModelException(path + ".png", "The PNG is not valid base64.");
        }
        if (bytes.length > 65536) {
            throw new ModelException(path + ".png", "The PNG is larger than 64 KiB.");
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new ModelException(path + ".png", "The texture is not a PNG.");
            }
            if (image.getWidth() > MAX_EDGE || image.getHeight() > MAX_EDGE) {
                throw new ModelException(path + ".png", "Textures must be at most 128 pixels on an edge.");
            }
            return new Texture(name, bytes, image.getWidth(), image.getHeight());
        } catch (IOException ex) {
            throw new ModelException(path + ".png", "The texture is not a PNG.");
        }
    }

    private static Element readElement(JsonNode node, int index, int textureCount) throws ModelException {
        String path = "model.elements[" + index + "]";
        if (node == null || !node.isObject()) {
            throw new ModelException(path, "A cube must be an object.");
        }
        float[] from = vec(node.get("from"), path + ".from");
        float[] to = vec(node.get("to"), path + ".to");
        if (from[0] == to[0] && from[1] == to[1] && from[2] == to[2]) {
            throw new ModelException(path, "A cube must have extent on one axis.");
        }
        float[] origin = node.has("origin") ? vec(node.get("origin"), path + ".origin") : midpoint(from, to);
        float[] rotation = node.has("rotation") ? vec(node.get("rotation"), path + ".rotation") : new float[]{0, 0, 0};
        String name = text(node, "name");
        if (name == null) {
            name = "cube_" + index;
        }
        Map<String, Face> faces = new LinkedHashMap<>();
        JsonNode faceNode = node.get("faces");
        if (faceNode != null && !faceNode.isNull()) {
            if (!faceNode.isObject()) {
                throw new ModelException(path + ".faces", "faces must be an object.");
            }
            for (String face : FACES) {
                JsonNode one = faceNode.get(face);
                if (one == null) {
                    continue;
                }
                float[] uv = new float[]{0, 0, 16, 16};
                if (one.has("uv")) {
                    uv = uv(one.get("uv"), path + ".faces." + face + ".uv");
                }
                int texture = 0;
                if (one.has("texture")) {
                    texture = one.get("texture").asInt(-1);
                }
                if (textureCount > 0 && (texture < 0 || texture >= textureCount)) {
                    throw new ModelException(path + ".faces." + face + ".texture", "Texture index is out of range.");
                }
                faces.put(face, new Face(uv, texture));
            }
        }
        return new Element(name, from, to, origin, rotation, faces);
    }

    private static float[] midpoint(float[] from, float[] to) {
        return new float[]{(from[0] + to[0]) / 2f, (from[1] + to[1]) / 2f, (from[2] + to[2]) / 2f};
    }

    private static float[] vec(JsonNode node, String path) throws ModelException {
        if (node == null || !node.isArray() || node.size() != 3) {
            throw new ModelException(path, "Expected three numbers.");
        }
        float[] values = new float[3];
        for (int i = 0; i < 3; i++) {
            if (!node.get(i).isNumber()) {
                throw new ModelException(path, "Expected three numbers.");
            }
            values[i] = (float) node.get(i).asDouble();
            if (values[i] < -64 || values[i] > 64) {
                throw new ModelException(path, "Coordinates stay within -64 and 64.");
            }
        }
        return values;
    }

    private static float[] uv(JsonNode node, String path) throws ModelException {
        if (node == null || !node.isArray() || node.size() != 4) {
            throw new ModelException(path, "UV needs four numbers.");
        }
        float[] values = new float[4];
        for (int i = 0; i < 4; i++) {
            values[i] = (float) node.get(i).asDouble();
        }
        return values;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    public enum Motion {
        NONE, FLAP, IDLE
    }

    public static Motion motion(String name) {
        if (name == null) {
            return Motion.NONE;
        }
        String key = name.toLowerCase(Locale.ROOT);
        if (key.contains("flap")) {
            return Motion.FLAP;
        }
        if (key.contains("idle")) {
            return Motion.IDLE;
        }
        return Motion.NONE;
    }

    public record Texture(String name, byte[] png, int width, int height) {
    }

    public record Face(float[] uv, int texture) {
    }

    public record Element(String name, float[] from, float[] to, float[] origin, float[] rotation, Map<String, Face> faces) {
    }
}
