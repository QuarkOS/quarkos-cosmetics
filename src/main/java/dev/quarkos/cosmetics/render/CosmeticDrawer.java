package dev.quarkos.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.quarkos.cosmetics.config.ClientSettings;
import dev.quarkos.cosmetics.config.TintColor;
import dev.quarkos.cosmetics.harness.ClientHarness;
import dev.quarkos.cosmetics.model.Worn;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public final class CosmeticDrawer {
    public enum Pass {
        CUTOUT_NO_CULL,
        CUTOUT,
        EYES,
        /** Emissive candle. Same frames as the base strip, biased forward so it does not z-fight. */
        GLOW,
        EMISSIVE,
        //? if >=1.21.11 {
        MASK_MENU,
        //?}
        TRANSLUCENT,
        TRANSLUCENT_CULL
    }

    public interface Submitter {
        /**
         * @param texture texture to bind, or null for vertex-colored geometry
         * @param pass cutout for the chick, cutout-no-cull for wings and packed meshes,
         *             glow for the jack-o'-lantern candle, translucent for the ghost
         */
        void submit(PoseStack pose, Identifier texture, Pass pass, Consumer<VertexConsumer> draw);
    }

    private CosmeticDrawer() {
    }

    /** Packed id, or a gallery copy of the same wings (the cube mesh is the folded rest pose). */
    private static boolean kiteWings(ModelLibrary.Resolved resolved) {
        if (PackedCatalog.KITE_WINGS.equals(resolved.id()) || "Kite wings".equals(resolved.name())) {
            return true;
        }
        for (var element : resolved.model().elements) {
            String name = element.name();
            if (name != null && name.contains("wing_left_flap")) {
                return true;
            }
        }
        return false;
    }

    public static boolean canTint(String id) {
        ModelLibrary.Resolved packed = ModelLibrary.packedById(id);
        return packed != null && packed.slot().wornOnHead() && !Chick.ID.equals(id) && !JackOLanternMask.ID.equals(id)
                && (packed.blockbench() || !packed.model().elements.isEmpty());
    }

    public static void cosmetics(PoseStack pose, Submitter submitter, int light, float age,
                                 ModelPart root, ModelPart head, ModelPart body, ModelPart leftArm, ModelPart rightArm,
                                 List<Worn.Placement> placed, boolean wingsFlap, boolean self, boolean wardrobePreview) {
        if (self && !wardrobePreview) {
            age = ClientHarness.releaseAge(age);
        }
        Function<Worn.Anchor, ModelPart> parts = anchor -> switch (anchor) {
            case HEAD -> head;
            case BODY -> body;
            case LEFT_ARM -> leftArm;
            case RIGHT_ARM -> rightArm;
        };
        for (Worn.Placement placement : placed) {
            ModelPart part = parts.apply(placement.anchor());
            if (Chick.isPerch(placement.slot()) && Chick.draws(placement.id())) {
                pose.pushPose();
                if (root != null) {
                    root.translateAndRotate(pose);
                }
                Chick.draw(pose, submitter, light, age, placement.slot(), part, body, self && !wardrobePreview);
                pose.popPose();
                continue;
            }
            if (JackOLanternMask.ID.equals(placement.id())) {
                JackOLanternMask.draw(pose, submitter, light, part,
                        placement.offsetX(), placement.offsetY(), placement.offsetZ(), wardrobePreview);
                continue;
            }
            ModelLibrary.Resolved resolved = ModelLibrary.resolve(placement.id());
            if (resolved == null) {
                continue;
            }
            if (kiteWings(resolved)) {
                KiteWings.draw(pose, submitter, light, age, wingsFlap, part,
                        placement.offsetX(), placement.offsetY(), placement.offsetZ());
                continue;
            }
            int tint = self && canTint(placement.id()) ? ClientSettings.get().tint(placement.slot()) : TintColor.DEFAULT;
            if (resolved.blockbench()) {
                BlockbenchCosmetic.draw(pose, submitter, light, age, root, parts, resolved, tint,
                        placement.offsetX(), placement.offsetY(), placement.offsetZ());
                continue;
            }
            pose.pushPose();
            part.translateAndRotate(pose);
            packed(pose, submitter, light, age, resolved, tint, placement.offsetX(), placement.offsetY(), placement.offsetZ());
            pose.popPose();
        }
    }

    static void packed(PoseStack pose, Submitter submitter, int light, float age, ModelLibrary.Resolved model, int tint,
                       float offsetX, float offsetY, float offsetZ) {
        pose.pushPose();
        // MeshDraw reverses the quad order to match the winding of the reflected model.
        pose.scale(1f / 16f, -1f / 16f, 1f / 16f);
        pose.translate(offsetX, offsetY, offsetZ);
        Matrix4f view = new Matrix4f(pose.last().pose());
        Matrix3f normals = new Matrix3f(pose.last().normal());
        submitter.submit(pose, model.texture(), Pass.CUTOUT_NO_CULL, consumer ->
                MeshDraw.model(consumer, view, normals, light, model.model(), model.width(), model.height(), age, tint));
        pose.popPose();
    }
}
