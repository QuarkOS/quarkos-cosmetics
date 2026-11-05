package dev.quarkos.cosmetics.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.quarkos.cosmetics.model.CosmeticModel;
import dev.quarkos.cosmetics.model.ModelException;
import dev.quarkos.cosmetics.model.Slot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ModelLibrary {
    private static final Map<String, Resolved> PACKED = new LinkedHashMap<>();
    private static final Map<String, Resolved> GALLERY = new LinkedHashMap<>();
    private static boolean loaded;

    private ModelLibrary() {
    }

    public record Resolved(String id, String name, String description, Slot slot, CosmeticModel model,
                           Identifier texture, int width, int height, byte[] png) {
    }

    public static List<Resolved> packed() {
        ensure();
        return List.copyOf(PACKED.values());
    }

    public static Resolved resolve(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        ensure();
        Resolved packed = PACKED.get(id);
        if (packed != null) {
            return packed;
        }
        synchronized (GALLERY) {
            return GALLERY.get(id);
        }
    }

    public static void remember(String id, String name, String description, Slot slot, CosmeticModel model) {
        if (model.textures.isEmpty()) {
            return;
        }
        CosmeticModel.Texture texture = model.textures.get(0);
        Identifier location = Identifier.fromNamespaceAndPath("quarkos_cosmetics", "textures/gallery/" + id + ".png");
        register(location, texture.png());
        Resolved resolved = new Resolved(id, name, description, slot, model, location, texture.width(), texture.height(), texture.png());
        synchronized (GALLERY) {
            GALLERY.put(id, resolved);
        }
    }

    private static void ensure() {
        if (loaded) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        loaded = true;
        ResourceManager resources = minecraft.getResourceManager();
        Identifier catalogId = Identifier.fromNamespaceAndPath("quarkos_cosmetics", "cosmetics/catalog.json");
        Optional<Resource> catalog = resources.getResource(catalogId);
        if (catalog.isEmpty()) {
            return;
        }
        try (InputStream stream = catalog.get().open()) {
            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(stream);
            for (com.fasterxml.jackson.databind.JsonNode item : root.path("items")) {
                loadItem(resources, item);
            }
        } catch (IOException ignored) {
            loaded = false;
        }
    }

    private static void loadItem(ResourceManager resources, com.fasterxml.jackson.databind.JsonNode item) {
        String id = item.path("id").asText("");
        String modelPath = item.path("model").asText("");
        String texturePath = item.path("texture").asText("");
        Slot slot = Slot.parse(item.path("slot").asText("")).orElse(null);
        if (id.isBlank() || slot == null) {
            return;
        }
        try {
            String json = new String(resources.getResource(Identifier.fromNamespaceAndPath("quarkos_cosmetics", modelPath)).orElseThrow().open().readAllBytes());
            com.fasterxml.jackson.databind.JsonNode modelNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            // Armor geo is drawn by JackOLanternMask. The gallery cube format cannot hold an 80×1920 strip.
            // Armor geo and Blockbench files are drawn by their own renderers.
            CosmeticModel model = modelNode.has("minecraft:geometry") || modelPath.endsWith(".bbmodel")
                    ? new CosmeticModel(List.of(), List.of())
                    : CosmeticModel.parse(json);
            byte[] png = resources.getResource(Identifier.fromNamespaceAndPath("quarkos_cosmetics", texturePath)).orElseThrow().open().readAllBytes();
            int width = 16;
            int height = 16;
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
            if (image != null) {
                width = image.getWidth();
                height = image.getHeight();
            }
            PACKED.put(id, new Resolved(
                    id,
                    item.path("name").asText(id),
                    item.path("description").asText(""),
                    slot,
                    model,
                    Identifier.fromNamespaceAndPath("quarkos_cosmetics", texturePath),
                    width,
                    height,
                    png));
        } catch (IOException | ModelException ignored) {
            // A broken packed file is skipped. The gallery can still publish other models.
        }
    }

    public static void register(Identifier location, byte[] png) {
        try {
            NativeImage image = NativeImage.read(new java.io.ByteArrayInputStream(png));
            //? if >=1.21.11 {
            DynamicTexture texture = new DynamicTexture(() -> location.toString(), image);
            //?} else {
            DynamicTexture texture = new DynamicTexture(image);
            //?}
            Minecraft.getInstance().getTextureManager().register(location, texture);
        } catch (IOException ignored) {
            // The cosmetic stays invisible until a valid PNG arrives.
        }
    }

    public static List<Resolved> packed(Slot slot) {
        List<Resolved> list = new ArrayList<>();
        for (Resolved resolved : packed()) {
            if (resolved.slot == slot) {
                list.add(resolved);
            } else if (Chick.ID.equals(resolved.id) && Chick.isPerch(slot)) {
                list.add(resolved);
            }
        }
        return list;
    }
}
