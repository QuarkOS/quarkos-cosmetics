package dev.quarkos.cosmetics.config;

import dev.quarkos.cosmetics.gallery.GalleryClient;
import dev.quarkos.cosmetics.gallery.GalleryWork;
import dev.quarkos.cosmetics.mixin.InventoryPreviewAccess;
import dev.quarkos.cosmetics.model.Slot;
import dev.quarkos.cosmetics.render.ModelLibrary;
import dev.quarkos.cosmetics.render.SkinImages;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Structured wardrobe. Regions come from {@link WardrobeLayout} so a reskin can keep the same ids. */
public final class WardrobeScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("quarkos_cosmetics");
    private final Screen parent;
    private Slot slot = Slot.HAT;
    private boolean pickedSlot;
    private boolean loggedThumbs;
    private String selectedPacked = "";
    private String selectedGallery = "";
    private EditBox urlBox;
    private EditBox queryBox;
    private List<WardrobeLayout.Rect> regions = List.of();
    private final List<Thumb> thumbs = new ArrayList<>();
    private float spin = 110f;

    public WardrobeScreen(Screen parent) {
        super(Component.translatable("screen.quarkos_cosmetics.title"));
        this.parent = parent;
    }

    public void rebuild() {
        if (this.minecraft == null) {
            return;
        }
        this.clearWidgets();
        this.init();
    }

    @Override
    protected void init() {
        this.regions = WardrobeLayout.regions(this.width, this.height);
        ClientSettings settings = ClientSettings.get();
        if (!pickedSlot) {
            for (Slot value : Slot.values()) {
                if (!settings.equippedId(value).isBlank()) {
                    slot = value;
                    break;
                }
            }
            pickedSlot = true;
        }
        WardrobeLayout.Rect header = region(WardrobeLayout.HEADER);
        WardrobeLayout.Rect tabs = region(WardrobeLayout.TABS);
        WardrobeLayout.Rect list = region(WardrobeLayout.LIST);
        WardrobeLayout.Rect detail = region(WardrobeLayout.DETAIL);
        WardrobeLayout.Rect gallery = region(WardrobeLayout.GALLERY);
        WardrobeLayout.Rect settingsRect = region(WardrobeLayout.SETTINGS);

        addRenderableWidget(Button.builder(Component.translatable("text.quarkos_cosmetics.done"), button -> close())
                .bounds(header.x() + header.w() - 70, header.y(), 70, 20).build());

        int tabW = Math.max(36, (tabs.w() - 8) / Slot.values().length);
        int tabX = tabs.x();
        for (Slot value : Slot.values()) {
            Slot chosen = value;
            String label = value == slot ? "[" + shortName(value) + "]" : shortName(value);
            addRenderableWidget(Button.builder(Component.literal(label), button -> {
                slot = chosen;
                selectedGallery = "";
                rebuild();
            }).bounds(tabX, tabs.y(), Math.min(tabW, tabs.w()), 20).build());
            tabX += tabW + 2;
            if (tabX > tabs.x() + tabs.w() - 20) {
                break;
            }
        }

        thumbs.clear();
        int cell = 44;
        int cols = Math.max(1, list.w() / cell);
        int index = 0;
        String equipped = settings.equippedId(slot);
        for (ModelLibrary.Resolved resolved : ModelLibrary.packed(slot)) {
            int col = index % cols;
            int row = index / cols;
            int x = list.x() + col * cell;
            int y = list.y() + row * cell;
            if (y + cell > list.y() + list.h()) {
                break;
            }
            String id = resolved.id();
            Button button = Button.builder(Component.empty(), pressed -> {
                selectedPacked = id;
                settings.equip(slot, id);
                save();
                rebuild();
            }).bounds(x, y, cell - 4, cell - 4).build();
            button.setTooltip(Tooltip.create(Component.literal(resolved.name())));
            addRenderableWidget(button);
            thumbs.add(new Thumb(button, resolved.texture(), id.equals(equipped)));
            index++;
        }

        addRenderableWidget(Button.builder(Component.literal("Empty slot"), button -> {
            GalleryWork.clear(slot);
            selectedPacked = "";
        }).bounds(detail.x(), detail.y(), Math.min(90, detail.w()), 20).build());
        addRenderableWidget(Button.builder(Component.literal(settings.categoryOn.getOrDefault(slot, true) ? shortName(slot) + " on" : shortName(slot) + " off"), button -> {
            settings.categoryOn.put(slot, !settings.categoryOn.getOrDefault(slot, true));
            save();
            rebuild();
        }).bounds(detail.x() + 94, detail.y(), Math.min(90, detail.w()), 20).build());

        urlBox = new EditBox(this.font, gallery.x(), gallery.y(), Math.max(40, gallery.w() - 62), 18, Component.literal("Gallery URL"));
        urlBox.setMaxLength(200);
        urlBox.setValue(settings.galleryUrl);
        urlBox.setResponder(value -> {
            String next = GalleryClient.normalizeBase(value);
            if (!next.equals(GalleryClient.normalizeBase(settings.galleryUrl))) {
                GalleryWork.dropSession();
            }
            settings.galleryUrl = next.isBlank() ? ClientSettings.DEFAULT_GALLERY : next;
            save();
        });
        addRenderableWidget(urlBox);
        addRenderableWidget(Button.builder(Component.literal("Refresh"), button -> GalleryWork.browse(slot, queryBox == null ? "" : queryBox.getValue()))
                .bounds(gallery.x() + gallery.w() - 58, gallery.y(), 58, 18).build());

        queryBox = new EditBox(this.font, gallery.x(), gallery.y() + 22, Math.max(40, gallery.w() / 2), 18, Component.literal("Search"));
        queryBox.setMaxLength(48);
        queryBox.setHint(Component.literal("Search"));
        addRenderableWidget(queryBox);

        int actionX = gallery.x() + gallery.w() / 2 + 4;
        int actionW = Math.max(40, gallery.w() / 2 - 4);
        addRenderableWidget(Button.builder(Component.literal("Sign in"), button -> GalleryWork.signIn())
                .bounds(actionX, gallery.y() + 22, actionW, 18).build());

        int row = gallery.y() + 44;
        for (GalleryClient.Listed listed : GalleryWork.browse()) {
            if (row > gallery.y() + gallery.h() - 78) {
                break;
            }
            Slot listedSlot = Slot.parse(listed.slot()).orElse(slot);
            if (listedSlot != slot) {
                continue;
            }
            String id = listed.id();
            String label = listed.name() + " · " + listed.author();
            addRenderableWidget(Button.builder(Component.literal(trim(label, 32)), button -> {
                selectedGallery = id;
                GalleryWork.equip(slot, id);
            }).bounds(gallery.x(), row, gallery.w(), 18).build());
            row += 20;
        }

        int foot = gallery.y() + gallery.h() - 20;
        addRenderableWidget(Button.builder(Component.literal("Publish packed"), button -> {
            String id = !selectedPacked.isBlank() ? selectedPacked : settings.equippedId(slot);
            GalleryWork.publishPacked(id);
        }).bounds(gallery.x(), foot, Math.min(110, gallery.w() / 2), 18).build());
        addRenderableWidget(Button.builder(Component.literal("Report"), button -> GalleryWork.report(selectedGallery))
                .bounds(gallery.x() + Math.min(114, gallery.w() / 2 + 4), foot, Math.min(70, gallery.w() / 2), 18).build());

        int sy = settingsRect.y();
        addRenderableWidget(Button.builder(Component.literal(settings.hideOthers ? "Others hidden" : "Others shown"), button -> {
            settings.hideOthers = !settings.hideOthers;
            save();
            rebuild();
        }).bounds(settingsRect.x(), sy, 110, 18).build());
        addRenderableWidget(Button.builder(Component.literal(settings.skinLayers ? "3D skin on" : "3D skin off"), button -> {
            settings.skinLayers = !settings.skinLayers;
            save();
            rebuild();
        }).bounds(settingsRect.x() + 114, sy, 100, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Skin " + settings.skinDistance + "m"), button -> {
            settings.skinDistance = switch (settings.skinDistance) {
                case 8 -> 16;
                case 16 -> 32;
                case 32 -> 64;
                default -> 8;
            };
            save();
            rebuild();
        }).bounds(settingsRect.x() + 218, sy, 90, 18).build());
        addRenderableWidget(Button.builder(Component.literal(itemHidden() ? "Item hidden" : "Item shown"), button -> {
            String id = settings.equippedId(slot);
            if (id.isBlank()) {
                return;
            }
            if (!settings.disabled.remove(id)) {
                settings.disabled.add(id);
            }
            save();
            rebuild();
        }).bounds(settingsRect.x(), sy + 22, 110, 18).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, WardrobeLayout.Palette.INK);
        for (WardrobeLayout.Rect rect : regions) {
            graphics.fill(rect.x() - 2, rect.y() - 2, rect.x() + rect.w() + 2, rect.y() + rect.h() + 2, WardrobeLayout.Palette.PANEL);
            graphics.fill(rect.x() - 2, rect.y() - 2, rect.x() + rect.w() + 2, rect.y() - 1, WardrobeLayout.Palette.LINE);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, region(WardrobeLayout.HEADER).x(), region(WardrobeLayout.HEADER).y() + 6, WardrobeLayout.Palette.AMBER, false);
        WardrobeLayout.Rect preview = region(WardrobeLayout.PREVIEW);
        graphics.drawString(this.font, "Preview", preview.x() + 4, preview.y() + 4, WardrobeLayout.Palette.MUTED, false);
        graphics.drawString(this.font, "Packed", region(WardrobeLayout.LIST).x(), region(WardrobeLayout.LIST).y() - 10, WardrobeLayout.Palette.PAPER, false);
        graphics.drawString(this.font, "Gallery", region(WardrobeLayout.GALLERY).x(), region(WardrobeLayout.GALLERY).y() - 10, WardrobeLayout.Palette.PAPER, false);
        WardrobeLayout.Rect gallery = region(WardrobeLayout.GALLERY);
        String status = trim(GalleryWork.status(), Math.max(16, gallery.w() / 6));
        graphics.drawString(this.font, status, gallery.x(), gallery.y() + gallery.h() - 40, WardrobeLayout.Palette.MUTED, false);
        int painted = 0;
        for (Thumb thumb : thumbs) {
            if (thumb.equipped) {
                int x = thumb.button.getX();
                int y = thumb.button.getY();
                graphics.fill(x, y, x + thumb.button.getWidth(), y + 2, WardrobeLayout.Palette.AMBER);
            }
            if (thumb.paint(graphics)) {
                painted++;
            }
        }
        if (!loggedThumbs && painted > 0) {
            loggedThumbs = true;
            LOGGER.info("wardrobe painted {} item thumbnails", painted);
        }
        if (this.minecraft != null && this.minecraft.player != null) {
            int top = preview.y() + 18;
            int bottom = preview.y() + preview.h() - 8;
            int box = Math.max(24, bottom - top);
            // Leave a clear margin above the head and below the feet at every turn.
            int scale = Math.max(18, Math.round(box * 0.24f));
            spinPreview(graphics, preview, scale, this.minecraft.player);
        } else {
            graphics.drawString(this.font, "Join a world to preview.", preview.x() + 6, preview.y() + 28, WardrobeLayout.Palette.PAPER, false);
        }
    }

    /** Rear three-quarter. A full spin turns the wings edge-on and they read as strips down the front. */
    private float rearYaw() {
        spin += 0.02f;
        return 158f + (float) Math.sin(spin) * 14f;
    }

    private void spinPreview(GuiGraphics graphics, WardrobeLayout.Rect preview, int scale, net.minecraft.world.entity.LivingEntity player) {
        int x0 = preview.x() + 6;
        int y0 = preview.y() + 18;
        int x1 = preview.x() + preview.w() - 6;
        int y1 = preview.y() + preview.h() - 8;
        graphics.fill(x0, y0, x1, y1, 0xFF100E0C);
        //? if >=1.21.11 {
        for (int guard = 0; guard < 8 && graphics.scissorStack.peek() != null; guard++) {
            graphics.disableScissor();
        }
        var state = InventoryPreviewAccess.quarkos$extract(player);
        if (state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState living) {
            float yaw = rearYaw();
            living.bodyRot = 180f + yaw;
            living.yRot = yaw;
            living.xRot = 0f;
            float entityScale = living.scale == 0f ? 1f : living.scale;
            living.boundingBoxWidth /= entityScale;
            living.boundingBoxHeight /= entityScale;
            living.scale = 1f;
            living.shadowRadius = 0f;
        }
        Quaternionf camera = new Quaternionf().rotateX(0.06f);
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI).mul(camera);
        Vector3f translation = new Vector3f(0f, state.boundingBoxHeight / 2f + 0.0625f, 0f);
        graphics.submitEntityRenderState(state, scale, translation, pose, camera, x0, y0, x1, y1);
        //?} else {
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI).rotateY((float) Math.toRadians(rearYaw()));
        Quaternionf camera = new Quaternionf().rotateX(0.06f);
        Vector3f translation = new Vector3f(0f, player.getBbHeight() / 2f + 0.0625f, 0f);
        float x = (x0 + x1) / 2f;
        float y = y1 - 4f;
        InventoryScreen.renderEntityInInventory(graphics, x, y, scale, translation, pose, camera, player);
        //?}
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        close();
    }

    private void close() {
        save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private void save() {
        if (this.minecraft != null) {
            ClientSettings.get().save(this.minecraft.gameDirectory.toPath());
        }
    }

    private boolean itemHidden() {
        return ClientSettings.get().disabled.contains(ClientSettings.get().equippedId(slot));
    }

    private WardrobeLayout.Rect region(String id) {
        return WardrobeLayout.find(regions, id);
    }

    private static String shortName(Slot slot) {
        return switch (slot) {
            case HAT -> "Hat";
            case EARS -> "Ears";
            case SHOULDER_LEFT -> "L.Sh";
            case SHOULDER_RIGHT -> "R.Sh";
            case BACK -> "Back";
            case TAIL -> "Tail";
        };
    }

    private static final class Thumb {
        private final Button button;
        private final Identifier texture;
        private final boolean equipped;
        private int[] argb;
        private int width;
        private int height;

        private Thumb(Button button, Identifier texture, boolean equipped) {
            this.button = button;
            this.texture = texture;
            this.equipped = equipped;
        }

        private boolean paint(GuiGraphics graphics) {
            ensure();
            int dest = Math.min(32, button.getWidth() - 6);
            if (dest < 8) {
                return false;
            }
            int ox = button.getX() + (button.getWidth() - dest) / 2;
            int oy = button.getY() + (button.getHeight() - dest) / 2 + 1;
            graphics.fill(ox - 1, oy - 1, ox + dest + 1, oy + dest + 1, 0xFF14110E);
            if (argb == null) {
                return false;
            }
            for (int sy = 0; sy < height; sy++) {
                int y0 = oy + sy * dest / height;
                int y1 = oy + (sy + 1) * dest / height;
                for (int sx = 0; sx < width; sx++) {
                    int color = argb[sy * width + sx];
                    if (((color >>> 24) & 255) < 16) {
                        continue;
                    }
                    int x0 = ox + sx * dest / width;
                    int x1 = ox + (sx + 1) * dest / width;
                    graphics.fill(x0, y0, x1, Math.max(y0 + 1, y1), color | 0xFF000000);
                }
            }
            return true;
        }

        private void ensure() {
            if (argb != null) {
                return;
            }
            SkinImages.Pixels pixels = SkinImages.read(texture);
            if (pixels == null || pixels.width() <= 0 || pixels.height() <= 0 || pixels.argb().length < pixels.width() * pixels.height()) {
                return;
            }
            argb = pixels.argb();
            width = pixels.width();
            height = pixels.height();
        }
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
