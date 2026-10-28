package dev.quarkos.cosmetics.render;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.quarkos.cosmetics.config.ClientSettings;
import dev.quarkos.cosmetics.model.CosmeticModel;
import dev.quarkos.cosmetics.model.Slot;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One delivered chick, geometry.chick. Bedrock pixels are scaled by 1/16 once
 * and drawn with cutout, same as the kite wings. The equipment slot is the perch.
 */
public final class Chick {
    public static final String ID = "quarkos:chick";
    private static final Logger LOGGER = LoggerFactory.getLogger("quarkos_cosmetics");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("quarkos_cosmetics", "textures/cosmetics/chick.png");
    private static final String GEO = "/assets/quarkos_cosmetics/geckolib/models/chick.geo.json";
    private static final String ANIMATION = "/assets/quarkos_cosmetics/geckolib/animations/chick.animation.json";
    private static final float[] ZERO = new float[3];
    private static final float[] ONE = {1f, 1f, 1f};
    /** In-bed travel. One name, so a newer walk clip drops in without a path change. */
    private static final String TRAVEL_CLIP = "walk";
    private static final String SETTLE_CLIP = "settle";
    /** 1.25 s from the perch to the chest, inside the 1–1.5 s window. */
    private static final float TRAVEL_TICKS = 25f;
    private static final float HEAD_PX = 8f;
    private static boolean loggedScale;

    private static Model model;
    private static boolean failed;
    private static String heldClip;
    private static Float heldSeconds;
    private static Player bound;
    private static boolean boundHelmet;
    private static boolean boundHat;
    private static final Map<UUID, Mood> MOODS = new LinkedHashMap<>();
    private static String logged = "";

    private Chick() {
    }

    public static boolean isPerch(Slot slot) {
        return slot == Slot.HAT || slot == Slot.SHOULDER_LEFT || slot == Slot.SHOULDER_RIGHT;
    }

    public static boolean draws(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        if (ID.equals(id) || ClientSettings.get().isChickGallery(id)) {
            return true;
        }
        ModelLibrary.Resolved resolved = ModelLibrary.resolve(id);
        if (resolved == null) {
            return false;
        }
        for (CosmeticModel.Element element : resolved.model().elements) {
            if ("geometry_chick".equals(element.name())) {
                return true;
            }
        }
        return false;
    }

    /** Harness hold. A null clip follows the player. A null time plays the clip. */
    public static void hold(String clip, Float seconds) {
        heldClip = clip == null || clip.isBlank() ? null : clip;
        heldSeconds = seconds;
    }

    public static void bind(Player player, boolean helmet, boolean hatLayer) {
        bound = player;
        boundHelmet = helmet;
        boundHat = hatLayer;
    }

    public static void draw(PoseStack pose, CosmeticDrawer.Submitter submitter, int light, float age, Slot slot,
                            ModelPart head, ModelPart body) {
        if (!isPerch(slot)) {
            return;
        }
        Model loaded = load();
        if (loaded == null) {
            return;
        }
        Sample sample = sample(loaded, slot, age);
        if (sample == null) {
            return;
        }
        String announce = sample.clip + "@" + slot.wire;
        if (!announce.equals(logged)) {
            logged = announce;
            LOGGER.info("chick playing {} at {}s on {}", sample.clip, sample.time, slot.wire);
        }
        float scale = chickScale(loaded);
        float contact = sitContact(loaded);
        if (!loggedScale) {
            loggedScale = true;
            float width = bodyWidth(loaded);
            LOGGER.info("chick body {} px scaled by {} to {} px", width, scale, width * scale);
        }
        Place place = place(pose, loaded, slot, head, body, scale, contact);
        pose.pushPose();
        place.part.translateAndRotate(pose);
        pose.scale(1f / 16f, -1f / 16f, 1f / 16f);
        pose.translate(place.x, place.y, place.z);
        if (place.turn != null) {
            pose.mulPose(place.turn);
        }
        pose.scale(scale, scale, scale);
        Matrix4f view = new Matrix4f(pose.last().pose());
        Matrix3f normals = new Matrix3f(pose.last().normal());
        submitter.submit(pose, TEXTURE, false, consumer -> render(consumer, view, normals, light, loaded, sample));
        pose.popPose();
    }

