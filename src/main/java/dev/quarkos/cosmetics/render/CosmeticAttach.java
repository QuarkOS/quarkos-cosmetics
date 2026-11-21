package dev.quarkos.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.quarkos.cosmetics.config.ClientSettings;
import dev.quarkos.cosmetics.gallery.EquipmentMirror;
import dev.quarkos.cosmetics.model.Slot;
import dev.quarkos.cosmetics.model.Worn;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

final class CosmeticAttach {
    private CosmeticAttach() {
    }

    static void draw(PoseStack pose, CosmeticDrawer.Submitter submitter, int light, float age,
                     ModelPart root, ModelPart head, ModelPart body, ModelPart leftArm, ModelPart rightArm,
                     List<Worn.Placement> placements, boolean wingsFlap, boolean self, boolean wardrobePreview,
                     Player wearer) {
        CosmeticDrawer.cosmetics(pose, submitter, light, age, root, head, body, leftArm, rightArm,
                placements, wingsFlap, self, wardrobePreview, wearer);
    }

    static List<Worn.Placement> placements(UUID uuid, boolean self, boolean elytra, boolean helmet, boolean chest, boolean legs) {
        ClientSettings settings = ClientSettings.get();
        Map<Slot, String> equipped = self || uuid == null ? settings.equipped : settings.hideOthers || !ClientSettings.GALLERY_ENABLED ? Map.of() : EquipmentMirror.slots(uuid);
        return Worn.plan(equipped, settings.disabled, settings.categoryOn, Mods.tailsLoaded(), elytra, helmet, chest, legs);
    }

    static boolean showSkin(boolean self, double distanceSq) {
        ClientSettings settings = ClientSettings.get();
        if (!settings.skinLayers) {
            return false;
        }
        if (!self && settings.hideOthers) {
            return false;
        }
        int distance = Math.max(0, settings.skinDistance);
        return distanceSq <= (double) distance * (double) distance;
    }
}
