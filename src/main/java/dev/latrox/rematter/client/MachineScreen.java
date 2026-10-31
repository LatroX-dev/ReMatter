package dev.latrox.rematter.client;

import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.content.MachineSnapshot;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** A menu-driven terminal; only server-supplied display data is rendered. */
public final class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private EditBox search;
    private Button run;
    private Button automation;
    private int page;
    private static final int PAGE_SIZE = 5;
    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 292, 256);
        inventoryLabelX = 65;
        inventoryLabelY = 163;
    }
    @Override protected void init() {
        super.init();
        search = new EditBox(font, leftPos + 114, topPos + 25, 166, 16, Component.translatable("screen.rematter.search"));
        search.setHint(Component.translatable("screen.rematter.search"));
        search.setResponder(value -> page = 0);
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.literal("<"), button -> send(0)).bounds(leftPos + 8, topPos + 120, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> send(1)).bounds(leftPos + 80, topPos + 120, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal("<"), button -> page = Math.max(0, page - 1)).bounds(leftPos + 114, topPos + 108, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> page++).bounds(leftPos + 260, topPos + 108, 20, 16).build());
        run = addRenderableWidget(Button.builder(Component.translatable("screen.rematter.pause"), button -> send(2)).bounds(leftPos + 8, topPos + 143, 92, 18).build());
        automation = addRenderableWidget(Button.builder(Component.empty(), button -> send(3)).bounds(leftPos + 174, topPos + 143, 110, 18).build());
    }
    private void send(int action) {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        var state = menu.snapshot();
        run.setMessage(Component.translatable(state.running() ? "screen.rematter.pause" : "screen.rematter.run"));
        automation.setMessage(Component.translatable(state.automation() ? "screen.rematter.automation_on" : "screen.rematter.automation_off"));
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xff17272d);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xff293c43);
        graphics.fill(x + 6, y + 20, x + 106, y + 140, 0xff17272d);
        graphics.fill(x + 110, y + 20, x + 286, y + 140, 0xff17272d);
        for (var slot : menu.slots) {
            graphics.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xff0e1b20);
            graphics.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xff40565e);
        }
        MachineSnapshot state = menu.snapshot();
        graphics.fill(x + 14, y + 42, x + 98, y + 47, 0xff40565e);
        graphics.fill(x + 14, y + 42, x + 14 + (int) Math.min(84, state.energy() * 84L / 1_000_000), y + 47, 0xff53d9bf);
        if (state.duration() > 0) {
            graphics.fill(x + 39, y + 72, x + 65, y + 77, 0xff40565e);
            graphics.fill(x + 39, y + 72, x + 39 + Math.min(26, state.progress() * 26 / state.duration()), y + 77, 0xff53d9bf);
        }
    }
    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        MachineSnapshot state = menu.snapshot();
        graphics.text(font, title, 8, 7, 0xffe6f4f3, false);
        graphics.text(font, playerInventoryTitle, 65, 163, 0xffc2d4d9, false);
        graphics.text(font, compact(state.energy()) + " FE", 14, 28, 0xffc2d4d9, false);
        graphics.text(font, Component.translatable("status.rematter." + state.status()), 14, 51, 0xff83cbb9, false);
        graphics.text(font, Component.translatable("screen.rematter.input"), 12, 84, 0xffc2d4d9, false);
        graphics.text(font, Component.translatable("screen.rematter.output"), 66, 84, 0xffc2d4d9, false);
        if (!state.choices().isEmpty()) {
            String selected = state.choices().get(Math.floorMod(state.selected(), state.choices().size()));
            graphics.text(font, font.plainSubstrByWidth(selected, 88), 12, 111, 0xffe6f4f3, false);
        }
        List<MachineSnapshot.Entry> entries = state.entries().stream()
                .filter(entry -> entry.name().toLowerCase(Locale.ROOT).contains(search.getValue().toLowerCase(Locale.ROOT)))
                .toList();
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(page, pages - 1);
        for (int line = 0; line < PAGE_SIZE && page * PAGE_SIZE + line < entries.size(); line++) {
            var entry = entries.get(page * PAGE_SIZE + line);
            int y = 47 + line * 12;
            int color = switch (entry.phase()) { case "gas" -> 0xffd5c0f2; case "liquid" -> 0xff80cfff; default -> 0xffe6f4f3; };
            graphics.text(font, font.plainSubstrByWidth(entry.name(), 110), 115, y, color, false);
            String amount = compact(entry.amount());
            graphics.text(font, amount, 280 - font.width(amount), y, color, false);
        }
        graphics.text(font, (page + 1) + " / " + pages, 183, 112, 0xffc2d4d9, false);
        String storage = state.kind() == MachineKind.ANALYZER.ordinal() ? "Sample" : compact(state.used()) + " / " + compact(state.capacity()) + " u";
        graphics.text(font, storage, 114, 130, 0xffc2d4d9, false);
        if (state.fluidAmount() > 0) {
            graphics.text(font, state.fluidName(), 8, 190, 0xff80cfff, false);
            graphics.text(font, compact(state.fluidAmount()), 8, 202, 0xff80cfff, false);
            graphics.text(font, "mB", 8, 214, 0xff80cfff, false);
        }
    }
    private static String compact(long value) {
        if (value >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        if (value >= 10_000) return String.format(Locale.ROOT, "%.1fk", value / 1000.0);
        return Long.toString(value);
    }
}
