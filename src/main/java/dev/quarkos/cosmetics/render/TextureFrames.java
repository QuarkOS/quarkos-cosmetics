package dev.quarkos.cosmetics.render;

import com.fasterxml.jackson.databind.JsonNode;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Selects a frame inside a standalone entity texture. Minecraft animates atlas sprites separately. */
record TextureFrames(int width, int height, int frameWidth, int frameHeight, List<Frame> sequence, int duration) {
    record Frame(int x, int y, int ticks) {
    }

    static TextureFrames read(BufferedImage image, int uvWidth, int uvHeight, JsonNode animation) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (animation.isMissingNode()) {
            return new TextureFrames(width, height, width, height, List.of(new Frame(0, 0, 1)), 1);
        }

        int frameWidth = animation.path("width").asInt(uvWidth);
        int frameHeight = animation.path("height").asInt(uvHeight);
        int ticks = animation.path("frametime").asInt(1);
        if (frameWidth <= 0 || frameHeight <= 0 || width % frameWidth != 0 || height % frameHeight != 0 || ticks <= 0) {
            throw new IllegalArgumentException("invalid texture frame size or duration");
        }
        int columns = width / frameWidth;
        int count = columns * (height / frameHeight);
        JsonNode frames = animation.path("frames");
        if (!frames.isMissingNode() && (!frames.isArray() || frames.isEmpty())) {
            throw new IllegalArgumentException("texture frames must be a nonempty array");
        }
        List<Frame> sequence = new ArrayList<>();
        int duration = 0;
        for (int i = 0; i < (frames.isMissingNode() ? count : frames.size()); i++) {
            JsonNode node = frames.path(i);
            int index = node.isMissingNode() ? i : node.isIntegralNumber() ? node.intValue() : node.path("index").asInt(-1);
            int time = node.path("time").asInt(ticks);
            if (index < 0 || index >= count || time <= 0) {
                throw new IllegalArgumentException("invalid texture frame " + index);
            }
            sequence.add(new Frame(index % columns * frameWidth, index / columns * frameHeight, time));
            duration = Math.addExact(duration, time);
        }
        return new TextureFrames(width, height, frameWidth, frameHeight, List.copyOf(sequence), duration);
    }

    Frame at(float ageTicks) {
        int elapsed = Math.floorMod((long) Math.floor(ageTicks), duration);
        for (Frame frame : sequence) {
            if (elapsed < frame.ticks) {
                return frame;
            }
            elapsed -= frame.ticks;
        }
        throw new IllegalStateException("texture has no frame at " + ageTicks);
    }
}
