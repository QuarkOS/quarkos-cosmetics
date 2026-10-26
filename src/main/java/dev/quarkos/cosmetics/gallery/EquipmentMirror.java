package dev.quarkos.cosmetics.gallery;

import dev.quarkos.cosmetics.config.ClientSettings;
import dev.quarkos.cosmetics.model.Slot;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Other players' equipped cosmetics, refreshed from the gallery about every two seconds. */
public final class EquipmentMirror {
    private static final ConcurrentHashMap<String, Holder> HOLDERS = new ConcurrentHashMap<>();

    private EquipmentMirror() {
    }

    public static Map<Slot, String> slots(UUID uuid) {
        if (uuid == null) {
            return Map.of();
        }
        String key = ClientSettings.hyphenate(uuid.toString());
        Holder holder = HOLDERS.computeIfAbsent(key, ignored -> new Holder());
        long now = System.currentTimeMillis();
        if (!holder.inflight && now - holder.fetchedAt > 2000L) {
            holder.inflight = true;
            GalleryWork.refreshEquipment(key);
        }
        return Map.copyOf(holder.slots);
    }

    public static void accept(String uuid, GalleryClient.Equipment equipment) {
        Holder holder = HOLDERS.computeIfAbsent(uuid, ignored -> new Holder());
        EnumMap<Slot, String> next = new EnumMap<>(Slot.class);
        for (Slot slot : Slot.values()) {
            String id = equipment.id(slot);
            if (id != null && !id.isBlank()) {
                next.put(slot, id);
            }
        }
        holder.slots.clear();
        holder.slots.putAll(next);
        holder.fetchedAt = System.currentTimeMillis();
        holder.inflight = false;
    }

    public static void failed(String uuid) {
        Holder holder = HOLDERS.get(uuid);
        if (holder != null) {
            holder.fetchedAt = System.currentTimeMillis();
            holder.inflight = false;
        }
    }

    private static final class Holder {
        private final EnumMap<Slot, String> slots = new EnumMap<>(Slot.class);
        private volatile long fetchedAt;
        private volatile boolean inflight;
    }
}
