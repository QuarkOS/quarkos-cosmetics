package dev.quarkos.cosmetics.forge.anim;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.keyframe.Keyframe;
import software.bernie.geckolib.loading.math.MathValue;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

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
            var clip = model.getAnimation(anim, "idle");
            for (var bone : clip.boneAnimations()) {
                if (!"wing".equals(bone.boneName())) {
                    continue;
                }
                List<Keyframe<MathValue>> frames = bone.rotationKeyFrames().zKeyframes();
                if (frames.isEmpty()) {
                    break;
                }
                double length = Math.max(clip.length(), 0.001);
                float sampled = sample(frames, seconds % length);
                if (!logged) {
                    logged = true;
                    LOGGER.info("geckolib idle clip {}s drives cosmetic motion", length);
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

    private static float sample(List<Keyframe<MathValue>> frames, double time) {
        double cursor = 0;
        for (Keyframe<MathValue> frame : frames) {
            double span = Math.max(frame.length(), 1e-4);
            if (time <= cursor + span) {
                double delta = (time - cursor) / span;
                double start = frame.startValue().get();
                double finish = frame.endValue().get();
                return (float) (start + (finish - start) * delta);
            }
            cursor += span;
        }
        Keyframe<MathValue> last = frames.get(frames.size() - 1);
        return (float) last.endValue().get();
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
            controllers.add(new AnimationController<>(this, "flap", 0, state -> state.setAndContinue(IDLE)));
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }

        @Override
        public double getTick(Object object) {
            return 0;
        }
    }

    private static final class KiteModel extends GeoModel<KiteAnim> {
        private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
                "quarkos_cosmetics", "geckolib/models/kite.geo.json");
        private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
                "quarkos_cosmetics", "textures/cosmetics/kite_wings.png");
        private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(
                "quarkos_cosmetics", "geckolib/animations/kite.animation.json");

        @Override
        public ResourceLocation getModelResource(KiteAnim animatable) {
            return MODEL;
        }

        @Override
        public ResourceLocation getTextureResource(KiteAnim animatable) {
            return TEXTURE;
        }

        @Override
        public ResourceLocation getAnimationResource(KiteAnim animatable) {
            return ANIMATION;
        }
    }
}
