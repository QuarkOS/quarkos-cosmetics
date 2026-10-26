package dev.quarkos.cosmetics.gallery.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import dev.quarkos.cosmetics.gallery.ApiException;
import dev.quarkos.cosmetics.gallery.GalleryConfig;
import dev.quarkos.cosmetics.gallery.Ids;
import dev.quarkos.cosmetics.gallery.Slots;
import dev.quarkos.cosmetics.gallery.store.GalleryStore;
import dev.quarkos.cosmetics.gallery.store.StoredCosmetic;
import dev.quarkos.cosmetics.gallery.store.StoredEquipment;
import dev.quarkos.cosmetics.gallery.store.StoredReport;
import dev.quarkos.cosmetics.gallery.store.StoredSession;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GalleryService {
    private static final Set<String> PUBLISH_FIELDS = Set.of("name", "description", "slot", "model");
    private static final Set<String> EQUIP_FIELDS = Set.of("slot", "cosmeticId");
    private static final Set<String> REPORT_FIELDS = Set.of("reason", "detail");
    private static final Set<String> TAKEDOWN_FIELDS = Set.of("note");
    private static final Set<String> REASONS = Set.of("spam", "stolen", "inappropriate", "malicious", "other");
    private static final Set<String> REJECTED = Set.of(
            "accessToken", "access_token", "password", "likes", "like", "followers", "following", "price", "premium"
    );

    private final GalleryStore store;
    private final ModelValidator validator;
    private final GalleryConfig config;
    private final Clock clock;

    public GalleryService(GalleryStore store, ModelValidator validator, GalleryConfig config, Clock clock) {
        this.store = store;
        this.validator = validator;
        this.config = config;
        this.clock = clock;
    }

    public Map<String, Object> info() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "cosmetics-gallery");
        body.put("version", "1.0.0");
        body.put("free", true);
        body.put("auth", "mojang-join");
        body.put("playerKey", "uuid");
        body.put("modelFormat", ModelValidator.FORMAT);
        body.put("slots", Slots.ALL);
        body.put("limits", config.limits().toMap(config.reportThreshold()));
        return body;
    }

    public Map<String, Object> publish(StoredSession session, JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new ApiException(400, "validation_failed", "Publish a JSON object.");
        }
        rejectFields(body, PUBLISH_FIELDS);
        String name = requiredText(body, "name");
        name = name.trim();
        if (!name.matches("[\\p{L}0-9][\\p{L}0-9 '\\-]{0,47}") || name.length() > 48) {
            throw new ApiException(400, "validation_failed", "Name must be 1 to 48 letters, digits, spaces, apostrophes, or hyphens.");
        }
        String description = "";
        if (body.has("description") && !body.get("description").isNull()) {
            if (!body.get("description").isTextual()) {
                throw new ApiException(400, "validation_failed", "description must be a string.");
            }
            description = body.get("description").asText().trim();
            if (description.length() > 280 || containsControl(description)) {
                throw new ApiException(400, "validation_failed", "Description must be at most 280 characters without control characters.");
            }
        }
        String slot = requiredText(body, "slot");
        if (!Slots.known(slot)) {
            throw new ApiException(400, "validation_failed", "slot must be one of " + String.join(", ", Slots.ALL) + ".");
        }
        if (!body.has("model")) {
            throw new ApiException(400, "validation_failed", "model is required.");
        }
        ModelValidator.Counts counts = validator.validate(body.get("model"));
        if (store.countByAuthor(session.uuid(), true) >= config.limits().maxPublicPerPlayer()) {
            throw new ApiException(409, "limit_reached",
                    "This player already has " + config.limits().maxPublicPerPlayer() + " public cosmetics. Take one down first.");
        }
        if (store.countByAuthor(session.uuid(), false) >= config.limits().maxRecordsPerPlayer()) {
            throw new ApiException(409, "limit_reached",
                    "This player has reached the stored cosmetic limit. The gallery stays free; the cap is only there to bound disk use.");
        }
        String id = UUID.randomUUID().toString().replace("-", "");
        String createdAt = now();
        StoredCosmetic cosmetic = new StoredCosmetic(
                id,
                name,
                description,
                slot,
                session.uuid(),
                session.name(),
                counts.elements(),
                counts.triangles(),
                "public",
                null,
                createdAt,
                body.get("model")
        );
        store.saveCosmetic(cosmetic);
        return summary(cosmetic);
    }

    public Map<String, Object> list(String query, String slot, String cursor, String limitRaw) {
        if (slot != null && !slot.isBlank() && !Slots.known(slot)) {
            throw new ApiException(400, "validation_failed", "slot must be one of " + String.join(", ", Slots.ALL) + ".");
        }
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.length() > 48) {
            throw new ApiException(400, "validation_failed", "q must be at most 48 characters.");
        }
        int offset = 0;
        if (cursor != null && !cursor.isBlank()) {
            if (!cursor.matches("\\d{1,9}")) {
                throw new ApiException(400, "validation_failed", "cursor must be a decimal offset.");
            }
            offset = Integer.parseInt(cursor);
        }
        int limit = 20;
        if (limitRaw != null && !limitRaw.isBlank()) {
            if (!limitRaw.matches("\\d{1,3}")) {
                throw new ApiException(400, "validation_failed", "limit must be an integer from 1 to 50.");
            }
            limit = Integer.parseInt(limitRaw);
            if (limit < 1 || limit > 50) {
                throw new ApiException(400, "validation_failed", "limit must be an integer from 1 to 50.");
            }
        }
        List<Map<String, Object>> matches = new ArrayList<>();
        for (StoredCosmetic cosmetic : store.allCosmetics()) {
            if (!"public".equals(cosmetic.status())) {
                continue;
            }
            if (slot != null && !slot.isBlank() && !slot.equals(cosmetic.slot())) {
                continue;
            }
            if (!needle.isEmpty() && !cosmetic.name().toLowerCase(Locale.ROOT).contains(needle)
                    && !cosmetic.description().toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            matches.add(summary(cosmetic));
        }
        int end = Math.min(matches.size(), offset + limit);
        List<Map<String, Object>> page = offset >= matches.size() ? List.of() : matches.subList(offset, end);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", page);
        body.put("nextCursor", end < matches.size() ? Integer.toString(end) : null);
        return body;
    }

    public Map<String, Object> get(String id) {
        StoredCosmetic cosmetic = requirePublic(id);
        Map<String, Object> body = summary(cosmetic);
        body.put("model", cosmetic.model());
        return body;
    }

    public Map<String, Object> equip(StoredSession session, JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new ApiException(400, "validation_failed", "Equip a JSON object.");
        }
        rejectFields(body, EQUIP_FIELDS);
        String slot = requiredText(body, "slot");
        if (!Slots.known(slot)) {
            throw new ApiException(400, "validation_failed", "slot must be one of " + String.join(", ", Slots.ALL) + ".");
        }
        if (!body.has("cosmeticId")) {
            throw new ApiException(400, "validation_failed", "cosmeticId is required. Use null to clear the slot.");
        }
        JsonNode cosmeticNode = body.get("cosmeticId");
        String cosmeticId = null;
        if (!cosmeticNode.isNull()) {
            if (!cosmeticNode.isTextual() || !Ids.COSMETIC_ID.matcher(cosmeticNode.asText()).matches()) {
                throw new ApiException(400, "validation_failed", "cosmeticId must be the id returned by publish.");
            }
            cosmeticId = cosmeticNode.asText();
            StoredCosmetic cosmetic = store.findCosmetic(cosmeticId).orElseThrow(
                    () -> new ApiException(404, "not_found", "That cosmetic is not in the gallery."));
            if (!"public".equals(cosmetic.status())) {
                throw new ApiException(409, "taken_down", "That cosmetic was taken down.");
            }
            if (!slot.equals(cosmetic.slot())) {
                throw new ApiException(400, "validation_failed", "That cosmetic belongs in the " + cosmetic.slot() + " slot.");
            }
        }
        Map<String, String> slots = emptySlots();
        store.findEquipment(session.uuid()).ifPresent(existing -> {
            if (existing.slots() != null) {
                for (String known : Slots.ALL) {
                    String value = existing.slots().get(known);
                    if (value != null && stillPublic(value)) {
                        slots.put(known, value);
                    }
                }
            }
        });
        slots.put(slot, cosmeticId);
        StoredEquipment equipment = new StoredEquipment(session.uuid(), session.name(), slots, now());
        store.saveEquipment(equipment);
        return equipmentView(equipment);
    }

    public Map<String, Object> equipment(String uuid) {
        String canonical = Ids.hyphenateUuid(uuid);
        StoredEquipment equipment = store.findEquipment(canonical).orElse(null);
        if (equipment == null) {
            return equipmentView(new StoredEquipment(canonical, null, emptySlots(), null));
        }
        Map<String, String> slots = emptySlots();
        if (equipment.slots() != null) {
            for (String known : Slots.ALL) {
                String value = equipment.slots().get(known);
                if (value != null && stillPublic(value)) {
                    slots.put(known, value);
                }
            }
        }
        return equipmentView(new StoredEquipment(canonical, equipment.playerName(), slots, equipment.updatedAt()));
    }

    public Map<String, Object> report(StoredSession session, String id, JsonNode body) {
        StoredCosmetic cosmetic = requirePublic(id);
        if (session.uuid().equals(cosmetic.authorUuid())) {
            throw new ApiException(403, "forbidden", "Take down your own cosmetic instead of reporting it.");
        }
        if (body == null || !body.isObject()) {
            throw new ApiException(400, "validation_failed", "Report a JSON object.");
        }
        rejectFields(body, REPORT_FIELDS);
        String reason = requiredText(body, "reason");
        if (!REASONS.contains(reason)) {
            throw new ApiException(400, "validation_failed", "reason must be one of " + String.join(", ", REASONS) + ".");
        }
        String detail = "";
        if (body.has("detail") && !body.get("detail").isNull()) {
            if (!body.get("detail").isTextual()) {
                throw new ApiException(400, "validation_failed", "detail must be a string.");
            }
            detail = body.get("detail").asText().trim();
            if (detail.length() > 280 || containsControl(detail)) {
                throw new ApiException(400, "validation_failed", "detail must be at most 280 characters without control characters.");
            }
        }
        StoredReport report = new StoredReport(
                UUID.randomUUID().toString().replace("-", ""),
                session.uuid(),
                session.name(),
                reason,
                detail,
                now()
        );
        if (!store.addReport(cosmetic.id(), report)) {
            throw new ApiException(409, "conflict", "You already reported this cosmetic.");
        }
        int count = store.reports(cosmetic.id()).size();
        String galleryStatus = "public";
        if (count >= config.reportThreshold()) {
            takedown(cosmetic, "reports");
            galleryStatus = "taken_down";
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", report.id());
        response.put("cosmeticId", cosmetic.id());
        response.put("status", "taken_down".equals(galleryStatus) ? "taken_down" : "open");
        response.put("galleryStatus", galleryStatus);
        return response;
    }

    public Map<String, Object> takedown(StoredSession session, String id, JsonNode body) {
        if (!Ids.COSMETIC_ID.matcher(id).matches()) {
            throw new ApiException(400, "validation_failed", "Cosmetic id is not valid.");
        }
        StoredCosmetic cosmetic = store.findCosmetic(id).orElseThrow(
                () -> new ApiException(404, "not_found", "That cosmetic is not in the gallery."));
        if (!"public".equals(cosmetic.status())) {
            if (session.uuid().equals(cosmetic.authorUuid())) {
                throw new ApiException(409, "conflict", "That cosmetic is already taken down.");
            }
            throw new ApiException(404, "not_found", "That cosmetic is not in the gallery.");
        }
        if (!session.uuid().equals(cosmetic.authorUuid())) {
            throw new ApiException(403, "forbidden", "Only the player who published a cosmetic can take it down.");
        }
        if (body != null && !body.isNull()) {
            if (!body.isObject()) {
                throw new ApiException(400, "validation_failed", "Takedown body must be a JSON object.");
            }
            rejectFields(body, TAKEDOWN_FIELDS);
            if (body.has("note") && !body.get("note").isNull()) {
                if (!body.get("note").isTextual()) {
                    throw new ApiException(400, "validation_failed", "note must be a string.");
                }
                String note = body.get("note").asText();
                if (note.length() > 280 || containsControl(note)) {
                    throw new ApiException(400, "validation_failed", "note must be at most 280 characters without control characters.");
                }
            }
        }
        takedown(cosmetic, "author");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", cosmetic.id());
        response.put("status", "taken_down");
        response.put("by", "author");
        return response;
    }

    private void takedown(StoredCosmetic cosmetic, String by) {
        StoredCosmetic hidden = new StoredCosmetic(
                cosmetic.id(),
                cosmetic.name(),
                cosmetic.description(),
                cosmetic.slot(),
                cosmetic.authorUuid(),
                cosmetic.authorName(),
                0,
                0,
                "taken_down",
                by,
                cosmetic.createdAt(),
                cosmetic.model()
        );
        store.saveCosmetic(hidden);
        store.stripModel(hidden);
        store.clearEquipped(cosmetic.id());
    }

    private StoredCosmetic requirePublic(String id) {
        if (!Ids.COSMETIC_ID.matcher(id).matches()) {
            throw new ApiException(400, "validation_failed", "Cosmetic id is not valid.");
        }
        StoredCosmetic cosmetic = store.findCosmetic(id).orElseThrow(
                () -> new ApiException(404, "not_found", "That cosmetic is not in the gallery."));
        if (!"public".equals(cosmetic.status())) {
            throw new ApiException(404, "not_found", "That cosmetic is not in the gallery.");
        }
        return cosmetic;
    }

    private boolean stillPublic(String id) {
        return store.findCosmetic(id).map(cosmetic -> "public".equals(cosmetic.status())).orElse(false);
    }

    private static Map<String, Object> summary(StoredCosmetic cosmetic) {
        Map<String, Object> author = new LinkedHashMap<>();
        author.put("uuid", cosmetic.authorUuid());
        author.put("name", cosmetic.authorName());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", cosmetic.id());
        body.put("name", cosmetic.name());
        body.put("description", cosmetic.description());
        body.put("slot", cosmetic.slot());
        body.put("author", author);
        body.put("elements", cosmetic.elements());
        body.put("triangles", cosmetic.triangles());
        body.put("createdAt", cosmetic.createdAt());
        body.put("href", "/v1/cosmetics/" + cosmetic.id());
        return body;
    }

    private static Map<String, Object> equipmentView(StoredEquipment equipment) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("playerUuid", equipment.playerUuid());
        body.put("playerName", equipment.playerName());
        body.put("slots", new LinkedHashMap<>(equipment.slots()));
        body.put("updatedAt", equipment.updatedAt());
        return body;
    }

    private static Map<String, String> emptySlots() {
        Map<String, String> slots = new LinkedHashMap<>();
        for (String slot : Slots.ALL) {
            slots.put(slot, null);
        }
        return slots;
    }

    private static void rejectFields(JsonNode body, Set<String> allowed) {
        body.fieldNames().forEachRemaining(name -> {
            if (REJECTED.contains(name)) {
                if ("accessToken".equals(name) || "access_token".equals(name) || "password".equals(name)) {
                    throw new ApiException(400, "access_token_rejected",
                            "Send the Minecraft access token only to sessionserver join. This service does not accept or store it.");
                }
                throw new ApiException(400, "validation_failed", "This gallery has no social or payment fields.");
            }
            if (!allowed.contains(name)) {
                throw new ApiException(400, "validation_failed", "Unknown field.");
            }
        });
    }

    private static String requiredText(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw new ApiException(400, "validation_failed", field + " is required.");
        }
        return node.asText();
    }

    private static boolean containsControl(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private String now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS).toString();
    }
}
