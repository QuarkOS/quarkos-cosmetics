package dev.quarkos.cosmetics.gallery.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.quarkos.cosmetics.gallery.Jsons;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * File store keyed by cosmetic id, session token, and player UUID.
 * A later shared backend can replace this class without changing the HTTP API.
 */
public final class GalleryStore {
    private final Path root;
    private final Jsons json;
    private final SecureRandom random = new SecureRandom();
    private final Object lock = new Object();

    public GalleryStore(Path root, Jsons json) throws IOException {
        this.root = root;
        this.json = json;
        Files.createDirectories(root.resolve("cosmetics"));
        Files.createDirectories(root.resolve("equipment"));
        Files.createDirectories(root.resolve("sessions"));
        Files.createDirectories(root.resolve("reports"));
    }

    public StoredSession createSession(String uuid, String name, Instant expiresAt) {
        synchronized (lock) {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            StoredSession session = new StoredSession(token, uuid, name, expiresAt.toString());
            write(root.resolve("sessions").resolve(token + ".json"), session);
            return session;
        }
    }

    public Optional<StoredSession> findSession(String token, Instant now) {
        synchronized (lock) {
            Path path = root.resolve("sessions").resolve(token + ".json");
            if (!Files.isRegularFile(path)) {
                return Optional.empty();
            }
            StoredSession session = read(path, StoredSession.class);
            if (session == null) {
                return Optional.empty();
            }
            if (!Instant.parse(session.expiresAt()).isAfter(now)) {
                delete(path);
                return Optional.empty();
            }
            return Optional.of(session);
        }
    }

    public void deleteSession(String token) {
        synchronized (lock) {
            delete(root.resolve("sessions").resolve(token + ".json"));
        }
    }

    public void saveCosmetic(StoredCosmetic cosmetic) {
        synchronized (lock) {
            write(cosmeticPath(cosmetic.id()), cosmetic);
        }
    }

    public Optional<StoredCosmetic> findCosmetic(String id) {
        synchronized (lock) {
            Path path = cosmeticPath(id);
            if (!Files.isRegularFile(path)) {
                return Optional.empty();
            }
            return Optional.ofNullable(read(path, StoredCosmetic.class));
        }
    }

    public List<StoredCosmetic> allCosmetics() {
        synchronized (lock) {
            List<StoredCosmetic> found = new ArrayList<>();
            try (Stream<Path> files = Files.list(root.resolve("cosmetics"))) {
                files.filter(path -> path.getFileName().toString().endsWith(".json")).forEach(path -> {
                    StoredCosmetic cosmetic = read(path, StoredCosmetic.class);
                    if (cosmetic != null) {
                        found.add(cosmetic);
                    }
                });
            } catch (IOException ex) {
                throw new IllegalStateException("Could not list cosmetics.", ex);
            }
            found.sort(Comparator.comparing(StoredCosmetic::createdAt).reversed().thenComparing(StoredCosmetic::id));
            return found;
        }
    }

    public int countByAuthor(String uuid, boolean publicOnly) {
        synchronized (lock) {
            int count = 0;
            for (StoredCosmetic cosmetic : allCosmeticsUnlocked()) {
                if (uuid.equals(cosmetic.authorUuid()) && (!publicOnly || "public".equals(cosmetic.status()))) {
                    count++;
                }
            }
            return count;
        }
    }

    public void saveEquipment(StoredEquipment equipment) {
        synchronized (lock) {
            write(root.resolve("equipment").resolve(equipment.playerUuid() + ".json"), equipment);
        }
    }

    public Optional<StoredEquipment> findEquipment(String uuid) {
        synchronized (lock) {
            Path path = root.resolve("equipment").resolve(uuid + ".json");
            if (!Files.isRegularFile(path)) {
                return Optional.empty();
            }
            return Optional.ofNullable(read(path, StoredEquipment.class));
        }
    }

    public List<StoredReport> reports(String cosmeticId) {
        synchronized (lock) {
            return readReports(cosmeticId);
        }
    }

