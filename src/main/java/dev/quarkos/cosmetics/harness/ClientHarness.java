package dev.quarkos.cosmetics.harness;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import dev.quarkos.cosmetics.config.ClientSettings;
import dev.quarkos.cosmetics.gallery.EquipmentMirror;
import dev.quarkos.cosmetics.gallery.GalleryClient;
import dev.quarkos.cosmetics.model.ModelException;
import dev.quarkos.cosmetics.model.Slot;
import dev.quarkos.cosmetics.render.ModelLibrary;
import net.minecraft.client.CameraType;
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
import java.util.UUID;

/**
 * Headless portrait pass. Third person, HUD hidden, one cosmetic at a time, front then back.
 * The last frame is another player wearing cosmetics published through the local gallery.
 */
public final class ClientHarness {
    private static final Logger LOGGER = LoggerFactory.getLogger("quarkos_cosmetics");
    private static final List<Shot> SHOTS = List.of(
            new Shot("kite-wings-front", Slot.BACK, "quarkos:kite_wings", true, false),
            new Shot("kite-wings-back", Slot.BACK, "quarkos:kite_wings", false, false),
            new Shot("kite-wings-flap", Slot.BACK, "quarkos:kite_wings", false, true),
            new Shot("lantern-cap-front", Slot.HAT, "quarkos:lantern_cap", true, false),
            new Shot("lantern-cap-back", Slot.HAT, "quarkos:lantern_cap", false, false),
            new Shot("fox-ears-front", Slot.EARS, "quarkos:fox_tips", true, false),
            new Shot("fox-ears-back", Slot.EARS, "quarkos:fox_tips", false, false),
            new Shot("ribbon-tail-front", Slot.TAIL, "quarkos:ribbon_tail", true, false),
            new Shot("ribbon-tail-back", Slot.TAIL, "quarkos:ribbon_tail", false, false),
            new Shot("ink-wren-front", Slot.SHOULDER_LEFT, "quarkos:ink_wren", true, false),
            new Shot("ink-wren-back", Slot.SHOULDER_LEFT, "quarkos:ink_wren", false, false),
            new Shot("pebble-moth-front", Slot.SHOULDER_RIGHT, "quarkos:pebble_moth", true, false),
            new Shot("pebble-moth-back", Slot.SHOULDER_RIGHT, "quarkos:pebble_moth", false, false)
    );

    private static State state = State.WAIT_TITLE;
    private static int ticks;
    private static boolean armed;
    private static boolean shotQueued;
    private static boolean studio;
    private static int shotIndex;
    private static int settledAt = -1;
    private static boolean pairStarted;
    private static int pairShot;
    private static volatile boolean pairReady;
    private static volatile boolean pairFailed;
    private static volatile String pairKey = "";
    private static volatile UUID pairId;
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
        if (shotIndex >= SHOTS.size()) {
            state = State.PAIR;
            ticks = 0;
            shotQueued = false;
            return;
        }
        Shot shot = SHOTS.get(shotIndex);
        frame(client, shot.front);
        // Commands sent on the join tick are dropped. Keep trying until the teleport sticks, then let the camera leave the ground.
        if (settledAt < 0) {
            if (ticks == 1 || ticks % 10 == 0) {
                prepare(client, shot);
                shotQueued = false;
            }
            if (client.player.getY() >= 100.0) {
                settledAt = ticks;
                LOGGER.info("studio ready at y={}", client.player.getY());
            } else if (ticks > 20 * 30) {
                LOGGER.error("player never reached the studio, y={}", client.player.getY());
                state = State.STOP;
            }
            return;
        }
        if (ticks - settledAt < 40) {
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
        client.player.connection.sendCommand("tp @s 0.5 101 0.5 180 0");
        client.player.connection.sendCommand("item replace entity @s armor.head with air");
        client.player.connection.sendCommand("item replace entity @s armor.chest with air");
        client.player.connection.sendCommand("item replace entity @s armor.legs with air");
        client.player.connection.sendCommand("clear @s");
        client.player.setShiftKeyDown(false);
        client.player.setSwimming(false);
        client.player.stopFallFlying();
        ClientSettings settings = ClientSettings.get();
        settings.skinLayers = false;
        settings.hideOthers = false;
        settings.disabled.clear();
        for (Slot slot : Slot.values()) {
            settings.categoryOn.put(slot, true);
            settings.equip(slot, "");
        }
        settings.equip(shot.slot, shot.id);
        settings.save(client.gameDirectory.toPath());
    }

    private static void frame(Minecraft client, boolean front) {
        client.options.hideGui = true;
        System.setProperty("quarkos_cosmetics.orbit", "0");
        client.options.setCameraType(front ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
        client.player.setYRot(180f);
        client.player.yRotO = 180f;
        client.player.setYBodyRot(180f);
        client.player.yBodyRotO = 180f;
        client.player.setYHeadRot(180f);
        client.player.yHeadRotO = 180f;
        client.player.setXRot(0f);
        client.player.xRotO = 0f;
        client.setCameraEntity(client.player);
    }

    private static void pair(Minecraft client) {
        if (client.player == null || client.level == null) {
            state = State.STOP;
            return;
        }
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
        PAIR,
        STOP
    }

    private record Shot(String name, Slot slot, String id, boolean front, boolean flap) {
    }
}
