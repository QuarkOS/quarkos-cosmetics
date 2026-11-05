package dev.quarkos.cosmetics.gallery.store;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StoredCosmetic(
        String id,
        String name,
        String description,
        String slot,
        String authorUuid,
        String authorName,
        int elements,
        int triangles,
        String status,
        String takedownBy,
        String createdAt,
        JsonNode model
) {
}
