package dev.quarkos.cosmetics.render;

import java.util.ArrayList;
import java.util.List;

/** Extrudes opaque pixels from a 64×64 skin's second layer. Player heads are not handled here. */
public final class SkinShell {
    public enum Part {
        HEAD, BODY, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG
    }

    public record Voxel(Part part, float x, float y, float z, float u, float v) {
    }

    private SkinShell() {
    }

    public static List<Voxel> build(int width, int height, int[] argb, boolean hat, boolean jacket,
                                     boolean rightSleeve, boolean leftSleeve, boolean rightPants, boolean leftPants) {
        if (width != 64 || height != 64 || argb == null || argb.length < width * height) {
            return List.of();
        }
        List<Voxel> voxels = new ArrayList<>();
        if (hat) {
            stamp(voxels, argb, width, 40, 8, 8, 8, (dx, dy) -> new Voxel(Part.HEAD, -4f + dx, 7f - dy, -4.55f, 40 + dx, 8 + dy));
            stamp(voxels, argb, width, 56, 8, 8, 8, (dx, dy) -> new Voxel(Part.HEAD, 3.55f - dx, 7f - dy, 4.55f, 56 + dx, 8 + dy));
            stamp(voxels, argb, width, 40, 0, 8, 8, (dx, dy) -> new Voxel(Part.HEAD, -4f + dx, 8.55f, -4f + dy, 40 + dx, dy));
        }
        if (jacket) {
            stamp(voxels, argb, width, 20, 36, 8, 12, (dx, dy) -> new Voxel(Part.BODY, -4f + dx, 11f - dy, -2.55f, 20 + dx, 36 + dy));
        }
        if (rightSleeve) {
            stamp(voxels, argb, width, 44, 36, 4, 12, (dx, dy) -> new Voxel(Part.RIGHT_ARM, -3f + dx, 9f - dy, -2.55f, 44 + dx, 36 + dy));
        }
        if (leftSleeve) {
            stamp(voxels, argb, width, 52, 52, 4, 12, (dx, dy) -> new Voxel(Part.LEFT_ARM, -1f + dx, 9f - dy, -2.55f, 52 + dx, 52 + dy));
        }
        if (rightPants) {
            stamp(voxels, argb, width, 4, 36, 4, 12, (dx, dy) -> new Voxel(Part.RIGHT_LEG, -2f + dx, 11f - dy, -2.55f, 4 + dx, 36 + dy));
        }
        if (leftPants) {
            stamp(voxels, argb, width, 4, 52, 4, 12, (dx, dy) -> new Voxel(Part.LEFT_LEG, -2f + dx, 11f - dy, -2.55f, 4 + dx, 52 + dy));
        }
        return voxels;
    }

    private static void stamp(List<Voxel> voxels, int[] argb, int width, int u, int v, int w, int h, Placer placer) {
        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                int pixel = argb[(v + dy) * width + (u + dx)];
                if (((pixel >>> 24) & 0xFF) < 16) {
                    continue;
                }
                voxels.add(placer.place(dx, dy));
            }
        }
    }

    @FunctionalInterface
    private interface Placer {
        Voxel place(int dx, int dy);
    }
}
