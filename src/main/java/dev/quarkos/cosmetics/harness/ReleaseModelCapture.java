package dev.quarkos.cosmetics.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.quarkos.cosmetics.model.Slot;
import dev.quarkos.cosmetics.render.ModelLibrary;
import dev.quarkos.cosmetics.render.ReleaseModelGeometry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

final class ReleaseModelCapture {
    private static boolean verifyWardrobe;

    record Frame(String path, String id, Slot slot, float yaw, boolean plate, boolean sneak,
                 float age, String clip, String source, String action, boolean perch) {
        boolean equipped() {
            return !id.isEmpty();
        }
    }

    private ReleaseModelCapture() {
    }

    static boolean enabled() {
        return System.getProperty("quarkos_cosmetics.releasePlan") != null;
    }

    static boolean verifyWardrobe() {
        return verifyWardrobe;
    }

    static List<Frame> prepare(Path shots) {
        var models = ModelLibrary.packed();
        try {
            Path plan = Path.of(System.getProperty("quarkos_cosmetics.releasePlan"));
            JsonNode root = new ObjectMapper().readTree(plan.toFile());
            var expected = new HashSet<String>();
            root.path("inventory").forEach(id -> expected.add(id.asText()));
            var loaded = new HashSet<>(models.stream().map(ModelLibrary.Resolved::id).toList());
            if (root.path("schema").asInt() != 1 || expected.isEmpty() || !loaded.equals(expected)) {
                throw new IllegalStateException("Release plan inventory " + expected + " differs from loaded models " + loaded);
            }
            List<Frame> frames = new ArrayList<>();
            for (JsonNode entry : root.path("frames")) {
                JsonNode camera = root.path("cameras").required(entry.required("camera").asText());
                String path = entry.required("path").asText();
                String id = entry.required("id").asText();
                Slot slot = Slot.parse(entry.required("slot").asText()).orElseThrow();
                if (!path.matches("[a-z0-9_/-]+") || !id.isEmpty() && !loaded.contains(id)) {
                    throw new IllegalStateException("Invalid release capture " + path + " for " + id);
                }
                frames.add(new Frame(path, id, slot, (float) camera.required("yaw").asDouble(),
                        entry.path("plate").asBoolean(), "sneak".equals(camera.path("pose").asText()),
                        (float) entry.required("age").asDouble(), entry.path("clip").asText(),
                        entry.path("source").asText(), entry.path("action").asText(), entry.path("perch").asBoolean()));
            }
            if (frames.isEmpty()) {
                throw new IllegalStateException("Release plan has no frames");
            }
            verifyWardrobe = root.path("models").size() == models.size();
            ReleaseModelGeometry.write(shots.resolve("geometry.txt"), models);
            return List.copyOf(frames);
        } catch (IOException e) {
            throw new IllegalStateException("Could not prepare release capture", e);
        }
    }
}
