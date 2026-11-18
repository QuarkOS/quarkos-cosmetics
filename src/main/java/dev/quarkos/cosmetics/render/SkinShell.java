package dev.quarkos.cosmetics.render;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Builds the six overlay boxes in vanilla ModelPart coordinates, in skin texels. */
public final class SkinShell {
    static final int SKIN_SIZE = 64;
    static final float DEPTH = 0.5f;
    // Vanilla's entity shader discards alpha below 0.1.
    private static final int MIN_ALPHA = 26;

    public enum Part {
        HEAD, BODY, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG
    }

    public enum Face {
        // The low three corner bits select the maximum coordinate on X, Y and Z.
        DOWN(0, -1, 0, 5, 4, 0, 1),
        UP(0, 1, 0, 3, 2, 6, 7),
        WEST(-1, 0, 0, 0, 4, 6, 2),
        EAST(1, 0, 0, 5, 1, 3, 7),
        NORTH(0, 0, -1, 1, 0, 2, 3),
        SOUTH(0, 0, 1, 4, 5, 7, 6);

        final int x;
        final int y;
        final int z;
        private final int[] corners;

        Face(int x, int y, int z, int... corners) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.corners = corners;
        }

        int bit() {
            return 1 << ordinal();
        }

        int corner(int index) {
            return corners[index];
        }

        Face opposite() {
            return switch (this) {
                case DOWN -> UP;
                case UP -> DOWN;
                case WEST -> EAST;
                case EAST -> WEST;
                case NORTH -> SOUTH;
                case SOUTH -> NORTH;
            };
        }
    }

    /** The minimum texel footprint on the undeformed box and the faces of its extrusion. */
    public record Voxel(Face face, int x, int y, int z, int u, int v, int exposedFaces) {
        public boolean exposes(Face side) {
            return (exposedFaces & side.bit()) != 0;
        }

        float minX() { return x + minimumOffset(face.x); }
        float minY() { return y + minimumOffset(face.y); }
        float minZ() { return z + minimumOffset(face.z); }
        float maxX() { return x + maximumOffset(face.x); }
        float maxY() { return y + maximumOffset(face.y); }
        float maxZ() { return z + maximumOffset(face.z); }

        private static float minimumOffset(int normal) {
            return Math.min(normal, 0) * DEPTH;
        }

        private static float maximumOffset(int normal) {
            return normal == 0 ? 1f : Math.max(normal, 0) * DEPTH;
        }
    }

    private SkinShell() {
    }

    public static Map<Part, List<Voxel>> build(int width, int height, int[] argb, boolean slim) {
        if (width != SKIN_SIZE || height != SKIN_SIZE || argb == null || argb.length < width * height) {
            return Map.of();
        }
        int armWidth = slim ? 3 : 4;
        Map<Part, List<Voxel>> boxes = new EnumMap<>(Part.class);
        boxes.put(Part.HEAD, box(argb, 32, 0, -4, -8, -4, 8, 8, 8));
        boxes.put(Part.BODY, box(argb, 16, 32, -4, 0, -2, 8, 12, 4));
        boxes.put(Part.LEFT_ARM, box(argb, 48, 48, -1, -2, -2, armWidth, 12, 4));
        boxes.put(Part.RIGHT_ARM, box(argb, 40, 32, 1 - armWidth, -2, -2, armWidth, 12, 4));
        boxes.put(Part.LEFT_LEG, box(argb, 0, 48, -2, 0, -2, 4, 12, 4));
        boxes.put(Part.RIGHT_LEG, box(argb, 0, 32, -2, 0, -2, 4, 12, 4));
        return Map.copyOf(boxes);
    }

    private static List<Voxel> box(int[] argb, int u, int v, int x, int y, int z,
                                   int width, int height, int depth) {
        List<Voxel> voxels = new ArrayList<>();
        int frontU = u + depth;
        int eastU = frontU + width;
        int sideV = v + depth;
        int maxX = x + width;
        int maxY = y + height;
        int maxZ = z + depth;

        // Vanilla model coordinates have Y down. V increases towards -Z on both horizontal faces.
        stamp(voxels, argb, Face.DOWN, frontU, v, width, depth, x, y, maxZ - 1, Face.EAST, Face.NORTH);
        stamp(voxels, argb, Face.UP, eastU, v, width, depth, x, maxY, maxZ - 1, Face.EAST, Face.NORTH);
        stamp(voxels, argb, Face.WEST, u, sideV, depth, height, x, y, maxZ - 1, Face.NORTH, Face.UP);
        stamp(voxels, argb, Face.NORTH, frontU, sideV, width, height, x, y, z, Face.EAST, Face.UP);
        stamp(voxels, argb, Face.EAST, eastU, sideV, depth, height, maxX, y, z, Face.SOUTH, Face.UP);
        stamp(voxels, argb, Face.SOUTH, eastU + depth, sideV, width, height, maxX - 1, y, maxZ, Face.WEST, Face.UP);
        return List.copyOf(voxels);
    }

    private static void stamp(List<Voxel> voxels, int[] argb, Face face, int u, int v, int width, int height,
                              int x, int y, int z, Face uAxis, Face vAxis) {
        for (int row = 0; row < height; row++) {
            int rowV = v + row;
            for (int column = 0; column < width; column++) {
                int texelU = u + column;
                if (!visible(argb, texelU, rowV)) {
                    continue;
                }
                int faces = face.bit();
                if (column == 0 || !visible(argb, texelU - 1, rowV)) {
                    faces |= uAxis.opposite().bit();
                }
                if (column == width - 1 || !visible(argb, texelU + 1, rowV)) {
                    faces |= uAxis.bit();
                }
                if (row == 0 || !visible(argb, texelU, rowV - 1)) {
                    faces |= vAxis.opposite().bit();
                }
                if (row == height - 1 || !visible(argb, texelU, rowV + 1)) {
                    faces |= vAxis.bit();
                }
                voxels.add(new Voxel(face,
                        x + column * uAxis.x + row * vAxis.x,
                        y + column * uAxis.y + row * vAxis.y,
                        z + column * uAxis.z + row * vAxis.z,
                        texelU, rowV, faces));
            }
        }
    }

    private static boolean visible(int[] argb, int u, int v) {
        return (argb[v * SKIN_SIZE + u] >>> 24) >= MIN_ALPHA;
    }
}
