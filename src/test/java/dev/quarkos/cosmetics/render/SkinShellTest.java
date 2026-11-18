package dev.quarkos.cosmetics.render;

import dev.quarkos.cosmetics.render.SkinShell.Face;
import dev.quarkos.cosmetics.render.SkinShell.Part;
import dev.quarkos.cosmetics.render.SkinShell.Voxel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinShellTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void allOverlayTexelsLandOnTheMatchingModelPart(boolean slim) {
        int[] pixels = new int[64 * 64];
        Arrays.fill(pixels, 0xFFFFFFFF);
        Map<Part, List<Voxel>> shell = SkinShell.build(64, 64, pixels, slim);

        checkBox(shell.get(Part.HEAD), -4, -8, -4, 8, 8, 8, 40, 0);
        checkBox(shell.get(Part.BODY), -4, 0, -2, 8, 12, 4, 20, 32);
        checkBox(shell.get(Part.LEFT_ARM), -1, -2, -2, slim ? 3 : 4, 12, 4, 52, 48);
        checkBox(shell.get(Part.RIGHT_ARM), slim ? -2 : -3, -2, -2, slim ? 3 : 4, 12, 4, 44, 32);
        checkBox(shell.get(Part.LEFT_LEG), -2, 0, -2, 4, 12, 4, 4, 48);
        checkBox(shell.get(Part.RIGHT_LEG), -2, 0, -2, 4, 12, 4, 4, 32);
        assertEquals(slim ? 1568 : 1632, shell.values().stream().mapToInt(List::size).sum());
    }

    @Test
    void onlyVisibleOverlayTexelsProduceVoxels() {
        int[] pixels = new int[64 * 64];
        pixels[35 * 64 + 4] = 0x00FFFFFF; // Fully transparent, in the pants' top face.
        pixels[36 * 64 + 4] = 0x19FFFFFF; // Below the entity shader's alpha threshold.
        pixels[36 * 64 + 5] = 0x1AFFFFFF; // Just above the threshold.
        pixels[36 * 64 + 6] = 0xFFFFFFFF;
        pixels[16 * 64 + 4] = 0xFFFFFFFF; // Base skin.
        pixels[33 * 64 + 39] = 0xFFFFFFFF; // Unused skin area.
        Map<Part, List<Voxel>> shell = SkinShell.build(64, 64, pixels, false);

        List<Voxel> voxels = shell.get(Part.RIGHT_LEG);
        assertEquals(List.of(5, 6), voxels.stream().map(Voxel::u).toList());
        assertEquals(2, shell.values().stream().mapToInt(List::size).sum());
        Voxel first = voxels.getFirst();
        assertTrue(first.exposes(Face.NORTH));
        assertTrue(first.exposes(Face.WEST));
        assertFalse(first.exposes(Face.EAST));
        assertFalse(first.exposes(Face.SOUTH));
        assertFalse(voxels.getLast().exposes(Face.WEST));
    }

    @Test
    void invalidSkinDimensionsAndMissingPixelsProduceNoShell() {
        assertTrue(SkinShell.build(64, 32, new int[64 * 32], false).isEmpty());
        assertTrue(SkinShell.build(64, 64, new int[64 * 64 - 1], false).isEmpty());
        assertTrue(SkinShell.build(64, 64, null, true).isEmpty());
    }

    @Test
    void bothTrianglesOfEveryFacePointOutward() {
        for (Face face : Face.values()) {
            int[] normal = {face.x, face.y, face.z};
            for (int i = 1; i <= 2; i++) {
                int[] a = edge(face.corner(0), face.corner(i));
                int[] b = edge(face.corner(0), face.corner(i + 1));
                assertArrayEquals(normal, new int[] {
                        a[1] * b[2] - a[2] * b[1],
                        a[2] * b[0] - a[0] * b[2],
                        a[0] * b[1] - a[1] * b[0]
                }, face.name());
            }
        }
    }

    private static void checkBox(List<Voxel> voxels, int x, int y, int z,
                                 int width, int height, int depth, int frontU, int topV) {
        Map<Integer, Voxel> byTexel = voxels.stream()
                .collect(Collectors.toMap(voxel -> voxel.v() * 64 + voxel.u(), Function.identity()));
        assertEquals(2 * (width * height + width * depth + height * depth), byTexel.size());

        for (int row = 0; row < height; row++) {
            int v = topV + depth + row;
            for (int i = 0; i < width; i++) {
                checkTexel(byTexel, frontU + i, v, Face.NORTH, x + i, y + row, z);
                checkTexel(byTexel, frontU + width + depth + i, v, Face.SOUTH,
                        x + width - 1 - i, y + row, z + depth);
            }
            for (int i = 0; i < depth; i++) {
                checkTexel(byTexel, frontU - depth + i, v, Face.WEST, x, y + row, z + depth - 1 - i);
                checkTexel(byTexel, frontU + width + i, v, Face.EAST, x + width, y + row, z + i);
            }
        }
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < depth; j++) {
                checkTexel(byTexel, frontU + i, topV + j, Face.DOWN, x + i, y, z + depth - 1 - j);
                checkTexel(byTexel, frontU + width + i, topV + j, Face.UP, x + i, y + height, z + depth - 1 - j);
            }
        }
    }

    private static void checkTexel(Map<Integer, Voxel> byTexel, int u, int v, Face face, int x, int y, int z) {
        Voxel voxel = byTexel.get(v * 64 + u);
        assertNotNull(voxel, () -> "Missing " + face + " texel (" + u + ", " + v + ")");
        assertEquals(face, voxel.face());
        assertArrayEquals(new int[] {x, y, z}, new int[] {voxel.x(), voxel.y(), voxel.z()});
        assertTrue(voxel.exposes(face));
        assertFalse(voxel.exposes(face.opposite()));
        checkBounds(x, face.x, voxel.minX(), voxel.maxX());
        checkBounds(y, face.y, voxel.minY(), voxel.maxY());
        checkBounds(z, face.z, voxel.minZ(), voxel.maxZ());
    }

    private static void checkBounds(int footprint, int direction, float min, float max) {
        assertEquals(direction < 0 ? footprint - 0.5f : footprint, min);
        assertEquals(footprint + (direction < 0 ? 0f : direction > 0 ? 0.5f : 1f), max);
    }

    private static int[] edge(int from, int to) {
        return new int[] {(to & 1) - (from & 1), (to >> 1 & 1) - (from >> 1 & 1), (to >> 2) - (from >> 2)};
    }
}
