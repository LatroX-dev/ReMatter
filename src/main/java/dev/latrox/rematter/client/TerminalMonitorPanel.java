package dev.latrox.rematter.client;

import static dev.latrox.rematter.client.UiPalette.*;

import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.client.ui.DarkButton;
import dev.latrox.rematter.client.ui.ScrollBar;
import dev.latrox.rematter.client.ui.ScrollModel;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.network.TerminalMonitorPayload;
import dev.latrox.rematter.network.TerminalMonitorPayload.MonitorRow;
import dev.latrox.rematter.network.TerminalMonitorPayload.RowKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Network Stock & Automation Monitor panel for wired and wireless Matter Terminals.
 * Displays live cell/worker/emitter telemetry, rolling substance net rates (u/min),
 * machine target caps, Matter Level Emitter thresholds, and Void Cell overflow coverage.
 */
final class TerminalMonitorPanel {
    private static final int VISIBLE_ROWS = 6;

    private enum Filter {
        ALL,
        RATES,
        RULES
    }

    private final MachineMenu menu;
    private final Font font;
    private final Consumer<AbstractWidget> addWidget;
    private final int left;
    private final int top;
    private final int width;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final List<DarkButton> filterButtons = new ArrayList<>();
    private final List<RowWidget> rowWidgets = new ArrayList<>();
    private final ScrollModel scroll = new ScrollModel(VISIBLE_ROWS);
    private final ScrollBar scrollBar = new ScrollBar(scroll);

    private Filter activeFilter = Filter.ALL;
    private boolean visible;
    private List<MonitorRow> filteredRows = List.of();

    TerminalMonitorPanel(
            MachineMenu menu,
            Font font,
            Consumer<AbstractWidget> addWidget,
            int left,
            int top,
            int width) {
        this.menu = menu;
        this.font = font;
        this.addWidget = addWidget;
        this.left = left;
        this.top = top;
        this.width = width;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.rematter.monitor." + key, args);
    }

