package dev.latrox.rematter.client;

import static dev.latrox.rematter.client.UiPalette.*;

import dev.latrox.rematter.client.ui.DarkButton;
import dev.latrox.rematter.content.CustomNames;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachinePortMode;
import dev.latrox.rematter.network.ChunkMapPayload;
import dev.latrox.rematter.network.ClientChunkMaps;
import dev.latrox.rematter.network.DimensionalLinkPayload;
import dev.latrox.rematter.primal.DimensionalEntangledLinkBlock;
import dev.latrox.rematter.primal.DimensionalEntangledLinkBlockEntity;
import dev.latrox.rematter.primal.DimensionalEntangledLinkMenu;
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

/** Zero-overlap screen for Dimensional Entangled Link (peer dimension/coords, Chunk Anchor tab with 5x5 map, unified Settings with General, Sides & Access). */
public final class DimensionalEntangledLinkScreen extends AbstractContainerScreen<DimensionalEntangledLinkMenu> {
    private enum Tab { MAIN, ANCHOR, SETTINGS }
    private enum SettingsSection { GENERAL, SIDES, ACCESS }

    private static final int W = 216, H = 216;
    private static final int ACCENT = machineAccent(MachineKind.NETWORK_INTERFACE);
    private DarkButton mainTab, anchorTab, settingsTab, generalSubTab, sidesSubTab, accessSubTab, unpairBtn;
    private SideDiagram diagram;
    private DeviceGeneralPanel generalPanel;
    private DeviceAccessPanel accessPanel;
    private Tab tab = Tab.MAIN;
    private SettingsSection settingsSection = SettingsSection.GENERAL;

    public DimensionalEntangledLinkScreen(DimensionalEntangledLinkMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.rematter.dimensional_link." + key, args);
    }

