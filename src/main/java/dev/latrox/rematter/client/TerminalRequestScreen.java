package dev.latrox.rematter.client;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.client.ui.DarkButton;
import dev.latrox.rematter.client.ui.ScrollBar;
import dev.latrox.rematter.client.ui.ScrollModel;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.content.TerminalBatchPlanner;
import dev.latrox.rematter.content.TerminalBatchPlanner.CatalogTarget;
import dev.latrox.rematter.content.TerminalBatchPlanner.TargetKind;
import dev.latrox.rematter.network.ClientChemistry;
import dev.latrox.rematter.network.TerminalBatchPlanPayload;
import dev.latrox.rematter.network.TerminalBatchRequestPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Multi-step chemistry & allowlisted synthesis batch request screen for wired and wireless Matter Terminals.
 * Displays requestable substances and researched raw/natural synthesis materials on the left,
 * and a live server-verified multi-step execution plan + active jobs on the right.
 */
public final class TerminalRequestScreen extends SubstanceScreenLayer {
    private static final int ROW_H = 22;

    private enum FilterMode {
        ALL,
        SUBSTANCES,
        MATERIALS
    }

    private final MachineMenu menu;
    private final ScrollModel scroll = new ScrollModel(5);
    private final ScrollBar bar = new ScrollBar(scroll);
    private final List<TargetRowButton> rows = new ArrayList<>();
    private final List<DarkButton> filterButtons = new ArrayList<>();

    private FilterMode activeFilter = FilterMode.ALL;
    private List<CatalogTarget> allTargets = List.of();
    private List<CatalogTarget> filtered = List.of();
    private @Nullable CatalogTarget selected;
    private ChemistryRegistry shownCatalogue;
    private long shownResearchRevision = -1L;
    private net.minecraft.locale.Language shownLanguage;

    private EditBox search;
    private EditBox quantityBox;
    private DarkButton pinButton;
    private DarkButton submitButton;
    private DarkButton jobsButton;
    private boolean showingJobs;
    private final ScrollModel jobScroll = new ScrollModel(4);
    private final ScrollBar jobBar = new ScrollBar(jobScroll);
    private final List<JobRow> jobRows = new ArrayList<>();

    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int leftColWidth;
    private int rightColX;
    private int rightColWidth;
    private int listTop;
    private int footerY;
    private int pollTicks;

    public TerminalRequestScreen(MachineMenu menu) {
        super(tr("title"));
        this.menu = menu;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.rematter.terminal.request." + key, args);
    }