    /**
     * Head and shoulder stay on their parts. In bed the chick walks in body space from that perch
     * to the chest, faces its travel, then lies on the chest for settle and sleep.
     */
    private static Place place(PoseStack pose, Model loaded, Slot slot, ModelPart head, ModelPart body,
                               float scale, float contact) {
        float[] perch = perch(slot, scale, contact);
        ModelPart part = slot == Slot.HAT ? head : body;
        Mood mood = bound == null ? null : MOODS.get(bound.getUUID());
        if (heldClip != null || mood == null || mood.bed == Bed.PERCH) {
            return new Place(part, perch[0], perch[1], perch[2], null);
        }
        Matrix4f toBody = bodyPixels(pose, body);
        Vector3f start = toBody(toBody, anchor(pose, part, perch[0], perch[1], perch[2]));
        Vector3f startUp = axis(pose, toBody, part, perch, 0f, 1f, 0f);
        Vector3f startBeak = axis(pose, toBody, part, perch, 0f, 0f, -1f);
        Vector3f outward = new Vector3f(0f, 0f, -1f);
        Vector3f surface = new Vector3f(0f, -4.5f, -2f);
        Vector3f chest = new Vector3f(surface).fma(0.1f - contact * scale, outward);
        Vector3f control = new Vector3f(chest).add(0f, 8f, -6f);
        boolean settled = mood.bed == Bed.SETTLE || mood.bed == Bed.ASLEEP;
        float s = settled ? 1f : smooth(mood.trip);
        Vector3f pos = settled ? new Vector3f(chest) : bezier(start, control, chest, s);
        Vector3f up;
        Vector3f beak;
        if (settled) {
            up = outward;
            beak = new Vector3f(0f, -1f, 0f);
        } else {
            float upBlend = smooth(Math.min(1f, s / 0.3f));
            up = new Vector3f(startUp).lerp(outward, upBlend);
            if (up.lengthSquared() < 1.0e-6f) {
                up.set(outward);
            }
            up.normalize();
            Vector3f tangent = bezierTangent(start, control, chest, Math.max(0.001f, Math.min(0.999f, s)));
            if (mood.bed == Bed.TO_PERCH) {
                tangent.negate();
            }
            beak = new Vector3f(tangent).fma(-tangent.dot(up), up);
            if (beak.lengthSquared() < 0.04f) {
                beak.set(startBeak).fma(-startBeak.dot(up), up);
            }
            float align = mood.bed == Bed.TO_PERCH || s <= 0.65f ? 0f : (s - 0.65f) / 0.35f;
            if (align > 0f) {
                Vector3f rest = new Vector3f(0f, -1f, 0f).fma(-new Vector3f(0f, -1f, 0f).dot(up), up);
                if (rest.lengthSquared() > 1.0e-4f && beak.lengthSquared() > 1.0e-4f) {
                    rest.normalize();
                    beak.normalize().lerp(rest, align);
                }
            }
        }
        return new Place(body, pos.x, pos.y, pos.z, facing(up, beak));
    }

    /** Feet stay 0.1 px clear. Sit tucks the belly to {@code contact}; scale would lift it, so Y comes back down. */
    private static float[] perch(Slot slot, float scale, float contact) {
        float x = slot == Slot.SHOULDER_RIGHT ? -7f : slot == Slot.SHOULDER_LEFT ? 7f : 0f;
        float y = slot == Slot.HAT ? 32.1f : 24.1f;
        if (slot == Slot.HAT) {
            if (boundHelmet) {
                y += 1f;
            } else if (boundHat) {
                y += 0.5f;
            }
        }
        y -= contact * (scale - 1f);
        return new float[] {x, y - 24f, 0f};
    }

    private static float chickScale(Model loaded) {
        float width = bodyWidth(loaded);
        if (width < 0.001f) {
            return 1f;
        }
        return (HEAD_PX * 2f / 3f) / width;
    }

    private static float bodyWidth(Model loaded) {
        Bone body = loaded.bones.get("body");
        if (body == null) {
            return 4f;
        }
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (Cube cube : body.cubes) {
            min = Math.min(min, cube.ox);
            max = Math.max(max, cube.ox + cube.sx);
        }
        return max - min;
    }

    /** Lowest body corner once the sit tuck is applied. Measured from the body cubes and the sit clip. */
    private static float sitContact(Model loaded) {
        Bone body = loaded.bones.get("body");
        float minY = 0f;
        if (body != null) {
            minY = Float.POSITIVE_INFINITY;
            for (Cube cube : body.cubes) {
                minY = Math.min(minY, cube.oy);
            }
        }
        Clip sit = loaded.clips.get("sit");
        float drop = 0f;
        if (sit != null) {
            Channel channel = sit.bones.get("body");
            if (channel != null && channel.position.times.length > 0) {
                drop = channel.position.sample(0)[1];
            }
        }
        return minY + drop;
    }

    private static void updateBed(Player player, Mood mood, Model loaded, float age) {
        int tick = player.tickCount;
        if (tick == mood.lastBedTick) {
            return;
        }
        int steps = mood.lastBedTick < 0 ? 1 : Math.max(1, tick - mood.lastBedTick);
        mood.lastBedTick = tick;
        boolean sleeping = player.isSleeping();
        Clip settle = loaded.clips.get(SETTLE_CLIP);
        double settleLen = settle == null || settle.length <= 0.0001 ? 0.8 : settle.length;
        int settleTicks = Math.max(1, (int) Math.round(settleLen * 20.0));
        switch (mood.bed) {
            case PERCH -> {
                if (sleeping) {
                    enter(mood, Bed.TO_CHEST, age);
                    mood.trip = 0f;
                }
            }
            case TO_CHEST -> {
                if (!sleeping) {
                    enter(mood, Bed.TO_PERCH, age);
                } else {
                    mood.trip = Math.min(1f, mood.trip + steps / TRAVEL_TICKS);
                    if (mood.trip >= 1f) {
                        enter(mood, Bed.SETTLE, age);
                    }
                }
            }
            case SETTLE -> {
                if (!sleeping) {
                    enter(mood, Bed.TO_PERCH, age);
                } else if (tick - mood.phaseTick >= settleTicks) {
                    enter(mood, Bed.ASLEEP, age);
                }
            }
            case ASLEEP -> {
                if (!sleeping) {
                    enter(mood, Bed.TO_PERCH, age);
                }
            }
            case TO_PERCH -> {
                if (sleeping) {
                    enter(mood, Bed.TO_CHEST, age);
                } else {
                    mood.trip = Math.max(0f, mood.trip - steps / TRAVEL_TICKS);
                    if (mood.trip <= 0f) {
                        enter(mood, Bed.PERCH, age);
                    }
                }
            }
        }
    }

