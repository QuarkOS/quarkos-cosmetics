package dev.quarkos.cosmetics.config.ui;

//? if >=1.21.11 {
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.quarkos.cosmetics.config.WardrobeLayout.Rect;
import dev.quarkos.cosmetics.render.ThumbnailMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.MultiBufferSource;

//? if forge {
/*import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
*///?}

public class ThumbnailRenderer extends PictureInPictureRenderer<ThumbnailRenderer.State> {
    private static boolean available;
    private ThumbnailMesh rendered;

    public ThumbnailRenderer(MultiBufferSource.BufferSource buffers) {
        super(buffers);
        available = true;
    }

    public static boolean available() {
        return available;
    }

    static void submit(GuiGraphics graphics, ThumbnailMesh mesh, Rect area) {
        //? if forge {
        /*GuiRenderState gui = graphics.getRenderState();
        ScreenRectangle scissor = graphics.getScissorStack().peek();
        *///?} else {
        GuiRenderState gui = graphics.guiRenderState;
        ScreenRectangle scissor = graphics.scissorStack.peek();
        //?}
        ScreenRectangle bounds = PictureInPictureRenderState.getBounds(area.x(), area.y(), area.right(), area.bottom(), scissor);
        if (bounds != null) {
            gui.submitPicturesInPictureState(new State(mesh, area.x(), area.y(), area.right(), area.bottom(),
                    mesh.scaleToFit(area.w(), area.h()), scissor, bounds));
        }
    }

    public record State(ThumbnailMesh mesh, int x0, int y0, int x1, int y1, float scale,
                        ScreenRectangle scissorArea, ScreenRectangle bounds) implements PictureInPictureRenderState {
    }

    @Override
    public Class<State> getRenderStateClass() {
        return State.class;
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2f;
    }

    @Override
    protected boolean textureIsReadyToBlit(State state) {
        return rendered == state.mesh();
    }

    @Override
    protected void renderToTexture(State state, PoseStack pose) {
        var previousLights = RenderSystem.getShaderLights();
        try {
            Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
            state.mesh().render(pose, bufferSource);
            bufferSource.endBatch();
            rendered = state.mesh();
        } finally {
            RenderSystem.setShaderLights(previousLights);
        }
    }

    @Override
    protected String getTextureLabel() {
        return "cosmetic card";
    }

    //? if forge {
    /*// Forge registers a single renderer per state class. Each pending blit needs its own image.
    public static final class Forge extends ThumbnailRenderer {
        private final Map<Key, ThumbnailRenderer> renderers = new HashMap<>();
        private final Set<Key> visible = new HashSet<>();

        public Forge(MultiBufferSource.BufferSource buffers) {
            super(buffers);
        }

        @Override
        public void prepare(State state, GuiRenderState gui, int guiScale) {
            Key key = new Key(state.mesh(), (state.x1() - state.x0()) * guiScale, (state.y1() - state.y0()) * guiScale);
            visible.add(key);
            renderers.computeIfAbsent(key, ignored -> new ThumbnailRenderer(bufferSource)).prepare(state, gui, guiScale);
        }

        public void endFrame() {
            renderers.entrySet().removeIf(entry -> {
                if (visible.contains(entry.getKey())) {
                    return false;
                }
                entry.getValue().close();
                return true;
            });
            visible.clear();
        }

        @Override
        public void close() {
            renderers.values().forEach(ThumbnailRenderer::close);
            renderers.clear();
            super.close();
        }

        private record Key(ThumbnailMesh mesh, int pixelWidth, int pixelHeight) {
        }
    }
    *///?}
}
//?}
