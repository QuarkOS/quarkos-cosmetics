package dev.quarkos.cosmetics.gallery.auth;

import java.io.IOException;
import java.util.Optional;

/**
 * The only outbound call this service makes: Mojang hasJoined.
 * The access token is never passed in and never stored.
 */
public interface MojangSessions {
    Optional<Profile> hasJoined(String username, String serverId) throws IOException;

    /**
     * @param id   profile id from hasJoined, without hyphens
     * @param name current Mojang name
     */
    record Profile(String id, String name) {
    }
}
