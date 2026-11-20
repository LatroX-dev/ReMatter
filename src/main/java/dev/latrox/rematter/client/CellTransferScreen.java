package dev.latrox.rematter.client;

import dev.latrox.rematter.celltransfer.CellTransferMenu;
import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.client.ui.*;
import dev.latrox.rematter.content.CellPartitionData;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachineSnapshot;
import dev.latrox.rematter.content.VoidMatterCellItem;
import dev.latrox.rematter.network.CellTransferConfigPayload;
import dev.latrox.rematter.network.CellTransferConfigPayload.Action;
import dev.latrox.rematter.network.CellTransferPayload;
import dev.latrox.rematter.network.ClientChemistry;
import dev.latrox.rematter.network.StockFavoriteTogglePayload;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Clean two-tab Cell Transfer Station UI:
 * - Tab 1 (Transfer): uncluttered Source -> Target dock, Transfer/Swap buttons, and Source Cell contents.
 * - Tab 2 (Cell Filter): spacious 3-row substance whitelist editor with a full-width bounded search bar.
 */
public final class CellTransferScreen extends AbstractContainerScreen<CellTransferMenu> implements SubstanceHoverProvider {
    private static final int COLUMNS = 12, TRANSFER_ROWS = 2, FILTER_ROWS = 3, PITCH = 18;
    private static final int GRID_X = 14, TRANSFER_GRID_Y = 90, FILTER_GRID_Y = 63;
    private static final int ACCENT = UiPalette.machineAccent(MachineKind.DRIVE);

    private final ScrollModel scroll = new ScrollModel(TRANSFER_ROWS);
    private final ScrollBar bar = new ScrollBar(scroll);
    private final List<SubstanceTile> tiles = new ArrayList<>();

    private DarkButton transferTab, partitionTab;
    private DarkButton transferBtn, swapBtn, allBtn;
    private DarkButton sourceSlotBtn, targetSlotBtn, cellAnyBtn, cellContentsBtn, cellClearBtn, clearSearchBtn;
    private EditBox search;

    private boolean partition;
    private int selectedCell = 1;
    private boolean userPickedSlot;
    private long acknowledgement = -1, pending = -1;
    private int favoritesRevision = -1;
    private ChemistryRegistry catalogue;
    private List<SubstanceKey> filtered = List.of();

