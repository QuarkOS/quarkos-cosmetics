package dev.quarkos.cosmetics.harness;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import dev.quarkos.cosmetics.config.ClientSettings;
import dev.quarkos.cosmetics.config.TintColor;
import dev.quarkos.cosmetics.config.WardrobeScreen;
import dev.quarkos.cosmetics.gallery.EquipmentMirror;
import dev.quarkos.cosmetics.gallery.GalleryClient;
import dev.quarkos.cosmetics.model.ModelException;
import dev.quarkos.cosmetics.model.Slot;
import dev.quarkos.cosmetics.render.Chick;
import dev.quarkos.cosmetics.render.JackOLanternMask;
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
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    /** Harness pose applied again at render, so a tick cannot stand the player back up. */
    public enum BodyPose { STAND, SNEAK, SWIM, FLY }
    public static BodyPose bodyPose = BodyPose.STAND;
    /** Wardrobe captures after the screen is drawn. World portraits stay on the frame-end hook. */
    public static boolean afterGui;
    private static Path pendingGui;
    private static final List<Shot> SHOTS = List.of(
            new Shot("lantern_cap-front", Slot.HAT, "quarkos:lantern_cap", true, false, 0f, false),
            new Shot("lantern_cap-three-quarter", Slot.HAT, "quarkos:lantern_cap", true, false, 40f, false),
            new Shot("lantern_cap-side", Slot.HAT, "quarkos:lantern_cap", true, false, 90f, false),
            new Shot("paper_boat-front", Slot.HAT, "quarkos:paper_boat", true, false, 0f, false),
            new Shot("paper_boat-three-quarter", Slot.HAT, "quarkos:paper_boat", true, false, 40f, false),
            new Shot("paper_boat-side", Slot.HAT, "quarkos:paper_boat", true, false, 90f, false),
            new Shot("crescent_circlet-front", Slot.HAT, "quarkos:crescent_circlet", true, false, 0f, false),
            new Shot("crescent_circlet-three-quarter", Slot.HAT, "quarkos:crescent_circlet", true, false, 40f, false),
            new Shot("crescent_circlet-side", Slot.HAT, "quarkos:crescent_circlet", true, false, 90f, false),
            new Shot("jack_o_lantern_mask-front", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 0f, false),
            new Shot("jack_o_lantern_mask-three-quarter", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 40f, false),
            new Shot("jack_o_lantern_mask-side", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 90f, false),
            new Shot("jack_o_lantern_mask-front-night", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 0f, false),
            new Shot("jack_o_lantern_mask-three-quarter-night", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 40f, false),
            new Shot("jack_o_lantern_mask-side-night", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 90f, false),
            new Shot("jack_o_lantern_mask-front-night-zoom", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 0f, false),
            new Shot("gif-day-front-f12", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 0f, false),
            new Shot("gif-day-front-close-f12", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 0f, false),
            new Shot("gif-day-three-quarter-f12", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 40f, false),
            new Shot("gif-day-three-quarter-close-f12", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 40f, false),
            new Shot("gif-night-front-f12", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 0f, false),
            new Shot("gif-night-front-close-f12", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 0f, false),
            new Shot("gif-night-three-quarter-f12", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 40f, false),
            new Shot("gif-night-three-quarter-close-f12", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 40f, false),
            new Shot("gif-day-front-f15", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 0f, false),
            new Shot("gif-day-front-close-f15", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 0f, false),
            new Shot("gif-day-three-quarter-f15", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 40f, false),
            new Shot("gif-day-three-quarter-close-f15", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 40f, false),
            new Shot("gif-night-front-f15", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 0f, false),
            new Shot("gif-night-front-close-f15", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 0f, false),
            new Shot("gif-night-three-quarter-f15", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 40f, false),
            new Shot("gif-night-three-quarter-close-f15", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 40f, false),
            new Shot("spill-night-three-quarter-on-f12", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 40f, false),
            new Shot("spill-night-three-quarter-off-f12", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 40f, false),
            new Shot("spill-night-three-quarter-on-f15", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 40f, false),
            new Shot("spill-night-three-quarter-close-on-f12", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 40f, false),
            new Shot("spill-night-three-quarter-close-on-f15", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 40f, false),
            new Shot("fox_tips-front", Slot.EARS, "quarkos:fox_tips", true, false, 0f, false),
            new Shot("fox_tips-three-quarter", Slot.EARS, "quarkos:fox_tips", true, false, 40f, false),
            new Shot("fox_tips-side", Slot.EARS, "quarkos:fox_tips", true, false, 90f, false),
            new Shot("moth_tufts-front", Slot.EARS, "quarkos:moth_tufts", true, false, 0f, false),
            new Shot("moth_tufts-three-quarter", Slot.EARS, "quarkos:moth_tufts", true, false, 40f, false),
            new Shot("moth_tufts-side", Slot.EARS, "quarkos:moth_tufts", true, false, 90f, false),
            new Shot("ribbon_bows-front", Slot.EARS, "quarkos:ribbon_bows", true, false, 0f, false),
            new Shot("ribbon_bows-three-quarter", Slot.EARS, "quarkos:ribbon_bows", true, false, 40f, false),
            new Shot("ribbon_bows-side", Slot.EARS, "quarkos:ribbon_bows", true, false, 90f, false),
            new Shot("chick-front", Slot.HAT, Chick.ID, true, false, 0f, false),
            new Shot("chick-three-quarter", Slot.HAT, Chick.ID, true, false, 40f, false),
            new Shot("chick-side", Slot.HAT, Chick.ID, true, false, 90f, false),
            new Shot("scarecrow_straw_hat-front", Slot.HAT, "quarkos:scarecrow_straw_hat", true, false, 0f, false),
            new Shot("scarecrow_straw_hat-three-quarter", Slot.HAT, "quarkos:scarecrow_straw_hat", true, false, 40f, false),
            new Shot("scarecrow_straw_hat-side", Slot.HAT, "quarkos:scarecrow_straw_hat", true, false, 90f, false),
            new Shot("candle_skull_crown-front", Slot.HAT, "quarkos:candle_skull_crown", true, false, 0f, false),
            new Shot("candle_skull_crown-three-quarter", Slot.HAT, "quarkos:candle_skull_crown", true, false, 40f, false),
            new Shot("candle_skull_crown-side", Slot.HAT, "quarkos:candle_skull_crown", true, false, 90f, false),
            new Shot("candle_skull_crown-front-night", Slot.HAT, "quarkos:candle_skull_crown", true, false, 0f, false),
            new Shot("ghost_buddy-front", Slot.HAT, "quarkos:ghost_buddy", true, false, 0f, false),
            new Shot("ghost_buddy-three-quarter", Slot.HAT, "quarkos:ghost_buddy", true, false, 40f, false),
            new Shot("ghost_buddy-side", Slot.HAT, "quarkos:ghost_buddy", true, false, 90f, false),
            new Shot("ghost_buddy-front-night", Slot.HAT, "quarkos:ghost_buddy", true, false, 0f, false),
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
            new Shot("chick-to-chest", Slot.HAT, Chick.ID, false, false, 0f, false),
            new Shot("jack-o-lantern/mask-front-night", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 0f, false),
            new Shot("jack-o-lantern/mask-three-quarter-night", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 0f, false),
            new Shot("jack-o-lantern/mask-side-night", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 0f, false),
            new Shot("jack-o-lantern/mask-front-night-zoom", Slot.HAT, "quarkos:jack_o_lantern_mask", true, false, 0f, false),
            new Shot("sneak-front", Slot.HAT, "quarkos:lantern_cap", true, false, 0f, false),
            new Shot("sneak-back", Slot.HAT, "quarkos:lantern_cap", false, false, 0f, false),
            new Shot("swim-front", Slot.HAT, "quarkos:lantern_cap", true, false, 0f, false),
            new Shot("swim-back", Slot.HAT, "quarkos:lantern_cap", false, false, 0f, false),
            new Shot("elytra-flight", Slot.BACK, "quarkos:kite_wings", false, false, 0f, false),
            new Shot("elytra-hides-back", Slot.BACK, "quarkos:kite_wings", false, false, 0f, false),
            new Shot("armor-front", Slot.HAT, "quarkos:lantern_cap", true, false, 0f, false),
            new Shot("armor-back", Slot.BACK, "quarkos:folded_cloak", false, false, 0f, false),
            new Shot("skin-layer-front", Slot.HAT, "", true, false, 0f, false),
            new Shot("ears-hook", Slot.EARS, "quarkos:fox_tips", true, false, 0f, false),
            new Shot("skin-world-front", Slot.HAT, "", true, false, 0f, false),
            new Shot("skin-world-back", Slot.HAT, "", false, false, 0f, false),
            new Shot("skin-world-side", Slot.HAT, "", true, false, 90f, false),
            new Shot("skin-world-off-front", Slot.HAT, "", true, false, 0f, false)
    );

    private static final List<UiShot> UI_SHOTS = List.of(
            new UiShot("ui-wardrobe-hat", WardrobeScreen.View.WARDROBE, Slot.HAT, "quarkos:scarecrow_straw_hat", 2, false),
            new UiShot("ui-wardrobe-ears", WardrobeScreen.View.WARDROBE, Slot.EARS, "quarkos:scarecrow_straw_hat", 2, false),
            new UiShot("ui-wardrobe-colour", WardrobeScreen.View.WARDROBE, Slot.HAT, "quarkos:candle_skull_crown", 2, true),
            new UiShot("ui-gallery", WardrobeScreen.View.GALLERY, Slot.HAT, Chick.ID, 2, false),
            new UiShot("ui-settings", WardrobeScreen.View.SETTINGS, Slot.HAT, "quarkos:ghost_buddy", 2, false),
            new UiShot("ui-wardrobe-scale4", WardrobeScreen.View.WARDROBE, Slot.HAT, "quarkos:scarecrow_straw_hat", 4, false),
            new UiShot("ui-cards-zoom", WardrobeScreen.View.WARDROBE, Slot.HAT, "quarkos:scarecrow_straw_hat", 4, false),
            skinPreview("skin-preview-0", 0f, true),
            skinPreview("skin-preview-90", 90f, true),
            skinPreview("skin-preview-180", 180f, true),
            skinPreview("skin-preview-270", 270f, true),
            skinPreview("skin-preview-off-0", 0f, false),
            maskPreview("mask-preview-0", 0f),
            maskPreview("mask-preview-90", 90f),
            maskPreview("mask-preview-180", 180f),
            maskPreview("mask-preview-270", 270f),
            maskCard("mask-card-zoom", false),
            maskCard("mask-card-zoom-equipped", true)
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
    private static Set<String> shotFilter;
    private static List<UiShot> activeUi;
    private static int uiShotIndex;
    private static boolean layerTestSkin;
    private static ClientSettings preStudioSettings;
    private static int shotRequestedAt;
    private static volatile Path awaitingShotWrite;
    private static volatile Path writtenShot;
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

    private static Screen currentScreen(Minecraft client) {
        //? if >=26.3 {
        /*return client.gui.screen();
        *///?} else {
        return client.screen;
        //?}
    }

    private static void setScreen(Minecraft client, Screen screen) {
        //? if >=26.3 {
        /*client.gui.setScreen(screen);
        *///?} else {
        client.setScreen(screen);
        //?}
    }

    private static void setHudHidden(Minecraft client, boolean hidden) {
        //? if >=26.3 {
        /*if (client.gui.hud.isHidden() != hidden) {
            client.gui.hud.toggle();
        }
        *///?} else {
        client.options.hideGui = hidden;
        //?}
    }

    private static void resizeGui(Minecraft client) {
        //? if >=26.3 {
        /*client.resizeGui();
        *///?} else {
        client.resizeDisplay();
        //?}
    }

    private static void setStudioTime(Minecraft client, long time) {
        var server = client.getSingleplayerServer();
        if (server != null) {
            //? if >=26.3 {
            /*server.execute(() -> {
                var world = server.overworld();
                world.dimensionType().defaultClock().ifPresent(clock -> world.clockManager().setTotalTicks(clock, time));
            });
            *///?} else {
            server.overworld().setDayTime(time);
            //?}
        }
    }

    private static void sleepInStudioBed(net.minecraft.world.entity.player.Player player) {
        BlockPos bed = new BlockPos(1, 101, 0);
        //? if >=26.3 {
        /*player.startSleeping(bed);
        *///?} else {
        player.startSleepInBed(bed);
        //?}
    }

    public static boolean usesLayerTestSkin() {
        return layerTestSkin;
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
            setHudHidden(client, true);
            for (PlayerModelPart part : PlayerModelPart.values()) {
                //? if >=1.21.11 {
                client.options.setModelPart(part, true);
                //?} else {
                /*client.options.toggleModelPart(part, true);
                *///?}
            }
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
                case UI_WARDROBE -> uiWardrobe(client);
                case STOP -> stop(client);
            }
        } catch (RuntimeException e) {
            LOGGER.error("cosmetics harness failed in {}", state, e);
            state = State.STOP;
        }
    }

    private static void waitTitle(Minecraft client) {
        //? if >=26.3 {
        /*if (client.gui.overlay() != null || !(currentScreen(client) instanceof TitleScreen)) {
        *///?} else {
        if (client.getOverlay() != null || !(currentScreen(client) instanceof TitleScreen)) {
        //?}
            if (ticks > 20 * 180) {
                LOGGER.error("title screen did not appear, screen={}", currentScreen(client));
                state = State.STOP;
            }
            return;
        }
        state = State.WAIT_WORLD;
        ticks = 0;
    }

    private static void waitWorld(Minecraft client) {
        Screen screen = currentScreen(client);
        if (screen instanceof net.minecraft.client.gui.screens.ConfirmScreen) {
            pressFirst(screen);
            return;
        }
        if (!(screen instanceof CreateWorldScreen create)) {
            if (ticks == 1) {
                //? if >=1.21.11 {
                CreateWorldScreen.openFresh(client, () -> setScreen(client, new TitleScreen()));
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
        if (Boolean.getBoolean("quarkos_cosmetics.wardrobeOnly")) {
            state = wardrobeState();
        } else {
            state = State.SHOTS;
        }
        ticks = 0;
        shotIndex = 0;
        shotQueued = false;
        settledAt = -1;
        screenAt = -1;
    }

    private static void shots(Minecraft client) {
        if (client.player == null || client.level == null) {
            if (currentScreen(client) instanceof net.minecraft.client.gui.screens.ConfirmScreen) {
                pressFirst(currentScreen(client));
            }
            if (ticks > 20 * 180) {
                LOGGER.error("world did not load, screen={}", currentScreen(client));
                state = State.STOP;
            }
            return;
        }
        if (shotIndex >= activeShots().size()) {
            state = shotFilter().isEmpty() || shotFilter().stream().anyMatch(name -> name.contains("two-client"))
                    ? State.PAIR : wardrobeState();
            ticks = 0;
            shotQueued = false;
            settledAt = -1;
            screenAt = -1;
            return;
        }
        Shot shot = activeShots().get(shotIndex);
        layerTestSkin = shot.name.startsWith("skin-world-");
        if (layerTestSkin && preStudioSettings == null) {
            installStudioSettings();
        }
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
        if (shot.name.startsWith("chick-") && !headShowcase(shot.name) && !chickReady(client, shot)) {
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
        // The candle strip includes cream frames that wash the carving out.
        // Night shots wait for a frame whose flame texel is orange, so the eyes stay readable.
        if (shot.name.startsWith("jack_o_lantern_mask") && shot.name.contains("night") && !shotQueued) {
            int candle = Math.floorMod(client.player.tickCount / 2, 24);
            if (candle != 7 && candle != 12 && candle != 20) {
                return;
            }
        }
        if ((shot.name.startsWith("gif-") || shot.name.startsWith("spill-")) && !shotQueued) {
            int candle = Math.floorMod(client.player.tickCount / 2, 24);
            int wanted = gifFrame(shot.name);
            if (candle != wanted) {
                return;
            }
            LOGGER.info("gif shot {} locked to candle frame {}", shot.name, candle);
        }
        Runnable next = () -> {
            restoreStudioSettings();
            shotIndex++;
            ticks = 0;
            shotQueued = false;
            settledAt = -1;
            state = State.SHOTS;
        };
        if (layerTestSkin) {
            shootAndLog(client, shot.name, next);
        } else {
            shootWhenReady(client, shot.name, next);
        }
    }

    /** Candle frame named on a gif shot, as in {@code gif-day-front-f15}. */
    private static int gifFrame(String name) {
        int at = name.lastIndexOf("-f");
        if (at < 0) {
            return 12;
        }
        try {
            return Integer.parseInt(name.substring(at + 2));
        } catch (NumberFormatException ex) {
            return 12;
        }
    }

    private static List<Shot> activeShots() {
        if (active == null) {
            Set<String> names = shotFilter();
            if (names.isEmpty()) {
                active = SHOTS;
            } else {
                active = SHOTS.stream().filter(shot -> names.contains(shot.name())).toList();
                LOGGER.info("harness shot filter {}", active.stream().map(Shot::name).toList());
            }
        }
        return active;
    }

    private static Set<String> shotFilter() {
        if (shotFilter == null) {
            shotFilter = Arrays.stream(System.getProperty("quarkos_cosmetics.only", "").split(","))
                    .map(String::trim).filter(name -> !name.isEmpty()).collect(Collectors.toUnmodifiableSet());
        }
        return shotFilter;
    }

    private static List<UiShot> activeUiShots() {
        if (activeUi == null) {
            Set<String> names = shotFilter();
            if (names.isEmpty() && Boolean.getBoolean("quarkos_cosmetics.wardrobeOnly")) {
                activeUi = List.of();
            } else {
                activeUi = UI_SHOTS.stream().filter(shot -> names.isEmpty() || names.contains(shot.name())).toList();
            }
        }
        return activeUi;
    }

    private static State wardrobeState() {
        return shotFilter().isEmpty() || shotFilter().contains("wardrobe") ? State.WARDROBE : uiWardrobeState();
    }

    private static State uiWardrobeState() {
        return activeUiShots().isEmpty() ? State.STOP : State.UI_WARDROBE;
    }

    private static void installStudioSettings() {
        preStudioSettings = ClientSettings.get();
        ClientSettings studioSettings = new ClientSettings();
        studioSettings.galleryUrl = preStudioSettings.galleryUrl;
        ClientSettings.install(studioSettings);
    }

    private static void restoreStudioSettings() {
        layerTestSkin = false;
        if (preStudioSettings != null) {
            ClientSettings.install(preStudioSettings);
            preStudioSettings = null;
        }
    }

    private static void prepare(Minecraft client, Shot shot) {
        setScreen(client, null);
        setHudHidden(client, true);
        client.options.fov().set(54);
        client.options.gamma().set(1.0);
        client.options.bobView().set(false);
        if (!studio) {
            studio = true;
            client.player.connection.sendCommand("time set 6000");
            client.player.connection.sendCommand("weather clear");
            client.player.connection.sendCommand("gamemode creative");
            client.player.connection.sendCommand("difficulty peaceful");
            client.player.connection.sendCommand("kill @e[type=!player]");
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
        } else if (shot.name.equals("elytra-flight")) {
            client.player.connection.sendCommand("tp @s 0.5 108 0.5 180 20");
        } else if (!shot.name.contains("scared")) {
            client.player.connection.sendCommand("tp @s 0.5 101 0.5 180 8");
        } else {
            client.player.connection.sendCommand("tp @s 0.5 116 0.5 180 12");
        }
        if (shot.name.contains("night")) {
            client.player.connection.sendCommand("time set 18000");
            client.options.gamma().set(0.0);
        } else {
            client.player.connection.sendCommand("time set 6000");
            client.options.gamma().set(1.0);
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
        settings.skinLayers = layerTestSkin && !shot.name.contains("-off-");
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
        if (!layerTestSkin) {
            settings.save(client.gameDirectory.toPath());
        }
    }

    private static boolean headShowcase(String name) {
        return name.startsWith("lantern_cap-")
                || name.startsWith("paper_boat-")
                || name.startsWith("crescent_circlet-")
                || name.startsWith("jack_o_lantern_mask-")
                || name.startsWith("gif-")
                || name.startsWith("spill-")
                || name.startsWith("fox_tips-")
                || name.startsWith("moth_tufts-")
                || name.startsWith("ribbon_bows-")
                || name.equals("chick-front")
                || name.equals("chick-three-quarter")
                || name.equals("chick-side")
                || name.startsWith("scarecrow_straw_hat-")
                || name.startsWith("candle_skull_crown-")
                || name.startsWith("ghost_buddy-");
    }

    /** Full-body F5 framing. Night shots use the same camera; the zoom is the face close-up. */
    private static void frameHead(Minecraft client, Shot shot) {
        portraitLock = false;
        bodyPose = BodyPose.STAND;
        afterGui = false;
        KiteWings.hold(null, null);
        if (Chick.ID.equals(shot.id)) {
            Chick.hold("sit", 0.9f);
        } else {
            Chick.hold(null, null);
        }
        client.player.setSprinting(false);
        client.player.getAbilities().mayfly = true;
        client.player.getAbilities().flying = false;
        client.player.setShiftKeyDown(false);
        client.player.setSwimming(false);
        client.player.setNoGravity(false);
        client.player.setPose(net.minecraft.world.entity.Pose.STANDING);
        client.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY);
        client.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.item.ItemStack.EMPTY);
        client.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.item.ItemStack.EMPTY);
        client.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, net.minecraft.world.item.ItemStack.EMPTY);
        setHudHidden(client, true);
        client.options.bobView().set(false);
        if (!layerTestSkin) {
            ClientSettings.get().skinLayers = false;
            dev.quarkos.cosmetics.render.JackOLanternMask.setSpill(!shot.name.contains("-off-"));
        }
        boolean night = shot.name.contains("night");
        boolean zoom = shot.name.contains("zoom");
        boolean close = shot.name.contains("close");
        boolean three = shot.name.contains("three-quarter");
        client.options.gamma().set(night ? 0.0 : 1.0);
        setStudioTime(client, night ? 18000L : 6000L);
        if (client.player.tickCount % 10 == 0) {
            client.player.connection.sendCommand("kill @e[type=!player]");
        }
        client.player.setYRot(180f);
        client.player.setYHeadRot(180f);
        client.player.yRotO = 180f;
        client.player.yHeadRotO = 180f;
        client.player.setYBodyRot(180f);
        client.player.yBodyRotO = 180f;
        client.player.setXRot(0f);
        client.player.xRotO = 0f;
        client.setCameraEntity(client.player);
        if (zoom || close) {
            // Close sits on the pumpkin, a little above, so the carved walls read the way the GIF does.
            // Spill closes stay far enough that the stem and the chin both stay in frame.
            portraitLock = true;
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            boolean spillClose = close && shot.name.startsWith("spill-");
            float dist = spillClose ? 2.55f : close ? 1.55f : 1.9f;
            client.options.fov().set(close ? (spillClose ? 40 : 30) : 54);
            lockY = spillClose ? 102.55f : close ? 103.05f : 102.82f;
            lockPitch = spillClose ? 4f : close ? (three ? 18f : 8f) : 0f;
            if (three) {
                double rad = Math.toRadians(40);
                lockX = 0.5 - Math.sin(rad) * dist;
                lockZ = 0.5 - Math.cos(rad) * dist;
                lockYaw = 320f;
            } else {
                lockX = 0.5;
                lockZ = 0.5 - dist;
                lockYaw = 0f;
            }
            return;
        }
        portraitDistance = 2.55f;
        portraitDrop = 0.42f;
        portraitOrbit = shot.orbit;
        client.options.fov().set(54);
        client.options.setCameraType(shot.front ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
    }

    private static void frame(Minecraft client, Shot shot) {
        portraitLock = false;
        if (layerTestSkin || headShowcase(shot.name)) {
            frameHead(client, shot);
            return;
        }
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
        setHudHidden(client, true);
        client.options.setCameraType(shot.front ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
        client.player.setYRot(180f);
        client.player.setYHeadRot(180f);
        client.player.yRotO = 180f;
        client.player.yHeadRotO = 180f;
        client.player.setYBodyRot(180f);
        client.player.yBodyRotO = 180f;
        client.player.setXRot(8f);
        client.setCameraEntity(client.player);
        afterGui = false;
        bodyPose = BodyPose.STAND;
        client.player.setShiftKeyDown(false);
        client.player.setSwimming(false);
        client.player.setNoGravity(false);
        ClientSettings.get().skinLayers = false;
        if (shot.name.startsWith("sneak-")) {
            bodyPose = BodyPose.SNEAK;
            client.player.setShiftKeyDown(true);
            client.player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
            portraitDistance = 3.4f;
            portraitDrop = 0.2f;
            // A side angle shows the body pitch. Front-on, the crouch reads as a shorter stand.
            portraitOrbit = shot.front ? 70f : 250f;
            client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        } else if (shot.name.startsWith("swim-")) {
            bodyPose = BodyPose.SWIM;
            portraitLock = true;
            client.options.fov().set(46);
            // The crawl lies along the look axis. A three-quarter shows head and feet.
            boolean back = shot.name.endsWith("back");
            lockX = back ? 2.6 : -2.2;
            lockY = 102.35;
            lockZ = back ? 2.4 : -1.6;
            // Camera looks at the player: yaw = atan2(-(playerX-camX), playerZ-camZ).
            lockYaw = back ? 132f : 308f;
            lockPitch = 16f;
        } else if (shot.name.equals("elytra-flight")) {
            bodyPose = BodyPose.FLY;
            KiteWings.hold("flap", null);
            client.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ELYTRA));
            portraitLock = true;
            client.options.fov().set(42);
            lockX = 2.8;
            lockY = 109.6;
            lockZ = 3.8;
            lockYaw = 145f;
            lockPitch = 22f;
        } else if (shot.name.equals("elytra-hides-back")) {
            client.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ELYTRA));
            KiteWings.hold("idle", 0f);
            portraitDistance = 3.2f;
            portraitDrop = 0.45f;
        } else if (shot.name.startsWith("armor-")) {
            client.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET));
            client.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE));
            client.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_LEGGINGS));
            portraitDistance = 2.7f;
        } else if (shot.name.startsWith("skin-layer")) {
            ClientSettings.get().skinLayers = true;
            portraitDistance = 2.55f;
        } else if (shot.name.contains("night")) {
            client.player.setXRot(0f);
            client.player.xRotO = 0f;
            portraitLock = true;
            boolean zoom = shot.name.contains("zoom");
            boolean side = shot.name.contains("side");
            boolean three = shot.name.contains("three-quarter");
            float dist = zoom ? 1.12f : 1.28f;
            client.options.fov().set(zoom ? 30 : 40);
            setStudioTime(client, 18000L);
            client.options.gamma().set(0.0);
            if (side) {
                lockX = 0.5 - dist;
                lockY = 102.52;
                lockZ = 0.5;
                lockYaw = 270f;
                lockPitch = 0f;
            } else if (three) {
                double rad = Math.toRadians(40);
                lockX = 0.5 - Math.sin(rad) * dist;
                lockZ = 0.5 - Math.cos(rad) * dist;
                lockY = 102.52;
                lockYaw = 320f;
                lockPitch = 0f;
            } else {
                lockX = 0.5;
                lockY = 102.52;
                lockZ = 0.5 - dist;
                lockYaw = 0f;
                lockPitch = 0f;
            }
        }
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
            setHudHidden(client, true);
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
                sleepInStudioBed(client.player);
            }
            clearSleepFade(client.player);
            if (currentScreen(client) != null) {
                setScreen(client, null);
            }
            setHudHidden(client, true);
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
                sleepInStudioBed(client.player);
            }
            if (client.player.isSleeping()) {
                clearSleepFade(client.player);
                if (currentScreen(client) != null) {
                    setScreen(client, null);
                }
            }
            setHudHidden(client, true);
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
                sleepInStudioBed(client.player);
            }
            clearSleepFade(client.player);
            if (currentScreen(client) != null) {
                setScreen(client, null);
            }
            setHudHidden(client, true);
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
        if (shot.name.equals("chick-chest-asleep") && (!client.player.isSleeping() || currentScreen(client) != null || !Chick.asleep())) {
            if (ticks - settledAt > 20 * 15) {
                LOGGER.error("chick never settled on the chest, sleeping={} screen={}", client.player.isSleeping(), currentScreen(client));
                state = State.STOP;
            }
            return false;
        }
        if (shot.name.contains("sleep") && (!client.player.isSleeping() || currentScreen(client) != null)) {
            if (ticks - settledAt > 20 * 12) {
                LOGGER.error("player never slept, y={} screen={}", client.player.getY(), currentScreen(client));
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
                if (shotWritten(path)) {
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
        //? if >=26.3 {
        /*requestShot(client, path);
        *///?}
        //? if >=1.21.11 && <26.3 {
        Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> writeImage(path, image));
        //?}
        //? if <1.21.11 {
        /*NativeImage image = Screenshot.takeScreenshot(client.getMainRenderTarget());
        writeImage(path, image);
        *///?}
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
        setHudHidden(client, true);
        Chick.hold(head ? "sit" : "idle", head ? 0.9f : 0.5f);
        if (ticks % 20 == 0) {
            client.player.connection.sendCommand("tp @s 0.5 90 0.5 180 0");
        }
        if (!pairReady) {
            if (pairFailed || ticks > 20 * 40) {
                LOGGER.error("chick two-client gallery setup failed");
                enterWardrobe(activeUiShots().isEmpty() ? State.STOP : wardrobeState());
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
            enterWardrobe(wardrobeState());
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
                    if (shotWritten(path)) {
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
        //? if >=26.3 {
        /*requestShot(client, path);
        *///?}
        //? if >=1.21.11 && <26.3 {
        Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> writeImage(path, image));
        //?}
        //? if <1.21.11 {
        /*NativeImage image = Screenshot.takeScreenshot(client.getMainRenderTarget());
        writeImage(path, image);
        *///?}
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
        portraitLock = false;
        KiteWings.hold(null, null);
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
        setHudHidden(client, true);
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
                enterWardrobe(activeUiShots().isEmpty() ? State.STOP : wardrobeState());
            }
            return;
        }
        anchorGuest(client, 0f, 0f, true);
        if (ticks < 50) {
            return;
        }
        shootWhenReady(client, "two-client", () -> enterWardrobe(wardrobeState()));
    }

    private static void enterWardrobe(State next) {
        state = next;
        ticks = 0;
        shotQueued = false;
        settledAt = -1;
        screenAt = -1;
    }

    private static void wardrobe(Minecraft client) {
        if (!wardrobeWorldReady(client)) {
            return;
        }
        setHudHidden(client, true);
        afterGui = true;
        standInWardrobe(client);
        if (client.options.guiScale().get() != 1) {
            setScreen(client, null);
            client.options.guiScale().set(1);
            resizeGui(client);
            screenAt = -1;
            return;
        }
        if (!wardrobeStudioReady(client, () -> {
            resetLegacyWardrobe();
            ClientSettings.get().save(client.gameDirectory.toPath());
        })) {
            return;
        }
        if (!(currentScreen(client) instanceof WardrobeScreen)) {
            resetLegacyWardrobe();
            clearWardrobeArmour(client);
            setScreen(client, WardrobeScreen.legacyCapture());
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
            state = uiWardrobeState();
            ticks = 0;
            shotQueued = false;
            screenAt = -1;
        });
    }

    private static void uiWardrobe(Minecraft client) {
        if (!wardrobeWorldReady(client)) {
            return;
        }
        setHudHidden(client, false);
        afterGui = true;
        standInWardrobe(client);
        if (preStudioSettings == null) {
            installStudioSettings();
            setScreen(client, null);
            client.setCameraEntity(client.player);
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.player.getAbilities().flying = false;
            client.player.setSprinting(false);
            if (client.player.isSleeping()) {
                client.player.stopSleeping();
            }
            KiteWings.hold(null, null);
            Chick.hold(null, null);
        }
        if (!wardrobeStudioReady(client, () -> {})) {
            return;
        }

        UiShot shot = activeUiShots().get(uiShotIndex);
        if (screenAt < 0) {
            openUiShot(client, shot);
            screenAt = ticks;
            return;
        }
        if (ticks - screenAt < 8) {
            return;
        }
        if (!shotQueued && client.getWindow().getGuiScale() != shot.scale()) {
            LOGGER.error("UI shot {} requested GUI scale {}, got {}", shot.name(), shot.scale(), client.getWindow().getGuiScale());
            state = State.STOP;
            return;
        }
        shootAndLog(client, shot.name(), () -> {
            layerTestSkin = false;
            screenAt = -1;
            ticks = 0;
            if (++uiShotIndex == activeUiShots().size()) {
                state = State.STOP;
            }
        });
    }

    private static void openUiShot(Minecraft client, UiShot shot) {
        ClientSettings settings = ClientSettings.get();
        layerTestSkin = shot.layerTestSkin();
        for (Slot slot : Slot.values()) {
            settings.equip(slot, "");
        }
        settings.skinLayers = shot.skinLayers();
        settings.equip(Slot.HAT, shot.hat());
        settings.setTint(Slot.HAT, shot.colour() ? 0x7BBFFF : TintColor.DEFAULT);
        clearWardrobeArmour(client);

        // A filtered run can enter the wardrobe straight from a night portrait.
        setStudioTime(client, 6000L);
        client.options.gamma().set(1.0);
        setScreen(client, null);
        client.options.guiScale().set(shot.scale());
        // Minecraft caps scale 4 below a 1280 × 960 framebuffer.
        if (shot.scale() == 4 && (client.getWindow().getWidth() < 1280 || client.getWindow().getHeight() < 960)) {
            client.getWindow().setWindowed(1920, 1080);
        }
        resizeGui(client);
        setScreen(client, WardrobeScreen.studioCapture(
                shot.view(), shot.slot(), shot.colour(), shot.previewYaw(), shot.cardToReveal()));
    }

    private static boolean wardrobeWorldReady(Minecraft client) {
        if (client.player != null && client.level != null) {
            return true;
        }
        if (currentScreen(client) instanceof net.minecraft.client.gui.screens.ConfirmScreen) {
            pressFirst(currentScreen(client));
        }
        if (ticks > 20 * 180) {
            LOGGER.error("wardrobe world did not load, screen={}", currentScreen(client));
            state = State.STOP;
        }
        return false;
    }

    private static void standInWardrobe(Minecraft client) {
        bodyPose = BodyPose.STAND;
        portraitOrbit = 0f;
        client.player.setPose(net.minecraft.world.entity.Pose.STANDING);
        client.player.setSwimming(false);
        client.player.setShiftKeyDown(false);
        client.player.stopFallFlying();
        client.player.setNoGravity(false);
    }

    private static boolean wardrobeStudioReady(Minecraft client, Runnable prepareCosmetics) {
        if (settledAt >= 0) {
            return true;
        }
        if (ticks == 1 || ticks % 10 == 0) {
            setScreen(client, null);
            if (!studio) {
                studio = true;
                client.player.connection.sendCommand("time set 6000");
                client.player.connection.sendCommand("weather clear");
                client.player.connection.sendCommand("gamemode creative");
                client.player.connection.sendCommand("fill -24 100 -24 24 100 24 white_concrete");
                client.player.connection.sendCommand("fill -24 101 -24 24 116 24 air");
            }
            client.player.connection.sendCommand("tp @s 0.5 101 0.5 180 0");
            prepareCosmetics.run();
        }
        if (client.player.getY() >= 100.0) {
            settledAt = ticks;
            LOGGER.info("wardrobe studio ready at y={}", client.player.getY());
        } else if (ticks > 20 * 30) {
            LOGGER.error("wardrobe player never reached the studio, y={}", client.player.getY());
            state = State.STOP;
        }
        return false;
    }

    private static void resetLegacyWardrobe() {
        ClientSettings settings = ClientSettings.get();
        for (Slot slot : Slot.values()) {
            settings.categoryOn.put(slot, true);
            settings.equip(slot, "");
        }
        settings.skinLayers = true;
        settings.equip(Slot.BACK, "quarkos:kite_wings");
    }

    private static void clearWardrobeArmour(Minecraft client) {
        client.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY);
        client.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.item.ItemStack.EMPTY);
        client.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.item.ItemStack.EMPTY);
    }

    private static void publishAda(Minecraft client) {
        List<String> bodies = new ArrayList<>();
        List<Slot> slots = new ArrayList<>();
        try {
            for (String packed : List.of("quarkos:lantern_cap")) {
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
                for (Slot slot : Slot.values()) {
                    gallery.equip(session.token(), slot, null);
                }
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
        // Wings parent to the body. Head yaw alone leaves the body at 0, so the folded fan sits on one side.
        guest.setYBodyRot(yaw);
        guest.yRotO = yaw;
        guest.xRotO = pitch;
        guest.yHeadRotO = yaw;
        guest.yBodyRotO = yaw;
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
            if (shotWritten(path)) {
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

    private static boolean shotWritten(Path path) throws IOException {
        //? if >=26.3 {
        /*return path.equals(writtenShot);
        *///?} else {
        return Files.size(path) > 1000L;
        //?}
    }

    private static void shootAndLog(Minecraft client, String name, Runnable next) {
        Path path = shots().resolve(name + ".png");
        if (!shotQueued) {
            try {
                Files.createDirectories(path.getParent());
            } catch (Exception ex) {
                LOGGER.error("could not create screenshot directory", ex);
                state = State.STOP;
                return;
            }
            awaitingShotWrite = path;
            shotRequestedAt = ticks;
            shotQueued = true;
            requestShot(client, path);
        } else if (path.equals(writtenShot)) {
            LOGGER.info("UI_SHOT_DONE {}", name);
            awaitingShotWrite = null;
            writtenShot = null;
            shotQueued = false;
            next.run();
        } else if (ticks - shotRequestedAt > 20 * 20) {
            LOGGER.error("screenshot {} was not written", path);
            state = State.STOP;
        }
    }

    /** Mask proof captures after the candle pass, so the copy includes the emissive layer. */
    public static void shotAtFrameEnd() {
        frameEndShots = true;
    }

    /** Called at the end of GameRenderer.render, after the wardrobe screen is on the target. */
    public static void captureGuiPending() {
        Path path = pendingGui;
        if (path == null) {
            return;
        }
        pendingGui = null;
        Minecraft client = Minecraft.getInstance();
        //? if >=1.21.11 {
        writeTargetNow(client, path);
        //?} else {
        NativeImage image = Screenshot.takeScreenshot(client.getMainRenderTarget());
        writeImage(path, image);
        //?}
    }

    public static void holdBody(net.minecraft.world.entity.player.Player player) {
        if (bodyPose == BodyPose.STAND) {
            return;
        }
        player.setDeltaMovement(0.0, 0.0, 0.0);
        player.setYRot(180f);
        player.yRotO = 180f;
        player.setYHeadRot(180f);
        player.yHeadRotO = 180f;
        player.setYBodyRot(180f);
        player.yBodyRotO = 180f;
        if (bodyPose == BodyPose.SNEAK) {
            player.setShiftKeyDown(true);
            player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
            player.setOnGround(true);
            return;
        }
        if (bodyPose == BodyPose.SWIM) {
            player.setSwimming(true);
            player.setPose(net.minecraft.world.entity.Pose.SWIMMING);
            player.setXRot(0f);
            player.xRotO = 0f;
            player.setOnGround(false);
            return;
        }
        player.setPos(0.5, 108.0, 0.5);
        player.xo = 0.5;
        player.yo = 108.0;
        player.zo = 0.5;
        player.setNoGravity(true);
        player.setOnGround(false);
        player.setXRot(20f);
        player.xRotO = 20f;
        player.setPose(net.minecraft.world.entity.Pose.FALL_FLYING);
        player.startFallFlying();
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
        //? if >=26.3 {
        /*awaitingShotWrite = path;
        writtenShot = null;
        *///?}
        if (afterGui) {
            pendingGui = path;
            return;
        }
        if (frameEndShots) {
            pendingShot = path;
            return;
        }
        //? if >=26.3 {
        /*writeTargetNow(client, path);
        *///?}
        //? if >=1.21.11 && <26.3 {
        if ("forge".equals(System.getProperty("quarkos_cosmetics.loader"))) {
            writeTargetNow(client, path);
            return;
        }
        Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> writeImage(path, image));
        //?}
        //? if <1.21.11 {
        /*NativeImage image = Screenshot.takeScreenshot(client.getMainRenderTarget());
        writeImage(path, image);
        *///?}
    }

    //? if >=26.3 {
    /*private static void writeTargetNow(Minecraft client, Path path) {
        Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), image -> writeImage(path, image));
    }
    *///?}
    //? if >=1.21.11 && <26.3 {
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
            if (path.equals(awaitingShotWrite)) {
                writtenShot = path;
            }
        } catch (Exception e) {
            LOGGER.error("failed to write {}", path, e);
        } finally {
            image.close();
        }
    }

    private static void stop(Minecraft client) {
        if (ticks > 10) {
            restoreStudioSettings();
            LOGGER.info("cosmetics harness finished");
            LOGGER.info("HARNESS_DONE");
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
        UI_WARDROBE,
        STOP
    }

    private record Shot(String name, Slot slot, String id, boolean front, boolean spread, float orbit, boolean film) {
    }

    private static UiShot skinPreview(String name, float yaw, boolean skinLayers) {
        return new UiShot(name, WardrobeScreen.View.SETTINGS, Slot.HAT, "", 2, false, yaw, true, skinLayers, null);
    }

    private static UiShot maskPreview(String name, float yaw) {
        return new UiShot(name, WardrobeScreen.View.WARDROBE, Slot.HAT, JackOLanternMask.ID, 2, false,
                yaw, false, true, null);
    }

    private static UiShot maskCard(String name, boolean equipped) {
        return new UiShot(name, WardrobeScreen.View.WARDROBE, Slot.HAT, equipped ? JackOLanternMask.ID : "", 4, false,
                null, false, true, JackOLanternMask.ID);
    }

    private record UiShot(String name, WardrobeScreen.View view, Slot slot, String hat, int scale, boolean colour,
                          Float previewYaw, boolean layerTestSkin, boolean skinLayers, String cardToReveal) {
        private UiShot(String name, WardrobeScreen.View view, Slot slot, String hat, int scale, boolean colour) {
            this(name, view, slot, hat, scale, colour, null, false, true, null);
        }
    }
}
