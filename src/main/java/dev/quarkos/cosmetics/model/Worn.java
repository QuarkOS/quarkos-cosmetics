package dev.quarkos.cosmetics.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Which cosmetic sits on which body part, after toggles, armor, and mod conflicts. */
public final class Worn {
    public enum Anchor {
        HEAD, BODY, LEFT_ARM, RIGHT_ARM
    }

    public record Placement(Slot slot, String id, Anchor anchor, float offsetX, float offsetY, float offsetZ) {
    }

    private Worn() {
    }

    public static List<Placement> plan(
            Map<Slot, String> equipped,
            Set<String> disabled,
            Map<Slot, Boolean> categories,
            boolean tailsMod,
            boolean elytra,
            boolean helmet,
            boolean chest,
            boolean legs
    ) {
        List<Placement> placed = new ArrayList<>();
        for (Slot slot : Slot.values()) {
            String id = equipped.get(slot);
            if (id == null || id.isBlank()) {
                continue;
            }
            if (disabled.contains(id) || !categories.getOrDefault(slot, true)) {
                continue;
            }
            if (slot == Slot.TAIL && tailsMod) {
                continue;
            }
            if (slot == Slot.BACK && elytra) {
                continue;
            }
            float x = 0;
            float y = 0;
            float z = 0;
            Anchor anchor = switch (slot) {
                case HAT, EARS -> Anchor.HEAD;
                case SHOULDER_LEFT -> Anchor.LEFT_ARM;
                case SHOULDER_RIGHT -> Anchor.RIGHT_ARM;
                case BACK, TAIL, BODY -> Anchor.BODY;
            };
            if (slot == Slot.HAT && helmet) {
                y = 3f;
            }
            if ((slot == Slot.SHOULDER_LEFT || slot == Slot.SHOULDER_RIGHT || slot == Slot.BACK) && chest && !elytra) {
                z = 2f;
                if (slot == Slot.SHOULDER_LEFT) {
                    x = 2f;
                } else if (slot == Slot.SHOULDER_RIGHT) {
                    x = -2f;
                }
            }
            if (slot == Slot.TAIL && legs) {
                z = 2f;
            }
            placed.add(new Placement(slot, id, anchor, x, y, z));
        }
        return placed;
    }
}
