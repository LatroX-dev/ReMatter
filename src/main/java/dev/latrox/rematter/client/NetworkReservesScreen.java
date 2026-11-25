package dev.latrox.rematter.client;

import static dev.latrox.rematter.client.UiPalette.*;

import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.ReservedSubstances;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.client.ui.DarkButton;
import dev.latrox.rematter.client.ui.ScrollBar;
import dev.latrox.rematter.client.ui.ScrollModel;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.content.MachineSnapshot.StockKey;
import dev.latrox.rematter.content.NetworkReservesSavedData;
import dev.latrox.rematter.network.ClientChemistry;
import dev.latrox.rematter.network.NetworkReservesPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Searchable global substance reserve editor opened from a Matter Terminal. */
public final class NetworkReservesScreen extends SubstanceScreenLayer {
    private static final int W = 312, H = 204, ROW_H = 18;
    private static final long[] PRESETS = {0L, 1_000L, 10_000L, 50_000L, 250_000L, 1_000_000L};
    private final MachineMenu menu;
    private final int accent;
    private final ScrollModel scroll = new ScrollModel(7);
    private final ScrollBar bar = new ScrollBar(scroll);
    private final List<RowButton> rowButtons = new ArrayList<>();
    private final List<DarkButton> presetButtons = new ArrayList<>();
    private List<RowEntry> allEntries = List.of();
    private List<RowEntry> filtered = List.of();
    private StockKey focusedKey;
    private EditBox search, amountBox;
    private DarkButton applyBtn, closeBtn;
    private int left, top, listX, listW, listTop, detailX, detailW;
    private int lastReserveRev = -1;
    private boolean updatingAmount;
    private Component error = Component.empty();

    private record RowEntry(StockKey stockKey, SubstanceKey key, String name, long stock, long reserve) {}

    public NetworkReservesScreen(MachineMenu menu) {
        super(Component.translatable("screen.rematter.reserves.title"));
        this.menu = menu;
        this.accent = machineAccent(menu.kind());
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.rematter.reserves." + key, args);
    }

    @Override protected void init() {
        rememberLayout();
        left = (width - W) / 2;
        top = (height - H) / 2;
        listX = left + 8;
        listW = 148;
        listTop = top + 42;
        detailX = listX + listW + 6;
        detailW = left + W - 8 - detailX;
        scroll.setVisibleRows(7);

        String q = search == null ? "" : search.getValue();
        search = addRenderableWidget(new EditBox(font, listX + 18, top + 25, listW - 22, 12, tr("search")));
        search.setMaxLength(128);
        search.setBordered(false);
        search.setTextColor(TEXT);
        search.setHint(tr("search").copy().withStyle(s -> s.withColor(MUTED & 0xffffff)));
        search.setValue(q);
        search.setResponder(v -> { scroll.reset(); rebuildFilter(); });

        rowButtons.clear();
        for (int i = 0; i < scroll.visibleRows(); i++) {
            int row = i;
            rowButtons.add(addRenderableWidget(new RowButton(Button.builder(Component.empty(), b -> selectRow(row))
                    .bounds(listX, listTop + i * ROW_H, listW - 7, ROW_H))));
        }
        bar.setBounds(listX + listW - 5, listTop, 5, scroll.visibleRows() * ROW_H);

        amountBox = addRenderableWidget(new EditBox(font, detailX + 6, top + 90, detailW - 38, 16, tr("amount")));
        amountBox.setMaxLength(13);
        amountBox.setFilter(s -> s.matches("[0-9]*"));
        amountBox.setTextColor(TEXT);
        amountBox.setHint(Component.literal("0"));
        amountBox.setResponder(v -> { if (!updatingAmount) error = Component.empty(); });

        applyBtn = addRenderableWidget(DarkButton.icon(UiIcon.CONFIRM, tr("apply"), tr("apply_hint"),
                detailX + detailW - 28, top + 89, 22, 18, b -> saveSelected()));
        applyBtn.setAccent(accent);

        presetButtons.clear();
        int pw = (detailW - 18) / 3;
        for (int i = 0; i < PRESETS.length; i++) {
            long val = PRESETS[i];
            int col = i % 3;
            int row = i / 3;
            String lbl = val == 0L ? tr("off").getString() : Quantities.compact(val);
            var btn = addRenderableWidget(DarkButton.create(Component.literal(lbl), tr("preset_hint", Quantities.number(val)),
                    detailX + 6 + col * (pw + 3), top + 114 + row * 20, pw, 17, b -> applyPreset(val)));
            btn.setAccent(accent);
            presetButtons.add(btn);
        }

        closeBtn = addRenderableWidget(DarkButton.labelIcon(UiIcon.CANCEL, Component.translatable("screen.rematter.recipes.close"),
                Component.translatable("screen.rematter.recipes.close_hint"),
                detailX + detailW - 56, top + H - 24, 52, 18, b -> onClose()));
        closeBtn.setAccent(accent);

        rebuildEntries();
        setInitialFocus(search);
    }

