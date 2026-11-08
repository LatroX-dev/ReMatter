package dev.latrox.rematter.client;

import static dev.latrox.rematter.client.UiPalette.*;
import dev.latrox.rematter.client.ScreenChrome.Icon;

import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.content.MachineCellAccess;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.content.MachinePortMode;
import dev.latrox.rematter.content.MachineSnapshot;
import dev.latrox.rematter.network.TerminalDevicesPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

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
    private final List<StockButton> stockButtons = new ArrayList<>();
    private EditBox search;
    private Button primary, choose, automation, networkJobs, backend, repeat, addBatch, previous, next;
    private Button terminalDevices, returnToTerminal;
    private TabButton processTab, upgradesTab, configurationTab;
    private Button ports;
    private boolean sideTabs;
    private int page;

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
        search.setResponder(value -> page = 0);

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
        repeat = control("", 12, 126, 224, 18, b -> send(5), "continuous_hint");
        addBatch = control("", 240, 126, 44, 18, b -> send(4), "add_batch_hint");
        previous = control("previous_hint", 12, STOCK_FOOTER, 20, 13, b -> page = Math.max(0, page - 1), "previous_hint");
        next = control("next_hint", 264, STOCK_FOOTER, 20, 13, b -> page++, "next_hint");
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
    private Button control(String key, int x, int y, int width, int height, Button.OnPress action, String hint) {
        var button = addRenderableWidget(Button.builder(key.isEmpty() ? Component.empty() : tr(key), action)
                .bounds(leftPos + x, topPos + y, width, height).build(ConsoleButton::new));
        explain(button,tr(hint));return button;
    }
    private static void explain(Button button, Component hint) {
        Component label=button.getMessage();
        button.setTooltip(Tooltip.create(label.getString().isBlank()||label.getString().equals(hint.getString())?hint:label.copy().append("\n").append(hint)));
    }
    private TabButton tab(MachineMenu.View view, int x, int width, String key) {
        int tabX = sideTabs ? leftPos - SIDE_TAB_WIDTH + 1 : leftPos + x;
        int tabY = sideTabs ? SIDE_TAB_TOP + view.ordinal() * SIDE_TAB_PITCH : 25;
        return addRenderableWidget(new TabButton(Button.builder(tr(key), b -> { menu.setView(view); page = 0; })
                .bounds(tabX, topPos + tabY, sideTabs ? SIDE_TAB_WIDTH : width, sideTabs ? SIDE_TAB_HEIGHT : 18)
                .tooltip(Tooltip.create(tr(key))), view));
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
    private void openTerminalDevices() {
        menu.acceptTerminalDevices(TerminalDevicesPayload.EMPTY);
        send(MachineMenu.OPEN_TERMINAL_DEVICES);
        minecraft.pushGuiLayer(new TerminalDevicesScreen(menu));
    }
    private void send(int action) { if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action); }
    private Component statusText() {
        var s = menu.snapshot();
        if (s.status().equals("structure") && industrial()) return Component.translatable("status.rematter." + s.structure().diagnostic());
        if (working(s.status())) return tr("processing");
        return Component.translatable("status.rematter." + s.status());
    }
    private List<MachineSnapshot.Entry> entries() {
        String query = search == null ? "" : search.getValue().strip().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return menu.snapshot().entries();
        return menu.snapshot().entries().stream().filter(e -> e.name().toLowerCase(Locale.ROOT).contains(query)
                || e.id().toLowerCase(Locale.ROOT).contains(query) || e.phase().contains(query)
                || SubstanceLabels.matchesSymbol(SubstanceLabels.iconLabel(e.id()), query)).toList();
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
        processTab.setTooltip(Tooltip.create(processTab.getMessage()));
        upgradesTab.visible = ready && menu.supportsUpgrades();
        upgradesTab.active = ready;
        if (sideTabs) configurationTab.setY(topPos + SIDE_TAB_TOP + (upgradesTab.visible ? 2 : 1) * SIDE_TAB_PITCH);
        explain(upgradesTab,tr("upgrade_hint").copy().append("\n").append(tr("upgrade_limit")).append("\n").append(tr("upgrade_effect_hint")));
        search.visible = search.active = ready && main() && passive();
        search.setX(leftPos + gridX() + 5);
        search.setWidth(CONTENT_RIGHT - gridX() - 10);
        primary.visible = ready && main() && decision.visible(); primary.active = ready && decision.enabled();
        primary.setMessage(tr(decision.labelKey())); explain(primary,tr(decision.hintKey()));
        choose.visible = ready && main() && !s.networkJobs() && selecting() && !decision.opensRecipeSelection(); choose.active = ready && s.choiceCount() > 0;
        automation.visible = ready && configuration(); automation.setMessage(tr(s.automation() ? "automation_on" : "automation_off"));
        explain(automation,tr("automation_hint"));
        repeat.visible = addBatch.visible = ready && configuration() && selecting() && !s.networkJobs();
        boolean valid = s.selected() >= 0 && !s.preview().id().isEmpty() && s.selectedId().equals(s.preview().id()) && !s.status().equals("selection_unavailable");
        repeat.active = valid; repeat.setMessage(tr(s.repeat() ? "stop_repeat" : "run_repeat"));
        explain(repeat,tr("continuous_hint"));
        addBatch.active = valid && !s.repeat() && s.queuedBatches() < 64;
        boolean recoveringUpgrade = configuration() && menu.slots.get(2).isActive();
        boolean canNetwork = menu.kind().processes() && menu.kind()!=MachineKind.ANALYZER && !handPreparator() && !recoveringUpgrade;
        networkJobs.visible = ready && configuration() && canNetwork;
        networkJobs.setMessage(tr(s.networkJobs()?"network_jobs_on":"network_jobs_off"));
        explain(networkJobs,tr("network_jobs_hint"));
        backend.visible = ready && configuration() && menu.kind()==MachineKind.NETWORK_INTERFACE;
        String backendName=switch(s.selectedName()){case "ae2"->"AE2";case "rs"->"Refined Storage";default->"Unavailable";};
        backend.setMessage(Component.translatable("screen.rematter.backend",backendName));
        explain(backend,tr("backend_hint"));
        automation.setWidth(canNetwork ? 134 : 272);
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
            b.entry = i < perPage() && index < values.size() ? values.get(index) : null;
            b.visible = b.active = ready && main() && passive() && b.entry != null;
            b.setX(leftPos + gridX() + i % gridColumns() * tilePitch());
            b.setY(topPos + STOCK_TOP + i / gridColumns() * STOCK_PITCH);
            b.setWidth(tilePitch() - 2);
            if (b.entry != null) {
                String label = SubstanceLabels.shortLabel(b.entry.id(), b.entry.name());
                b.setMessage(Component.literal(label + ", " + b.entry.phase() + ", " + number(b.entry.amount()) + " u"));
                b.setTooltip(Tooltip.create(stockTooltip(b.entry, label)));
            }
        }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        updateWidgets(); super.extractRenderState(g, mouseX, mouseY, partialTick);
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
                g.setTooltipForNextFrame(handPreparator() ? tr("crank_hint") : Component.literal(number(s.energy()) + " / 1,000,000 FE"), mouseX, mouseY);
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
            if (search.isFocused()) g.fill(x + gridX() + 1, y + 67, x + CONTENT_RIGHT - 1, y + 68, ACCENT);
            if (disk()) ScreenChrome.inset(g, x + 12, y + 49, 92, 95);
        } else if (main() && !passive()) {
            if (!handPreparator()) {
                ScreenChrome.inset(g, x + 14, y + 74, 12, 34);
                int fill = (int) Math.clamp(30L * s.energy() / 1_000_000, 0, 30);
                g.fill(x + 16, y + 106 - fill, x + 24, y + 106, ACCENT);
            }
            int arrowStart = processArrowStart(), arrowEnd = 200;
            g.fill(x + arrowStart, y + 88, x + arrowEnd, y + 94, HIGHLIGHT);
            g.fill(x + arrowEnd - 4, y + 84, x + arrowEnd, y + 98, HIGHLIGHT);
            g.fill(x + arrowEnd, y + 86, x + arrowEnd + 4, y + 96, HIGHLIGHT);
            int progress = s.duration() > 0 ? (int) Math.clamp((long) (arrowEnd - arrowStart) * s.progress() / s.duration(), 0, arrowEnd - arrowStart) : 0;
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
        String name = !p.name().isBlank() ? p.name() : !s.selectedName().isBlank() ? s.selectedName()
                : tr(selecting() ? "choose_recipe" : "add_input").getString();
        ScreenChrome.icon(g, Icon.RECIPE, 12, 52, MUTED);
        ScreenChrome.twoLines(g, font, name, 30, 50, choose.visible ? 162 : 254, TEXT);
        if (handPreparator()) ScreenChrome.icon(g, Icon.CRANK, 14, 87, ACCENT);
        if (menu.inputRole()) {
            for (int index : new int[]{0, 3, 4}) if (menu.slots.get(index).isActive()) drawSlotLabel(g, index, s);
        } else if (p.inputs().size() == 1) {
            var ingredient = p.inputs().getFirst();
            ScreenChrome.twoLines(g, font, SubstanceLabels.shortLabel(ingredient.id(), ingredient.name()), 38, 75, 90,
                    ingredient.available() < ingredient.required() ? WARNING : TEXT);
            text(g, compact(ingredient.required()) + " u", 38, 99, 90, MUTED);
        } else if (!p.inputs().isEmpty()) {
            for (int i = 0; i < Math.min(2, p.inputs().size()); i++) {
                var ingredient = p.inputs().get(i);
                text(g, SubstanceLabels.shortLabel(ingredient.id(), ingredient.name()), 38, 72 + i * 19, 90,
                        ingredient.available() < ingredient.required() ? WARNING : TEXT);
                text(g, compact(ingredient.required()) + " u", 38, 81 + i * 19, 80, MUTED);
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
                text(g, compact(p.outputCount()) + (p.outputKind().equals("fluid") ? " mB" : ""), 246, 89, 38, MUTED);
        } else if (!p.outputs().isEmpty()) {
            var output = p.outputs().getFirst();
            ScreenChrome.twoLines(g, font, SubstanceLabels.shortLabel(output.id(), output.name()), 209, 73, 75, TEXT);
            text(g, compact(output.required()) + " u", 209, 98, 65, ACCENT);
            if (p.outputs().size() > 1 || !p.complete()) g.text(font, "+", 278, 98, MUTED, false);
        }
        if (s.duration() > 0) {
            String progress = Math.clamp(100L * s.progress() / s.duration(), 0, 100) + "%";
            g.text(font, progress, (processArrowStart() + 200 - font.width(progress)) / 2, 100, MUTED, false);
        }
        String status = statusText().getString();
        if (s.repeat() && selecting()) status += " · " + tr("continuous").getString();
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
        String cost = s.preview().id().isEmpty() ? compact(s.energy()) + " FE"
                : compact(s.preview().energy()) + " FE · " + String.format(Locale.ROOT, "%.1f s", s.preview().ticks() / 20.0);
        text(g, cost, 68, 131, 210, MUTED);
    }

    private void configurationLabels(GuiGraphicsExtractor g, MachineSnapshot s) {
        String heading = tr("automation_explain").getString();
        if (industrial()) heading = s.structure().formed()
                ? dimensions(s) + " · " + s.structure().workRate() + "× · −" + s.structure().energySavingPercent() + "% FE"
                : statusText().getString();
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
                text(g, passive() ? compact(s.used()) + " / " + compact(s.capacity()) + " u" : compact(s.energy()) + " FE", 33, 82, 249, MUTED);
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
        var visibleEntries = entries();
        if (visibleEntries.isEmpty()) {
            String message = tr(!s.entriesComplete() ? "contents_unavailable" : search.getValue().isBlank() ? "empty_storage" : "no_matches").getString();
            ScreenChrome.twoLines(g, font, message, gridX() + 5, 87, CONTENT_RIGHT - gridX() - 10, MUTED);
        }
        int pageCount = pages(visibleEntries);
        String pagination = pageCount > 1 ? (page + 1) + " / " + pageCount : "";
        String capacity = disk() ? "" : compact(s.used()) + " / " + compact(s.capacity()) + " u";
        String footer = pagination.isEmpty() ? capacity : capacity.isEmpty() ? pagination : capacity + " · " + pagination;
        if (!s.entriesComplete()) footer = footer.isEmpty() ? tr("partial_short").getString() : footer + " · " + tr("partial_short").getString();
        int start = gridX() + (previous.visible ? 24 : 2), end = CONTENT_RIGHT - (next.visible ? 24 : 2);
        String label = fitted(footer, end - start);
        g.text(font, label, (start + end - font.width(label)) / 2, STOCK_FOOTER + 3, s.entriesComplete() ? ACCENT : WARNING, false);
    }

    private static void capacityBar(GuiGraphicsExtractor g, MachineSnapshot s, int x, int y, int width) {
        ScreenChrome.inset(g, x, y, width, 5);
        int fill = s.capacity() > 0 ? (int) Math.clamp((width - 2) * (double) s.used() / s.capacity(), 0, width - 2) : 0;
        if (fill > 0) g.fill(x + 1, y + 1, x + 1 + fill, y + 4, s.status().equals("unknown_schema") ? WARNING : ACCENT);
    }
    private List<Component> recipeDetails(MachineSnapshot s) {
        var p=s.preview();var lines=new ArrayList<Component>();lines.add(Component.literal(p.name().isBlank()?s.selectedName():p.name()));
        if(menu.inputRole()&&!preparator()&&s.sampleBatchSize()>1)lines.add(Component.translatable("screen.rematter.composition_batch",s.sampleBatchSize()));
        lines.add(handPreparator()?Component.translatable("screen.rematter.crank_count",(p.ticks()+19)/20):Component.literal(String.format(Locale.ROOT,"%,d FE · %.1f s",p.energy(),p.ticks()/20.0)));
        for(var a:p.inputs())lines.add(Component.literal(SubstanceLabels.shortLabel(a.id(),a.name())+" · "+a.phase()+": "+a.required()+" needed / "+a.available()+" available"));
        for(var a:p.outputs())lines.add(Component.literal("→ "+SubstanceLabels.shortLabel(a.id(),a.name())+" · "+a.phase()+": "+a.required()+" u"));
        if(!p.complete())lines.add(tr("preview_partial"));return lines;
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
        lines.add(tr("source_"+s.storageSource()));lines.add(Component.literal(number(s.used())+" / "+number(s.capacity())+" u"));
        if(menu.kind().processes())lines.add(Component.literal(number(s.energy())+" / 1,000,000 FE"));
        if(disk()) {
            lines.add(Component.translatable("screen.rematter.drive_cells",s.cells().stream().filter(MachineSnapshot.CellView::installed).count()));
            if(s.cells().stream().anyMatch(c->c.installed()&&!c.readable()))lines.add(tr("drive_blocked"));
            for(int bay=0;bay<s.cells().size();bay++) {
                var cell=s.cells().get(bay);if(!cell.installed())continue;
                lines.add(Component.translatable("screen.rematter.drive_bay",bay+1).append(": ").append(cell.name())
                        .append(cell.readable()?" · "+cell.types()+" / "+cell.maxTypes()+" types":" · "+tr("cell_unreadable").getString()));
            }
        }
        var n=s.network();
        if(!n.status().equals("none") && (configuration() || !disk())) {
            lines.add(Component.translatable("screen.rematter.network_summary",n.nodes(),n.storages(),n.cells()));
            if(n.status().equals("ready") && n.storages()>1 && !s.storageSource().equals("network"))lines.add(Component.translatable("screen.rematter.network_capacity",number(n.used()),number(n.capacity()),n.types()));
            else if(!n.status().equals("ready"))lines.add(Component.translatable("status.rematter."+n.status()));
        }
        if(s.fluidAmount()>0)lines.add(Component.literal(s.fluidName()+" · "+s.fluidAmount()+" mB"));
        if(!s.entriesComplete())lines.add(tr("partial_contents_hint"));
        return lines;
    }
    private Component stockTooltip(MachineSnapshot.Entry e, String label) { return Component.literal(label+" · "+e.phase()+"\n"+number(e.amount())+" u stored"+(e.available()==e.amount()?"":"\n"+number(e.available())+" u available")+"\n"+tr("substance_details_hint").getString()); }
    private static void substanceIcon(GuiGraphicsExtractor g,String id,String phase,int x,int y) {
        try {
            var key = new SubstanceKey(SubstanceId.parse(id),Phase.valueOf(phase.toUpperCase(Locale.ROOT)));
            SubstanceIcon.draw(g,x,y,key);
        } catch(IllegalArgumentException ignored) {
            ScreenChrome.icon(g,Icon.INFO,x+2,y+2,MUTED);
        }
    }
    private static int phaseColor(String phase) {return switch(phase){case "liquid"->LIQUID;case "gas"->GAS;default->WARNING;};}
    private String dimensions(MachineSnapshot s){var v=s.structure();return v.width()+"×"+v.depth()+"×"+v.height();}
    private String fitted(String value,int width) {
        return ScreenChrome.fit(font,value,width);
    }
    private void text(GuiGraphicsExtractor g,String v,int x,int y,int width,int color){g.text(font,fitted(v,width),x,y,color,false);}
    private ItemStack outputIcon(MachineSnapshot.Preview p){try{Identifier id=Identifier.parse(p.outputId());if(p.outputKind().equals("item")&&BuiltInRegistries.ITEM.containsKey(id))return new ItemStack(BuiltInRegistries.ITEM.getValue(id));if(p.outputKind().equals("fluid")&&BuiltInRegistries.FLUID.containsKey(id))return new ItemStack(BuiltInRegistries.FLUID.getValue(id).getBucket());}catch(RuntimeException ignored){}return ItemStack.EMPTY;}
    private static String number(long v){return String.format(Locale.ROOT,"%,d",v);}
    private static String compact(long v){if(v>=1_000_000_000_000L)return String.format(Locale.ROOT,"%.1e",(double)v);if(v>=1_000_000_000)return String.format(Locale.ROOT,"%.1fB",v/1e9);if(v>=1_000_000)return String.format(Locale.ROOT,"%.1fM",v/1e6);if(v>=10_000)return String.format(Locale.ROOT,"%.1fk",v/1000.0);return Long.toString(v);}
    private class ConsoleButton extends Button {
        ConsoleButton(Builder b){super(b);}
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

    private final class StockButton extends ConsoleButton {
        private MachineSnapshot.Entry entry;

        StockButton(Builder builder) { super(builder); }

        @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float partial) {
            if (entry == null) return;
            background(g, false);
            substanceIcon(g, entry.id(), entry.phase(), getX() + 3, getY() + 1);
            int left = getX() + 22;
            int available = getWidth() - 26;
            text(g, entry.name(), left, getY() + 1, available, TEXT);
            text(g, compact(entry.amount()) + " u", left, getY() + 9, available, ACCENT);
        }
    }
    /** Quantity details are a real accessible screen, never an inventory slot or an implicit withdrawal. */
    private final class StockDetailsScreen extends Screen {
        private final String id,phase;private int x,y;
        StockDetailsScreen(String id,String phase){super(tr("substance_details"));this.id=id;this.phase=phase;}
        @Override protected void init(){x=(width-244)/2;y=(height-170)/2;addRenderableWidget(Button.builder(tr("back_short"),b->onClose()).bounds(x+87,y+141,70,18).build(ConsoleButton::new));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void tick(){if(minecraft.player==null||minecraft.player.containerMenu!=menu)onClose();}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){ScreenChrome.panel(g,x,y,244,170);var entry=menu.snapshot().entries().stream().filter(e->e.id().equals(id)&&e.phase().equals(phase)).findFirst().orElse(null);String name=entry==null?id:entry.name();substanceIcon(g,id,phase,x+11,y+12);ScreenChrome.twoLines(g,font,name,x+34,y+11,198,TEXT);
            String symbol=SubstanceLabels.iconLabel(id);text(g,symbol.isEmpty()?phase:symbol+" · "+phase,x+34,y+33,198,phaseColor(phase));
            String stored=entry==null?(menu.snapshot().entriesComplete()?"0":"?"):number(entry.amount());String available=entry==null?(menu.snapshot().entriesComplete()?"0":"?"):number(entry.available());
            ScreenChrome.twoLines(g, font, Component.translatable("screen.rematter.stock_stored", stored).getString(), x + 11, y + 50, 222, TEXT);
            ScreenChrome.twoLines(g, font, Component.translatable("screen.rematter.stock_available", available).getString(), x + 11, y + 71, 222, TEXT);
            ScreenChrome.twoLines(g, font, tr("quantity_not_item").getString(), x + 11, y + 93, 222, MUTED);
            text(g, id, x + 11, y + 114, 222, MUTED);
            if (!menu.snapshot().entriesComplete()) text(g, tr("partial_contents_hint").getString(), x + 11, y + 126, 222, WARNING);
            super.extractRenderState(g, mx, my, partial);
        }
    }
}
