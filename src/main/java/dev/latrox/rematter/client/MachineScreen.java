package dev.latrox.rematter.client;

import static dev.latrox.rematter.client.UiPalette.*;
import dev.latrox.rematter.client.ScreenChrome.Icon;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.content.MachineBlockEntity;
import dev.latrox.rematter.content.MachineCellAccess;
import dev.latrox.rematter.content.MachineChoices;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.content.MachinePortMode;
import dev.latrox.rematter.content.MachineSnapshot;
import dev.latrox.rematter.content.StockQuery;
import dev.latrox.rematter.network.ClientChemistry;
import dev.latrox.rematter.network.ReleaseHeldOrderPayload;
import dev.latrox.rematter.network.StockQueryPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Familiar machine slots and a separate disk/quantity browser; all actions retain server validation. */
public final class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private static final int PANEL_WIDTH = 296;
    private static final int PANEL_HEIGHT = 240;
    private static final int SIDE_TAB_WIDTH = 23;
    private static final int SIDE_TAB_HEIGHT = 22;
    private static final int SIDE_TAB_TOP = 30;
    private static final int SIDE_TAB_PITCH = 26;
    private static final int CONTENT_RIGHT = 284;
    private static final int STOCK_TOP = 73;
    private static final int STOCK_PITCH = 20;
    private static final int STOCK_FOOTER = 134;
    private static final int GRID_ROWS = 3;
    private static final int FULL_GRID_COLUMNS = 3;
    private static final int CHIP_SIZE = 16;
    private final List<StockButton> stockButtons = new ArrayList<>();
    private EditBox search;
    private ConsoleButton primary, choose, automation, networkJobs, backend, repeat, addBatch, previous, next;
    private ConsoleButton terminalDevices, returnToTerminal, release;
    private TabButton processTab, upgradesTab, configurationTab;
    private ConsoleButton ports;
    private boolean sideTabs;
    private int page;
    private ChipButton favoritesChip;
    private final List<ChipButton> phaseChips = new ArrayList<>();
    private final Set<String> phaseFilter = new HashSet<>();
    private boolean favoritesOnly;
    private MachineMenu.Stock shownStock;
    private ChemistryRegistry shownCatalogue;
    private StockFilter shownFilter;
    private int shownFavorites = -1;
    private List<MachineSnapshot.Entry> shownEntries = List.of();
    private StockQuery sentQuery;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);
    }
    public List<Rect2i> viewerExclusionAreas() {
        if (!sideTabs || processTab == null) return List.of();
        var areas = new ArrayList<Rect2i>(3);
        for (var tab : List.of(processTab, upgradesTab, configurationTab)) {
            if (tab.visible) areas.add(new Rect2i(tab.getX(), tab.getY(), tab.getWidth(), tab.getHeight()));
        }
        return areas;
    }

    @Override protected boolean hasClickedOutside(double mouseX, double mouseY, int x, int y) {
        for (var area : viewerExclusionAreas()) {
            if (mouseX >= area.getX() && mouseX < area.getX() + area.getWidth()
                    && mouseY >= area.getY() && mouseY < area.getY() + area.getHeight()) return false;
        }
        return super.hasClickedOutside(mouseX, mouseY, x, y);
    }

    @Override protected void init() {
        super.init();
        sideTabs = width >= PANEL_WIDTH + SIDE_TAB_WIDTH + 1;
        if (sideTabs) leftPos = Math.max(leftPos, SIDE_TAB_WIDTH);
        String query = search == null ? "" : search.getValue();
        search = addRenderableWidget(new EditBox(font, leftPos + 17, topPos + 53, 262, 14, tr("search")));
        search.setHint(tr("search").copy().withStyle(style -> style.withColor(MUTED & 0xffffff)));
        search.setMaxLength(128);
        search.setBordered(false);
        search.setTextColor(TEXT);
        search.setValue(query);
        search.setResponder(value -> { page = 0; updateWidgets(); });
        favoritesChip = chip(Icon.STAR, "favorites", ACCENT_BRIGHT, () -> favoritesOnly, () -> favoritesOnly = !favoritesOnly);
        phaseChips.clear();
        phaseChips.add(chip(Icon.SOLID, "solid", phaseColor("solid"), () -> phaseFilter.contains("solid"), () -> togglePhase("solid")));
        phaseChips.add(chip(Icon.LIQUID, "liquid", phaseColor("liquid"), () -> phaseFilter.contains("liquid"), () -> togglePhase("liquid")));
        phaseChips.add(chip(Icon.GAS, "gas", phaseColor("gas"), () -> phaseFilter.contains("gas"), () -> togglePhase("gas")));

        processTab = tab(MachineMenu.View.MAIN, 12, 84, passive() ? "tab_contents" : "tab_process");
        upgradesTab = tab(MachineMenu.View.UPGRADES, 100, 84, "tab_upgrades");
        configurationTab = tab(MachineMenu.View.CONFIGURATION, 188, 96, "tab_configuration");
        ports = control("ports", 205, 50, 79, 19, b -> openPorts(), "ports_hint");
        primary = control("start", 166, 117, 118, 23, b -> {
            var decision = MachinePrimaryAction.forSnapshot(menu.snapshot());
            if (!decision.enabled()) return;
            if (decision.opensRecipeSelection()) openRecipes();
            else if (decision.menuAction() >= 0) send(decision.menuAction());
        }, "start_once_hint");
        choose = control("recipe", 197, 50, 87, 19, b -> openRecipes(), "choose_hint");
        automation = control("", 12, 104, 272, 18, b -> send(3), "automation_hint");
        networkJobs = control("", 150, 104, 134, 18, b -> send(8), "network_jobs_hint");
        backend = control("", 12, 126, 272, 18, b -> send(9), "backend_hint");
        release = control("release_held", 150, 104, 134, 18, b -> openRelease(), "release_held_hint");
        repeat = control("", 12, 126, 224, 18, b -> send(5), "continuous_hint");
        addBatch = control("", 240, 126, 44, 18, b -> send(4), "add_batch_hint");
        previous = control("previous_hint", 12, STOCK_FOOTER, 20, 13, b -> turnPage(-1), "previous_hint");
        next = control("next_hint", 264, STOCK_FOOTER, 20, 13, b -> turnPage(1), "next_hint");
        terminalDevices = control("terminal.network", 186, 5, 98, 17, b -> openTerminalDevices(), "terminal.network_hint");
        returnToTerminal = control("terminal.return", 186, 5, 98, 17,
                b -> send(MachineMenu.RETURN_TO_TERMINAL), "terminal.return_hint");
        addBatch.setMessage(tr("add_batch_hint"));
        stockButtons.clear();
        for (int i = 0; i < FULL_GRID_COLUMNS * GRID_ROWS; i++) {
            var button = new StockButton(Button.builder(Component.empty(), b -> {
                var stock = (StockButton) b;
                if (stock.entry != null) minecraft.pushGuiLayer(new StockDetailsScreen(stock.entry.id(), stock.entry.phase()));
            }).bounds(leftPos, topPos, tilePitch() - 2, STOCK_PITCH - 2));
            stockButtons.add(addRenderableWidget(button));
        }
        updateWidgets();
    }
    private static Component tr(String key) { return Component.translatable("screen.rematter." + key); }
    private ConsoleButton control(String key, int x, int y, int width, int height, Button.OnPress action, String hint) {
        var button = addRenderableWidget(new ConsoleButton(Button.builder(key.isEmpty() ? Component.empty() : tr(key), action)
                .bounds(leftPos + x, topPos + y, width, height)));
        button.explain(tr(hint));return button;
    }
    private TabButton tab(MachineMenu.View view, int x, int width, String key) {
        int tabX = sideTabs ? leftPos - SIDE_TAB_WIDTH + 1 : leftPos + x;
        int tabY = sideTabs ? SIDE_TAB_TOP + view.ordinal() * SIDE_TAB_PITCH : 25;
        return addRenderableWidget(new TabButton(Button.builder(tr(key), b -> { menu.setView(view); page = 0; updateWidgets(); })
                .bounds(tabX, topPos + tabY, sideTabs ? SIDE_TAB_WIDTH : width, sideTabs ? SIDE_TAB_HEIGHT : 18)
                .tooltip(Tooltip.create(tr(key))), view));
    }
    private ChipButton chip(Icon icon, String filter, int color, BooleanSupplier on, Runnable toggle) {
        var chip = addRenderableWidget(new ChipButton(Button.builder(tr("filter_" + filter), b -> {
            toggle.run();
            page = 0;
            updateWidgets();
        }).bounds(leftPos, topPos, CHIP_SIZE, CHIP_SIZE), icon, color, on));
        chip.explain(tr(filter.equals("favorites") ? "filter_favorites_hint" : "filter_phase_hint"));
        return chip;
    }
    private void togglePhase(String phase) {
        if (!phaseFilter.remove(phase)) phaseFilter.add(phase);
    }
    private UUID player() { return minecraft.player == null ? null : minecraft.player.getUUID(); }
    private boolean favorite(MachineSnapshot.StockKey key) {
        UUID player = player();
        return player != null && StockFavorites.get().contains(player, key);
    }
    private void turnPage(int step) {
        page = Math.max(0, page + step);
        updateWidgets();
    }
    private boolean main() { return menu.view() == MachineMenu.View.MAIN; }
    private boolean configuration() { return menu.view() == MachineMenu.View.CONFIGURATION; }
    private boolean passive() { return !menu.kind().processes(); }
    private boolean disk() { return menu.diskRole(); }
    private boolean handPreparator() { return menu.kind() == MachineKind.HAND_PREPARATOR; }
    private boolean preparator() { return dev.latrox.rematter.content.preparation.PreparationTier.isPreparation(menu.kind()); }
    private boolean groupedIngredients() { return preparator() && !menu.snapshot().networkJobs() && menu.slots.get(3).isActive(); }
    private boolean industrial() { return menu.kind() == MachineKind.INDUSTRIAL_PREPARATOR || menu.kind() == MachineKind.INDUSTRIAL_REACTOR; }
    private boolean selecting() { return menu.kind() == MachineKind.SYNTHESIZER || menu.kind() == MachineKind.ELECTROLYZER || menu.kind() == MachineKind.REACTOR || menu.kind() == MachineKind.INDUSTRIAL_REACTOR; }
    private static boolean working(String status) { return List.of("working", "grinding", "sieving", "collecting", "dosing", "mixing", "packing", "pressing", "sintering", "cooling").contains(status); }
    private void openRecipes() { minecraft.pushGuiLayer(new ProcessSelectionScreen(menu)); }
    private void openPorts() { minecraft.pushGuiLayer(new MachinePortSettingsScreen(menu)); }
    private void openRelease() {
        var orders = menu.snapshot().orders();
        if (!orders.held().isEmpty()) minecraft.pushGuiLayer(new ReleaseConfirmScreen(orders));
    }
    private void openTerminalDevices() {
        menu.resetTerminalDevices();
        send(MachineMenu.OPEN_TERMINAL_DEVICES);
        minecraft.pushGuiLayer(new TerminalDevicesScreen(menu));
    }
    private void send(int action) { if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action); }
    private Component statusText() {
        var s = menu.snapshot();
        if (s.status().equals("structure") && industrial()) return Component.translatable("status.rematter." + s.structure().diagnostic());
        if (working(s.status())) return tr("processing");
        return StatusText.of(s.status(), s.orders().stale(), s.orders().quarantined());
    }
    private static Component name(MachineSnapshot.Entry entry) { return SubstanceNames.substance(entry.id(), entry.phase(), entry.known()); }
    private static Component name(MachineSnapshot.Amount amount) { return SubstanceNames.substance(amount.id(), amount.phase(), amount.known()); }
    private static String label(MachineSnapshot.Amount amount) {
        return amount.known() ? SubstanceLabels.shortLabel(amount.id(), name(amount).getString()) : name(amount).getString();
    }
    /** Filtered once per stock, catalogue, filter or favorite change, not on every frame. */
    private List<MachineSnapshot.Entry> entries() {
        var stock = menu.stock();
        var catalogue = ClientChemistry.current();
        var filter = new StockFilter(search == null ? "" : search.getValue(), phaseFilter, favoritesOnly);
        int favorites = StockFavorites.get().revision();
        if (stock == shownStock && catalogue == shownCatalogue && filter.equals(shownFilter) && favorites == shownFavorites) return shownEntries;
        if (catalogue != shownCatalogue || !filter.equals(shownFilter) || favorites != shownFavorites) sendStockQuery(filter, catalogue);
        shownEntries = filter.apply(stock.entries(), this::favorite, e -> name(e).getString(), SubstanceLabels::iconLabel);
        shownStock = stock;
        shownCatalogue = catalogue;
        shownFilter = filter;
        shownFavorites = favorites;
        return shownEntries;
    }
    /** The server keeps search matches and favorites within its row limit; only a changed query is sent. */
    private void sendStockQuery(StockFilter filter, ChemistryRegistry catalogue) {
        UUID player = player();
        var query = filter.query(player == null ? Set.of() : StockFavorites.get().of(player), catalogue.substances(),
                definition -> SubstanceNames.name(definition).getString(), SubstanceLabels::iconLabel);
        if (query.equals(sentQuery)) return;
        sentQuery = query;
        ClientPacketDistributor.sendToServer(new StockQueryPayload(menu.containerId, query));
    }
    private int gridColumns() { return disk() ? 2 : FULL_GRID_COLUMNS; }
    private int gridX() { return disk() ? 111 : 12; }
    private int tilePitch() { return (CONTENT_RIGHT + 2 - gridX()) / gridColumns(); }
    private int perPage() { return gridColumns() * GRID_ROWS; }
    private int pages(List<MachineSnapshot.Entry> values) { return Math.max(1, (values.size() + perPage() - 1) / perPage()); }
    private void updateWidgets() {
        var s = menu.snapshot(); boolean ready = s.containerId() == menu.containerId;
        terminalDevices.visible = ready && menu.kind() == MachineKind.TERMINAL && !menu.remote();
        returnToTerminal.visible = ready && menu.remote();
        terminalDevices.active = returnToTerminal.active = minecraft.player != null && !minecraft.player.isSpectator();
        var decision = MachinePrimaryAction.forSnapshot(s);
        processTab.active = configurationTab.active = ready;
        ports.visible = ports.active = ready && configuration() && MachinePortMode.configurable(menu.kind());
        processTab.setMessage(tr(passive() ? "tab_contents" : "tab_process"));
        processTab.explain(processTab.getMessage());
        upgradesTab.visible = ready && menu.supportsUpgrades();
        upgradesTab.active = ready;
        if (sideTabs) configurationTab.setY(topPos + SIDE_TAB_TOP + (upgradesTab.visible ? 2 : 1) * SIDE_TAB_PITCH);
        upgradesTab.explain(tr("upgrade_hint").copy().append("\n").append(tr("upgrade_limit")).append("\n").append(tr("upgrade_effect_hint")));
        search.visible = search.active = ready && main() && passive();
        search.setX(leftPos + gridX() + 5);
        var chips = new ArrayList<ChipButton>(phaseChips);
        chips.addFirst(favoritesChip);
        int chipsLeft = CONTENT_RIGHT - 2 - chips.size() * (CHIP_SIZE + 1);
        search.setWidth(chipsLeft - gridX() - 9);
        for (int i = 0; i < chips.size(); i++) {
            var chip = chips.get(i);
            chip.visible = chip.active = search.visible;
            chip.setX(leftPos + chipsLeft + i * (CHIP_SIZE + 1));
            chip.setY(topPos + 51);
        }
        primary.visible = ready && main() && decision.visible(); primary.active = ready && decision.enabled();
        primary.setMessage(tr(decision.labelKey())); primary.explain(tr(decision.hintKey()));
        choose.visible = ready && main() && !s.networkJobs() && selecting() && !decision.opensRecipeSelection(); choose.active = ready && s.choiceCount() > 0;
        automation.visible = ready && configuration(); automation.setMessage(tr(s.automation() ? "automation_on" : "automation_off"));
        automation.explain(tr("automation_hint"));
        repeat.visible = addBatch.visible = ready && configuration() && selecting() && !s.networkJobs();
        boolean valid = s.selected() >= 0 && !s.preview().id().isEmpty() && s.selectedId().equals(s.preview().id()) && !s.status().equals("selection_unavailable");
        repeat.active = valid; repeat.setMessage(tr(s.repeat() ? "stop_repeat" : "run_repeat"));
        repeat.explain(tr("continuous_hint"));
        addBatch.active = valid && !s.repeat() && s.queuedBatches() < 64;
        boolean recoveringUpgrade = configuration() && menu.slots.get(2).isActive();
        boolean canNetwork = menu.kind().processes() && menu.kind()!=MachineKind.ANALYZER && !handPreparator() && !recoveringUpgrade;
        networkJobs.visible = ready && configuration() && canNetwork;
        networkJobs.setMessage(tr(s.networkJobs()?"network_jobs_on":"network_jobs_off"));
        networkJobs.explain(tr("network_jobs_hint"));
        backend.visible = ready && configuration() && menu.kind()==MachineKind.NETWORK_INTERFACE;
        backend.setMessage(Component.translatable("screen.rematter.backend",tr(switch(s.backend()){case "ae2","rs"->"backend_"+s.backend();default->"backend_none";})));
        backend.explain(tr("backend_hint"));
        release.visible = ready && configuration() && menu.kind() == MachineKind.NETWORK_INTERFACE && !s.orders().held().isEmpty();
        release.active = minecraft.player != null && !minecraft.player.isSpectator();
        automation.setWidth(canNetwork || release.visible ? 134 : 272);
        backend.setX(leftPos + (recoveringUpgrade ? 109 : 12));
        backend.setWidth(recoveringUpgrade ? 175 : 272);
        var values = entries();
        int pageCount = pages(values);
        page = Math.clamp(page, 0, pageCount - 1);
        previous.visible = next.visible = ready && main() && passive() && pageCount > 1;
        previous.active = page > 0; next.active = page + 1 < pageCount;
        previous.setX(leftPos + gridX());
        for (int i = 0; i < stockButtons.size(); i++) {
            var b = stockButtons.get(i); int index = page * perPage() + i;
            b.show(i < perPage() && index < values.size() ? values.get(index) : null);
            b.favorite = b.entry != null && favorite(b.entry.key());
            b.visible = b.active = ready && main() && passive() && b.entry != null;
            b.setX(leftPos + gridX() + i % gridColumns() * tilePitch());
            b.setY(topPos + STOCK_TOP + i / gridColumns() * STOCK_PITCH);
            b.setWidth(tilePitch() - 2);
        }
    }
    @Override protected void containerTick() {
        super.containerTick();
        updateWidgets();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int mx = mouseX - leftPos, my = mouseY - topPos; var s = menu.snapshot();
        if (s.containerId() != menu.containerId) return;
        if(main() && disk()) {
            for(int bay=0;bay<menu.diskBayCount();bay++) {
                var slot=menu.slots.get(menu.diskMenuSlot(bay));
                if(inside(mx,my,slot.x,slot.y,16,16) && !slot.hasItem()) {
                    tooltip(g,List.of(Component.translatable("screen.rematter.drive_bay",bay+1),tr("drive_bay_hint")),mouseX,mouseY);return;
                }
            }
        }
        if(overActiveSlot(mx,my))return;
        Component marker=slotMarkerTooltip(mx,my,s);
        if(marker!=null){g.setTooltipForNextFrame(marker,mouseX,mouseY);return;}
        if (menu.view() == MachineMenu.View.UPGRADES) {
            if (inside(mx, my, 12, 55, 134, 37)) tooltip(g, List.of(Component.translatable("item.rematter.speed_upgrade"), tr("speed_upgrade_hint")), mouseX, mouseY);
            else if (inside(mx, my, 150, 55, 134, 37)) tooltip(g, List.of(Component.translatable("item.rematter.efficiency_upgrade"), tr("efficiency_upgrade_hint")), mouseX, mouseY);
            else if (inside(mx, my, 67, 105, 217, 36)) tooltip(g, List.of(tr("upgrade_hint"), tr("upgrade_limit"), tr("upgrade_effect_hint")), mouseX, mouseY);
            return;
        }
        if (main() && !passive()) {
            if (inside(mx, my, 13, 74, 19, 38)) {
                g.setTooltipForNextFrame(handPreparator() ? tr("crank_hint") : energyLine(), mouseX, mouseY);
            } else if (inside(mx, my, 12, 114, primary.visible ? 149 : 272, 28)) {
                var lines = new ArrayList<Component>();
                lines.add(statusText());
                if (industrial()) lines.addAll(structureDetails(s));
                tooltip(g, lines, mouseX, mouseY);
            } else if (!s.preview().id().isEmpty() && (inside(mx, my, 12, 49, choose.visible ? 179 : 272, 22)
                    || !menu.inputRole() && inside(mx, my, 38, 73, 87, 39)
                    || !menu.slots.get(1).isActive() && inside(mx, my, 205, 73, 79, 39))) {
                tooltip(g, recipeDetails(s), mouseX, mouseY);
            }
        } else if (main() && passive()) {
            if (inside(mx, my, gridX() + (previous.visible ? 24 : 2), STOCK_FOOTER,
                    CONTENT_RIGHT - gridX() - (previous.visible ? 48 : 4), 13)
                    || disk() && inside(mx, my, 12, 48, 92, 97))
                tooltip(g, storageDetails(s), mouseX, mouseY);
        } else if (configuration()) {
            if (inside(mx, my, 12, 49, ports.visible ? 187 : 272, 22))
                tooltip(g, industrial() ? structureDetails(s) : storageDetails(s), mouseX, mouseY);
            else if (inside(mx, my, 12, 73, 272, 28) && java.util.stream.IntStream.of(0, 1, 3, 4).noneMatch(i -> menu.slots.get(i).isActive()))
                tooltip(g, preparator() ? List.of(tr("optional_mix_hint")) : menu.kind() == MachineKind.ANALYZER ? List.of(tr("sample_kept")) : storageDetails(s), mouseX, mouseY);
        }
    }
    private static boolean inside(int x,int y,int left,int top,int width,int height) { return x>=left && x<left+width && y>=top && y<top+height; }
    private Component slotMarkerTooltip(int x, int y, MachineSnapshot s) {
        if (!(main() && !passive() || configuration())) return null;
        for (int i : new int[]{0, 1, 3, 4}) {
            var slot = menu.slots.get(i);
            if (slot.isActive() && inside(x, y, slot.x - 12, slot.y - 13, 40, 12)) {
                Component label = slotLabel(i, s);
                return i == 3 && groupedIngredients() ? label.copy().append("\n").append(tr("optional_mix_hint")) : label;
            }
        }
        var output = menu.slots.get(1);
        return main() && !passive() && !outputIcon(s.preview()).isEmpty() && inside(x, y, output.x - 12, output.y - 13, 40, 12)
                ? slotLabel(1, s) : null;
    }

    private Component slotLabel(int index, MachineSnapshot snapshot) {
        if (index == 1) return tr(snapshot.networkJobs() ? "to_network" : "output");
        if (isDiskSlot(index)) return tr("disk_slot");
        if (snapshot.networkJobs()) return tr("from_network");
        if (index == 3) return tr("mix_slot");
        if (index == 4) return tr("additive_slot");
        return tr(menu.kind() == MachineKind.ANALYZER ? "sample" : groupedIngredients() ? "ingredients" : "input");
    }

    private boolean isDiskSlot(int index) { return index == 0 && disk(); }
    private boolean overActiveSlot(int x,int y) { return menu.slots.stream().anyMatch(slot -> slot.isActive() && inside(x,y,slot.x,slot.y,16,16)); }
    private void tooltip(GuiGraphicsExtractor g,List<Component> lines,int x,int y) { if(!lines.isEmpty())g.setComponentTooltipForNextFrame(font,lines,x,y); }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        ScreenChrome.panel(g, x, y, PANEL_WIDTH, PANEL_HEIGHT);
        g.fill(x + 8, y + 23, x + 288, y + 24, BORDER);
        g.fill(x + 8, y + 23, x + 64, y + 24, machineAccent(menu.kind()));
        ScreenChrome.inset(g, x + 8, y + 46, 280, 102);
        g.fill(x + 9, y + 47, x + 287, y + 147, PANEL);
        var s = menu.snapshot();
        if (menu.view() == MachineMenu.View.UPGRADES) {
            ScreenChrome.raised(g, x + 12, y + 55, 134, 37, inside(mouseX - x, mouseY - y, 12, 55, 134, 37), true);
            ScreenChrome.raised(g, x + 150, y + 55, 134, 37, inside(mouseX - x, mouseY - y, 150, 55, 134, 37), true);
        } else if (main() && passive() && search.visible) {
            ScreenChrome.inset(g, x + gridX(), y + 49, CONTENT_RIGHT - gridX(), 20);
            if (search.isFocused()) g.fill(x + gridX() + 1, y + 67, search.getX() + search.getWidth(), y + 68, ACCENT);
            if (disk()) ScreenChrome.inset(g, x + 12, y + 49, 92, 95);
        } else if (main() && !passive()) {
            if (!handPreparator()) {
                ScreenChrome.inset(g, x + 14, y + 74, 12, 34);
                int fill = (int) Math.clamp(30L * menu.progress().energy() / MachineBlockEntity.ENERGY_CAPACITY, 0, 30);
                g.fill(x + 16, y + 106 - fill, x + 24, y + 106, ACCENT);
            }
            int arrowStart = processArrowStart(), arrowEnd = 200;
            g.fill(x + arrowStart, y + 88, x + arrowEnd, y + 94, HIGHLIGHT);
            g.fill(x + arrowEnd - 4, y + 84, x + arrowEnd, y + 98, HIGHLIGHT);
            g.fill(x + arrowEnd, y + 86, x + arrowEnd + 4, y + 96, HIGHLIGHT);
            var work = menu.progress();
            int progress = work.duration() > 0 ? (int) Math.clamp((long) (arrowEnd - arrowStart) * work.progress() / work.duration(), 0, arrowEnd - arrowStart) : 0;
            g.fill(x + arrowStart, y + 88, x + arrowStart + progress, y + 94, ACCENT);
            g.fill(x + 12, y + 112, x + CONTENT_RIGHT, y + 113, BORDER);
        }
        for(int i=0;i<menu.slots.size();i++) {
            var slot=menu.slots.get(i);if(!slot.isActive())continue;
            int color=i>=MachineMenu.FIRST_PLAYER_SLOT?SLOT_EDGE:i==2?UPGRADE:i==1?ACCENT:SLOT_EDGE;
            if(main()&&disk()&&(i==0||i>=5&&i<10)) {
                int bay=MachineCellAccess.bayForSlot(MachineKind.DRIVE,i);
                if(bay<s.cells().size()&&s.cells().get(bay).installed())color=s.cells().get(bay).readable()?ACCENT:WARNING;
            }
            g.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,color);
            g.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,RECESS);
            g.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+1,0xff0a1115);
            g.fill(x+slot.x,y+slot.y+1,x+slot.x+1,y+slot.y+15,0xff0a1115);
            g.fill(x+slot.x,y+slot.y+15,x+slot.x+16,y+slot.y+16,HIGHLIGHT);
        }
        var output = menu.slots.get(1);
        if (main() && !passive() && !output.hasItem()) {
            var icon = outputIcon(s.preview());
            if (!icon.isEmpty()) g.fakeItem(icon, x + output.x, y + output.y);
        }
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int titleWidth = terminalDevices.visible || returnToTerminal.visible ? 168 : 272;
        if (font.width(title) <= titleWidth) g.text(font, title, 12, 10, TEXT, false);
        else ScreenChrome.twoLines(g, font, title.getString(), 12, 4, titleWidth, TEXT);
        g.text(font, tr("inventory"), MachineMenu.PLAYER_SLOTS_X, 151, MUTED, false);
        if (sideTabs) {
            var selectedTab = switch (menu.view()) {
                case MAIN -> processTab;
                case UPGRADES -> upgradesTab;
                case CONFIGURATION -> configurationTab;
            };
            ScreenChrome.icon(g, selectedTab.icon(), 12, 28, ACCENT_BRIGHT);
            text(g, selectedTab.getMessage().getString(), 30, 30, 254, TEXT);
        }
        var s = menu.snapshot();
        if (s.containerId() != menu.containerId) {
            g.text(font, tr("loading"), 12, 54, MUTED, false);
            return;
        }
        if (menu.view() == MachineMenu.View.UPGRADES) {
            upgradeLabels(g, s);
        } else if (configuration()) {
            configurationLabels(g, s);
        } else if (passive()) {
            storageLabels(g, s);
        } else {
            processLabels(g, s);
        }
    }

    private int processArrowStart() { return menu.mixingRole() ? 157 : 136; }

    private void processLabels(GuiGraphicsExtractor g, MachineSnapshot s) {
        var p = s.preview();
        String name = previewTitle(s).getString();
        ScreenChrome.icon(g, Icon.RECIPE, 12, 52, MUTED);
        ScreenChrome.twoLines(g, font, name, 30, 50, choose.visible ? 162 : 254, TEXT);
        if (handPreparator()) ScreenChrome.icon(g, Icon.CRANK, 14, 87, ACCENT);
        if (menu.inputRole()) {
            for (int index : new int[]{0, 3, 4}) if (menu.slots.get(index).isActive()) drawSlotLabel(g, index, s);
        } else if (p.inputs().size() == 1) {
            var ingredient = p.inputs().getFirst();
            ScreenChrome.twoLines(g, font, label(ingredient), 38, 75, 90,
                    !ingredient.known() || ingredient.available() < ingredient.required() ? WARNING : TEXT);
            text(g, matter(ingredient.required()), 38, 99, 90, MUTED);
        } else if (!p.inputs().isEmpty()) {
            for (int i = 0; i < Math.min(2, p.inputs().size()); i++) {
                var ingredient = p.inputs().get(i);
                text(g, label(ingredient), 38, 72 + i * 19, 90,
                        !ingredient.known() || ingredient.available() < ingredient.required() ? WARNING : TEXT);
                text(g, matter(ingredient.required()), 38, 81 + i * 19, 80, MUTED);
            }
            if (p.inputs().size() > 2 || !p.complete()) g.text(font, "+", 122, 100, MUTED, false);
        } else if (selecting()) {
            ScreenChrome.twoLines(g, font, tr("stored_resources").getString(), 38, 81, 90, MUTED);
        }
        if (menu.kind() == MachineKind.ANALYZER) {
            text(g, tr("analysis_result").getString(), 208, 73, 76, MUTED);
            if (!p.outputs().isEmpty()) {
                text(g, Component.translatable("screen.rematter.substance_count", p.outputs().size()).getString(), 208, 85, 76, TEXT);
                text(g, tr("sample_kept").getString(), 208, 98, 76, MUTED);
            }
        } else if (menu.slots.get(1).isActive() || !outputIcon(p).isEmpty()) {
            drawSlotLabel(g, 1, s);
            if (p.outputCount() > 0 && (p.outputKind().equals("fluid") || !menu.slots.get(1).hasItem()))
                text(g, p.outputKind().equals("fluid") ? Quantities.fluid(Quantities.compact(p.outputCount())).getString() : Quantities.compact(p.outputCount()), 246, 89, 38, MUTED);
        } else if (!p.outputs().isEmpty()) {
            var output = p.outputs().getFirst();
            ScreenChrome.twoLines(g, font, label(output), 209, 73, 75, output.known() ? TEXT : WARNING);
            text(g, matter(output.required()), 209, 98, 65, ACCENT);
            if (p.outputs().size() > 1 || !p.complete()) g.text(font, "+", 278, 98, MUTED, false);
        }
        var work = menu.progress();
        if (work.duration() > 0) {
            String progress = Component.translatable("screen.rematter.percent", Math.clamp(100L * work.progress() / work.duration(), 0, 100)).getString();
            g.text(font, progress, (processArrowStart() + 200 - font.width(progress)) / 2, 100, MUTED, false);
        }
        String status = (s.repeat() && selecting() ? joined(statusText(), tr("continuous")) : statusText()).getString();
        boolean activeWork = working(s.status());
        ScreenChrome.icon(g, activeWork ? Icon.PLAY : s.status().equals("ready") ? Icon.CHECK : Icon.INFO,
                12, 119, activeWork ? ACCENT : TEXT);
        ScreenChrome.twoLines(g, font, status, 30, 118, primary.visible ? 129 : 254, activeWork ? ACCENT : TEXT);
    }

    private void drawSlotLabel(GuiGraphicsExtractor g, int index, MachineSnapshot s) {
        var slot = menu.slots.get(index);
        Component caption = slotLabel(index, s);
        if (index != 1 && s.networkJobs() && !isDiskSlot(index)) caption = tr("terminal.network");
        else if (index == 0 && groupedIngredients()) caption = tr("input");
        String label = fitted(caption.getString(), index == 1 ? 78 : 42);
        g.text(font, label, slot.x + 8 - font.width(label) / 2, slot.y - 12, index == 1 ? ACCENT : MUTED, false);
    }

    private void upgradeLabels(GuiGraphicsExtractor g, MachineSnapshot s) {
        ScreenChrome.icon(g, Icon.SPEED, 23, 67, ACCENT);
        text(g, tr("speed_short").getString(), 42, 69, 96, TEXT);
        ScreenChrome.icon(g, Icon.EFFICIENCY, 161, 67, ACCENT);
        text(g, tr("efficiency_short").getString(), 180, 69, 96, TEXT);
        if (menu.slots.get(2).isActive()) {
            ScreenChrome.icon(g, Icon.UPGRADES, 24, 126, UPGRADE);
            text(g, tr("upgrades").getString(), 68, 116, 210, TEXT);
        }
        String cost = (s.preview().id().isEmpty() ? Quantities.energy(Quantities.compact(menu.progress().energy()))
                : Component.translatable("screen.rematter.preview_cost", Quantities.compact(s.preview().energy()), Quantities.seconds(s.preview().ticks()))).getString();
        text(g, cost, 68, 131, 210, MUTED);
    }

    private void configurationLabels(GuiGraphicsExtractor g, MachineSnapshot s) {
        String heading = tr("automation_explain").getString();
        var v = s.structure();
        if (industrial()) heading = (v.formed() ? Component.translatable("screen.rematter.structure_heading", v.width(), v.depth(), v.height(),
                v.workRate(), v.energySavingPercent()) : statusText()).getString();
        else if (menu.kind() == MachineKind.NETWORK_INTERFACE) heading = statusText().getString();
        if (disk() && java.util.stream.IntStream.of(1, 2, 3, 4).anyMatch(i -> menu.slots.get(i).hasItem()))
            heading = tr("drive_recovery").getString();
        ScreenChrome.icon(g, Icon.CONFIGURATION, 12, 52, MUTED);
        ScreenChrome.twoLines(g, font, heading, 30, 50, ports.visible ? 167 : 254, TEXT);
        boolean hasSlots = false;
        for (int index : new int[]{0, 1, 3, 4}) {
            if (menu.slots.get(index).isActive()) {
                drawSlotLabel(g, index, s);
                hasSlots = true;
            }
        }
        if (!hasSlots) {
            if (handPreparator()) {
                ScreenChrome.icon(g, Icon.CRANK, 14, 80, ACCENT);
                text(g, tr("hand_powered").getString(), 33, 82, 249, MUTED);
            } else if (s.network().nodes() > 0) {
                ScreenChrome.icon(g, Icon.NETWORK, 14, 80, ACCENT);
                text(g, Component.translatable("screen.rematter.network_summary", s.network().nodes(), s.network().storages(), s.network().cells()).getString(),
                        33, 82, 249, MUTED);
            } else {
                ScreenChrome.icon(g, passive() ? Icon.STORAGE : Icon.POWER, 14, 80, ACCENT);
                text(g, (passive() ? capacity(Quantities.compact(s.used()), Quantities.compact(s.capacity()))
                        : Quantities.energy(Quantities.compact(menu.progress().energy()))).getString(), 33, 82, 249, MUTED);
            }
        }
        if (menu.slots.get(2).isActive()) ScreenChrome.icon(g, Icon.UPGRADES, 24, 126, UPGRADE);
        if (s.queuedBatches() > 0 && !repeat.visible && !backend.visible) {
            ScreenChrome.icon(g, Icon.REPEAT, 108, 128, MUTED);
            g.text(font, Integer.toString(s.queuedBatches()), 126, 130, MUTED, false);
        }
    }

    private void storageLabels(GuiGraphicsExtractor g, MachineSnapshot s) {
        if (disk()) {
            long installed = s.cells().stream().filter(MachineSnapshot.CellView::installed).count();
            text(g, Component.translatable("screen.rematter.drive_cells", installed).getString(), 18, 54, 80, TEXT);
            capacityBar(g, s, 18, 68, 80);
            for (int bay = 0; bay < menu.diskBayCount(); bay++) {
                var slot = menu.slots.get(menu.diskMenuSlot(bay));
                int labelX = slot.x + (MachineCellAccess.driveColumn(bay) == 0 ? -12 : 20);
                g.text(font, Integer.toString(bay + 1), labelX, slot.y + 4, MUTED, false);
            }
        }
        var stock = menu.stock();
        var visibleEntries = entries();
        if (visibleEntries.isEmpty()) {
            String message = tr(!stock.readable() ? "contents_unavailable" : stock.entries().isEmpty() ? "empty_storage" : "no_matches").getString();
            ScreenChrome.twoLines(g, font, message, gridX() + 5, 87, CONTENT_RIGHT - gridX() - 10, MUTED);
        }
        int pageCount = pages(visibleEntries);
        var parts = new ArrayList<Component>();
        // A shortened list leads the footer so the notice is never cut off.
        if (stock.truncated()) parts.add(Component.translatable("screen.rematter.stock_truncated", stock.entries().size(), stock.totalTypes()));
        if (!disk()) parts.add(capacity(Quantities.compact(s.used()), Quantities.compact(s.capacity())));
        if (pageCount > 1) parts.add(Component.translatable("screen.rematter.page", Math.min(page, pageCount - 1) + 1, pageCount));
        String footer = parts.stream().reduce(MachineScreen::joined).map(Component::getString).orElse("");
        int notice = stock.truncated() ? ScreenChrome.ICON_SIZE + 2 : 0;
        int start = gridX() + (previous.visible ? 24 : 2), end = CONTENT_RIGHT - (next.visible ? 24 : 2);
        String label = fitted(footer, end - start - notice);
        int labelX = (start + end - font.width(label) + notice) / 2;
        if (notice > 0) ScreenChrome.icon(g, Icon.INFO, labelX - notice, STOCK_FOOTER, WARNING);
        g.text(font, label, labelX, STOCK_FOOTER + 3, stock.complete() ? ACCENT : WARNING, false);
    }

    private static void capacityBar(GuiGraphicsExtractor g, MachineSnapshot s, int x, int y, int width) {
        ScreenChrome.inset(g, x, y, width, 5);
        int fill = s.capacity() > 0 ? (int) Math.clamp((width - 2) * (double) s.used() / s.capacity(), 0, width - 2) : 0;
        if (fill > 0) g.fill(x + 1, y + 1, x + 1 + fill, y + 4, s.status().equals("unknown_schema") ? WARNING : ACCENT);
    }
    private List<Component> recipeDetails(MachineSnapshot s) {
        var p=s.preview();var lines=new ArrayList<Component>();lines.add(previewTitle(s));
        if(menu.inputRole()&&!preparator()&&s.sampleBatchSize()>1)lines.add(Component.translatable("screen.rematter.composition_batch",s.sampleBatchSize()));
        lines.add(handPreparator()?Component.translatable("screen.rematter.crank_count",(p.ticks()+19)/20)
                :Component.translatable("screen.rematter.preview_cost",number(p.energy()),Quantities.seconds(p.ticks())));
        for(var a:p.inputs())lines.add(Component.translatable("screen.rematter.preview_input",label(a),SubstanceNames.phase(a.phase()),number(a.required()),number(a.available())));
        for(var a:p.outputs())lines.add(Component.translatable("screen.rematter.preview_output",label(a),SubstanceNames.phase(a.phase()),number(a.required())));
        if(p.inputs().stream().anyMatch(a->!a.known())||p.outputs().stream().anyMatch(a->!a.known()))lines.add(tr("unknown_substance_hint"));
        if(!p.complete())lines.add(tr("preview_partial"));return lines;
    }
    /** Every title is resolved from IDs in the player's own language. */
    private Component previewTitle(MachineSnapshot s) {
        var p = s.preview();
        if (p.id().isEmpty()) return s.selectedId().isEmpty() ? tr(selecting() ? "choose_recipe" : "add_input") : SubstanceNames.choice(menu.kind(), s.selectedId());
        if (s.networkJobs()) return orderTitle(p);
        if (p.id().startsWith(MachineSnapshot.ITEM_SOURCE)) return SubstanceNames.item(p.id().substring(MachineSnapshot.ITEM_SOURCE.length()));
        if (preparator()) return SubstanceNames.item(p.outputId());
        if (!selecting() && p.id().startsWith(MachineChoices.FLUID_PREFIX)) return SubstanceNames.fluid(p.id().substring(MachineChoices.FLUID_PREFIX.length()));
        return SubstanceNames.choice(menu.kind(), p.id());
    }
    private Component orderTitle(MachineSnapshot.Preview p) {
        var title = Component.empty();
        for (var resource : p.resources()) {
            if (!title.getSiblings().isEmpty()) title.append(" + ");
            title.append(Component.translatable("screen.rematter.order_amount", number(resource.amount()),
                    resource.kind().equals("fluid") ? SubstanceNames.fluid(resource.id()) : SubstanceNames.item(resource.id())));
        }
        if (!p.resources().isEmpty()) title.append(" → ");
        Component output = switch (p.outputKind()) {
            case "item" -> Component.translatable("screen.rematter.order_amount", number(p.outputCount()), SubstanceNames.item(p.outputId()));
            case "fluid" -> Component.translatable("screen.rematter.order_amount", number(p.outputCount()), SubstanceNames.fluid(p.outputId()));
            default -> p.outputs().isEmpty() ? Component.literal(p.outputId()) : name(p.outputs().getFirst());
        };
        return title.append(output);
    }
    private List<Component> structureDetails(MachineSnapshot s) {
        var lines=new ArrayList<Component>();if(!industrial())return lines;var v=s.structure();
        if(v.width()>0)lines.add(Component.translatable("screen.rematter.structure_size",v.width(),v.depth(),v.height()));
        if(!v.formed()){lines.add(Component.translatable("status.rematter."+v.diagnostic()));lines.add(Component.translatable("screen.rematter.help_"+v.diagnostic()));lines.add(tr(menu.kind()==MachineKind.INDUSTRIAL_PREPARATOR?"preparation_stack":"reactor_stack"));}
        else {
            lines.add(Component.translatable("screen.rematter.structure_speed",v.workRate()));
            lines.add(Component.translatable("screen.rematter.structure_efficiency",v.energySavingPercent()));
            if(menu.kind()==MachineKind.INDUSTRIAL_PREPARATOR) {
                moduleDetail(lines,"grinding_unit",v.grinding(),"module_throughput");
                moduleDetail(lines,"mixing_basin",v.mixing(),"module_throughput");
                moduleDetail(lines,"separation_unit",v.separation(),"module_efficiency");
            } else {
                moduleDetail(lines,"reaction_chamber",v.chambers(),"module_throughput");
                moduleDetail(lines,"field_coil",v.coils(),"module_efficiency");
            }
            lines.add(tr("structure_scaling_hint"));
        }return lines;
    }
    private static void moduleDetail(List<Component> lines,String id,int count,String benefit) {
        lines.add(Component.translatable("screen.rematter.module_detail",count,Component.translatable("block.rematter."+id),tr(benefit)));
    }
    private List<Component> storageDetails(MachineSnapshot s) {
        var lines=new ArrayList<Component>();
        if(handPreparator()){lines.add(tr("hand_powered"));lines.add(tr("crank_hint"));return lines;}
        if(menu.kind()==MachineKind.NETWORK_INTERFACE)lines.add(statusText());
        lines.add(tr("source_"+s.storageSource()));lines.add(capacity(number(s.used()),number(s.capacity())));
        if(menu.kind().processes())lines.add(energyLine());
        if(disk()) {
            lines.add(Component.translatable("screen.rematter.drive_cells",s.cells().stream().filter(MachineSnapshot.CellView::installed).count()));
            if(s.cells().stream().anyMatch(c->c.installed()&&!c.readable()))lines.add(tr("drive_blocked"));
            for(int bay=0;bay<s.cells().size();bay++) {
                var cell=s.cells().get(bay);if(!cell.installed())continue;
                lines.add(Component.translatable("screen.rematter.drive_bay_detail",Component.translatable("screen.rematter.drive_bay",bay+1),SubstanceNames.item(cell.itemId()),
                        cell.readable()?Component.translatable("screen.rematter.cell_types",cell.types(),cell.maxTypes()):tr("cell_unreadable")));
            }
        }
        var n=s.network();
        if(!n.status().equals("none") && (configuration() || !disk())) {
            lines.add(Component.translatable("screen.rematter.network_summary",n.nodes(),n.storages(),n.cells()));
            if(n.status().equals("ready") && n.storages()>1 && !s.storageSource().equals("network"))lines.add(Component.translatable("screen.rematter.network_capacity",number(n.used()),number(n.capacity()),n.types()));
            else if(!n.status().equals("ready"))lines.add(Component.translatable("status.rematter."+n.status()));
        }
        if(s.fluidAmount()>0)lines.add(Component.translatable("screen.rematter.fluid_amount",SubstanceNames.fluid(s.fluidId()),number(s.fluidAmount())));
        var stock=menu.stock();
        if(stock.truncated()){lines.add(Component.translatable("screen.rematter.partial_contents_hint",stock.entries().size(),stock.totalTypes()));lines.add(tr("truncated_hint"));}
        else if(!stock.readable())lines.add(tr("contents_unavailable"));
        return lines;
    }
    private static Component stockTooltip(MachineSnapshot.Entry e, String label) {
        var lines = joined(Component.literal(label), SubstanceNames.phase(e.phase())).append("\n").append(Component.translatable("screen.rematter.stock_stored", number(e.amount())));
        if (e.available() != e.amount()) lines.append("\n").append(Component.translatable("screen.rematter.stock_available", number(e.available())));
        if (!e.known()) lines.append("\n").append(tr("unknown_substance_hint"));
        return lines.append("\n").append(tr("substance_details_hint"));
    }
    private static SubstanceKey key(String id,String phase) {
        try { return new SubstanceKey(SubstanceId.parse(id),Phase.valueOf(phase.toUpperCase(Locale.ROOT))); }
        catch(IllegalArgumentException invalid) { return null; }
    }
    private static void substanceIcon(GuiGraphicsExtractor g,SubstanceKey key,int x,int y) {
        if(key!=null)SubstanceIcon.draw(g,x,y,key);
        else ScreenChrome.icon(g,Icon.INFO,x+2,y+2,MUTED);
    }
    private static int phaseColor(String phase) {return switch(phase){case "liquid"->LIQUID;case "gas"->GAS;default->WARNING;};}
    private String fitted(String value,int width) {
        return ScreenChrome.fit(font,value,width);
    }
    private void text(GuiGraphicsExtractor g,String v,int x,int y,int width,int color){g.text(font,fitted(v,width),x,y,color,false);}
    private ItemStack outputIcon(MachineSnapshot.Preview p){try{Identifier id=Identifier.parse(p.outputId());if(p.outputKind().equals("item")&&BuiltInRegistries.ITEM.containsKey(id))return new ItemStack(BuiltInRegistries.ITEM.getValue(id));if(p.outputKind().equals("fluid")&&BuiltInRegistries.FLUID.containsKey(id))return new ItemStack(BuiltInRegistries.FLUID.getValue(id).getBucket());}catch(RuntimeException ignored){}return ItemStack.EMPTY;}
    private static String number(long v){return Quantities.number(v);}
    private static String matter(long v){return Quantities.matter(Quantities.compact(v)).getString();}
    private static Component capacity(String used,String capacity){return Component.translatable("screen.rematter.capacity",used,capacity);}
    private static MutableComponent joined(Component first,Component second){return Component.translatable("screen.rematter.joined",first,second);}
    private Component energyLine(){return Component.translatable("screen.rematter.energy_buffer",number(menu.progress().energy()),number(MachineBlockEntity.ENERGY_CAPACITY));}
    private class ConsoleButton extends Button {
        private Component explainedLabel, explainedHint;
        ConsoleButton(Builder b){super(b);}
        /** The tooltip names the control and explains it; it is rebuilt only when either text changes. */
        void explain(Component hint) {
            Component label=getMessage();
            if(label.equals(explainedLabel)&&hint.equals(explainedHint))return;
            explainedLabel=label;explainedHint=hint;
            setTooltip(Tooltip.create(label.getString().isBlank()||label.getString().equals(hint.getString())?hint:label.copy().append("\n").append(hint)));
        }
        void background(GuiGraphicsExtractor g,boolean selected){ScreenChrome.raised(g,getX(),getY(),getWidth(),getHeight(),selected||isHoveredOrFocused(),active);}
        private boolean toggled(){var s=menu.snapshot();return this==automation&&s.automation()||this==networkJobs&&s.networkJobs()||this==repeat&&s.repeat();}
        private Icon symbol() {
            if(this==primary)return switch(MachinePrimaryAction.forSnapshot(menu.snapshot()).action()) {
                case PAUSE->Icon.PAUSE;case CHOOSE_RECIPE->Icon.RECIPE;case CRANK->Icon.CRANK;case LOCAL_CONTROL->Icon.BACK;
                default->Icon.PLAY;
            };
            if(this==choose)return Icon.RECIPE;
            if(this==automation)return Icon.AUTOMATION;
            if(this==networkJobs||this==backend)return Icon.NETWORK;
            if(this==terminalDevices)return Icon.NETWORK;
            if(this==returnToTerminal)return Icon.BACK;
            if(this==repeat)return Icon.REPEAT;
            if(this==addBatch)return Icon.ADD;
            if(this==previous)return Icon.PREVIOUS;
            if(this==next)return Icon.NEXT;
            return null;
        }
        private String visualLabel() {
            if(this==networkJobs)return tr(toggled()?"network_jobs_short_on":"network_jobs_short_off").getString();
            if(this==addBatch||this==previous||this==next)return "";
            return getMessage().getString();
        }
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float partial){
            background(g,toggled());Icon symbol=symbol();
            String label=fitted(visualLabel(),getWidth()-(symbol==null?8:24));
            int textWidth=font.width(label),contentWidth=(symbol==null?0:12)+(label.isEmpty()?0:textWidth+(symbol==null?0:6));
            int x=getX()+(getWidth()-contentWidth)/2;
            if(symbol!=null)ScreenChrome.icon(g,symbol,x,getY()+(getHeight()-12)/2,active?this==primary||toggled()?ACCENT_BRIGHT:TEXT:DISABLED);
            if(!label.isEmpty())g.text(font,label,x+(symbol==null?0:18),getY()+(getHeight()-8)/2,active?TEXT:DISABLED,false);
        }
    }
    private final class TabButton extends ConsoleButton {
        private final MachineMenu.View view;

        TabButton(Builder builder, MachineMenu.View view) {
            super(builder);
            this.view = view;
        }

        private Icon icon() {
            return switch (view) {
                case MAIN -> passive() ? Icon.STORAGE : Icon.PLAY;
                case UPGRADES -> Icon.UPGRADES;
                case CONFIGURATION -> Icon.CONFIGURATION;
            };
        }

        @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float partial) {
            boolean selected = menu.view() == view;
            background(g, selected);
            int color = active ? selected ? ACCENT_BRIGHT : TEXT : DISABLED;
            if (sideTabs) {
                ScreenChrome.icon(g, icon(), getX() + (getWidth() - ScreenChrome.ICON_SIZE) / 2,
                        getY() + (getHeight() - ScreenChrome.ICON_SIZE) / 2, color);
                if (selected) g.fill(getX() + getWidth() - 2, getY() + 3, getX() + getWidth(), getY() + getHeight() - 3, ACCENT);
                return;
            }
            ScreenChrome.icon(g, icon(), getX() + 6, getY() + 3, color);
            String label = fitted(getMessage().getString(), getWidth() - 26);
            g.text(font, label, getX() + 22, getY() + 5, color, false);
            if (selected) g.fill(getX() + 3, getY() + getHeight() - 3, getX() + getWidth() - 3, getY() + getHeight() - 2, ACCENT);
        }
    }

    /** A toggle in the stock search row. Its icon takes the filter's colour while it is on. */
    private final class ChipButton extends ConsoleButton {
        private final Icon icon;
        private final int color;
        private final BooleanSupplier on;

        ChipButton(Builder builder, Icon icon, int color, BooleanSupplier on) {
            super(builder);
            this.icon = icon;
            this.color = color;
            this.on = on;
        }

        @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float partial) {
            boolean selected = on.getAsBoolean();
            background(g, selected);
            ScreenChrome.icon(g, icon, getX() + (getWidth() - ScreenChrome.ICON_SIZE) / 2, getY() + (getHeight() - ScreenChrome.ICON_SIZE) / 2,
                    selected ? color : MUTED);
        }
    }

    private final class StockButton extends ConsoleButton {
        private MachineSnapshot.Entry entry;
        private ChemistryRegistry catalogue;
        private SubstanceKey key;
        private String name = "", amount = "";
        private boolean favorite;

        StockButton(Builder builder) { super(builder); }

        /** Names, icon key and tooltip are resolved once per shown row and catalogue. */
        void show(MachineSnapshot.Entry value) {
            if (java.util.Objects.equals(value, entry) && catalogue == ClientChemistry.current()) return;
            entry = value;
            catalogue = ClientChemistry.current();
            if (value == null) return;
            key = key(value.id(), value.phase());
            name = name(value).getString();
            amount = matter(value.amount());
            String label = value.known() ? SubstanceLabels.shortLabel(value.id(), name) : name;
            setMessage(Component.translatable("screen.rematter.stock_entry", label, SubstanceNames.phase(value.phase()),
                    Quantities.matter(number(value.amount()))));
            setTooltip(Tooltip.create(stockTooltip(value, label)));
        }

        @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float partial) {
            if (entry == null) return;
            background(g, false);
            substanceIcon(g, key, getX() + 3, getY() + 1);
            int left = getX() + 22;
            int available = getWidth() - 26;
            text(g, name, left, getY() + 1, available, entry.known() ? TEXT : WARNING);
            text(g, amount, left, getY() + 9, available, ACCENT);
            if (favorite) {
                int right = getX() + getWidth() - 4;
                g.fill(right - 3, getY() + 2, right, getY() + 3, ACCENT_BRIGHT);
                g.fill(right - 2, getY() + 1, right - 1, getY() + 4, ACCENT_BRIGHT);
            }
        }
    }
    /** Quantity details are a real accessible screen, never an inventory slot or an implicit withdrawal. */
    private final class StockDetailsScreen extends Screen {
        private final String id,phase;private final SubstanceKey key;private int x,y;
        private ConsoleButton favoriteButton;private MachineMenu.Stock seen;private MachineSnapshot.Entry entry;
        StockDetailsScreen(String id,String phase){super(tr("substance_details"));this.id=id;this.phase=phase;key=key(id,phase);}
        @Override protected void init(){x=(width-244)/2;y=(height-170)/2;
            favoriteButton=addRenderableWidget(new ConsoleButton(Button.builder(Component.empty(),b->toggleFavorite()).bounds(x+11,y+141,140,18)));
            addRenderableWidget(Button.builder(tr("back_short"),b->onClose()).bounds(x+163,y+141,70,18).build(ConsoleButton::new));
            refreshFavorite();}
        /** Favorites are stored on this client for the current player; the server never sees them. */
        private void toggleFavorite(){UUID player=player();if(player!=null&&key!=null)StockFavorites.get().toggle(player,new MachineSnapshot.StockKey(id,phase));refreshFavorite();}
        private void refreshFavorite(){boolean on=key!=null&&favorite(new MachineSnapshot.StockKey(id,phase));
            favoriteButton.setMessage(tr(on?"favorite_remove":"favorite_add"));favoriteButton.active=key!=null&&player()!=null;}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void tick(){if(minecraft.player==null||minecraft.player.containerMenu!=menu)onClose();}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){ScreenChrome.panel(g,x,y,244,170);var stock=menu.stock();
            if(stock!=seen){seen=stock;entry=stock.entries().stream().filter(e->e.id().equals(id)&&e.phase().equals(phase)).findFirst().orElse(null);}
            String name=entry==null?id:name(entry).getString();substanceIcon(g,key,x+11,y+12);ScreenChrome.twoLines(g,font,name,x+34,y+11,198,TEXT);
            String symbol=SubstanceLabels.iconLabel(id);text(g,(symbol.isEmpty()?SubstanceNames.phase(phase):joined(Component.literal(symbol),SubstanceNames.phase(phase))).getString(),x+34,y+33,198,phaseColor(phase));
            String stored=entry==null?(stock.complete()?"0":"?"):number(entry.amount());String available=entry==null?(stock.complete()?"0":"?"):number(entry.available());
            ScreenChrome.twoLines(g, font, Component.translatable("screen.rematter.stock_stored", stored).getString(), x + 11, y + 50, 222, TEXT);
            ScreenChrome.twoLines(g, font, Component.translatable("screen.rematter.stock_available", available).getString(), x + 11, y + 71, 222, TEXT);
            ScreenChrome.twoLines(g, font, tr("quantity_not_item").getString(), x + 11, y + 93, 222, MUTED);
            text(g, id, x + 11, y + 114, 222, MUTED);
            if (entry != null && !entry.known()) text(g, tr("unknown_substance_hint").getString(), x + 11, y + 126, 222, WARNING);
            else if (!stock.complete()) text(g, Component.translatable("screen.rematter.partial_contents_hint", stock.entries().size(), stock.totalTypes()).getString(), x + 11, y + 126, 222, WARNING);
            super.extractRenderState(g, mx, my, partial);
        }
    }
    /**
     * The explicit confirmation before a stopped delivery is released: the player sees what the order holds and where it
     * goes. The server checks the order again and hands out only what it can.
     */
    private final class ReleaseConfirmScreen extends Screen {
        private static final int SHOWN = 4;
        private final MachineSnapshot.OrdersView orders;private final List<Component> lines = new ArrayList<>();private int x,y;
        ReleaseConfirmScreen(MachineSnapshot.OrdersView orders){super(tr("release_title"));this.orders=orders;
            orders.matter().forEach(m->lines.add(joined(Component.literal(label(m)),Quantities.matter(number(m.required())))));
            orders.resources().forEach(r->lines.add(r.kind().equals("fluid")?joined(SubstanceNames.fluid(r.id()),Quantities.fluid(number(r.amount())))
                    :Component.translatable("gui.rematter.choice.items",number(r.amount()),SubstanceNames.item(r.id()))));}
        @Override protected void init(){x=(width-244)/2;y=(height-170)/2;
            addRenderableWidget(Button.builder(tr("release_confirm"),b->confirm()).bounds(x+11,y+141,140,18).build(ConsoleButton::new));
            addRenderableWidget(Button.builder(tr("back_short"),b->onClose()).bounds(x+163,y+141,70,18).build(ConsoleButton::new));}
        private void confirm(){
            if(minecraft.player!=null&&minecraft.player.containerMenu==menu&&menu.snapshot().orders().held().equals(orders.held()))
                ClientPacketDistributor.sendToServer(new ReleaseHeldOrderPayload(menu.containerId,UUID.fromString(orders.held())));
            onClose();}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void tick(){if(minecraft.player==null||minecraft.player.containerMenu!=menu||!menu.snapshot().orders().held().equals(orders.held()))onClose();}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){ScreenChrome.panel(g,x,y,244,170);
            ScreenChrome.icon(g,Icon.INFO,x+11,y+11,WARNING);text(g,getTitle().getString(),x+30,y+13,202,TEXT);
            ScreenChrome.twoLines(g,font,tr("release_explain").getString(),x+11,y+30,222,MUTED);
            for(int i=0;i<Math.min(SHOWN,lines.size());i++)text(g,lines.get(i).getString(),x+11,y+56+i*12,222,TEXT);
            int hidden=lines.size()-Math.min(SHOWN,lines.size());
            if(hidden>0||!orders.complete())text(g,tr("release_more").getString(),x+11,y+56+SHOWN*12,222,MUTED);
            ScreenChrome.twoLines(g,font,tr("release_warning").getString(),x+11,y+118,222,WARNING);
            super.extractRenderState(g,mx,my,partial);}
    }
}
