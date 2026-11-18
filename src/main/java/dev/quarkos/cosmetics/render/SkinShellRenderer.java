package dev.quarkos.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

//? if >=1.21.11 {
import net.minecraft.client.model.player.PlayerModel;
//?} else {
/*import net.minecraft.client.model.PlayerModel;
*///?}

public final class SkinShellRenderer {
    private static final int CACHE_LIMIT = 64;
    private static final SkinShell.Part[] PARTS = SkinShell.Part.values();
    // Texture lookups and geometry construction happen on the render thread.
    private static final Map<Key, CachedShell> CACHE = new LinkedHashMap<>(16, 0.75f, true);

    private record Key(Identifier texture, boolean slim) {
    }

    private record CachedShell(Object source, Map<SkinShell.Part, List<SkinShell.Voxel>> parts) {
    }

    private SkinShellRenderer() {
    }

    public static void render(
            //? if >=1.21.11 {
            PlayerModel model,
            //?} else {
            /*PlayerModel<?> model,
            *///?}
            PoseStack pose, VertexConsumer consumer, int light, int overlay, int color,
            Identifier skin, boolean slim, boolean self, double distanceSq) {
        if (skin == null || !CosmeticAttach.showSkin(self, distanceSq)) {
            model.renderToBuffer(pose, consumer, light, overlay, color);
            return;
        }
        Map<SkinShell.Part, List<SkinShell.Voxel>> shell = cached(skin, slim);
        if (shell.isEmpty()) {
            model.renderToBuffer(pose, consumer, light, overlay, color);
            return;
        }

        // The entries follow the SkinShell.Part order.
        ModelPart[] bases = {model.head, model.body, model.leftArm, model.rightArm, model.leftLeg, model.rightLeg};
        ModelPart[] flat = {model.hat, model.jacket, model.leftSleeve, model.rightSleeve, model.leftPants, model.rightPants};
        int replaced = 0;
        //? if >=1.21.11 {
        if (!model.root().visible) {
            model.renderToBuffer(pose, consumer, light, overlay, color);
            return;
        }
        //?}
        for (int i = 0; i < PARTS.length; i++) {
            if (bases[i].visible && flat[i].visible && !flat[i].skipDraw && !shell.get(PARTS[i]).isEmpty()) {
                flat[i].visible = false;
                replaced |= 1 << i;
            }
        }
        try {
            model.renderToBuffer(pose, consumer, light, overlay, color);
        } finally {
            for (int i = 0; i < PARTS.length; i++) {
                if ((replaced & (1 << i)) != 0) {
                    flat[i].visible = true;
                }
            }
        }

        if (replaced == 0) {
            return;
        }
        pose.pushPose();
        try {
            //? if >=1.21.11 {
            model.root().translateAndRotate(pose);
            //?}
            for (int i = 0; i < PARTS.length; i++) {
                if ((replaced & (1 << i)) != 0) {
                    pose.pushPose();
                    try {
                        bases[i].translateAndRotate(pose);
                        MeshDraw.skin(consumer, pose.last(), light, overlay, color, shell.get(PARTS[i]));
                    } finally {
                        pose.popPose();
                    }
                }
            }
        } finally {
            pose.popPose();
        }
    }

    private static Map<SkinShell.Part, List<SkinShell.Voxel>> cached(Identifier skin, boolean slim) {
        var texture = Minecraft.getInstance().getTextureManager().getTexture(skin);
        Object source = texture instanceof DynamicTexture dynamic && dynamic.getPixels() != null
                ? dynamic.getPixels() : texture;
        //? if <1.21.11 {
        /*if (texture instanceof SkinImages.LegacyTexture legacy) {
            source = legacy.quarkos$skinPixels();
        }
        *///?}
        Key key = new Key(skin, slim);
        CachedShell cached = CACHE.get(key);
        if (cached != null && cached.source() == source) {
            return cached.parts();
        }
        SkinImages.Pixels pixels = SkinImages.read(skin);
        Map<SkinShell.Part, List<SkinShell.Voxel>> parts = pixels == null ? Map.of()
                : SkinShell.build(pixels.width(), pixels.height(), pixels.argb(), slim);
        if (parts.values().stream().allMatch(List::isEmpty)) {
            parts = Map.of();
        }
        CACHE.put(key, new CachedShell(source, parts));
        if (CACHE.size() > CACHE_LIMIT) {
            CACHE.remove(CACHE.keySet().iterator().next());
        }
        return parts;
    }

    public static void clearCache() {
        CACHE.clear();
    }
}
