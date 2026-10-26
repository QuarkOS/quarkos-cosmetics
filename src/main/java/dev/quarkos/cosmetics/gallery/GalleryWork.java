package dev.quarkos.cosmetics.gallery;

import dev.quarkos.cosmetics.config.ClientSettings;
import dev.quarkos.cosmetics.config.WardrobeScreen;
import dev.quarkos.cosmetics.model.ModelException;
import dev.quarkos.cosmetics.model.Slot;
import dev.quarkos.cosmetics.render.ModelLibrary;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Gallery calls leave the client thread. The bearer token stays in memory.
 * The Minecraft access token is read only for the Mojang join step.
 */
public final class GalleryWork {
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "quarkos-gallery");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile String bearer;
    private static volatile String status = "Gallery idle.";
    private static volatile List<GalleryClient.Listed> browse = List.of();
    private static int reportIndex;

    private GalleryWork() {
    }

    public static String status() {
        return status;
    }

    public static List<GalleryClient.Listed> browse() {
        return browse;
    }

    public static boolean signedIn() {
        return bearer != null && !bearer.isBlank();
    }

    public static void dropSession() {
        bearer = null;
    }

    public static void signIn() {
        Minecraft minecraft = Minecraft.getInstance();
        var user = minecraft.getUser();
        String name = user.getName();
        UUID profile = user.getProfileId();
        String accessToken = user.getAccessToken();
        status = "Signing in…";
        EXEC.execute(() -> {
            try {
                GalleryClient.Session session = client().signIn(name, accessToken, profile);
                onClient(() -> {
                    bearer = session.token();
                    ClientSettings settings = ClientSettings.get();
                    settings.uuid = session.uuid();
                    settings.playerName = session.name();
                    settings.save(minecraft.gameDirectory.toPath());
                    status = "Signed in as " + session.name() + ".";
                    refreshScreen();
                });
            } catch (Exception ex) {
                fail(ex);
            }
        });
    }

    public static void browse(Slot slot, String query) {
        status = "Loading the gallery…";
        EXEC.execute(() -> {
            try {
                GalleryClient.Page page = client().list(query, slot, null, 20);
                onClient(() -> {
                    browse = List.copyOf(page.items());
                    status = browse.isEmpty() ? "Nothing published for this slot yet." : browse.size() + " gallery cosmetics.";
                    refreshScreen();
                });
            } catch (Exception ex) {
                fail(ex);
            }
        });
    }

    public static void equip(Slot slot, String cosmeticId) {
        if (!signedIn()) {
            status = "Sign in before equipping a gallery cosmetic.";
            refreshScreen();
            return;
        }
        String token = bearer;
        EXEC.execute(() -> {
            try {
                GalleryClient client = client();
                client.equip(token, slot, cosmeticId);
                GalleryClient.Detail detail = client.cosmetic(cosmeticId);
                onClient(() -> {
                    remember(detail);
                    ClientSettings.get().equip(slot, cosmeticId);
                    save();
                    status = "Equipped " + detail.listed().name() + ".";
                    refreshScreen();
                });
            } catch (Exception ex) {
                fail(ex);
            }
        });
    }

    public static void clear(Slot slot) {
        ClientSettings.get().equip(slot, "");
        save();
        if (!signedIn()) {
            status = "Cleared " + slot.wire + ".";
            refreshScreen();
            return;
        }
        String token = bearer;
        EXEC.execute(() -> {
            try {
                client().equip(token, slot, null);
                onClient(() -> {
                    status = "Cleared " + slot.wire + ".";
                    refreshScreen();
                });
            } catch (Exception ex) {
                fail(ex);
            }
        });
    }

    public static void publishPacked(String packedId) {
        if (!signedIn()) {
            status = "Sign in before publishing.";
            refreshScreen();
            return;
        }
        ModelLibrary.Resolved resolved = ModelLibrary.resolve(packedId);
        if (resolved == null || resolved.png() == null) {
            status = "Equip a packed cosmetic before publishing.";
            refreshScreen();
            return;
        }
        final String body;
        try {
            body = resolved.model().publishBody(resolved.name(), resolved.description(), resolved.slot(), "texture", resolved.png());
        } catch (ModelException ex) {
            status = ex.getMessage();
            refreshScreen();
            return;
        }
        String token = bearer;
        Slot slot = resolved.slot();
        EXEC.execute(() -> {
            try {
                GalleryClient client = client();
                GalleryClient.Listed listed = client.publish(token, body);
                client.equip(token, slot, listed.id());
                GalleryClient.Detail detail = client.cosmetic(listed.id());
                onClient(() -> {
                    remember(detail);
                    ClientSettings.get().equip(slot, listed.id());
                    save();
                    status = "Published " + listed.name() + ".";
                    browse(slot, "");
                });
            } catch (Exception ex) {
                fail(ex);
            }
        });
    }

    public static void report(String cosmeticId) {
        if (cosmeticId == null || cosmeticId.isBlank()) {
            status = "Select a gallery cosmetic to report.";
            refreshScreen();
            return;
        }
        if (!signedIn()) {
            status = "Sign in before reporting.";
            refreshScreen();
            return;
        }
        String[] reasons = {"spam", "stolen", "inappropriate", "malicious", "other"};
        String reason = reasons[reportIndex % reasons.length];
        reportIndex++;
        String token = bearer;
        EXEC.execute(() -> {
            try {
                GalleryClient.Report report = client().report(token, cosmeticId, reason, "Reported from the wardrobe.");
                onClient(() -> {
                    status = "Report " + report.status() + " (" + reason + ").";
                    refreshScreen();
                });
            } catch (Exception ex) {
                fail(ex);
            }
        });
    }

    public static void refreshEquipment(String uuid) {
        EXEC.execute(() -> {
            try {
                GalleryClient.Equipment equipment = client().equipment(uuid);
                onClient(() -> {
                    EquipmentMirror.accept(uuid, equipment);
                    for (Slot slot : Slot.values()) {
                        String id = equipment.id(slot);
                        if (id != null && !id.isBlank() && ModelLibrary.resolve(id) == null) {
                            fetchModel(id);
                        }
                    }
                });
            } catch (Exception ex) {
                onClient(() -> EquipmentMirror.failed(uuid));
            }
        });
    }

    private static void fetchModel(String id) {
        EXEC.execute(() -> {
            try {
                GalleryClient.Detail detail = client().cosmetic(id);
                onClient(() -> remember(detail));
            } catch (Exception ignored) {
                // A missing model leaves that slot empty until the next refresh.
            }
        });
    }

    private static void remember(GalleryClient.Detail detail) {
        Slot slot = Slot.parse(detail.listed().slot()).orElse(null);
        if (slot == null) {
            return;
        }
        ModelLibrary.remember(detail.listed().id(), detail.listed().name(), detail.listed().description(), slot, detail.model());
    }

    private static GalleryClient client() {
        return new GalleryClient(ClientSettings.get().galleryUrl);
    }

    private static void save() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientSettings.get().save(minecraft.gameDirectory.toPath());
    }

    private static void fail(Exception ex) {
        String message = ex instanceof GalleryException gallery ? gallery.getMessage() : "The gallery request failed.";
        if (message == null || message.toLowerCase(java.util.Locale.ROOT).contains("accesstoken")) {
            message = "The gallery request failed.";
        }
        String text = message;
        onClient(() -> {
            status = text;
            refreshScreen();
        });
    }

    private static void onClient(Runnable runnable) {
        Minecraft.getInstance().execute(runnable);
    }

    private static void refreshScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof WardrobeScreen wardrobe) {
            wardrobe.rebuild();
        }
    }
}