    private static void enter(Mood mood, Bed bed, float age) {
        mood.bed = bed;
        mood.phaseAge = age;
        mood.phaseTick = mood.lastBedTick;
        if (!bed.name().equals(mood.bedLogged)) {
            mood.bedLogged = bed.name();
            LOGGER.info("chick bed {}", bed.name());
        }
    }

    private static Sample bedSample(Model loaded, Mood mood, float age) {
        if (mood.bed == Bed.SETTLE) {
            Clip clip = loaded.clips.get(SETTLE_CLIP);
            double length = clip == null || clip.length <= 0.0001 ? 0.8 : clip.length;
            double time = Math.min(length, Math.max(0.0, (age - mood.phaseAge) / 20.0));
            return new Sample(SETTLE_CLIP, time, null, 0, 1f);
        }
        if (mood.bed == Bed.ASLEEP) {
            Clip clip = loaded.clips.get("sleep");
            double length = clip == null || clip.length <= 0.0001 ? 4 : clip.length;
            return new Sample("sleep", (age / 20.0) % length, null, 0, 1f);
        }
        Clip clip = loaded.clips.get(TRAVEL_CLIP);
        double length = clip == null || clip.length <= 0.0001 ? 0.6 : clip.length;
        String from = null;
        double fromTime = 0;
        float blend = 1f;
        if (mood.bed == Bed.TO_CHEST) {
            float stood = (age - mood.phaseAge) / 20f;
            if (stood < 0.2f) {
                blend = Math.max(0f, stood / 0.2f);
                from = "sit";
                Clip sit = loaded.clips.get("sit");
                double sitLength = sit == null || sit.length <= 0.0001 ? 1 : sit.length;
                fromTime = (age / 20.0) % sitLength;
            }
        }
        return new Sample(TRAVEL_CLIP, (age / 20.0) % length, from, fromTime, blend);
    }

    private static Vector3f anchor(PoseStack pose, ModelPart part, float x, float y, float z) {
        pose.pushPose();
        part.translateAndRotate(pose);
        pose.scale(1f / 16f, -1f / 16f, 1f / 16f);
        pose.translate(x, y, z);
        Vector3f point = new Vector3f();
        pose.last().pose().transformPosition(point);
        pose.popPose();
        return point;
    }

    private static Matrix4f bodyPixels(PoseStack pose, ModelPart body) {
        pose.pushPose();
        body.translateAndRotate(pose);
        pose.scale(1f / 16f, -1f / 16f, 1f / 16f);
        Matrix4f inverse = new Matrix4f(pose.last().pose()).invert();
        pose.popPose();
        return inverse;
    }

    private static Vector3f toBody(Matrix4f inverseBody, Vector3f point) {
        return inverseBody.transformPosition(new Vector3f(point));
    }

    private static Vector3f axis(PoseStack pose, Matrix4f inverseBody, ModelPart part, float[] perch,
                                 float dx, float dy, float dz) {
        Vector3f origin = toBody(inverseBody, anchor(pose, part, perch[0], perch[1], perch[2]));
        Vector3f shifted = toBody(inverseBody, anchor(pose, part, perch[0] + dx, perch[1] + dy, perch[2] + dz));
        return shifted.sub(origin);
    }

    private static Vector3f bezier(Vector3f a, Vector3f b, Vector3f c, float s) {
        float u = 1f - s;
        return new Vector3f(a).mul(u * u).fma(2f * u * s, b).fma(s * s, c);
    }

    private static Vector3f bezierTangent(Vector3f a, Vector3f b, Vector3f c, float s) {
        Vector3f towardControl = new Vector3f(b).sub(a).mul(2f * (1f - s));
        return towardControl.fma(2f * s, new Vector3f(c).sub(b));
    }

    private static float smooth(float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        return clamped * clamped * (3f - 2f * clamped);
    }