    public CellTransferScreen(CellTransferMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, CellTransferMenu.WIDTH, CellTransferMenu.HEIGHT);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.rematter.transfer." + key, args);
    }

    @Override
    protected void init() {
        super.init();
        leftPos = Math.max(23, leftPos);
        String query = search == null ? "" : search.getValue();

        // External left-rail tabs (same visual idiom as EnergyCubeScreen & MachineScreen)
        transferTab = addRenderableWidget(DarkButton.icon(
                UiIcon.EXCHANGE, tr("selection"), tr("selection_hint"),
                leftPos - 22, topPos + 18, 24, 22,
                b -> setMode(false)));
        partitionTab = addRenderableWidget(DarkButton.icon(
                UiIcon.TARGET, tr("partition"), tr("partition_hint"),
                leftPos - 22, topPos + 42, 24, 22,
                b -> setMode(true)));

        // Tab 1 (Transfer) controls: spacious center Transfer + Swap buttons, and contextual All reset
        transferBtn = addRenderableWidget(DarkButton.create(
                tr("move"), tr("move_hint"),
                leftPos + 94, topPos + 24, 68, 20,
                b -> send(Action.TRANSFER, -1, null, false)));
        swapBtn = addRenderableWidget(DarkButton.labelIcon(
                UiIcon.EXCHANGE, tr("swap_short"), tr("swap_hint"),
                leftPos + 94, topPos + 47, 68, 18,
                b -> send(Action.SWAP, -1, null, false)));
        swapBtn.setTooltip(Tooltip.create(tr("swap").copy().append("\n").append(tr("swap_hint"))));
        allBtn = addRenderableWidget(DarkButton.create(
                tr("all_short"), tr("all_hint"),
                leftPos + 198, topPos + 70, 46, 16,
                b -> send(Action.GLOBAL_ALL, -1, null, true)));

        // Tab 2 (Cell Filter) controls: clear slot selector + presets + full-width search bar
        sourceSlotBtn = addRenderableWidget(DarkButton.create(
                tr("sources"), tr("configure_slot", tr("sources")),
                leftPos + 12, topPos + 22, 58, 16,
                b -> selectPartitionSlot(0)));
        targetSlotBtn = addRenderableWidget(DarkButton.create(
                tr("targets"), tr("configure_slot", tr("targets")),
                leftPos + 72, topPos + 22, 58, 16,
                b -> selectPartitionSlot(1)));
        cellAnyBtn = addRenderableWidget(DarkButton.create(
                tr("all_short"), tr("any_cell_hint"),
                leftPos + 134, topPos + 22, 30, 16,
                b -> send(Action.CELL_ANY, selectedCell, null, true)));
        cellContentsBtn = addRenderableWidget(DarkButton.create(
                tr("contents_short"), tr("contents_hint"),
                leftPos + 166, topPos + 22, 40, 16,
                b -> send(Action.CELL_TO_CONTENTS, selectedCell, null, false)));
        cellClearBtn = addRenderableWidget(DarkButton.create(
                tr("clear_short"), tr("clear_cell_hint"),
                leftPos + 208, topPos + 22, 36, 16,
                b -> send(Action.CELL_CLEAR, selectedCell, null, false)));

        // No EditBox.setHint() — vanilla EditBox renders hints unclipped; we draw bounded hint text via ScreenChrome.text
        search = addRenderableWidget(new EditBox(font, leftPos + 30, topPos + 45, 194, 12, tr("search")));
        search.setMaxLength(128);
        search.setBordered(false);
        search.setTextColor(UiPalette.TEXT);
        search.setValue(query);
        search.setResponder(value -> {
            scroll.reset();
            rebuild();
        });
        clearSearchBtn = addRenderableWidget(DarkButton.icon(
                UiIcon.CLEAR, tr("clear_search"), tr("clear_search_hint"),
                leftPos + 228, topPos + 42, 16, 16,
                b -> {
                    search.setValue("");
                    setFocused(search);
                }));

        tiles.clear();
        for (int i = 0; i < COLUMNS * FILTER_ROWS; i++) {
            int index = i;
            tiles.add(addRenderableWidget(new SubstanceTile(
                    Button.builder(Component.empty(), b -> toggle(index))
                            .bounds(leftPos + GRID_X + (i % COLUMNS) * PITCH, topPos + FILTER_GRID_Y + (i / COLUMNS) * PITCH, PITCH, PITCH))));
        }
        rebuild();
    }

    private void selectPartitionSlot(int slot) {
        selectedCell = slot;
        userPickedSlot = true;
        scroll.reset();
        rebuild();
    }

    private void setMode(boolean partitionMode) {
        partition = partitionMode;
        menu.setPartitionView(partitionMode);
        if (!partitionMode && search != null) {
            search.setFocused(false);
        }
        if (partition && !userPickedSlot) autoPickPartitionSlot();
        scroll.setVisibleRows(partition ? FILTER_ROWS : TRANSFER_ROWS);
        scroll.reset();
        rebuild();
    }

    private void autoPickPartitionSlot() {
        if (CellPartitionData.read(menu.getSlot(1).getItem()).isPresent()) {
            selectedCell = 1;
        } else if (CellPartitionData.read(menu.getSlot(0).getItem()).isPresent()) {
            selectedCell = 0;
        } else {
            selectedCell = 1;
        }
    }

    private static Component slotName(int slot) {
        return tr(slot == 0 ? "sources" : "targets");
    }

    private Optional<CellPartitionData.Spec> policy(int slot) {
        return CellPartitionData.read(menu.getSlot(slot).getItem());
    }

    private Optional<CellPartitionData.Spec> activePolicy() {
        return policy(selectedCell);
    }

    private boolean editable() {
        return !menu.locked() && pending < 0;
    }

    private void send(Action action, int slot, SubstanceKey key, boolean value) {
        if (!editable()) return;
        pending = menu.acknowledgement();
        ClientPacketDistributor.sendToServer(new CellTransferConfigPayload(menu.containerId, menu.revision(), action, slot, key, value));
        refresh();
    }

    private void toggle(int tile) {
        int index = scroll.offset() * COLUMNS + tile;
        if (index >= filtered.size()) return;
        var key = filtered.get(index);
        send(partition ? Action.CELL_TOGGLE : Action.GLOBAL_TOGGLE, partition ? selectedCell : -1, key, true);
    }

    private boolean favorite(SubstanceKey key) {
        return minecraft.player != null && StockFavorites.get().containsSubstance(minecraft.player.getUUID(), key);
    }

    private void togglePin(SubstanceKey key) {
        if (minecraft.player == null) return;
        var stockKey = new MachineSnapshot.StockKey(key.id().toString(), key.phase().name().toLowerCase(Locale.ROOT));
        StockFavorites.get().toggle(minecraft.player.getUUID(), stockKey);
        ClientPacketDistributor.sendToServer(new StockFavoriteTogglePayload(stockKey));
        rebuild();
    }

    private void rebuild() {
        menu.setPartitionView(partition);
        int visibleRows = partition ? FILTER_ROWS : TRANSFER_ROWS;
        scroll.setVisibleRows(visibleRows);
        int gridY = partition ? FILTER_GRID_Y : TRANSFER_GRID_Y;
        bar.setBounds(leftPos + 235, topPos + gridY, 5, visibleRows * PITCH);

        catalogue = ClientChemistry.current();
        acknowledgement = menu.acknowledgement();
        favoritesRevision = StockFavorites.get().revision();
        if (partition && !userPickedSlot) autoPickPartitionSlot();
        var keys = new TreeSet<SubstanceKey>();
        var spec = activePolicy();
        if (partition && spec.isPresent()) {
            for (var definition : catalogue.substances()) {
                if (!ReservedSubstances.reserved(definition.id())) {
                    for (var phase : definition.phases()) keys.add(new SubstanceKey(definition.id(), phase));
                }
            }
            keys.addAll(spec.get().allowed());
            menu.available().forEach(entry -> keys.add(entry.key()));
        } else if (!partition) {
            keys.addAll(menu.selection());
            menu.available().forEach(entry -> keys.add(entry.key()));
        }
        String query = partition && search != null ? search.getValue().strip().toLowerCase(Locale.ROOT) : "";
        filtered = keys.stream()
                .filter(key -> key.id().toString().length() <= CellPartitionData.MAX_ID_LENGTH)
                .filter(key -> query.isEmpty()
                        || key.id().toString().contains(query)
                        || SubstanceNames.labelled(key).getString().toLowerCase(Locale.ROOT).contains(query)
                        || SubstanceNames.phase(key.phase()).getString().toLowerCase(Locale.ROOT).contains(query)
                        || SubstanceLabels.matchesSymbol(SubstanceLabels.iconLabel(key.id()), query))
                .sorted(Comparator.comparing((SubstanceKey k) -> !(partition && spec.isPresent()
                                && spec.get().restricted() && spec.get().allowed().contains(k)))
                        .thenComparing(k -> !favorite(k)))
                .toList();
        scroll.setSize((filtered.size() + COLUMNS - 1) / COLUMNS);
        refresh();
    }

    private void refresh() {
        if (search == null) return;
        menu.setPartitionView(partition);
        var spec = activePolicy();
        boolean cellEditable = editable() && spec.isPresent();
        boolean hasCells = !menu.getSlot(0).getItem().isEmpty() || !menu.getSlot(1).getItem().isEmpty();

        transferTab.setSelected(!partition);
        partitionTab.setSelected(partition);

        transferBtn.visible = swapBtn.visible = !partition;
        allBtn.visible = !partition && !menu.selectedAll();

        sourceSlotBtn.visible = targetSlotBtn.visible = cellAnyBtn.visible = cellContentsBtn.visible = cellClearBtn.visible = partition;
        search.visible = search.active = partition;
        clearSearchBtn.visible = partition && !search.getValue().isEmpty();

        if (!partition) {
            transferBtn.active = editable() && menu.canTransfer();
            transferBtn.setTooltip(Tooltip.create(tr("move").copy().append("\n")
                    .append(tr("status." + menu.status())).append("\n")
                    .append(tr("ordinary_amount", Quantities.number(menu.movableOrdinary()))).append("\n")
                    .append(tr("primal_amount", Quantities.number(menu.movablePrimal())))));
            swapBtn.active = editable() && hasCells;
            allBtn.active = editable();
        } else {
            sourceSlotBtn.active = targetSlotBtn.active = editable();
            sourceSlotBtn.setSelected(selectedCell == 0);
            targetSlotBtn.setSelected(selectedCell == 1);
            cellAnyBtn.active = cellContentsBtn.active = cellClearBtn.active = cellEditable;
            boolean unrestricted = spec.map(p -> !p.restricted()).orElse(false);
            cellAnyBtn.setSelected(spec.isPresent() && unrestricted);
            cellClearBtn.setSelected(spec.isPresent() && !unrestricted && spec.get().allowed().isEmpty());
            cellContentsBtn.setSelected(false);
        }

        for (var b : List.of(transferTab, partitionTab, transferBtn, swapBtn, allBtn,
                sourceSlotBtn, targetSlotBtn, cellAnyBtn, cellContentsBtn, cellClearBtn, clearSearchBtn)) {
            b.setAccent(ACCENT);
        }

        int visibleTileLimit = COLUMNS * (partition ? FILTER_ROWS : TRANSFER_ROWS);
        int gridY = partition ? FILTER_GRID_Y : TRANSFER_GRID_Y;
        for (int i = 0; i < tiles.size(); i++) {
            var tile = tiles.get(i);
            tile.setPosition(leftPos + GRID_X + (i % COLUMNS) * PITCH, topPos + gridY + (i / COLUMNS) * PITCH);
            if (i >= visibleTileLimit) {
                tile.key = null;
                tile.visible = false;
                tile.active = false;
                continue;
            }
            int index = scroll.offset() * COLUMNS + i;
            tile.key = index < filtered.size() ? filtered.get(index) : null;
            tile.visible = !filtered.isEmpty();
            tile.active = (partition ? cellEditable : editable()) && tile.key != null;
            tile.selected = tile.key != null && (partition
                    ? spec.map(p -> p.restricted() && p.allowed().contains(tile.key)).orElse(false)
                    : menu.selectedAll() || menu.selection().contains(tile.key));
            if (tile.key != null) {
                var key = tile.key;
                long amount = menu.available().stream().filter(e -> e.key().equals(key)).mapToLong(CellTransferPayload.Entry::amount).findFirst().orElse(0);
                var name = SubstanceNames.labelled(key);
                tile.setMessage(name);
                tile.setTooltip(Tooltip.create(SubstanceDetails.joined(SubstanceDetails.tooltip(new SubstanceDetails.Hover(key, amount), true))
                        .append("\n")
                        .append(partition ? tr(tile.selected ? "remove_filter" : "add_filter") : tr("available", Quantities.number(amount)))
                        .append("\n")
                        .append(tr(partition ? "ghost_hint" : "toggle_hint"))
                        .append("\n")
                        .append(Component.translatable("screen.rematter.ui.pin_toggle"))));
            } else {
                tile.setMessage(Component.empty());
                tile.setTooltip(null);
            }
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (pending >= 0 && menu.acknowledgement() != pending) pending = -1;
        if (acknowledgement != menu.acknowledgement()
                || catalogue != ClientChemistry.current()
                || favoritesRevision != StockFavorites.get().revision()) rebuild();
        else refresh();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractBackground(g, mx, my, partial);
        ScreenChrome.panel(g, leftPos, topPos, imageWidth, imageHeight);
        g.fill(leftPos + 8, topPos + 17, leftPos + imageWidth - 8, topPos + 18, UiPalette.BORDER);
        g.fill(leftPos + 8, topPos + 17, leftPos + 56, topPos + 18, ACCENT);

        if (!partition) {
            ScreenChrome.inset(g, leftPos + 12, topPos + 22, 78, 46);
            ScreenChrome.inset(g, leftPos + 166, topPos + 22, 78, 46);
            ScreenChrome.inset(g, leftPos + 12, topPos + 88, 232, 40);
        } else {
            ScreenChrome.inset(g, leftPos + 12, topPos + 42, 232, 16);
            ScreenChrome.icon(g, UiIcon.SEARCH, leftPos + 12, topPos + 42, UiPalette.MUTED);
            ScreenChrome.inset(g, leftPos + 12, topPos + 61, 232, 58);
        }

        for (int i = 0; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            if (!slot.isActive()) continue;
            ScreenChrome.slot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
            if (i < CellTransferMenu.FIRST_PLAYER_SLOT && !slot.hasItem()) {
                ScreenChrome.icon(g, UiIcon.SLOT_CELL, leftPos + slot.x, topPos + slot.y, UiPalette.GHOST);
            }
        }
        if (!filtered.isEmpty()) {
            bar.extractRenderState(g);
        }
    }

    private String slotLine1(int index) {
        ItemStack stack = menu.getSlot(index).getItem();
        if (stack.isEmpty()) return tr("slot_empty").getString();
        if (stack.getItem() instanceof VoidMatterCellItem) return tr("slot_void").getString();
        var views = menu.slotViews();
        if (index >= views.size()) return tr("slot_empty").getString();
        var v = views.get(index);
        if (!v.readable()) return tr("status.locked").getString();
        return Quantities.compact(v.amount()) + "/" + Quantities.compact(v.capacity());
    }

    private String slotLine2(int index) {
        ItemStack stack = menu.getSlot(index).getItem();
        if (stack.isEmpty()) return "";
        var views = menu.slotViews();
        if (index < views.size() && views.get(index).kind() == 2) return tr("primal").getString();
        var spec = policy(index);
        if (spec.isEmpty()) return "";
        return spec.get().restricted()
                ? tr("filter_count", Integer.toString(spec.get().allowed().size())).getString()
                : tr("filter_any").getString();
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        ScreenChrome.text(g, font, title.getString(), 8, 6, imageWidth - 16, UiPalette.TEXT);

        if (!partition) {
            ScreenChrome.text(g, font, tr("sources").getString(), 16, 25, 70, UiPalette.MUTED);
            ScreenChrome.text(g, font, tr("targets").getString(), 170, 25, 70, UiPalette.MUTED);

            ScreenChrome.text(g, font, slotLine1(0), 37, 39, 49, UiPalette.TEXT);
            String s2 = slotLine2(0);
            if (!s2.isEmpty()) ScreenChrome.text(g, font, s2, 37, 50, 49, UiPalette.MUTED);

            ScreenChrome.text(g, font, slotLine1(1), 191, 39, 49, UiPalette.TEXT);
            String t2 = slotLine2(1);
            if (!t2.isEmpty()) ScreenChrome.text(g, font, t2, 191, 50, 49, UiPalette.MUTED);

            long movable = Math.addExact(menu.movableOrdinary(), menu.movablePrimal());
            long moved = menu.movedLast();
            String statusText = menu.canTransfer()
                    ? tr("status.ready").getString() + " (" + Quantities.compact(movable) + " u)"
                    : "transferred".equals(menu.status()) && moved > 0
                            ? tr("status.transferred").getString() + " (" + Quantities.compact(moved) + " u)"
                            : tr("status." + menu.status()).getString();
            int statusColor = menu.locked() ? UiPalette.WARNING : menu.canTransfer() ? ACCENT : UiPalette.MUTED;
            ScreenChrome.text(g, font, statusText, 14, 74, allBtn.visible ? 180 : 228, statusColor);

            if (filtered.isEmpty()) {
                boolean sourcePrimal = !menu.slotViews().isEmpty() && menu.slotViews().getFirst().kind() == 2;
                if (sourcePrimal) {
                    ScreenChrome.text(g, font, tr("primal_amount", Quantities.number(menu.primalSourceAmount())).getString(),
                            18, 104, 216, UiPalette.TEXT);
                } else {
                    String emptyKey = menu.getSlot(0).getItem().isEmpty() ? "insert_source_hint" : "no_matches";
                    ScreenChrome.text(g, font, tr(emptyKey).getString(), 18, 104, 216, UiPalette.MUTED);
                }
            }
        } else {
            if (search.getValue().isEmpty() && !search.isFocused()) {
                ScreenChrome.text(g, font, tr("search").getString(), 30, 46, 190, UiPalette.MUTED);
            }
            if (filtered.isEmpty()) {
                String emptyKey = activePolicy().isEmpty()
                        ? (menu.getSlot(selectedCell).getItem().isEmpty() ? "insert_partition_hint" : "fixed_or_unreadable")
                        : "no_matches";
                ScreenChrome.twoLines(g, font, tr(emptyKey).getString(), 18, 81, 214, UiPalette.MUTED);
            }
            String slotSummary = slotName(selectedCell).getString() + ": "
                    + (menu.getSlot(selectedCell).getItem().isEmpty()
                            ? tr("slot_empty").getString()
                            : (slotLine2(selectedCell).isEmpty() ? slotLine1(selectedCell) : slotLine2(selectedCell)));
            ScreenChrome.text(g, font, slotSummary, 14, 121, 212, UiPalette.MUTED);
        }

        ScreenChrome.text(g, font, Component.translatable("screen.rematter.inventory").getString(), 48, 133, 160, UiPalette.MUTED);
        if (menu.partial()) ScreenChrome.icon(g, UiIcon.WARNING, 228, 131, UiPalette.WARNING);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractRenderState(g, mx, my, partial);
        int x = mx - leftPos, y = my - topPos;
        if (!partition && x >= 12 && x < 194 && y >= 70 && y < 86) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    tr("status." + menu.status()),
                    tr("preview"),
                    tr("ordinary_amount", Quantities.number(menu.movableOrdinary())),
                    tr("primal_amount", Quantities.number(menu.movablePrimal())),
                    tr("last_ordinary", Quantities.number(menu.movedOrdinaryLast())),
                    tr("last_primal", Quantities.number(menu.movedPrimalLast()))), mx, my);
        }
        if (menu.partial() && x >= 228 && x < 244 && y >= 131 && y < 147) {
            g.setComponentTooltipForNextFrame(font, List.of(tr("partial")), mx, my);
        }
        if (!partition) {
            for (int i = 0; i < CellTransferMenu.FIRST_PLAYER_SLOT; i++) {
                var slot = menu.getSlot(i);
                if (slot.isActive() && !slot.hasItem() && x >= slot.x && x < slot.x + 16 && y >= slot.y && y < slot.y + 16) {
                    g.setComponentTooltipForNextFrame(font, List.of(slotName(i), tr(i == 0 ? "source_hint" : "target_hint")), mx, my);
                }
            }
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        int gridY = partition ? FILTER_GRID_Y : TRANSFER_GRID_Y;
        int gridH = (partition ? FILTER_ROWS : TRANSFER_ROWS) * PITCH;
        if (x >= leftPos + GRID_X && x < leftPos + 240 && y >= topPos + gridY && y < topPos + gridY + gridH) {
            scroll.wheel(dy);
            refresh();
            return true;
        }
        return super.mouseScrolled(x, y, dx, dy);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1) {
            var hovered = substanceAt(event.x(), event.y());
            if (hovered.isPresent()) {
                SubstanceDetails.open(hovered.get(), ACCENT);
                return true;
            }
        }
        if (!filtered.isEmpty() && bar.mouseClicked(event.x(), event.y(), event.button())) {
            refresh();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public Optional<SubstanceDetails.Hover> substanceAt(double x, double y) {
        for (var tile : tiles) {
            if (tile.visible && tile.key != null && x >= tile.getX() && x < tile.getX() + tile.getWidth() && y >= tile.getY() && y < tile.getY() + tile.getHeight()) {
                long amount = menu.available().stream().filter(entry -> entry.key().equals(tile.key)).mapToLong(CellTransferPayload.Entry::amount).findFirst().orElse(0);
                return Optional.of(new SubstanceDetails.Hover(tile.key, amount));
            }
        }
        return Optional.empty();
    }

    @Override
    public net.minecraft.client.renderer.Rect2i substancePanelBounds() {
        return new net.minecraft.client.renderer.Rect2i(leftPos, topPos, imageWidth, imageHeight);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!filtered.isEmpty() && bar.mouseDragged(event.x(), event.y())) {
            refresh();
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean changed = bar.mouseReleased();
        return super.mouseReleased(event) || changed;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (partition && search.canConsumeInput() && !event.isEscape()) {
            if (search.keyPressed(event)) return true;
            if (event.key() != 258) return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (partition && search.isFocused()) return search.charTyped(event);
        return super.charTyped(event);
    }

    private final class SubstanceTile extends Button {
        private SubstanceKey key;
        private boolean selected;

        private SubstanceTile(Builder builder) {
            super(builder);
        }

        @Override
        public void onPress(InputWithModifiers input) {
            if (key != null && input.hasShiftDown()) {
                togglePin(key);
                return;
            }
            super.onPress(input);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float partial) {
            ScreenChrome.raised(g, getX(), getY(), getWidth(), getHeight(), selected || isHoveredOrFocused(), active);
            if (key != null) {
                SubstanceIcon.draw(g, getX() + 1, getY() + 1, key);
                if (favorite(key)) {
                    g.fill(getX() + getWidth() - 4, getY() + 2, getX() + getWidth() - 2, getY() + 4, UiPalette.AMBER);
                }
            }
            if (selected) g.fill(getX() + 1, getY() + getHeight() - 2, getX() + getWidth() - 1, getY() + getHeight() - 1, ACCENT);
        }
    }
}
