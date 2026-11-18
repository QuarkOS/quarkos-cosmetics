package dev.quarkos.cosmetics.gallery;

import dev.quarkos.cosmetics.config.ClientSettings;
import dev.quarkos.cosmetics.config.WardrobeScreen;
import dev.quarkos.cosmetics.model.ModelException;
import dev.quarkos.cosmetics.model.Slot;
import dev.quarkos.cosmetics.render.Chick;
import dev.quarkos.cosmetics.render.ModelLibrary;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private static final ExecutorService PREVIEW_EXEC = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "quarkos-gallery-preview");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile String bearer;
    private static volatile String status = "Gallery idle.";
    private static volatile List<GalleryClient.Listed> browse = List.of();
    private static volatile SignInStatus signInStatus = SignInStatus.SIGNED_OUT;
    private static volatile BrowseView browseView = new BrowseView(null, BrowseStatus.IDLE);
    private static int reportIndex;
    private static final Set<String> PREVIEW_REQUESTS = new HashSet<>();

    public enum SignInStatus { SIGNED_OUT, SIGNING_IN, SIGNED_IN, ERROR }

    public enum BrowseStatus { IDLE, LOADING, READY, ERROR }

    private record BrowseView(Slot slot, BrowseStatus status) {
    }

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

    public static SignInStatus signInStatus() {
        return signInStatus;
    }

    public static BrowseStatus browseStatus(Slot slot) {
        BrowseView view = browseView;
        return view.slot() == slot ? view.status() : BrowseStatus.IDLE;
    }

    public static void dropSession() {
        bearer = null;
        signInStatus = SignInStatus.SIGNED_OUT;
    }

    public static void signIn() {
        Minecraft minecraft = Minecraft.getInstance();
        var user = minecraft.getUser();
        String name = user.getName();
        UUID profile = user.getProfileId();
        String accessToken = user.getAccessToken();
        status = "Signing in…";
        signInStatus = SignInStatus.SIGNING_IN;
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
                    signInStatus = SignInStatus.SIGNED_IN;
                    refreshScreen();
                });
            } catch (Exception ex) {
                fail(ex, () -> signInStatus = SignInStatus.ERROR);
            }
        });
    }

    public static void browse(Slot slot, String query) {
        BrowseView request = new BrowseView(slot, BrowseStatus.LOADING);
        browseView = request;
        PREVIEW_REQUESTS.clear();
        status = "Loading the gallery…";
        EXEC.execute(() -> {
            try {
                GalleryClient.Page page = client().list(query, slot, null, 20);
                onClient(() -> {
                    browse = List.copyOf(page.items());
                    finishBrowse(request, BrowseStatus.READY);
                    status = browse.isEmpty() ? "Nothing published for this slot yet." : browse.size() + " gallery cosmetics.";
                    refreshScreen();
                });
            } catch (Exception ex) {
                fail(ex, () -> finishBrowse(request, BrowseStatus.ERROR));
            }
        });
    }

    /** The listing contains only metadata. Fetch a model when its card becomes visible. */
    public static void loadPreview(String id) {
        BrowseView view = browseView;
        if (view.status() != BrowseStatus.READY || ModelLibrary.resolve(id) != null || !PREVIEW_REQUESTS.add(id)) {
            return;
        }
        String url = ClientSettings.get().galleryUrl;
        PREVIEW_EXEC.execute(() -> {
            // Requests queued for an old search can be skipped before opening a connection.
            if (browseView != view) {
                return;
            }
            try {
                GalleryClient.Detail detail = new GalleryClient(url).cosmetic(id);
                onClient(() -> {
                    if (url.equals(ClientSettings.get().galleryUrl) && ModelLibrary.resolve(id) == null) {
                        remember(detail);
                        refreshScreen();
                    }
                });
            } catch (Exception ignored) {
                // The slot icon stays until the gallery is searched again.
            }
        });
    }

    private static void finishBrowse(BrowseView request, BrowseStatus status) {
        if (browseView == request) {
            browseView = new BrowseView(request.slot(), status);
        }
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

    /** Publishes the one chick into the perch slot when signed in. The slot is the sync field. */
    public static void syncChick(Slot perch) {
        if (perch != Slot.HAT) {
            return;
        }
        ClientSettings settings = ClientSettings.get();
        settings.setChickPerch(perch);
        save();
        if (!signedIn()) {
            status = "Chick on " + perch.wire + ".";
            refreshScreen();
            return;
        }
        String token = bearer;
        String cached = settings.chickGallery.get(perch);
        EXEC.execute(() -> {
            try {
                GalleryClient client = client();
                String id = cached;
                if (id == null || id.isBlank()) {
                    ModelLibrary.Resolved resolved = ModelLibrary.resolve(Chick.ID);
                    if (resolved == null || resolved.png() == null) {
                        throw new GalleryException(400, "validation_failed", "The chick model is not loaded.");
                    }
                    String body = resolved.model().publishBody(resolved.name(), resolved.description(), perch, "texture", resolved.png());
                    id = client.publish(token, body).id();
                }
                client.equip(token, perch, id);
                for (Slot other : new Slot[] {Slot.HAT, Slot.SHOULDER_LEFT, Slot.SHOULDER_RIGHT}) {
                    if (other != perch && settings.chickGallery.get(other) != null) {
                        client.equip(token, other, null);
                    }
                }
                GalleryClient.Detail detail = client.cosmetic(id);
                String equippedId = id;
                onClient(() -> {
                    settings.chickGallery.put(perch, equippedId);
                    remember(detail);
                    settings.setChickPerch(perch);
                    settings.equip(perch, equippedId);
                    for (Slot other : new Slot[] {Slot.HAT, Slot.SHOULDER_LEFT, Slot.SHOULDER_RIGHT}) {
                        if (other != perch && settings.isChickGallery(settings.equippedId(other))) {
                            settings.equip(other, "");
                        }
                    }
                    save();
                    status = "Chick on " + perch.wire + ".";
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
        fail(ex, () -> {});
    }

    private static void fail(Exception ex, Runnable updateView) {
        String message = ex instanceof GalleryException gallery ? gallery.getMessage() : "The gallery request failed.";
        if (message == null || message.toLowerCase(java.util.Locale.ROOT).contains("accesstoken")) {
            message = "The gallery request failed.";
        }
        String text = message;
        onClient(() -> {
            updateView.run();
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