    void initWidgets() {
        widgets.clear();
        filterButtons.clear();
        rowWidgets.clear();

        int btnW = (width - 8) / 3;
        Filter[] values = Filter.values();
        String[] keys = {"filter_all", "filter_rates", "filter_rules"};
        String[] hintKeys = {"filter_all_hint", "filter_rates_hint", "filter_rules_hint"};
        for (int i = 0; i < values.length; i++) {
            Filter target = values[i];
            DarkButton btn = DarkButton.create(
                    tr(keys[i]),
                    tr(hintKeys[i]),
                    left + i * (btnW + 4),
                    top + 42,
                    btnW,
                    16,
                    b -> {
                        activeFilter = target;
                        scroll.setOffset(0);
                        refreshRows();
                    });
            btn.setAccent(machineAccent(MachineKind.TERMINAL));
            addWidget.accept(btn);
            widgets.add(btn);
            filterButtons.add(btn);
        }

        int listTop = top + 62;
        int rowW = width - 8;
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            RowWidget row = new RowWidget(left, listTop + i * 20, rowW);
            addWidget.accept(row);
            widgets.add(row);
            rowWidgets.add(row);
        }
        scrollBar.setBounds(left + width - 5, listTop, 5, VISIBLE_ROWS * 20 - 2);
        setVisible(false);
    }

    void setVisible(boolean value) {
        visible = value;
        for (AbstractWidget widget : widgets) {
            widget.visible = value;
            widget.active = value;
        }
        if (value) refreshRows();
    }

    void tick() {
        if (visible) refreshRows();
    }

    private void refreshRows() {
        for (int i = 0; i < filterButtons.size(); i++) {
            filterButtons.get(i).setSelected(Filter.values()[i] == activeFilter);
        }
        TerminalMonitorPayload data = menu.terminalMonitor();
        if (data.containerId() != menu.containerId) {
            filteredRows = List.of();
        } else {
            filteredRows = data.rows().stream()
                    .filter(row -> switch (activeFilter) {
                        case ALL -> true;
                        case RATES -> row.kind() == RowKind.RATE;
                        case RULES -> row.kind() != RowKind.RATE;
                    })
                    .toList();
        }
        scroll.setSize(filteredRows.size());
        for (int i = 0; i < rowWidgets.size(); i++) {
            RowWidget widget = rowWidgets.get(i);
            int idx = scroll.offset() + i;
            MonitorRow entry = visible && idx < filteredRows.size() ? filteredRows.get(idx) : null;
            widget.show(entry);
            widget.visible = visible && entry != null;
            widget.active = visible && entry != null;
        }
    }

    boolean mouseScrolled(double mx, double my, double sy) {
        if (!visible) return false;
        if (mx >= left && mx < left + width && my >= top + 60 && my < top + 184) {
            scroll.wheel(sy);
            refreshRows();
            return true;
        }
        return false;
    }

    boolean mouseClicked(MouseButtonEvent event) {
        if (!visible) return false;
        if (scrollBar.mouseClicked(event.x(), event.y(), event.button())) {
            refreshRows();
            return true;
        }
        return false;
    }

    boolean mouseDragged(MouseButtonEvent event) {
        if (!visible) return false;
        if (scrollBar.mouseDragged(event.x(), event.y())) {
            refreshRows();
            return true;
        }
        return false;
    }

    boolean mouseReleased() {
        return visible && scrollBar.mouseReleased();
    }

    Optional<SubstanceDetails.Hover> substanceAt(double mx, double my) {
        if (!visible) return Optional.empty();
        for (RowWidget widget : rowWidgets) {
            if (widget.visible && widget.entry != null && widget.isMouseOver(mx, my)) {
                SubstanceKey key = parseKey(widget.entry.substanceId(), widget.entry.phase());
                if (key != null) {
                    return Optional.of(new SubstanceDetails.Hover(key, Math.max(0L, widget.entry.current())));
                }
            }
        }
        return Optional.empty();
    }

    void drawBackground(GuiGraphicsExtractor g) {
        if (!visible) return;
        ScreenChrome.inset(g, left, top, width, 38);
    }

    void extractRenderState(GuiGraphicsExtractor g) {
        if (!visible) return;
        scrollBar.extractRenderState(g);
    }

    void drawLabels(GuiGraphicsExtractor g, int leftPos, int topPos) {
        if (!visible) return;
        int relX = left - leftPos;
        int relY = top - topPos;
        TerminalMonitorPayload d = menu.terminalMonitor();
        String storageSummary = tr(
                "summary_storage",
                Quantities.compact(d.used()),
                Quantities.compact(d.capacity()),
                d.finiteCells(),
                d.voidCells()).getString();
        ScreenChrome.text(g, font, storageSummary, relX + 4, relY + 5, width - 8, TEXT);

        String activitySummary = tr(
                "summary_activity",
                d.activeWorkers(),
                d.totalWorkers(),
                d.activeEmitters(),
                d.totalEmitters(),
                d.nodes()).getString();
        ScreenChrome.text(g, font, activitySummary, relX + 4, relY + 17, width - 8, MUTED);

        int barW = width - 8;
        int fill = d.capacity() > 0 ? (int) Math.clamp((long) barW * d.used() / d.capacity(), 0, barW) : 0;
        g.fill(relX + 4, relY + 30, relX + 4 + barW, relY + 33, HIGHLIGHT);
        if (fill > 0) {
            g.fill(relX + 4, relY + 30, relX + 4 + fill, relY + 33, machineAccent(MachineKind.TERMINAL));
        }

        if (filteredRows.isEmpty()) {
            ScreenChrome.text(g, font, tr("empty").getString(), relX + 4, relY + 72, width - 14, MUTED);
        }
    }

    private static @Nullable SubstanceKey parseKey(String id, String phase) {
        if (id.isEmpty() || phase.isEmpty()) return null;
        try {
            return new SubstanceKey(SubstanceId.parse(id), Phase.valueOf(phase.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private final class RowWidget extends Button {
        private @Nullable MonitorRow entry;

        RowWidget(int x, int y, int w) {
            super(Button.builder(Component.empty(), b -> {
                var row = (RowWidget) b;
                if (row.entry != null) {
                    SubstanceKey key = parseKey(row.entry.substanceId(), row.entry.phase());
                    if (key != null) {
                        SubstanceDetails.open(
                                new SubstanceDetails.Hover(key, Math.max(0L, row.entry.current())),
                                machineAccent(MachineKind.TERMINAL));
                    }
                }
            }).bounds(x, y, w, 18));
        }

        void show(@Nullable MonitorRow value) {
            entry = value;
            if (value == null) return;
            Component title = rowTitle(value);
            setMessage(title);
            setTooltip(Tooltip.create(rowTooltip(value, title)));
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float partial) {
            if (entry == null) return;
            ScreenChrome.raised(g, getX(), getY(), getWidth(), 18, isHoveredOrFocused(), true);
            SubstanceKey key = parseKey(entry.substanceId(), entry.phase());
            if (key != null) {
                SubstanceIcon.draw(g, getX() + 1, getY() + 1, key);
            } else {
                UiIcon fallback = switch (entry.kind()) {
                    case RATE -> UiIcon.MONITOR;
                    case TARGET_CAP -> UiIcon.MACHINE;
                    case LEVEL_EMITTER -> UiIcon.INFO;
                    case VOID_OVERFLOW -> UiIcon.STOCK;
                };
                ScreenChrome.icon(g, fallback, getX() + 1, getY() + 1, rowBadgeColor(entry));
            }
            String badge = rowBadge(entry);
            int badgeColor = rowBadgeColor(entry);
            int badgeW = Math.min(72, font.width(badge));
            int rightX = getX() + getWidth() - 4;
            ScreenChrome.text(g, font, badge, rightX - badgeW, getY() + 5, badgeW, badgeColor);
            int nameMaxW = Math.max(24, getWidth() - badgeW - 26);
            ScreenChrome.text(g, font, getMessage().getString(), getX() + 20, getY() + 5, nameMaxW, entry.known() ? TEXT : WARNING);
        }

        private Component rowTitle(MonitorRow r) {
            if (!r.substanceId().isEmpty()) {
                return SubstanceNames.substance(r.substanceId(), r.phase(), r.known());
            }
            if (r.kind() == RowKind.VOID_OVERFLOW) {
                return tr("void_all");
            }
            if (!r.contextId().isEmpty()) {
                return Component.translatable("block.rematter." + r.contextId());
            }
            return tr("emitter_unset");
        }

        private String rowBadge(MonitorRow r) {
            return switch (r.kind()) {
                case RATE -> (r.limit() > 0 ? "+" : "") + Quantities.compact(r.limit()) + "/m";
                case TARGET_CAP -> Quantities.compact(r.current()) + " / " + Quantities.compact(r.limit());
                case LEVEL_EMITTER -> tr(r.active() ? "badge_emitting" : "badge_idle").getString();
                case VOID_OVERFLOW -> tr("badge_void", r.limit()).getString();
            };
        }

        private int rowBadgeColor(MonitorRow r) {
            return switch (r.kind()) {
                case RATE -> r.limit() > 0 ? ACCENT : WARNING;
                case TARGET_CAP -> r.active() ? ACCENT : AMBER;
                case LEVEL_EMITTER -> r.active() ? ACCENT : MUTED;
                case VOID_OVERFLOW -> VIOLET;
            };
        }

        private Component rowTooltip(MonitorRow r, Component title) {
            var lines = new ArrayList<Component>();
            lines.add(title);
            switch (r.kind()) {
                case RATE -> {
                    String signed = (r.limit() > 0 ? "+" : "") + Quantities.number(r.limit());
                    lines.add(tr("tip_rate", signed));
                    lines.add(tr("tip_stock", Quantities.number(r.current())));
                }
                case TARGET_CAP -> {
                    if (!r.contextId().isEmpty()) {
                        lines.add(Component.translatable("block.rematter." + r.contextId()));
                    }
                    lines.add(tr(
                            r.active() ? "tip_target_active" : "tip_target_reached",
                            Quantities.number(r.current()),
                            Quantities.number(r.limit())));
                }
                case LEVEL_EMITTER -> lines.add(tr(
                        r.belowMode() ? "tip_emitter_below" : "tip_emitter_above",
                        Quantities.number(r.current()),
                        Quantities.number(r.limit()),
                        tr(r.active() ? "badge_emitting" : "badge_idle")));
                case VOID_OVERFLOW -> lines.add(tr(
                        r.substanceId().isEmpty() ? "tip_void_all" : "tip_void_filtered",
                        r.limit()));
            }
            return SubstanceDetails.joined(lines);
        }
    }
}
