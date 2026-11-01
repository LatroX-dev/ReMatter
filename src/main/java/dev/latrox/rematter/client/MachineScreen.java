package dev.latrox.rematter.client;

import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.content.MachineSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** One recipe flow and one production action; configuration is a separate view. */
public final class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private static final int INK = 0xff10191e, PANEL = 0xff1b2a31, EDGE = 0xff3b5058;
    private static final int PAPER = 0xffe4efec, MUTED = 0xff9eb2b6, CYAN = 0xff67e1ce, COPPER = 0xffe1a579;
    private static final int CERAMIC = 0xffe1dac7, CERAMIC_EDGE = 0xffaaa695, LABEL = 0xff293238;
    private static final int ROWS = 6;
    private EditBox search;
    private Button primary, choose, settings, automation, repeat, addBatch, previous, next;
    private int page;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 256, 240);
    }
    @Override protected void init() {
        super.init();
        String query = search == null ? "" : search.getValue();
        search = addRenderableWidget(new EditBox(font, leftPos + 12, topPos + 30, 232, 14, tr("search")));
        search.setHint(tr("search")); search.setMaxLength(128); search.setBordered(false);
        search.setTextColor(PAPER); search.setValue(query); search.setResponder(value -> page = 0);
        primary = control("start", 90, 120, 74, 18, b -> {
            var decision = MachinePrimaryAction.forSnapshot(menu.snapshot());
            if (!decision.enabled()) return;
            if (decision.opensRecipeSelection()) openRecipes(); else if (decision.menuAction() >= 0) send(decision.menuAction());
        }, "start_once_hint");
        choose = control("recipe", 12, 120, 72, 18, b -> openRecipes(), "choose_hint");
        settings = control("details", 170, 120, 74, 18, b -> {
            menu.setSettingsOpen(!menu.settingsOpen()); page = 0;
        }, "details_hint");
        automation = control("", 94, 94, 150, 18, b -> send(3), "automation_hint");
        repeat = control("", 94, 116, 94, 18, b -> send(5), "continuous_hint");
        addBatch = control("add_batch", 192, 116, 52, 18, b -> send(4), "add_batch_hint");
        previous = control("previous", 12, 130, 20, 16, b -> page = Math.max(0, page - 1), "previous_hint");
        next = control("next", 72, 130, 20, 16, b -> page++, "next_hint");
        updateWidgets();
    }
    private static Component tr(String key) { return Component.translatable("screen.rematter." + key); }
    private Button control(String key, int x, int y, int width, int height, Button.OnPress action, String hint) {
        return addRenderableWidget(Button.builder(key.isEmpty() ? Component.empty() : tr(key), action)
                .bounds(leftPos + x, topPos + y, width, height).tooltip(Tooltip.create(tr(hint))).build(ConsoleButton::new));
    }
    private void openRecipes() { minecraft.pushGuiLayer(new ProcessSelectionScreen(menu)); }
    private void send(int action) {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
    }
    private boolean passive() { return !menu.kind().processes(); }
    private boolean drive() { return menu.kind() == MachineKind.DRIVE; }
    private boolean directCell() { return menu.kind() == MachineKind.DECOMPOSER || menu.kind() == MachineKind.SYNTHESIZER; }
    private boolean handPreparator() { return menu.kind() == MachineKind.HAND_PREPARATOR; }
    private boolean preparator() { return handPreparator() || menu.kind() == MachineKind.PREPARATOR || menu.kind() == MachineKind.INDUSTRIAL_PREPARATOR; }
    private boolean industrial() { return menu.kind() == MachineKind.INDUSTRIAL_PREPARATOR || menu.kind() == MachineKind.INDUSTRIAL_REACTOR; }
    private boolean selecting() {
        return menu.kind() == MachineKind.SYNTHESIZER || menu.kind() == MachineKind.ELECTROLYZER
                || menu.kind() == MachineKind.REACTOR || menu.kind() == MachineKind.INDUSTRIAL_REACTOR;
    }
    private static boolean working(String status) { return List.of("working", "grinding", "sieving", "collecting", "dosing", "mixing", "packing").contains(status); }
    private Component statusText() {
        var s = menu.snapshot();
        if (s.status().equals("structure") && industrial()) return Component.translatable("status.rematter." + s.structure().diagnostic());
        if (working(s.status())) return tr("processing");
        return Component.translatable("status.rematter." + s.status());
    }
    private List<MachineSnapshot.Entry> entries() {
        String query = search == null ? "" : search.getValue().strip().toLowerCase(Locale.ROOT);
        return menu.snapshot().entries().stream().filter(e -> e.name().toLowerCase(Locale.ROOT).contains(query)
                || e.id().toLowerCase(Locale.ROOT).contains(query) || e.phase().contains(query)).toList();
    }
    private void updateWidgets() {
        var s = menu.snapshot();
        boolean ready = s.containerId() == menu.containerId, detail = menu.settingsOpen();
        var decision = MachinePrimaryAction.forSnapshot(s);
        search.visible = search.active = ready && passive() && !detail;
        primary.visible = ready && !detail && decision.visible();
        primary.active = ready && decision.enabled();
        primary.setMessage(tr(decision.labelKey()));
        primary.setTooltip(Tooltip.create(tr(decision.hintKey())));
        primary.setX(leftPos + (selecting() && !decision.opensRecipeSelection() ? 90 : 12));
        primary.setWidth(selecting() && decision.opensRecipeSelection() ? 152 : 74);
        choose.visible = ready && selecting() && !detail && !decision.opensRecipeSelection();
        choose.active = ready && s.choiceCount() > 0;
        settings.active = ready;
        settings.setMessage(tr(detail ? "back_short" : "details"));
        settings.setX(leftPos + (detail ? 190 : passive() ? 174 : 170));
        settings.setY(topPos + (detail ? 25 : passive() ? 130 : 120));
        settings.setWidth(detail ? 54 : passive() ? 70 : 74);
        settings.setHeight(detail || !passive() ? 18 : 16);
        automation.visible = ready && detail;
        automation.setMessage(tr(s.automation() ? "automation_on" : "automation_off"));
        repeat.visible = addBatch.visible = ready && detail && selecting();
        boolean valid = s.selected() >= 0 && !s.preview().id().isEmpty() && s.selectedId().equals(s.preview().id()) && !s.status().equals("selection_unavailable");
        repeat.active = valid;
        repeat.setMessage(tr(s.repeat() ? "stop_repeat" : "run_repeat"));
        // Adding a finite order must never silently cancel an existing continuous order.
        addBatch.active = valid && !s.repeat() && s.queuedBatches() < 64;
        int pages = Math.max(1, (entries().size() + ROWS - 1) / ROWS);
        page = Math.clamp(page, 0, pages - 1);
        previous.visible = next.visible = ready && passive() && !detail;
        previous.active = page > 0; next.active = page + 1 < pages;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        updateWidgets();
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int mx = mouseX - leftPos, my = mouseY - topPos;
        var s = menu.snapshot();
        if (menu.settingsOpen()) {
            if (inside(mx, my, 12, 26, 170, 17)) tooltip(g, industrial() ? structureDetails(s) : recipeDetails(s), mouseX, mouseY);
            else if (inside(mx, my, 12, 82, 232, 11) || inside(mx, my, 12, 136, 232, 12))
                tooltip(g, preparator() || menu.kind() == MachineKind.ANALYZER ? recipeDetails(s) : storageDetails(s), mouseX, mouseY);
        } else if (!passive()) {
            if (inside(mx, my, 12, 47, 14, 42)) g.setTooltipForNextFrame(handPreparator() ? tr("crank_hint") : Component.literal(String.format(Locale.ROOT, "%,d / 1,000,000 FE", s.energy())), mouseX, mouseY);
            else if (inside(mx, my, 12, 94, 232, 20)) {
                var lines = new ArrayList<Component>(); lines.add(statusText());
                if (industrial() && !s.structure().formed()) lines.addAll(structureDetails(s));
                if (directCell() && !s.cell().installed()) lines.add(tr("direct_cell_hint"));
                tooltip(g, lines, mouseX, mouseY);
            } else if (!overActiveSlot(mx, my) && !s.preview().id().isEmpty() && (inside(mx, my, 12, 26, 232, 16)
                    || !menu.inputRole() && inside(mx, my, 32, 47, 76, 43)
                    || !menu.slots.get(1).isActive() && inside(mx, my, 177, 47, 67, 43))) {
                tooltip(g, recipeDetails(s), mouseX, mouseY);
            } else if (menu.slots.get(3).isActive() && !menu.slots.get(3).hasItem() && inside(mx, my, 79, 61, 18, 18)) {
                g.setTooltipForNextFrame(tr(preparator() ? "optional_mix_hint" : "direct_cell_hint"), mouseX, mouseY);
            }
        } else if (inside(mx, my, drive() ? 110 : 12, 52, drive() ? 134 : 232, 72)) {
            int index = page * ROWS + (my - 52) / 12;
            var values = entries();
            if (index < values.size()) {
                var e = values.get(index);
                g.setTooltipForNextFrame(Component.literal(e.name() + " · " + e.phase() + "\n" + String.format(Locale.ROOT, "%,d stored units", e.amount())), mouseX, mouseY);
            }
        } else if (inside(mx, my, 96, 130, 74, 16)) tooltip(g, storageDetails(s), mouseX, mouseY);
    }
    private static boolean inside(int x, int y, int left, int top, int width, int height) { return x >= left && x < left + width && y >= top && y < top + height; }
    private boolean overActiveSlot(int x, int y) {
        return menu.slots.stream().anyMatch(slot -> slot.isActive() && inside(x, y, slot.x, slot.y, 16, 16));
    }
    private void tooltip(GuiGraphicsExtractor g, List<Component> lines, int x, int y) {
        if (!lines.isEmpty()) g.setComponentTooltipForNextFrame(font, lines, x, y);
    }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        g.fill(x + 3, y + 3, x + imageWidth + 3, y + imageHeight + 3, 0x88000000);
        g.fill(x, y, x + imageWidth, y + imageHeight, CERAMIC_EDGE);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, CERAMIC);
        g.fill(x + 4, y + 22, x + imageWidth - 4, y + imageHeight - 4, INK);
        g.fill(x + 8, y + 18, x + 50, y + 20, CYAN);
        var s = menu.snapshot();
        if (!menu.settingsOpen() && passive()) {
            g.fill(x + 10, y + 27, x + 246, y + 46, PANEL);
            int start = drive() ? 110 : 12;
            for (int i = 0; i < ROWS; i++) if (i % 2 == 0) g.fill(x + start, y + 52 + i * 12, x + 244, y + 63 + i * 12, PANEL);
        } else if (!menu.settingsOpen()) {
            if (!handPreparator()) {
                g.fill(x + 15, y + 48, x + 23, y + 86, EDGE);
                int fill = (int) (36L * s.energy() / 1_000_000);
                g.fill(x + 16, y + 85 - fill, x + 22, y + 85, CYAN);
            }
            g.fill(x + 110, y + 68, x + 170, y + 74, EDGE);
            g.fill(x + 164, y + 64, x + 169, y + 78, EDGE);
            g.fill(x + 169, y + 66, x + 173, y + 76, EDGE);
            int progress = s.duration() > 0 ? (int) Math.clamp(60L * s.progress() / s.duration(), 0, 60) : 0;
            g.fill(x + 110, y + 68, x + 110 + progress, y + 74, CYAN);
        }
        for (int i = 0; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            if (!slot.isActive()) continue;
            int color = i == 0 || i == 3 ? CYAN : i == 1 ? COPPER : i == 2 ? 0xffb9a4d5 : EDGE;
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, color);
            g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, PANEL);
        }
        if (!menu.settingsOpen() && !passive() && !menu.slots.get(1).hasItem()) {
            var icon = outputIcon(s.preview());
            if (!icon.isEmpty()) g.fakeItem(icon, x + 188, y + 62);
        }
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        String name = title.getString().replace(" Controller", "");
        text(g, name, 9, 7, 238, LABEL);
        g.text(font, tr("inventory"), 47, 147, MUTED, false);
        var s = menu.snapshot();
        if (s.containerId() != menu.containerId) { g.text(font, tr("loading"), 12, 31, MUTED, false); return; }
        if (menu.settingsOpen()) { detailsLabels(g, s); return; }
        if (passive()) { storageLabels(g, s); return; }
        var p = s.preview();
        if (!handPreparator()) g.text(font, "FE", 13, 89, MUTED, false);
        String recipe = !p.name().isBlank() ? p.name() : !s.selectedName().isBlank() ? s.selectedName()
                : tr(selecting() ? "choose_recipe" : "add_input").getString();
        text(g, recipe, 12, 30, 232, PAPER);
        if (menu.inputRole()) {
            g.text(font, tr(menu.kind() == MachineKind.ANALYZER ? "sample" : "input"), 40, 48, MUTED, false);
        } else if (directCell()) {
            g.text(font, tr("stored_short"), 32, 48, MUTED, false);
            text(g, compact(s.used()), 32, 66, 42, PAPER);
        } else if (!p.inputs().isEmpty()) {
            for (int i = 0; i < Math.min(2, p.inputs().size()); i++) {
                var a = p.inputs().get(i);
                text(g, a.name(), 32, 49 + i * 20, 73, a.available() < a.required() ? COPPER : PAPER);
                g.text(font, compact(a.required()), 32, 59 + i * 20, MUTED, false);
            }
            if (p.inputs().size() > 2 || !p.complete()) g.text(font, "+", 98, 83, MUTED, false);
        }
        if (menu.slots.get(3).isActive()) g.text(font, tr(preparator() ? "mix_slot" : "cell_slot"), 78, 48, MUTED, false);
        if (menu.kind() == MachineKind.ANALYZER) {
            text(g, tr("analysis_result").getString(), 177, 48, 67, MUTED);
            if (!p.outputs().isEmpty()) {
                g.text(font, Component.translatable("screen.rematter.substance_count", p.outputs().size()), 177, 64, PAPER, false);
                text(g, tr("sample_kept").getString(), 177, 80, 67, MUTED);
            }
        } else if (menu.slots.get(1).isActive() || !outputIcon(p).isEmpty()) {
            g.text(font, tr("output"), 181, 48, MUTED, false);
            if (p.outputCount() > 0 && (p.outputKind().equals("fluid") || !menu.slots.get(1).hasItem()))
                g.text(font, p.outputCount() + (p.outputKind().equals("fluid") ? " mB" : ""), 188, 83, MUTED, false);
        } else if (!p.outputs().isEmpty()) {
            var output = p.outputs().getFirst();
            text(g, output.name(), 177, 54, 67, PAPER);
            g.text(font, compact(output.required()) + " u", 177, 69, CYAN, false);
            if (p.outputs().size() > 1 || !p.complete()) g.text(font, "+", 237, 82, MUTED, false);
        }
        if (s.duration() > 0) {
            String progress = Math.clamp(100L * s.progress() / s.duration(), 0, 100) + "%";
            g.text(font, progress, 141 - font.width(progress) / 2, 83, MUTED, false);
        }
        String status = statusText().getString() + (s.repeat() && selecting() ? " · " + tr("continuous").getString() : "");
        text(g, status, 12, 101, 232, working(s.status()) ? CYAN : PAPER);
    }
    private void detailsLabels(GuiGraphicsExtractor g, MachineSnapshot s) {
        String structure = industrial() ? s.structure().formed()
                ? dimensions(s) + " · " + tr("columns_short").getString() + ": " + s.structure().moduleCount()
                : Component.translatable("status.rematter." + s.structure().diagnostic()).getString()
                : tr("details").getString();
        text(g, structure, 12, 30, 170, industrial() && !s.structure().formed() ? COPPER : PAPER);
        if (menu.slots.get(0).isActive()) g.text(font, tr(drive() ? "cell_slot" : "input"), 40, 48, MUTED, false);
        if (menu.slots.get(1).isActive()) g.text(font, tr("output"), 181, 48, MUTED, false);
        if (menu.slots.get(3).isActive()) g.text(font, tr(preparator() ? "mix_slot" : "cell_slot"), 78, 48, MUTED, false);
        String source = preparator() ? tr("optional_mix_hint").getString() : menu.kind() == MachineKind.ANALYZER ? tr("sample_kept").getString()
                : tr("source_" + s.storageSource()).getString() + " · " + compact(s.used()) + " / " + compact(s.capacity()) + " u";
        text(g, source, 12, 83, 232, MUTED);
        if (menu.slots.get(2).isActive()) g.text(font, tr("upgrades"), 12, 120, MUTED, false);
        else if (handPreparator()) text(g, tr("hand_powered").getString(), 12, 100, 76, MUTED);
        String summary = handPreparator() ? tr("hand_upgrade_hint").getString() : compact(s.energy()) + " FE";
        if (s.queuedBatches() > 0) summary += " · " + s.queuedBatches() + " " + tr("queued_short").getString();
        if (industrial() && s.structure().formed()) summary += " · " + s.structure().workRate() + "×";
        text(g, summary, 12, 137, 232, MUTED);
    }
    private void storageLabels(GuiGraphicsExtractor g, MachineSnapshot s) {
        if (drive()) {
            g.text(font, tr("cell_slot"), 40, 48, MUTED, false);
            text(g, s.cell().installed() ? s.cell().name() : tr("insert_cell").getString(), 12, 87, 92, PAPER);
            if (s.cell().installed()) text(g, s.cell().readable() ? s.cell().types() + " / " + s.cell().maxTypes() + " types" : tr("cell_unreadable").getString(), 12, 103, 92, MUTED);
        }
        var values = entries();
        int start = drive() ? 110 : 12, nameWidth = drive() ? 83 : 178;
        for (int i = 0; i < ROWS && page * ROWS + i < values.size(); i++) {
            var entry = values.get(page * ROWS + i); int y = 54 + 12 * i;
            text(g, entry.name(), start + 2, y, nameWidth, PAPER);
            String amount = compact(entry.amount());
            g.text(font, amount, 242 - font.width(amount), y, CYAN, false);
        }
        if (values.isEmpty()) text(g, tr(search.getValue().isBlank() ? "empty_storage" : "no_matches").getString(), start + 2, 84, 242 - start, MUTED);
        g.text(font, (page + 1) + "/" + Math.max(1, (values.size() + ROWS - 1) / ROWS), 39, 134, MUTED, false);
        text(g, compact(s.used()) + " u", 100, 134, 68, CYAN);
    }
    private List<Component> recipeDetails(MachineSnapshot s) {
        var p = s.preview(); var lines = new ArrayList<Component>();
        lines.add(Component.literal(p.name().isBlank() ? s.selectedName() : p.name()));
        if (menu.inputRole() && !preparator() && s.sampleBatchSize() > 1)
            lines.add(Component.translatable("screen.rematter.composition_batch", s.sampleBatchSize()));
        lines.add(handPreparator() ? Component.translatable("screen.rematter.crank_count", (p.ticks() + 19) / 20)
                : Component.literal(String.format(Locale.ROOT, "%,d FE · %.1f s", p.energy(), p.ticks() / 20.0)));
        for (var a : p.inputs()) lines.add(Component.literal(a.name() + " · " + a.phase() + ": " + a.required() + " needed / " + a.available() + " stored"));
        for (var a : p.outputs()) lines.add(Component.literal("→ " + a.name() + " · " + a.phase() + ": " + a.required() + " u"));
        if (!p.complete()) lines.add(tr("preview_partial"));
        return lines;
    }
    private List<Component> structureDetails(MachineSnapshot s) {
        var lines = new ArrayList<Component>();
        if (!industrial()) { lines.add(tr("details_hint")); return lines; }
        var structure = s.structure();
        if (structure.width() > 0) lines.add(Component.translatable("screen.rematter.structure_size", structure.width(), structure.depth(), structure.height()));
        if (!structure.formed()) {
            lines.add(Component.translatable("status.rematter." + structure.diagnostic()));
            lines.add(Component.translatable("screen.rematter.help_" + structure.diagnostic()));
            lines.add(tr(menu.kind() == MachineKind.INDUSTRIAL_PREPARATOR ? "preparation_stack" : "reactor_stack"));
        } else {
            lines.add(Component.translatable("screen.rematter.structure_columns", structure.moduleCount()));
            lines.add(Component.translatable("screen.rematter.structure_speed", structure.workRate()));
            lines.add(tr("structure_scaling_hint"));
        }
        return lines;
    }
    private List<Component> storageDetails(MachineSnapshot s) {
        var lines = new ArrayList<Component>();
        lines.add(tr("source_" + s.storageSource()));
        lines.add(Component.literal(String.format(Locale.ROOT, "%,d / %,d u", s.used(), s.capacity())));
        if (s.fluidAmount() > 0) lines.add(Component.literal(s.fluidName() + " · " + s.fluidAmount() + " mB"));
        if (s.queuedBatches() > 0) lines.add(Component.literal(tr("queued").getString() + " " + s.queuedBatches()));
        for (var e : s.entries().stream().limit(8).toList()) lines.add(Component.literal(e.name() + " · " + e.phase() + " · " + e.amount() + " u"));
        if (!s.entriesComplete() || s.entries().size() > 8) lines.add(tr("preview_partial"));
        if (directCell() && !s.cell().installed()) lines.add(tr("direct_cell_hint"));
        return lines;
    }
    private String dimensions(MachineSnapshot s) { var v = s.structure(); return v.width() + "×" + v.depth() + "×" + v.height(); }
    private void text(GuiGraphicsExtractor g, String value, int x, int y, int width, int color) { g.text(font, font.plainSubstrByWidth(value, width), x, y, color, false); }
    private ItemStack outputIcon(MachineSnapshot.Preview p) {
        try {
            Identifier id = Identifier.parse(p.outputId());
            if (p.outputKind().equals("item") && BuiltInRegistries.ITEM.containsKey(id)) return new ItemStack(BuiltInRegistries.ITEM.getValue(id));
            if (p.outputKind().equals("fluid") && BuiltInRegistries.FLUID.containsKey(id)) return new ItemStack(BuiltInRegistries.FLUID.getValue(id).getBucket());
        } catch (RuntimeException ignored) { }
        return ItemStack.EMPTY;
    }
    private static String compact(long v) {
        if (v >= 1_000_000_000_000L) return String.format(Locale.ROOT, "%.1e", (double) v);
        if (v >= 1_000_000_000) return String.format(Locale.ROOT, "%.1fB", v / 1_000_000_000.0);
        if (v >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", v / 1_000_000.0);
        if (v >= 10_000) return String.format(Locale.ROOT, "%.1fk", v / 1000.0);
        return Long.toString(v);
    }
    private final class ConsoleButton extends Button {
        ConsoleButton(Builder builder) { super(builder); }
        @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
            boolean focus = active && isHoveredOrFocused();
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), focus ? CYAN : EDGE);
            g.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, focus ? 0xff29434a : PANEL);
            g.text(font, getMessage(), getX() + (getWidth() - font.width(getMessage())) / 2, getY() + (getHeight() - 8) / 2, active ? PAPER : MUTED, false);
        }
    }
}
