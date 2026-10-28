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
import net.minecraft.world.entity.Pose;
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
    private static final List<Shot> SHOTS = List.of(
            new Shot("kite-wings-front", Slot.BACK, "quarkos:kite_wings", true, false, 0f, ""),
            new Shot("kite-wings-back", Slot.BACK, "quarkos:kite_wings", false, false, 0f, ""),
            new Shot("kite-wings-side", Slot.BACK, "quarkos:kite_wings", false, false, 72f, ""),
            new Shot("kite-wings-three-quarter", Slot.BACK, "quarkos:kite_wings", false, false, 38f, ""),
            new Shot("kite-wings-folded-back", Slot.BACK, "quarkos:kite_wings", false, false, 0f, ""),
            new Shot("kite-wings-flying", Slot.BACK, "quarkos:kite_wings", false, false, 0f, ""),
            new Shot("kite-wings-flap", Slot.BACK, "quarkos:kite_wings", false, true, 0f, "open"),
            new Shot("lantern-cap-front", Slot.HAT, "quarkos:lantern_cap", true, false, 0f, ""),
            new Shot("lantern-cap-back", Slot.HAT, "quarkos:lantern_cap", false, false, 0f, ""),
            new Shot("fox-ears-front", Slot.EARS, "quarkos:fox_tips", true, false, 0f, ""),
            new Shot("fox-ears-back", Slot.EARS, "quarkos:fox_tips", false, false, 0f, ""),
            new Shot("ribbon-tail-front", Slot.TAIL, "quarkos:ribbon_tail", true, false, 0f, ""),
            new Shot("ribbon-tail-back", Slot.TAIL, "quarkos:ribbon_tail", false, false, 0f, ""),
            new Shot("ink-wren-front", Slot.SHOULDER_LEFT, "quarkos:ink_wren", true, false, 0f, ""),
            new Shot("ink-wren-back", Slot.SHOULDER_LEFT, "quarkos:ink_wren", false, false, 0f, ""),
            new Shot("pebble-moth-front", Slot.SHOULDER_RIGHT, "quarkos:pebble_moth", true, false, 0f, ""),
            new Shot("pebble-moth-back", Slot.SHOULDER_RIGHT, "quarkos:pebble_moth", false, false, 0f, ""),
            new Shot("chick-idle", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, ""),
            new Shot("chick-idle-clip", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, ""),
            new Shot("chick-peck", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, ""),
            new Shot("chick-peck-clip", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, ""),
            new Shot("chick-sit", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, ""),
            new Shot("chick-sit-clip", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, ""),
            new Shot("chick-sleep", Slot.HAT, Chick.ID, false, false, 0f, ""),
            new Shot("chick-sleep-clip", Slot.HAT, Chick.ID, false, false, 0f, ""),
            new Shot("chick-scared", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, ""),
            new Shot("chick-scared-clip", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, ""),
            new Shot("chick-alert", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, ""),
            new Shot("chick-alert-clip", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, ""),
            new Shot("chick-happy", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, ""),
            new Shot("chick-happy-clip", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, ""),
            new Shot("chick-shake", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, ""),
            new Shot("chick-shake-clip", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, ""),
            new Shot("chick-head", Slot.HAT, Chick.ID, true, false, 0f, ""),
            new Shot("chick-shoulder", Slot.SHOULDER_LEFT, Chick.ID, false, false, 36f, ""),
            new Shot("chick-head-side", Slot.HAT, Chick.ID, false, false, 0f, ""),
            new Shot("chick-shoulder-side", Slot.SHOULDER_LEFT, Chick.ID, false, false, 0f, ""),
            new Shot("chick-head-front", Slot.HAT, Chick.ID, false, false, 0f, ""),
            new Shot("chick-head-three-quarter", Slot.HAT, Chick.ID, false, false, 0f, ""),
            new Shot("chick-shoulder-front", Slot.SHOULDER_LEFT, Chick.ID, false, false, 0f, ""),
            new Shot("chick-chest-asleep", Slot.HAT, Chick.ID, false, false, 0f, ""),
            new Shot("chick-chest-sit-side", Slot.HAT, Chick.ID, false, false, 0f, ""),
            new Shot("chick-chest-sit-three-quarter", Slot.HAT, Chick.ID, false, false, 0f, ""),
            new Shot("chick-chest-light", Slot.HAT, Chick.ID, false, false, 0f, ""),
            new Shot("chick-to-chest", Slot.HAT, Chick.ID, false, false, 0f, ""),
            new Shot("chick-compare-head", Slot.HAT, Chick.ID, false, false, 0f, ""),
            new Shot("chick-compare-shoulder", Slot.SHOULDER_LEFT, Chick.ID, false, false, 0f, ""),
            new Shot("chick-compare-sleep", Slot.HAT, Chick.ID, false, false, 0f, ""),
            new Shot("chick-head-idle-alert", Slot.HAT, Chick.ID, false, false, 0f, ""),
            new Shot("chick-shoulder-film", Slot.SHOULDER_LEFT, Chick.ID, false, false, 0f, ""),
            new Shot("chick-scared-film", Slot.SHOULDER_LEFT, Chick.ID, false, false, 0f, "")
    );

    private static State state = State.WAIT_TITLE;
    private static int ticks;
    private static boolean armed;
    private static boolean shotQueued;
    private static boolean studio;
    private static int shotIndex;
    private static int settledAt = -1;
    private static int screenAt = -1;
    private static List<Shot> active;
    private static boolean pairStarted;
    private static int pairShot;
    private static volatile boolean pairReady;
    private static volatile boolean pairFailed;
    private static volatile String pairKey = "";
    private static volatile UUID pairId;
    private static volatile String adaToken = "";
    private static volatile String chickHatId = "";
    private static volatile String chickShoulderId = "";
    private static int filmIndex = -1;
    private static int filmStartTick = -1;
    private static int sleepSeenAt = -1;
    private static int asleepSeenAt = -1;
    private static boolean fallReleased;
    private static double airY = 108;
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
                case WARDROBE -> wardrobe(client);
                case PAIR -> pair(client);
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
        if (Boolean.getBoolean("quarkos_cosmetics.launchCheck")) {
            LOGGER.info("cosmetics launch check reached the title");
            state = State.STOP;
            ticks = 0;
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
        state = State.SHOTS;
        ticks = 0;
        shotIndex = 0;
        shotQueued = false;
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
                state = only.contains("wardrobe") ? State.WARDROBE : State.STOP;
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
            boolean needsBed = shot.name.contains("sleep") || shot.name.equals("chick-to-chest")
                    || shot.name.equals("chick-chest-asleep") || chestSit(shot.name);
            boolean bedPlaced = !needsBed
                    || client.level.getBlockState(new BlockPos(0, 101, 2)).is(net.minecraft.world.level.block.Blocks.RED_BED);
            if (client.player.getY() >= 100.0 && bedPlaced) {
                settledAt = ticks;
                asleepSeenAt = -1;
                LOGGER.info("studio ready at y={}", client.player.getY());
            } else if (ticks > 20 * 30) {
                LOGGER.error("player never reached the studio, y={}", client.player.getY());
                state = State.STOP;
            }
            return;
        }
        int settleWait = shot.name.equals("chick-chest-asleep") || chestSit(shot.name) ? 100 : 40;
        if (ticks - settledAt < settleWait) {
            return;
        }
        if (shot.name.startsWith("chick-") && !chickReady(client, shot)) {
            return;
        }
        if (filmShot(shot.name) || (shot.name.startsWith("chick-") && shot.name.endsWith("-clip"))) {
            chickClip(client, shot);
            return;
        }
        // The flap clip is 1.6s. Age is in ticks, so the open keyframe at 0.8s lands every 32 ticks.
        if (shot.flap && Math.floorMod(client.player.tickCount, 32) != 16) {
            return;
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

    /** Opens the existing wardrobe. The layout stays as authored. */
    private static void wardrobe(Minecraft client) {
        if (client.player == null || client.level == null) {
            if (ticks > 20 * 30) {
                LOGGER.error("wardrobe world did not load, screen={}", client.screen);
                state = State.STOP;
            }
            return;
        }
        if (!(client.screen instanceof WardrobeScreen)) {
            if (settledAt < 0) {
                if (ticks == 1 || ticks % 10 == 0) {
                    client.setScreen(null);
                    client.player.connection.sendCommand("gamemode creative");
                    client.player.connection.sendCommand("tp @s 0.5 101 0.5 180 0");
                }
                if (client.player.getY() >= 100.0) {
                    settledAt = ticks;
                } else if (ticks > 20 * 20) {
                    LOGGER.error("wardrobe player never reached the studio, y={}", client.player.getY());
                    state = State.STOP;
                }
                return;
            }
            if (ticks - settledAt < 10) {
                return;
            }
            client.setScreen(new WardrobeScreen(null));
            screenAt = ticks;
            shotQueued = false;
            return;
        }
        if (screenAt < 0 || ticks - screenAt < 40) {
            return;
        }
        shootWhenReady(client, "wardrobe", () -> {
            state = State.STOP;
            ticks = 0;
            shotQueued = false;
        });
    }

    private static void prepare(Minecraft client, Shot shot) {
        client.setScreen(null);
        client.options.hideGui = true;
        client.options.fov().set(54);
        client.options.gamma().set(1.0);
        client.options.bobView().set(false);
        if (!studio) {
            studio = true;
            client.player.connection.sendCommand("fill -2 89 -2 2 89 2 barrier");
        }
        client.player.connection.sendCommand("fill -24 100 -24 24 100 24 white_concrete");
        // Join-tick commands are dropped, so the rules are sent again until the studio sticks.
        client.player.connection.sendCommand("gamerule doDaylightCycle false");
        client.player.connection.sendCommand("gamerule doMobSpawning false");
        client.player.connection.sendCommand("difficulty peaceful");
        client.player.connection.sendCommand("weather clear");
        client.player.connection.sendCommand("gamemode creative");
        if (shot.name.equals("chick-to-chest")) {
            // Wake first so the clip starts on the perch, then stand north of the foot facing the bed.
            if (client.player.isSleeping()) {
                client.player.stopSleeping();
            }
            Chick.resetBed();
            client.player.connection.sendCommand("tp @s -0.7 101 1.5 270 8");
        } else {
            if (shot.name.equals("chick-chest-asleep") || chestSit(shot.name)) {
                Chick.resetBed();
            }
            client.player.connection.sendCommand("tp @s 0.5 101 0.5 180 0");
        }
        client.player.connection.sendCommand("item replace entity @s armor.head with air");
        client.player.connection.sendCommand("item replace entity @s armor.chest with air");
        client.player.connection.sendCommand("item replace entity @s armor.legs with air");
        client.player.connection.sendCommand("clear @s");
        client.player.setShiftKeyDown(false);
        client.player.setSprinting(false);
        client.player.setSwimming(false);
        client.player.stopFallFlying();
        client.player.getAbilities().flying = false;
        client.player.setNoGravity(false);
        boolean bedTrip = shot.name.equals("chick-to-chest") || shot.name.equals("chick-chest-asleep")
                || chestSit(shot.name);
        if (!shot.name.contains("sleep") && !bedTrip && client.player.isSleeping()) {
            client.player.stopSleeping();
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
        if (shot.name.equals("chick-chest-light")) {
            client.player.connection.sendCommand("kill @e[type=!player]");
            client.player.connection.sendCommand(
                    "summon chicken 3.4 101 2.3 {NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[180f,0f]}");
        } else {
            client.player.connection.sendCommand("kill @e[type=!player]");
        }
        if (shot.name.contains("sleep") || shot.name.equals("chick-to-chest") || shot.name.equals("chick-chest-asleep")
                || chestSit(shot.name)) {
            boolean day = shot.name.equals("chick-to-chest") || shot.name.equals("chick-chest-asleep")
                    || chestSit(shot.name) || shot.name.equals("chick-compare-sleep");
            client.player.connection.sendCommand(day ? "time set 6000" : "time set 18000");
            client.player.connection.sendCommand("setblock 0 101 1 red_bed[facing=south,part=foot]");
            client.player.connection.sendCommand("setblock 0 101 2 red_bed[facing=south,part=head]");
            if (chestSit(shot.name) || shot.name.equals("chick-compare-sleep")) {
                // Vanilla daylight, shared with the chicken beside the bed.
                client.player.connection.sendCommand("setblock 2 102 1 air");
                client.player.connection.sendCommand("setblock 2 102 2 air");
                client.player.connection.sendCommand("setblock -1 102 1 air");
                client.player.connection.sendCommand("setblock -1 102 2 air");
                client.player.connection.sendCommand("setblock 0 103 1 air");
                client.player.connection.sendCommand("setblock 0 103 2 air");
            } else {
                // Invisible lights so the chick on the pillow reads at night.
                client.player.connection.sendCommand("setblock 2 102 1 light[level=15]");
                client.player.connection.sendCommand("setblock 2 102 2 light[level=15]");
                client.player.connection.sendCommand("setblock -1 102 1 light[level=15]");
                client.player.connection.sendCommand("setblock -1 102 2 light[level=15]");
                client.player.connection.sendCommand("setblock 0 103 1 light[level=15]");
                client.player.connection.sendCommand("setblock 0 103 2 light[level=15]");
            }
        } else {
            client.player.connection.sendCommand("time set 6000");
            client.player.connection.sendCommand("setblock 0 101 1 air");
            client.player.connection.sendCommand("setblock 0 101 2 air");
            client.player.connection.sendCommand("setblock 2 102 1 air");
            client.player.connection.sendCommand("setblock 2 102 2 air");
            client.player.connection.sendCommand("setblock -1 102 1 air");
            client.player.connection.sendCommand("setblock -1 102 2 air");
            client.player.connection.sendCommand("setblock 0 103 1 air");
            client.player.connection.sendCommand("setblock 0 103 2 air");
        }
    }

    private static void frame(Minecraft client, Shot shot) {
        client.options.hideGui = true;
        boolean chick = shot.name.startsWith("chick-");
        boolean kite = shot.name.startsWith("kite-wings");
        boolean fly = shot.name.equals("kite-wings-flying");
        boolean open = shot.name.equals("kite-wings-back")
                || shot.name.equals("kite-wings-side")
                || shot.name.equals("kite-wings-three-quarter");
        boolean folded = shot.name.equals("kite-wings-folded-back");
        System.setProperty("quarkos_cosmetics.orbit", Float.toString(shot.orbit));
        System.clearProperty("quarkos_cosmetics.wingPose");
        if (kite) {
            System.setProperty("quarkos_cosmetics.zoom", "3.35");
            client.options.fov().set(50);
        } else {
            System.clearProperty("quarkos_cosmetics.zoom");
            client.options.fov().set(54);
        }
        if (open) {
            KiteWings.hold("flap", 0f);
        } else if (folded) {
            KiteWings.hold("idle", 0f);
        } else if (fly) {
            KiteWings.hold("flap", null);
        } else {
            KiteWings.hold(null, null);
        }
        client.options.setCameraType(shot.front ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
        boolean sleepingShot = shot.name.contains("sleep");
        if (!sleepingShot) {
            System.clearProperty("quarkos_cosmetics.bed");
        }
        if (!shot.name.contains("scared")) {
            System.clearProperty("quarkos_cosmetics.air");
        }
        client.player.setYRot(180f);
        client.player.yRotO = 180f;
        client.player.setYBodyRot(180f);
        client.player.yBodyRotO = 180f;
        client.player.setYHeadRot(180f);
        client.player.yHeadRotO = 180f;
        client.player.setXRot(kite || chick ? 8f : 0f);
        client.player.xRotO = kite || chick ? 8f : 0f;
        client.player.setSprinting(fly);
        client.player.getAbilities().mayfly = true;
        client.player.getAbilities().flying = fly && !chick;
        client.player.setNoGravity(false);
        if (!chick) {
            client.player.setPose(Pose.STANDING);
            client.player.stopFallFlying();
        }
        client.setCameraEntity(client.player);
        if (!chick) {
            Chick.hold(null, null);
            return;
        }
        boolean headShot = shot.name.equals("chick-head");
        System.setProperty("quarkos_cosmetics.zoom", headShot ? "3.2" : "2.55");
        System.setProperty("quarkos_cosmetics.drop", shot.name.contains("sleep") ? "0.15" : headShot ? "0.18" : "0.35");
        if (shot.name.contains("sleep")) {
            System.setProperty("quarkos_cosmetics.bed", "1");
        } else {
            System.clearProperty("quarkos_cosmetics.bed");
        }
        System.setProperty("quarkos_cosmetics.orbit", Float.toString(shot.orbit));
        client.options.fov().set(50);
        String clip = chickClipName(shot.name);
        boolean side = shot.name.equals("chick-head-side") || shot.name.equals("chick-shoulder-side");
        boolean shoulderView = shot.name.equals("chick-shoulder-front")
                || shot.name.equals("chick-compare-shoulder")
                || shot.name.equals("chick-shoulder-film");
        boolean front = shot.name.equals("chick-head-front") || shot.name.equals("chick-head-three-quarter")
                || shoulderView
                || shot.name.equals("chick-compare-head")
                || shot.name.equals("chick-head-idle-alert");
        if (shot.name.equals("chick-head-idle-alert")) {
            // Eight seconds of idle, then alert. A null time plays the clip instead of freezing a frame.
            Chick.hold(filmIndex >= 80 ? "alert" : "idle", null);
        } else if (shot.name.equals("chick-shoulder-film")) {
            Chick.hold("idle", null);
        } else if (shot.name.equals("chick-scared-film")) {
            Chick.hold("scared", null);
        } else if (shot.name.equals("chick-compare-head") || shot.name.equals("chick-compare-shoulder")) {
            // Idle at 0s is the identity scale, so the body is the geo cube and the reference matches.
            Chick.hold("idle", 0f);
        } else if (shot.name.equals("chick-to-chest") || shot.name.equals("chick-chest-asleep")
                || shot.name.equals("chick-compare-sleep") || chestSit(shot.name)) {
            Chick.hold(null, null);
        } else if (side) {
            Chick.hold(clip, 0f);
        } else if (front) {
            Chick.hold(clip, chickStill(clip));
        } else {
            Chick.hold(clip, shot.name.endsWith("-clip") ? null : chickStill(clip));
        }
        if (Chick.ID.equals(shot.id) && Chick.isPerch(shot.slot)) {
            ClientSettings.get().setChickPerch(shot.slot);
        }
        client.player.setSprinting(false);
        client.player.getAbilities().flying = false;
        client.options.fov().set(50);
        if (side) {
            System.setProperty("quarkos_cosmetics.side", shot.name.equals("chick-head-side") ? "head" : "shoulder");
            client.options.hideGui = true;
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            client.options.fov().set(shot.name.equals("chick-shoulder-side") ? 22 : 32);
        } else {
            System.clearProperty("quarkos_cosmetics.side");
        }
        if (front) {
            String which = shot.name.equals("chick-head-three-quarter") ? "three"
                    : shoulderView ? "shoulder" : "head";
            System.setProperty("quarkos_cosmetics.front", which);
            client.options.hideGui = true;
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            client.options.fov().set(shoulderView ? 32 : 30);
        } else {
            System.clearProperty("quarkos_cosmetics.front");
        }
        if (shot.name.equals("chick-chest-asleep")) {
            System.setProperty("quarkos_cosmetics.flat", "1");
            if (!client.player.isSleeping()) {
                client.player.startSleepInBed(new BlockPos(0, 101, 2));
            }
            if (client.player.isSleeping()) {
                System.setProperty("quarkos_cosmetics.bed", "1");
                client.screen = null;
                holdSleepTimer(client.player);
            }
            client.options.hideGui = true;
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            client.options.fov().set(36);
        } else {
            System.clearProperty("quarkos_cosmetics.flat");
        }
        if (chestSit(shot.name)) {
            String which = shot.name.equals("chick-chest-sit-three-quarter") ? "three"
                    : shot.name.equals("chick-chest-light") ? "light" : "side";
            System.setProperty("quarkos_cosmetics.sit", which);
            if (!client.player.isSleeping()) {
                client.player.startSleepInBed(new BlockPos(0, 101, 2));
            }
            if (client.player.isSleeping()) {
                System.setProperty("quarkos_cosmetics.bed", "1");
                client.screen = null;
                holdSleepTimer(client.player);
            }
            client.options.hideGui = true;
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            client.options.fov().set(shot.name.equals("chick-chest-light") ? 50
                    : shot.name.equals("chick-chest-sit-side") ? 22 : 28);
        } else if (shot.name.equals("chick-compare-sleep")) {
            System.setProperty("quarkos_cosmetics.sit", "face");
            if (!client.player.isSleeping()) {
                client.player.startSleepInBed(new BlockPos(0, 101, 2));
            }
            if (client.player.isSleeping()) {
                System.setProperty("quarkos_cosmetics.bed", "1");
                client.screen = null;
                holdSleepTimer(client.player);
            }
            client.options.hideGui = true;
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            client.options.fov().set(30);
        } else {
            System.clearProperty("quarkos_cosmetics.sit");
        }
        if (shot.name.equals("chick-to-chest")) {
            if (filmIndex < 0 && ticks % 5 == 0) {
                client.player.connection.sendCommand("kill @e[type=chicken]");
            }
            System.setProperty("quarkos_cosmetics.chest", "1");
            if (!client.player.isSleeping()) {
                client.player.setYRot(270f);
                client.player.yRotO = 270f;
                client.player.setYBodyRot(270f);
                client.player.yBodyRotO = 270f;
                client.player.setYHeadRot(270f);
                client.player.yHeadRotO = 270f;
            }
            if (filmIndex >= 6 && !client.player.isSleeping()) {
                client.player.startSleepInBed(new BlockPos(0, 101, 2));
            }
            if (client.player.isSleeping()) {
                System.setProperty("quarkos_cosmetics.bed", "1");
                client.screen = null;
                holdSleepTimer(client.player);
            }
            client.options.hideGui = true;
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else {
            System.clearProperty("quarkos_cosmetics.chest");
        }
        if (shot.name.contains("sleep") && !client.player.isSleeping()) {
            client.player.startSleepInBed(new BlockPos(0, 101, 2));
        }
        if (shot.name.contains("sleep") && client.player.isSleeping()) {
            // The bed screen draws the chat and the Leave Bed button. Clearing the field
            // skips that screen without calling stopSleeping. The sleep timer fades the
            // world to black, so the pose stays and the timer does not.
            client.screen = null;
            holdSleepTimer(client.player);
            if (ticks % 10 == 0) {
                client.player.connection.sendCommand("difficulty peaceful");
                client.player.connection.sendCommand("gamerule doMobSpawning false");
                client.player.connection.sendCommand("kill @e[type=!player]");
            }
        }
        if (shot.name.equals("chick-scared-film")) {
            // A real fall from the top of the world, well over 6 blocks. Gravity moves the
            // body. The camera follows. They are sent back up before their feet reach the floor.
            client.player.getAbilities().flying = false;
            client.player.setNoGravity(false);
            client.player.setOnGround(false);
            client.player.setPose(Pose.STANDING);
            client.options.fov().set(40);
            if (filmIndex < 0) {
                fallReleased = false;
                System.clearProperty("quarkos_cosmetics.fall");
                System.setProperty("quarkos_cosmetics.air", "310");
            } else {
                System.clearProperty("quarkos_cosmetics.air");
                System.setProperty("quarkos_cosmetics.fall", "1");
                if (!fallReleased || client.player.getY() < 118.0) {
                    fallReleased = true;
                    placeFalling(client.player, 310.0);
                }
            }
        } else if (shot.name.contains("scared")) {
            if (ticks <= 1) {
                airY = 112.0;
            }
            if (!shot.name.endsWith("-clip")) {
                airY = 108.0;
            } else {
                airY -= 0.45;
                if (airY < 104.0) {
                    airY = 112.0;
                }
            }
            System.setProperty("quarkos_cosmetics.air", String.format(java.util.Locale.US, "%.3f", airY));
            client.player.fallDistance = 8f;
            client.player.setOnGround(false);
            client.player.setPose(Pose.STANDING);
        }
    }

    /** Drop the local player from the top of the overworld. Old positions match so the camera does not smear. */
    private static void placeFalling(net.minecraft.world.entity.player.Player player, double y) {
        player.setPos(0.5, y, 0.5);
        player.xo = 0.5;
        player.yo = y;
        player.zo = 0.5;
        player.xOld = 0.5;
        player.yOld = y;
        player.zOld = 0.5;
        player.setDeltaMovement(0.0, 0.0, 0.0);
        player.fallDistance = 0f;
        player.setOnGround(false);
    }

    private static void holdSleepTimer(net.minecraft.world.entity.player.Player player) {
        try {
            java.lang.reflect.Field field = net.minecraft.world.entity.player.Player.class.getDeclaredField("sleepCounter");
            field.setAccessible(true);
            field.setInt(player, 0);
        } catch (ReflectiveOperationException e) {
            LOGGER.error("could not hold the sleep pose", e);
        }
    }

    private static boolean chickReady(Minecraft client, Shot shot) {
        if (shot.name.equals("chick-compare-sleep")) {
            if (!client.player.isSleeping() || !Chick.asleep()) {
                asleepSeenAt = -1;
                if (ticks - settledAt > 20 * 20) {
                    LOGGER.error("chick never reached sleep for the compare still, sleeping={}", client.player.isSleeping());
                    state = State.STOP;
                }
                return false;
            }
            if (asleepSeenAt < 0) {
                asleepSeenAt = ticks;
            }
            // A few ticks so the saved frame is the sleep clip, not the last settle frame.
            return ticks - asleepSeenAt >= 8;
        }
        if (shot.name.equals("chick-chest-asleep") || chestSit(shot.name)) {
            if (!client.player.isSleeping() || !Chick.asleep()) {
                if (ticks - settledAt > 20 * 20) {
                    LOGGER.error("chick never settled on the chest, sleeping={}", client.player.isSleeping());
                    state = State.STOP;
                }
                return false;
            }
            return true;
        }
        if (shot.name.contains("sleep")) {
            if (!client.player.isSleeping()) {
                sleepSeenAt = -1;
                if (ticks - settledAt > 20 * 12) {
                    LOGGER.error("player never slept, y={}", client.player.getY());
                    state = State.STOP;
                }
                return false;
            }
            if (sleepSeenAt < 0 || sleepSeenAt > ticks) {
                sleepSeenAt = ticks;
            }
            // Let the bed, the night, and the side camera land before the shot.
            // Give the peaceful rule time to remove hostiles before the frame is saved.
            if (ticks - sleepSeenAt < 80) {
                return false;
            }
        }
        return true;
    }

    /** Upright sit on the chest: side, three-quarter, and the chicken light comparison. */
    private static boolean chestSit(String name) {
        return name.equals("chick-chest-sit-side")
                || name.equals("chick-chest-sit-three-quarter")
                || name.equals("chick-chest-light");
    }

    private static String chickClipName(String name) {
        String body = name.startsWith("chick-") ? name.substring("chick-".length()) : name;
        if (body.endsWith("-clip")) {
            body = body.substring(0, body.length() - "-clip".length());
        }
        return switch (body) {
            case "head", "head-side", "head-front", "head-three-quarter" -> "sit";
            case "shoulder", "shoulder-side", "shoulder-front" -> "idle";
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

    /** Fifteen seconds at ten frames a second. The camera stays put for the whole take. */
    private static boolean filmShot(String name) {
        return name.equals("chick-to-chest")
                || name.equals("chick-head-idle-alert")
                || name.equals("chick-shoulder-film")
                || name.equals("chick-scared-film");
    }

    /** Plays one chick clip and writes an mp4 next to the stills. */
    private static void chickClip(Minecraft client, Shot shot) {
        int frames = filmShot(shot.name) ? 150 : 20;
        if (filmIndex < 0) {
            filmIndex = 0;
            filmStartTick = ticks;
            shotQueued = false;
            try {
                Path dir = chickFrame(shot.name, 0).getParent();
                if (Files.isDirectory(dir)) {
                    try (var listing = Files.list(dir)) {
                        listing.forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (Exception ignored) {
                                // A leftover frame must not be encoded into this take.
                            }
                        });
                    }
                }
            } catch (Exception ignored) {
                // The directory is created on the first frame.
            }
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
        String pattern = filmShot(name) ? "f%03d.png" : "f%02d.png";
        return Path.of("/tmp/chick-frames").resolve(name).resolve(String.format(pattern, index));
    }

    private static Path chickFilmOutput(String shotName) {
        return switch (shotName) {
            case "chick-shoulder-film" -> shots().resolve("chick-shoulder.mp4");
            case "chick-scared-film" -> shots().resolve("chick-scared.mp4");
            default -> shots().resolve(shotName + ".mp4");
        };
    }

    private static void encodeChickClip(String shotName, int frames) {
        Path mp4 = filmShot(shotName)
                ? chickFilmOutput(shotName)
                : shots().resolve("chick-" + chickClipName(shotName) + ".mp4");
        String digits = filmShot(shotName) ? "f%03d.png" : "f%02d.png";
        Path pattern = chickFrame(shotName, 0).getParent().resolve(digits);
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
        KiteWings.hold(null, null);
        client.player.setSprinting(false);
        client.player.getAbilities().flying = false;
        if (!pairStarted) {
            pairStarted = true;
            publishChickAda(client);
        }
        System.setProperty("quarkos_cosmetics.zoom", "3.35");
        System.setProperty("quarkos_cosmetics.drop", "0.55");
        boolean head = pairShot == 0;
        System.setProperty("quarkos_cosmetics.orbit", head ? "0" : "36");
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
        anchorGuest(client, false);
        if (guest != null) {
            guest.setXRot(8f);
            guest.xRotO = 8f;
        }
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

    private static void pair(Minecraft client) {
        if (client.player == null || client.level == null) {
            state = State.STOP;
            return;
        }
        if (System.getProperty("quarkos_cosmetics.only", "").contains("chick-two")) {
            chickPair(client);
            return;
        }
        KiteWings.hold(null, null);
        Chick.hold(null, null);
        client.player.setSprinting(false);
        client.player.getAbilities().flying = false;
        if (!pairStarted) {
            pairStarted = true;
            publishAda(client);
        }
        client.options.hideGui = true;
        boolean front = pairShot > 0;
        System.setProperty("quarkos_cosmetics.orbit", front ? "58" : "0");
        client.options.setCameraType(front ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
        client.player.connection.sendCommand("tp @s 48 70 48 180 0");
        if (!pairReady) {
            if (pairFailed || ticks > 20 * 40) {
                LOGGER.error("two-client gallery setup failed");
                state = State.STOP;
            }
            return;
        }
        anchorGuest(client, front);
        if (ticks < 50) {
            return;
        }
        String name = front ? "two-client-front" : "two-client";
        shootWhenReady(client, name, () -> {
            if (pairShot == 0) {
                pairShot = 1;
                ticks = 30;
                shotQueued = false;
                return;
            }
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

    private static void anchorGuest(Minecraft client, boolean front) {
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
        float body = 180f;
        float head = 180f;
        guest.setYRot(180f);
        guest.yRotO = 180f;
        guest.setYBodyRot(body);
        guest.yBodyRotO = body;
        guest.setYHeadRot(head);
        guest.yHeadRotO = head;
        guest.setXRot(0f);
        guest.xRotO = 0f;
        guest.xo = guest.getX();
        guest.yo = guest.getY();
        guest.zo = guest.getZ();
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
            //? if >=1.21.11 {
            Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> writeImage(path, image));
            //?} else {
            NativeImage image = Screenshot.takeScreenshot(client.getMainRenderTarget());
            writeImage(path, image);
            //?}
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
        WARDROBE,
        PAIR,
        STOP
    }

    private record Shot(String name, Slot slot, String id, boolean front, boolean flap, float orbit, String wing) {
    }
}
