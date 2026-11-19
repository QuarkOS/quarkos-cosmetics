package dev.quarkos.cosmetics.render;

import dev.quarkos.cosmetics.model.Slot;
import dev.quarkos.cosmetics.model.Worn.Anchor;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PackedCatalogTest {
    @Test
    void everyCatalogEntryLoadsWithItsDeclaredSlotAndVisibleGeometry() throws Exception {
        List<PackedCatalog.Result> results = PackedCatalog.load(PackedCatalogTest::resource);
        assertEquals(58, results.size());
        Set<Slot> slots = EnumSet.noneOf(Slot.class);
        for (PackedCatalog.Result result : results) {
            assertNotNull(result.item(), () -> result.id() + " " + result.error());
            PackedCatalog.Item item = result.item();
            assertEquals(item.entry().slot(), Slot.parse(result.slot()).orElseThrow());
            slots.add(item.entry().slot());
            assertTrue(item.cubes() > 0, result.id());
            assertTrue(item.frames().width() > 0 && item.frames().height() > 0, result.id());
            if (item.blockbench() != null) {
                assertTrue(item.blockbench().roots().stream().map(BlockbenchModel.Root::bone)
                        .flatMap(PackedCatalogTest::faces).findAny().isPresent(), result.id());
            }
        }
        assertEquals(EnumSet.allOf(Slot.class), slots);
        assertTrue(results.stream().noneMatch(result -> result.id().equals("quarkos:snowman_head_hat")));
    }

    @Test
    void loadsTheSecondRootAndUsesOnlyLoopingClips() throws Exception {
        List<PackedCatalog.Result> results = PackedCatalog.load(PackedCatalogTest::resource);
        PackedCatalog.Item dragon = item(results, "dragon_wings");
        assertEquals(Anchor.BODY, dragon.blockbench().roots().getFirst().anchor());
        assertEquals("animation.dragon_wings.flap", dragon.animation());
        assertEquals("animation.knight_helmet_visor.idle", item(results, "knight_helmet_visor").animation());
        PackedCatalog.Item owl = item(results, "owl_shoulder_buddy");
        assertEquals("animation.owl_shoulder_buddy.head_turn+animation.owl_shoulder_buddy.blink", owl.animation());
        assertEquals(Slot.SHOULDER_RIGHT, owl.entry().slot());
    }

    @Test
    void detectsMixedAlphaAndKeepsBaseAndGlowStripsInStep() throws Exception {
        List<PackedCatalog.Result> results = PackedCatalog.load(PackedCatalogTest::resource);
        BlockbenchModel.Root fishbowl = item(results, "fish_bowl_helmet").blockbench().roots().getFirst();
        List<BlockbenchModel.Face> faces = faces(fishbowl.bone()).toList();
        assertTrue(faces.stream().anyMatch(BlockbenchModel.Face::translucent));
        assertTrue(faces.stream().anyMatch(face -> !face.translucent()));

        PackedCatalog.Item core = item(results, "energy_core_chest");
        assertEquals(0, core.frames().at(0).y());
        assertEquals(64, core.frames().at(2).y());
        assertEquals(core.frames().at(39), core.glowFrames().at(39));
        assertEquals(0, core.frames().at(40).y());
        assertTrue(core.blockbench().roots().stream().map(BlockbenchModel.Root::bone)
                .flatMap(PackedCatalogTest::faces).anyMatch(BlockbenchModel.Face::glowing));
    }

    @Test
    void aMissingTextureProducesAFailureForItsCatalogEntry() throws Exception {
        List<PackedCatalog.Result> results = PackedCatalog.load(path -> path.equals("textures/cosmetics/wings.png")
                ? null : resource(path));
        var kite = results.stream().filter(result -> result.id().equals("quarkos:kite_wings")).findFirst().orElseThrow();
        assertNull(kite.item());
        assertTrue(kite.error().contains("textures/cosmetics/wings.png"));
        assertEquals(1, results.stream().filter(result -> result.item() == null).count());
    }

    private static PackedCatalog.Item item(List<PackedCatalog.Result> results, String id) {
        return results.stream().filter(result -> result.id().equals("quarkos:" + id)).findFirst().orElseThrow().item();
    }

    private static java.util.stream.Stream<BlockbenchModel.Face> faces(BlockbenchModel.Bone bone) {
        return java.util.stream.Stream.concat(bone.cubes().stream().flatMap(cube -> cube.faces().values().stream()),
                bone.children().stream().flatMap(PackedCatalogTest::faces));
    }

    private static InputStream resource(String path) {
        return PackedCatalogTest.class.getResourceAsStream("/assets/quarkos_cosmetics/" + path);
    }
}
