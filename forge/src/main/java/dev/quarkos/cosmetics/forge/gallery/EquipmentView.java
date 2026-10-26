package dev.quarkos.cosmetics.forge.gallery;

import java.util.Map;

public record EquipmentView(
        String playerUuid,
        String playerName,
        Map<String, String> slots,
        Map<String, CosmeticModel> models
) {
    public CosmeticModel worn(String slot) {
        String id = slots.get(slot);
        if (id == null || id.isBlank()) {
            return null;
        }
        return models.get(id);
    }
}
