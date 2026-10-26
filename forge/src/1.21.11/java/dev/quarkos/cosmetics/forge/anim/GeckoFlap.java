package dev.quarkos.cosmetics.forge.anim;

import dev.quarkos.cosmetics.forge.gallery.GalleryClient;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.cache.animation.Keyframe;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class GeckoFlap {
    private static final Logger LOGGER = LoggerFactory.getLogger("quarkos_cosmetics");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static KiteAnim anim;
    private static KiteModel model;
    private static boolean logged;

    private GeckoFlap() {
    }

    public static float degrees(float ageInTicks) {
        double seconds = ageInTicks / 20.0;
        try {
            ensure();
            var clip = model.getBakedAnimation(anim, "idle");
            for (var bone : clip.boneAnimations()) {
                if (!"wing".equals(bone.boneName())) {
                    continue;
                }
                Keyframe[] frames = bone.rotationKeyFrames().zKeyframes();
                if (frames.length == 0) {
                    break;
                }
                double length = Math.max(clip.length(), 0.001);
                float sampled = sample(frames, seconds % length);
                if (!logged) {
                    logged = true;
                    LOGGER.info("geckolib idle clip {}s on {} drives cosmetic motion", length, GalleryClient.configuredBase());
                }
                return sampled;
            }
        } catch (Throwable throwable) {
            if (!logged) {
                logged = true;
                LOGGER.warn("geckolib flap is not ready yet: {}", throwable.toString());
            }
        }
        return (float) Math.sin(seconds * Math.PI) * 28f;
    }

    private static float sample(Keyframe[] frames, double time) {
        for (Keyframe frame : frames) {
            double end = frame.startTime() + frame.length();
            if (time + 1e-4 >= frame.startTime() && time <= end + 1e-4) {
                double span = Math.max(frame.length(), 1e-4);
                double delta = (time - frame.startTime()) / span;
                double start = frame.startValue().get(null);
                double finish = frame.endValue().get(null);
                return (float) (start + (finish - start) * delta);
            }
        }
        return (float) frames[frames.length - 1].endValue().get(null);
    }

    private static void ensure() {
        if (anim == null) {
            anim = new KiteAnim();
            model = new KiteModel();
            anim.getAnimatableInstanceCache();
        }
    }

    private static final class KiteAnim implements GeoAnimatable {
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
            controllers.add(new AnimationController<>("flap", 0, test -> test.setAndContinue(IDLE)));
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }
    }

    private static final class KiteModel extends GeoModel<KiteAnim> {
        @Override
        public Identifier getModelResource(GeoRenderState state) {
            return Identifier.fromNamespaceAndPath("quarkos_cosmetics", "kite.geo");
        }

        @Override
        public Identifier getTextureResource(GeoRenderState state) {
            return Identifier.fromNamespaceAndPath("quarkos_cosmetics", "textures/cosmetics/kite_wings.png");
        }

        @Override
        public Identifier getAnimationResource(KiteAnim animatable) {
            return Identifier.fromNamespaceAndPath("quarkos_cosmetics", "kite.animation");
        }
    }
}
