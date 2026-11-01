package dev.latrox.rematter.client;

import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.content.MachineSnapshot;
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

/** Role-specific views over the same stable server inventory. */
public final class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private static final int INK = 0xff10191e, PANEL = 0xff1b2a31, EDGE = 0xff3b5058;
    private static final int PAPER = 0xffe4efec, MUTED = 0xff9eb2b6, CYAN = 0xff67e1ce, COPPER = 0xffe1a579;
    private static final int CERAMIC = 0xffe1dac7, CERAMIC_EDGE = 0xffaaa695, LABEL = 0xff293238;
    private static final int ROWS = 6;
    private EditBox search;
    private Button pause, automation, choose, once, repeat, settings, previous, next, crank;
    private int page;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 320, 240);
    }

    @Override protected void init() {
        super.init();
        String query = search == null ? "" : search.getValue();
        search = addRenderableWidget(new EditBox(font, leftPos + 16, topPos + 44, 288, 14, tr("search")));
        search.setHint(tr("search")); search.setMaxLength(128); search.setBordered(false);
        search.setTextColor(PAPER); search.setValue(query); search.setResponder(value -> page = 0);
        pause = control("", 254, 23, 58, 16, b -> send(2), "pause_hint");
        choose = control("", 8, 136, 94, 18, b -> minecraft.pushGuiLayer(new ProcessSelectionScreen(menu)), "choose_hint");
        once = control("", 108, 136, 72, 18, b -> send(4), "once_hint");
        repeat = control("", 186, 136, 92, 18, b -> send(5), "repeat_hint");
        settings = control("settings_short", 284, 136, 28, 18, b -> {
            menu.setSettingsOpen(!menu.settingsOpen()); page = 0;
        }, "settings_hint");
        automation = control("", 120, 69, 184, 20, b -> send(3), "automation_hint");
        previous = control("previous", 8, 136, 24, 18, b -> page = Math.max(0, page - 1), "previous_hint");
        next = control("next", 96, 136, 24, 18, b -> page++, "next_hint");
        crank = control("crank", 8, 136, 94, 18, b -> send(6), "crank_hint");
        updateWidgets();
    }

    private static Component tr(String key) { return Component.translatable("screen.rematter." + key); }
    private Button control(String key, int x, int y, int width, int height, Button.OnPress action, String hint) {
        return addRenderableWidget(Button.builder(key.isEmpty() ? Component.empty() : tr(key), action)
                .bounds(leftPos + x, topPos + y, width, height).tooltip(Tooltip.create(tr(hint))).build(ConsoleButton::new));
    }
    private void send(int action) {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
    }
    private boolean passive() { return !menu.kind().processes(); }
    private boolean drive() { return menu.kind() == MachineKind.DRIVE; }
    private boolean directCell() { return menu.kind() == MachineKind.DECOMPOSER || menu.kind() == MachineKind.SYNTHESIZER; }
    private boolean preparator() { return menu.kind() == MachineKind.PREPARATOR || handPreparator() || menu.kind() == MachineKind.INDUSTRIAL_PREPARATOR; }
    private boolean handPreparator() { return menu.kind() == MachineKind.HAND_PREPARATOR; }
    private Component statusText(String status) {
        if (status.equals("structure") && menu.kind() == MachineKind.INDUSTRIAL_PREPARATOR) return tr("preparation_structure");
        if (status.equals("structure") && menu.kind() == MachineKind.INDUSTRIAL_REACTOR) return tr("reactor_structure");
        return Component.translatable("status.rematter." + status);
    }
    private static boolean working(String status) { return List.of("working", "grinding", "sieving", "collecting", "dosing", "mixing", "packing").contains(status); }
    private boolean selecting() {
        return menu.kind() == MachineKind.SYNTHESIZER || menu.kind() == MachineKind.ELECTROLYZER
                || menu.kind() == MachineKind.REACTOR || menu.kind() == MachineKind.INDUSTRIAL_REACTOR;
    }
    private List<MachineSnapshot.Entry> entries() {
        String query = search == null ? "" : search.getValue().strip().toLowerCase(Locale.ROOT);
        return menu.snapshot().entries().stream().filter(e -> e.name().toLowerCase(Locale.ROOT).contains(query)
                || e.id().toLowerCase(Locale.ROOT).contains(query) || e.phase().contains(query)).toList();
    }
    private void updateWidgets() {
        var s = menu.snapshot();
        boolean ready = s.containerId() == menu.containerId;
        boolean detail = menu.settingsOpen();
        search.visible = passive() && !detail;
        search.active = search.visible;
        search.setX(leftPos + (drive() ? 124 : 16));
        search.setWidth(drive() ? 180 : 288);
        pause.visible = ready && !passive() && !handPreparator();
        pause.active = !s.status().equals("selection_unavailable") && (!selecting() || s.repeat() || s.queuedBatches() > 0);
        pause.setMessage(tr(s.running() ? "pause" : menu.kind() == MachineKind.DECOMPOSER || preparator() ? "start" : "resume"));
        choose.visible = once.visible = repeat.visible = ready && selecting() && !detail;
        choose.active = s.choiceCount() > 0;
        choose.setMessage(tr(menu.kind() == MachineKind.SYNTHESIZER ? "choose_product" : "choose_process"));
        once.setMessage(tr(menu.kind() == MachineKind.SYNTHESIZER ? "make_once" : "run_once"));
        once.active = s.selected() >= 0 && !s.preview().id().isEmpty() && s.queuedBatches() < 64;
        repeat.active = s.selected() >= 0;
        repeat.setMessage(tr(s.repeat() ? "repeat_on" : "repeat_off"));
        settings.setMessage(tr(detail ? "back_short" : "settings_short"));
        settings.active = ready;
        crank.visible = ready && handPreparator() && !detail;
        crank.active = ready && !s.preview().id().isEmpty();
        automation.visible = detail;
        automation.setMessage(tr(s.automation() ? "automation_on" : "automation_off"));
        int pages = Math.max(1, (entries().size() + ROWS - 1) / ROWS);
        page = Math.clamp(page, 0, pages - 1);
        previous.visible = next.visible = passive() && !detail;
        previous.active = page > 0;
        next.active = page + 1 < pages;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        updateWidgets();
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        var s = menu.snapshot();
        int mx = mouseX - leftPos, my = mouseY - topPos;
        boolean onCellSlot = menu.slots.get(3).isActive() && mx >= 15 && mx < 33 && my >= 179 && my < 197;
        if (mx >= 8 && mx < 140 && my >= 158 && my < 236 && !onCellSlot) {
            List<Component> lines = new java.util.ArrayList<>();
            if (preparator()) {
                lines.add(tr("secondary_ingredient"));
                lines.add(tr("preparation_input_hint"));
                if (menu.slots.get(3).hasItem()) lines.add(menu.slots.get(3).getItem().getHoverName());
                g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
                return;
            }
            if (menu.kind() == MachineKind.ANALYZER) {
                lines.add(tr("sample_storage_optional"));
                lines.add(tr("analyzer_hint"));
                g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
                return;
            }
            lines.add(tr("source_" + s.storageSource()));
            lines.add(Component.literal(String.format(Locale.ROOT, "%,d / %,d u", s.used(), s.capacity())));
            if (directCell()) {
                lines.add(tr(s.cell().installed() ? "local_cell_hint" : "direct_cell_hint"));
                if (s.cell().installed()) lines.add(Component.literal(s.cell().name()));
            }
            if (s.fluidAmount() > 0) lines.add(Component.literal(s.fluidName() + " · " + String.format(Locale.ROOT, "%,d mB", s.fluidAmount())));
            if (s.queuedBatches() > 0) lines.add(Component.literal(tr("queued").getString() + " " + s.queuedBatches()));
            for (var entry : s.entries().stream().limit(5).toList())
                lines.add(Component.literal(entry.name() + " · " + entry.phase() + " · " + entry.amount() + " u"));
            if (s.entries().size() > 5 || !s.entriesComplete()) lines.add(tr("preview_partial"));
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        } else if (mx >= 8 && mx < 250 && my >= 23 && my < 38) {
            g.setTooltipForNextFrame(statusText(s.status()), mouseX, mouseY);
        } else if (!passive() && !menu.settingsOpen() && mx >= 12 && mx < 104 && my >= 98 && my < 127) {
            g.setTooltipForNextFrame(handPreparator() ? tr("crank_hint") : Component.literal(String.format(Locale.ROOT, "%,d / 1,000,000 FE", s.energy())), mouseX, mouseY);
        } else if (passive() && !menu.settingsOpen() && mx >= (drive() ? 120 : 12) && mx < 308 && my >= 61 && my < 133) {
            var rows = entries(); int index = page * ROWS + (my - 61) / 12;
            if (index < rows.size()) {
                var e = rows.get(index);
                g.setTooltipForNextFrame(Component.literal(e.name() + " · " + e.phase() + "\n"
                        + String.format(Locale.ROOT, "%,d stored units", e.amount())), mouseX, mouseY);
            }
        } else if (!passive() && !menu.settingsOpen() && mx >= 120 && mx < 308 && my >= 42 && my < 131) {
            List<Component> lines = new java.util.ArrayList<>();
            var p = s.preview();
            lines.add(Component.literal(p.name().isBlank() ? s.selectedName() : p.name()));
            lines.add(handPreparator() ? Component.translatable("screen.rematter.crank_count", (p.ticks() + 19) / 20) : Component.literal(String.format(Locale.ROOT, "%,d FE · %.1f s", p.energy(), p.ticks() / 20.0)));
            for (var a : p.inputs())
                lines.add(Component.literal(a.name() + " · " + a.phase() + ": " + a.required() + " needed / " + a.available() + " stored"));
            for (var a : p.outputs())
                lines.add(Component.literal("→ " + a.name() + " · " + a.phase() + ": " + a.required() + " u"));
            if (!p.complete()) lines.add(tr("preview_partial"));
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        g.fill(x + 3, y + 3, x + imageWidth + 3, y + imageHeight + 3, 0x88000000);
        g.fill(x, y, x + imageWidth, y + imageHeight, CERAMIC_EDGE);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, CERAMIC);
        g.fill(x + 4, y + 22, x + imageWidth - 4, y + imageHeight - 4, INK);
        g.fill(x + 8, y + 18, x + 61, y + 20, accent());
        if ((passive() && !drive()) || menu.settingsOpen()) panel(g, x + 8, y + 40, 304, 94);
        else { panel(g, x + 8, y + 40, 102, 92); panel(g, x + 116, y + 40, 196, 92); }
        if (passive() && !menu.settingsOpen()) {
            int start = drive() ? 120 : 12;
            g.fill(x + start, y + 42, x + 308, y + 58, PANEL);
            for (int i = 0; i < ROWS; i++) g.fill(x + start, y + 61 + 12 * i, x + 308, y + 72 + 12 * i, i % 2 == 0 ? PANEL : INK);
        }
        panel(g, x + 8, y + 158, 132, 78);
        panel(g, x + 145, y + 156, 167, 80);
        for (int i = 0; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            if (!slot.isActive()) continue;
            int color = i == 0 || i == 3 ? CYAN : i == 1 ? COPPER : i == 2 ? 0xffb9a4d5 : EDGE;
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, color);
            g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, INK);
        }
        var s = menu.snapshot();
        if (!passive() && !menu.settingsOpen()) {
            if (!handPreparator()) {
                g.fill(x + 15, y + 113, x + 103, y + 118, EDGE);
                g.fill(x + 15, y + 114, x + 15 + (int) (88L * s.energy() / 1_000_000), y + 117, CYAN);
            }
            g.fill(x + 43, y + 75, x + 73, y + 79, EDGE);
            if ((working(s.status()) || handPreparator()) && s.duration() > 0)
                g.fill(x + 43, y + 75, x + 43 + (int) Math.clamp(30L * s.progress() / s.duration(), 0, 30), y + 79, CYAN);
            g.fill(x + 71, y + 73, x + 74, y + 81, accent());
            if (!menu.inputRole()) {
                ItemStack icon = outputIcon(s.preview());
                if (!icon.isEmpty()) g.fakeItem(icon, x + 18, y + 70);
                else phaseMark(g, x + 18, y + 71, accent());
            }
            if (!menu.slots.get(1).isActive()) phaseMark(g, x + 81, y + 71, COPPER);
        }
        double fullness = s.capacity() == 0 ? 0 : Math.clamp((double) s.used() / s.capacity(), 0, 1);
        if (menu.kind() != MachineKind.ANALYZER && !preparator()) {
            g.fill(x + 16, y + 228, x + 132, y + 231, EDGE);
            g.fill(x + 16, y + 228, x + 16 + (int) (116 * fullness), y + 231, fullness > .95 ? COPPER : CYAN);
        }
        if (passive() && !menu.settingsOpen()) {
            g.fill(x + 136, y + 147, x + 274, y + 150, EDGE);
            g.fill(x + 136, y + 147, x + 136 + (int) (138 * fullness), y + 150, fullness > .95 ? COPPER : CYAN);
        }
    }

    @Override protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        var s = menu.snapshot();
        g.text(font, font.plainSubstrByWidth(title.getString(), 277), 9, 7, LABEL, false);
        g.text(font, "RM", 295, 7, LABEL, false);
        String status = statusText(s.status()).getString();
        g.fill(9, 28, 12, 31, working(s.status()) ? CYAN : COPPER);
        g.text(font, font.plainSubstrByWidth(status, passive() ? 292 : 232), 17, 26, PAPER, false);
        sourceLabels(g, s);
        if (menu.settingsOpen()) { settingsLabels(g, s); return; }
        if (passive()) { storageLabels(g, s); return; }
        g.text(font, tr(preparator() ? "primary_ingredient" : menu.inputRole() ? "input" : "product"), 14, 55, MUTED, false);
        g.text(font, tr(menu.kind() == MachineKind.ANALYZER ? "sample" : "output"), 75, 55, MUTED, false);
        g.text(font, handPreparator() ? tr("hand_powered").getString() : compact(s.energy()) + " FE", 15, 101, PAPER, false);
        String progress = (working(s.status()) || handPreparator()) && s.duration() > 0
                ? Math.clamp(s.progress() * 100L / s.duration(), 0, 100) + "%"
                : (menu.kind() == MachineKind.ANALYZER ? "Sample kept" : "");
        g.text(font, progress, 15, 122, MUTED, false);
        var p = s.preview();
        if (selecting() && (p.outputKind().equals("item") || p.outputKind().equals("fluid"))) {
            String batch = p.outputCount() + (p.outputKind().equals("fluid") ? " mB" : " / batch");
            g.text(font, font.plainSubstrByWidth(batch, 90), 15, 90, MUTED, false);
        }
        String name = p.name().isBlank() ? (s.selectedName().isBlank() ? tr(menu.inputRole() ? "insert_input" : "choose_hint").getString() : s.selectedName()) : p.name();
        g.text(font, font.plainSubstrByWidth(name, 180), 123, 47, PAPER, false);
        if (preparator()) {
            if (!p.id().isEmpty()) g.text(font, handPreparator() ? Component.translatable("screen.rematter.crank_count", (p.ticks() + 19) / 20).getString() : compact(p.energy()) + " FE · " + String.format(Locale.ROOT, "%.1f s", p.ticks() / 20.0), 123, 60, MUTED, false);
            List<String> stages = menu.slots.get(3).hasItem() ? List.of("dosing", "mixing", "packing") : List.of("grinding", "sieving", "collecting");
            for (int i = 0; i < 3; i++) {
                String stage = stages.get(i);
                boolean active = s.status().equals(stage);
                g.fill(123, 79 + i * 16, 128, 84 + i * 16, active ? CYAN : EDGE);
                g.text(font, Component.translatable("status.rematter." + stage), 134, 77 + i * 16, active ? PAPER : MUTED, false);
            }
            if (!p.id().isEmpty()) g.text(font, p.outputCount() + " / batch", 15, 90, MUTED, false);
        } else if (!p.id().isEmpty()) {
            g.text(font, compact(p.energy()) + " FE · " + String.format(Locale.ROOT, "%.1f s", p.ticks() / 20.0), 123, 60, MUTED, false);
            boolean conversion = p.outputKind().equals("matter") && !p.inputs().isEmpty() && !p.outputs().isEmpty();
            List<MachineSnapshot.Amount> amounts = conversion
                    ? java.util.stream.Stream.concat(p.inputs().stream(), p.outputs().stream()).toList()
                    : p.inputs().isEmpty() ? p.outputs() : p.inputs();
            Component heading = conversion ? tr("input_output") : tr(p.inputs().isEmpty() ? "yield" : "needed_stored");
            if (menu.kind() == MachineKind.ANALYZER) {
                heading = s.sampleBatchSize() > 1
                        ? Component.translatable("screen.rematter.composition_batch", s.sampleBatchSize()) : tr("composition");
            } else if (menu.inputRole() && s.sampleBatchSize() > 1) heading = Component.literal("Yield / " + s.sampleBatchSize() + " items");
            g.text(font, heading, 123, 75, MUTED, false);
            for (int i = 0; i < Math.min(3, amounts.size()); i++) {
                var a = amounts.get(i); int row = 88 + i * 12;
                g.fill(123, row + 1, 125, row + 7, phaseColor(a.phase()));
                boolean output = p.inputs().isEmpty() || conversion && i >= p.inputs().size();
                g.text(font, font.plainSubstrByWidth((conversion && output ? "→ " : "") + a.name(), 92), 130, row, PAPER, false);
                String value = compact(a.required()) + (output ? " u" : " / " + compact(a.available()));
                g.text(font, value, 305 - font.width(value), row, !output && a.available() < a.required() ? COPPER : CYAN, false);
            }
            if (amounts.size() > 3 || !p.complete()) g.text(font, "+", 304, 122, MUTED, false);
        }
        if (!selecting()) g.text(font, font.plainSubstrByWidth(tr(menu.kind() == MachineKind.ANALYZER ? "analyzer_hint" : handPreparator() ? "crank_hint_short" : preparator() ? "preparator_hint" : "decomposer_hint").getString(), handPreparator() ? 165 : 270), handPreparator() ? 112 : 9, 141, MUTED, false);
    }

    private void sourceLabels(GuiGraphicsExtractor g, MachineSnapshot s) {
        // Source card and standard 9-column inventory share the lower row so all
        // controls and slots fit Minecraft's minimum 320 x 240 logical viewport.
        if (preparator()) {
            g.text(font, font.plainSubstrByWidth(tr("secondary_ingredient").getString(), 116), 16, 163, PAPER, false);
            ItemStack secondary = menu.slots.get(3).getItem();
            String name = secondary.isEmpty() ? tr("insert_secondary").getString() : secondary.getHoverName().getString();
            wrapped(g, name, 40, 179, 92, 2, MUTED);
            g.text(font, tr("dry_preparation"), 16, 207, CYAN, false);
            return;
        }
        if (menu.kind() == MachineKind.ANALYZER) {
            wrapped(g, tr("sample_storage_optional").getString(), 16, 163, 116, 2, PAPER);
            wrapped(g, tr("analyzer_hint").getString(), 16, 196, 116, 3, MUTED);
            return;
        }
        g.text(font, font.plainSubstrByWidth(tr("source_" + s.storageSource()).getString(), 104), 16, 163, PAPER, false);
        if (!s.entriesComplete()) g.text(font, "+", 130, 163, COPPER, false);
        String detail = directCell() && s.cell().installed() ? s.cell().name()
                : directCell() ? tr("insert_cell").getString() : tr("stored_resources").getString();
        wrapped(g, detail, directCell() ? 40 : 16, 179, directCell() ? 92 : 116, 2, MUTED);
        String amount = compact(s.used()) + " / " + compact(s.capacity()) + " u";
        g.text(font, font.plainSubstrByWidth(amount, 116), 16, 203, CYAN, false);
        if (s.queuedBatches() > 0)
            g.text(font, tr("queued").getString() + " " + s.queuedBatches(), 16, 217, CYAN, false);
        if (s.fluidAmount() > 0) {
            String tank = compact(s.fluidAmount()) + " mB";
            g.text(font, tank, 132 - font.width(tank), 217, COPPER, false);
        }
    }

    private void wrapped(GuiGraphicsExtractor g, String value, int x, int y, int width, int lines, int color) {
        String remaining = value.strip();
        for (int line = 0; line < lines && !remaining.isEmpty(); line++) {
            String part = font.plainSubstrByWidth(remaining, width);
            if (part.isEmpty()) return;
            if (part.length() < remaining.length() && part.lastIndexOf(' ') > 0)
                part = part.substring(0, part.lastIndexOf(' '));
            g.text(font, part, x, y + line * 11, color, false);
            remaining = remaining.substring(part.length()).strip();
        }
    }

    private void storageLabels(GuiGraphicsExtractor g, MachineSnapshot s) {
        if (drive()) {
            var cell = s.cell();
            g.text(font, tr("matter_cell"), 15, 48, PAPER, false);
            String name = cell.installed() ? cell.name() : tr("insert_cell").getString();
            g.text(font, font.plainSubstrByWidth(name, 60), 41, 74, cell.installed() ? PAPER : MUTED, false);
            g.text(font, cell.installed() ? compact(cell.capacity()) + " u" : tr("portable_storage").getString(), 15, 101, MUTED, false);
            if (cell.installed()) {
                String types = cell.readable() ? cell.types() + " / " + cell.maxTypes() + " types" : tr("cell_unreadable").getString();
                g.text(font, font.plainSubstrByWidth(types, 88), 15, 120, cell.readable() ? CYAN : COPPER, false);
            }
        }
        var values = entries();
        for (int i = 0; i < ROWS && page * ROWS + i < values.size(); i++) {
            var e = values.get(page * ROWS + i); int y = 63 + i * 12;
            int x = drive() ? 123 : 15;
            g.fill(x, y + 1, x + 2, y + 7, phaseColor(e.phase()));
            g.text(font, font.plainSubstrByWidth(e.name(), drive() ? 82 : 164), x + 7, y, PAPER, false);
            g.text(font, e.phase(), drive() ? 220 : 193, y, phaseColor(e.phase()), false);
            String amount = compact(e.amount()); g.text(font, amount, 303 - font.width(amount), y, PAPER, false);
        }
        if (values.isEmpty()) {
            String key = drive() && !s.cell().installed() ? "insert_cell_hint" : search.getValue().isBlank() ? "empty_storage" : "no_matches";
            g.text(font, tr(key), drive() ? 125 : 20, 86, MUTED, false);
        }
        g.text(font, (page + 1) + " / " + Math.max(1, (values.size() + ROWS - 1) / ROWS), 43, 141, MUTED, false);
        g.text(font, compact(s.used()) + " / " + compact(s.capacity()) + " u", 136, 136, MUTED, false);
    }

    private void settingsLabels(GuiGraphicsExtractor g, MachineSnapshot s) {
        g.text(font, tr("settings"), 16, 47, PAPER, false);
        g.text(font, tr("automation_explain"), 120, 57, MUTED, false);
        if (handPreparator()) {
            g.text(font, tr("hand_powered"), 16, 94, MUTED, false);
            g.text(font, tr("hand_upgrade_hint"), 120, 100, MUTED, false);
        } else if (menu.kind().processes()) {
            g.text(font, tr("upgrades"), 16, 94, MUTED, false);
            g.text(font, tr("upgrade_hint"), 120, 100, MUTED, false);
            g.text(font, tr("upgrade_limit"), 120, 113, MUTED, false);
        } else {
            g.text(font, tr("passive_hint"), 120, 100, MUTED, false);
            if (s.energy() > 0) g.text(font, compact(s.energy()) + " stored FE", 120, 113, MUTED, false);
        }
        if (menu.slots.get(0).hasItem() || menu.slots.get(1).hasItem()) g.text(font, tr("contents"), 16, 57, MUTED, false);
    }

    private ItemStack outputIcon(MachineSnapshot.Preview p) {
        try {
            Identifier id = Identifier.parse(p.outputId());
            if (p.outputKind().equals("item") && BuiltInRegistries.ITEM.containsKey(id))
                return new ItemStack(BuiltInRegistries.ITEM.getValue(id));
            if (p.outputKind().equals("fluid") && BuiltInRegistries.FLUID.containsKey(id))
                return new ItemStack(BuiltInRegistries.FLUID.getValue(id).getBucket());
        } catch (RuntimeException ignored) { }
        return ItemStack.EMPTY;
    }
    private int accent() { return menu.kind() == MachineKind.SYNTHESIZER ? COPPER : CYAN; }
    private static int phaseColor(String phase) { return switch (phase) { case "gas" -> 0xffbea9e2; case "liquid" -> 0xff7ecce8; default -> COPPER; }; }
    private static void phaseMark(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x + 5, y, x + 10, y + 5, color); g.fill(x + 1, y + 8, x + 6, y + 13, color); g.fill(x + 10, y + 8, x + 15, y + 13, color);
    }
    private static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) { g.fill(x, y, x + w, y + h, EDGE); g.fill(x + 1, y + 1, x + w - 1, y + h - 1, INK); }
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
            int border = active && isHoveredOrFocused() ? CYAN : EDGE;
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), border);
            g.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, active && isHoveredOrFocused() ? 0xff29434a : PANEL);
            g.text(font, getMessage(), getX() + (getWidth() - font.width(getMessage())) / 2, getY() + (getHeight() - 8) / 2, active ? PAPER : MUTED, false);
        }
    }
}
