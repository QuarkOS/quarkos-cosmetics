package dev.quarkos.cosmetics.render;

import net.minecraft.resources.Identifier;

//? if <26.3 {
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
//?}

/**
 * 1.21.11 draws the rind and the candle in one mesh, after the world, into the main
 * color target. Candle vertices are marked with alpha 0; the mask shader samples the
 * glow strip and skips the lightmap. Everywhere else the candle is a full-bright
 * cutout of that strip. Eyes is a translucent pass, and on 1.21.11 it never lands in
 * the main color target, so the carved texels stay the rind's dark brown.
 */
public final class MaskGlow {
    private MaskGlow() {
    }

    /** Present so client setup can touch the class before the first frame. */
    public static void boot() {
    }

    //? if >=26.3 {
    /*public static net.minecraft.client.renderer.rendertype.RenderType type(Identifier texture) {
        return net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(texture);
    }
    *///?}
    //? if >=1.21.11 && <26.3 {
    private static final Map<Identifier, net.minecraft.client.renderer.rendertype.RenderType> TYPES = new ConcurrentHashMap<>();
    private static com.mojang.blaze3d.pipeline.RenderPipeline PIPELINE;
    private static net.minecraft.client.renderer.rendertype.RenderType COMBINED;

    public static net.minecraft.client.renderer.rendertype.RenderType type(Identifier texture) {
        return TYPES.computeIfAbsent(texture, MaskGlow::create);
    }

    /** Shell on Sampler0, glow strip on Sampler1, lightmap on Sampler2. One mesh. */
    public static net.minecraft.client.renderer.rendertype.RenderType combined(Identifier shell, Identifier glow) {
        if (COMBINED == null) {
            if (PIPELINE == null) {
                var pipeline = com.mojang.blaze3d.pipeline.RenderPipeline.builder(net.minecraft.client.renderer.RenderPipelines.MATRICES_FOG_SNIPPET)
                        .withLocation("quarkos_cosmetics/mask")
                        .withVertexShader("core/entity")
                        .withFragmentShader(net.minecraft.resources.Identifier.fromNamespaceAndPath("quarkos_cosmetics", "core/mask"))
                        .withShaderDefine("NO_OVERLAY")
                        // Same cardinal shade as the player. NO_CARDINAL_LIGHTING left the shell at full
                        // texture brightness in daylight, so the head sat above the kimono. Candle cores
                        // ignore vertex color in mask.fsh and stay full bright.
                        .withSampler("Sampler0")
                        .withSampler("Sampler1")
                        .withSampler("Sampler2")
                        .withUniform("Lighting", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
                        .withVertexFormat(com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
                        .build();
                //? if fabric {
                PIPELINE = net.minecraft.client.renderer.RenderPipelines.register(pipeline);
                //?} else {
                // Forge keeps register private. The public map is the whole method.
                net.minecraft.client.renderer.RenderPipelines.PIPELINES_BY_LOCATION.put(pipeline.getLocation(), pipeline);
                PIPELINE = pipeline;
                //?}
            }
            var setup = net.minecraft.client.renderer.rendertype.RenderSetup.builder(PIPELINE)
                    .withTexture("Sampler0", shell)
                    .withTexture("Sampler1", glow)
                    .useLightmap()
                    .bufferSize(1 << 20)
                    .createRenderSetup();
            COMBINED = net.minecraft.client.renderer.rendertype.RenderType.create("mask", setup);
        }
        return COMBINED;
    }

    private static net.minecraft.client.renderer.rendertype.RenderType create(Identifier texture) {
        return net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(texture);
    }
    //?}
    //? if <1.21.11 {
    /*private static final Map<Identifier, net.minecraft.client.renderer.RenderType> TYPES = new ConcurrentHashMap<>();

    public static net.minecraft.client.renderer.RenderType type(Identifier texture) {
        return TYPES.computeIfAbsent(texture, MaskGlow::create);
    }

    private static net.minecraft.client.renderer.RenderType create(Identifier texture) {
        return net.minecraft.client.renderer.RenderType.entityCutout(texture);
    }
    *///?}
}
