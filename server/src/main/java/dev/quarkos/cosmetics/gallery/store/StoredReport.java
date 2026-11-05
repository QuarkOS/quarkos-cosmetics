package dev.quarkos.cosmetics.gallery.store;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StoredReport(
        String id,
        String reporterUuid,
        String reporterName,
        String reason,
        String detail,
        String createdAt
) {
}
