package dev.latrox.rematter.client;

import static dev.latrox.rematter.client.UiPalette.*;

import dev.latrox.rematter.client.ui.DarkButton;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachinePortMode;
import dev.latrox.rematter.content.RedstoneMode;
import dev.latrox.rematter.content.SpatialAnchorBlock;
import dev.latrox.rematter.content.SpatialAnchorBlockEntity;
import dev.latrox.rematter.content.SpatialAnchorMenu;
import dev.latrox.rematter.network.ChunkMapPayload;
import dev.latrox.rematter.network.ClientChunkMaps;
import dev.latrox.rematter.network.SpatialAnchorPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/** Compact zero-overlap screen for Spatial Anchor (1x1/3x3/5x5 presets, clickable 5x5 chunk map, FE buffer, unified Settings with General, Sides & Access). */
public final class SpatialAnchorScreen extends AbstractContainerScreen<SpatialAnchorMenu> {
    private enum Tab { MAIN, SETTINGS }
    private enum SettingsSection { GENERAL, SIDES, ACCESS }

    private static final int W = 296, H = 184;
    private static final int LEFT_W = 186;
    private static final int MAP_X = 202, MAP_Y = 38, MAP_CELL = 4;
    private static final int ACCENT = machineAccent(MachineKind.NETWORK_INTERFACE);
    private DarkButton mainTab, settingsTab, generalSubTab, sidesSubTab, accessSubTab, preset1Btn, preset3Btn, preset5Btn;
    private SideDiagram diagram;
    private DeviceGeneralPanel generalPanel;
    private DeviceAccessPanel accessPanel;
    private Tab tab = Tab.MAIN;
    private SettingsSection settingsSection = SettingsSection.GENERAL;
    private int draftMask;
    private RedstoneMode draftRedstone = RedstoneMode.IGNORED;

    public SpatialAnchorScreen(SpatialAnchorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.rematter.spatial_anchor." + key, args);
    }

