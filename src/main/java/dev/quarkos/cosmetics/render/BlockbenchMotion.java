package dev.quarkos.cosmetics.render;

import dev.quarkos.cosmetics.render.BlockbenchModel.ActionClip;
import dev.quarkos.cosmetics.render.BlockbenchModel.Clip;

import java.util.EnumMap;

final class BlockbenchMotion {
    private static final float TICKS_PER_SECOND = 20f;
    private static final float BLEND_TICKS = 3f;

    enum Action {
        SNEAK, UNSNEAK, JUMP, HIT, WALK;

        static Action fromClip(String name) {
            return switch (name.substring(name.lastIndexOf('.') + 1)) {
                case "on_sneak" -> SNEAK;
                case "on_unsneak" -> UNSNEAK;
                case "on_jump" -> JUMP;
                case "on_hit" -> HIT;
                case "walk" -> WALK;
                default -> null;
            };
        }
    }

    record State(int tick, boolean crouched, boolean grounded, double y, int hurtTime, boolean walking) {
    }

    record Playback(Clip clip, float seconds, float weight) {
    }

    private final EnumMap<Action, Integer> started = new EnumMap<>(Action.class);
    private State previous;

    void observe(State current) {
        if (previous != null && current.tick == previous.tick) {
            return;
        }
        if (previous == null || current.tick < previous.tick) {
            started.clear();
            if (current.crouched) started.put(Action.SNEAK, current.tick);
            if (current.walking) started.put(Action.WALK, current.tick);
        } else {
            if (current.crouched != previous.crouched) {
                started.put(current.crouched ? Action.SNEAK : Action.UNSNEAK, current.tick);
            }
            if (previous.grounded && !current.grounded && current.y > previous.y + .04) {
                started.put(Action.JUMP, current.tick);
            }
            if (current.hurtTime > previous.hurtTime) {
                started.put(Action.HIT, current.tick);
            }
            if (current.walking && !previous.walking) {
                started.put(Action.WALK, current.tick);
            }
        }
        previous = current;
    }

    Playback playback(BlockbenchModel model, float age) {
        if (previous == null || model.actions().isEmpty()) {
            return null;
        }
        Playback active = null;
        int latest = Integer.MIN_VALUE;
        for (var entry : model.actions().entrySet()) {
            Action action = entry.getKey();
            Integer tick = started.get(action);
            if (tick == null || tick < latest
                    || action == Action.WALK && !previous.walking
                    || action == Action.UNSNEAK && previous.crouched) {
                continue;
            }
            float elapsed = age - tick;
            boolean blendIn = action != Action.UNSNEAK;
            float weight = blendIn ? Math.clamp(elapsed / BLEND_TICKS, 0f, 1f) : 1f;
            ActionClip clip = entry.getValue();
            if (action == Action.SNEAK && !previous.crouched) {
                int released = started.getOrDefault(Action.UNSNEAK, previous.tick);
                elapsed = released - tick;
                weight = Math.clamp(elapsed / BLEND_TICKS, 0f, 1f)
                        * (1f - Math.clamp((age - released) / BLEND_TICKS, 0f, 1f));
            }
            Playback candidate = sample(action, clip, elapsed, weight);
            if (candidate != null) {
                active = candidate;
                latest = tick;
            }
        }
        return active;
    }

    static Playback sample(BlockbenchModel model, String name, float ticks) {
        Action action = Action.fromClip(name);
        ActionClip clip = action == null ? null : model.actions().get(action);
        if (clip == null) {
            return null;
        }
        float weight = action == Action.UNSNEAK ? 1f : Math.clamp(ticks / BLEND_TICKS, 0f, 1f);
        return sample(action, clip, ticks, weight);
    }

    private static Playback sample(Action action, ActionClip actionClip, float ticks, float weight) {
        Clip clip = actionClip.clip();
        float duration = clip.length() * TICKS_PER_SECOND;
        if (ticks < 0 || duration <= 0 || weight <= 0) {
            return null;
        }
        if (action == Action.WALK) {
            return new Playback(clip, clip.time(ticks), weight);
        }
        if (!actionClip.hold()) {
            weight *= Math.clamp((duration - ticks) / BLEND_TICKS, 0f, 1f);
        }
        return weight == 0 ? null : new Playback(clip, Math.min(ticks, duration) / TICKS_PER_SECOND, weight);
    }
}
