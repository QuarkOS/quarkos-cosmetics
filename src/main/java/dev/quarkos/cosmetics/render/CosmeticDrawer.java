package dev.quarkos.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.quarkos.cosmetics.model.Worn;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;
import java.util.function.Consumer;

public final class CosmeticDrawer {
    public interface Submitter {
        void submit(PoseStack pose, Identifier texture, boolean translucent, Consumer<VertexConsumer> draw);
    }

    private CosmeticDrawer() {
    }

    public static void cosmetics(PoseStack pose, Submitter submitter, int light, float age, boolean wingFlap,
                                 ModelPart head, ModelPart body, ModelPart leftArm, ModelPart rightArm,
                                 List<Worn.Placement> placed) {
        for (Worn.Placement placement : placed) {
            ModelPart part = switch (placement.anchor()) {
                case HEAD -> head;
                case BODY -> body;
                case LEFT_ARM -> leftArm;
                case RIGHT_ARM -> rightArm;
            };
            if ("quarkos:kite_wings".equals(placement.id())) {
                KiteWings.draw(pose, submitter, light, age, wingFlap, part,
                        placement.offsetX(), placement.offsetY(), placement.offsetZ());
                continue;
            }
            ModelLibrary.Resolved resolved = ModelLibrary.resolve(placement.id());
            if (resolved == null) {
                continue;
            }
            pose.pushPose();
            part.translateAndRotate(pose);
            pose.scale(1f / 16f, -1f / 16f, 1f / 16f);
            pose.translate(placement.offsetX(), placement.offsetY(), placement.offsetZ());
            Matrix4f view = new Matrix4f(pose.last().pose());
            Matrix3f normals = new Matrix3f(pose.last().normal());
            submitter.submit(pose, resolved.texture(), false, consumer ->
                    MeshDraw.model(consumer, view, normals, light, resolved.model(), resolved.width(), resolved.height(), age));
            pose.popPose();
        }
    }

    public static void shellAll(PoseStack pose, Submitter submitter, int light,
                               ModelPart head, ModelPart body, ModelPart leftArm, ModelPart rightArm,
                               ModelPart leftLeg, ModelPart rightLeg, Identifier texture,
                               List<SkinShell.Voxel> voxels) {
        shell(pose, submitter, light, head, texture, filter(voxels, SkinShell.Part.HEAD));
        shell(pose, submitter, light, body, texture, filter(voxels, SkinShell.Part.BODY));
        shell(pose, submitter, light, leftArm, texture, filter(voxels, SkinShell.Part.LEFT_ARM));
        shell(pose, submitter, light, rightArm, texture, filter(voxels, SkinShell.Part.RIGHT_ARM));
        shell(pose, submitter, light, leftLeg, texture, filter(voxels, SkinShell.Part.LEFT_LEG));
        shell(pose, submitter, light, rightLeg, texture, filter(voxels, SkinShell.Part.RIGHT_LEG));
    }

    private static List<SkinShell.Voxel> filter(List<SkinShell.Voxel> voxels, SkinShell.Part part) {
        List<SkinShell.Voxel> list = new java.util.ArrayList<>();
        for (SkinShell.Voxel voxel : voxels) {
            if (voxel.part() == part) {
                list.add(voxel);
            }
        }
        return list;
    }

    public static void shell(PoseStack pose, Submitter submitter, int light, ModelPart part,
                             Identifier texture, List<SkinShell.Voxel> voxels) {
        if (voxels.isEmpty()) {
            return;
        }
        pose.pushPose();
        part.translateAndRotate(pose);
        pose.scale(1f / 16f, -1f / 16f, 1f / 16f);
        Matrix4f view = new Matrix4f(pose.last().pose());
        Matrix3f normals = new Matrix3f(pose.last().normal());
        submitter.submit(pose, texture, true, consumer -> {
            for (SkinShell.Voxel voxel : voxels) {
                MeshDraw.voxel(consumer, view, normals, light, voxel.x(), voxel.y(), voxel.z(), 1.02f, voxel.u(), voxel.v(), 64, 64);
            }
        });
        pose.popPose();
    }
}
