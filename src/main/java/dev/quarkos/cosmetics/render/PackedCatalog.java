package dev.quarkos.cosmetics.render;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import dev.quarkos.cosmetics.model.CosmeticModel;
import dev.quarkos.cosmetics.model.ModelException;
import dev.quarkos.cosmetics.model.Slot;
import dev.quarkos.cosmetics.render.BlockbenchModel.Vec;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reads the catalog and its assets through the same parser in the client and in tests. */
final class PackedCatalog {
    static final String PATH = "cosmetics/catalog.json";
    static final String KITE_WINGS = "quarkos:kite_wings";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final CosmeticModel EMPTY_MESH = new CosmeticModel(List.of(), List.of());
    private static final Set<String> LEGACY_BLOCKBENCH = Set.of(
            "quarkos:scarecrow_straw_hat", "quarkos:candle_skull_crown", "quarkos:ghost_buddy");

    @FunctionalInterface
    interface Assets {
        InputStream open(String path) throws IOException;
    }

    record Entry(String id, String name, String description, Slot slot, String model, int marker,
                 Vec offset, boolean legacyBlockbench) {
        boolean blockato() {
            return model.startsWith("cosmetics/blockato/") || KITE_WINGS.equals(id);
        }
    }

    record Texture(String path, byte[] png, TextureFrames frames) {
    }

    record Item(Entry entry, CosmeticModel packed, BlockbenchModel blockbench, Texture base, Texture glow,
                int cubes, String animation) {
    }

    record Result(String id, String slot, Item item, String error) {
    }

    static List<Result> load(Assets assets) throws IOException {
        JsonNode items = tree(assets, PATH).path("items");
        if (!items.isArray() || items.isEmpty()) {
            throw new IOException("catalog must contain items");
        }
        Set<String> ids = new HashSet<>();
        List<Result> results = new ArrayList<>();
        for (JsonNode node : items) {
            String id = node.path("id").asText("<missing>");
            String slot = node.path("slot").asText("<missing>");
            try {
                if (!id.matches("quarkos:[a-z0-9_]+") || !ids.add(id)) {
                    throw new IllegalArgumentException("invalid or duplicate id " + id);
                }
                Item item = loadItem(assets, node, id, slot);
                results.add(new Result(id, item.entry.slot.wire, item, null));
            } catch (IOException | ModelException | RuntimeException ex) {
                results.add(new Result(id, slot, null, ex.toString()));
            }
        }
        return List.copyOf(results);
    }

