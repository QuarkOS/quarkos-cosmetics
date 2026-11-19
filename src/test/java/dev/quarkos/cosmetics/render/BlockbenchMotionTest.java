package dev.quarkos.cosmetics.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockbenchMotionTest {
    @Test
    void onlyAnUpwardTakeoffStartsTheJumpClipAndItExpires() throws Exception {
        var jetpack = load("jetpack");
        var motion = new BlockbenchMotion();
        motion.observe(state(100, false, true, 64.0, 0));
        assertNull(motion.playback(jetpack, 100));
        motion.observe(state(101, false, false, 63.9, 0));
        assertNull(motion.playback(jetpack, 104));
        motion.observe(state(110, false, true, 63.0, 0));
        motion.observe(state(111, false, false, 63.4, 0));
        var jump = motion.playback(jetpack, 117);
        assertNotNull(jump);
        assertTrue(jump.clip().name().endsWith(".on_jump"));
        assertEquals(.3f, jump.seconds(), .001f);
        assertNull(motion.playback(jetpack, 130));
    }

    @Test
    void keepsTheFoldedPoseUntilStandingAndPlaysTheAuthoredRelease() throws Exception {
        var wings = load("dragon_wings");
        var motion = new BlockbenchMotion();
        motion.observe(state(0, false, true, 64.0, 0));
        motion.observe(state(1, true, true, 64.0, 0));
        var folded = motion.playback(wings, 50);
        assertNotNull(folded);
        assertTrue(folded.clip().name().endsWith(".on_sneak"));
        assertEquals(.4f, folded.seconds(), .001f);
        assertEquals(1f, folded.weight());

        motion.observe(state(51, false, true, 64.0, 0));
        var unfolding = motion.playback(wings, 51);
        assertNotNull(unfolding);
        assertTrue(unfolding.clip().name().endsWith(".on_unsneak"));
        assertEquals(1f, unfolding.weight());
        assertNull(motion.playback(wings, 60));
    }

    @Test
    void continuingDamageDoesNotRestartTheHitAnimation() throws Exception {
        var ears = load("bunny_ears");
        var motion = new BlockbenchMotion();
        motion.observe(state(0, false, true, 64.0, 0));
        motion.observe(state(1, false, true, 64.0, 10));
        motion.observe(state(5, false, true, 64.0, 6));
        var hit = motion.playback(ears, 5);
        assertNotNull(hit);
        assertTrue(hit.clip().name().endsWith(".on_hit"));
        assertEquals(.2f, hit.seconds(), .001f);
        assertNull(motion.playback(ears, 18));
    }

    private static BlockbenchMotion.State state(int tick, boolean sneak, boolean onGround, double y, int hurt) {
        return new BlockbenchMotion.State(tick, sneak, onGround, y, hurt, false);
    }

    private static BlockbenchModel load(String id) throws Exception {
        return PackedCatalog.load(path -> BlockbenchMotionTest.class.getResourceAsStream("/assets/quarkos_cosmetics/" + path))
                .stream().filter(result -> result.id().equals("quarkos:" + id)).findFirst().orElseThrow().item().blockbench();
    }
}
