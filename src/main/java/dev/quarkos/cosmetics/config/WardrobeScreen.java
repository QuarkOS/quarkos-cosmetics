package dev.quarkos.cosmetics.config;

import dev.quarkos.cosmetics.config.WardrobeLayout.Rect;
import dev.quarkos.cosmetics.config.ui.CardGrid;
import dev.quarkos.cosmetics.config.ui.ColourPicker;
import dev.quarkos.cosmetics.config.ui.FlatButton;
import dev.quarkos.cosmetics.config.ui.FlatEditBox;
import dev.quarkos.cosmetics.config.ui.GridMetrics;
import dev.quarkos.cosmetics.config.ui.PlayerPreview;
import dev.quarkos.cosmetics.config.ui.TextFit;
import dev.quarkos.cosmetics.config.ui.Thumbnail;
import dev.quarkos.cosmetics.config.ui.ToggleSwitch;
import dev.quarkos.cosmetics.config.ui.UiTheme;
import dev.quarkos.cosmetics.gallery.GalleryClient;
import dev.quarkos.cosmetics.gallery.GalleryWork;
import dev.quarkos.cosmetics.model.CosmeticModel;
import dev.quarkos.cosmetics.model.Slot;
import dev.quarkos.cosmetics.render.Chick;
import dev.quarkos.cosmetics.render.CosmeticDrawer;
import dev.quarkos.cosmetics.render.ModelLibrary;
//? if >=26.3 {
/*import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//? if <26.3 {
import org.lwjgl.glfw.GLFW;
//?}
//? if >=1.21.11 {
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

public final class WardrobeScreen extends Screen {
    //? if >=26.3 {
    /*private static final int ENTER = InputConstants.KEY_RETURN;
    private static final int KEYPAD_ENTER = InputConstants.KEY_NUMPADENTER;
    private static final int LEFT = InputConstants.KEY_LEFT;
    private static final int RIGHT = InputConstants.KEY_RIGHT;
    private static final int PAGE_UP = InputConstants.KEY_PAGEUP;
    private static final int PAGE_DOWN = InputConstants.KEY_PAGEDOWN;
    private static final int MOUSE_LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    *///?} else {
    private static final int ENTER = GLFW.GLFW_KEY_ENTER;
    private static final int KEYPAD_ENTER = GLFW.GLFW_KEY_KP_ENTER;
    private static final int LEFT = GLFW.GLFW_KEY_LEFT;
    private static final int RIGHT = GLFW.GLFW_KEY_RIGHT;
    private static final int PAGE_UP = GLFW.GLFW_KEY_PAGE_UP;
    private static final int PAGE_DOWN = GLFW.GLFW_KEY_PAGE_DOWN;
    private static final int MOUSE_LEFT = GLFW.GLFW_MOUSE_BUTTON_LEFT;
    //?}

    public enum View {
        WARDROBE("Wardrobe"), GALLERY("Gallery"), SETTINGS("Settings");

        private final String label;

        View(String label) {
            this.label = label;
        }
    }

    private enum Drag { NONE, PREVIEW, COLOUR, SCROLLBAR }

    private record Card(String id, String name, String author, Thumbnail thumbnail) {
    }

    private static final List<Slot> HEAD_SLOTS = Arrays.stream(Slot.values()).filter(Slot::wornOnHead).toList();
    private static final List<Slot> WARDROBE_SLOTS = List.of(Slot.HAT, Slot.EARS, Slot.SHOULDER_LEFT, Slot.SHOULDER_RIGHT);
    private static final int[] SKIN_DISTANCES = {8, 16, 32, 64};
    private final Screen parent;
    private final PlayerPreview preview;
    private final boolean studioGallery;
    private final boolean suppressHover;
    private String studioCardId;
    private final Map<Slot, Integer> wardrobeScroll = new EnumMap<>(Slot.class);
    private final Map<Slot, Integer> galleryScroll = new EnumMap<>(Slot.class);

    private View view;
    private Slot slot;
    private boolean colourOpen;
    private boolean revealEquipped;
    private boolean gallerySettingsOpen;
    private String query = "";
    private String urlDraft;
    private String gallerySelection = "";
    private String galleryNotice = "";

    private WardrobeLayout.Frame frame;
    private WardrobeLayout.Collection collection;
    private WardrobeLayout.Gallery gallery;
    private WardrobeLayout.Settings settingsLayout;
    private CardGrid grid;
    private View gridView;
    private Slot gridSlot;
    private ColourPicker picker;
    private FlatEditBox queryBox;
    private FlatEditBox urlBox;
    private Rect emptyWardrobe;
    private boolean emptyGallery;
    private boolean rebuildPending;
    private Drag drag = Drag.NONE;

    public WardrobeScreen(Screen parent) {
        this(parent, View.WARDROBE, null, false, false, false);
    }

    private WardrobeScreen(Screen parent, View view, Slot slot, boolean colourOpen, boolean studio, boolean legacy) {
        super(Component.translatable("screen.quarkos_cosmetics.title"));
        this.parent = parent;
        this.view = view;
        this.slot = slot;
        this.colourOpen = colourOpen;
        studioGallery = studio && view == View.GALLERY;
        suppressHover = studio && !legacy;
        preview = new PlayerPreview(studio, legacy);
    }

    /** UI captures suppress hover, use an offline Gallery sample, and can reveal a specific wardrobe card. */
    public static WardrobeScreen studioCapture(View view, Slot slot, boolean colourOpen, Float previewYaw,
                                                String cardToReveal) {
        WardrobeScreen screen = new WardrobeScreen(null, view, slot, colourOpen, true, false);
        screen.studioCardId = cardToReveal;
        if (previewYaw != null) {
            screen.preview.setYaw(previewYaw);
        }
        return screen;
    }

    public static WardrobeScreen legacyCapture() {
        return new WardrobeScreen(null, View.WARDROBE, Slot.HAT, false, true, true);
    }

    @Override
    protected void init() {
        rebuildPending = false;
        finishDrag();
        rememberScroll();
        grid = null;
        picker = null;
        queryBox = null;
        urlBox = null;
        emptyWardrobe = null;
        emptyGallery = false;

        ClientSettings settings = ClientSettings.get();
        if (slot == null) {
            slot = WARDROBE_SLOTS.stream().filter(value -> !settings.equippedId(value).isBlank()).findFirst().orElse(Slot.HAT);
        }
        if (urlDraft == null) {
            urlDraft = settings.galleryUrl;
        }
        frame = WardrobeLayout.frame(width, height);
        initHeader();
        Rect previewRect = frame.preview();
        FlatButton reset = button(new Rect(previewRect.right() - 55, previewRect.bottom() - 24, 45, 17),
                "Reset", preview::reset);
        reset.active = minecraft != null && minecraft.player != null;
        reset.setTooltip(Tooltip.create(Component.literal("Reset preview view")));

        switch (view) {
            case WARDROBE -> initWardrobe(settings);
            case GALLERY -> initGallery(settings);
            case SETTINGS -> initSettings(settings);
        }
    }

    public void rebuild() {
        rebuildPending = true;
    }

    private void refreshWidgets() {
        boolean wasSearching = queryBox != null && getFocused() == queryBox;
        boolean wasEditingUrl = urlBox != null && getFocused() == urlBox;
        Component focusedButton = getFocused() instanceof FlatButton button ? button.getMessage() : null;
        setFocused(null);
        clearWidgets();
        init();
        if (wasSearching && queryBox != null) {
            focusField(queryBox);
        } else if (wasEditingUrl && urlBox != null) {
            focusField(urlBox);
        } else if (focusedButton != null) {
            for (var child : children()) {
                if (child instanceof FlatButton button && button.active && button.visible && focusedButton.equals(button.getMessage())) {
                    setFocused(button);
                    break;
                }
            }
        }
    }

    private void focusField(EditBox field) {
        setFocused(field);
        field.setFocused(true);
    }

    private void initHeader() {
        Rect header = frame.header();
        Rect nav = navigation();
        View[] views = View.values();
        for (int i = 0; i < views.length; i++) {
            View tab = views[i];
            choice(nav.column(i, views.length, 4), tab.label, () -> view == tab, () -> open(tab));
        }
        addRenderableWidget(new FlatButton(new Rect(header.right() - 50, header.y() + 5, 48, 20),
                Component.translatable("text.quarkos_cosmetics.done"), pressed -> onClose()));
    }

    private Rect navigation() {
        Rect header = frame.header();
        int navWidth = Math.min(206, header.w() - 152);
        int centered = Math.max(header.x() + 142, header.x() + (header.w() - navWidth) / 2);
        int x = Math.min(centered, header.right() - navWidth - 58);
        return new Rect(x, header.y() + 5, navWidth, 20);
    }

    private void open(View next) {
        if (view != next) {
            if (next == View.GALLERY && slot.isShoulder()) {
                slot = Slot.HAT;
            }
            view = next;
            rebuild();
        }
    }

    private void initWardrobe(ClientSettings settings) {
        String equipped = settings.equippedId(slot);
        boolean hasEquipped = !equipped.isBlank();
        boolean canTint = hasEquipped && CosmeticDrawer.canTint(equipped);
        collection = WardrobeLayout.collection(frame, canTint && colourOpen);
        Rect toolbar = collection.toolbar();
        String visibleLabel = toolbar.w() >= 237 ? "Category visible" : "Visible";
        int toggleWidth = font.width(visibleLabel) + 42;
        int categoryWidth = Math.min(290, toolbar.w() - toggleWidth - 4);
        categories(new Rect(toolbar.x(), toolbar.y(), categoryWidth, toolbar.h()), true);
        addRenderableWidget(ToggleSwitch.create(trailing(toolbar, toggleWidth), visibleLabel,
                () -> settings.categoryOn.getOrDefault(slot, true), on -> {
                    settings.categoryOn.put(slot, on);
                    save();
                }));

        List<Card> cards = new ArrayList<>();
        cards.add(new Card("", "None", null, null));
        for (ModelLibrary.Resolved model : ModelLibrary.packed(slot)) {
            if (!slot.isShoulder() || Chick.ID.equals(model.id())) {
                cards.add(new Card(model.id(), model.name(), null, Thumbnail.of(model)));
            }
        }
        GridMetrics metrics = buildGrid(collection.grid(), cards, false, equipped);
        if (cards.size() == 1 && slot.wornOnHead()) {
            Rect none = metrics.card(0, 0);
            int left = none.right() + 12;
            emptyWardrobe = new Rect(left, none.y() + 8, collection.grid().right() - left, 17);
            if (emptyWardrobe.w() >= 89 && none.h() >= 48) {
                button(new Rect(left, emptyWardrobe.bottom() + 3, 89, 18), "Browse gallery", () -> open(View.GALLERY));
            }
        }

        if (!hasEquipped) {
            return;
        }
        Rect details = collection.equipped();
        int colourWidth = canTint ? 64 : 0;
        Rect itemToggle = new Rect(details.right() - colourWidth - 102, details.y() + 5, 99, 22);
        addRenderableWidget(ToggleSwitch.create(itemToggle, "Item shown",
                () -> !settings.disabled.contains(equipped), on -> {
                    if (on) {
                        settings.disabled.remove(equipped);
                    } else {
                        settings.disabled.add(equipped);
                    }
                    save();
                }));
        if (canTint) {
            FlatButton colour = new FlatButton(new Rect(details.right() - 61, details.y() + 6, 55, 20),
                    Component.literal("Colour"), pressed -> {
                        colourOpen = !colourOpen;
                        revealEquipped = colourOpen;
                        rebuild();
                    }, (button, graphics, font, bounds, hovered) -> {
                        UiTheme.frame(graphics, bounds, button.fill(hovered), colourOpen ? UiTheme.ACCENT : button.border());
                        int tint = settings.tint(slot);
                        UiTheme.tile(graphics, new Rect(bounds.x() + 5, bounds.y() + 6, 8, 8),
                                tint == TintColor.DEFAULT ? UiTheme.MUTED : 0xFF000000 | tint);
                        UiTheme.text(graphics, font, "Colour", new Rect(bounds.x() + 17, bounds.y(), bounds.w() - 21, bounds.h()), UiTheme.TEXT);
                    });
            addRenderableWidget(colour);
        }
        if (collection.colour().h() > 0) {
            picker = new ColourPicker(collection.colour(), () -> settings.tint(slot), tint -> settings.setTint(slot, tint),
                    this::save, this::addRenderableWidget);
        }
    }

    private void categories(Rect bar, boolean wardrobe) {
        List<Slot> slots = wardrobe ? WARDROBE_SLOTS : HEAD_SLOTS;
        for (int i = 0; i < slots.size(); i++) {
            Slot target = slots.get(i);
            String label = slotName(target);
            int count = wardrobe && !target.isShoulder() ? ModelLibrary.packed(target).size() : 0;
            FlatButton button = new FlatButton(bar.column(i, slots.size(), 4), Component.literal(label), pressed -> {
                if (slot != target) {
                    slot = target;
                    gallerySelection = "";
                    colourOpen = false;
                    rebuild();
                }
            }, (widget, graphics, font, bounds, hovered) -> {
                boolean selected = slot == target;
                UiTheme.frame(graphics, bounds, selected ? UiTheme.HOVER : widget.fill(hovered),
                        selected ? UiTheme.ACCENT : widget.border());
                int ink = selected ? UiTheme.TEXT : UiTheme.MUTED;
                if (target.isShoulder() || (wardrobe && bounds.w() < 54)) {
                    String caption = target.isShoulder() && font.width(label) > bounds.w() - 4
                            ? target == Slot.SHOULDER_LEFT ? "Left" : "Right" : label;
                    UiTheme.centered(graphics, font, caption, bounds.inset(2), ink);
                    return;
                }
                UiTheme.slotIcon(graphics, target, bounds.x() + 5, bounds.y() + (bounds.h() - 12) / 2, ink);
                String countLabel = Integer.toString(count);
                int numberWidth = font.width(countLabel);
                int reserve = wardrobe ? numberWidth + 7 : 5;
                UiTheme.text(graphics, font, label, new Rect(bounds.x() + 21, bounds.y(), bounds.w() - 21 - reserve, bounds.h()), ink);
                if (wardrobe) {
                    UiTheme.text(graphics, font, countLabel,
                            new Rect(bounds.right() - numberWidth - 5, bounds.y(), numberWidth, bounds.h()), UiTheme.MUTED);
                }
            });
            if (wardrobe) {
                button.setTooltip(Tooltip.create(Component.literal(label)));
            }
            addRenderableWidget(button);
        }
    }

    private void initGallery(ClientSettings settings) {
        gallery = WardrobeLayout.gallery(frame, gallerySettingsOpen);
        Rect account = gallery.account();
        button(trailing(account, 112), gallerySettingsOpen ? "Gallery settings  −" : "Gallery settings  +", () -> {
            gallerySettingsOpen = !gallerySettingsOpen;
            rebuild();
        });
        FlatButton signIn = addRenderableWidget(new FlatButton(
                new Rect(account.right() - 174, account.y(), 56, account.h()), Component.literal("Sign in"), pressed -> {
                    galleryNotice = "";
                    pressed.active = false;
                    GalleryWork.signIn();
                }));
        signIn.active = !studioGallery && !GalleryWork.signedIn() && GalleryWork.signInStatus() != GalleryWork.SignInStatus.SIGNING_IN;
        signIn.visible = studioGallery || !GalleryWork.signedIn();

        if (gallerySettingsOpen) {
            Rect connection = gallery.connection();
            urlBox = addRenderableWidget(new FlatEditBox(font,
                    new Rect(connection.x() + 66, connection.y(), connection.w() - 124, connection.h()), Component.literal("Gallery URL")));
            urlBox.setMaxLength(200);
            urlBox.setHint(Component.literal("Gallery URL"));
            urlBox.setValue(urlDraft);
            urlBox.setResponder(value -> urlDraft = value);
            button(trailing(connection, 52), "Apply", this::applyGalleryUrl);
        }

        Rect filters = gallery.filters();
        int categoryWidth = Math.min(166, filters.w() * 2 / 5);
        categories(leading(filters, categoryWidth), false);
        queryBox = addRenderableWidget(new FlatEditBox(font,
                new Rect(filters.x() + categoryWidth + 8, filters.y(), filters.w() - categoryWidth - 67, filters.h()),
                Component.literal("Search gallery")));
        queryBox.setMaxLength(48);
        queryBox.setHint(Component.literal(slot == Slot.HAT ? "Search hats" : "Search ears"));
        queryBox.setValue(query);
        queryBox.setResponder(value -> query = value);
        button(trailing(filters, 53), "Search", this::searchGallery);

        List<GalleryClient.Listed> entries = galleryEntries().stream().filter(item -> matchesSlot(item, slot)).toList();
        List<Card> cards = entries.stream()
                .map(item -> new Card(item.id(), item.name(), item.author(), Thumbnail.of(ModelLibrary.resolve(item.id()))))
                .toList();
        emptyGallery = cards.isEmpty();
        buildGrid(gallery.grid(), cards, true, settings.equippedId(slot));

        Rect actions = gallery.actions();
        String publishId = publishable(settings.equippedId(slot));
        FlatButton publish = button(leading(actions, 61), "Publish", () -> {
            galleryNotice = "";
            GalleryWork.publishPacked(publishId);
        });
        publish.active = !studioGallery && GalleryWork.signedIn() && !publishId.isBlank();
        if (!publish.active) {
            publish.setTooltip(Tooltip.create(Component.literal(GalleryWork.signedIn()
                    ? "Equip a publishable packed cosmetic" : "Sign in to publish")));
        }
        String reportId = gallerySelection.isBlank() ? settings.equippedId(slot) : gallerySelection;
        FlatButton report = button(new Rect(actions.x() + 67, actions.y(), 54, actions.h()), "Report", () -> {
            galleryNotice = "";
            GalleryWork.report(reportId);
        });
        report.active = !studioGallery && GalleryWork.signedIn() && entries.stream().anyMatch(item -> item.id().equals(reportId));
        if (!report.active) {
            report.setTooltip(Tooltip.create(Component.literal(GalleryWork.signedIn()
                    ? "Select a gallery cosmetic" : "Sign in to report")));
        }
    }

    private static String publishable(String id) {
        ModelLibrary.Resolved model = ModelLibrary.packedById(id);
        if (model == null || model.model().elements.isEmpty() || model.png() == null
                || model.png().length > 65536 || model.width() > CosmeticModel.MAX_EDGE || model.height() > CosmeticModel.MAX_EDGE) {
            return "";
        }
        return id;
    }

    private List<GalleryClient.Listed> galleryEntries() {
        if (!studioGallery) {
            return GalleryWork.browse();
        }
        return ModelLibrary.packed().stream().filter(item -> item.slot().wornOnHead())
                .map(item -> new GalleryClient.Listed(item.id(), item.name(), item.description(), item.slot().wire, "Quarkos Studio"))
                .toList();
    }

    private static boolean matchesSlot(GalleryClient.Listed listed, Slot slot) {
        return Slot.parse(listed.slot()).orElse(slot) == slot;
    }

    private void applyGalleryUrl() {
        ClientSettings settings = ClientSettings.get();
        String normalized = GalleryClient.normalizeBase(urlDraft);
        String next = normalized.isBlank() ? ClientSettings.DEFAULT_GALLERY : normalized;
        if (!next.equals(GalleryClient.normalizeBase(settings.galleryUrl))) {
            GalleryWork.dropSession();
        }
        settings.galleryUrl = next;
        urlDraft = next;
        if (urlBox != null) {
            urlBox.setValue(next);
        }
        galleryNotice = "Gallery URL saved.";
        save();
        rebuild();
    }

    private void searchGallery() {
        if (studioGallery) {
            return;
        }
        galleryNotice = "";
        gallerySelection = "";
        galleryScroll.remove(slot);
        grid = null;
        GalleryWork.browse(slot, query);
        rebuild();
    }

    private GridMetrics buildGrid(Rect bounds, List<Card> entries, boolean isGallery, String equipped) {
        boolean compact = width < 640;
        int columns = isGallery ? 4 : 3;
        int minCardWidth = compact ? 74 : 94;
        int preferredHeight = compact ? 58 : isGallery ? 126 : 146;
        GridMetrics metrics = GridMetrics.fit(bounds, entries.size(), minCardWidth, preferredHeight, columns);
        // Fit two rows in the compact wardrobe when the viewport is short by at most one gap.
        if (!isGallery && compact && metrics.rows() > 1 && metrics.pageRows() == 1 && bounds.h() >= 2 * preferredHeight) {
            int cardHeight = (bounds.h() - GridMetrics.GAP) / 2;
            metrics = GridMetrics.fit(bounds, entries.size(), minCardWidth, cardHeight, columns);
        }
        List<FlatButton> buttons = new ArrayList<>();
        int revealIndex = -1;
        for (int i = 0; i < entries.size(); i++) {
            Card card = entries.get(i);
            boolean worn = card.id().equals(equipped) || !isGallery && Chick.ID.equals(card.id())
                    && equipped.equals(ClientSettings.get().chickGallery.get(slot));
            if (studioCardId == null ? worn : card.id().equals(studioCardId)) {
                revealIndex = i;
            }
            FlatButton button = new FlatButton(metrics.card(i, 0), Component.literal(worn ? card.name() + ", selected" : card.name()),
                    pressed -> chooseCard(card, isGallery),
                    (widget, graphics, font, rect, hovered) -> drawCard(widget, graphics, rect, hovered, card, isGallery, worn));
            String tooltip = TextFit.ellipsis(card.name(), Math.min(156, width - 20), font::width);
            button.setTooltip(Tooltip.create(Component.literal(tooltip)));
            buttons.add(addRenderableWidget(button));
        }
        gridSlot = slot;
        gridView = view;
        Map<Slot, Integer> positions = scrollPositions(view);
        int row = positions.getOrDefault(slot, 0);
        if (!isGallery && (studioCardId != null || revealEquipped || !positions.containsKey(slot))) {
            row = metrics.reveal(revealIndex, row);
            revealEquipped = false;
        }
        grid = new CardGrid(metrics, buttons, row);
        return metrics;
    }

    private void chooseCard(Card card, boolean isGallery) {
        if (isGallery) {
            if (!studioGallery) {
                gallerySelection = card.id();
                galleryNotice = "";
                GalleryWork.equip(slot, card.id());
                rebuild();
            }
        } else if (card.id().isBlank()) {
            GalleryWork.clear(slot);
            rebuild();
        } else if (Chick.ID.equals(card.id()) && Chick.isPerch(slot)) {
            GalleryWork.syncChick(slot);
            rebuild();
        } else {
            ClientSettings.get().equip(slot, card.id());
            save();
            rebuild();
        }
    }

    private void initSettings(ClientSettings settings) {
        settingsLayout = WardrobeLayout.settings(frame);
        addRenderableWidget(ToggleSwitch.create(toggleLine(settingsLayout.hideOthers()), "Hide others",
                () -> settings.hideOthers, on -> {
                    settings.hideOthers = on;
                    save();
                }));
        addRenderableWidget(ToggleSwitch.create(toggleLine(settingsLayout.skinLayer()), "3D skin layer",
                () -> settings.skinLayers, on -> {
                    settings.skinLayers = on;
                    save();
                }));
        Rect distance = distanceOptions();
        for (int i = 0; i < SKIN_DISTANCES.length; i++) {
            int metres = SKIN_DISTANCES[i];
            choice(distance.column(i, SKIN_DISTANCES.length, 2), metres + " m",
                    () -> settings.skinDistance == metres, () -> {
                        settings.skinDistance = metres;
                        save();
                    });
        }
    }

    private Rect distanceOptions() {
        Rect row = settingsLayout.skinDistance();
        int optionWidth = Math.min(198, row.w() / 2 + 10);
        return new Rect(row.right() - optionWidth - 7, row.y() + 5, optionWidth, 24);
    }

    private static Rect toggleLine(Rect row) {
        return new Rect(row.x() + 3, row.y() + 2, row.w() - 6, 21);
    }

    private void choice(Rect rect, String label, BooleanSupplier selected, Runnable pick) {
        addRenderableWidget(new FlatButton(rect, Component.literal(label), button -> pick.run(),
                (button, graphics, font, bounds, hovered) -> {
                    boolean on = selected.getAsBoolean();
                    UiTheme.frame(graphics, bounds, on ? UiTheme.HOVER : button.fill(hovered), button.border());
                    if (on) {
                        UiTheme.fill(graphics, new Rect(bounds.x() + 2, bounds.bottom() - 2, bounds.w() - 4, 1), UiTheme.ACCENT);
                    }
                    UiTheme.centered(graphics, font, label, bounds.inset(3), on ? UiTheme.TEXT : UiTheme.MUTED);
                }));
    }

    private FlatButton button(Rect bounds, String label, Runnable action) {
        return addRenderableWidget(new FlatButton(bounds, Component.literal(label), pressed -> action.run()));
    }

    @Override
    //? if >=26.3 {
    /*public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    *///?} else {
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    //?}
    }

    @Override
    //? if >=26.3 {
    /*public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    *///?} else {
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    //?}
        if (rebuildPending) {
            refreshWidgets();
        }
        UiTheme.fill(graphics, new Rect(0, 0, width, height), UiTheme.BACKGROUND);
        Rect head = frame.header();
        UiTheme.slotIcon(graphics, Slot.HAT, head.x() + 2, head.y() + 9, UiTheme.ACCENT);
        UiTheme.text(graphics, font, title.getString(),
                new Rect(head.x() + 22, head.y(), navigation().x() - head.x() - 29, head.h()), UiTheme.TEXT);
        UiTheme.fill(graphics, new Rect(head.x(), head.bottom() - 1, head.w(), 1), UiTheme.BORDER);
        UiTheme.frame(graphics, frame.content(), UiTheme.PANEL, UiTheme.BORDER);
        drawPreview(graphics);

        switch (view) {
            case WARDROBE -> drawWardrobe(graphics);
            case GALLERY -> drawGallery(graphics);
            case SETTINGS -> drawSettings(graphics);
        }
        if (grid != null) {
            grid.drawScrollbar(graphics);
        }
        //? if >=26.3 {
        /*super.extractRenderState(graphics, suppressHover ? -1 : mouseX, suppressHover ? -1 : mouseY, partialTick);
        *///?} else {
        super.render(graphics, suppressHover ? -1 : mouseX, suppressHover ? -1 : mouseY, partialTick);
        //?}
    }

    //? if >=26.3 {
    /*private void drawPreview(GuiGraphicsExtractor graphics) {
    *///?} else {
    private void drawPreview(GuiGraphics graphics) {
    //?}
        Rect panel = frame.preview();
        Rect stage = frame.stage();
        UiTheme.frame(graphics, panel, UiTheme.PANEL, UiTheme.BORDER);
        UiTheme.text(graphics, font, "Player preview", new Rect(stage.x(), panel.y() + 4, stage.w(), 20), UiTheme.TEXT);
        UiTheme.frame(graphics, stage, UiTheme.STAGE, UiTheme.BORDER);
        if (minecraft != null && minecraft.player != null) {
            preview.draw(graphics, stage.inset(1), minecraft.player);
            UiTheme.text(graphics, font, "Drag to turn", new Rect(stage.x(), panel.bottom() - 24, panel.w() - 67, 17), UiTheme.MUTED);
        } else {
            UiTheme.centered(graphics, font, "Join a world", new Rect(stage.x(), stage.y() + stage.h() / 2 - 12, stage.w(), 12), UiTheme.TEXT);
            UiTheme.centered(graphics, font, "to preview your skin", new Rect(stage.x(), stage.y() + stage.h() / 2 + 1, stage.w(), 12), UiTheme.MUTED);
        }
    }

    //? if >=26.3 {
    /*private void drawWardrobe(GuiGraphicsExtractor graphics) {
    *///?} else {
    private void drawWardrobe(GuiGraphics graphics) {
    //?}
        Rect details = collection.equipped();
        String equipped = ClientSettings.get().equippedId(slot);
        ModelLibrary.Resolved model = ModelLibrary.resolve(equipped);
        String name = model != null ? model.name() : equipped.isBlank() ? "None" : "Unavailable cosmetic";
        int labelWidth = details.w() - (CosmeticDrawer.canTint(equipped) ? 176 : 112);
        UiTheme.frame(graphics, details, UiTheme.STAGE, UiTheme.BORDER);
        UiTheme.text(graphics, font, "Equipped", new Rect(details.x() + 7, details.y() + 3, labelWidth, 12), UiTheme.MUTED);
        UiTheme.text(graphics, font, name, new Rect(details.x() + 7, details.y() + 16, labelWidth, 12), UiTheme.TEXT);
        if (equipped.isBlank()) {
            String hint = "Pick a card to equip";
            UiTheme.text(graphics, font, hint, trailing(details.inset(7), font.width(hint)), UiTheme.MUTED);
        }
        if (picker != null) {
            UiTheme.frame(graphics, collection.colour(), UiTheme.STAGE, UiTheme.BORDER);
        }
        if (emptyWardrobe != null) {
            UiTheme.text(graphics, font, "No packed " + (slot == Slot.HAT ? "hats" : "ears") + " in this build.", emptyWardrobe, UiTheme.MUTED);
        }
    }

    //? if >=26.3 {
    /*private void drawGallery(GuiGraphicsExtractor graphics) {
    *///?} else {
    private void drawGallery(GuiGraphics graphics) {
    //?}
        GalleryWork.SignInStatus signIn = studioGallery ? GalleryWork.SignInStatus.SIGNED_OUT : GalleryWork.signInStatus();
        String accountText = switch (signIn) {
            case SIGNED_OUT -> "Signed out";
            case SIGNING_IN -> "Signing in…";
            case SIGNED_IN -> "Signed in as " + ClientSettings.get().playerName;
            case ERROR -> "Sign-in error";
        };
        int reserve = signIn == GalleryWork.SignInStatus.SIGNED_IN ? 120 : 182;
        Rect chip = leading(gallery.account(), gallery.account().w() - reserve);
        UiTheme.frame(graphics, chip, UiTheme.STAGE, UiTheme.BORDER);
        int dot = signIn == GalleryWork.SignInStatus.SIGNED_IN || signIn == GalleryWork.SignInStatus.SIGNING_IN
                ? UiTheme.ACCENT : UiTheme.MUTED;
        if (chip.w() >= 20) {
            UiTheme.tile(graphics, new Rect(chip.x() + 7, chip.y() + 8, 6, 6), dot);
        }
        UiTheme.text(graphics, font, accountText, new Rect(chip.x() + 19, chip.y(), chip.w() - 25, chip.h()), UiTheme.TEXT);
        if (gallerySettingsOpen) {
            UiTheme.text(graphics, font, "Gallery URL", leading(gallery.connection(), 62), UiTheme.MUTED);
        }

        Rect gridRect = gallery.grid();
        if (emptyGallery) {
            GalleryWork.BrowseStatus browseStatus = GalleryWork.browseStatus(slot);
            String empty = switch (browseStatus) {
                case IDLE -> "Search to browse gallery cosmetics.";
                case LOADING -> "Loading gallery…";
                case READY -> "No matching cosmetics.";
                case ERROR -> "Could not load the gallery.";
            };
            UiTheme.frame(graphics, gridRect, UiTheme.STAGE, UiTheme.BORDER);
            UiTheme.centered(graphics, font, empty, gridRect.inset(8), UiTheme.MUTED);
        }
        Rect actions = gallery.actions();
        String status = studioGallery ? "Studio catalog sample" : galleryNotice.isBlank() ? GalleryWork.status() : galleryNotice;
        if (!status.equals("Gallery idle.") && !status.equals(accountText) && !status.equals(accountText + ".")) {
            UiTheme.text(graphics, font, status, new Rect(actions.x() + 131, actions.y(), actions.w() - 131, actions.h()), UiTheme.MUTED);
        }
    }

    //? if >=26.3 {
    /*private void drawSettings(GuiGraphicsExtractor graphics) {
    *///?} else {
    private void drawSettings(GuiGraphics graphics) {
    //?}
        Rect panel = frame.content();
        UiTheme.text(graphics, font, "Display settings", new Rect(panel.x() + 10, panel.y() + 6, panel.w() - 20, 17), UiTheme.TEXT);
        drawSettingRow(graphics, settingsLayout.hideOthers(), "Hide cosmetics and 3D layers on other players.");
        drawSettingRow(graphics, settingsLayout.skinLayer(), "Give the outer layer of player skins depth.");
        Rect distance = settingsLayout.skinDistance();
        UiTheme.frame(graphics, distance, UiTheme.STAGE, UiTheme.BORDER);
        int metres = ClientSettings.get().skinDistance;
        String label = Arrays.stream(SKIN_DISTANCES).anyMatch(preset -> preset == metres) ? "Skin distance" : "Skin distance · " + metres + " m";
        UiTheme.text(graphics, font, label,
                new Rect(distance.x() + 8, distance.y(), distanceOptions().x() - distance.x() - 12, distance.h()), UiTheme.TEXT);
    }

    //? if >=26.3 {
    /*private void drawSettingRow(GuiGraphicsExtractor graphics, Rect row, String description) {
    *///?} else {
    private void drawSettingRow(GuiGraphics graphics, Rect row, String description) {
    //?}
        UiTheme.frame(graphics, row, UiTheme.STAGE, UiTheme.BORDER);
        UiTheme.text(graphics, font, description,
                new Rect(row.x() + 8, row.y() + 21, row.w() - 16, 13), UiTheme.MUTED);
    }

    //? if >=26.3 {
    /*private void drawCard(FlatButton button, GuiGraphicsExtractor graphics, Rect rect, boolean hovered, Card card, boolean galleryCard, boolean worn) {
    *///?} else {
    private void drawCard(FlatButton button, GuiGraphics graphics, Rect rect, boolean hovered, Card card, boolean galleryCard, boolean worn) {
    //?}
        boolean picked = galleryCard && card.id().equals(gallerySelection);
        int outline = worn || picked ? UiTheme.ACCENT : button.border();
        UiTheme.frame(graphics, rect, hovered ? UiTheme.HOVER : UiTheme.CONTROL, outline);
        int labelHeight = galleryCard ? 26 : 16;
        Rect art = new Rect(rect.x() + 2, rect.y() + 2, rect.w() - 4, rect.h() - labelHeight - 2);
        UiTheme.fill(graphics, art, UiTheme.STAGE);
        if (card.thumbnail() == null || !card.thumbnail().draw(graphics, art.inset(3))) {
            if (card.id().isBlank()) {
                UiTheme.emptyIcon(graphics, art.x() + (art.w() - 22) / 2, art.y() + (art.h() - 22) / 2);
            } else {
                if (galleryCard && !studioGallery && card.thumbnail() == null) {
                    GalleryWork.loadPreview(card.id());
                }
                UiTheme.slotIcon(graphics, slot, art.x() + (art.w() - 12) / 2, art.y() + (art.h() - 12) / 2, UiTheme.MUTED);
            }
        }
        if (worn) {
            UiTheme.check(graphics, rect.right() - 16, rect.y() + 4);
        }
        UiTheme.text(graphics, font, card.name(),
                new Rect(rect.x() + 6, rect.bottom() - labelHeight, rect.w() - 12, 15), UiTheme.TEXT);
        if (galleryCard) {
            UiTheme.text(graphics, font, card.author(),
                    new Rect(rect.x() + 6, rect.bottom() - 14, rect.w() - 12, 12), UiTheme.MUTED);
        }
    }

    private boolean beginDrag(double x, double y) {
        if (picker != null && picker.contains(x, y)) {
            setFocused(picker.slider());
            picker.drag(x);
            drag = Drag.COLOUR;
        } else if (grid != null && grid.grab(x, y)) {
            clearHiddenFocus();
            drag = Drag.SCROLLBAR;
        } else if (frame.stage().contains(x, y) && minecraft != null && minecraft.player != null) {
            setFocused(null);
            preview.beginDrag();
            drag = Drag.PREVIEW;
        }
        return drag != Drag.NONE;
    }

    private boolean moveDrag(double x, double y, double dx, double dy) {
        switch (drag) {
            case NONE -> { return false; }
            case PREVIEW -> preview.drag(dx, dy);
            case COLOUR -> picker.drag(x);
            case SCROLLBAR -> {
                grid.drag(y);
                clearHiddenFocus();
            }
        }
        return true;
    }

    private boolean finishDrag() {
        Drag previous = drag;
        drag = Drag.NONE;
        if (previous == Drag.PREVIEW) {
            preview.endDrag();
        } else if (previous == Drag.COLOUR) {
            save();
        }
        return previous != Drag.NONE;
    }

    private boolean shortcut(int key) {
        if (key == ENTER || key == KEYPAD_ENTER) {
            if (queryBox != null && getFocused() == queryBox) {
                searchGallery();
                return true;
            }
            if (urlBox != null && getFocused() == urlBox) {
                applyGalleryUrl();
                return true;
            }
        }
        if (picker != null && getFocused() == picker.slider() && (key == LEFT || key == RIGHT)) {
            picker.adjust(key == LEFT ? -1 : 1);
            return true;
        }
        if (grid != null && !(getFocused() instanceof EditBox)
                && (key == PAGE_UP || key == PAGE_DOWN)
                && grid.page(key == PAGE_UP ? -1 : 1)) {
            clearHiddenFocus();
            return true;
        }
        return false;
    }

    //? if >=1.21.11 {
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        return event.button() == MOUSE_LEFT && beginDrag(event.x(), event.y()) || super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return event.button() == MOUSE_LEFT && moveDrag(event.x(), event.y(), dragX, dragY)
                || super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return event.button() == MOUSE_LEFT && finishDrag() || super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return shortcut(event.key()) || super.keyPressed(event);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double x, double y, int button) {
        return button == MOUSE_LEFT && beginDrag(x, y) || super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        return button == MOUSE_LEFT && moveDrag(x, y, dragX, dragY) || super.mouseDragged(x, y, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return button == MOUSE_LEFT && finishDrag() || super.mouseReleased(x, y, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return shortcut(key) || super.keyPressed(key, scanCode, modifiers);
    }
    *///?}

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (grid != null && grid.wheel(x, y, vertical)) {
            clearHiddenFocus();
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    private void clearHiddenFocus() {
        if (getFocused() instanceof FlatButton button && !button.visible) {
            setFocused(null);
        }
    }

    private void rememberScroll() {
        if (grid != null) {
            scrollPositions(gridView).put(gridSlot, grid.row());
        }
    }

    private Map<Slot, Integer> scrollPositions(View page) {
        return page == View.GALLERY ? galleryScroll : wardrobeScroll;
    }

    private static String slotName(Slot slot) {
        return Component.translatable("slot.quarkos_cosmetics." + slot.wire).getString();
    }

    private static Rect leading(Rect row, int width) {
        return new Rect(row.x(), row.y(), width, row.h());
    }

    private static Rect trailing(Rect row, int width) {
        return new Rect(row.right() - width, row.y(), width, row.h());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        save();
        if (minecraft != null) {
            //? if >=26.3 {
            /*minecraft.gui.setScreen(parent);
            *///?} else {
            minecraft.setScreen(parent);
            //?}
        }
    }

    private void save() {
        if (minecraft != null) {
            ClientSettings.get().save(minecraft.gameDirectory.toPath());
        }
    }
}