    /** @return false when this player already reported the cosmetic */
    public boolean addReport(String cosmeticId, StoredReport report) {
        synchronized (lock) {
            List<StoredReport> reports = new ArrayList<>(readReports(cosmeticId));
            for (StoredReport existing : reports) {
                if (report.reporterUuid().equals(existing.reporterUuid())) {
                    return false;
                }
            }
            reports.add(report);
            write(root.resolve("reports").resolve(cosmeticId + ".json"), reports);
            return true;
        }
    }

    public void clearEquipped(String cosmeticId) {
        synchronized (lock) {
            try (Stream<Path> files = Files.list(root.resolve("equipment"))) {
                files.filter(path -> path.getFileName().toString().endsWith(".json")).forEach(path -> {
                    StoredEquipment equipment = read(path, StoredEquipment.class);
                    if (equipment == null || equipment.slots() == null) {
                        return;
                    }
                    Map<String, String> slots = new LinkedHashMap<>(equipment.slots());
                    boolean changed = false;
                    for (Map.Entry<String, String> entry : slots.entrySet()) {
                        if (cosmeticId.equals(entry.getValue())) {
                            entry.setValue(null);
                            changed = true;
                        }
                    }
                    if (changed) {
                        write(path, new StoredEquipment(equipment.playerUuid(), equipment.playerName(), slots, equipment.updatedAt()));
                    }
                });
            } catch (IOException ex) {
                throw new IllegalStateException("Could not update equipment.", ex);
            }
        }
    }

    public void stripModel(StoredCosmetic cosmetic) {
        synchronized (lock) {
            ObjectNode empty = json.object();
            StoredCosmetic stripped = new StoredCosmetic(
                    cosmetic.id(),
                    cosmetic.name(),
                    cosmetic.description(),
                    cosmetic.slot(),
                    cosmetic.authorUuid(),
                    cosmetic.authorName(),
                    0,
                    0,
                    cosmetic.status(),
                    cosmetic.takedownBy(),
                    cosmetic.createdAt(),
                    empty
            );
            write(cosmeticPath(cosmetic.id()), stripped);
        }
    }

    private List<StoredCosmetic> allCosmeticsUnlocked() {
        List<StoredCosmetic> found = new ArrayList<>();
        try (Stream<Path> files = Files.list(root.resolve("cosmetics"))) {
            files.filter(path -> path.getFileName().toString().endsWith(".json")).forEach(path -> {
                StoredCosmetic cosmetic = read(path, StoredCosmetic.class);
                if (cosmetic != null) {
                    found.add(cosmetic);
                }
            });
        } catch (IOException ex) {
            throw new IllegalStateException("Could not list cosmetics.", ex);
        }
        return found;
    }

    private List<StoredReport> readReports(String cosmeticId) {
        Path path = root.resolve("reports").resolve(cosmeticId + ".json");
        if (!Files.isRegularFile(path)) {
            return List.of();
        }
        try {
            JsonNode node = json.mapper().readTree(path.toFile());
            if (node == null || !node.isArray()) {
                return List.of();
            }
            List<StoredReport> reports = new ArrayList<>();
            for (JsonNode item : node) {
                reports.add(json.mapper().convertValue(item, StoredReport.class));
            }
            return reports;
        } catch (IOException ex) {
            return List.of();
        }
    }

    private Path cosmeticPath(String id) {
        return root.resolve("cosmetics").resolve(id + ".json");
    }

    private void write(Path path, Object value) {
        try {
            Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
            Files.write(tmp, json.write(value));
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomic) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not write " + path.getFileName(), ex);
        }
    }

    private <T> T read(Path path, Class<T> type) {
        try {
            return json.mapper().readValue(path.toFile(), type);
        } catch (IOException ex) {
            System.err.println("Skipping unreadable gallery file " + path.getFileName());
            return null;
        }
    }

    private void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not delete " + path.getFileName(), ex);
        }
    }
}