    @Override
    protected void init() {
        rememberLayout();
        int accent = UiPalette.machineAccent(MachineKind.TERMINAL);
        panelWidth = Math.min(348, width - 12);
        panelHeight = Math.min(228, height - 12);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        leftColWidth = (panelWidth - 24) * 52 / 100;
        rightColX = left + 12 + leftColWidth + 4;
        rightColWidth = panelWidth - (rightColX - left) - 8;
        listTop = top + 60;
        footerY = top + panelHeight - 24;

        int visibleRows = Math.max(3, (footerY - listTop - 4) / ROW_H);
        scroll.setVisibleRows(visibleRows);

        String prevSearch = search == null ? "" : search.getValue();
        String prevQty = quantityBox == null ? "1" : quantityBox.getValue();

        search = addRenderableWidget(new EditBox(font, left + 25, top + 25, leftColWidth - 21, 12, tr("search")));
        search.setMaxLength(96);
        search.setBordered(false);
        search.setTextColor(UiPalette.TEXT);
        search.setHint(tr("search").copy().withStyle(s -> s.withColor(UiPalette.MUTED & (UiPalette.WHITE >>> 8))));
        search.setValue(prevSearch);
        search.setResponder(v -> {
            scroll.reset();
            applyFilter();
        });

        filterButtons.clear();
        FilterMode[] modes = FilterMode.values();
        String[] modeKeys = {"filter_all", "filter_substances", "filter_materials"};
        String[] hintKeys = {"filter_all_hint", "filter_substances_hint", "filter_materials_hint"};
        int pillW = (leftColWidth - 4) / 3;
        for (int i = 0; i < modes.length; i++) {
            FilterMode targetMode = modes[i];
            DarkButton btn = addRenderableWidget(DarkButton.create(
                    tr(modeKeys[i]),
                    tr(hintKeys[i]),
                    left + 8 + i * (pillW + 2),
                    top + 41,
                    pillW,
                    16,
                    b -> {
                        activeFilter = targetMode;
                        scroll.reset();
                        applyFilter();
                    }));
            btn.setAccent(accent);
            filterButtons.add(btn);
        }

        rows.clear();
        for (int i = 0; i < visibleRows; i++) {
            int rowIdx = i;
            TargetRowButton rowBtn = addRenderableWidget(new TargetRowButton(
                    Button.builder(Component.empty(), b -> selectRow(rowIdx))
                            .bounds(left + 8, listTop + i * ROW_H, leftColWidth - 8, ROW_H - 1)));
            rows.add(rowBtn);
        }
        bar.setBounds(left + 8 + leftColWidth - 5, listTop, 5, visibleRows * ROW_H - 1);

        int qtyY = top + 21;
        quantityBox = addRenderableWidget(new EditBox(font, rightColX + 38, qtyY + 4, 40, 12, tr("batches")));
        quantityBox.setMaxLength(3);
        quantityBox.setBordered(false);
        quantityBox.setFilter(v -> v.matches("[0-9]*"));
        quantityBox.setTextColor(UiPalette.TEXT);
        quantityBox.setValue(prevQty);
        quantityBox.setResponder(v -> requestServerPreview());

        DarkButton minusBtn = addRenderableWidget(DarkButton.create(
                Component.literal("-"),
                tr("step_batches_hint"),
                rightColX + 84,
                qtyY,
                16,
                18,
                b -> adjustBatches(-1)));
        DarkButton plusBtn = addRenderableWidget(DarkButton.create(
                Component.literal("+"),
                tr("step_batches_hint"),
                rightColX + 102,
                qtyY,
                16,
                18,
                b -> adjustBatches(1)));
        DarkButton plus8Btn = addRenderableWidget(DarkButton.create(
                Component.literal("+8"),
                tr("step_batches_hint"),
                rightColX + 120,
                qtyY,
                22,
                18,
                b -> adjustBatches(8)));
        minusBtn.setAccent(accent);
        plusBtn.setAccent(accent);
        plus8Btn.setAccent(accent);

        pinButton = addRenderableWidget(DarkButton.labelIcon(UiIcon.TARGET, Component.translatable("screen.rematter.lab.pin"),
                Component.translatable("screen.rematter.lab.pin_hint"), left + 8, footerY, leftColWidth - 5, 18, button -> {
                    if (selected != null) PinnedLabGoal.of(selected, requestedBatches()).toggle();
                    refreshWidgets();
                }));
        pinButton.setAccent(accent);

        submitButton = addRenderableWidget(DarkButton.labelIcon(
                UiIcon.CONFIRM,
                tr("submit"),
                tr("submit_hint"),
                rightColX,
                footerY,
                rightColWidth - 42,
                18,
                b -> submitCurrentPlan()));
        submitButton.setAccent(accent);

        jobsButton = addRenderableWidget(DarkButton.icon(
                UiIcon.MONITOR, tr("jobs"), tr("jobs_hint"), rightColX + rightColWidth - 40, footerY, 18, 18,
                b -> { showingJobs = !showingJobs; refreshWidgets(); }));
        jobsButton.setAccent(accent);

        int jobsTop = top + 57;
        jobScroll.setVisibleRows(Math.max(1, (footerY - jobsTop - 6) / ROW_H));
        jobBar.setBounds(rightColX + rightColWidth - 7, jobsTop, 5, jobScroll.visibleRows() * ROW_H);
        jobRows.clear();
        for (int i = 0; i < jobScroll.visibleRows(); i++) {
            JobRow row = new JobRow();
            row.cancel = addRenderableWidget(DarkButton.icon(UiIcon.EJECT, tr("cancel_job"), tr("cancel_job_hint"),
                    rightColX + rightColWidth - 27, jobsTop + ROW_H * i, 18, 18,
                    b -> { if (row.job != null) cancelJob(row.job.jobId()); }));
            row.cancel.setAccent(UiPalette.WARNING);
            jobRows.add(row);
        }

        DarkButton closeBtn = addRenderableWidget(DarkButton.icon(
                UiIcon.CANCEL,
                tr("close"),
                tr("close_hint"),
                rightColX + rightColWidth - 20,
                footerY,
                18,
                18,
                b -> onClose()));
        closeBtn.setAccent(accent);

        reloadCatalog();
        setInitialFocus(search);
        requestServerPreview();
    }

