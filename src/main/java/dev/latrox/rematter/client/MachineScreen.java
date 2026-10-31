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
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** A compact instrument panel. All quantities and operation states come from the server. */
public final class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private static final int INK = 0xff10191e, PANEL = 0xff17252c, EDGE = 0xff394b51;
    private static final int PAPER = 0xffe4efec, MUTED = 0xff9eb2b6, CYAN = 0xff67e1ce, COPPER = 0xffcc9169;
    private static final int PAGE_SIZE = 5;
    private EditBox search;
    private Button run, automation, previousChoice, nextChoice, previousPage, nextPage;
    private int page;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 292, 256);
        inventoryLabelX = 65;
        inventoryLabelY = 163;
    }

    @Override protected void init() {
        super.init();
        search = new EditBox(font, leftPos + 119, topPos + 28, 157, 12, Component.translatable("screen.rematter.search"));
        search.setHint(Component.translatable("screen.rematter.search"));
        search.setBordered(false);
        search.setTextColor(PAPER);
        search.setResponder(value -> page = 0);
        addRenderableWidget(search);
        previousChoice = control("<", 8, 122, 20, 16, button -> send(0), "Previous process");
        nextChoice = control(">", 80, 122, 20, 16, button -> send(1), "Next process");
        previousPage = control("<", 114, 108, 20, 16, button -> page = Math.max(0, page - 1), "Previous substances");
        nextPage = control(">", 260, 108, 20, 16, button -> page++, "Next substances");
        run = control("", 8, 143, 94, 18, button -> send(2), "Pause or resume processing");
        automation = control("", 174, 143, 110, 18, button -> send(3), "Allow external item, fluid and chemical transfers");
    }

    private Button control(String text, int x, int y, int width, int height, Button.OnPress action, String hint) {
        return addRenderableWidget(Button.builder(Component.literal(text), action)
                .bounds(leftPos + x, topPos + y, width, height).tooltip(Tooltip.create(Component.literal(hint)))
                .build(ConsoleButton::new));
    }

    private void send(int action) {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
    }

    private List<MachineSnapshot.Entry> entries() {
        String query = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT);
        return menu.snapshot().entries().stream().filter(entry -> entry.name().toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        var state = menu.snapshot();
        int pages = Math.max(1, (entries().size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.clamp(page, 0, pages - 1);
        previousPage.active = page > 0;
        nextPage.active = page + 1 < pages;
        previousChoice.active = nextChoice.active = state.choices().size() > 1;
        run.active = state.kind() != MachineKind.STORAGE.ordinal() && state.kind() != MachineKind.TERMINAL.ordinal();
        run.setMessage(Component.translatable(state.running() ? "screen.rematter.pause" : "screen.rematter.run"));
        automation.setMessage(Component.translatable(state.automation() ? "screen.rematter.automation_on" : "screen.rematter.automation_off"));
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int mx = mouseX - leftPos, my = mouseY - topPos;
        if (mx >= 12 && mx < 101 && my >= 26 && my < 48) {
            graphics.setTooltipForNextFrame(Component.literal(String.format(Locale.ROOT, "%,d / 1,000,000 FE", state.energy())), mouseX, mouseY);
        } else if (state.kind() != MachineKind.ANALYZER.ordinal() && mx >= 112 && mx < 282 && my >= 127 && my < 139) {
            graphics.setTooltipForNextFrame(Component.literal(String.format(Locale.ROOT, "%,d / %,d stored units", state.used(), state.capacity())), mouseX, mouseY);
        } else if (mx >= 10 && mx < 103 && my >= 110 && my < 121 && !state.choices().isEmpty()) {
            graphics.setTooltipForNextFrame(Component.literal(state.choices().get(Math.floorMod(state.selected(), state.choices().size()))), mouseX, mouseY);
        } else if (mx >= 114 && mx < 282 && my >= 46 && my < 106) {
            int line = (my - 46) / 12;
            int index = page * PAGE_SIZE + line;
            List<MachineSnapshot.Entry> values = entries();
            if (index < values.size()) {
                var entry = values.get(index);
                graphics.setTooltipForNextFrame(Component.literal(entry.name() + " · " + entry.phase() + " · "
                        + String.format(Locale.ROOT, "%,d units", entry.amount())), mouseX, mouseY);
            }
        }
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        graphics.fill(x + 3, y + 3, x + imageWidth + 3, y + imageHeight + 3, 0x88000000);
        graphics.fill(x, y, x + imageWidth, y + imageHeight, EDGE);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, PANEL);
        graphics.fill(x + 2, y + 2, x + imageWidth - 2, y + 18, INK);
        graphics.fill(x + 8, y + 18, x + 55, y + 19, COPPER);
        graphics.fill(x + 55, y + 18, x + 284, y + 19, EDGE);
        panel(graphics, x + 6, y + 22, 100, 118);
        panel(graphics, x + 110, y + 22, 176, 118);
        graphics.fill(x + 114, y + 25, x + 281, y + 42, 0xff22343b);
        graphics.fill(x + 114, y + 41, x + 281, y + 42, EDGE);
        for (int i = 0; i < PAGE_SIZE; i++) {
            int lineY = y + 46 + i * 12;
            graphics.fill(x + 114, lineY, x + 282, lineY + 11, i % 2 == 0 ? 0xff1a2b32 : INK);
        }
        panel(graphics, x + 61, y + 170, 168, 82);
        for (int i = 0; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            int color = i == 0 ? CYAN : i == 1 ? COPPER : i == 2 ? 0xffa99aca : EDGE;
            graphics.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, color);
            graphics.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, INK);
            graphics.fill(x + slot.x + 1, y + slot.y + 1, x + slot.x + 15, y + slot.y + 2, 0xff2e3d43);
        }
        MachineSnapshot state = menu.snapshot();
        segmented(graphics, x + 14, y + 41, 84, 6, Math.clamp(state.energy() / 1_000_000.0, 0, 1), CYAN);
        int statusColor = state.status().equals("working") ? CYAN : state.status().equals("ready") || state.status().equals("analyzed") ? COPPER : MUTED;
        graphics.fill(x + 14, y + 53, x + 17, y + 56, statusColor);
        graphics.fill(x + 40, y + 71, x + 63, y + 77, 0xff2e424a);
        if (state.duration() > 0) {
            int progress = (int) Math.clamp(state.progress() * 23L / state.duration(), 0, 23);
            graphics.fill(x + 40, y + 72, x + 40 + progress, y + 76, CYAN);
        }
        if (state.status().equals("working")) {
            int pulse = (int) ((System.nanoTime() / 70_000_000L) % 20);
            graphics.fill(x + 40 + pulse, y + 73, x + 43 + pulse, y + 75, PAPER);
        }
        graphics.fill(x + 63, y + 72, x + 65, y + 76, COPPER);
        double fullness = state.capacity() <= 0 ? 0 : Math.clamp((double) state.used() / state.capacity(), 0, 1);
        if (state.kind() != MachineKind.ANALYZER.ordinal()) {
            graphics.fill(x + 114, y + 136, x + 282, y + 138, EDGE);
            graphics.fill(x + 114, y + 136, x + 114 + (int) (168 * fullness), y + 138, fullness > .95 ? COPPER : CYAN);
        }
        panel(graphics, x + 8, y + 174, 46, 76);
        int fluidHeight = (int) Math.clamp(state.fluidAmount() * 37L / 16_000, 0, 37);
        graphics.fill(x + 13, y + 188, x + 18, y + 227, EDGE);
        graphics.fill(x + 14, y + 226 - fluidHeight, x + 17, y + 226, state.fluidName().equals("Lava") ? 0xffeea16b : 0xff7ecce8);
        for (int i = 0; i < 4; i++) graphics.fill(x + 241, y + 184 + i * 5, x + 276, y + 186 + i * 5, INK);
        graphics.fill(x + 242, y + 242, x + 275, y + 243, COPPER);
        for (int[] bolt : new int[][]{{3, 3}, {286, 3}, {3, 250}, {286, 250}}) {
            graphics.fill(x + bolt[0], y + bolt[1], x + bolt[0] + 3, y + bolt[1] + 3, MUTED);
            graphics.fill(x + bolt[0] + 1, y + bolt[1] + 1, x + bolt[0] + 2, y + bolt[1] + 2, INK);
        }
    }

    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        MachineSnapshot state = menu.snapshot();
        graphics.text(font, font.plainSubstrByWidth(title.getString(), 245), 9, 7, PAPER, false);
        graphics.text(font, "RM", 268, 7, COPPER, false);
        graphics.text(font, playerInventoryTitle, 65, 163, MUTED, false);
        graphics.text(font, compact(state.energy()) + " FE", 14, 28, PAPER, false);
        graphics.text(font, Component.translatable("status.rematter." + state.status()), 22, 51, MUTED, false);
        graphics.text(font, Component.translatable("screen.rematter.input"), 12, 84, MUTED, false);
        graphics.text(font, Component.translatable("screen.rematter.output"), 66, 84, MUTED, false);
        if (!state.choices().isEmpty()) {
            int selected = Math.floorMod(state.selected(), state.choices().size());
            graphics.text(font, font.plainSubstrByWidth(state.choices().get(selected), 90), 11, 112, PAPER, false);
            String counter = (selected + 1) + "/" + state.choices().size();
            graphics.text(font, counter, 54 - font.width(counter) / 2, 126, MUTED, false);
        }
        List<MachineSnapshot.Entry> values = entries();
        int pages = Math.max(1, (values.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.clamp(page, 0, pages - 1);
        for (int line = 0; line < PAGE_SIZE && page * PAGE_SIZE + line < values.size(); line++) {
            var entry = values.get(page * PAGE_SIZE + line);
            int rowY = 47 + line * 12;
            int color = phaseColor(entry.phase());
            graphics.fill(115, rowY + 1, 117, rowY + 7, color);
            graphics.text(font, font.plainSubstrByWidth(entry.name(), 95), 122, rowY, PAPER, false);
            String amount = compact(entry.amount());
            graphics.text(font, amount, 279 - font.width(amount), rowY, color, false);
        }
        if (values.isEmpty()) graphics.text(font, search.getValue().isBlank() ? "No substances" : "No matches", 122, 70, MUTED, false);
        String pagesText = (page + 1) + " / " + pages;
        graphics.text(font, pagesText, 198 - font.width(pagesText) / 2, 112, MUTED, false);
        String storage = state.kind() == MachineKind.ANALYZER.ordinal() ? "Sample composition" : compact(state.used()) + " / " + compact(state.capacity()) + " u";
        graphics.text(font, storage, 114, 127, MUTED, false);
        graphics.text(font, "TANK", 13, 178, MUTED, false);
        graphics.text(font, compact(state.fluidAmount()), 22, 191, PAPER, false);
        graphics.text(font, "mB", 22, 203, MUTED, false);
        if (!state.fluidName().isEmpty()) graphics.text(font, font.plainSubstrByWidth(state.fluidName(), 39), 12, 236, MUTED, false);
        graphics.text(font, "ReMatter", 238, 232, MUTED, false);
    }

    private static int phaseColor(String phase) {
        return switch (phase) { case "gas" -> 0xffbea9e2; case "liquid" -> 0xff7ecce8; default -> COPPER; };
    }
    private static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, EDGE);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, INK);
    }
    private static void segmented(GuiGraphicsExtractor graphics, int x, int y, int width, int height, double amount, int color) {
        int fill = (int) (width * amount);
        for (int offset = 0; offset < width; offset += 7) {
            graphics.fill(x + offset, y, x + Math.min(width, offset + 5), y + height, EDGE);
            if (offset < fill) graphics.fill(x + offset, y + 1, x + Math.min(fill, Math.min(width, offset + 5)), y + height - 1, color);
        }
    }
    private static String compact(long value) {
        if (value >= 1_000_000_000_000_000L) return String.format(Locale.ROOT, "%.1e", (double) value);
        if (value >= 1_000_000_000_000L) return String.format(Locale.ROOT, "%.1fT", value / 1_000_000_000_000.0);
        if (value >= 1_000_000_000L) return String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000.0);
        if (value >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        if (value >= 10_000) return String.format(Locale.ROOT, "%.1fk", value / 1000.0);
        return Long.toString(value);
    }

    private final class ConsoleButton extends Button {
        ConsoleButton(Builder builder) { super(builder); }
        @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            int edge = active && isHoveredOrFocused() ? CYAN : active ? EDGE : 0xff27343a;
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), edge);
            graphics.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, active && isHoveredOrFocused() ? 0xff29434a : PANEL);
            graphics.text(font, getMessage(), getX() + (getWidth() - font.width(getMessage())) / 2,
                    getY() + (getHeight() - 8) / 2, active ? PAPER : 0xff70858d, false);
        }
    }
}