    private static Item loadItem(Assets assets, JsonNode node, String id, String slotName) throws IOException, ModelException {
        Slot slot = Slot.parse(slotName).orElseThrow(() -> new IllegalArgumentException("unknown slot " + slotName));
        String modelPath = required(node, "model");
        String texturePath = required(node, "texture");
        String marker = required(node, "marker");
        if (!marker.matches("[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("marker must be a six-digit RGB colour");
        }
        Entry entry = new Entry(id, required(node, "name"), required(node, "description"), slot,
                modelPath, Integer.parseInt(marker, 16), Vec.read(node.path("model_offset"), Vec.ZERO),
                LEGACY_BLOCKBENCH.contains(id));
        byte[] modelBytes = bytes(assets, modelPath);
        JsonNode root = JSON.readTree(modelBytes);
        byte[] png = bytes(assets, texturePath);
        BufferedImage texture = image(png, texturePath);
        if (modelPath.endsWith(".bbmodel")) {
            int width = root.path("resolution").path("width").asInt();
            int height = root.path("resolution").path("height").asInt();
            TextureFrames frames = frames(assets, texturePath, texture, width, height);
            String glowPath = node.path("glow").asText(legacyGlow(id, texturePath));
            byte[] glowPng = glowPath == null ? null : bytes(assets, glowPath);
            BufferedImage glow = glowPng == null ? null : image(glowPng, glowPath);
            TextureFrames glowFrames = glow == null ? null : frames(assets, glowPath, glow, width, height);
            BlockbenchModel model = BlockbenchModel.read(root, texture, frames, glow, glowFrames, entry.legacyBlockbench);
            String animation = String.join("+", model.idle().stream().map(BlockbenchModel.Clip::name).toList());
            return new Item(entry, EMPTY_MESH, model, new Texture(texturePath, png, frames),
                    glow == null ? null : new Texture(glowPath, glowPng, glowFrames),
                    model.cubes(), animation.isEmpty() ? "none" : animation);
        }

        CosmeticModel packed = root.has("minecraft:geometry") ? EMPTY_MESH
                : CosmeticModel.parse(new String(modelBytes, StandardCharsets.UTF_8));
        JsonNode geo = root;
        String animation = "none";
        if ("quarkos:chick".equals(id)) {
            geo = tree(assets, "geckolib/models/chick.geo.json");
            animation = animation(assets, "geckolib/animations/chick.animation.json", "animation.chick.sit");
        } else if (KITE_WINGS.equals(id)) {
            animation = animation(assets, "geckolib/animations/wings.animation.json", "idle");
        } else if ("quarkos:jack_o_lantern_mask".equals(id)) {
            image(bytes(assets, legacyGlow(id, texturePath)), legacyGlow(id, texturePath));
        }
        int cubes = geo.has("minecraft:geometry") ? geoCubes(geo) : packed.elements.size();
        JsonNode dimensions = geo.path("minecraft:geometry").path(0).path("description");
        TextureFrames frames = frames(assets, texturePath, texture,
                dimensions.path("texture_width").asInt(texture.getWidth()),
                dimensions.path("texture_height").asInt(texture.getHeight()));
        return new Item(entry, packed, null, new Texture(texturePath, png, frames), null, cubes, animation);
    }

    private static String legacyGlow(String id, String texture) {
        return switch (id) {
            case "quarkos:candle_skull_crown", "quarkos:ghost_buddy", "quarkos:jack_o_lantern_mask" ->
                    texture.substring(0, texture.length() - ".png".length()) + "_glow.png";
            default -> null;
        };
    }

    private static String animation(Assets assets, String path, String name) throws IOException {
        JsonNode clip = tree(assets, path).path("animations").path(name);
        if (!clip.path("bones").isObject() || clip.path("bones").isEmpty()) {
            throw new IOException(path + " has no clip " + name);
        }
        return name;
    }

    private static int geoCubes(JsonNode root) {
        int cubes = 0;
        for (JsonNode geo : root.path("minecraft:geometry")) {
            JsonNode description = geo.path("description");
            if (description.path("texture_width").asInt() <= 0 || description.path("texture_height").asInt() <= 0) {
                throw new IllegalArgumentException("missing geometry texture dimensions");
            }
            for (JsonNode bone : geo.path("bones")) {
                required(bone, "name");
                for (JsonNode cube : bone.path("cubes")) {
                    Vec.read(cube.path("origin"), null);
                    Vec.read(cube.path("size"), null);
                    if (!cube.hasNonNull("origin") || !cube.hasNonNull("size")) {
                        throw new IllegalArgumentException("missing geometry cube bounds");
                    }
                    cubes++;
                }
            }
        }
        if (cubes == 0) {
            throw new IllegalArgumentException("geometry has no cubes");
        }
        return cubes;
    }

    private static TextureFrames frames(Assets assets, String path, BufferedImage image, int width, int height) throws IOException {
        try (InputStream stream = assets.open(path + ".mcmeta")) {
            JsonNode animation = stream == null ? MissingNode.getInstance() : JSON.readTree(stream).path("animation");
            return TextureFrames.read(image, width, height, animation);
        }
    }

    private static BufferedImage image(byte[] bytes, String path) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            throw new IOException("invalid texture " + path);
        }
        return image;
    }

    private static JsonNode tree(Assets assets, String path) throws IOException {
        return JSON.readTree(bytes(assets, path));
    }

    private static byte[] bytes(Assets assets, String path) throws IOException {
        try (InputStream stream = assets.open(path)) {
            if (stream == null) {
                throw new FileNotFoundException(path);
            }
            return stream.readAllBytes();
        }
    }

    private static String required(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            throw new IllegalArgumentException("missing " + field);
        }
        return value;
    }

    private PackedCatalog() {
    }
}
