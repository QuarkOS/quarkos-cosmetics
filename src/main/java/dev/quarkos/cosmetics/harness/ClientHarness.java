package dev.quarkos.cosmetics.harness;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import dev.quarkos.cosmetics.config.ClientSettings;
import dev.quarkos.cosmetics.config.WardrobeScreen;
import dev.quarkos.cosmetics.gallery.EquipmentMirror;
import dev.quarkos.cosmetics.gallery.GalleryClient;
import dev.quarkos.cosmetics.model.ModelException;
import dev.quarkos.cosmetics.model.Slot;
import dev.quarkos.cosmetics.render.Chick;
import dev.quarkos.cosmetics.render.KiteWings;
import dev.quarkos.cosmetics.render.ModelLibrary;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Headless portrait pass. Third person, HUD hidden, one cosmetic at a time, front then back.
 * The last frame is another player wearing cosmetics published through the local gallery.
 */
public final class ClientHarness {
    private static final Logger LOGGER = LoggerFactory.getLogger("quarkos_cosmetics");
    /** Third-person distance and look-down used by {@code CameraZoomMixin} while the harness is armed. */
    public static float portraitDistance = 2.55f;
    public static float portraitDrop = 0.42f;
    /** Degrees added to the player's yaw to swing the harness camera off the back. 0 stays behind. */
    public static float portraitOrbit;
    /** When set, the camera sits at this pose instead of the third-person rig. Sleep uses it. */
    public static boolean portraitLock;
    public static double lockX;
    public static double lockY;
    public static double lockZ;
    public static float lockYaw;
    public static float lockPitch;
    private static final List<Shot> SHOTS = List.of(
            new Shot("kite-wings-front", Slot.BACK, "quarkos:kite_wings", true, false, 0f, false),
            new Shot("kite-wings-back", Slot.BACK, "quarkos:kite_wings", false, false, 0f, false),
            new Shot("kite-wings-side", Slot.BACK, "quarkos:kite_wings", false, false, 72f, false),
            new Shot("kite-wings-three-quarter", Slot.BACK, "quarkos:kite_wings", false, false, 38f, false),
            new Shot("kite-wings-folded-back", Slot.BACK, "quarkos:kite_wings", false, false, 0f, false),
            new Shot("kite-wings-flying", Slot.BACK, "quarkos:kite_wings", false, false, 0f, false),
            new Shot("kite-wings-flap", Slot.BACK, "quarkos:kite_wings", false, false, 0f, true),
            new Shot("lantern-cap-front", Slot.HAT, "quarkos:lantern_cap", true, false, 0f, false),
            new Shot("lantern-cap-back", Slot.HAT, "quarkos:lantern_cap", false, false, 0f, false),
            new Shot("fox-ears-front", Slot.EARS, "quarkos:fox_tips", true, false, 0f, false),
            new Shot("fox-ears-back", Slot.EARS, "quarkos:fox_tips", false, false, 0f, false),
            new Shot("ribbon-tail-front", Slot.TAIL, "quarkos:ribbon_tail", true, false, 0f, false),
            new Shot("ribbon-tail-back", Slot.TAIL, "quarkos:ribbon_tail", false, false, 0f, false),
            new Shot("ink-wren-front", Slot.SHOULDER_LEFT, "quarkos:ink_wren", true, false, 0f, false),
            new Shot("ink-wren-back", Slot.SHOULDER_LEFT, "quarkos:ink_wren", false, false, 0f, false),
            new Shot("pebble-moth-front", Slot.SHOULDER_RIGHT, "quarkos:pebble_moth", true, false, 0f, false),
            new Shot("pebble-moth-back", Slot.SHOULDER_RIGHT, "quarkos:pebble_moth", false, false, 0f, false),
            new Shot("paper-boat-front", Slot.HAT, "quarkos:paper_boat", true, false, 0f, false),
            new Shot("paper-boat-back", Slot.HAT, "quarkos:paper_boat", false, false, 0f, false),
            new Shot("crescent-circlet-front", Slot.HAT, "quarkos:crescent_circlet", true, false, 0f, false),
            new Shot("crescent-circlet-back", Slot.HAT, "quarkos:crescent_circlet", false, false, 0f, false),
            new Shot("jack-o-lantern-front", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 0f, false),
            new Shot("jack-o-lantern-back", Slot.HAT, "quarkos:jack_o_lantern_mask", false, false, 0f, false),
            new Shot("moth-tufts-front", Slot.EARS, "quarkos:moth_tufts", true, false, 0f, false),
            new Shot("moth-tufts-back", Slot.EARS, "quarkos:moth_tufts", false, false, 0f, false),
            new Shot("ribbon-bows-front", Slot.EARS, "quarkos:ribbon_bows", true, false, 0f, false),
            new Shot("ribbon-bows-back", Slot.EARS, "quarkos:ribbon_bows", false, false, 0f, false),
            new Shot("moss-pebble-front", Slot.SHOULDER_LEFT, "quarkos:moss_pebble", true, false, 0f, false),
            new Shot("moss-pebble-back", Slot.SHOULDER_LEFT, "quarkos:moss_pebble", false, false, 0f, false),
            new Shot("copper-bell-front", Slot.SHOULDER_RIGHT, "quarkos:copper_bell", true, false, 0f, false),
            new Shot("copper-bell-back", Slot.SHOULDER_RIGHT, "quarkos:copper_bell", false, false, 0f, false),
            new Shot("folded-cloak-front", Slot.BACK, "quarkos:folded_cloak", true, false, 0f, false),
            new Shot("folded-cloak-back", Slot.BACK, "quarkos:folded_cloak", false, false, 0f, false),
            new Shot("paper-fan-front", Slot.BACK, "quarkos:paper_fan", true, false, 0f, false),
            new Shot("paper-fan-back", Slot.BACK, "quarkos:paper_fan", false, false, 0f, false),
            new Shot("fern-plume-front", Slot.TAIL, "quarkos:fern_plume", true, false, 0f, false),
            new Shot("fern-plume-back", Slot.TAIL, "quarkos:fern_plume", false, false, 0f, false),
            new Shot("bead-chain-front", Slot.TAIL, "quarkos:bead_chain", true, false, 0f, false),
            new Shot("bead-chain-back", Slot.TAIL, "quarkos:bead_chain", false, false, 0f, false),
            new Shot("pet-chick-front", Slot.HAT, Chick.ID, true, false, 0f, false),
            new Shot("pet-chick-back", Slot.HAT, Chick.ID, false, false, 0f, false),
            new Shot("chick-idle", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, false),
            new Shot("chick-idle-clip", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, true),
            new Shot("chick-peck", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, false),
            new Shot("chick-peck-clip", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, true),
            new Shot("chick-sit", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, false),
            new Shot("chick-sit-clip", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, true),
            new Shot("chick-sleep", Slot.HAT, Chick.ID, false, false, 0f, false),
            new Shot("chick-sleep-clip", Slot.HAT, Chick.ID, false, false, 0f, true),
            new Shot("chick-scared", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, false),
            new Shot("chick-scared-clip", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, true),
            new Shot("chick-alert", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, false),
            new Shot("chick-alert-clip", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, true),
            new Shot("chick-happy", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, false),
            new Shot("chick-happy-clip", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, true),
            new Shot("chick-shake", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, false),
            new Shot("chick-shake-clip", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, true),
            new Shot("chick-head", Slot.HAT, Chick.ID, false, false, 0f, false),
            new Shot("chick-shoulder", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, false),
            new Shot("chick-head-side", Slot.HAT, Chick.ID, false, false, 0f, false),
            new Shot("chick-shoulder-side", Slot.SHOULDER_LEFT, Chick.ID, false, false, 0f, false),
            new Shot("chick-chest-asleep", Slot.HAT, Chick.ID, false, false, 0f, false),
            new Shot("chick-to-chest", Slot.HAT, Chick.ID, false, false, 0f, false)
    );

