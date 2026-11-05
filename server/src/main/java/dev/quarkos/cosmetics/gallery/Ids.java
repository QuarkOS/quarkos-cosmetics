package dev.quarkos.cosmetics.gallery;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Ids {
    public static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    public static final Pattern SERVER_ID = Pattern.compile("[0-9a-f]{32}");
    public static final Pattern COSMETIC_ID = Pattern.compile("[0-9a-f]{32}");

    private Ids() {
    }

    public static String hyphenateUuid(String raw) {
        String compact = raw.toLowerCase(Locale.ROOT).replace("-", "");
        if (!compact.matches("[0-9a-f]{32}")) {
            throw new ApiException(400, "bad_uuid", "Player UUID must be 32 hex characters, with or without hyphens.");
        }
        return compact.substring(0, 8) + "-" + compact.substring(8, 12) + "-" + compact.substring(12, 16) + "-"
                + compact.substring(16, 20) + "-" + compact.substring(20);
    }

    public static String hyphenateMojangId(String raw) {
        try {
            return hyphenateUuid(raw);
        } catch (ApiException ex) {
            throw new ApiException(502, "mojang_unavailable", "Mojang returned a profile id this service could not use.");
        }
    }
}
