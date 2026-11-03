package dev.quarkos.cosmetics.render;

import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Emissive candle layer. Eyes skips the lightmap and the sun tint, so the candle
 * stays at the texture's own brightness after dark. The translucent emissive type
 * still multiplies by the directional light and goes dim at midnight.
 */
public final class MaskGlow {
    private MaskGlow() {
    }

    /** Present so client setup can touch the class before the first frame. */
    public static void boot() {
    }

    //? if >=1.21.11 {
    private static final Map<Identifier, net.minecraft.client.renderer.rendertype.RenderType> TYPES = new ConcurrentHashMap<>();

    public static net.minecraft.client.renderer.rendertype.RenderType type(Identifier texture) {
        return TYPES.computeIfAbsent(texture, MaskGlow::create);
    }

    private static net.minecraft.client.renderer.rendertype.RenderType create(Identifier texture) {
        return net.minecraft.client.renderer.rendertype.RenderTypes.eyes(texture);
    }
    //?} else {
    private static final Map<Identifier, net.minecraft.client.renderer.RenderType> TYPES = new ConcurrentHashMap<>();

    public static net.minecraft.client.renderer.RenderType type(Identifier texture) {
        return TYPES.computeIfAbsent(texture, MaskGlow::create);
    }

    private static net.minecraft.client.renderer.RenderType create(Identifier texture) {
        return net.minecraft.client.renderer.RenderType.eyes(texture);
    }
    //?}
}
