package dev.latrox.rematter.client;

import dev.latrox.rematter.client.ui.DarkButton;
import dev.latrox.rematter.content.MachinePortMode;
import dev.latrox.rematter.energy.EnergyCubeBlock;
import dev.latrox.rematter.energy.EnergyCubeMenu;
import dev.latrox.rematter.energy.EnergyCubeSideMode;
import dev.latrox.rematter.network.EnergyCubePayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/** Storage readout, Charge (+) / Discharge (-) bays, and unified Settings tab with General, Sides & Access sub-tabs. */
public final class EnergyCubeScreen extends AbstractContainerScreen<EnergyCubeMenu> {
    private enum Tab { STORAGE, SETTINGS }
    private enum SettingsSection { GENERAL, SIDES, ACCESS }

    private SideDiagram diagram;
    private DarkButton storageTab, settingsTab, generalSubTab, sidesSubTab, accessSubTab;
    private DeviceGeneralPanel generalPanel;
    private DeviceAccessPanel accessPanel;
    private Tab tab = Tab.STORAGE;
    private SettingsSection settingsSection = SettingsSection.GENERAL;

    public EnergyCubeScreen(EnergyCubeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 216, 216);
    }

    private static Component tr(String key, Object... args) { return Component.translatable("screen.rematter.energy." + key, args); }

    @Override protected void init() {
        super.init();
        leftPos = Math.max(23, leftPos);
        storageTab = addRenderableWidget(DarkButton.icon(UiIcon.ENERGY, tr("storage"), tr("storage_hint"),
                leftPos - 22, topPos + 18, 24, 22, b -> { tab = Tab.STORAGE; refresh(); }));
        settingsTab = DeviceGeneralPanel.createSettingsTab(this::addRenderableWidget, leftPos, topPos, 1, accent(),
                () -> { tab = Tab.SETTINGS; refresh(); });

        generalSubTab = DeviceGeneralPanel.createSubTab(this::addRenderableWidget, UiIcon.GENERAL,
                Component.translatable("screen.rematter.settings.section.general"),
                Component.translatable("screen.rematter.settings.section_hint.general"),
                leftPos + 8, topPos + 20, accent(),
                () -> { settingsSection = SettingsSection.GENERAL; refresh(); });
        sidesSubTab = DeviceGeneralPanel.createSubTab(this::addRenderableWidget, UiIcon.SIDES,
                Component.translatable("screen.rematter.settings.section.sides"),
                tr("sides_hint"), leftPos + 32, topPos + 20, accent(),
                () -> { settingsSection = SettingsSection.SIDES; refresh(); });
        accessSubTab = DeviceGeneralPanel.createSubTab(this::addRenderableWidget, UiIcon.ACCESS,
                Component.translatable("screen.rematter.settings.section.access"),
                Component.translatable("screen.rematter.settings.section_hint.access"),
                leftPos + 56, topPos + 20, accent(),
                () -> { settingsSection = SettingsSection.ACCESS; refresh(); });

        generalPanel = new DeviceGeneralPanel(font, this::addRenderableWidget, leftPos + 8, topPos + 38, imageWidth - 16, accent(), title,
                menu::securityMode, menu::canManageSecurity, () -> !menu.locked(),
                forward -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                                forward ? EnergyCubeMenu.CYCLE_SECURITY : EnergyCubeMenu.CYCLE_SECURITY_BACK);
                    }
                },
                menu::customName,
                clean -> {
                    menu.setClientCustomName(clean);
                    ClientPacketDistributor.sendToServer(new EnergyCubePayload.Rename(menu.containerId, clean));
                },
                null, null);

        accessPanel = new DeviceAccessPanel(font, this::addRenderableWidget, leftPos + 8, topPos + 40, imageWidth - 16, accent(),
                () -> !menu.locked(),
                menu::redstoneMode,
                forward -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                                forward ? EnergyCubeMenu.CYCLE_REDSTONE : EnergyCubeMenu.CYCLE_REDSTONE_BACK);
                    }
                },
                null,
                menu::automationAllowed,
                () -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, EnergyCubeMenu.TOGGLE_AUTOMATION);
                    }
                });

        // The same diagram as every device; on a cube all six sides are connections, the front included.
        diagram = new SideDiagram(new SideDiagram.Host() {
            @Override public SideDiagram.Look look(MachinePortMode.Side side) {
                var mode = menu.sideMode(side);
                return new SideDiagram.Look(mode == EnergyCubeSideMode.DISABLED ? SideDiagram.Face.PLAIN : SideDiagram.Face.PLATE,
                        modeColor(mode), modeIcon(mode), false,
                        tr("side_tooltip", side.label(), mode.label(), mode.meaning()), inspectNeighbor(side));
            }
            @Override public void step(MachinePortMode.Side side, boolean forward) {
                if (minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                            side.ordinal() + (forward ? 0 : MachinePortMode.Side.values().length));
                }
            }
            @Override public boolean editable() { return !menu.locked(); }
        }, font, leftPos + 18, topPos + 38, this::addRenderableWidget);
        diagram.show(MachinePortMode.Side.FRONT);
        refresh();
    }

    private SideDiagram.@Nullable Neighbor inspectNeighbor(MachinePortMode.Side side) {
        if (minecraft == null || minecraft.level == null || !minecraft.level.hasChunkAt(menu.position())) return null;
        var state = minecraft.level.getBlockState(menu.position());
        if (!state.hasProperty(EnergyCubeBlock.FACING)) return null;
        return SideDiagram.inspectNeighbor(minecraft.level, menu.position(), state.getValue(EnergyCubeBlock.FACING), side);
    }

    public List<Rect2i> viewerExclusionAreas() {
        var areas = new ArrayList<Rect2i>(2);
        for (var button : List.of(storageTab, settingsTab)) {
            if (button != null && button.visible) {
                areas.add(new Rect2i(button.getX(), button.getY(), button.getWidth(), button.getHeight()));
            }
        }
        return areas;
    }

    @Override protected boolean hasClickedOutside(double mx, double my, int x, int y) {
        for (var r : viewerExclusionAreas()) {
            if (mx >= r.getX() && mx < r.getX() + r.getWidth() && my >= r.getY() && my < r.getY() + r.getHeight()) return false;
        }
        return super.hasClickedOutside(mx, my, x, y);
    }

    private int accent() {
        return menu.tier() == dev.latrox.rematter.energy.EnergyCubeTier.PRIMAL ? UiPalette.PRIMAL : UiPalette.EMBER;
    }

    private boolean showingSides() { return tab == Tab.SETTINGS && settingsSection == SettingsSection.SIDES; }
    private boolean showingGeneral() { return tab == Tab.SETTINGS && settingsSection == SettingsSection.GENERAL; }
    private boolean showingAccess() { return tab == Tab.SETTINGS && settingsSection == SettingsSection.ACCESS; }

    private void refresh() {
        menu.setSettings(tab != Tab.STORAGE);
        int a = accent();
        storageTab.setAccent(a);
        settingsTab.setAccent(a);
        storageTab.setSelected(tab == Tab.STORAGE);
        settingsTab.setSelected(tab == Tab.SETTINGS);

        boolean inSettings = tab == Tab.SETTINGS;
        generalSubTab.visible = generalSubTab.active = inSettings;
        generalSubTab.setAccent(a);
        generalSubTab.setSelected(showingGeneral());
        sidesSubTab.visible = sidesSubTab.active = inSettings;
        sidesSubTab.setAccent(a);
        sidesSubTab.setSelected(showingSides());
        accessSubTab.visible = accessSubTab.active = inSettings;
        accessSubTab.setAccent(a);
        accessSubTab.setSelected(showingAccess());

        if (generalPanel != null) generalPanel.refresh(showingGeneral(), a);
        if (accessPanel != null) accessPanel.refresh(showingAccess(), a);
        diagram.update(showingSides());
    }

    @Override protected void containerTick() { super.containerTick(); refresh(); }

    @Override public boolean keyPressed(KeyEvent event) {
        if (generalPanel != null && generalPanel.handlesKey(event)) return true;
        return super.keyPressed(event);
    }

    @Override public boolean charTyped(CharacterEvent event) {
        if (generalPanel != null && generalPanel.handlesChar(event)) return true;
        return super.charTyped(event);
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractBackground(g, mx, my, partial);
        int a = accent();
        ScreenChrome.panel(g, leftPos, topPos, imageWidth, imageHeight);
        g.fill(leftPos + 8, topPos + 17, leftPos + 208, topPos + 18, UiPalette.BORDER);
        g.fill(leftPos + 8, topPos + 17, leftPos + 56, topPos + 18, a);
        if (showingGeneral() && generalPanel != null) {
            generalPanel.extractBackground(g);
        } else if (tab == Tab.STORAGE) {
            ScreenChrome.inset(g, leftPos + 18, topPos + 28, 180, 12);
            int fill = (int) Math.clamp(176L * menu.energy() / Math.max(1L, menu.capacity()), 0L, 176L);
            g.fill(leftPos + 20, topPos + 30, leftPos + 20 + fill, topPos + 38, a);
        }
        int chargeCount = menu.tier().chargingSlots(), totalCubeSlots = menu.tier().totalSlots();
        for (int i = 0; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            if (!slot.isActive()) continue;
            ScreenChrome.slot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
            if (i < chargeCount && !slot.hasItem()) ScreenChrome.icon(g, UiIcon.PLUS, leftPos + slot.x, topPos + slot.y, UiPalette.GHOST);
            else if (i >= chargeCount && i < totalCubeSlots && !slot.hasItem()) ScreenChrome.icon(g, UiIcon.MINUS, leftPos + slot.x, topPos + slot.y, UiPalette.GHOST);
        }
    }

    @Override protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        DeviceGeneralPanel.drawTitle(g, font, menu.customName(), title, imageWidth - 16);
        if (tab == Tab.SETTINGS) {
            String sectionTitle = Component.translatable(switch (settingsSection) {
                case GENERAL -> "screen.rematter.settings.section.general";
                case SIDES -> "screen.rematter.settings.section.sides";
                case ACCESS -> "screen.rematter.settings.section.access";
            }).getString();
            ScreenChrome.text(g, font, sectionTitle, 82, 25, 126, UiPalette.TEXT);
            if (showingGeneral()) {
                if (generalPanel != null) generalPanel.extractLabels(g, leftPos, topPos);
            } else if (showingAccess()) {
                if (accessPanel != null) accessPanel.extractLabels(g, leftPos, topPos);
            } else {
                var side = diagram.shown();
                var mode = menu.sideMode(side);
                int y = 92;
                ScreenChrome.icon(g, modeIcon(mode), 18, y - 3, modeColor(mode));
                ScreenChrome.text(g, font, tr("side_state", side.label(), mode.label()).getString(), 36, y + 1, 162,
                        mode == EnergyCubeSideMode.DISABLED ? UiPalette.MUTED : UiPalette.TEXT);
                ScreenChrome.twoLines(g, font, mode.meaning().getString(), 18, y + 11, 180, UiPalette.MUTED);
            }
        } else {
            ScreenChrome.text(g, font, tr("reserve", Quantities.compact(menu.energy()), Quantities.compact(menu.capacity())).getString(), 18, 44, 102, UiPalette.TEXT);
            ScreenChrome.icon(g, UiIcon.EXCHANGE, 122, 40, accent());
            ScreenChrome.text(g, font, tr("rate", Quantities.compact(menu.transferRate())).getString(), 138, 44, 64, UiPalette.MUTED);
            ScreenChrome.text(g, font, tr("charge_label").getString(), 18, 69, 84, UiPalette.PORT_ENERGY_OUTPUT);
            ScreenChrome.text(g, font, tr("discharge_label").getString(), 114, 69, 84, UiPalette.PORT_ENERGY_INPUT);
            boolean paused = !menu.locked() && menu.redstonePaused();
            UiIcon statusIcon = menu.locked() ? UiIcon.STATUS_ERROR : paused ? UiIcon.STATUS_BLOCKED : UiIcon.INFO;
            int iconTint = (menu.locked() || paused) ? UiPalette.WHITE : UiPalette.MUTED;
            String statusText = menu.locked() ? tr("locked").getString()
                    : paused ? Component.translatable("status.rematter.redstone_paused").getString()
                    : tr("charging_hint").getString();
            ScreenChrome.icon(g, statusIcon, 16, 101, iconTint);
            ScreenChrome.text(g, font, statusText, 34, 105, 164, (menu.locked() || paused) ? UiPalette.WARNING : UiPalette.MUTED);
        }
        ScreenChrome.text(g, font, Component.translatable("screen.rematter.inventory").getString(), 18, 123, 180, UiPalette.MUTED);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractRenderState(g, mx, my, partial);
        if (tab != Tab.STORAGE) return;
        int x = mx - leftPos, y = my - topPos;
        if (x >= 18 && x < 198 && y >= 28 && y < 54) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    tr("reserve", Quantities.number(menu.energy()), Quantities.number(menu.capacity())),
                    tr("rate_hint", Quantities.number(menu.transferRate()))), mx, my);
        }
        int chargeCount = menu.tier().chargingSlots(), totalCubeSlots = menu.tier().totalSlots();
        for (int i = 0; i < totalCubeSlots; i++) {
            var slot = menu.slots.get(i);
            if (!slot.hasItem() && x >= slot.x && x < slot.x + 16 && y >= slot.y && y < slot.y + 16) {
                g.setComponentTooltipForNextFrame(font, i < chargeCount
                        ? List.of(tr("charge_slot"), tr("charge_slot_hint"))
                        : List.of(tr("discharge_slot"), tr("discharge_slot_hint")), mx, my);
            }
        }
        if (x >= 16 && x < 200 && y >= 100 && y < 116) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    tr("charging_hint"), tr("rate_hint", Quantities.number(menu.transferRate()))), mx, my);
        }
    }

    private static UiIcon modeIcon(EnergyCubeSideMode mode) {
        return switch (mode) {
            case INPUT -> UiIcon.SLOT_INPUT;
            case OUTPUT -> UiIcon.SLOT_OUTPUT;
            case INPUT_OUTPUT -> UiIcon.EXCHANGE;
            case DISABLED -> UiIcon.DISABLED;
        };
    }

    private static int modeColor(EnergyCubeSideMode mode) {
        return switch (mode) {
            case INPUT, INPUT_OUTPUT -> UiPalette.PORT_ENERGY_INPUT;
            case OUTPUT -> UiPalette.PORT_ENERGY_OUTPUT;
            case DISABLED -> UiPalette.MUTED;
        };
    }
}
