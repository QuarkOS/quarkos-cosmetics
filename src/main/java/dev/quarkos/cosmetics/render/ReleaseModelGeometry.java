package dev.quarkos.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Exports the same quads the Blockbench renderer submits, in upright player units. */
public final class ReleaseModelGeometry {
    private ReleaseModelGeometry() {
    }

    public static void write(Path path, List<ModelLibrary.Resolved> models) throws IOException {
        try (var out = new PrintWriter(Files.newBufferedWriter(path))) {
            for (var model : models) {
                if (!model.blockbench() && !PackedCatalog.KITE_WINGS.equals(model.id())) {
                    continue;
                }
                out.println("M " + model.id());
                var pose = new PoseStack();
                pose.translate(0, 24, 0);
                pose.scale(16, -16, 16);
                CosmeticDrawer.Submitter sink = (stack, texture, pass, draw) -> {
                    PackedCatalog.Texture asset = asset(model, texture);
                    boolean strip = texture.getPath().startsWith("dynamic/blockbench/");
                    out.println("T " + pass + " " + texture.getPath() + " "
                            + (strip ? asset.frames().width() : 0) + " " + (strip ? asset.frames().height() : 0));
                    draw.accept(new Vertices(out));
                };
                if (model.blockbench()) {
                    BlockbenchCosmetic.draw(pose, sink, 0, 0, null, anchor -> null, model, 0xffffff, 0, 0, 0, null);
                } else {
                    KiteWings.hold("flap", 0f);
                    try {
                        KiteWings.draw(pose, sink, 0, 0, false, null, 0, 0, 0);
                    } finally {
                        KiteWings.hold(null, null);
                    }
                }
            }
            if (out.checkError()) {
                throw new IOException("Could not write " + path);
            }
        }
    }

    private static PackedCatalog.Texture asset(ModelLibrary.Resolved model, Identifier texture) {
        var item = model.builtin();
        var glow = item.glow();
        return glow != null && texture.getPath().endsWith(glow.path()) ? glow : item.base();
    }

    private static final class Vertices implements VertexConsumer {
        private final PrintWriter out;
        private float x, y, z, u, v;

        private Vertices(PrintWriter out) {
            this.out = out;
        }

        @Override
        public VertexConsumer addVertex(float px, float py, float pz) {
            x = px;
            y = py;
            z = pz;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }

        @Override
        public VertexConsumer setNormal(float nx, float ny, float nz) {
            out.println("V " + x + " " + y + " " + z + " " + u + " " + v);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
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

        //? if >=1.21.11 {
        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
        //?}
    }
}