    public List<Rect2i> viewerExclusionAreas() {
        var areas = new ArrayList<Rect2i>(2);
        for (var btn : List.of(mainTab, settingsTab)) {
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
        draftMask = menu.chunkMask();
        draftRedstone = menu.redstoneMode();

        mainTab = addRenderableWidget(DarkButton.icon(UiIcon.LOCATE,
                title, tr("map_click_hint"),
                leftPos - 22, topPos + 18, 24, 22, b -> { tab = Tab.MAIN; refreshWidgets(); }));
        settingsTab = DeviceGeneralPanel.createSettingsTab(this::addRenderableWidget, leftPos, topPos, 1, ACCENT,
                () -> { tab = Tab.SETTINGS; refreshWidgets(); });

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

        generalPanel = new DeviceGeneralPanel(font, this::addRenderableWidget, leftPos + 8, topPos + 40, W - 16, ACCENT, title,
                menu::securityMode, menu::canManageSecurity, () -> true,
                forward -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                                forward ? SpatialAnchorMenu.CYCLE_SECURITY : SpatialAnchorMenu.CYCLE_SECURITY_BACK);
                    }
                },
                menu::customName,
                clean -> sendConfig(draftMask, draftRedstone, clean),
                null, null);

        accessPanel = new DeviceAccessPanel(font, this::addRenderableWidget, leftPos + 8, topPos + 40, W - 16, ACCENT,
                () -> true,
                () -> draftRedstone,
                forward -> sendConfig(draftMask, draftRedstone.stepSimple(forward), menu.customName()),
                tr("redstone_hint"),
                null, null);

        diagram = new SideDiagram(new SideDiagram.Host() {
            @Override public SideDiagram.Look look(MachinePortMode.Side side) {
                return DeviceGeneralPanel.portLook(side, SpatialAnchorBlockEntity.isConfigurableSide(side),
                        menu.portMode(side), inspectNeighbor(side));
            }
            @Override public void step(MachinePortMode.Side side, boolean forward) {
                if (minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                            (forward ? SpatialAnchorMenu.CYCLE_PORT : SpatialAnchorMenu.CYCLE_PORT_BACK) + side.ordinal());
                }
            }
            @Override public boolean editable() { return true; }
        }, font, leftPos + (W - SideDiagram.WIDTH) / 2, topPos + 44, this::addRenderableWidget);
        diagram.show(MachinePortMode.Side.BACK);

        preset1Btn = addRenderableWidget(DarkButton.create(tr("preset_1x1"),
                tr("preset_hint", tr("preset_1x1"), 1, SpatialAnchorBlockEntity.DRAIN_1X1),
                leftPos + 8, topPos + 55, 60, 18, b -> sendConfig(SpatialAnchorBlockEntity.MASK_1X1, draftRedstone, menu.customName())));
        preset1Btn.setAccent(ACCENT);

        preset3Btn = addRenderableWidget(DarkButton.create(tr("preset_3x3"),
                tr("preset_hint", tr("preset_3x3"), 9, SpatialAnchorBlockEntity.DRAIN_3X3),
                leftPos + 71, topPos + 55, 60, 18, b -> sendConfig(SpatialAnchorBlockEntity.MASK_3X3, draftRedstone, menu.customName())));
        preset3Btn.setAccent(ACCENT);

        preset5Btn = addRenderableWidget(DarkButton.create(tr("preset_5x5"),
                tr("preset_hint", tr("preset_5x5"), 25, SpatialAnchorBlockEntity.DRAIN_5X5),
                leftPos + 134, topPos + 55, 60, 18, b -> sendConfig(SpatialAnchorBlockEntity.MASK_5X5, draftRedstone, menu.customName())));
        preset5Btn.setAccent(ACCENT);

        ClientPacketDistributor.sendToServer(new ChunkMapPayload.Request(menu.containerId));
        refreshWidgets();
    }

    private SideDiagram.@Nullable Neighbor inspectNeighbor(MachinePortMode.Side side) {
        if (minecraft == null || minecraft.level == null || !minecraft.level.hasChunkAt(menu.blockPos())) return null;
        var state = minecraft.level.getBlockState(menu.blockPos());
        if (!state.hasProperty(SpatialAnchorBlock.FACING)) return null;
        return SideDiagram.inspectNeighbor(minecraft.level, menu.blockPos(), state.getValue(SpatialAnchorBlock.FACING), side);
    }

    private void sendConfig(int nextMask, RedstoneMode nextRs, String nextName) {
        draftMask = SpatialAnchorBlockEntity.sanitizeMask(nextMask);
        draftRedstone = nextRs;
        menu.setClientCustomName(nextName);
        ClientPacketDistributor.sendToServer(new SpatialAnchorPayload.Configure(
                menu.containerId, draftMask, draftRedstone, nextName));
        refreshWidgets();
    }

    private boolean showingSides() { return tab == Tab.SETTINGS && settingsSection == SettingsSection.SIDES; }
    private boolean showingGeneral() { return tab == Tab.SETTINGS && settingsSection == SettingsSection.GENERAL; }
    private boolean showingAccess() { return tab == Tab.SETTINGS && settingsSection == SettingsSection.ACCESS; }

    private void refreshWidgets() {
        if (preset1Btn == null) return;
        draftMask = menu.chunkMask();
        draftRedstone = menu.redstoneMode();
        mainTab.setAccent(ACCENT);
        mainTab.setSelected(tab == Tab.MAIN);
        settingsTab.setAccent(ACCENT);
        settingsTab.setSelected(tab == Tab.SETTINGS);

        boolean inSettings = tab == Tab.SETTINGS;
        generalSubTab.visible = generalSubTab.active = inSettings;
        generalSubTab.setAccent(ACCENT);
        generalSubTab.setSelected(showingGeneral());
        sidesSubTab.visible = sidesSubTab.active = inSettings;
        sidesSubTab.setAccent(ACCENT);
        sidesSubTab.setSelected(showingSides());
        accessSubTab.visible = accessSubTab.active = inSettings;
        accessSubTab.setAccent(ACCENT);
        accessSubTab.setSelected(showingAccess());

        boolean main = tab == Tab.MAIN;
        preset1Btn.visible = preset1Btn.active = main;
        preset3Btn.visible = preset3Btn.active = main;
        preset5Btn.visible = preset5Btn.active = main;

        preset1Btn.setSelected(draftMask == SpatialAnchorBlockEntity.MASK_1X1);
        preset3Btn.setSelected(draftMask == SpatialAnchorBlockEntity.MASK_3X3);
        preset5Btn.setSelected(draftMask == SpatialAnchorBlockEntity.MASK_5X5);

        if (generalPanel != null) generalPanel.refresh(showingGeneral(), ACCENT);
        if (accessPanel != null) accessPanel.refresh(showingAccess(), ACCENT);
        if (diagram != null) diagram.update(showingSides());
    }

    @Override protected void containerTick() {
        super.containerTick();
        refreshWidgets();
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (tab == Tab.MAIN && event.button() == 0) {
            int localX = (int) event.x() - leftPos;
            int localY = (int) event.y() - topPos;
            var cell = ChunkMapWidget.cellAt(localX, localY, MAP_X, MAP_Y, MAP_CELL);
            if (cell != null && !cell.center()) {
                int nextMask = SpatialAnchorBlockEntity.sanitizeMask(draftMask ^ (1 << cell.bitIndex()));
                sendConfig(nextMask, draftRedstone, menu.customName());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
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
            return;
        }

        ScreenChrome.inset(g, leftPos + 8, topPos + 20, LEFT_W, 16);

        ScreenChrome.inset(g, leftPos + 8, topPos + 39, LEFT_W, 12);
        int fill = (int) Math.clamp((long) (LEFT_W - 4) * menu.energy() / SpatialAnchorBlockEntity.CAPACITY, 0L, LEFT_W - 4);
        g.fill(leftPos + 10, topPos + 41, leftPos + 10 + fill, topPos + 49, menu.energy() < menu.drainPerTick() ? WARNING : ACCENT);

        ScreenChrome.inset(g, leftPos + 8, topPos + 78, LEFT_W, 98);

        var map = ClientChunkMaps.forContainer(menu.containerId);
        var hovered = ChunkMapWidget.cellAt(mx - leftPos, my - topPos, MAP_X, MAP_Y, MAP_CELL);
        int hoverBit = hovered != null && !hovered.center() ? hovered.bitIndex() : -1;
        ChunkMapWidget.draw(g, leftPos + MAP_X, topPos + MAP_Y, MAP_CELL, map, menu.active(), ACCENT, hoverBit);
        ScreenChrome.inset(g, leftPos + MAP_X, topPos + 126, ChunkMapWidget.pixelSize(MAP_CELL), 50);
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
                        SpatialAnchorBlockEntity.isConfigurableSide(side), menu.portMode(side), 18, 102, W - 36);
            }
            return;
        }

        boolean active = menu.active();
        ScreenChrome.icon(g, active ? UiIcon.STATUS_WORKING : UiIcon.STATUS_BLOCKED, 9, 20, -1);
        String stateText = tr("reason." + menu.reason()).getString() + " · " + menu.forcedChunks() + "/" + menu.desiredChunks() + " ch";
        ScreenChrome.text(g, font, stateText, 27, 24, LEFT_W - 24, active ? ACCENT : WARNING);

        int radius = menu.radius();
        String areaText = switch (radius) {
            case 0 -> tr("area_1x1", menu.drainPerTick()).getString();
            case 1 -> tr("area_3x3", menu.drainPerTick()).getString();
            case 2 -> tr("area_5x5", menu.drainPerTick()).getString();
            default -> tr("area_custom", menu.desiredChunks(), menu.drainPerTick()).getString();
        };
        ScreenChrome.text(g, font, areaText, 12, 86, LEFT_W - 8, ACCENT);

        int total = menu.ownerTotalChunks();
        int max = menu.maxChunks();
        ScreenChrome.text(g, font, tr("quota", total, max).getString(), 12, 102, LEFT_W - 8,
                total >= max && !active ? WARNING : TEXT);
        ScreenChrome.text(g, font, tr("drain", Quantities.number(menu.drainPerTick()),
                Quantities.compact(menu.energy()), Quantities.compact(SpatialAnchorBlockEntity.CAPACITY)).getString(),
                12, 117, LEFT_W - 8, MUTED);
        ScreenChrome.twoLines(g, font, tr("map_click_hint").getString(), 12, 134, LEFT_W - 8, MUTED);

        int mapW = ChunkMapWidget.pixelSize(MAP_CELL);
        ScreenChrome.text(g, font, tr("map_title").getString(), MAP_X, 24, mapW, TEXT);

        g.fill(MAP_X + 4, 132, MAP_X + 10, 138, active ? ACCENT_BRIGHT : ACCENT);
        ScreenChrome.text(g, font, Component.translatable("screen.rematter.chunk_map.legend_this").getString(), MAP_X + 14, 131, mapW - 18, TEXT);
        g.fill(MAP_X + 4, 144, MAP_X + 10, 150, WARNING);
        ScreenChrome.text(g, font, Component.translatable("screen.rematter.chunk_map.legend_other").getString(), MAP_X + 14, 143, mapW - 18, MUTED);
        var map = ClientChunkMaps.forContainer(menu.containerId);
        if (map != null) {
            ScreenChrome.text(g, font, Component.translatable("screen.rematter.chunk_map.chunk_coords", map.centerChunkX(), map.centerChunkZ()).getString(),
                    MAP_X + 4, 157, mapW - 8, MUTED);
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractRenderState(g, mx, my, partial);
        if (tab != Tab.MAIN) return;
        int x = mx - leftPos, y = my - topPos;
        if (x >= 8 && x < 8 + LEFT_W && y >= 39 && y < 51) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("screen.rematter.energy_buffer",
                            Quantities.number(menu.energy()), Quantities.number(SpatialAnchorBlockEntity.CAPACITY)),
                    tr("drain_detail", Quantities.number(menu.drainPerTick()))), mx, my);
            return;
        }
        var mapTip = ChunkMapWidget.tooltip(x, y, MAP_X, MAP_Y, MAP_CELL,
                ClientChunkMaps.forContainer(menu.containerId), menu.active(), true);
        if (mapTip != null) {
            g.setComponentTooltipForNextFrame(font, mapTip, mx, my);
        }
    }
}