    public List<Rect2i> viewerExclusionAreas() {
        var areas = new ArrayList<Rect2i>(3);
        for (var btn : List.of(mainTab, anchorTab, settingsTab)) {
            if (btn != null && btn.visible) {
                areas.add(new Rect2i(btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight()));
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

    @Override protected void init() {
        super.init();
        leftPos = Math.max(23, leftPos);
        mainTab = addRenderableWidget(DarkButton.icon(UiIcon.NETWORK_ONLINE,
                tr("tab_main"), tr("tab_main_hint"),
                leftPos - 22, topPos + 18, 24, 22, b -> setTab(Tab.MAIN)));
        anchorTab = addRenderableWidget(DarkButton.icon(UiIcon.LOCATE,
                Component.translatable("screen.rematter.ui.tab_anchor"),
                Component.translatable("screen.rematter.ui.tab_anchor_hint"),
                leftPos - 22, topPos + 42, 24, 22, b -> setTab(Tab.ANCHOR)));
        settingsTab = DeviceGeneralPanel.createSettingsTab(this::addRenderableWidget, leftPos, topPos, 2, ACCENT,
                () -> setTab(Tab.SETTINGS));

        generalSubTab = DeviceGeneralPanel.createSubTab(this::addRenderableWidget, UiIcon.GENERAL,
                Component.translatable("screen.rematter.settings.section.general"),
                Component.translatable("screen.rematter.settings.section_hint.general"),
                leftPos + 8, topPos + 20, ACCENT,
                () -> { settingsSection = SettingsSection.GENERAL; refreshWidgets(); });
        sidesSubTab = DeviceGeneralPanel.createSubTab(this::addRenderableWidget, UiIcon.SIDES,
                Component.translatable("screen.rematter.settings.section.sides"),
                Component.translatable("screen.rematter.settings.section_hint.sides"),
                leftPos + 32, topPos + 20, ACCENT,
                () -> { settingsSection = SettingsSection.SIDES; refreshWidgets(); });
        accessSubTab = DeviceGeneralPanel.createSubTab(this::addRenderableWidget, UiIcon.ACCESS,
                Component.translatable("screen.rematter.settings.section.access"),
                Component.translatable("screen.rematter.settings.section_hint.access"),
                leftPos + 56, topPos + 20, ACCENT,
                () -> { settingsSection = SettingsSection.ACCESS; refreshWidgets(); });

        generalPanel = new DeviceGeneralPanel(font, this::addRenderableWidget, leftPos + 8, topPos + 38, W - 16, ACCENT, title,
                menu::securityMode, menu::canManageSecurity, () -> true,
                forward -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                                forward ? DimensionalEntangledLinkMenu.CYCLE_SECURITY : DimensionalEntangledLinkMenu.CYCLE_SECURITY_BACK);
                    }
                },
                menu::customName,
                clean -> sendUpdate(clean, false),
                null, null);

        accessPanel = new DeviceAccessPanel(font, this::addRenderableWidget, leftPos + 8, topPos + 40, W - 16, ACCENT,
                () -> true,
                menu::redstoneMode,
                forward -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                                forward ? DimensionalEntangledLinkMenu.CYCLE_REDSTONE : DimensionalEntangledLinkMenu.CYCLE_REDSTONE_BACK);
                    }
                },
                null, null, null);

        diagram = new SideDiagram(new SideDiagram.Host() {
            @Override public SideDiagram.Look look(MachinePortMode.Side side) {
                return DeviceGeneralPanel.portLook(side, DimensionalEntangledLinkBlockEntity.isConfigurableSide(side),
                        menu.portMode(side), inspectNeighbor(side));
            }
            @Override public void step(MachinePortMode.Side side, boolean forward) {
                if (minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                            (forward ? DimensionalEntangledLinkMenu.CYCLE_PORT : DimensionalEntangledLinkMenu.CYCLE_PORT_BACK) + side.ordinal());
                }
            }
            @Override public boolean editable() { return true; }
        }, font, leftPos + 18, topPos + 38, this::addRenderableWidget);
        diagram.show(MachinePortMode.Side.BACK);

        unpairBtn = addRenderableWidget(DarkButton.labelIcon(UiIcon.CANCEL, tr("unpair"), tr("unpair_hint"),
                leftPos + 150, topPos + 45, 54, 16, b -> sendUpdate(menu.customName(), true)));
        unpairBtn.setAccent(ACCENT);

        refreshWidgets();
    }

    private SideDiagram.@Nullable Neighbor inspectNeighbor(MachinePortMode.Side side) {
        if (minecraft == null || minecraft.level == null || !minecraft.level.hasChunkAt(menu.blockPos())) return null;
        var state = minecraft.level.getBlockState(menu.blockPos());
        if (!state.hasProperty(DimensionalEntangledLinkBlock.FACING)) return null;
        return SideDiagram.inspectNeighbor(minecraft.level, menu.blockPos(), state.getValue(DimensionalEntangledLinkBlock.FACING), side);
    }

    private void setTab(Tab next) {
        tab = next;
        menu.setAnchorTab(next == Tab.ANCHOR);
        if (next == Tab.ANCHOR) {
            ClientPacketDistributor.sendToServer(new ChunkMapPayload.Request(menu.containerId));
        }
        refreshWidgets();
    }

    private void sendUpdate(String nextName, boolean clearLink) {
        menu.setClientCustomName(nextName);
        if (clearLink) menu.setClientUnpaired();
        ClientPacketDistributor.sendToServer(new DimensionalLinkPayload.Update(menu.containerId, nextName, clearLink));
        refreshWidgets();
    }

    private boolean showingSides() { return tab == Tab.SETTINGS && settingsSection == SettingsSection.SIDES; }
    private boolean showingGeneral() { return tab == Tab.SETTINGS && settingsSection == SettingsSection.GENERAL; }
    private boolean showingAccess() { return tab == Tab.SETTINGS && settingsSection == SettingsSection.ACCESS; }

    private void refreshWidgets() {
        menu.setAnchorTab(tab == Tab.ANCHOR);
        if (mainTab != null) {
            mainTab.setAccent(ACCENT);
            mainTab.setSelected(tab == Tab.MAIN);
        }
        if (anchorTab != null) {
            anchorTab.setAccent(ACCENT);
            anchorTab.setSelected(tab == Tab.ANCHOR);
        }
        if (settingsTab != null) {
            settingsTab.setAccent(ACCENT);
            settingsTab.setSelected(tab == Tab.SETTINGS);
        }
        boolean inSettings = tab == Tab.SETTINGS;
        if (generalSubTab != null) {
            generalSubTab.visible = generalSubTab.active = inSettings;
            generalSubTab.setAccent(ACCENT);
            generalSubTab.setSelected(showingGeneral());
        }
        if (sidesSubTab != null) {
            sidesSubTab.visible = sidesSubTab.active = inSettings;
            sidesSubTab.setAccent(ACCENT);
            sidesSubTab.setSelected(showingSides());
        }
        if (accessSubTab != null) {
            accessSubTab.visible = accessSubTab.active = inSettings;
            accessSubTab.setAccent(ACCENT);
            accessSubTab.setSelected(showingAccess());
        }
        if (unpairBtn != null) {
            unpairBtn.visible = unpairBtn.active = tab == Tab.MAIN && menu.hasPeer();
        }
        if (generalPanel != null) generalPanel.refresh(showingGeneral(), ACCENT);
        if (accessPanel != null) accessPanel.refresh(showingAccess(), ACCENT);
        if (diagram != null) diagram.update(showingSides());
    }

    @Override protected void containerTick() {
        super.containerTick();
        refreshWidgets();
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (generalPanel != null && generalPanel.handlesKey(event)) return true;
        if (event.isEscape()) { onClose(); return true; }
        return super.keyPressed(event);
    }

    @Override public boolean charTyped(CharacterEvent event) {
        if (generalPanel != null && generalPanel.handlesChar(event)) return true;
        return super.charTyped(event);
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractBackground(g, mx, my, partial);
        ScreenChrome.panel(g, leftPos, topPos, W, H);
        g.fill(leftPos + 8, topPos + 17, leftPos + W - 8, topPos + 18, BORDER);
        g.fill(leftPos + 8, topPos + 17, leftPos + 56, topPos + 18, ACCENT);

        if (tab == Tab.SETTINGS) {
            if (showingGeneral() && generalPanel != null) generalPanel.extractBackground(g);
        } else if (tab == Tab.ANCHOR) {
            ChunkMapWidget.draw(g, leftPos + 10, topPos + 22, 4,
                    ClientChunkMaps.forContainer(menu.containerId), menu.anchorActive(), ACCENT);
            ScreenChrome.inset(g, leftPos + 98, topPos + 22, 110, 24);
            ScreenChrome.inset(g, leftPos + 98, topPos + 49, 110, 57);
            ScreenChrome.inset(g, leftPos + 10, topPos + 108, 198, 12);
        } else {
            ScreenChrome.inset(g, leftPos + 8, topPos + 20, W - 16, 16);
            ScreenChrome.inset(g, leftPos + 8, topPos + 39, W - 16, 36);
            ScreenChrome.inset(g, leftPos + 8, topPos + 79, W - 16, 36);
        }

        for (int i = 0; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            if (!slot.isActive()) continue;
            ScreenChrome.slot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
            if (i == 0) {
                g.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y, UPGRADE);
                if (!slot.hasItem()) ScreenChrome.icon(g, UiIcon.LOCATE, leftPos + slot.x, topPos + slot.y, GHOST);
            }
        }
    }

    @Override protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        DeviceGeneralPanel.drawTitle(g, font, menu.customName(), title, W - 16);

        if (tab == Tab.SETTINGS) {
            String sectionTitle = Component.translatable(switch (settingsSection) {
                case GENERAL -> "screen.rematter.settings.section.general";
                case SIDES -> "screen.rematter.settings.section.sides";
                case ACCESS -> "screen.rematter.settings.section.access";
            }).getString();
            ScreenChrome.text(g, font, sectionTitle, 82, 25, W - 90, TEXT);
            if (showingGeneral()) {
                if (generalPanel != null) generalPanel.extractLabels(g, leftPos, topPos);
            } else if (showingAccess()) {
                if (accessPanel != null) accessPanel.extractLabels(g, leftPos, topPos);
            } else {
                var side = diagram.shown();
                DeviceGeneralPanel.drawPortReadout(g, font, side,
                        DimensionalEntangledLinkBlockEntity.isConfigurableSide(side), menu.portMode(side), 18, 92, 180);
            }
        } else if (tab == Tab.ANCHOR) {
            drawAnchorLabels(g);
        } else {
            if (menu.redstonePaused()) {
                ScreenChrome.icon(g, UiIcon.STATUS_BLOCKED, 9, 20, WHITE);
                ScreenChrome.text(g, font, Component.translatable("status.rematter.redstone_paused").getString(), 27, 24, W - 40, WARNING);
            } else {
                int st = menu.linkState();
                UiIcon icon = st == 1 ? UiIcon.NETWORK_ONLINE : st == 2 ? UiIcon.STATUS_WAITING : UiIcon.NETWORK_OFFLINE;
                int col = st == 1 ? ACCENT : st == 2 ? WARNING : MUTED;
                ScreenChrome.icon(g, icon, 9, 20, col);
                String statusKey = st == 1 ? "state_linked" : st == 2 ? "state_remote_unloaded" : "state_unpaired";
                ScreenChrome.text(g, font, tr(statusKey).getString(), 27, 24, W - 40, col);
            }

            if (menu.hasPeer()) {
                String dimLabel = CustomNames.shortDimensionLabel(menu.peerDimension());
                String dimPrefix = dimLabel.isEmpty() ? "Overworld" : dimLabel;
                var p = menu.peerPos();
                ScreenChrome.text(g, font, dimPrefix + " · " + p.getX() + ", " + p.getY() + ", " + p.getZ(), 12, 45, 134, TEXT);
            } else {
                ScreenChrome.text(g, font, tr("pair_hint").getString(), 12, 45, 192, MUTED);
            }
            ScreenChrome.text(g, font, tr("throughput").getString(), 12, 59, 134, ACCENT);

            boolean hasAnchor = menu.slots.get(0).hasItem();
            ScreenChrome.icon(g, hasAnchor && menu.anchorActive() ? UiIcon.STATUS_WORKING : hasAnchor ? UiIcon.STATUS_BLOCKED : UiIcon.LOCATE,
                    12, 83, hasAnchor ? -1 : MUTED);
            String anchorText = !hasAnchor ? tr("anchor_empty").getString()
                    : menu.anchorActive() ? tr("anchor_active", menu.ownerTotalChunks(), menu.maxChunks()).getString()
                    : tr("anchor_inactive", menu.ownerTotalChunks(), menu.maxChunks()).getString();
            ScreenChrome.text(g, font, anchorText, 30, 87, 174, hasAnchor && menu.anchorActive() ? ACCENT : TEXT);
            ScreenChrome.twoLines(g, font, tr("anchor_tab_hint").getString(), 12, 98, 192, MUTED);
        }

        ScreenChrome.text(g, font, Component.translatable("screen.rematter.inventory").getString(), 18, 123, 180, MUTED);
    }

    private void drawAnchorLabels(GuiGraphicsExtractor g) {
        boolean installed = menu.slots.get(0).hasItem();
        boolean active = menu.anchorActive();
        int total = menu.ownerTotalChunks();
        int max = Math.max(1, menu.maxChunks());
        ScreenChrome.text(g, font, Component.translatable("item.rematter.anchor_upgrade").getString(), 102, 30, 80, TEXT);

        ScreenChrome.icon(g, installed && active ? UiIcon.STATUS_WORKING : installed ? UiIcon.STATUS_BLOCKED : UiIcon.LOCATE,
                100, 51, installed ? -1 : MUTED);
        String anchorLine = !installed
                ? Component.translatable("screen.rematter.ui.upgrade_anchor_empty").getString()
                : active
                ? Component.translatable("screen.rematter.ui.upgrade_anchor_active", 1, total, max).getString()
                : Component.translatable("screen.rematter.ui.upgrade_anchor_inactive", total, max).getString();
        ScreenChrome.text(g, font, anchorLine, 117, 55, 88, installed && active ? ACCENT : installed ? WARNING : MUTED);

        ScreenChrome.text(g, font, Component.translatable("screen.rematter.spatial_anchor.quota", total, max).getString(),
                102, 68, 102, total >= max && !active ? WARNING : TEXT);
        int line = 0;
        for (var part : font.split(tr("anchor_slot_hint"), 102)) {
            if (line >= 3) break;
            g.text(font, part, 102, 78 + line++ * 9, MUTED, false);
        }

        g.fill(14, 111, 20, 117, active ? ACCENT_BRIGHT : ACCENT);
        ScreenChrome.text(g, font, Component.translatable("screen.rematter.chunk_map.legend_this").getString(), 23, 110, 58, TEXT);
        g.fill(84, 111, 90, 117, WARNING);
        ScreenChrome.text(g, font, Component.translatable("screen.rematter.chunk_map.legend_other").getString(), 93, 110, 54, MUTED);
        var map = ClientChunkMaps.forContainer(menu.containerId);
        if (map != null) {
            ScreenChrome.text(g, font, Component.translatable("screen.rematter.chunk_map.chunk_coords", map.centerChunkX(), map.centerChunkZ()).getString(),
                    150, 110, 56, MUTED);
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractRenderState(g, mx, my, partial);
        if (tab != Tab.ANCHOR) return;
        int x = mx - leftPos, y = my - topPos;
        var mapTip = ChunkMapWidget.tooltip(x, y, 10, 22, 4,
                ClientChunkMaps.forContainer(menu.containerId), menu.anchorActive());
        if (mapTip != null) {
            g.setComponentTooltipForNextFrame(font, mapTip, mx, my);
            return;
        }
        var slot = menu.slots.get(0);
        if (slot.isActive() && !slot.hasItem() && x >= slot.x && x < slot.x + 16 && y >= slot.y && y < slot.y + 16) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("item.rematter.anchor_upgrade"),
                    tr("anchor_slot_hint")), mx, my);
        }
    }
}