    private void rebuildEntries() {
        lastReserveRev = ClientNetworkReserves.revision();
        var stockMap = new HashMap<StockKey, Long>();
        for (var e : menu.stock().entries()) stockMap.put(e.key(), e.amount());
        long primalAmt = menu.extras().has("primal") ? menu.extras().get("primal").getAsLong() : 0L;
        var primalStockKey = new StockKey(ReservedSubstances.PRIMAL_MATTER.toString(), "solid");
        stockMap.put(primalStockKey, primalAmt);

        var keys = new LinkedHashSet<StockKey>();
        keys.add(primalStockKey);
        keys.addAll(ClientNetworkReserves.all().keySet());
        keys.addAll(stockMap.keySet());
        for (var def : ClientChemistry.current().substances()) {
            for (var phase : def.phases()) {
                keys.add(new StockKey(def.id().toString(), phase.name().toLowerCase(Locale.ROOT)));
            }
        }

        var built = new ArrayList<RowEntry>(keys.size());
        for (var sk : keys) {
            SubstanceKey parsed = parseKey(sk);
            String label = parsed == null ? sk.id() : SubstanceNames.substance(parsed).getString();
            long stock = stockMap.getOrDefault(sk, 0L);
            long reserve = ClientNetworkReserves.amount(sk);
            built.add(new RowEntry(sk, parsed, label, stock, reserve));
        }
        built.sort(Comparator.<RowEntry>comparingInt(e -> e.reserve() > 0L ? 0 : e.stock() > 0L ? 1 : 2)
                .thenComparing(e -> e.name().toLowerCase(Locale.ROOT)));
        allEntries = List.copyOf(built);
        if (focusedKey == null && !allEntries.isEmpty()) focusedKey = allEntries.getFirst().stockKey();
        rebuildFilter();
    }

    private void rebuildFilter() {
        String q = search == null ? "" : search.getValue().strip().toLowerCase(Locale.ROOT);
        filtered = allEntries.stream().filter(e -> q.isEmpty()
                || e.name().toLowerCase(Locale.ROOT).contains(q)
                || e.stockKey().id().toLowerCase(Locale.ROOT).contains(q)
                || (e.key() != null && SubstanceLabels.matchesSymbol(SubstanceLabels.iconLabel(e.key().id()), q))).toList();
        scroll.setSize(filtered.size());
        if (focusedKey != null && filtered.stream().noneMatch(e -> e.stockKey().equals(focusedKey))) {
            focusedKey = filtered.isEmpty() ? null : filtered.getFirst().stockKey();
        }
        syncFocusedAmount();
        refreshRows();
    }

    private void selectRow(int row) {
        int idx = scroll.offset() + row;
        if (idx >= filtered.size()) return;
        focusedKey = filtered.get(idx).stockKey();
        error = Component.empty();
        syncFocusedAmount();
        refreshRows();
    }

    private void syncFocusedAmount() {
        if (amountBox == null) return;
        long reserve = focusedKey == null ? 0L : ClientNetworkReserves.amount(focusedKey);
        updatingAmount = true;
        amountBox.setValue(Long.toString(reserve));
        updatingAmount = false;
    }