    private static List<Shot> active;
    private static State state = State.WAIT_TITLE;
    private static int ticks;
    private static boolean armed;
    private static boolean shotQueued;
    private static boolean frameEndShots;
    private static Path pendingShot;
    private static boolean studio;
    private static int shotIndex;
    private static int settledAt = -1;
    private static int screenAt = -1;
    private static boolean pairStarted;
    private static int pairShot;
    private static int filmIndex = -1;
    private static int filmStartTick = -1;
    private static volatile boolean pairReady;
    private static volatile boolean pairFailed;
    private static volatile String pairKey = "";
    private static volatile UUID pairId;
    private static volatile String adaToken = "";
    private static volatile String chickHatId = "";
    private static volatile String chickShoulderId = "";
    private static RemotePlayer guest;

    private ClientHarness() {
    }

    public static void tick(Minecraft client) {
        if (!Boolean.getBoolean("quarkos_cosmetics.harness")) {
            return;
        }
        if (!armed) {
            armed = true;
            client.options.pauseOnLostFocus = false;
            client.options.onboardAccessibility = false;
            client.options.menuBackgroundBlurriness().set(0);
            client.options.guiScale().set(2);
            client.options.renderDistance().set(8);
            client.options.simulationDistance().set(6);
            client.options.framerateLimit().set(30);
            client.options.fov().set(54);
            client.options.gamma().set(1.0);
            client.options.bobView().set(false);
            client.options.hideGui = true;
            LOGGER.info("cosmetics harness armed");
        }
        ticks++;
        try {
            switch (state) {
                case WAIT_TITLE -> waitTitle(client);
                case WAIT_WORLD -> waitWorld(client);
                case SHOTS -> shots(client);
                case PAIR -> pair(client);
                case WARDROBE -> wardrobe(client);
                case STOP -> stop(client);
            }
        } catch (RuntimeException e) {
            LOGGER.error("cosmetics harness failed in {}", state, e);
            state = State.STOP;
        }
    }

    private static void waitTitle(Minecraft client) {
        if (client.getOverlay() != null || !(client.screen instanceof TitleScreen)) {
            if (ticks > 20 * 180) {
                LOGGER.error("title screen did not appear, screen={}", client.screen);
                state = State.STOP;
            }
            return;
        }
        state = State.WAIT_WORLD;
        ticks = 0;
    }

    private static void waitWorld(Minecraft client) {
        Screen screen = client.screen;
        if (screen instanceof net.minecraft.client.gui.screens.ConfirmScreen) {
            pressFirst(screen);
            return;
        }
        if (!(screen instanceof CreateWorldScreen create)) {
            if (ticks == 1) {
                //? if >=1.21.11 {
                CreateWorldScreen.openFresh(client, () -> client.setScreen(new TitleScreen()));
                //?} else {
                CreateWorldScreen.openFresh(client, new TitleScreen());
                //?}
            }
            if (ticks > 20 * 90) {
                LOGGER.error("create world did not open, screen={}", screen);
                state = State.STOP;
            }
            return;
        }
        var presets = create.getUiState().getNormalPresetList();
        if (presets.isEmpty()) {
            if (ticks > 20 * 90) {
                LOGGER.error("world presets never loaded");
                state = State.STOP;
            }
            return;
        }
        WorldCreationUiState ui = create.getUiState();
        ui.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
        ui.setAllowCommands(true);
        ui.setName("quarkos-portraits");
        presets.stream().filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().ifPresent(ui::setWorldType);
        if (!press(create, "selectWorld.create")) {
            LOGGER.error("could not press the create button");
            state = State.STOP;
            return;
        }
        state = Boolean.getBoolean("quarkos_cosmetics.wardrobeOnly") ? State.WARDROBE : State.SHOTS;
        ticks = 0;
        shotIndex = 0;
        shotQueued = false;
        settledAt = -1;
        screenAt = -1;
    }

