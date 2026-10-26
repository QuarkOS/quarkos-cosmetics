package dev.quarkos.cosmetics.gallery.store;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StoredEquipment(
        String playerUuid,
        String playerName,
        Map<String, String> slots,
        String updatedAt
) {
}
