package dev.quarkos.cosmetics.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.Optional;

/** Reads a texture into ARGB. */
public final class SkinImages {
    public record Pixels(int width, int height, int[] argb) {
    }

    //? if <1.21.11 {
    /*public interface LegacyTexture {
        Pixels quarkos$skinPixels();
    }
    *///?}

    private SkinImages() {
    }

    public static Pixels read(Identifier location) {
        if (location == null) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        var texture = minecraft.getTextureManager().getTexture(location);
        //? if <1.21.11 {
        /*if (texture instanceof LegacyTexture legacy) {
            return legacy.quarkos$skinPixels();
        }
        *///?}
        if (texture instanceof DynamicTexture dynamic && dynamic.getPixels() != null) {
            return from(dynamic.getPixels());
        }
        Optional<Resource> resource = minecraft.getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            return null;
        }
        try (InputStream stream = resource.get().open()) {
            NativeImage image = NativeImage.read(stream);
            try {
                return from(image);
            } finally {
                image.close();
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Pixels from(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] argb = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                //? if >=1.21.11 {
                argb[y * width + x] = image.getPixel(x, y);
                //?} else {
                /*int pixel = image.getPixelRGBA(x, y);
                int alpha = (pixel >>> 24) & 255;
                int blue = (pixel >>> 16) & 255;
                int green = (pixel >>> 8) & 255;
                int red = pixel & 255;
                argb[y * width + x] = (alpha << 24) | (red << 16) | (green << 8) | blue;
                *///?}
            }
        }
        return new Pixels(width, height, argb);
    }
}