    private static void shots(Minecraft client) {
        if (client.player == null || client.level == null) {
            if (client.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen) {
                pressFirst(client.screen);
            }
            if (ticks > 20 * 180) {
                LOGGER.error("world did not load, screen={}", client.screen);
                state = State.STOP;
            }
            return;
        }
        if (shotIndex >= activeShots().size()) {
            String only = System.getProperty("quarkos_cosmetics.only", "");
            if (!only.isBlank()) {
                if (only.contains("wardrobe")) {
                    state = State.WARDROBE;
                } else if (only.contains("two-client")) {
                    state = State.PAIR;
                } else {
                    state = State.STOP;
                }
            } else {
                state = State.PAIR;
            }
            ticks = 0;
            shotQueued = false;
            settledAt = -1;
            screenAt = -1;
            return;
        }
        Shot shot = activeShots().get(shotIndex);
        frame(client, shot);
        // Commands sent on the join tick are dropped. Keep trying until the teleport sticks, then let the camera leave the ground.
        if (settledAt < 0) {
            if (ticks == 1 || ticks % 10 == 0) {
                prepare(client, shot);
                shotQueued = false;
            }
            // Sleep moves the player onto the bed before the platform exists. Wait until they are over the studio.
            boolean overStudio = Math.abs(client.player.getX() - 0.5) < 4.0 && Math.abs(client.player.getZ() - 0.5) < 4.0
                    && client.player.getY() > 90.0;
            if (studio && overStudio && ticks > 40) {
                settledAt = ticks;
                LOGGER.info("studio ready at {},{},{}", client.player.getX(), client.player.getY(), client.player.getZ());
            } else if (ticks > 20 * 30) {
                LOGGER.error("player never reached the studio, y={}", client.player.getY());
                state = State.STOP;
            }
            return;
        }
        int settleWait = shot.name.contains("sleep") || shot.name.contains("scared") ? 60 : 40;
        if (shot.name.equals("chick-chest-asleep")) {
            settleWait = 100;
        }
        if (ticks - settledAt < settleWait) {
            return;
        }
        if (shot.name.startsWith("chick-") && !chickReady(client, shot)) {
            return;
        }
        if (shot.name.equals("chick-to-chest")) {
            chickClip(client, shot);
            return;
        }
        if (shot.name.startsWith("chick-") && shot.name.endsWith("-clip")) {
            chickClip(client, shot);
            return;
        }
        if (shot.film) {
            film(client, shot);
            return;
        }
        // Flap peaks at 0.8s of a 1.6s clip, which is tick 16 of every 32.
        if (shot.spread && !shotQueued) {
            int phase = Math.floorMod(client.player.tickCount, 32);
            if (phase < 15 || phase > 17) {
                return;
            }
        }
        shootWhenReady(client, shot.name, () -> {
            shotIndex++;
            ticks = 0;
            shotQueued = false;
            settledAt = -1;
            state = State.SHOTS;
        });
    }

    private static List<Shot> activeShots() {
        if (active == null) {
            String only = System.getProperty("quarkos_cosmetics.only", "");
            if (only.isBlank()) {
                active = SHOTS;
            } else {
                Set<String> names = Set.of(only.split(","));
                active = SHOTS.stream().filter(shot -> names.contains(shot.name())).toList();
                LOGGER.info("harness shot filter {}", active.stream().map(Shot::name).toList());
            }
        }
        return active;
    }

    private static void prepare(Minecraft client, Shot shot) {
        client.setScreen(null);
        client.options.hideGui = true;
        client.options.fov().set(54);
        client.options.gamma().set(1.0);
        client.options.bobView().set(false);
        if (!studio) {
            studio = true;
            client.player.connection.sendCommand("time set 6000");
            client.player.connection.sendCommand("weather clear");
            client.player.connection.sendCommand("gamemode creative");
            client.player.connection.sendCommand("fill -24 100 -24 24 100 24 white_concrete");
            client.player.connection.sendCommand("fill -24 101 -24 24 116 24 air");
            client.player.connection.sendCommand("fill -2 89 -2 2 89 2 barrier");
        }
        if (shot.name.equals("chick-to-chest")) {
            client.player.connection.sendCommand("tp @s -0.5 101 0.5 180 8");
        } else if (shot.name.equals("chick-chest-asleep")) {
            client.player.connection.sendCommand("tp @s 0.5 101 0.5 180 8");
        } else if (shot.name.contains("sleep")) {
            client.player.connection.sendCommand("tp @s 0.5 102.2 0.5 180 8");
        } else if (!shot.name.contains("scared")) {
            client.player.connection.sendCommand("tp @s 0.5 101 0.5 180 8");
        } else {
            client.player.connection.sendCommand("tp @s 0.5 116 0.5 180 12");
        }
        client.player.connection.sendCommand("item replace entity @s armor.head with air");
        client.player.connection.sendCommand("item replace entity @s armor.chest with air");
        client.player.connection.sendCommand("item replace entity @s armor.legs with air");
        client.player.connection.sendCommand("clear @s");
        client.player.setShiftKeyDown(false);
        client.player.setSwimming(false);
        client.player.stopFallFlying();
        boolean bedShot = shot.name.contains("sleep") || shot.name.equals("chick-to-chest") || shot.name.equals("chick-chest-asleep");
        if (!bedShot && client.player.isSleeping()) {
            client.player.stopSleeping();
        }
        if (bedShot) {
            client.player.connection.sendCommand("time set 6000");
            client.player.connection.sendCommand("setblock 0 100 0 white_concrete");
            client.player.connection.sendCommand("setblock 1 100 0 white_concrete");
            client.player.connection.sendCommand("setblock 0 101 0 red_bed[facing=east,part=foot]");
            client.player.connection.sendCommand("setblock 1 101 0 red_bed[facing=east,part=head]");
        } else {
            client.player.connection.sendCommand("time set 6000");
            client.player.connection.sendCommand("setblock 0 101 0 air");
            client.player.connection.sendCommand("setblock 1 101 0 air");
            client.player.connection.sendCommand("setblock 0 100 0 white_concrete");
            client.player.connection.sendCommand("setblock 1 100 0 white_concrete");
            client.player.connection.sendCommand("kill @e[type=!player]");
        }
        ClientSettings settings = ClientSettings.get();
        settings.skinLayers = false;
        settings.hideOthers = false;
        settings.disabled.clear();
        for (Slot slot : Slot.values()) {
            settings.categoryOn.put(slot, true);
            settings.equip(slot, "");
        }
        if (Chick.ID.equals(shot.id) && Chick.isPerch(shot.slot)) {
            settings.setChickPerch(shot.slot);
        } else {
            settings.equip(shot.slot, shot.id);
        }
        settings.save(client.gameDirectory.toPath());
    }

