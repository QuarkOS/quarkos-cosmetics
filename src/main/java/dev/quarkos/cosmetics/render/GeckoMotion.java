package dev.quarkos.cosmetics.render;

import dev.quarkos.cosmetics.model.CosmeticModel;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Wing flap and pet idle come from GeckoLib's baked animation files.
 * A sine wave is only the stand-in until that cache has loaded.
 */
public final class GeckoMotion {
    private static final Logger LOGGER = LoggerFactory.getLogger("quarkos_cosmetics");
    private static long nextMissLog;
    private static boolean announced;

    private GeckoMotion() {
    }

    public static float flapDegrees(String name, float age) {
        if (CosmeticModel.motion(name) != CosmeticModel.Motion.FLAP) {
            return 0f;
        }
        String key = name.toLowerCase(Locale.ROOT);
        boolean right = key.contains("right");
        Float sampled = sample(flapBone(key, right), age, true);
        if (sampled != null) {
            return sampled;
        }
        float sign = right ? -1f : 1f;
        return sign * (float) Math.sin(age * 0.28f) * 20f;
    }

    /** Spread-only pitch from the same flap clip. Idle stays at the folded element pitch. */
    public static float flapPitch(String name, float age) {
        if (CosmeticModel.motion(name) != CosmeticModel.Motion.FLAP) {
            return 0f;
        }
        String key = name.toLowerCase(Locale.ROOT);
        Float sampled = sample(flapBone(key, key.contains("right")), age, false);
        return sampled == null ? 0f : sampled;
    }

    /** Leading edge and each feather are their own bone, so the flap can fan them. */
    private static String flapBone(String key, boolean right) {
        String side = right ? "wing_right" : "wing_left";
        if (key.contains("lead")) {
            return side + "_lead";
        }
        if (key.contains("feather_d")) {
            return side + "_d";
        }
        if (key.contains("feather_c")) {
            return side + "_c";
        }
        if (key.contains("feather_b")) {
            return side + "_b";
        }
        if (key.contains("feather_a")) {
            return side + "_a";
        }
        return side;
    }

    public static float idleDegrees(String name, float age) {
        if (CosmeticModel.motion(name) != CosmeticModel.Motion.IDLE) {
            return 0f;
        }
        Float sampled = sample("idle", age, false);
        if (sampled != null) {
            return sampled;
        }
        return (float) Math.sin(age * 0.16f) * 12f;
    }

    private static Float sample(String bone, float age, boolean zAxis) {
        try {
            Double radians = read(zAxis ? "flap" : "idle", bone, age, zAxis);
            if (radians == null) {
                noteMiss("missing " + bone);
                return null;
            }
            if (!announced) {
                announced = true;
                LOGGER.info("geckolib animation driving {} {}", zAxis ? "flap" : "idle", bone);
            }
            float degrees = (float) Math.toDegrees(radians);
            return zAxis ? degrees : -degrees;
        } catch (RuntimeException ex) {
            noteMiss(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            return null;
        }
    }

    private static void noteMiss(String reason) {
        long now = System.currentTimeMillis();
        if (now < nextMissLog) {
            return;
        }
        nextMissLog = now + 5000L;
        LOGGER.info("geckolib motion falling back to a sine ({})", reason);
    }

    //? if >=1.21.11 {
    private static Double read(String clip, String bone, float age, boolean zAxis) {
        var cache = software.bernie.geckolib.cache.GeckoLibResources.getBakedAnimations();
        software.bernie.geckolib.cache.animation.Animation animation = null;
        Identifier file = Identifier.fromNamespaceAndPath("quarkos_cosmetics", "motions");
        try {
            animation = cache.getAnimation(file, new Identifier[0], clip);
        } catch (RuntimeException ignored) {
            for (var entry : cache.cache().entrySet()) {
                if (entry.getKey().getPath().contains("motion")) {
                    animation = entry.getValue().getAnimation(clip);
                    if (animation != null) {
                        break;
                    }
                }
            }
        }
        if (animation == null) {
            return null;
        }
        double length = animation.length();
        double time = length <= 0.0001 ? 0 : (age / 20.0) % length;
        for (var animated : animation.boneAnimations()) {
            if (!bone.equals(animated.boneName())) {
                continue;
            }
            var frames = zAxis ? animated.rotationKeyFrames().zKeyframes() : animated.rotationKeyFrames().xKeyframes();
            return sampleSeconds(frames, time);
        }
        return null;
    }

    private static double sampleSeconds(software.bernie.geckolib.cache.animation.Keyframe[] frames, double time) {
        software.bernie.geckolib.cache.animation.Keyframe span = null;
        for (var frame : frames) {
            if (frame.length() <= 0.000001) {
                continue;
            }
            double begin = frame.startTime() - frame.length();
            if (time >= begin - 0.0001 && time <= frame.startTime() + 0.0001) {
                span = frame;
                break;
            }
        }
        if (span == null) {
            return frames.length == 0 ? 0 : frames[frames.length - 1].endValue().get(null);
        }
        double begin = span.startTime() - span.length();
        double progress = span.length() <= 0.000001 ? 1 : (time - begin) / span.length();
        progress = Math.max(0, Math.min(1, progress));
        double start = span.startValue().get(null);
        double end = span.endValue().get(null);
        return start + (end - start) * progress;
    }
    //?} else {
    private static Double read(String clip, String bone, float age, boolean zAxis) {
        software.bernie.geckolib.loading.object.BakedAnimations baked = null;
        for (var entry : software.bernie.geckolib.cache.GeckoLibCache.getBakedAnimations().entrySet()) {
            if (entry.getKey().getPath().contains("motion")) {
                baked = entry.getValue();
                break;
            }
        }
        if (baked == null) {
            return null;
        }
        software.bernie.geckolib.animation.Animation animation = baked.getAnimation(clip);
        if (animation == null) {
            return null;
        }
        double length = animation.length();
        double time = length <= 0.0001 ? 0 : age % length;
        for (var animated : animation.boneAnimations()) {
            if (!bone.equals(animated.boneName())) {
                continue;
            }
            var frames = zAxis ? animated.rotationKeyFrames().zKeyframes() : animated.rotationKeyFrames().xKeyframes();
            return sampleTicks(frames, time);
        }
        return null;
    }

    private static double sampleTicks(java.util.List<? extends software.bernie.geckolib.animation.keyframe.Keyframe<?>> frames, double time) {
        double cursor = 0;
        software.bernie.geckolib.animation.keyframe.Keyframe<?> last = null;
        for (var frame : frames) {
            last = frame;
            double next = cursor + frame.length();
            if (frame.length() > 0.000001 && time <= next + 0.0001) {
                double progress = (time - cursor) / frame.length();
                progress = Math.max(0, Math.min(1, progress));
                double start = frame.startValue().get();
                double end = frame.endValue().get();
                return start + (end - start) * progress;
            }
            cursor = next;
        }
        return last == null ? 0 : last.endValue().get();
    }
    //?}
}