    /** Bedrock +Y to {@code up}, beak (−Z) to {@code beak}, in the same space as the perch translate. */
    private static Quaternionf facing(Vector3f upIn, Vector3f beakIn) {
        Vector3f up = new Vector3f(upIn);
        if (up.lengthSquared() < 1.0e-6f) {
            up.set(0f, 1f, 0f);
        }
        up.normalize();
        Vector3f beak = new Vector3f(beakIn);
        if (beak.lengthSquared() < 1.0e-6f) {
            beak.set(0f, 0f, -1f);
        }
        beak.normalize().fma(-beak.dot(up), up);
        if (beak.lengthSquared() < 1.0e-6f) {
            Vector3f hint = Math.abs(up.y) < 0.9f ? new Vector3f(0f, 1f, 0f) : new Vector3f(1f, 0f, 0f);
            beak.set(hint).fma(-hint.dot(up), up);
        }
        beak.normalize();
        Vector3f right = beak.cross(up, new Vector3f());
        if (right.lengthSquared() < 1.0e-6f) {
            right.set(1f, 0f, 0f);
        }
        right.normalize();
        Vector3f back = right.cross(up, new Vector3f()).normalize();
        if (back.dot(beak) > 0f) {
            right.negate();
            back.set(right).cross(up).normalize();
        }
        Matrix3f basis = new Matrix3f(
                right.x, up.x, back.x,
                right.y, up.y, back.y,
                right.z, up.z, back.z);
        return new Quaternionf().setFromUnnormalized(basis);
    }

    private static Sample sample(Model loaded, Slot slot, float age) {
        if (heldClip != null) {
            Clip clip = loaded.clips.get(heldClip);
            double length = clip == null || clip.length <= 0.0001 ? 1 : clip.length;
            double time = heldSeconds != null ? heldSeconds : (age / 20.0) % length;
            return new Sample(heldClip, time, null, 0, 1f);
        }
        if (bound == null) {
            String resting = slot == Slot.HAT ? "sit" : "idle";
            return new Sample(resting, 0, null, 0, 1f);
        }
        Mood mood = MOODS.computeIfAbsent(bound.getUUID(), ignored -> new Mood());
        updateBed(bound, mood, loaded, age);
        if (mood.bed != Bed.PERCH) {
            return bedSample(loaded, mood, age);
        }
        String clip = choose(bound, slot, mood, age);
        if (mood.clip != null && !mood.clip.equals(clip)) {
            mood.fromClip = mood.clip;
            mood.fromTime = mood.time;
            mood.blendTotal = blendTicks(mood.clip, clip);
            mood.blendLeft = mood.blendTotal;
        }
        if (bound.tickCount != mood.lastTick) {
            int steps = mood.lastTick < 0 ? 1 : Math.max(1, bound.tickCount - mood.lastTick);
            mood.lastTick = bound.tickCount;
            mood.blendLeft = Math.max(0, mood.blendLeft - steps);
        }
        mood.clip = clip;
        mood.time = clock(loaded, mood, clip, age);
        float blend = mood.blendTotal <= 0 ? 1f : 1f - mood.blendLeft / (float) mood.blendTotal;
        return new Sample(clip, mood.time, mood.fromClip, mood.fromTime, blend);
    }

    private static String choose(Player player, Slot slot, Mood mood, float age) {
        int tick = player.tickCount;
        boolean hostile = hostile(player);
        boolean hurt = player.hurtTime > 0;
        boolean moving = moving(player);
        boolean sneaking = player.isShiftKeyDown() || player.isCrouching();
        if (moving || hurt || player.isSprinting()) {
            mood.stillSince = tick;
        } else if (mood.stillSince < 0) {
            mood.stillSince = tick;
        }
        boolean sleeping = player.isSleeping() || (!hostile && !hurt && tick - mood.stillSince >= 600);
        boolean wet = player.isInWater();
        boolean rain = player.level().isRainingAt(player.blockPosition());
        if (mood.wasWet && !wet && tick >= mood.shakeReady) {
            start(mood, "shake", age, tick + 100);
        } else if (!mood.wasRaining && rain && !wet && tick >= mood.shakeReady) {
            start(mood, "shake", age, tick + 100);
        }
        mood.wasWet = wet;
        mood.wasRaining = rain;
        boolean eating = player.isUsingItem() && player.getUseItem().has(DataComponents.FOOD);
        boolean levelUp = mood.lastLevel >= 0 && player.experienceLevel > mood.lastLevel;
        boolean xp = mood.lastXp >= 0f && player.experienceProgress > mood.lastXp + 0.02f;
        boolean kill = !mood.sawDeath && deathNearby(player);
        if ((eating && !mood.wasEating) || levelUp || xp || kill) {
            start(mood, "happy", age, mood.shakeReady);
        }
        mood.wasEating = eating;
        mood.lastLevel = player.experienceLevel;
        mood.lastXp = player.experienceProgress;
        mood.sawDeath = deathNearby(player);
        boolean scared = player.fallDistance > 3f
                || (player.isFallFlying() && player.getDeltaMovement().y < -0.4)
                || player.isOnFire()
                || player.isInLava()
                || (player.getHealth() > 0f && player.getHealth() < 6f);
        if (mood.wasScared && !scared) {
            mood.alertUntil = Math.max(mood.alertUntil, tick + 40);
        }
        mood.wasScared = scared;
        if (hostile || hurt) {
            mood.alertUntil = Math.max(mood.alertUntil, tick + 60);
        }
        String resting = slot == Slot.HAT || sneaking || !moving ? "sit" : "idle";
        if ((resting.equals("idle") || resting.equals("sit")) && mood.oneshot == null && tick >= mood.nextPeck) {
            start(mood, "peck", age, mood.shakeReady);
            mood.nextPeck = tick + 160 + player.getRandom().nextInt(241);
        } else if (mood.nextPeck < 0) {
            mood.nextPeck = tick + 160 + player.getRandom().nextInt(241);
        }
        finishOneshot(mood, age);
        String clip = resting;
        if ("peck".equals(mood.oneshot)) {
            clip = "peck";
        }
        if (sleeping) {
            clip = "sleep";
        }
        if (tick < mood.alertUntil) {
            clip = "alert";
        }
        if ("happy".equals(mood.oneshot)) {
            clip = "happy";
        }
        if ("shake".equals(mood.oneshot)) {
            clip = "shake";
        }
        if (scared) {
            clip = "scared";
        }
        return clip;
    }