    private void refreshRows() {
        for (int i = 0; i < rowButtons.size(); i++) {
            int idx = scroll.offset() + i;
            var btn = rowButtons.get(i);
            btn.entry = idx < filtered.size() ? filtered.get(idx) : null;
            btn.visible = btn.active = btn.entry != null;
        }
        boolean editable = focusedKey != null && menu.canManageSecurity();
        if (amountBox != null) {
            amountBox.active = editable;
            amountBox.setEditable(editable);
        }
        if (applyBtn != null) applyBtn.active = editable;
        for (var p : presetButtons) p.active = editable;
    }

    private void applyPreset(long value) {
        if (focusedKey == null || !menu.canManageSecurity()) return;
        updatingAmount = true;
        amountBox.setValue(Long.toString(value));
        updatingAmount = false;
        sendReserve(focusedKey, value);
    }

    private void saveSelected() {
        if (focusedKey == null || amountBox == null || !menu.canManageSecurity()) return;
        String raw = amountBox.getValue().strip();
        try {
            long val = raw.isEmpty() ? 0L : Long.parseLong(raw);
            if (val < 0L || val > NetworkReservesSavedData.MAX_RESERVE) throw new IllegalArgumentException();
            error = Component.empty();
            sendReserve(focusedKey, val);
        } catch (IllegalArgumentException invalid) {
            error = Component.translatable("screen.rematter.invalid_target");
        }
    }

    private void sendReserve(StockKey key, long amount) {
        ClientPacketDistributor.sendToServer(new NetworkReservesPayload.Update(menu.containerId, key, amount));
    }

    private RowEntry focusedEntry() {
        if (focusedKey == null) return null;
        for (var e : allEntries) if (e.stockKey().equals(focusedKey)) return e;
        return null;
    }

