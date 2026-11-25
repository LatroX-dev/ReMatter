package dev.latrox.rematter.client;

import static dev.latrox.rematter.client.UiPalette.*;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.ReservedSubstances;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.client.ui.DarkButton;
import dev.latrox.rematter.client.ui.ScrollBar;
import dev.latrox.rematter.client.ui.ScrollModel;
import dev.latrox.rematter.content.CellPartitionData;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachineSnapshot;
import dev.latrox.rematter.content.MatterCableRelayBlockEntity;
import dev.latrox.rematter.content.MatterCableRelayBlockEntity.ChannelMode;
import dev.latrox.rematter.content.MatterCableRelayBlockEntity.DirectionMode;
import dev.latrox.rematter.content.MatterCableRelayBlockEntity.GateMode;
import dev.latrox.rematter.content.MatterCableRelayMenu;
import dev.latrox.rematter.network.ClientChemistry;
import dev.latrox.rematter.network.MatterCableRelayPayload;
import dev.latrox.rematter.network.StockFavoriteTogglePayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeSet;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** Three-tab configuration screen for Matter Cable Relay: Routing (Direction/Gate/Channel/Limits), Matter Filter, and Device Settings. */
public final class MatterCableRelayScreen extends AbstractContainerScreen<MatterCableRelayMenu>
        implements SubstanceHoverProvider {
    private static final int W = 236, H = 194;
    private static final int TAB_ROUTING = 0, TAB_FILTER = 1, TAB_DEVICE = 2;
    private static final int COLUMNS = 11, ROWS = 4, GRID_X = 14, GRID_Y = 111, PITCH = 18;
    private static final int SLOT_X = 8, SLOT_Y = 66;
    private static final int ACCENT = machineAccent(MachineKind.NETWORK_INTERFACE);
    private static final int[] ENERGY_PRESETS = {0, 1_000, 10_000, 65_536, 262_144, 1_048_576};
    private static final int[] MATTER_PRESETS = {0, 64, 128, 1_024, 4_096, 8_192};

    private final ScrollModel scroll = new ScrollModel(ROWS);
    private final ScrollBar bar = new ScrollBar(scroll);
    private final List<SubstanceTile> tiles = new ArrayList<>();
    private final List<FilterSlotButton> filterSlots = new ArrayList<>();

    private DarkButton routingTab, filterTab, deviceTab;
    private DarkButton directionBtn, reverseBtn, gateBtn, channelBtn, energyPresetBtn, matterPresetBtn, applyBtn;
    private DarkButton solidBtn, liquidBtn, gasBtn, primalBtn, filterModeBtn, clearFilterBtn;
    private DeviceGeneralPanel devicePanel;
    private EditBox energyBox, matterBox, searchBox;

    private int activeTab = TAB_ROUTING;
    private GateMode draftGate;
    private ChannelMode draftChannel;
    private DirectionMode draftDirection;
    private int draftStateMask;
    private boolean draftWhitelist;
    private List<SubstanceKey> draftFilter = List.of();
    private GateMode lastServerGate;
    private ChannelMode lastServerChannel;
    private DirectionMode lastServerDirection;
    private int lastServerEnergy = -1, lastServerMatter = -1, lastServerStateMask = -1;
    private boolean lastServerWhitelist;
    private List<SubstanceKey> lastServerFilter = List.of();
    private boolean dirty, updating;
    private ChemistryRegistry catalogue;
    private List<SubstanceKey> pickerKeys = List.of();
    private int favoritesRevision = -1;

    public MatterCableRelayScreen(MatterCableRelayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.rematter.cable_relay." + key, args);
    }

    private static Component dirLabel(Direction d) {
        return Component.translatable("direction.rematter." + d.getName());
    }

    public List<Rect2i> viewerExclusionAreas() {
        var areas = new ArrayList<Rect2i>(3);
        for (var btn : List.of(routingTab, filterTab, deviceTab)) {
            if (btn != null && btn.visible) {
                areas.add(new Rect2i(btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight()));
            }
        }
        return areas;
    }

    @Override protected boolean hasClickedOutside(double mx, double my, int x, int y) {
        for (var r : viewerExclusionAreas()) {
            if (mx >= r.getX() && mx < r.getX() + r.getWidth() && my >= r.getY() && my < r.getY() + r.getHeight()) return false;
        }
        return super.hasClickedOutside(mx, my, x, y);
    }

    @Override protected void init() {
        super.init();
        leftPos = Math.max(23, leftPos);
        syncDraftFromMenu();

        routingTab = addRenderableWidget(DarkButton.icon(UiIcon.EXCHANGE,
                title, tr("tab_routing_hint"),
                leftPos - 22, topPos + 18, 24, 22, b -> { activeTab = TAB_ROUTING; refreshWidgets(); }));
        filterTab = addRenderableWidget(DarkButton.icon(UiIcon.MATTER,
                tr("tab_filter"), tr("tab_filter_hint"),
                leftPos - 22, topPos + 42, 24, 22, b -> { activeTab = TAB_FILTER; rebuildCatalogue(); }));
        deviceTab = DeviceGeneralPanel.createSettingsTab(this::addRenderableWidget, leftPos, topPos, 2, ACCENT,
                () -> { activeTab = TAB_DEVICE; refreshWidgets(); });

        devicePanel = new DeviceGeneralPanel(font, this::addRenderableWidget, leftPos + 8, topPos + 20, W - 16, ACCENT, title,
                menu::securityMode, menu::canManageSecurity, () -> true,
                forward -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                                forward ? MatterCableRelayMenu.CYCLE_SECURITY : MatterCableRelayMenu.CYCLE_SECURITY_BACK);
                    }
                },
                menu::customName,
                this::sendConfig,
                null, null);

        // --- Tab 0: Routing ---
        directionBtn = addRenderableWidget(DarkButton.labelIcon(UiIcon.EXCHANGE,
                tr("direction", tr("direction." + draftDirection.key())), tr("direction_hint"),
                leftPos + 8, topPos + 41, 152, 18, b -> {
                    draftDirection = draftDirection.next();
                    sendConfig(menu.customName());
                }));
        directionBtn.setAccent(ACCENT);

        reverseBtn = addRenderableWidget(DarkButton.labelIcon(UiIcon.LOOP,
                tr("reverse"), tr("reverse_hint"),
                leftPos + 164, topPos + 41, 64, 18, b -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MatterCableRelayMenu.REVERSE_DIRECTION);
                    }
                }));
        reverseBtn.setAccent(ACCENT);

        gateBtn = addRenderableWidget(DarkButton.labelIcon(UiIcon.REDSTONE,
                tr("gate", tr("gate." + draftGate.key())), tr("gate_hint"),
                leftPos + 8, topPos + 63, W - 16, 18, b -> {
                    draftGate = draftGate.next();
                    sendConfig(menu.customName());
                }));
        gateBtn.setAccent(ACCENT);

        channelBtn = addRenderableWidget(DarkButton.labelIcon(UiIcon.EXCHANGE,
                tr("channel", tr("channel." + draftChannel.key())), tr("channel_hint"),
                leftPos + 8, topPos + 85, W - 16, 18, b -> {
                    draftChannel = draftChannel.next();
                    sendConfig(menu.customName());
                }));
        channelBtn.setAccent(ACCENT);

        energyBox = addRenderableWidget(new EditBox(font, leftPos + 8, topPos + 117, 142, 16, tr("energy_limit")));
        energyBox.setMaxLength(9);
        energyBox.setFilter(s -> s.matches("[0-9]*"));
        energyBox.setTextColor(TEXT);
        energyBox.setValue(Integer.toString(menu.energyLimit()));
        energyBox.setResponder(v -> { if (!updating) dirty = true; });

        energyPresetBtn = addRenderableWidget(DarkButton.labelIcon(UiIcon.ENERGY, tr("preset"), tr("energy_preset_hint"),
                leftPos + 154, topPos + 116, 74, 18, b -> cyclePreset(energyBox, ENERGY_PRESETS)));
        energyPresetBtn.setAccent(ACCENT);

        matterBox = addRenderableWidget(new EditBox(font, leftPos + 8, topPos + 148, 142, 16, tr("matter_limit")));
        matterBox.setMaxLength(7);
        matterBox.setFilter(s -> s.matches("[0-9]*"));
        matterBox.setTextColor(TEXT);
        matterBox.setValue(Integer.toString(menu.matterLimit()));
        matterBox.setResponder(v -> { if (!updating) dirty = true; });

        matterPresetBtn = addRenderableWidget(DarkButton.labelIcon(UiIcon.MATTER, tr("preset"), tr("matter_preset_hint"),
                leftPos + 154, topPos + 147, 74, 18, b -> cyclePreset(matterBox, MATTER_PRESETS)));
        matterPresetBtn.setAccent(ACCENT);

        applyBtn = addRenderableWidget(DarkButton.labelIcon(UiIcon.CONFIRM, tr("apply"), tr("apply_hint"),
                leftPos + 154, topPos + 170, 74, 18, b -> sendConfig(menu.customName())));
        applyBtn.setAccent(ACCENT);

        // --- Tab 1: Matter Filter ---
        solidBtn = addStateToggle(leftPos + 8, UiIcon.SOLID, MatterCableRelayBlockEntity.STATE_SOLID, "state_solid");
        liquidBtn = addStateToggle(leftPos + 64, UiIcon.LIQUID, MatterCableRelayBlockEntity.STATE_LIQUID, "state_liquid");
        gasBtn = addStateToggle(leftPos + 120, UiIcon.GAS, MatterCableRelayBlockEntity.STATE_GAS, "state_gas");
        primalBtn = addStateToggle(leftPos + 176, UiIcon.PRIMAL, MatterCableRelayBlockEntity.STATE_PRIMAL, "state_primal");

        filterModeBtn = addRenderableWidget(DarkButton.labelIcon(UiIcon.MATTER,
                tr(draftWhitelist ? "filter_whitelist" : "filter_blacklist"), tr("filter_mode_hint"),
                leftPos + 8, topPos + 44, 108, 18, b -> {
                    draftWhitelist = !draftWhitelist;
                    sendConfig(menu.customName());
                }));
        filterModeBtn.setAccent(ACCENT);

        clearFilterBtn = addRenderableWidget(DarkButton.icon(UiIcon.CLEAR,
                tr("filter_clear"), tr("filter_clear_hint"),
                leftPos + 210, topPos + 44, 18, 18, b -> {
                    draftFilter = List.of();
                    sendConfig(menu.customName());
                }));
        clearFilterBtn.setAccent(ACCENT);

        filterSlots.clear();
        for (int i = 0; i < MatterCableRelayBlockEntity.MAX_FILTER_ENTRIES; i++) {
            int idx = i;
            filterSlots.add(addRenderableWidget(new FilterSlotButton(Button.builder(Component.empty(), b -> removeFilterIndex(idx))
                    .bounds(leftPos + SLOT_X + i * PITCH, topPos + SLOT_Y, PITCH, PITCH))));
        }

        String query = searchBox == null ? "" : searchBox.getValue();
        searchBox = addRenderableWidget(new EditBox(font, leftPos + 26, topPos + 93, 198, 12, tr("filter_search")));
        searchBox.setMaxLength(128);
        searchBox.setBordered(false);
        searchBox.setTextColor(TEXT);
        searchBox.setHint(tr("filter_search").copy().withStyle(s -> s.withColor(MUTED & 0xffffff)));
        searchBox.setValue(query);
        searchBox.setResponder(v -> {
            scroll.reset();
            rebuildCatalogue();
        });

        tiles.clear();
        for (int i = 0; i < COLUMNS * ROWS; i++) {
            int idx = i;
            tiles.add(addRenderableWidget(new SubstanceTile(Button.builder(Component.empty(), b -> toggleFilterTile(idx))
                    .bounds(leftPos + GRID_X + (i % COLUMNS) * PITCH, topPos + GRID_Y + (i / COLUMNS) * PITCH, PITCH, PITCH))));
        }
        bar.setBounds(leftPos + GRID_X + COLUMNS * PITCH + 5, topPos + GRID_Y, 5, ROWS * PITCH);

        rebuildCatalogue();
    }

    private DarkButton addStateToggle(int x, UiIcon icon, int bit, String key) {
        var btn = addRenderableWidget(DarkButton.labelIcon(icon, tr(key), tr("state_toggle_hint", tr(key)),
                x, topPos + 22, 52, 18, b -> {
                    draftStateMask ^= bit;
                    sendConfig(menu.customName());
                }));
        btn.setAccent(bit == MatterCableRelayBlockEntity.STATE_PRIMAL ? PRIMAL : ACCENT);
        return btn;
    }

    private void syncDraftFromMenu() {
        draftGate = menu.gateMode();
        draftChannel = menu.channelMode();
        draftDirection = menu.directionMode();
        draftStateMask = menu.stateMask();
        draftWhitelist = menu.filterWhitelist();
        draftFilter = menu.filterSubstances();
        lastServerGate = draftGate;
        lastServerChannel = draftChannel;
        lastServerDirection = draftDirection;
        lastServerEnergy = menu.energyLimit();
        lastServerMatter = menu.matterLimit();
        lastServerStateMask = draftStateMask;
        lastServerWhitelist = draftWhitelist;
        lastServerFilter = draftFilter;
    }

    private void removeFilterIndex(int idx) {
        if (idx < 0 || idx >= draftFilter.size()) return;
        var next = new ArrayList<>(draftFilter);
        next.remove(idx);
        draftFilter = List.copyOf(next);
        sendConfig(menu.customName());
    }

    private void toggleFilterTile(int tileIndex) {
        int index = scroll.offset() * COLUMNS + tileIndex;
        if (index < 0 || index >= pickerKeys.size()) return;
        SubstanceKey chosen = pickerKeys.get(index);
        var next = new ArrayList<>(draftFilter);
        if (next.contains(chosen)) {
            next.remove(chosen);
        } else {
            if (next.size() >= MatterCableRelayBlockEntity.MAX_FILTER_ENTRIES) return;
            next.add(chosen);
        }
        draftFilter = List.copyOf(next);
        sendConfig(menu.customName());
    }

    private void cyclePreset(EditBox box, int[] presets) {
        int cur = parseBox(box, Integer.MAX_VALUE);
        int next = presets[0];
        for (int p : presets) {
            if (p > cur) { next = p; break; }
        }
        updating = true;
        box.setValue(Integer.toString(next));
        updating = false;
        sendConfig(menu.customName());
    }

    private static int parseBox(EditBox box, int max) {
        if (box == null) return 0;
        String raw = box.getValue().strip();
        if (raw.isEmpty()) return 0;
        try { return (int) Math.clamp(Long.parseLong(raw), 0L, max); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private void sendConfig(String nextName) {
        int eLim = parseBox(energyBox, MatterCableRelayBlockEntity.MAX_ENERGY_LIMIT);
        int mLim = parseBox(matterBox, MatterCableRelayBlockEntity.MAX_MATTER_LIMIT);
        dirty = false;
        ClientPacketDistributor.sendToServer(new MatterCableRelayPayload.Configure(
                menu.containerId, draftGate, draftChannel, draftDirection,
                eLim, mLim, draftStateMask, draftWhitelist, draftFilter, nextName));
        refreshWidgets();
    }

    private boolean favorite(SubstanceKey key) {
        return minecraft != null && minecraft.player != null
                && StockFavorites.get().containsSubstance(minecraft.player.getUUID(), key);
    }

    private void togglePin(SubstanceKey key) {
        if (minecraft == null || minecraft.player == null) return;
        var stockKey = new MachineSnapshot.StockKey(key.id().toString(), key.phase().name().toLowerCase(Locale.ROOT));
        StockFavorites.get().toggle(minecraft.player.getUUID(), stockKey);
        ClientPacketDistributor.sendToServer(new StockFavoriteTogglePayload(stockKey));
        rebuildCatalogue();
    }

    private void rebuildCatalogue() {
        catalogue = ClientChemistry.current();
        favoritesRevision = StockFavorites.get().revision();
        var keys = new TreeSet<SubstanceKey>();
        for (var definition : catalogue.substances()) {
            if (!ReservedSubstances.reserved(definition.id())) {
                for (var phase : definition.phases()) keys.add(new SubstanceKey(definition.id(), phase));
            }
        }
        keys.addAll(draftFilter);
        String query = searchBox == null ? "" : searchBox.getValue().strip().toLowerCase(Locale.ROOT);
        pickerKeys = keys.stream()
                .filter(key -> key.id().toString().length() <= CellPartitionData.MAX_ID_LENGTH)
                .filter(key -> query.isEmpty()
                        || key.id().toString().contains(query)
                        || SubstanceNames.labelled(key).getString().toLowerCase(Locale.ROOT).contains(query)
                        || SubstanceNames.phase(key.phase()).getString().toLowerCase(Locale.ROOT).contains(query)
                        || SubstanceLabels.matchesSymbol(SubstanceLabels.iconLabel(key.id()), query))
                .sorted(Comparator.comparing((SubstanceKey k) -> !draftFilter.contains(k))
                        .thenComparing(k -> !favorite(k)))
                .toList();
        scroll.setSize((pickerKeys.size() + COLUMNS - 1) / COLUMNS);
        refreshWidgets();
    }

    private void refreshWidgets() {
        if (gateBtn == null) return;
        routingTab.setAccent(ACCENT);
        routingTab.setSelected(activeTab == TAB_ROUTING);
        filterTab.setAccent(ACCENT);
        filterTab.setSelected(activeTab == TAB_FILTER);
        deviceTab.setAccent(ACCENT);
        deviceTab.setSelected(activeTab == TAB_DEVICE);

        boolean serverChanged = menu.gateMode() != lastServerGate
                || menu.channelMode() != lastServerChannel
                || menu.directionMode() != lastServerDirection
                || menu.energyLimit() != lastServerEnergy
                || menu.matterLimit() != lastServerMatter
                || menu.stateMask() != lastServerStateMask
                || menu.filterWhitelist() != lastServerWhitelist
                || !menu.filterSubstances().equals(lastServerFilter);
        if (!dirty && serverChanged) {
            boolean filterListChanged = !menu.filterSubstances().equals(lastServerFilter);
            syncDraftFromMenu();
            updating = true;
            energyBox.setValue(Integer.toString(lastServerEnergy));
            matterBox.setValue(Integer.toString(lastServerMatter));
            updating = false;
            if (filterListChanged && activeTab == TAB_FILTER) {
                rebuildCatalogue();
                return;
            }
        }

        boolean routing = activeTab == TAB_ROUTING;
        boolean filter = activeTab == TAB_FILTER;
        boolean device = activeTab == TAB_DEVICE;

        directionBtn.visible = directionBtn.active = routing;
        reverseBtn.visible = routing;
        reverseBtn.active = routing && draftDirection == DirectionMode.ONE_WAY;
        gateBtn.visible = gateBtn.active = routing;
        channelBtn.visible = channelBtn.active = routing;
        energyBox.visible = energyBox.active = routing;
        energyPresetBtn.visible = energyPresetBtn.active = routing;
        matterBox.visible = matterBox.active = routing;
        matterPresetBtn.visible = matterPresetBtn.active = routing;
        applyBtn.visible = applyBtn.active = routing;
        if (!routing) {
            energyBox.setFocused(false);
            matterBox.setFocused(false);
        }

        directionBtn.setMessage(tr("direction", tr("direction." + draftDirection.key())));
        directionBtn.setSelected(draftDirection == DirectionMode.ONE_WAY);
        gateBtn.setMessage(tr("gate", tr("gate." + draftGate.key())));
        gateBtn.setSelected(draftGate != GateMode.ALWAYS_OPEN);
        channelBtn.setMessage(tr("channel", tr("channel." + draftChannel.key())));
        channelBtn.setSelected(draftChannel != ChannelMode.BOTH);

        int eLim = parseBox(energyBox, MatterCableRelayBlockEntity.MAX_ENERGY_LIMIT);
        int mLim = parseBox(matterBox, MatterCableRelayBlockEntity.MAX_MATTER_LIMIT);
        energyPresetBtn.setMessage(eLim == 0 ? tr("unlimited") : Component.literal(Quantities.compact(eLim) + "/t"));
        matterPresetBtn.setMessage(mLim == 0 ? tr("unlimited") : Component.literal(Quantities.compact(mLim) + "/t"));

        solidBtn.visible = solidBtn.active = filter;
        liquidBtn.visible = liquidBtn.active = filter;
        gasBtn.visible = gasBtn.active = filter;
        primalBtn.visible = primalBtn.active = filter;
        solidBtn.setSelected((draftStateMask & MatterCableRelayBlockEntity.STATE_SOLID) != 0);
        liquidBtn.setSelected((draftStateMask & MatterCableRelayBlockEntity.STATE_LIQUID) != 0);
        gasBtn.setSelected((draftStateMask & MatterCableRelayBlockEntity.STATE_GAS) != 0);
        primalBtn.setSelected((draftStateMask & MatterCableRelayBlockEntity.STATE_PRIMAL) != 0);

        filterModeBtn.visible = filterModeBtn.active = filter;
        filterModeBtn.setMessage(tr(draftWhitelist ? "filter_whitelist" : "filter_blacklist"));
        filterModeBtn.setSelected(draftWhitelist);
        clearFilterBtn.visible = filter;
        clearFilterBtn.active = filter && !draftFilter.isEmpty();

        searchBox.visible = searchBox.active = filter;
        if (!filter) searchBox.setFocused(false);

        for (int i = 0; i < filterSlots.size(); i++) {
            var slot = filterSlots.get(i);
            slot.key = i < draftFilter.size() ? draftFilter.get(i) : null;
            slot.visible = filter;
            slot.active = filter && slot.key != null;
            if (filter && slot.key != null) {
                slot.setTooltip(Tooltip.create(SubstanceNames.labelled(slot.key).copy()
                        .append("\n").append(tr("filter_click_remove"))));
            } else {
                slot.setTooltip(null);
            }
        }

        for (int i = 0; i < tiles.size(); i++) {
            var tile = tiles.get(i);
            int index = scroll.offset() * COLUMNS + i;
            tile.key = index < pickerKeys.size() ? pickerKeys.get(index) : null;
            tile.visible = filter && !pickerKeys.isEmpty();
            tile.active = filter && tile.key != null;
            tile.selected = tile.key != null && draftFilter.contains(tile.key);
            if (tile.visible && tile.key != null) {
                tile.setMessage(SubstanceNames.labelled(tile.key));
                tile.setTooltip(Tooltip.create(
                        SubstanceDetails.joined(SubstanceDetails.tooltip(new SubstanceDetails.Hover(tile.key, 0L), true))
                                .append("\n").append(tr(tile.selected ? "filter_click_remove" : "filter_click_add"))
                                .append("\n").append(Component.translatable("screen.rematter.ui.pin_toggle"))));
            } else {
                tile.setMessage(Component.empty());
                tile.setTooltip(null);
            }
        }

        if (devicePanel != null) devicePanel.refresh(device, ACCENT);
    }

    @Override protected void containerTick() {
        super.containerTick();
        if (activeTab == TAB_FILTER && (catalogue != ClientChemistry.current()
                || favoritesRevision != StockFavorites.get().revision())) {
            rebuildCatalogue();
        } else {
            refreshWidgets();
        }
    }

    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (activeTab == TAB_FILTER && x >= leftPos + GRID_X && x < leftPos + GRID_X + COLUMNS * PITCH + 10
                && y >= topPos + GRID_Y && y < topPos + GRID_Y + ROWS * PITCH) {
            scroll.wheel(dy);
            refreshWidgets();
            return true;
        }
        return super.mouseScrolled(x, y, dx, dy);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (activeTab == TAB_FILTER) {
            if (event.button() == 1) {
                var hovered = substanceAt(event.x(), event.y());
                if (hovered.isPresent()) {
                    SubstanceDetails.open(hovered.get(), ACCENT);
                    return true;
                }
            }
            if (bar.mouseClicked(event.x(), event.y(), event.button())) {
                refreshWidgets();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (activeTab == TAB_FILTER && bar.mouseDragged(event.x(), event.y())) {
            refreshWidgets();
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        boolean changed = bar.mouseReleased();
        return super.mouseReleased(event) || changed;
    }

    @Override public Optional<SubstanceDetails.Hover> substanceAt(double x, double y) {
        if (activeTab != TAB_FILTER) return Optional.empty();
        for (var slot : filterSlots) {
            if (slot.visible && slot.key != null
                    && x >= slot.getX() && x < slot.getX() + slot.getWidth()
                    && y >= slot.getY() && y < slot.getY() + slot.getHeight()) {
                return Optional.of(new SubstanceDetails.Hover(slot.key, 0L));
            }
        }
        for (var tile : tiles) {
            if (tile.visible && tile.key != null
                    && x >= tile.getX() && x < tile.getX() + tile.getWidth()
                    && y >= tile.getY() && y < tile.getY() + tile.getHeight()) {
                return Optional.of(new SubstanceDetails.Hover(tile.key, 0L));
            }
        }
        return Optional.empty();
    }

    @Override public Rect2i substancePanelBounds() {
        return new Rect2i(leftPos, topPos, W, H);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (devicePanel != null && devicePanel.handlesKey(event)) return true;
        if (event.isEscape()) { onClose(); return true; }
        if (activeTab == TAB_ROUTING) {
            for (var box : List.of(energyBox, matterBox)) {
                if (box != null && box.isFocused()) {
                    if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                        sendConfig(menu.customName());
                        box.setFocused(false);
                        return true;
                    }
                    if (box.keyPressed(event)) return true;
                    if (event.key() != GLFW.GLFW_KEY_TAB) return true;
                }
            }
        } else if (activeTab == TAB_FILTER && searchBox != null && searchBox.canConsumeInput()) {
            if (searchBox.isFocused() && searchBox.keyPressed(event)) return true;
            if (event.key() != GLFW.GLFW_KEY_TAB) return true;
        }
        return super.keyPressed(event);
    }

    @Override public boolean charTyped(CharacterEvent event) {
        if (devicePanel != null && devicePanel.handlesChar(event)) return true;
        if (activeTab == TAB_FILTER && searchBox != null && searchBox.isFocused()) {
            return searchBox.charTyped(event);
        }
        return super.charTyped(event);
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractBackground(g, mx, my, partial);
        ScreenChrome.panel(g, leftPos, topPos, W, H);
        g.fill(leftPos + 8, topPos + 17, leftPos + W - 8, topPos + 18, BORDER);
        g.fill(leftPos + 8, topPos + 17, leftPos + 56, topPos + 18, ACCENT);
        if (activeTab == TAB_DEVICE) {
            if (devicePanel != null) devicePanel.extractBackground(g);
            return;
        }
        if (activeTab == TAB_FILTER) {
            ScreenChrome.inset(g, leftPos + 8, topPos + 89, W - 16, 18);
            ScreenChrome.icon(g, UiIcon.SEARCH, leftPos + 9, topPos + 90, MUTED);
            bar.extractRenderState(g);
            return;
        }
        ScreenChrome.inset(g, leftPos + 8, topPos + 21, W - 16, 16);
    }

    @Override protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        DeviceGeneralPanel.drawTitle(g, font, menu.customName(), title, W - 16);
        if (activeTab == TAB_DEVICE) {
            if (devicePanel != null) devicePanel.extractLabels(g, leftPos, topPos);
            return;
        }
        if (activeTab == TAB_FILTER) {
            String countStr = tr("filter_count", draftFilter.size(), MatterCableRelayBlockEntity.MAX_FILTER_ENTRIES).getString();
            ScreenChrome.text(g, font, countStr, 120, 49, 86, draftFilter.isEmpty() ? MUTED : TEXT);
            String slotHint = draftFilter.isEmpty()
                    ? tr(draftWhitelist ? "filter_empty_whitelist" : "filter_empty_blacklist").getString()
                    : tr("filter_slots_hint").getString();
            ScreenChrome.text(g, font, slotHint, 156, 71, 74, draftWhitelist && draftFilter.isEmpty() ? WARNING : MUTED);
            if (pickerKeys.isEmpty()) {
                ScreenChrome.text(g, font, tr("filter_no_matches").getString(), 18, 126, 194, MUTED);
            }
            return;
        }

        boolean open = menu.isOpen();
        ScreenChrome.icon(g, open ? UiIcon.STATUS_WORKING : UiIcon.STATUS_BLOCKED, 9, 21, -1);
        String flowSummary = menu.directionMode() == DirectionMode.ONE_WAY
                ? tr("flow_endpoints", dirLabel(menu.facing().getOpposite()), dirLabel(menu.facing())).getString()
                : tr("direction.bidirectional").getString();
        String stateText = tr(open ? "state_open" : "state_closed").getString()
                + " · " + tr(menu.redstonePowered() ? "rs_high" : "rs_low").getString()
                + " · " + flowSummary;
        ScreenChrome.text(g, font, stateText, 27, 25, W - 40, open ? ACCENT : WARNING);

        ScreenChrome.text(g, font, tr("energy_limit").getString(), 8, 107, W - 16, MUTED);
        ScreenChrome.text(g, font, tr("matter_limit").getString(), 8, 138, W - 16, MUTED);
        ScreenChrome.text(g, font, tr("footer_hint").getString(), 8, 174, 140, MUTED);
    }

    private final class FilterSlotButton extends Button {
        private @Nullable SubstanceKey key;

        private FilterSlotButton(Builder builder) { super(builder); }

        @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float partial) {
            ScreenChrome.inset(g, getX(), getY(), getWidth(), getHeight());
            if (key != null) {
                SubstanceIcon.draw(g, getX() + 1, getY() + 1, key);
                if (isHoveredOrFocused()) {
                    g.fill(getX() + 1, getY() + getHeight() - 2, getX() + getWidth() - 1, getY() + getHeight() - 1, WARNING);
                }
            }
        }
    }

    private final class SubstanceTile extends Button {
        private @Nullable SubstanceKey key;
        private boolean selected;

        private SubstanceTile(Builder builder) { super(builder); }

        @Override public void onPress(InputWithModifiers input) {
            if (key != null && input.hasShiftDown()) {
                togglePin(key);
                return;
            }
            super.onPress(input);
        }

        @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float partial) {
            ScreenChrome.raised(g, getX(), getY(), getWidth(), getHeight(), selected || isHoveredOrFocused(), active);
            if (key != null) {
                SubstanceIcon.draw(g, getX() + 1, getY() + 1, key);
                if (favorite(key)) {
                    g.fill(getX() + getWidth() - 4, getY() + 2, getX() + getWidth() - 2, getY() + 4, AMBER);
                }
                if (selected) {
                    g.fill(getX() + 1, getY() + getHeight() - 2, getX() + getWidth() - 1, getY() + getHeight() - 1, ACCENT);
                }
            }
        }
    }
}