    private static void start(Mood mood, String clip, float age, int shakeReady) {
        mood.oneshot = clip;
        mood.oneshotAge = age;
        if ("shake".equals(clip)) {
            mood.shakeReady = shakeReady;
        }
    }

    private static void finishOneshot(Mood mood, float age) {
        if (mood.oneshot == null || model == null) {
            return;
        }
        Clip clip = model.clips.get(mood.oneshot);
        double length = clip == null ? 1 : clip.length;
        if ((age - mood.oneshotAge) / 20.0 >= length) {
            mood.oneshot = null;
        }
    }

    private static double clock(Model loaded, Mood mood, String clip, float age) {
        Clip animation = loaded.clips.get(clip);
        double length = animation == null || animation.length <= 0.0001 ? 1 : animation.length;
        if (clip.equals(mood.oneshot)) {
            return Math.min(length, Math.max(0, (age - mood.oneshotAge) / 20.0));
        }
        return (age / 20.0) % length;
    }

    private static int blendTicks(String from, String to) {
        if (from == null) {
            return 0;
        }
        if ("scared".equals(to)) {
            return 2;
        }
        if ("scared".equals(from)) {
            return 4;
        }
        if ("peck".equals(to) || "happy".equals(to) || "shake".equals(to)) {
            return 2;
        }
        return 5;
    }

    private static boolean moving(Player player) {
        double dx = player.getDeltaMovement().x;
        double dz = player.getDeltaMovement().z;
        return dx * dx + dz * dz > 0.0009 || player.isSprinting() || player.isSwimming() || Math.abs(player.xxa) > 0.01f || Math.abs(player.zza) > 0.01f;
    }

    private static boolean hostile(Player player) {
        AABB box = player.getBoundingBox().inflate(8);
        return !player.level().getEntitiesOfClass(Monster.class, box, entity -> entity.isAlive() && entity.distanceToSqr(player) <= 64).isEmpty();
    }

