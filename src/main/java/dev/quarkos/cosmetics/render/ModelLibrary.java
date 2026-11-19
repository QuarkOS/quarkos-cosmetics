package dev.quarkos.cosmetics.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.quarkos.cosmetics.model.CosmeticModel;
import dev.quarkos.cosmetics.model.Slot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModelLibrary {
    private static final Logger LOGGER = LoggerFactory.getLogger("quarkos_cosmetics");
    private static final Map<String, Resolved> PACKED = new LinkedHashMap<>();
    private static final Map<String, Resolved> GALLERY = new LinkedHashMap<>();
    private static boolean loaded;
    private static List<PackedCatalog.Result> catalog = List.of();

    private ModelLibrary() {
    }

    public record Resolved(String id, String name, String description, Slot slot, CosmeticModel model,
                           Identifier texture, int width, int height, byte[] png, PackedCatalog.Item builtin) {
        public boolean blockato() {
            return builtin != null && builtin.entry().blockato();
        }

        boolean blockbench() {
            return builtin != null && builtin.blockbench() != null;
        }
    }

    public record ModelCheck(String id, String slot, boolean loaded, int cubes, String animation, String error) {
    }

    public static List<ModelCheck> modelChecks() {
        ensure();
        return catalog.stream().map(result -> {
            PackedCatalog.Item item = result.item();
            if (item == null) {
                return new ModelCheck(result.id(), result.slot(), false, 0, "none", result.error());
            }
            boolean ready = switch (result.id()) {
                case Chick.ID -> Chick.modelReady();
                case JackOLanternMask.ID -> JackOLanternMask.modelReady();
                case PackedCatalog.KITE_WINGS -> KiteWings.modelReady();
                default -> true;
            };
            return new ModelCheck(result.id(), result.slot(), ready, item.cubes(), item.animation(),
                    ready ? null : "renderer could not load the model");
        }).toList();
    }

    public static void clearPacked() {
        loaded = false;
        PACKED.clear();
        catalog = List.of();
    }

    static Identifier location(String path) {
        return Identifier.fromNamespaceAndPath("quarkos_cosmetics", path);
    }

    public static List<Resolved> packed() {
        ensure();
        return List.copyOf(PACKED.values());
    }

    public static Resolved packedById(String id) {
        ensure();
        return PACKED.get(id);
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
        Resolved resolved = new Resolved(id, name, description, slot, model, location, texture.width(), texture.height(), texture.png(), null);
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
        try {
            catalog = PackedCatalog.load(path -> {
                var resource = resources.getResource(location(path));
                return resource.isEmpty() ? null : resource.get().open();
            });
            for (PackedCatalog.Result result : catalog) {
                PackedCatalog.Item item = result.item();
                if (item == null) {
                    LOGGER.error("{} failed to load: {}", result.id(), result.error());
                    continue;
                }
                PackedCatalog.Entry entry = item.entry();
                PACKED.put(entry.id(), new Resolved(entry.id(), entry.name(), entry.description(), entry.slot(),
                        item.packed(), location(entry.texture()), item.frames().width(), item.frames().height(),
                        item.png(), item));
            }
        } catch (IOException | RuntimeException ex) {
            catalog = List.of(new PackedCatalog.Result("<catalog>", "none", null, ex.toString()));
            LOGGER.error("Could not load the cosmetics catalog", ex);
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
