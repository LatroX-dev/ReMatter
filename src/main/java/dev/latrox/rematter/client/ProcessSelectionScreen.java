package dev.latrox.rematter.client;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.content.MachineChoices;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.network.ClientChemistry;
import dev.latrox.rematter.network.SelectProcessPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** A searchable GUI layer that leaves the machine container open underneath. */
public final class ProcessSelectionScreen extends Screen {
    private final MachineMenu menu;
    private ChemistryRegistry registry;
    private List<MachineChoices.Choice> choices = List.of(), filtered = List.of();
    private final List<Button> rows = new ArrayList<>();
    private EditBox search;
    private Button previous, next;
    private int left, top, panelWidth, panelHeight, page, pageSize;

    public ProcessSelectionScreen(MachineMenu menu) {
        super(Component.literal("Select process"));
        this.menu = menu;
    }

    @Override protected void init() {
        panelWidth = Math.min(380, width - 20);
        panelHeight = Math.min(300, height - 20);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        pageSize = Math.max(1, (panelHeight - 90) / 20);
        String query = search == null ? "" : search.getValue();
        search = addRenderableWidget(new EditBox(font, left + 12, top + 28, panelWidth - 24, 18, Component.literal("Search processes")));
        search.setMaxLength(128);
        search.setHint(Component.literal("Search by name or mod:item"));
        search.setValue(query);
        search.setResponder(value -> { page = 0; filter(); });
        rows.clear();
        for (int i = 0; i < pageSize; i++) {
            final int row = i;
            rows.add(addRenderableWidget(Button.builder(Component.empty(), button -> select(row))
                    .bounds(left + 12, top + 52 + i * 20, panelWidth - 24, 18).build()));
        }
        previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> { page--; refreshRows(); })
                .bounds(left + 12, top + panelHeight - 28, 24, 18).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), button -> { page++; refreshRows(); })
                .bounds(left + panelWidth - 106, top + panelHeight - 28, 24, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(left + panelWidth - 76, top + panelHeight - 28, 64, 18).build());
        loadChoices();
        setInitialFocus(search);
    }

    private void loadChoices() {
        registry = ClientChemistry.current();
        int kind = menu.snapshot().kind();
        choices = kind >= 0 && kind < MachineKind.values().length
                ? MachineChoices.forKind(MachineKind.values()[kind], registry) : List.of();
        filter();
    }

    private void filter() {
        String query = search.getValue().strip().toLowerCase(Locale.ROOT);
        filtered = choices.stream().filter(choice -> choice.name().toLowerCase(Locale.ROOT).contains(query)
                || choice.id().toLowerCase(Locale.ROOT).contains(query)).toList();
        refreshRows();
    }

    private void refreshRows() {
        page = Math.clamp(page, 0, pages() - 1);
        for (int i = 0; i < rows.size(); i++) {
            Button row = rows.get(i);
            int index = page * pageSize + i;
            row.visible = row.active = index < filtered.size();
            if (row.visible) {
                var choice = filtered.get(index);
                String name = (choice.id().equals(menu.snapshot().selectedId()) ? "• " : "") + choice.name();
                row.setMessage(Component.literal(font.plainSubstrByWidth(name, panelWidth - 40)));
                row.setTooltip(Tooltip.create(Component.literal(choice.name() + "\n" + choice.id())));
            }
        }
        previous.active = page > 0;
        next.active = page + 1 < pages();
    }

    private int pages() { return Math.max(1, (filtered.size() + pageSize - 1) / pageSize); }

    private void select(int row) {
        int index = page * pageSize + row;
        if (minecraft.player != null && minecraft.player.containerMenu == menu && index < filtered.size()) {
            ClientPacketDistributor.sendToServer(new SelectProcessPayload(menu.containerId, filtered.get(index).id()));
            onClose();
        }
    }

    @Override public void tick() {
        if (minecraft.player == null || minecraft.player.containerMenu != menu) { onClose(); return; }
        if (registry != ClientChemistry.current()) loadChoices();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xff394b51);
        graphics.fill(left + 1, top + 1, left + panelWidth - 1, top + panelHeight - 1, 0xff10191e);
        graphics.text(font, title, left + 12, top + 11, 0xffe4efec, false);
        if (filtered.isEmpty()) graphics.text(font, "No matching processes", left + 12, top + 57, 0xff9eb2b6, false);
        graphics.text(font, (page + 1) + " / " + pages() + "  ·  " + filtered.size() + " results", left + 43, top + panelHeight - 23, 0xff9eb2b6, false);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