    private static boolean deathNearby(Player player) {
        AABB box = player.getBoundingBox().inflate(8);
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, box, living -> living != player && living.deathTime > 0 && living.deathTime < 5)) {
            return true;
        }
        return false;
    }

    private static void render(VertexConsumer consumer, Matrix4f view, Matrix3f normals, int light, Model loaded, Sample sample) {
        Map<String, Matrix4f> worlds = new LinkedHashMap<>();
        Map<String, Matrix3f> turns = new LinkedHashMap<>();
        for (Bone bone : loaded.bones.values()) {
            world(loaded, bone, sample, worlds, turns);
        }
        for (Bone bone : loaded.bones.values()) {
            Matrix4f local = worlds.get(bone.name);
            Matrix3f turn = turns.get(bone.name);
            if (local == null || bone.cubes.isEmpty()) {
                continue;
            }
            Matrix4f combined = new Matrix4f(view).mul(local);
            for (Cube cube : bone.cubes) {
                cube(consumer, combined, normals, turn, light, loaded, cube);
            }
        }
    }

    private static Matrix4f world(Model loaded, Bone bone, Sample sample, Map<String, Matrix4f> worlds, Map<String, Matrix3f> turns) {
        Matrix4f cached = worlds.get(bone.name);
        if (cached != null) {
            return cached;
        }
        Matrix4f matrix = new Matrix4f();
        if (bone.parent != null) {
            Bone parent = loaded.bones.get(bone.parent);
            if (parent != null) {
                matrix.set(world(loaded, parent, sample, worlds, turns));
            }
        }
        float[] position = sample.position(loaded, bone.name);
        float[] rotation = sample.rotation(loaded, bone.name);
        float[] scale = sample.scale(loaded, bone.name);
        float rx = (float) Math.toRadians(bone.rotation[0] + rotation[0]);
        float ry = (float) Math.toRadians(bone.rotation[1] + rotation[1]);
        float rz = (float) Math.toRadians(bone.rotation[2] + rotation[2]);
        matrix.translate(position[0], position[1], position[2]);
        matrix.translate(bone.pivot[0], bone.pivot[1], bone.pivot[2]);
        matrix.rotateZ(rz).rotateY(ry).rotateX(rx);
        matrix.scale(scale[0], scale[1], scale[2]);
        matrix.translate(-bone.pivot[0], -bone.pivot[1], -bone.pivot[2]);
        worlds.put(bone.name, matrix);
        Matrix3f turn = new Matrix3f(matrix);
        if (Math.abs(turn.determinant()) > 0.0001f) {
            turn.invert().transpose();
        }
        turns.put(bone.name, turn);
        return matrix;
    }

    private static void cube(VertexConsumer consumer, Matrix4f view, Matrix3f poseNormals, Matrix3f turn, int light,
                             Model loaded, Cube cube) {
        float inf = cube.inflate;
        float x0 = cube.ox - inf;
        float x1 = cube.ox + cube.sx + inf;
        float y0 = cube.oy - inf;
        float y1 = cube.oy + cube.sy + inf;
        float z0 = cube.oz - inf;
        float z1 = cube.oz + cube.sz + inf;
        boolean mirror = cube.mirror;
        face(consumer, view, poseNormals, turn, light, loaded, cube, mirror, mirror ? "negX" : "posX",
                x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0);
        face(consumer, view, poseNormals, turn, light, loaded, cube, mirror, mirror ? "posX" : "negX",
                x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0);
        face(consumer, view, poseNormals, turn, light, loaded, cube, mirror, "negZ",
                x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1);
        face(consumer, view, poseNormals, turn, light, loaded, cube, mirror, "posZ",
                x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1);
        face(consumer, view, poseNormals, turn, light, loaded, cube, mirror, "posY",
                x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0);
        face(consumer, view, poseNormals, turn, light, loaded, cube, mirror, "negY",
                x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0);
    }

    private static void face(VertexConsumer consumer, Matrix4f view, Matrix3f poseNormals, Matrix3f turn, int light,
                             Model loaded, Cube cube, boolean mirror, String side,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float nx, float ny, float nz) {
        double w = Math.floor(cube.sx);
        double h = Math.floor(cube.sy);
        double d = Math.floor(cube.sz);
        double u;
        double v;
        double uw;
        double vh;
        switch (side) {
            case "posX" -> {
                u = cube.u + d + w;
                v = cube.v + d;
                uw = d;
                vh = h;
            }
            case "negX" -> {
                u = cube.u;
                v = cube.v + d;
                uw = d;
                vh = h;
            }
            case "negZ" -> {
                u = cube.u + d;
                v = cube.v + d;
                uw = w;
                vh = h;
            }
            case "posZ" -> {
                u = cube.u + d + w + d;
                v = cube.v + d;
                uw = w;
                vh = h;
            }
            case "posY" -> {
                u = cube.u + d;
                v = cube.v;
                uw = w;
                vh = d;
            }
            default -> {
                u = cube.u + d + w;
                v = cube.v + d;
                uw = w;
                vh = -d;
            }
        }
        float u0 = (float) (u / loaded.textureWidth);
        float v0 = (float) (v / loaded.textureHeight);
        float u1 = (float) ((u + uw) / loaded.textureWidth);
        float v1 = (float) ((v + vh) / loaded.textureHeight);
        if (!mirror) {
            float swap = u0;
            u0 = u1;
            u1 = swap;
        }
        Vector3f normal = new Vector3f(nx, ny, nz);
        if (turn != null) {
            turn.transform(normal);
        }
        poseNormals.transform(normal);
        if (normal.lengthSquared() > 0.0001f) {
            normal.normalize();
        }
        vertex(consumer, view, x0, y0, z0, u0, v0, light, normal);
        vertex(consumer, view, x1, y1, z1, u1, v0, light, normal);
        vertex(consumer, view, x2, y2, z2, u1, v1, light, normal);
        vertex(consumer, view, x3, y3, z3, u0, v1, light, normal);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f view, float x, float y, float z,
                               float u, float v, int light, Vector3f normal) {
        consumer.addVertex(view, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(normal.x, normal.y, normal.z);
    }

    private static Model load() {
        if (model != null || failed) {
            return model;
        }
        try {
            model = Model.parse();
        } catch (RuntimeException ex) {
            failed = true;
            LOGGER.error("chick failed to load", ex);
        }
        return model;
    }

    private static final class Sample {
        final String clip;
        final double time;
        final String fromClip;
        final double fromTime;
        final float blend;

        Sample(String clip, double time, String fromClip, double fromTime, float blend) {
            this.clip = clip;
            this.time = time;
            this.fromClip = fromClip;
            this.fromTime = fromTime;
            this.blend = blend;
        }

        float[] position(Model loaded, String bone) {
            return mix(loaded, bone, false, false);
        }

        float[] rotation(Model loaded, String bone) {
            return mix(loaded, bone, true, false);
        }

        float[] scale(Model loaded, String bone) {
            return mix(loaded, bone, false, true);
        }

        private float[] mix(Model loaded, String bone, boolean rotation, boolean scale) {
            float[] to = channel(loaded.clips.get(clip), bone, rotation, scale, time);
            if (fromClip == null || blend >= 0.999f) {
                return to;
            }
            float[] from = channel(loaded.clips.get(fromClip), bone, rotation, scale, fromTime);
            float t = Math.max(0f, Math.min(1f, blend));
            return new float[] {
                    from[0] + (to[0] - from[0]) * t,
                    from[1] + (to[1] - from[1]) * t,
                    from[2] + (to[2] - from[2]) * t
            };
        }

        private static float[] channel(Clip clip, String bone, boolean rotation, boolean scale, double time) {
            if (clip == null) {
                return scale ? ONE : ZERO;
            }
            Channel channel = clip.bones.get(bone);
            if (channel == null) {
                return scale ? ONE : ZERO;
            }
            Track track = scale ? channel.scale : rotation ? channel.rotation : channel.position;
            if (track.times.length == 0) {
                return scale ? ONE : ZERO;
            }
            return track.sample(time);
        }
    }

    private enum Bed {
        PERCH, TO_CHEST, SETTLE, ASLEEP, TO_PERCH
    }

    private static final class Place {
        final ModelPart part;
        final float x;
        final float y;
        final float z;
        final Quaternionf turn;

        Place(ModelPart part, float x, float y, float z, Quaternionf turn) {
            this.part = part;
            this.x = x;
            this.y = y;
            this.z = z;
            this.turn = turn;
        }
    }

    private static final class Mood {
        private String clip;
        private String fromClip;
        private double time;
        private double fromTime;
        private int blendLeft;
        private int blendTotal;
        private int lastTick = -1;
        private Bed bed = Bed.PERCH;
        private int lastBedTick = -1;
        private int phaseTick = -1;
        private float phaseAge;
        private float trip;
        private String bedLogged = "";
        private int stillSince = -1;
        private int nextPeck = -1;
        private int alertUntil;
        private boolean wasScared;
        private boolean wasWet;
        private boolean wasRaining;
        private int shakeReady;
        private String oneshot;
        private float oneshotAge;
        private int lastLevel = -1;
        private float lastXp = -1f;
        private boolean wasEating;
        private boolean sawDeath;
    }

    private static final class Model {
        final float textureWidth;
        final float textureHeight;
        final Map<String, Bone> bones;
        final Map<String, Clip> clips;

        Model(float textureWidth, float textureHeight, Map<String, Bone> bones, Map<String, Clip> clips) {
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
            this.bones = bones;
            this.clips = clips;
        }

        static Model parse() {
            JsonNode geo = tree(GEO).path("minecraft:geometry").path(0);
            JsonNode description = geo.path("description");
            Map<String, Bone> bones = new LinkedHashMap<>();
            for (JsonNode node : geo.path("bones")) {
                Bone bone = Bone.read(node);
                bones.put(bone.name, bone);
            }
            Map<String, Clip> clips = new LinkedHashMap<>();
            JsonNode animations = tree(ANIMATION).path("animations");
            animations.fieldNames().forEachRemaining(name -> {
                String shortName = name.startsWith("animation.chick.") ? name.substring("animation.chick.".length()) : name;
                clips.put(shortName, Clip.read(animations.get(name)));
            });
            return new Model(description.path("texture_width").floatValue(), description.path("texture_height").floatValue(), bones, clips);
        }

        private static JsonNode tree(String path) {
            try (InputStream stream = Chick.class.getResourceAsStream(path)) {
                if (stream == null) {
                    throw new IllegalStateException("missing " + path);
                }
                return JSON.readTree(stream);
            } catch (java.io.IOException ex) {
                throw new IllegalStateException("could not read " + path, ex);
            }
        }
    }

    private static final class Bone {
        final String name;
        final String parent;
        final float[] pivot;
        final float[] rotation;
        final List<Cube> cubes;

        Bone(String name, String parent, float[] pivot, float[] rotation, List<Cube> cubes) {
            this.name = name;
            this.parent = parent;
            this.pivot = pivot;
            this.rotation = rotation;
            this.cubes = cubes;
        }

        static Bone read(JsonNode node) {
            List<Cube> cubes = new ArrayList<>();
            boolean boneMirror = node.path("mirror").asBoolean(false);
            for (JsonNode cube : node.path("cubes")) {
                cubes.add(Cube.read(cube, boneMirror));
            }
            String parent = node.hasNonNull("parent") ? node.get("parent").asText() : null;
            return new Bone(node.path("name").asText(), parent, vec(node.get("pivot")), vec(node.get("rotation")), cubes);
        }
    }

    private static final class Cube {
        final float ox;
        final float oy;
        final float oz;
        final float sx;
        final float sy;
        final float sz;
        final float u;
        final float v;
        final float inflate;
        final boolean mirror;

        Cube(float ox, float oy, float oz, float sx, float sy, float sz, float u, float v, float inflate, boolean mirror) {
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.u = u;
            this.v = v;
            this.inflate = inflate;
            this.mirror = mirror;
        }

        static Cube read(JsonNode node, boolean boneMirror) {
            float[] origin = vec(node.get("origin"));
            float[] size = vec(node.get("size"));
            float[] uv = vec2(node.get("uv"));
            return new Cube(origin[0], origin[1], origin[2], size[0], size[1], size[2], uv[0], uv[1],
                    node.path("inflate").floatValue(), node.path("mirror").asBoolean(boneMirror));
        }
    }

    private static final class Clip {
        final double length;
        final Map<String, Channel> bones;

        Clip(double length, Map<String, Channel> bones) {
            this.length = length;
            this.bones = bones;
        }

        static Clip read(JsonNode node) {
            Map<String, Channel> bones = new LinkedHashMap<>();
            JsonNode boneNode = node.path("bones");
            boneNode.fieldNames().forEachRemaining(bone -> bones.put(bone, Channel.read(boneNode.get(bone))));
            return new Clip(node.path("animation_length").asDouble(), bones);
        }
    }

    private static final class Channel {
        final Track rotation;
        final Track position;
        final Track scale;

        Channel(Track rotation, Track position, Track scale) {
            this.rotation = rotation;
            this.position = position;
            this.scale = scale;
        }

        static Channel read(JsonNode node) {
            return new Channel(Track.read(node.get("rotation")), Track.read(node.get("position")), Track.read(node.get("scale")));
        }
    }

    private static final class Track {
        final float[] times;
        final float[][] values;
        final boolean catmull;

        Track(float[] times, float[][] values, boolean catmull) {
            this.times = times;
            this.values = values;
            this.catmull = catmull;
        }

        float[] sample(double time) {
            if (times.length == 0) {
                return ZERO;
            }
            if (time <= times[0]) {
                return values[0];
            }
            int index = times.length - 2;
            for (int i = 0; i < times.length - 1; i++) {
                if (time <= times[i + 1]) {
                    index = i;
                    break;
                }
            }
            float span = times[index + 1] - times[index];
            float progress = span <= 0.000001f ? 1f : (float) ((time - times[index]) / span);
            if (!catmull) {
                return lerp(values[index], values[index + 1], progress);
            }
            float[] p0 = values[Math.max(0, index - 1)];
            float[] p1 = values[index];
            float[] p2 = values[Math.min(values.length - 1, index + 1)];
            float[] p3 = values[Math.min(values.length - 1, index + 2)];
            float[] out = new float[3];
            float t = progress;
            float t2 = t * t;
            float t3 = t2 * t;
            for (int axis = 0; axis < 3; axis++) {
                out[axis] = 0.5f * ((2f * p1[axis])
                        + (-p0[axis] + p2[axis]) * t
                        + (2f * p0[axis] - 5f * p1[axis] + 4f * p2[axis] - p3[axis]) * t2
                        + (-p0[axis] + 3f * p1[axis] - 3f * p2[axis] + p3[axis]) * t3);
            }
            return out;
        }

        private static float[] lerp(float[] start, float[] end, float progress) {
            return new float[] {
                    start[0] + (end[0] - start[0]) * progress,
                    start[1] + (end[1] - start[1]) * progress,
                    start[2] + (end[2] - start[2]) * progress
            };
        }

        static Track read(JsonNode node) {
            if (node == null || !node.isObject()) {
                return new Track(new float[0], new float[0][], false);
            }
            List<Float> times = new ArrayList<>();
            List<float[]> values = new ArrayList<>();
            boolean[] catmull = {false};
            node.fieldNames().forEachRemaining(key -> {
                times.add(Float.parseFloat(key));
                JsonNode value = node.get(key);
                if (value != null && value.isObject() && "catmullrom".equals(value.path("lerp_mode").asText(""))) {
                    catmull[0] = true;
                }
                values.add(vec(value));
            });
            Integer[] order = new Integer[times.size()];
            for (int i = 0; i < order.length; i++) {
                order[i] = i;
            }
            java.util.Arrays.sort(order, java.util.Comparator.comparingDouble(index -> times.get(index)));
            float[] sortedTimes = new float[order.length];
            float[][] sortedValues = new float[order.length][];
            for (int i = 0; i < order.length; i++) {
                sortedTimes[i] = times.get(order[i]);
                sortedValues[i] = values.get(order[i]);
            }
            return new Track(sortedTimes, sortedValues, catmull[0]);
        }
    }

    private static float[] vec(JsonNode node) {
        if (node == null || node.isNull()) {
            return new float[3];
        }
        if (node.isObject()) {
            if (node.has("post")) {
                return vec(node.get("post"));
            }
            if (node.has("pre")) {
                return vec(node.get("pre"));
            }
            if (node.has("vector")) {
                return vec(node.get("vector"));
            }
            return new float[3];
        }
        if (!node.isArray()) {
            return new float[3];
        }
        return new float[] {num(node.get(0)), num(node.get(1)), num(node.get(2))};
    }

    private static float[] vec2(JsonNode node) {
        if (node == null || !node.isArray()) {
            return new float[2];
        }
        return new float[] {num(node.get(0)), num(node.get(1))};
    }

    private static float num(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0f;
        }
        if (node.isNumber()) {
            return node.floatValue();
        }
        try {
            return Float.parseFloat(node.asText().trim());
        } catch (NumberFormatException ex) {
            return 0f;
        }
    }
}
