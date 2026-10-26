package dev.quarkos.cosmetics.forge.harness;

import dev.quarkos.cosmetics.config.CosmeticsScreen;
import dev.quarkos.cosmetics.forge.gallery.GalleryCache;
import dev.quarkos.cosmetics.forge.gallery.GallerySeenScreen;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class ForgeHarness {
    private static final Logger LOGGER = LoggerFactory.getLogger("quarkos_cosmetics");
    private static final String[] PIECES = {
            "kite-wings",
            "lantern-cap",
            "fox-ears",
            "ribbon-tail",
            "ink-wren",
            "pebble-moth"
    };

    private static State state = State.WAIT_TITLE;
    private static int ticks;
    private static int piece;
    private static boolean front = true;
    private static boolean pair;
    private static boolean armed;
    private static boolean shotQueued;

    private ForgeHarness() {
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
            client.options.gamma().set(1.0);
            client.options.guiScale().set(2);
            client.options.renderDistance().set(6);
            client.options.simulationDistance().set(5);
            client.options.framerateLimit().set(30);
            LOGGER.info("forge cosmetics harness armed");
        }
        ticks++;
        try {
            switch (state) {
                case WAIT_TITLE -> waitTitle(client);
                case WAIT_WORLD -> waitWorld(client);
                case PREPARE -> prepare(client);
                case SHOOT -> shootWhenReady(client, shotName(), ForgeHarness::advance);
                case STOP -> stop(client);
            }
        } catch (RuntimeException e) {
            LOGGER.error("forge harness failed in {}", state, e);
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

    private static String shotName() {
        if (pair) {
            return "two-client";
        }
        return PIECES[piece] + (front ? "-front" : "-back");
    }

    private static void openSettings(Minecraft client) {
        showAll();
        client.setScreen(new dev.quarkos.cosmetics.config.CosmeticsScreen(client.screen));
        state = State.SHOOT_SETTINGS;
        ticks = 0;
        shotQueued = false;
    }

    private static void openWorld() {
        state = State.WAIT_WORLD;
        ticks = 0;
        shotQueued = false;
    }

    private static void waitWorld(Minecraft client) {
        Screen screen = client.screen;
        if (screen instanceof net.minecraft.client.gui.screens.ConfirmScreen) {
            pressFirst(screen);
            return;
        }
        if (!(screen instanceof CreateWorldScreen create)) {
            if (ticks == 1) {
                WorldOpener.open(client);
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
        ui.setName("cosmetics");
        presets.stream().filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().ifPresent(ui::setWorldType);
        if (!press(create, "selectWorld.create")) {
            LOGGER.error("could not press the create button");
            state = State.STOP;
            return;
        }
        state = State.PREPARE;
        piece = 0;
        front = true;
        pair = false;
        ticks = 0;
    }

    private static void prepare(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null || client.getOverlay() != null) {
            if (client.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen) {
                pressFirst(client.screen);
            }
            if (ticks > 20 * 180) {
                LOGGER.error("world did not load, screen={}", client.screen);
                state = State.STOP;
            }
            return;
        }
        if (ticks == 1) {
            client.options.hideGui = true;
            frame(client);
            player.connection.sendCommand("gamerule doDaylightCycle false");
            player.connection.sendCommand("time set 6000");
            player.connection.sendCommand("weather clear");
            player.connection.sendCommand("gamemode creative");
            player.connection.sendCommand("fill -8 96 -8 8 100 8 minecraft:white_concrete");
            player.connection.sendCommand("fill -8 101 -8 8 105 8 minecraft:air");
            player.connection.sendCommand("tp @s 0.5 101 0.5 180 0");
            player.connection.sendCommand("clear");
        }
        if (ticks < (pair ? 30 : 25) || player.tickCount < 40) {
            return;
        }
        apply(player, pair ? "two-client" : PIECES[piece]);
        if (pair && GalleryCache.view() == null && ticks < 20 * 20) {
            return;
        }
        player.setYRot(180f);
        player.setYHeadRot(180f);
        player.setXRot(0f);
        player.setDeltaMovement(0, 0, 0);
        frame(client);
        state = State.SHOOT;
        ticks = 0;
        shotQueued = false;
    }

    private static void frame(Minecraft client) {
        client.options.setCameraType(pair || front ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
        client.options.fov().set(pair ? 62 : 30);
    }

    private static void advance() {
        shotQueued = false;
        ticks = 0;
        if (pair) {
            state = State.STOP;
            return;
        }
        front = true;
        piece++;
        if (piece >= PIECES.length) {
            pair = true;
        }
        state = State.PREPARE;
    }

    private static void openGallery(Minecraft client) {
        client.options.hideGui = false;
        if (client.player != null) {
            client.player.setPose(Pose.STANDING);
            client.player.setNoGravity(false);
        }
        client.setScreen(new GallerySeenScreen(null));
        state = State.SHOOT_GALLERY;
        ticks = 0;
        shotQueued = false;
    }

    private static void apply(LocalPlayer player, String name) {
        clearFlags();
        player.setPose(Pose.STANDING);
        player.setShiftKeyDown(false);
        player.setSwimming(false);
        player.setNoGravity(false);
        GalleryCache.showNeighbor(false);
        player.setPose(Pose.STANDING);
        switch (name) {
            case "kite-wings" -> CosmeticsScreen.wings = true;
            case "lantern-cap" -> CosmeticsScreen.hat = true;
            case "fox-ears" -> CosmeticsScreen.ears = true;
            case "ribbon-tail" -> CosmeticsScreen.tail = true;
            case "ink-wren" -> CosmeticsScreen.leftShoulder = true;
            case "pebble-moth" -> CosmeticsScreen.rightShoulder = true;
            case "two-client" -> GalleryCache.showNeighbor(true);
            default -> {
            }
        }
    }

    private static boolean needsItem(String name) {
        return name.equals("slot-shoulder-left") || name.equals("pose-elytra");
    }

    private static boolean hasChest(LocalPlayer player) {
        return !player.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
    }

    private static void equip(LocalPlayer player, String item) {
        if (player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA) && item.endsWith("elytra")) {
            return;
        }
        if (player.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE) && item.endsWith("iron_chestplate")) {
            return;
        }
        player.connection.sendCommand("item replace entity @s armor.chest with " + item);
    }

    private static void showAll() {
        CosmeticsScreen.wings = true;
        CosmeticsScreen.hat = true;
        CosmeticsScreen.ears = true;
        CosmeticsScreen.tail = true;
        CosmeticsScreen.leftShoulder = true;
        CosmeticsScreen.rightShoulder = true;
        CosmeticsScreen.hideOthers = false;
        CosmeticsScreen.skinLayers = true;
    }

    private static void clearFlags() {
        CosmeticsScreen.wings = false;
        CosmeticsScreen.hat = false;
        CosmeticsScreen.ears = false;
        CosmeticsScreen.tail = false;
        CosmeticsScreen.leftShoulder = false;
        CosmeticsScreen.rightShoulder = false;
        CosmeticsScreen.hideOthers = false;
        CosmeticsScreen.skinLayers = false;
    }

    private static void shootWhenReady(Minecraft client, String name, Runnable next) {
        Path path = shots().resolve(name + ".png");
        if (!shotQueued) {
            if (ticks < 20) {
                return;
            }
            shotQueued = true;
            try {
                Files.createDirectories(path.getParent());
            } catch (Exception e) {
                LOGGER.error("could not create shot directory", e);
                state = State.STOP;
                return;
            }
            ShotWriter.save(client, path);
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

    private static void stop(Minecraft client) {
        if (ticks > 10) {
            client.stop();
        }
    }

    private static boolean press(Screen screen, String key) {
        String label = Component.translatable(key).getString().toLowerCase(Locale.ROOT);
        for (var child : screen.children()) {
            if (child instanceof Button button) {
                String message = button.getMessage().getString().toLowerCase(Locale.ROOT);
                if (message.equals(label) || message.contains("create")) {
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
        ShotWriter.press(button);
    }

    private static Path shots() {
        String configured = System.getProperty("quarkos_cosmetics.shots");
        if (configured == null || configured.isBlank()) {
            return Path.of("/cursor/stores/bc-1243ce51-36c9-58c7-8fa4-153844455530/media/forge");
        }
        return Path.of(configured);
    }

    private enum State {
        WAIT_TITLE,
        OPEN_SETTINGS,
        SHOOT_SETTINGS,
        WAIT_WORLD,
        PREPARE,
        SHOOT,
        GALLERY_SCREEN,
        SHOOT_GALLERY,
        STOP
    }
}
