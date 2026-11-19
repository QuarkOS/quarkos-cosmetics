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

    public boolean wornOnHead() {
        return this == HAT || this == EARS;
    }

    public boolean isShoulder() {
        return this == SHOULDER_LEFT || this == SHOULDER_RIGHT;
    }

    public boolean isChickPerch() {
        return this == HAT || isShoulder();
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
