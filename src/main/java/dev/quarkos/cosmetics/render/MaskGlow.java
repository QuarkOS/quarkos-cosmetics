package dev.quarkos.cosmetics.render;

import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The rind and the candle share one draw. A second immediate draw was the pass
 * that vanished on most frames. Candle vertices are marked with alpha 0; the
 * mask shader samples the glow strip for those and skips the lightmap.
 */
public final class MaskGlow {
    private MaskGlow() {
    }

    /** Present so client setup can touch the class before the first frame. */
    public static void boot() {
    }

    //? if >=1.21.11 {
    private static final Map<Identifier, net.minecraft.client.renderer.rendertype.RenderType> TYPES = new ConcurrentHashMap<>();
    //? if fabric {
    private static com.mojang.blaze3d.pipeline.RenderPipeline PIPELINE;
    private static net.minecraft.client.renderer.rendertype.RenderType COMBINED;
    //?}

    public static net.minecraft.client.renderer.rendertype.RenderType type(Identifier texture) {
        return TYPES.computeIfAbsent(texture, MaskGlow::create);
    }

    //? if fabric {
    /** Shell on Sampler0, glow strip on Sampler1, lightmap on Sampler2. One mesh. */
    public static net.minecraft.client.renderer.rendertype.RenderType combined(Identifier shell, Identifier glow) {
        if (COMBINED == null) {
            if (PIPELINE == null) {
                PIPELINE = net.minecraft.client.renderer.RenderPipelines.register(com.mojang.blaze3d.pipeline.RenderPipeline.builder(net.minecraft.client.renderer.RenderPipelines.MATRICES_FOG_SNIPPET)
                        .withLocation("quarkos_cosmetics/mask")
                        .withVertexShader("core/entity")
                        .withFragmentShader(net.minecraft.resources.Identifier.fromNamespaceAndPath("quarkos_cosmetics", "core/mask"))
                        .withShaderDefine("NO_OVERLAY")
                        .withShaderDefine("NO_CARDINAL_LIGHTING")
                        .withSampler("Sampler0")
                        .withSampler("Sampler1")
                        .withSampler("Sampler2")
                        .withUniform("Lighting", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
                        .withVertexFormat(com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
                        .build());
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
    //?}

    private static net.minecraft.client.renderer.rendertype.RenderType create(Identifier texture) {
        //? if fabric {
        return net.minecraft.client.renderer.rendertype.RenderTypes.eyes(texture);
        //?} else {
        return net.minecraft.client.renderer.rendertype.RenderTypes.eyes(texture);
        //?}
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
