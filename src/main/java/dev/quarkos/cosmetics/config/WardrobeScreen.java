package dev.quarkos.cosmetics.config;

import dev.quarkos.cosmetics.gallery.GalleryClient;
import dev.quarkos.cosmetics.render.Chick;
import dev.quarkos.cosmetics.gallery.GalleryWork;
import dev.quarkos.cosmetics.model.Slot;
import dev.quarkos.cosmetics.render.ModelLibrary;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Structured wardrobe. Regions come from {@link WardrobeLayout} so a reskin can keep the same ids. */
public final class WardrobeScreen extends Screen {
    private final Screen parent;
    private Slot slot = Slot.HAT;
    private String selectedPacked = "";
    private String selectedGallery = "";
    private EditBox urlBox;
    private EditBox queryBox;
    private List<WardrobeLayout.Rect> regions = List.of();

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

        int y = list.y();
        for (ModelLibrary.Resolved resolved : ModelLibrary.packed(slot)) {
            if (y > list.y() + list.h() - 22) {
                break;
            }
            String id = resolved.id();
            String label = (id.equals(settings.equippedId(slot)) ? "* " : "") + resolved.name();
            addRenderableWidget(Button.builder(Component.literal(trim(label, 28)), button -> {
                selectedPacked = id;
                if (Chick.ID.equals(id) && Chick.isPerch(slot)) {
                    GalleryWork.syncChick(slot);
                } else {
                    settings.equip(slot, id);
                    save();
                }
                rebuild();
            }).bounds(list.x(), y, list.w(), 20).build());
            y += 22;
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
            if (row > gallery.y() + gallery.h() - 64) {
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
        Slot perch = Slot.parse(settings.chickPerch).orElse(Slot.HAT);
        String perchLabel = switch (perch) {
            case SHOULDER_LEFT -> "Perch left";
            case SHOULDER_RIGHT -> "Perch right";
            default -> "Perch head";
        };
        addRenderableWidget(Button.builder(Component.literal(perchLabel), button -> {
            Slot next = switch (Slot.parse(settings.chickPerch).orElse(Slot.HAT)) {
                case SHOULDER_LEFT -> Slot.SHOULDER_RIGHT;
                case SHOULDER_RIGHT -> Slot.HAT;
                default -> Slot.SHOULDER_LEFT;
            };
            GalleryWork.syncChick(next);
            rebuild();
        }).bounds(settingsRect.x() + 114, sy + 22, 150, 18).build());
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
        String status = GalleryWork.status();
        graphics.drawString(this.font, trim(status, 72), region(WardrobeLayout.GALLERY).x(), region(WardrobeLayout.SETTINGS).y() - 12, WardrobeLayout.Palette.MUTED, false);
        if (this.minecraft != null && this.minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, preview.x() + 8, preview.y() + 18, preview.x() + preview.w() - 8, preview.y() + preview.h() - 8, 50, 0.0625F, mouseX, mouseY, this.minecraft.player);
        } else {
            graphics.drawString(this.font, "Join a world to preview.", preview.x() + 6, preview.y() + 28, WardrobeLayout.Palette.PAPER, false);
        }
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

    private static String trim(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
