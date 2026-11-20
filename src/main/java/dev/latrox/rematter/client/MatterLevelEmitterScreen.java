package dev.latrox.rematter.client;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.ReservedSubstances;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.client.ui.DarkButton;
import dev.latrox.rematter.client.ui.ScrollBar;
import dev.latrox.rematter.client.ui.ScrollModel;
import dev.latrox.rematter.content.CellPartitionData;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachineSnapshot;
import dev.latrox.rematter.content.MatterLevelEmitterBlockEntity;
import dev.latrox.rematter.content.MatterLevelEmitterMenu;
import dev.latrox.rematter.network.ClientChemistry;
import dev.latrox.rematter.network.MatterLevelEmitterPayload;
import dev.latrox.rematter.network.StockFavoriteTogglePayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/** Console for Matter Level Emitter: substance picker, threshold input, >= / < comparison toggle, and live telemetry. */
public final class MatterLevelEmitterScreen extends AbstractContainerScreen<MatterLevelEmitterMenu>
        implements SubstanceHoverProvider {
    private static final int WIDTH = 236;
    private static final int HEIGHT = 186;
    private static final int COLUMNS = 11;
    private static final int ROWS = 4;
    private static final int GRID_X = 14;
    private static final int GRID_Y = 98;
    private static final int PITCH = 18;
    private static final int ACCENT = UiPalette.machineAccent(MachineKind.DRIVE);

    private final ScrollModel scroll = new ScrollModel(ROWS);
    private final ScrollBar bar = new ScrollBar(scroll);
    private final List<SubstanceTile> tiles = new ArrayList<>();
    private EditBox thresholdBox;
    private EditBox searchBox;
    private DarkButton modeButton;
    private DarkButton clearButton;
    private ChemistryRegistry catalogue;
    private List<SubstanceKey> filtered = List.of();
    private boolean updatingThresholdBox;
    private long lastSyncedThreshold = -1L;
    private int favoritesRevision = -1;
    private @Nullable SubstanceKey lastSyncedTarget;

    public MatterLevelEmitterScreen(MatterLevelEmitterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.rematter.level_emitter." + key, args);
    }

    @Override
    protected void init() {
        super.init();

        modeButton = addRenderableWidget(DarkButton.labelIcon(
                UiIcon.REDSTONE,
                modeLabel(menu.emitWhenBelow()),
                modeHint(menu.emitWhenBelow()),
                leftPos + 14, topPos + 50, 96, 18,
                b -> sendConfig(menu.target(), menu.threshold(), !menu.emitWhenBelow())));
        modeButton.setAccent(ACCENT);

        thresholdBox = addRenderableWidget(new EditBox(font, leftPos + 118, topPos + 55, 86, 12, tr("threshold")));
        thresholdBox.setMaxLength(13);
        thresholdBox.setBordered(false);
        thresholdBox.setTextColor(UiPalette.TEXT);
        thresholdBox.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        updatingThresholdBox = true;
        thresholdBox.setValue(Long.toString(menu.threshold()));
        updatingThresholdBox = false;
        lastSyncedThreshold = menu.threshold();
        thresholdBox.setResponder(this::onThresholdEdited);

        clearButton = addRenderableWidget(DarkButton.icon(
                UiIcon.CLEAR, tr("clear_target"), tr("clear_target_hint"),
                leftPos + 208, topPos + 50, 18, 18,
                b -> sendConfig(null, menu.threshold(), menu.emitWhenBelow())));
        clearButton.setAccent(ACCENT);

        String query = searchBox == null ? "" : searchBox.getValue();
        searchBox = addRenderableWidget(new EditBox(font, leftPos + 32, topPos + 78, 190, 12, tr("search")));
        searchBox.setMaxLength(128);
        searchBox.setBordered(false);
        searchBox.setTextColor(UiPalette.TEXT);
        searchBox.setHint(tr("search").copy().withStyle(s -> s.withColor(UiPalette.MUTED & 0xffffff)));
        searchBox.setValue(query);
        searchBox.setResponder(v -> {
            scroll.reset();
            rebuild();
        });

        tiles.clear();
        for (int i = 0; i < COLUMNS * ROWS; i++) {
            int idx = i;
            tiles.add(addRenderableWidget(new SubstanceTile(Button.builder(Component.empty(), b -> selectTile(idx))
                    .bounds(leftPos + GRID_X + (i % COLUMNS) * PITCH, topPos + GRID_Y + (i / COLUMNS) * PITCH, PITCH, PITCH))));
        }
        bar.setBounds(leftPos + GRID_X + COLUMNS * PITCH + 5, topPos + GRID_Y, 5, ROWS * PITCH);
        lastSyncedTarget = menu.target();
        rebuild();
    }

    private static Component modeLabel(boolean emitBelow) {
        return tr(emitBelow ? "mode_below" : "mode_above");
    }

    private static Component modeHint(boolean emitBelow) {
        return tr(emitBelow ? "mode_below_hint" : "mode_above_hint");
    }

    private void onThresholdEdited(String text) {
        if (updatingThresholdBox) return;
        long next = 0L;
        if (!text.isEmpty()) {
            try {
                next = Math.clamp(Long.parseLong(text), 0L, MatterLevelEmitterBlockEntity.MAX_THRESHOLD);
            } catch (NumberFormatException overflow) {
                next = MatterLevelEmitterBlockEntity.MAX_THRESHOLD;
            }
        }
        lastSyncedThreshold = next;
        sendConfig(menu.target(), next, menu.emitWhenBelow());
    }

    private void selectTile(int tileIndex) {
        int index = scroll.offset() * COLUMNS + tileIndex;
        if (index >= filtered.size()) return;
        SubstanceKey chosen = filtered.get(index);
        sendConfig(chosen, menu.threshold(), menu.emitWhenBelow());
    }

    private void sendConfig(@Nullable SubstanceKey target, long threshold, boolean emitWhenBelow) {
        ClientPacketDistributor.sendToServer(new MatterLevelEmitterPayload.Configure(
                menu.containerId, target, Math.clamp(threshold, 0L, MatterLevelEmitterBlockEntity.MAX_THRESHOLD), emitWhenBelow));
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
        catalogue = ClientChemistry.current();
        favoritesRevision = StockFavorites.get().revision();
        var keys = new TreeSet<SubstanceKey>();
        for (var definition : catalogue.substances()) {
            if (!ReservedSubstances.reserved(definition.id())) {
                for (var phase : definition.phases()) keys.add(new SubstanceKey(definition.id(), phase));
            }
        }
        keys.add(new SubstanceKey(ReservedSubstances.PRIMAL_MATTER, Phase.SOLID));
        if (menu.target() != null) keys.add(menu.target());

        String query = searchBox == null ? "" : searchBox.getValue().strip().toLowerCase(Locale.ROOT);
        filtered = keys.stream()
                .filter(key -> key.id().toString().length() <= CellPartitionData.MAX_ID_LENGTH)
                .filter(key -> query.isEmpty()
                        || key.id().toString().contains(query)
                        || SubstanceNames.labelled(key).getString().toLowerCase(Locale.ROOT).contains(query)
                        || SubstanceNames.phase(key.phase()).getString().toLowerCase(Locale.ROOT).contains(query)
                        || SubstanceLabels.matchesSymbol(SubstanceLabels.iconLabel(key.id()), query))
                .sorted(Comparator.comparing((SubstanceKey k) -> !k.equals(menu.target()))
                        .thenComparing(k -> !favorite(k)))
                .toList();
        scroll.setSize((filtered.size() + COLUMNS - 1) / COLUMNS);
        refresh();
    }

    private void refresh() {
        if (modeButton == null) return;
        modeButton.setMessage(modeLabel(menu.emitWhenBelow()));
        modeButton.setTooltip(Tooltip.create(modeLabel(menu.emitWhenBelow()).copy().append("\n").append(modeHint(menu.emitWhenBelow()))));
        clearButton.active = menu.target() != null;

        if (!thresholdBox.isFocused() && menu.threshold() != lastSyncedThreshold) {
            updatingThresholdBox = true;
            thresholdBox.setValue(Long.toString(menu.threshold()));
            updatingThresholdBox = false;
            lastSyncedThreshold = menu.threshold();
        }

        for (int i = 0; i < tiles.size(); i++) {
            var tile = tiles.get(i);
            int index = scroll.offset() * COLUMNS + i;
            tile.key = index < filtered.size() ? filtered.get(index) : null;
            tile.visible = !filtered.isEmpty();
            tile.active = tile.key != null;
            tile.selected = tile.key != null && tile.key.equals(menu.target());
            if (tile.key != null) {
                long shownAmount = tile.selected ? menu.observedStock() : 0L;
                tile.setMessage(SubstanceNames.labelled(tile.key));
                tile.setTooltip(Tooltip.create(
                        SubstanceDetails.joined(SubstanceDetails.tooltip(new SubstanceDetails.Hover(tile.key, shownAmount), true))
                                .append("\n").append(tr(tile.selected ? "selected_substance" : "click_to_monitor"))
                                .append("\n").append(Component.translatable("screen.rematter.ui.pin_toggle"))));
            } else {
                tile.setMessage(Component.empty());
                tile.setTooltip(null);
            }
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (catalogue != ClientChemistry.current()
                || !Objects.equals(lastSyncedTarget, menu.target())
                || favoritesRevision != StockFavorites.get().revision()) {
            lastSyncedTarget = menu.target();
            rebuild();
        } else {
            refresh();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractBackground(g, mx, my, partial);
        ScreenChrome.panel(g, leftPos, topPos, imageWidth, imageHeight);
        g.fill(leftPos + 8, topPos + 17, leftPos + imageWidth - 8, topPos + 18, UiPalette.BORDER);
        g.fill(leftPos + 8, topPos + 17, leftPos + 56, topPos + 18, ACCENT);

        ScreenChrome.inset(g, leftPos + 114, topPos + 50, 92, 18);
        ScreenChrome.inset(g, leftPos + 14, topPos + 74, 212, 18);
        ScreenChrome.icon(g, UiIcon.SEARCH, leftPos + 15, topPos + 75, UiPalette.MUTED);
        bar.extractRenderState(g);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        ScreenChrome.text(g, font, title.getString(), 8, 6, imageWidth - 16, UiPalette.TEXT);

        boolean netReady = menu.networkReady();
        boolean emitting = menu.emitting();
        UiIcon netIcon = netReady ? UiIcon.NETWORK_ONLINE : UiIcon.NETWORK_OFFLINE;
        int netTint = netReady ? UiPalette.ACCENT_BRIGHT : UiPalette.MUTED;
        ScreenChrome.icon(g, netIcon, 14, 20, netTint);

        UiIcon signalIcon = emitting ? UiIcon.STATUS_WORKING : UiIcon.STATUS_IDLE;
        ScreenChrome.icon(g, signalIcon, 30, 20, UiPalette.WHITE);
        Component signalText = menu.target() == null
                ? tr("status_unconfigured")
                : !netReady
                ? tr("status_offline")
                : tr(emitting ? "status_emitting" : "status_standby");
        int signalColor = emitting ? UiPalette.ACCENT_BRIGHT : !netReady ? UiPalette.WARNING : UiPalette.TEXT;
        ScreenChrome.text(g, font, signalText.getString(), 48, 24, 174, signalColor);

        var target = menu.target();
        if (target != null) {
            SubstanceIcon.draw(g, 14, 34, target);
            String stockLine = tr("stock_readout",
                    SubstanceNames.labelled(target).getString(),
                    Quantities.compact(menu.observedStock()),
                    Quantities.compact(menu.threshold())).getString();
            ScreenChrome.text(g, font, stockLine, 34, 38, 192, UiPalette.TEXT);
        } else {
            ScreenChrome.text(g, font, tr("select_prompt").getString(), 14, 38, 208, UiPalette.MUTED);
        }

        if (filtered.isEmpty()) {
            ScreenChrome.text(g, font, tr("no_matches").getString(), 18, 114, 194, UiPalette.MUTED);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractRenderState(g, mx, my, partial);
        int x = mx - leftPos, y = my - topPos;
        if (menu.target() != null && x >= 14 && x < 224 && y >= 34 && y < 49) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    SubstanceNames.labelled(menu.target()),
                    tr("exact_stock", Quantities.number(menu.observedStock()), Quantities.number(menu.threshold()))), mx, my);
        } else if (x >= 114 && x < 206 && y >= 50 && y < 68 && !thresholdBox.isFocused()) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    tr("threshold"),
                    tr("threshold_hint", Quantities.number(menu.threshold()))), mx, my);
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (x >= leftPos + GRID_X && x < leftPos + GRID_X + COLUMNS * PITCH + 10
                && y >= topPos + GRID_Y && y < topPos + GRID_Y + ROWS * PITCH) {
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
        if (bar.mouseClicked(event.x(), event.y(), event.button())) {
            refresh();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public Optional<SubstanceDetails.Hover> substanceAt(double x, double y) {
        for (var tile : tiles) {
            if (tile.visible && tile.key != null
                    && x >= tile.getX() && x < tile.getX() + tile.getWidth()
                    && y >= tile.getY() && y < tile.getY() + tile.getHeight()) {
                long amount = tile.selected ? menu.observedStock() : 0L;
                return Optional.of(new SubstanceDetails.Hover(tile.key, amount));
            }
        }
        return Optional.empty();
    }

    @Override
    public Rect2i substancePanelBounds() {
        return new Rect2i(leftPos, topPos, imageWidth, imageHeight);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (bar.mouseDragged(event.x(), event.y())) {
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
        if (!event.isEscape() && (thresholdBox.canConsumeInput() || searchBox.canConsumeInput())) {
            if (thresholdBox.isFocused() && thresholdBox.keyPressed(event)) return true;
            if (searchBox.isFocused() && searchBox.keyPressed(event)) return true;
            if (event.key() != 258) return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (thresholdBox.isFocused()) return thresholdBox.charTyped(event);
        if (searchBox.isFocused()) return searchBox.charTyped(event);
        return super.charTyped(event);
    }

    private final class SubstanceTile extends Button {
        private @Nullable SubstanceKey key;
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
                int iconAccent = ReservedSubstances.reserved(key) ? UiPalette.PRIMAL : ACCENT;
                SubstanceIcon.draw(g, getX() + 1, getY() + 1, key);
                if (favorite(key)) {
                    g.fill(getX() + getWidth() - 4, getY() + 2, getX() + getWidth() - 2, getY() + 4, UiPalette.AMBER);
                }
                if (selected) {
                    g.fill(getX() + 1, getY() + getHeight() - 2, getX() + getWidth() - 1, getY() + getHeight() - 1, iconAccent);
                }
            }
        }
    }
}
