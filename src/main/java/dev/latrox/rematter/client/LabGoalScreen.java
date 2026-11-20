package dev.latrox.rematter.client;

import dev.latrox.rematter.client.ui.DarkButton;
import dev.latrox.rematter.client.ui.ScrollBar;
import dev.latrox.rematter.client.ui.ScrollModel;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.content.TerminalBatchPlanner.Status;
import dev.latrox.rematter.network.TerminalBatchPlanPayload;
import dev.latrox.rematter.network.TerminalBatchRequestPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Opens from the current Drive or Chemical Processor. Every refresh quotes that machine's own network. */
final class LabGoalScreen extends Screen {
    private static final int LINE = 12;
    private final PinnedLabGoal goal;
    private final MachineMenu menu;
    private final ScrollModel scroll = new ScrollModel(8);
    private final ScrollBar bar = new ScrollBar(scroll);
    private int left, top, panelWidth, panelHeight, ticks;
    private final int accent;
    private TerminalBatchPlanPayload shown;
    private String networkStatus = "";
    private List<FormattedCharSequence> lines = List.of();

    LabGoalScreen(PinnedLabGoal goal, MachineMenu menu) {
        super(Component.translatable("screen.rematter.lab.title"));
        this.goal = goal;
        this.menu = menu;
        accent = UiPalette.machineAccent(menu.kind());
    }

    @Override protected void init() {
        panelWidth = Math.min(320, width - 12);
        panelHeight = Math.min(228, height - 12);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        scroll.setVisibleRows(Math.max(1, (panelHeight - 61) / LINE));
        bar.setBounds(left + panelWidth - 14, top + 25, 5, scroll.visibleRows() * LINE);
        addRenderableWidget(DarkButton.labelIcon(UiIcon.STAR_OUTLINE, Component.translatable("screen.rematter.lab.unpin"),
                Component.translatable("screen.rematter.lab.unpin_hint"), left + 8, top + panelHeight - 25, 110, 18,
                button -> { PinnedLabGoal.clear(); onClose(); })).setAccent(accent);
        addRenderableWidget(DarkButton.icon(UiIcon.BACK, Component.translatable("screen.rematter.lab.back"),
                Component.translatable("screen.rematter.lab.back"), left + panelWidth - 28, top + panelHeight - 25, 20, 18,
                button -> onClose())).setAccent(accent);
        shown = null;
        refresh();
        request();
    }

    private void request() {
        ClientPacketDistributor.sendToServer(new TerminalBatchRequestPayload(menu.containerId,
                TerminalBatchRequestPayload.Action.PIN_PREVIEW, goal.kind(), goal.id(), goal.phase(), goal.quantity(), 0));
    }

    @Override public void tick() {
        if (minecraft.player == null || minecraft.player.containerMenu != menu || !goal.equals(PinnedLabGoal.current())) {
            onClose();
            return;
        }
        if (++ticks % 20 == 0) request();
        if (shown != menu.terminalBatchPlan() || !networkStatus.equals(menu.snapshot().network().status())) refresh();
    }

    private void refresh() {
        shown = menu.terminalBatchPlan();
        networkStatus = menu.snapshot().network().status();
        var text = new ArrayList<Component>();
        text.add(goal.title());
        if (!goal.matches(shown, menu.containerId)) {
            text.add(Component.translatable("screen.rematter.lab.updating"));
        } else if (!networkStatus.equals("ready")) {
            text.add(StatusText.of(networkStatus, 0, 0));
        } else {
            text.add(switch (shown.status()) {
                case REQUIRES_OPEN_AIR -> Component.translatable("status.rematter.requires_open_air");
                case REQUIRES_RESEARCH -> Component.translatable("screen.rematter.terminal.request.status.requires_research");
                default -> Component.translatable("screen.rematter.terminal.request.status." + shown.status().name().toLowerCase(java.util.Locale.ROOT));
            });
            if (shown.status() == Status.REQUIRES_RESEARCH) {
                text.add(shown.targetResearchMissing() ? PinnedLabGoal.unlock(shown.researchSample())
                        : Component.translatable("screen.rematter.lab.step_research"));
            }
            for (var machine : shown.missingMachines()) {
                text.add(Component.translatable("screen.rematter.terminal.request.missing_machine",
                        Component.translatable("block.rematter." + machine.id())));
            }
            if (!shown.steps().isEmpty()) {
                var next = shown.steps().getFirst();
                text.add(Component.translatable("screen.rematter.lab.next", Component.translatable("block.rematter." + next.preferredMachine().id()),
                        SubstanceNames.choice(next.preferredMachine(), next.choiceId()), next.batches()));
                text.add(Component.translatable("screen.rematter.terminal.request.total_energy", Quantities.number(shown.totalEnergy())));
            }
            for (var row : shown.materials()) {
                Component material = Component.translatable("screen.rematter.lab.material", SubstanceNames.substance(row.substanceId(), row.phase(), true),
                        SubstanceNames.phase(row.phase()));
                if (row.required() > 0) {
                    text.add(Component.translatable("screen.rematter.lab.input", material, Quantities.number(row.required()), Quantities.number(row.available())));
                    if (row.required() > row.available()) text.add(Component.translatable("screen.rematter.terminal.request.missing_input",
                            Quantities.number(row.required() - row.available()), material));
                }
                if (row.remainder() > 0) text.add(Component.translatable("screen.rematter.lab.remainder", material, Quantities.number(row.remainder())));
            }
            if (shown.materialCount() > shown.materials().size()) {
                text.add(Component.translatable("screen.rematter.lab.more", shown.materialCount() - shown.materials().size()));
            }
        }
        lines = text.stream().flatMap(line -> font.split(line, panelWidth - 32).stream()).toList();
        scroll.setSize(lines.size());
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int x, int y, float partial) {}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float partial) {
        ScreenChrome.panel(graphics, left, top, panelWidth, panelHeight);
        ScreenChrome.icon(graphics, UiIcon.TARGET, left + 7, top + 1, accent);
        ScreenChrome.text(graphics, font, title.getString(), left + 27, top + 5, panelWidth - 38, UiPalette.TEXT);
        for (int row = 0; row < scroll.visibleRows() && row + scroll.offset() < lines.size(); row++) {
            graphics.text(font, lines.get(row + scroll.offset()), left + 9, top + 25 + row * LINE, UiPalette.TEXT, false);
        }
        bar.extractRenderState(graphics);
        super.extractRenderState(graphics, x, y, partial);
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) { scroll.wheel(dy); return true; }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        return bar.mouseClicked(event.x(), event.y(), event.button()) || super.mouseClicked(event, twice);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return bar.mouseDragged(event.x(), event.y()) || super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) { return bar.mouseReleased() | super.mouseReleased(event); }
    @Override public boolean keyPressed(KeyEvent event) {
        switch (event.key()) {
            case 264 -> scroll.scroll(1);
            case 265 -> scroll.scroll(-1);
            case 266 -> scroll.scroll(-scroll.visibleRows());
            case 267 -> scroll.scroll(scroll.visibleRows());
            default -> { return super.keyPressed(event); }
        }
        return true;
    }
}
