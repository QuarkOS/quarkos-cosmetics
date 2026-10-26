package dev.quarkos.cosmetics.gallery.store;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StoredSession(
        String token,
        String uuid,
        String name,
        String expiresAt
) {
}
