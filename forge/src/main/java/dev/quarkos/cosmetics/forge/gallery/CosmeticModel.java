package dev.quarkos.cosmetics.forge.gallery;

import java.util.List;

public record CosmeticModel(String id, String name, String slot, List<Cube> cubes, byte[] png) {
    public record Cube(float x0, float y0, float z0, float x1, float y1, float z1) {
    }
}