    @Override public void tick() {
        if (minecraft.player == null || minecraft.player.containerMenu != menu) { onClose(); return; }
        if (lastReserveRev != ClientNetworkReserves.revision()) rebuildEntries();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (inside(x, y, listX, listTop, listW, scroll.visibleRows() * ROW_H)) {
            scroll.wheel(dy);
            refreshRows();
            return true;
        }
        return super.mouseScrolled(x, y, dx, dy);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (bar.mouseClicked(event.x(), event.y(), event.button())) { refreshRows(); return true; }
        return super.mouseClicked(event, doubleClick);
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (bar.mouseDragged(event.x(), event.y())) { refreshRows(); return true; }
        return super.mouseDragged(event, dx, dy);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        return super.mouseReleased(event) | bar.mouseReleased();
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) { onClose(); return true; }
        if (amountBox != null && amountBox.isFocused()) {
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) { saveSelected(); return true; }
            if (amountBox.keyPressed(event)) return true;
            if (event.key() != GLFW.GLFW_KEY_TAB) return true;
        }
        if (search != null && search.isFocused()) {
            if (search.keyPressed(event)) return true;
            if (event.key() != GLFW.GLFW_KEY_TAB) return true;
        }
        return super.keyPressed(event);
    }

    @Override public boolean charTyped(CharacterEvent event) {
        if (amountBox != null && amountBox.isFocused()) return amountBox.charTyped(event);
        if (event.isAllowedChatCharacter() && search != null) {
            setFocused(search);
            return search.charTyped(event);
        }
        return super.charTyped(event);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        ScreenChrome.panel(g, left, top, W, H);
        g.fill(left + 8, top + 17, left + W - 8, top + 18, BORDER);
        g.fill(left + 8, top + 17, left + 56, top + 18, accent);
        ScreenChrome.icon(g, UiIcon.LOCKED, left + 6, top + 1, accent);
        ScreenChrome.text(g, font, title.getString(), left + 24, top + 5, W - 32, TEXT);

        ScreenChrome.inset(g, listX, top + 20, listW, 18);
        ScreenChrome.icon(g, UiIcon.SEARCH, listX + 1, top + 21, MUTED);
        ScreenChrome.inset(g, detailX, top + 20, detailW, H - 50);

        bar.extractRenderState(g);
        int activeCount = ClientNetworkReserves.all().size();
        ScreenChrome.text(g, font, tr("active_count", activeCount).getString(), listX, top + H - 15, listW, MUTED);

        var cur = focusedEntry();
        if (cur != null) {
            if (cur.key() != null) SubstanceIcon.draw(g, detailX + 6, top + 26, cur.key());
            else ScreenChrome.icon(g, UiIcon.UNKNOWN, detailX + 6, top + 26, MUTED);
            ScreenChrome.text(g, font, cur.name(), detailX + 26, top + 26, detailW - 32, TEXT);
            ScreenChrome.text(g, font, SubstanceNames.phase(cur.stockKey().phase()).getString(), detailX + 26, top + 36, detailW - 32, MUTED);

            ScreenChrome.text(g, font, tr("current_stock", Quantities.number(cur.stock())).getString(), detailX + 6, top + 52, detailW - 12, TEXT);
            int resCol = cur.reserve() > 0L ? (cur.stock() <= cur.reserve() ? WARNING : accent) : MUTED;
            String resVal = cur.reserve() > 0L ? Quantities.number(cur.reserve()) : tr("off").getString();
            ScreenChrome.text(g, font, tr("current_reserve", resVal).getString(), detailX + 6, top + 64, detailW - 12, resCol);

            Component lbl = !menu.canManageSecurity()
                    ? Component.translatable("screen.rematter.settings.security_owner_only")
                    : error.getString().isEmpty() ? tr("set_floor") : error;
            ScreenChrome.text(g, font, lbl.getString(), detailX + 6, top + 78, detailW - 12,
                    !menu.canManageSecurity() || !error.getString().isEmpty() ? WARNING : MUTED);
            ScreenChrome.twoLines(g, font, tr("help").getString(), detailX + 6, top + 156, detailW - 12, MUTED);
        }

        super.extractRenderState(g, mx, my, partial);
    }

    @Override public Optional<SubstanceDetails.Hover> substanceAt(double x, double y) {
        if (!inside(x, y, listX, listTop, listW - 7, scroll.visibleRows() * ROW_H)) return Optional.empty();
        int idx = scroll.offset() + (int) ((y - listTop) / ROW_H);
        if (idx < 0 || idx >= filtered.size()) return Optional.empty();
        var e = filtered.get(idx);
        return e.key() == null ? Optional.empty() : Optional.of(new SubstanceDetails.Hover(e.key(), e.stock()));
    }

    @Override public Rect2i substancePanelBounds() { return new Rect2i(left, top, W, H); }
    @Override public int substanceAccent() { return accent; }

    private static SubstanceKey parseKey(StockKey sk) {
        try { return new SubstanceKey(SubstanceId.parse(sk.id()), Phase.valueOf(sk.phase().toUpperCase(Locale.ROOT))); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private final class RowButton extends DarkButton {
        private RowEntry entry;
        RowButton(Button.Builder builder) { super(builder, null, false); }

        @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float partial) {
            if (entry == null) return;
            boolean selected = entry.stockKey().equals(focusedKey);
            ScreenChrome.raised(g, getX(), getY(), getWidth(), getHeight(), selected || isHoveredOrFocused(), active);
            if (entry.key() != null) SubstanceIcon.draw(g, getX() + 1, getY() + 1, entry.key());
            if (entry.reserve() > 0L) {
                String badge = Quantities.compact(entry.reserve());
                int bw = Math.min(44, font.width(badge));
                int col = entry.stock() <= entry.reserve() ? WARNING : accent;
                ScreenChrome.text(g, font, badge, getX() + getWidth() - bw - 4, getY() + 5, bw, col);
                ScreenChrome.text(g, font, entry.name(), getX() + 20, getY() + 5, getWidth() - bw - 26, TEXT);
            } else {
                ScreenChrome.text(g, font, entry.name(), getX() + 20, getY() + 5, getWidth() - 24, MUTED);
            }
        }
    }
}
