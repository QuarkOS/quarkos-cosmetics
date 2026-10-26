package dev.quarkos.cosmetics.harness;

import dev.quarkos.cosmetics.config.CosmeticsScreen;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ClientHarness {
    private static final Logger LOGGER = LoggerFactory.getLogger("quarkos_cosmetics");
    private static State state = State.WAIT_TITLE;
    private static int ticks;
    private static boolean armed;
    private static boolean shotQueued;

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
            client.options.renderDistance().set(6);
            client.options.simulationDistance().set(5);
            client.options.framerateLimit().set(30);
            LOGGER.info("cosmetics harness armed");
        }
        ticks++;
        try {
            switch (state) {
                case WAIT_TITLE -> waitTitle(client);
                case OPEN_SETTINGS -> openSettings(client);
                case SHOOT_SETTINGS -> shootWhenReady(client, "settings", ClientHarness::openWorld);
                case WAIT_WORLD -> waitWorld(client);
                case POSE -> pose(client);
                case SHOOT_PLAYER -> shootWhenReady(client, "cosmetic-player", () -> {
                    state = State.STOP;
                    ticks = 0;
                });
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
        state = State.OPEN_SETTINGS;
        ticks = 0;
    }

    private static void openSettings(Minecraft client) {
        if (!(client.screen instanceof TitleScreen)) {
            if (ticks > 20 * 20) {
                state = State.STOP;
            }
            return;
        }
        client.setScreen(new CosmeticsScreen(client.screen));
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
                CreateWorldScreen.openFresh(client, () -> client.setScreen(new TitleScreen()));
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
        state = State.POSE;
        ticks = 0;
    }

    private static void pose(Minecraft client) {
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
        if (ticks < 30) {
            return;
        }
        client.options.hideGui = true;
        client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (ticks == 30) {
            client.player.connection.sendCommand("time set day");
            client.player.connection.sendCommand("weather clear");
            client.player.connection.sendCommand("gamemode creative");
            client.player.connection.sendCommand("tp @s ~ ~ ~ 180 8");
            return;
        }
        client.player.setYRot(180f);
        client.player.setYHeadRot(180f);
        client.player.setXRot(8f);
        if (ticks < 90) {
            return;
        }
        state = State.SHOOT_PLAYER;
        ticks = 0;
        shotQueued = false;
    }

    private static void shootWhenReady(Minecraft client, String name, Runnable next) {
        Path path = shots().resolve(name + ".png");
        if (!shotQueued) {
            if (ticks < 25) {
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
            Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
                try {
                    image.writeToFile(path);
                    LOGGER.info("wrote {}", path);
                } catch (Exception e) {
                    LOGGER.error("failed to write {}", path, e);
                } finally {
                    image.close();
                }
            });
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
        OPEN_SETTINGS,
        SHOOT_SETTINGS,
        WAIT_WORLD,
        POSE,
        SHOOT_PLAYER,
        STOP
    }
}
