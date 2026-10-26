package dev.quarkos.cosmetics.model;

import java.util.Locale;
import java.util.Optional;

public enum Slot {
    HAT("hat"),
    EARS("ears"),
    SHOULDER_LEFT("shoulder_left"),
    SHOULDER_RIGHT("shoulder_right"),
    BACK("back"),
    TAIL("tail");

    public final String wire;

    Slot(String wire) {
        this.wire = wire;
    }

    public static Optional<Slot> parse(String wire) {
        if (wire == null) {
            return Optional.empty();
        }
        String key = wire.trim().toLowerCase(Locale.ROOT);
        for (Slot slot : values()) {
            if (slot.wire.equals(key)) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }
}
