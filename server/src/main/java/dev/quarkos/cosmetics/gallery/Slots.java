package dev.quarkos.cosmetics.gallery;

import java.util.List;
import java.util.Set;

/**
 * Worn slots from the approved cosmetics matrix. One back slot covers wings or bling.
 * Capes stay out of this service.
 */
public final class Slots {
    public static final List<String> ALL = List.of(
            "hat",
            "ears",
            "shoulder_left",
            "shoulder_right",
            "back",
            "tail"
    );
    public static final Set<String> SET = Set.copyOf(ALL);

    private Slots() {
    }

    public static boolean known(String slot) {
        return SET.contains(slot);
    }
}
