package dev.quarkos.cosmetics.render;

import net.minecraft.resources.Identifier;

//? if >=1.21.11 {
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
//? if >=26.3 {
/*import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.BindGroupLayouts;
*///?} else {
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
//?}
//?}

//? if <26.3 {
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
//?}

/** Render types for the mask's world and menu draws. */
public final class MaskGlow {
    private MaskGlow() {
    }

    /** Present so client setup can touch the class before the first frame. */
    public static void boot() {
    }

    //? if >=1.21.11 {
    private static RenderType menu;

    /** The mesh supplies its shaded texel colors and the PIP depth buffer resolves the visible faces. */
    static RenderType menu() {
        if (menu == null) {
            //? if >=26.3 {
            /*var builder = RenderPipeline.builder()
                    .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                    .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withColorTargetState(ColorTargetState.DEFAULT)
                    .withDepthStencilState(DepthStencilState.DEFAULT);
            *///?} else {
            var builder = RenderPipeline.builder()
                    .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(true);
            //?}
            var pipeline = builder.withLocation(Identifier.fromNamespaceAndPath("quarkos_cosmetics", "pipeline/mask_menu"))
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withCull(true)
                    .build();
            //? if fabric {
            RenderPipelines.register(pipeline);
            //?} else {
            /*RenderPipelines.PIPELINES_BY_LOCATION.put(pipeline.getLocation(), pipeline);
            *///?}
            menu = RenderType.create("mask_menu", RenderSetup.builder(pipeline).createRenderSetup());
        }
        return menu;
    }
    //?}

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