    private int requestedBatches() {
        if (quantityBox == null || quantityBox.getValue().isEmpty()) return 1;
        try {
            return Math.clamp(Integer.parseInt(quantityBox.getValue()), 1, TerminalBatchPlanner.MAX_REQUEST_BATCHES);
        } catch (NumberFormatException invalid) {
            return 1;
        }
    }

    private void adjustBatches(int delta) {
        int next = Math.clamp(requestedBatches() + delta, 1, TerminalBatchPlanner.MAX_REQUEST_BATCHES);
        quantityBox.setValue(Integer.toString(next));
    }

    private void reloadCatalog() {
        shownCatalogue = ClientChemistry.current();
        shownResearchRevision = ClientResearchState.revision();
        shownLanguage = net.minecraft.locale.Language.getInstance();
        boolean researchOn = ClientResearchState.enabled();
        allTargets = TerminalBatchPlanner.listTargets(
                shownCatalogue,
                id -> {
                    Identifier key = Identifier.tryParse(id);
                    return key != null && BuiltInRegistries.ITEM.containsKey(key);
                },
                id -> {
                    Identifier key = Identifier.tryParse(id);
                    return key != null && BuiltInRegistries.FLUID.containsKey(key);
                },
                ClientResearchState::known,
                ClientResearchState::knownSubstance,
                researchOn).stream()
                .sorted(Comparator.comparing(CatalogTarget::unlocked).reversed()
                        .thenComparing(t -> targetDisplayName(t).getString(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        applyFilter();
    }

    private void applyFilter() {
        String q = search == null ? "" : search.getValue().strip().toLowerCase(Locale.ROOT);
        filtered = allTargets.stream()
                .filter(t -> switch (activeFilter) {
                    case ALL -> true;
                    case SUBSTANCES -> t.kind() == TargetKind.SUBSTANCE;
                    case MATERIALS -> t.kind() != TargetKind.SUBSTANCE;
                })
                .filter(t -> q.isEmpty()
                        || targetDisplayName(t).getString().toLowerCase(Locale.ROOT).contains(q)
                        || t.id().toLowerCase(Locale.ROOT).contains(q)
                        || (t.kind() == TargetKind.SUBSTANCE
                                && SubstanceLabels.matchesSymbol(SubstanceLabels.iconLabel(t.id()), q)))
                .toList();

        scroll.setSize(filtered.size());
        int idx = 0;
        if (selected != null) {
            for (int i = 0; i < filtered.size(); i++) {
                CatalogTarget cand = filtered.get(i);
                if (cand.kind() == selected.kind() && cand.id().equals(selected.id()) && cand.phase().equals(selected.phase())) {
                    idx = i;
                    break;
                }
            }
        }
        if (filtered.isEmpty()) {
            selected = null;
        } else {
            scroll.select(idx);
            CatalogTarget nextSelected = filtered.get(scroll.selected());
            boolean changed = selected == null
                    || selected.kind() != nextSelected.kind()
                    || !selected.id().equals(nextSelected.id())
                    || !selected.phase().equals(nextSelected.phase());
            selected = nextSelected;
            if (changed) requestServerPreview();
        }
        refreshWidgets();
    }

    private void selectRow(int visibleRow) {
        int index = scroll.offset() + visibleRow;
        if (index < 0 || index >= filtered.size()) return;
        scroll.select(index);
        selected = filtered.get(index);
        requestServerPreview();
        refreshWidgets();
    }

    private void requestServerPreview() {
        if (minecraft == null || minecraft.player == null || minecraft.player.containerMenu != menu) return;
        if (selected == null) {
            ClientPacketDistributor.sendToServer(new TerminalBatchRequestPayload(
                    menu.containerId,
                    TerminalBatchRequestPayload.Action.POLL,
                    TargetKind.SUBSTANCE,
                    "",
                    "",
                    0,
                    0));
            return;
        }
        ClientPacketDistributor.sendToServer(new TerminalBatchRequestPayload(
                menu.containerId,
                TerminalBatchRequestPayload.Action.PREVIEW,
                selected.kind(),
                selected.id(),
                selected.phase(),
                requestedBatches(),
                0));
    }

    private void submitCurrentPlan() {
        if (selected == null || !canSubmitPlan(menu.terminalBatchPlan())) return;
        ClientPacketDistributor.sendToServer(new TerminalBatchRequestPayload(
                menu.containerId,
                TerminalBatchRequestPayload.Action.SUBMIT,
                selected.kind(),
                selected.id(),
                selected.phase(),
                requestedBatches(),
                0));
    }

    private void cancelJob(int jobId) {
        var plan = menu.terminalBatchPlan();
        if (plan.containerId() != menu.containerId || plan.activeJobs().stream().noneMatch(job -> job.jobId() == jobId)) return;
        ClientPacketDistributor.sendToServer(new TerminalBatchRequestPayload(
                menu.containerId,
                TerminalBatchRequestPayload.Action.CANCEL,
                selected != null ? selected.kind() : TargetKind.SUBSTANCE,
                selected != null ? selected.id() : "",
                selected != null ? selected.phase() : "",
                selected != null ? requestedBatches() : 0,
                jobId));
    }

    private void refreshWidgets() {
        for (int i = 0; i < filterButtons.size(); i++) {
            filterButtons.get(i).setSelected(FilterMode.values()[i] == activeFilter);
        }
        for (int i = 0; i < rows.size(); i++) {
            TargetRowButton btn = rows.get(i);
            int idx = scroll.offset() + i;
            CatalogTarget target = idx < filtered.size() ? filtered.get(idx) : null;
            btn.target = target;
            btn.visible = btn.active = target != null;
            if (target != null) {
                Component label = targetDisplayName(target);
                btn.setMessage(label);
                Component tip = targetUnlocked(target)
                        ? label.copy().append("\n").append(targetSubtitle(target))
                        : label.copy().append("\n").append(unlockHint(target));
                btn.setTooltip(Tooltip.create(tip));
            }
        }
        var plan = menu.terminalBatchPlan();
        PinnedLabGoal choice = selected == null ? null : PinnedLabGoal.of(selected, requestedBatches());
        boolean pinned = choice != null && choice.equals(PinnedLabGoal.current());
        pinButton.active = choice != null;
        pinButton.setSelected(pinned);
        pinButton.setMessage(Component.translatable(pinned ? "screen.rematter.lab.unpin" : "screen.rematter.lab.pin"));
        pinButton.setTooltip(Tooltip.create(Component.translatable(pinned ? "screen.rematter.lab.unpin_hint" : "screen.rematter.lab.pin_hint")));
        submitButton.active = canSubmitPlan(plan);
        jobsButton.setSelected(showingJobs);
        var jobs = plan.activeJobs();
        jobScroll.setSize(plan.containerId() == menu.containerId ? jobs.size() : 0);
        for (int i = 0; i < jobRows.size(); i++) {
            JobRow row = jobRows.get(i);
            int index = jobScroll.offset() + i;
            var job = showingJobs && index < jobScroll.size() ? jobs.get(index) : null;
            row.cancel.visible = row.cancel.active = job != null;
            if (job != null && !job.equals(row.job)) {
                var name = jobName(job);
                Component label = tr("cancel_job_named", job.jobId(), name);
                row.cancel.setMessage(label);
                row.cancel.setTooltip(Tooltip.create(label.copy().append("\n").append(tr("cancel_job_hint"))));
            }
            row.job = job;
        }
    }

    @Override
    public void tick() {
        if (minecraft.player == null || minecraft.player.containerMenu != menu) {
            onClose();
            return;
        }
        if (shownCatalogue != ClientChemistry.current()
                || shownResearchRevision != ClientResearchState.revision()
                || shownLanguage != net.minecraft.locale.Language.getInstance()) {
            reloadCatalog();
        }
        pollTicks++;
        if (pollTicks >= 10) {
            pollTicks = 0;
            requestServerPreview();
        }
        refreshWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (showingJobs && inside(x, y, rightColX, top + 42, rightColWidth, footerY - top - 46)) {
            jobScroll.wheel(dy);
            refreshWidgets();
            return true;
        }
        if (inside(x, y, left + 8, listTop, leftColWidth, scroll.visibleRows() * ROW_H)) {
            scroll.wheel(dy);
            refreshWidgets();
            return true;
        }
        return super.mouseScrolled(x, y, dx, dy);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1) {
            var hovered = substanceAt(event.x(), event.y());
            if (hovered.isPresent()) {
                SubstanceDetails.open(hovered.get(), UiPalette.machineAccent(MachineKind.TERMINAL));
                return true;
            }
        }
        if ((showingJobs && jobBar.mouseClicked(event.x(), event.y(), event.button())) || bar.mouseClicked(event.x(), event.y(), event.button())) {
            refreshWidgets();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if ((showingJobs && jobBar.mouseDragged(event.x(), event.y())) || bar.mouseDragged(event.x(), event.y())) {
            refreshWidgets();
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return super.mouseReleased(event) | bar.mouseReleased() | jobBar.mouseReleased();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            onClose();
            return true;
        }
        if (quantityBox != null && quantityBox.canConsumeInput()) {
            if (quantityBox.keyPressed(event)) return true;
            if (event.key() != 258) return true;
        }
        if (search != null && search.canConsumeInput()) {
            if (search.keyPressed(event)) return true;
            if (event.key() != 258) return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (quantityBox != null && quantityBox.isFocused()) {
            return quantityBox.charTyped(event);
        }
        if (event.isAllowedChatCharacter() && search != null) {
            setFocused(search);
            return search.charTyped(event);
        }
        return super.charTyped(event);
    }

    @Override
    public Optional<SubstanceDetails.Hover> substanceAt(double x, double y) {
        for (TargetRowButton row : rows) {
            if (row.visible && row.target != null && row.target.kind() == TargetKind.SUBSTANCE
                    && inside(x, y, row.getX(), row.getY(), row.getWidth(), row.getHeight())) {
                SubstanceKey key = parseSubstanceKey(row.target.id(), row.target.phase());
                if (key != null) return Optional.of(new SubstanceDetails.Hover(key, 1L));
            }
        }
        return Optional.empty();
    }

    @Override
    public Rect2i substancePanelBounds() {
        return new Rect2i(left, top, panelWidth, panelHeight);
    }

    @Override
    public int substanceAccent() {
        return UiPalette.machineAccent(MachineKind.TERMINAL);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        refreshWidgets();
        int accent = UiPalette.machineAccent(MachineKind.TERMINAL);
        ScreenChrome.panel(g, left, top, panelWidth, panelHeight);
        ScreenChrome.icon(g, UiIcon.QUEUE_ADD, left + 6, top + 1, accent);
        ScreenChrome.text(g, font, title.getString(), left + 24, top + 5, panelWidth - 32, UiPalette.TEXT);
        g.fill(left + 8, top + 17, left + panelWidth - 8, top + 18, UiPalette.BORDER);
        g.fill(left + 8, top + 17, left + 64, top + 18, accent);

        // Left search box
        ScreenChrome.inset(g, left + 8, top + 21, leftColWidth, 18);
        ScreenChrome.icon(g, UiIcon.SEARCH, left + 9, top + 22, UiPalette.MUTED);
        bar.extractRenderState(g);

        if (filtered.isEmpty()) {
            ScreenChrome.twoLines(g, font, tr("no_matches").getString(), left + 12, listTop + 8, leftColWidth - 16, UiPalette.MUTED);
        }

        // Right quantity box + plan panel
        ScreenChrome.text(g, font, tr("qty_short").getString(), rightColX, top + 26, 34, UiPalette.MUTED);
        ScreenChrome.inset(g, rightColX + 34, top + 21, 48, 18);

        int planBoxY = top + 42;
        int planBoxH = footerY - planBoxY - 4;
        ScreenChrome.inset(g, rightColX, planBoxY, rightColWidth, planBoxH);
        if (showingJobs) drawJobs(g);
        else drawPlanDetails(g, rightColX + 5, planBoxY + 5, rightColWidth - 10, planBoxH - 10);

        super.extractRenderState(g, mx, my, partial);
    }

    private void drawPlanDetails(GuiGraphicsExtractor g, int x, int y, int maxW, int maxH) {
        int lineY = y;
        int bottomY = y + maxH;
        TerminalBatchPlanPayload plan = menu.terminalBatchPlan();

        if (selected == null) {
            ScreenChrome.twoLines(g, font, tr("select_prompt").getString(), x, lineY, maxW, UiPalette.MUTED);
            lineY += 24;
        } else if (!targetUnlocked(selected)) {
            ScreenChrome.twoLines(g, font, unlockHint(selected).getString(), x, lineY, maxW, UiPalette.WARNING);
            lineY += 24;
        } else if (PinnedLabGoal.of(selected, requestedBatches()).matches(plan, menu.containerId)) {
            int statusColor = plan.status() == TerminalBatchPlanner.Status.READY ? UiPalette.ACCENT : UiPalette.WARNING;
            Component statusLabel = switch (plan.status()) {
                case READY -> tr("status.ready");
                case MISSING_INPUTS -> tr("status.missing_inputs");
                case MISSING_MACHINES -> tr("status.missing_machines");
                case REQUIRES_OPEN_AIR -> Component.translatable("status.rematter.requires_open_air");
                case REQUIRES_RESEARCH -> Component.translatable("screen.rematter.lab.step_research");
                case REQUIRES_PRESSURE -> tr("status.requires_pressure");
                case REQUIRES_FIELD -> tr("status.requires_field");
                case REQUIRES_ADVANCEMENT -> tr("status.requires_advancement");
                case FORBIDDEN_TARGET -> tr("status.forbidden_target");
                case BATCH_LIMIT_EXCEEDED -> tr("status.batch_limit_exceeded");
            };
            ScreenChrome.text(g, font, statusLabel.getString(), x, lineY, maxW, statusColor);
            lineY += 11;

            if (plan.totalEnergy() > 0 && lineY + 10 <= bottomY) {
                String energyLine = tr("total_energy", Quantities.compact(plan.totalEnergy())).getString();
                ScreenChrome.text(g, font, energyLine, x, lineY, maxW, UiPalette.MUTED);
                lineY += 11;
            }

            int shownSteps = 0;
            for (var step : plan.steps()) {
                if (lineY + 10 > bottomY || shownSteps >= 5) break;
                Component choiceLabel = SubstanceNames.choice(step.preferredMachine(), step.choiceId());
                String stepText = tr("step_line", step.batches(), choiceLabel).getString();
                ScreenChrome.text(g, font, stepText, x, lineY, maxW, UiPalette.TEXT);
                lineY += 10;
                shownSteps++;
            }

            for (var missing : plan.missingInputs()) {
                if (lineY + 10 > bottomY) break;
                Component subName = SubstanceNames.substance(missing.substanceId(), missing.phase(), true);
                String missText = tr("missing_input", Quantities.compact(missing.missing()), subName).getString();
                ScreenChrome.text(g, font, missText, x, lineY, maxW, UiPalette.WARNING);
                lineY += 10;
            }

            for (MachineKind kind : plan.missingMachines()) {
                if (lineY + 10 > bottomY) break;
                Component mName = Component.translatable("block.rematter." + kind.id());
                ScreenChrome.text(g, font, tr("missing_machine", mName).getString(), x, lineY, maxW, UiPalette.WARNING);
                lineY += 10;
            }
        }

    }

    private static final class JobRow {
        private DarkButton cancel;
        private dev.latrox.rematter.content.TerminalJobCoordinator.@Nullable JobSummary job;
    }

    private static Component jobName(dev.latrox.rematter.content.TerminalJobCoordinator.JobSummary job) {
        return switch (job.targetKind()) {
            case SUBSTANCE -> SubstanceNames.substance(job.targetId(), job.phase(), true);
            case SYNTHESIS_ITEM -> SubstanceNames.item(job.targetId());
            case SYNTHESIS_FLUID -> SubstanceNames.fluid(job.targetId());
        };
    }

    private void drawJobs(GuiGraphicsExtractor g) {
        int x = rightColX + 5;
        ScreenChrome.text(g, font, tr("active_jobs", jobScroll.size()).getString(), x, top + 46, rightColWidth - 10, UiPalette.ACCENT);
        if (jobScroll.scrollable()) jobBar.extractRenderState(g);
        for (JobRow row : jobRows) {
            var job = row.job;
            if (job == null) continue;
            int y = row.cancel.getY();
            int w = row.cancel.getX() - x - 2;
            ScreenChrome.text(g, font, tr("job_name", job.jobId(), jobName(job)).getString(), x, y, w, UiPalette.TEXT);
            ScreenChrome.text(g, font, tr("job_progress", job.completedBatches(), job.totalBatches(),
                    StatusText.of(job.status(), 0, 0)).getString(), x, y + 10, w, UiPalette.MUTED);
        }
    }

    /** Only the matching server quote knows the machine owner's research; unquoted rows use the viewer's catalogue. */
    private boolean targetUnlocked(CatalogTarget target) {
        var plan = menu.terminalBatchPlan();
        return target.equals(selected) && PinnedLabGoal.of(target, requestedBatches()).matches(plan, menu.containerId)
                ? !plan.targetResearchMissing() : target.unlocked();
    }

    private boolean canSubmitPlan(TerminalBatchPlanPayload plan) {
        return selected != null && PinnedLabGoal.of(selected, requestedBatches()).matches(plan, menu.containerId)
                && plan.status() == TerminalBatchPlanner.Status.READY && !plan.steps().isEmpty()
                && plan.activeJobs().size() < dev.latrox.rematter.content.TerminalJobCoordinator.MAX_ACTIVE_JOBS_PER_OWNER;
    }

    private Component unlockHint(CatalogTarget target) {
        var plan = menu.terminalBatchPlan();
        return PinnedLabGoal.unlock(PinnedLabGoal.of(target, requestedBatches()).matches(plan, menu.containerId)
                ? plan.researchSample() : "");
    }

    private static Component targetDisplayName(CatalogTarget t) {
        return switch (t.kind()) {
            case SUBSTANCE -> {
                SubstanceKey key = parseSubstanceKey(t.id(), t.phase());
                yield key != null ? SubstanceNames.labelled(key) : Component.literal(t.id());
            }
            case SYNTHESIS_ITEM -> SubstanceNames.item(t.id());
            case SYNTHESIS_FLUID -> SubstanceNames.fluid(t.id());
        };
    }

    private static Component targetSubtitle(CatalogTarget t) {
        return switch (t.kind()) {
            case SUBSTANCE -> SubstanceNames.phase(t.phase());
            case SYNTHESIS_ITEM -> tr("batch_items", t.outputPerBatch());
            case SYNTHESIS_FLUID -> tr("batch_fluid", t.outputPerBatch());
        };
    }

    private static @Nullable SubstanceKey parseSubstanceKey(String id, String phase) {
        try {
            return new SubstanceKey(SubstanceId.parse(id), Phase.valueOf(phase.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static boolean inside(double x, double y, int left, int top, int w, int h) {
        return x >= left && x < left + w && y >= top && y < top + h;
    }

    private final class TargetRowButton extends DarkButton {
        private @Nullable CatalogTarget target;

        TargetRowButton(Button.Builder builder) {
            super(builder, null, false);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float partial) {
            if (target == null) return;
            boolean chosen = selected != null
                    && selected.kind() == target.kind()
                    && selected.id().equals(target.id())
                    && selected.phase().equals(target.phase());
            ScreenChrome.raised(g, getX(), getY(), getWidth(), getHeight(), chosen || isHoveredOrFocused(), true);

            boolean unlocked = targetUnlocked(target);
            if (!unlocked) {
                ScreenChrome.icon(g, UiIcon.LOCKED, getX() + 2, getY() + 2, UiPalette.MUTED);
            } else if (target.kind() == TargetKind.SUBSTANCE) {
                SubstanceKey key = parseSubstanceKey(target.id(), target.phase());
                if (key != null) {
                    SubstanceIcon.draw(g, getX() + 2, getY() + 2, key);
                }
            } else if (target.kind() == TargetKind.SYNTHESIS_ITEM) {
                Identifier itemKey = Identifier.tryParse(target.id());
                if (itemKey != null && BuiltInRegistries.ITEM.containsKey(itemKey)) {
                    g.fakeItem(new ItemStack(BuiltInRegistries.ITEM.getValue(itemKey)), getX() + 2, getY() + 2);
                }
            } else {
                ScreenChrome.icon(g, UiIcon.LIQUID, getX() + 2, getY() + 2, UiPalette.LIQUID);
            }

            int textColor = unlocked ? UiPalette.TEXT : UiPalette.MUTED;
            ScreenChrome.text(g, font, getMessage().getString(), getX() + 21, getY() + 6, getWidth() - 25, textColor);
            if (chosen) {
                g.fill(getX() + 1, getY() + getHeight() - 2, getX() + getWidth() - 1, getY() + getHeight() - 1,
                        UiPalette.machineAccent(MachineKind.TERMINAL));
            }
        }
    }
}
