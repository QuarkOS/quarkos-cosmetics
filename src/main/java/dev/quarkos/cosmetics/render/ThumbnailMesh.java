package dev.quarkos.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.quarkos.cosmetics.config.TintColor;
//? if >=26.3 {
/*import net.minecraft.client.renderer.SubmitNodeCollector;
*///?} else {
import net.minecraft.client.renderer.MultiBufferSource;
//?}
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;

//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderTypes;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
*///?}

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

//? if >=26.3 {
/*import static net.minecraft.util.LightCoordsUtil.FULL_BRIGHT;
*///?} else {
import static net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
//?}

/** The cosmetic's own mesh in a fixed three-quarter pose. */
public final class ThumbnailMesh {
    private static final int CACHE_LIMIT = 64;
    private static final Map<ModelLibrary.Resolved, Optional<ThumbnailMesh>> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private final List<Batch> batches;
    private final Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
    private final Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);

    private ThumbnailMesh(List<Batch> batches) {
        this.batches = List.copyOf(batches);
        for (Batch batch : batches) {
            for (Vertex vertex : batch.vertices()) {
                min.set(Math.min(min.x, vertex.x()), Math.min(min.y, vertex.y()), Math.min(min.z, vertex.z()));
                max.set(Math.max(max.x, vertex.x()), Math.max(max.y, vertex.y()), Math.max(max.z, vertex.z()));
            }
        }
    }

    public static ThumbnailMesh of(ModelLibrary.Resolved model) {
        Optional<ThumbnailMesh> mesh = CACHE.computeIfAbsent(model, ThumbnailMesh::build);
        if (CACHE.size() > CACHE_LIMIT) {
            CACHE.remove(CACHE.keySet().iterator().next());
        }
        return mesh.orElse(null);
    }

    private static Optional<ThumbnailMesh> build(ModelLibrary.Resolved model) {
        PoseStack pose = new PoseStack();
        //? if >=26.3 {
        /*pose.rotate(new Quaternionf().rotateX(0.26f).rotateY(0.57f));
        *///?} else {
        pose.mulPose(new Quaternionf().rotateX(0.26f).rotateY(0.57f));
        //?}
        List<Batch> batches = new ArrayList<>();
        CosmeticDrawer.Submitter submitter = (stack, texture, pass, draw) -> {
            Capture capture = new Capture();
            draw.accept(capture);
            if (!capture.vertices.isEmpty()) {
                batches.add(new Batch(texture, pass, List.copyOf(capture.vertices)));
            }
        };
        int light = FULL_BRIGHT;
        if (model.blockbench()) {
            BlockbenchCosmetic.portrait(pose, submitter, light, model);
        } else if (PackedCatalog.KITE_WINGS.equals(model.id())) {
            KiteWings.portrait(pose, submitter, light);
        //? if >=1.21.11 {
        } else if (Chick.draws(model.id())) {
            Chick.portrait(pose, submitter, light);
        } else if (JackOLanternMask.ID.equals(model.id())) {
            JackOLanternMask.portrait(pose, submitter);
        //?}
        } else {
            CosmeticDrawer.packed(pose, submitter, light, 0f, model, TintColor.DEFAULT, 0f, 0f, 0f);
        }
        return batches.isEmpty() ? Optional.empty() : Optional.of(new ThumbnailMesh(batches));
    }

    public float scaleToFit(int width, int height) {
        float x = width / Math.max(0.001f, max.x - min.x);
        float y = height / Math.max(0.001f, max.y - min.y);
        return Math.min(x, y) * 0.88f;
    }

    //? if >=26.3 {
    /*public void render(PoseStack pose, SubmitNodeCollector collector) {
        pose.translate(-(min.x + max.x) / 2f, -(min.y + max.y) / 2f, -(min.z + max.z) / 2f);
        for (Batch batch : batches) {
            var type = switch (batch.pass()) {
                case CUTOUT -> RenderTypes.entityCutoutCull(batch.texture());
                case CUTOUT_NO_CULL -> RenderTypes.entityCutout(batch.texture());
                case GLOW -> MaskGlow.type(batch.texture());
                case EMISSIVE -> RenderTypes.entityTranslucentEmissive(batch.texture());
                case TRANSLUCENT_CULL -> RenderTypes.entityTranslucentCull(batch.texture());
                case MASK_MENU -> MaskGlow.menu();
                case TRANSLUCENT -> RenderTypes.entityTranslucent(batch.texture());
                case EYES -> RenderTypes.eyes(batch.texture());
            };
            collector.submitCustomGeometry(pose, type, (entry, consumer) ->
                    drawBatch(entry, consumer, batch, new Vector3f(), new Vector3f()));
        }
    }
    *///?} else {
    public void render(PoseStack pose, MultiBufferSource buffer) {
        pose.translate(-(min.x + max.x) / 2f, -(min.y + max.y) / 2f, -(min.z + max.z) / 2f);
        PoseStack.Pose entry = pose.last();
        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        for (Batch batch : batches) {
            //? if >=1.21.11 {
            var type = switch (batch.pass()) {
                case CUTOUT, GLOW -> RenderTypes.entityCutout(batch.texture());
                case EMISSIVE -> RenderTypes.entityTranslucentEmissive(batch.texture());
                case TRANSLUCENT_CULL -> RenderTypes.itemEntityTranslucentCull(batch.texture());
                case MASK_MENU -> MaskGlow.menu();
                case CUTOUT_NO_CULL -> RenderTypes.entityCutoutNoCull(batch.texture());
                case TRANSLUCENT -> RenderTypes.entityTranslucent(batch.texture());
                case EYES -> RenderTypes.eyes(batch.texture());
            };
            //?} else {
            /*var type = switch (batch.pass()) {
                case CUTOUT -> RenderType.entityCutout(batch.texture());
                case CUTOUT_NO_CULL, GLOW -> RenderType.entityCutoutNoCull(batch.texture());
                case EMISSIVE -> RenderType.entityTranslucentEmissive(batch.texture());
                case TRANSLUCENT -> RenderType.entityTranslucent(batch.texture());
                case TRANSLUCENT_CULL -> RenderType.entityTranslucentCull(batch.texture());
                case EYES -> RenderType.eyes(batch.texture());
            };
            *///?}
            drawBatch(entry, buffer.getBuffer(type), batch, position, normal);
        }
    }
    //?}

    private static void drawBatch(PoseStack.Pose entry, VertexConsumer consumer, Batch batch,
                                  Vector3f position, Vector3f normal) {
        for (Vertex vertex : batch.vertices()) {
            entry.pose().transformPosition(vertex.x(), vertex.y(), vertex.z(), position);
            entry.transformNormal(vertex.nx(), vertex.ny(), vertex.nz(), normal);
            consumer.addVertex(position.x, position.y, position.z, vertex.color(), vertex.u(), vertex.v(),
                    OverlayTexture.NO_OVERLAY, FULL_BRIGHT, normal.x, normal.y, normal.z);
        }
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private record Batch(Identifier texture, CosmeticDrawer.Pass pass, List<Vertex> vertices) {
    }

    private record Vertex(float x, float y, float z, int color, float u, float v, float nx, float ny, float nz) {
    }

    /** The shared cosmetic emitters write the normal last for each vertex. */
    private static final class Capture implements VertexConsumer {
        private final List<Vertex> vertices = new ArrayList<>();
        private float x, y, z, u, v;
        private int color = -1;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return setColor(a << 24 | r << 16 | g << 8 | b);
        }

        @Override
        public VertexConsumer setColor(int color) {
            this.color = color;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        //? if >=26.3 {
        /*@Override
        public VertexConsumer setUv3(float u, float v) {
            return this;
        }
        *///?}

        @Override
        public VertexConsumer setNormal(float nx, float ny, float nz) {
            vertices.add(new Vertex(x, y, z, color, u, v, nx, ny, nz));
            return this;
        }

        //? if >=1.21.11 {
        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
        //?}
    }
}