    private static void frame(Minecraft client, Shot shot) {
        portraitLock = false;
        boolean chick = shot.name.startsWith("chick-");
        boolean kite = shot.name.startsWith("kite-wings");
        boolean wide = kite || shot.spread || shot.film || shot.orbit != 0f;
        boolean fly = shot.name.equals("kite-wings-flying") || (shot.film && !chick);
        portraitDistance = kite ? 3.35f : wide ? 4.8f : 2.55f;
        portraitDrop = kite ? 0.55f : wide ? 0.15f : 0.42f;
        portraitOrbit = shot.front ? 0f : shot.orbit;
        boolean open = shot.name.equals("kite-wings-back") || shot.name.equals("kite-wings-side") || shot.name.equals("kite-wings-three-quarter");
        boolean folded = shot.name.equals("kite-wings-folded-back");
        if (open) {
            KiteWings.hold("flap", 0f);
        } else if (folded) {
            KiteWings.hold("idle", 0f);
        } else if (fly) {
            KiteWings.hold("flap", null);
        } else {
            KiteWings.hold(null, null);
        }
        client.player.setSprinting(fly);
        client.player.getAbilities().mayfly = true;
        client.player.getAbilities().flying = fly;
        client.options.fov().set(wide ? 50 : 54);
        client.options.hideGui = true;
        client.options.setCameraType(shot.front ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
        client.player.setYRot(180f);
        client.player.setYHeadRot(180f);
        client.player.yRotO = 180f;
        client.player.yHeadRotO = 180f;
        client.player.setYBodyRot(180f);
        client.player.yBodyRotO = 180f;
        client.player.setXRot(8f);
        client.setCameraEntity(client.player);
        if (!chick) {
            Chick.hold(null, null);
            return;
        }
        portraitDistance = 3.35f;
        portraitDrop = 0.55f;
        portraitOrbit = shot.orbit;
        String clip = chickClipName(shot.name);
        boolean side = shot.name.equals("chick-head-side") || shot.name.equals("chick-shoulder-side");
        if (shot.name.equals("chick-to-chest") || shot.name.equals("chick-chest-asleep")) {
            Chick.hold(null, null);
        } else if (side) {
            Chick.hold(clip, 0f);
        } else {
            Chick.hold(clip, shot.name.endsWith("-clip") ? null : chickStill(clip));
        }
        client.player.setSprinting(false);
        client.player.getAbilities().flying = false;
        client.options.fov().set(50);
        if (side) {
            client.options.hideGui = true;
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            portraitLock = true;
            // West of the player, looking east. Profile, so head depth and chick depth share the screen width.
            lockX = -3.6;
            lockY = shot.name.equals("chick-shoulder-side") ? 102.2 : 102.5;
            lockZ = 0.5;
            lockYaw = 270f;
            lockPitch = 2f;
            client.options.fov().set(32);
        }
        if (shot.name.equals("chick-chest-asleep")) {
            if (!client.player.isSleeping()) {
                client.player.startSleepInBed(new BlockPos(1, 101, 0));
            }
            clearSleepFade(client.player);
            if (client.screen != null) {
                client.setScreen(null);
            }
            client.options.hideGui = true;
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            portraitLock = true;
            lockX = 1.15;
            lockY = 102.35;
            lockZ = 2.15;
            lockYaw = 180f;
            lockPitch = 20f;
            client.options.fov().set(36);
        }
        if (shot.name.equals("chick-to-chest")) {
            if (filmIndex >= 8 && !client.player.isSleeping()) {
                client.player.startSleepInBed(new BlockPos(1, 101, 0));
            }
            if (client.player.isSleeping()) {
                clearSleepFade(client.player);
                if (client.screen != null) {
                    client.setScreen(null);
                }
            }
            client.options.hideGui = true;
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            portraitLock = true;
            lockX = 0.5;
            lockY = 102.55;
            lockZ = 3.35;
            lockYaw = 180f;
            lockPitch = 8f;
            client.options.fov().set(48);
        }
        if (shot.name.contains("sleep")) {
            if (!client.player.isSleeping()) {
                client.player.startSleepInBed(new BlockPos(1, 101, 0));
            }
            clearSleepFade(client.player);
            if (client.screen != null) {
                client.setScreen(null);
            }
            client.options.hideGui = true;
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            // Bed sits on the platform: foot (0,101,0), head (1,101,0). Close side view, same field of view as the standing portraits.
            portraitLock = true;
            lockX = 1.0;
            lockY = 102.15;
            lockZ = 2.45;
            lockYaw = 180f;
            lockPitch = 14f;
        }
        if (shot.name.contains("scared")) {
            client.player.connection.sendCommand("tp @s 0.5 116 0.5 180 12");
            client.player.fallDistance = 8f;
            client.player.setOnGround(false);
            portraitDistance = 5.4f;
            portraitDrop = -0.6f;
            portraitOrbit = 28f;
        }
    }

    /** The sleep overlay darkens the world as the timer climbs. Keep the pose, drop the fade. */
    private static void clearSleepFade(net.minecraft.world.entity.player.Player player) {
        Class<?> type = player.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField("sleepCounter");
                field.setAccessible(true);
                field.setInt(player, 0);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException ignored) {
                return;
            }
        }
    }

    private static boolean chickReady(Minecraft client, Shot shot) {
        if (shot.name.equals("chick-chest-asleep") && (!client.player.isSleeping() || client.screen != null || !Chick.asleep())) {
            if (ticks - settledAt > 20 * 15) {
                LOGGER.error("chick never settled on the chest, sleeping={} screen={}", client.player.isSleeping(), client.screen);
                state = State.STOP;
            }
            return false;
        }
        if (shot.name.contains("sleep") && (!client.player.isSleeping() || client.screen != null)) {
            if (ticks - settledAt > 20 * 12) {
                LOGGER.error("player never slept, y={} screen={}", client.player.getY(), client.screen);
                state = State.STOP;
            }
            return false;
        }
        if (shot.name.contains("scared") && client.player.getY() < 112.0) {
            if (ticks - settledAt > 20 * 12) {
                LOGGER.error("player never fell, y={} fall={}", client.player.getY(), client.player.fallDistance);
                state = State.STOP;
            }
            return false;
        }
        return true;
    }

    private static String chickClipName(String name) {
        String body = name.startsWith("chick-") ? name.substring("chick-".length()) : name;
        if (body.endsWith("-clip")) {
            body = body.substring(0, body.length() - "-clip".length());
        }
        return switch (body) {
            case "head", "head-side" -> "sit";
            case "shoulder", "shoulder-side" -> "idle";
            default -> body;
        };
    }

    private static Float chickStill(String clip) {
        return switch (clip) {
            case "idle" -> 0.5f;
            case "peck" -> 0.15f;
            case "sit" -> 0.9f;
            case "sleep" -> 1.0f;
            case "scared" -> 0f;
            case "alert" -> 0.3f;
            case "happy" -> 0.2f;
            case "shake" -> 0.1f;
            default -> 0f;
        };
    }

    /** Plays one chick clip and writes a short mp4 next to the stills. */
    private static void chickClip(Minecraft client, Shot shot) {
        int frames = shot.name.equals("chick-to-chest") ? 48 : 20;
        if (filmIndex < 0) {
            filmIndex = 0;
            filmStartTick = ticks;
            shotQueued = false;
        }
        if (ticks - filmStartTick < filmIndex * 2) {
            return;
        }
        if (shotQueued) {
            Path path = chickFrame(shot.name, filmIndex);
            try {
                if (Files.size(path) > 1000L) {
                    shotQueued = false;
                    filmIndex++;
                    if (filmIndex >= frames) {
                        encodeChickClip(shot.name, frames);
                        filmIndex = -1;
                        shotIndex++;
                        ticks = 0;
                        shotQueued = false;
                        settledAt = -1;
                    }
                }
            } catch (Exception ignored) {
                // The frame is still being written.
            }
            return;
        }
        Path path = chickFrame(shot.name, filmIndex);
        shotQueued = true;
        try {
            Files.createDirectories(path.getParent());
        } catch (Exception e) {
            LOGGER.error("could not create chick frames", e);
            state = State.STOP;
            return;
        }
        //? if >=1.21.11 {
        Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> writeImage(path, image));
        //?} else {
        NativeImage image = Screenshot.takeScreenshot(client.getMainRenderTarget());
        writeImage(path, image);
        //?}
    }

    private static Path chickFrame(String name, int index) {
        return Path.of("/tmp/chick-frames").resolve(name).resolve(String.format("f%02d.png", index));
    }

    private static void encodeChickClip(String shotName, int frames) {
        String clip = chickClipName(shotName);
        Path mp4 = shots().resolve("chick-" + clip + ".mp4");
        Path pattern = chickFrame(shotName, 0).getParent().resolve("f%02d.png");
        try {
            Files.createDirectories(mp4.getParent());
            Process process = new ProcessBuilder(
                    "ffmpeg", "-y", "-framerate", "10", "-i", pattern.toString(),
                    "-frames:v", Integer.toString(frames), "-pix_fmt", "yuv420p", mp4.toString())
                    .redirectErrorStream(true)
                    .start();
            process.getInputStream().readAllBytes();
            int code = process.waitFor();
            LOGGER.info("chick clip {} exit {}", mp4, code);
        } catch (Exception e) {
            LOGGER.error("could not encode {}", mp4, e);
        }
    }

    private static void chickPair(Minecraft client) {
        portraitLock = false;
        KiteWings.hold(null, null);
        client.player.setSprinting(false);
        client.player.getAbilities().flying = false;
        if (!pairStarted) {
            pairStarted = true;
            publishChickAda(client);
        }
        portraitDistance = 3.35f;
        portraitDrop = 0.55f;
        boolean head = pairShot == 0;
        portraitOrbit = head ? 0f : 36f;
        client.options.fov().set(50);
        client.options.hideGui = true;
        Chick.hold(head ? "sit" : "idle", head ? 0.9f : 0.5f);
        if (ticks % 20 == 0) {
            client.player.connection.sendCommand("tp @s 0.5 90 0.5 180 0");
        }
        if (!pairReady) {
            if (pairFailed || ticks > 20 * 40) {
                LOGGER.error("chick two-client gallery setup failed");
                state = State.STOP;
            }
            return;
        }
        anchorGuest(client, 180f, 8f, false);
        if (ticks < 40) {
            return;
        }
        String name = head ? "chick-two-client-head" : "chick-two-client-shoulder";
        shootWhenReady(client, name, () -> {
            if (head) {
                pairShot = 1;
                pairReady = false;
                ticks = 0;
                shotQueued = false;
                switchAdaShoulder(client);
                return;
            }
            Chick.hold(null, null);
            state = State.STOP;
            ticks = 0;
        });
    }

    private static void publishChickAda(Minecraft client) {
        List<String> bodies = new ArrayList<>();
        List<Slot> slots = new ArrayList<>();
        try {
            ModelLibrary.Resolved resolved = ModelLibrary.resolve(Chick.ID);
            if (resolved == null || resolved.png() == null) {
                LOGGER.error("packed chick missing for gallery publish");
                pairFailed = true;
                return;
            }
            for (Slot slot : List.of(Slot.HAT, Slot.SHOULDER_LEFT)) {
                bodies.add(resolved.model().publishBody(resolved.name(), resolved.description(), slot, "texture", resolved.png()));
                slots.add(slot);
            }
        } catch (ModelException ex) {
            LOGGER.error("could not build the chick gallery body", ex);
            pairFailed = true;
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                GalleryClient gallery = new GalleryClient(ClientSettings.get().galleryUrl);
                GalleryClient.Challenge challenge = gallery.createChallenge();
                GalleryClient.Session session = gallery.completeSession("Ada", challenge.serverId());
                adaToken = session.token();
                String key = ClientSettings.hyphenate(session.uuid());
                GalleryClient.Equipment previous = gallery.equipment(key);
                for (Slot slot : Slot.values()) {
                    String occupied = previous.id(slot);
                    if (occupied != null && !occupied.isBlank()) {
                        gallery.equip(session.token(), slot, null);
                    }
                }
                String hatId = null;
                String shoulderId = null;
                for (int i = 0; i < bodies.size(); i++) {
                    GalleryClient.Listed listed = gallery.publish(session.token(), bodies.get(i));
                    if (slots.get(i) == Slot.HAT) {
                        hatId = listed.id();
                        gallery.equip(session.token(), Slot.HAT, listed.id());
                    } else {
                        shoulderId = listed.id();
                    }
                }
                chickHatId = hatId;
                chickShoulderId = shoulderId;
                GalleryClient.Equipment equipment = gallery.equipment(key);
                List<GalleryClient.Detail> details = new ArrayList<>();
                for (Slot slot : Slot.values()) {
                    String id = equipment.id(slot);
                    if (id != null && !id.isBlank()) {
                        details.add(gallery.cosmetic(id));
                    }
                }
                client.execute(() -> {
                    for (GalleryClient.Detail detail : details) {
                        Slot slot = Slot.parse(detail.listed().slot()).orElse(null);
                        if (slot != null) {
                            ModelLibrary.remember(detail.listed().id(), detail.listed().name(), detail.listed().description(), slot, detail.model());
                        }
                    }
                    EquipmentMirror.accept(key, equipment);
                    pairKey = key;
                    pairId = UUID.fromString(key);
                    pairReady = true;
                    LOGGER.info("gallery client B can see Ada's chick on {}", equipment.id(Slot.HAT));
                });
            } catch (Exception ex) {
                LOGGER.error("Ada chick publish failed", ex);
                pairFailed = true;
            }
        }, "quarkos-ada-chick");
        worker.setDaemon(true);
        worker.start();
    }

    private static void switchAdaShoulder(Minecraft client) {
        Thread worker = new Thread(() -> {
            try {
                GalleryClient gallery = new GalleryClient(ClientSettings.get().galleryUrl);
                gallery.equip(adaToken, Slot.HAT, null);
                gallery.equip(adaToken, Slot.SHOULDER_LEFT, chickShoulderId);
                GalleryClient.Equipment equipment = gallery.equipment(pairKey);
                GalleryClient.Detail detail = gallery.cosmetic(chickShoulderId);
                client.execute(() -> {
                    Slot slot = Slot.parse(detail.listed().slot()).orElse(Slot.SHOULDER_LEFT);
                    ModelLibrary.remember(detail.listed().id(), detail.listed().name(), detail.listed().description(), slot, detail.model());
                    EquipmentMirror.accept(pairKey, equipment);
                    pairReady = true;
                    LOGGER.info("Ada chick moved to {}", equipment.id(Slot.SHOULDER_LEFT));
                });
            } catch (Exception ex) {
                LOGGER.error("Ada chick shoulder equip failed", ex);
                pairFailed = true;
            }
        }, "quarkos-ada-shoulder");
        worker.setDaemon(true);
        worker.start();
    }

    /** One flap cycle, back view, written as numbered frames for the GIF. */
    private static void film(Minecraft client, Shot shot) {
        int phase = Math.floorMod(client.player.tickCount, 32);
        if (filmIndex < 0) {
            if (phase != 0) {
                return;
            }
            filmIndex = 0;
            filmStartTick = ticks;
            shotQueued = false;
        }
        if (filmStartTick >= 0 && ticks - filmStartTick > 20 * 50) {
            LOGGER.error("flap film stalled at frame {}", filmIndex);
            state = State.STOP;
            return;
        }
        int target = filmIndex * 2;
        if (phase != target || shotQueued) {
            if (shotQueued) {
                Path path = filmFrame(filmIndex);
                try {
                    if (Files.size(path) > 1000L) {
                        shotQueued = false;
                        filmIndex++;
                        LOGGER.info("flap frame {} at phase {}", filmIndex, phase);
                        if (filmIndex >= 16) {
                            filmIndex = -1;
                            shotIndex++;
                            ticks = 0;
                            shotQueued = false;
                            settledAt = -1;
                        }
                    }
                } catch (Exception ignored) {
                    // The frame is still being written.
                }
            }
            return;
        }
        Path path = filmFrame(filmIndex);
        shotQueued = true;
        try {
            Files.createDirectories(path.getParent());
        } catch (Exception e) {
            LOGGER.error("could not create flap frames", e);
            state = State.STOP;
            return;
        }
        //? if >=1.21.11 {
        Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> writeImage(path, image));
        //?} else {
        NativeImage image = Screenshot.takeScreenshot(client.getMainRenderTarget());
        writeImage(path, image);
        //?}
    }

    private static Path filmFrame(int index) {
        return Path.of("/tmp/kite-flap-frames").resolve(String.format("f%02d.png", index));
    }

    private static void pair(Minecraft client) {
        if (client.player == null || client.level == null) {
            state = State.STOP;
            return;
        }
        if (System.getProperty("quarkos_cosmetics.only", "").contains("chick-two")) {
            chickPair(client);
            return;
        }
        // Folded rest pose, so the other player's wings match the packed idle shot.
        KiteWings.hold("idle", 0f);
        Chick.hold(null, null);
        client.player.setSprinting(false);
        client.player.getAbilities().flying = false;
        if (!pairStarted) {
            pairStarted = true;
            for (Slot slot : Slot.values()) {
                ClientSettings.get().equip(slot, "");
            }
            publishAda(client);
        }
        // Same studio as the portraits. The chick clips leave a bed at y=101; Ada was standing in it.
        portraitDistance = 3.7f;
        portraitDrop = 0.4f;
        portraitOrbit = 0f;
        client.options.fov().set(54);
        client.options.hideGui = true;
        if (ticks == 1 || ticks % 10 == 0) {
            if (client.player.isSleeping()) {
                client.player.stopSleeping();
            }
            client.player.connection.sendCommand("time set 6000");
            client.player.connection.sendCommand("weather clear");
            client.player.connection.sendCommand("gamemode creative");
            client.player.connection.sendCommand("tp @s 0.5 70 24 180 0");
            client.player.connection.sendCommand("fill -8 100 -8 8 100 8 white_concrete");
            client.player.connection.sendCommand("fill -8 101 -8 8 108 8 air");
        }
        if (!pairReady) {
            if (pairFailed || ticks > 20 * 40) {
                LOGGER.error("two-client gallery setup failed");
                state = State.STOP;
            }
            return;
        }
        boolean front = pairShot == 1;
        anchorGuest(client, front ? 0f : 180f, 0f, front);
        if (ticks < (pairShot == 0 ? 50 : 30)) {
            return;
        }
        String name = pairShot == 0 ? "two-client" : "two-client-front";
        shootWhenReady(client, name, () -> {
            if (pairShot == 0) {
                pairShot = 1;
                ticks = 0;
                shotQueued = false;
                return;
            }
            String only = System.getProperty("quarkos_cosmetics.only", "");
            if (!only.isBlank() && !only.contains("wardrobe")) {
                state = State.STOP;
                return;
            }
            ClientSettings.get().equip(Slot.BACK, "quarkos:kite_wings");
            client.setScreen(new WardrobeScreen(null));
            state = State.WARDROBE;
            ticks = 0;
            shotQueued = false;
        });
    }

    private static void wardrobe(Minecraft client) {
        if (client.player == null || client.level == null) {
            if (client.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen) {
                pressFirst(client.screen);
            }
            if (ticks > 20 * 180) {
                LOGGER.error("wardrobe world did not load, screen={}", client.screen);
                state = State.STOP;
            }
            return;
        }
        client.options.hideGui = true;
        portraitOrbit = 0f;
        if (client.options.guiScale().get() != 1) {
            client.setScreen(null);
            client.options.guiScale().set(1);
            client.resizeDisplay();
            screenAt = -1;
            return;
        }
        if (settledAt < 0) {
            if (ticks == 1 || ticks % 10 == 0) {
                client.setScreen(null);
                if (!studio) {
                    studio = true;
                    client.player.connection.sendCommand("time set 6000");
                    client.player.connection.sendCommand("weather clear");
                    client.player.connection.sendCommand("gamemode creative");
                    client.player.connection.sendCommand("fill -24 100 -24 24 100 24 white_concrete");
                    client.player.connection.sendCommand("fill -24 101 -24 24 116 24 air");
                }
                client.player.connection.sendCommand("tp @s 0.5 101 0.5 180 0");
                ClientSettings settings = ClientSettings.get();
                for (Slot slot : Slot.values()) {
                    settings.categoryOn.put(slot, true);
                    settings.equip(slot, "");
                }
                settings.equip(Slot.BACK, "quarkos:kite_wings");
                settings.save(client.gameDirectory.toPath());
            }
            if (client.player.getY() >= 100.0) {
                settledAt = ticks;
                LOGGER.info("wardrobe studio ready at y={}", client.player.getY());
            } else if (ticks > 20 * 30) {
                LOGGER.error("wardrobe player never reached the studio, y={}", client.player.getY());
                state = State.STOP;
            }
            return;
        }
        if (!(client.screen instanceof WardrobeScreen)) {
            client.setScreen(new WardrobeScreen(null));
            screenAt = ticks;
            return;
        }
        if (ticks - screenAt < 8) {
            return;
        }
        // Open wings, so the rear three-quarter shows the fan rather than the folded edge.
        int phase = Math.floorMod(client.player.tickCount, 32);
        if (phase < 15 || phase > 17) {
            return;
        }
        shootWhenReady(client, "wardrobe", () -> {
            state = State.STOP;
            ticks = 0;
        });
    }

    private static void publishAda(Minecraft client) {
        List<String> bodies = new ArrayList<>();
        List<Slot> slots = new ArrayList<>();
        try {
            for (String packed : List.of("quarkos:lantern_cap", "quarkos:kite_wings")) {
                ModelLibrary.Resolved resolved = ModelLibrary.resolve(packed);
                if (resolved == null || resolved.png() == null) {
                    LOGGER.error("packed cosmetic missing for gallery publish: {}", packed);
                    pairFailed = true;
                    return;
                }
                bodies.add(resolved.model().publishBody(resolved.name(), resolved.description(), resolved.slot(), "texture", resolved.png()));
                slots.add(resolved.slot());
            }
        } catch (ModelException ex) {
            LOGGER.error("could not build a gallery publish body", ex);
            pairFailed = true;
            return;
        }
        for (Slot slot : Slot.values()) {
            ClientSettings.get().equip(slot, "");
        }
        Thread worker = new Thread(() -> {
            try {
                GalleryClient gallery = new GalleryClient(ClientSettings.get().galleryUrl);
                GalleryClient.Challenge challenge = gallery.createChallenge();
                GalleryClient.Session session = gallery.completeSession("Ada", challenge.serverId());
                String key = ClientSettings.hyphenate(session.uuid());
                for (int i = 0; i < bodies.size(); i++) {
                    GalleryClient.Listed listed = gallery.publish(session.token(), bodies.get(i));
                    gallery.equip(session.token(), slots.get(i), listed.id());
                }
                GalleryClient.Equipment equipment = gallery.equipment(key);
                List<GalleryClient.Detail> details = new ArrayList<>();
                for (Slot slot : Slot.values()) {
                    String id = equipment.id(slot);
                    if (id != null && !id.isBlank()) {
                        details.add(gallery.cosmetic(id));
                    }
                }
                client.execute(() -> {
                    for (GalleryClient.Detail detail : details) {
                        Slot slot = Slot.parse(detail.listed().slot()).orElse(null);
                        if (slot != null) {
                            ModelLibrary.remember(detail.listed().id(), detail.listed().name(), detail.listed().description(), slot, detail.model());
                        }
                    }
                    EquipmentMirror.accept(key, equipment);
                    pairKey = key;
                    pairId = UUID.fromString(key);
                    pairReady = true;
                    LOGGER.info("gallery client B can see Ada {} with {} cosmetics", key, details.size());
                });
            } catch (Exception ex) {
                LOGGER.error("Ada gallery publish failed", ex);
                pairFailed = true;
            }
        }, "quarkos-ada");
        worker.setDaemon(true);
        worker.start();
    }

    private static void anchorGuest(Minecraft client, float yaw, float pitch, boolean front) {
        if (pairId == null || client.level == null) {
            return;
        }
        if (guest == null || client.level.getEntity(guest.getId()) == null) {
            guest = new RemotePlayer(client.level, new GameProfile(pairId, "Ada"));
            guest.setId(1_600_001);
            client.level.addEntity(guest);
            LOGGER.info("spawned gallery player Ada {}", pairKey);
        }
        guest.setPos(0.5, 101.0, 0.5);
        guest.setPose(net.minecraft.world.entity.Pose.STANDING);
        guest.setShiftKeyDown(false);
        guest.setYRot(yaw);
        guest.setXRot(pitch);
        guest.setYHeadRot(yaw);
        guest.yRotO = yaw;
        guest.xRotO = pitch;
        guest.yHeadRotO = yaw;
        guest.xo = guest.getX();
        guest.yo = guest.getY();
        guest.zo = guest.getZ();
        client.options.setCameraType(front ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
        client.setCameraEntity(guest);
    }

    private static void shootWhenReady(Minecraft client, String name, Runnable next) {
        Path path = shots().resolve(name + ".png");
        if (!shotQueued) {
            shotQueued = true;
            try {
                Files.createDirectories(path.getParent());
            } catch (Exception e) {
                LOGGER.error("could not create shot directory", e);
                state = State.STOP;
                return;
            }
            requestShot(client, path);
            return;
        }
        try {
            if (Files.size(path) > 1000L) {
                next.run();
            } else if (ticks > 20 * 20) {
                LOGGER.error("screenshot {} was not written", path);
                state = State.STOP;
            }
        } catch (Exception ignored) {
            if (ticks > 20 * 20) {
                LOGGER.error("screenshot {} missing", path);
                state = State.STOP;
            }
        }
    }

    /** Mask proof captures after the candle pass, so the copy includes the emissive layer. */
    public static void shotAtFrameEnd() {
        frameEndShots = true;
    }

    public static void capturePending() {
        Path path = pendingShot;
        if (path == null) {
            return;
        }
        pendingShot = null;
        Minecraft client = Minecraft.getInstance();
        //? if >=1.21.11 {
        writeTargetNow(client, path);
        //?} else {
        NativeImage image = Screenshot.takeScreenshot(client.getMainRenderTarget());
        writeImage(path, image);
        //?}
    }

    private static void requestShot(Minecraft client, Path path) {
        if (frameEndShots) {
            pendingShot = path;
            return;
        }
        //? if >=1.21.11 {
        if ("forge".equals(System.getProperty("quarkos_cosmetics.loader"))) {
            writeTargetNow(client, path);
            return;
        }
        Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> writeImage(path, image));
        //?} else {
        NativeImage image = Screenshot.takeScreenshot(client.getMainRenderTarget());
        writeImage(path, image);
        //?}
    }

    //? if >=1.21.11 {
    /** Read the main color texture on this thread, before the frame moves on. */
    private static void writeTargetNow(Minecraft client, Path path) {
        var target = client.getMainRenderTarget();
        var color = target.getColorTexture();
        if (!(color instanceof com.mojang.blaze3d.opengl.GlTexture texture)) {
            Screenshot.takeScreenshot(target, image -> writeImage(path, image));
            return;
        }
        int width = target.width;
        int height = target.height;
        org.lwjgl.opengl.GL11.glFinish();
        int previous = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
        org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, texture.glId());
        java.nio.ByteBuffer buffer = org.lwjgl.system.MemoryUtil.memAlloc(width * height * 4);
        NativeImage image = new NativeImage(width, height, false);
        try {
            org.lwjgl.opengl.GL11.glGetTexImage(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, buffer);
            for (int y = 0; y < height; y++) {
                int row = (height - 1 - y) * width * 4;
                for (int x = 0; x < width; x++) {
                    int i = row + x * 4;
                    int red = buffer.get(i) & 255;
                    int green = buffer.get(i + 1) & 255;
                    int blue = buffer.get(i + 2) & 255;
                    int alpha = buffer.get(i + 3) & 255;
                    image.setPixel(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
                }
            }
            writeImage(path, image);
        } catch (RuntimeException ex) {
            LOGGER.error("failed to read the frame", ex);
            image.close();
        } finally {
            org.lwjgl.system.MemoryUtil.memFree(buffer);
            org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, previous);
        }
    }
    //?}

    private static void writeImage(Path path, NativeImage image) {
        try {
            image.writeToFile(path);
            LOGGER.info("wrote {}", path);
        } catch (Exception e) {
            LOGGER.error("failed to write {}", path, e);
        } finally {
            image.close();
        }
    }

    private static void stop(Minecraft client) {
        if (ticks > 10) {
            LOGGER.info("cosmetics harness finished");
            client.stop();
        }
    }

    private static boolean press(Screen screen, String key) {
        String label = Component.translatable(key).getString();
        for (var child : screen.children()) {
            if (child instanceof Button button) {
                String message = button.getMessage().getString();
                if (message.equals(label) || message.toLowerCase(java.util.Locale.ROOT).contains("create")) {
                    pressButton(button);
                    return true;
                }
            }
        }
        return false;
    }

    private static void pressFirst(Screen screen) {
        for (var child : screen.children()) {
            if (child instanceof Button button) {
                pressButton(button);
                return;
            }
        }
    }

    private static void pressButton(Button button) {
        //? if >=1.21.11 {
        button.onPress(new net.minecraft.client.input.InputWithModifiers() {
            @Override
            public int input() {
                return 0;
            }

            @Override
            public int modifiers() {
                return 0;
            }
        });
        //?} else {
        button.onPress();
        //?}
    }

    private static Path shots() {
        String configured = System.getProperty("quarkos_cosmetics.shots");
        if (configured == null || configured.isBlank()) {
            return Path.of("/cursor/stores/bc-1243ce51-36c9-58c7-8fa4-153844455530/media/fabric");
        }
        return Path.of(configured);
    }

    private enum State {
        WAIT_TITLE,
        WAIT_WORLD,
        SHOTS,
        PAIR,
        WARDROBE,
        STOP
    }

    private record Shot(String name, Slot slot, String id, boolean front, boolean spread, float orbit, boolean film) {
    }
}
