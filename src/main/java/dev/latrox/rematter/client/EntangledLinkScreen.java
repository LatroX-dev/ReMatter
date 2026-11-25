package dev.latrox.rematter.client;

import static dev.latrox.rematter.client.UiPalette.*;

import dev.latrox.rematter.client.ui.DarkButton;
import dev.latrox.rematter.content.MachinePortMode;
import dev.latrox.rematter.network.EntangledLinkPayload;
import dev.latrox.rematter.primal.EntangledLinkBlock;
import dev.latrox.rematter.primal.EntangledLinkBlockEntity;
import dev.latrox.rematter.primal.EntangledLinkMenu;
import dev.latrox.rematter.primal.EntangledLinks;
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

/** Compact zero-overlap screen for regular Entangled Link (status, peer coords, unpair, unified Settings with General, Sides & Access). */
public final class EntangledLinkScreen extends AbstractContainerScreen<EntangledLinkMenu> {
    private enum Tab { MAIN, SETTINGS }
    private enum SettingsSection { GENERAL, SIDES, ACCESS }

    private static final int W = 216, H = 136;
    private static final int ACCENT = PRIMAL;
    private DarkButton mainTab, settingsTab, generalSubTab, sidesSubTab, accessSubTab, unpairBtn;
    private SideDiagram diagram;
    private DeviceGeneralPanel generalPanel;
    private DeviceAccessPanel accessPanel;
    private Tab tab = Tab.MAIN;
    private SettingsSection settingsSection = SettingsSection.GENERAL;

    public EntangledLinkScreen(EntangledLinkMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.rematter.entangled_link." + key, args);
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
        mainTab = addRenderableWidget(DarkButton.icon(UiIcon.NETWORK_ONLINE,
                title, tr("throughput", EntangledLinks.MAX_DISTANCE),
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

        generalPanel = new DeviceGeneralPanel(font, this::addRenderableWidget, leftPos + 8, topPos + 38, W - 16, ACCENT, title,
                menu::securityMode, menu::canManageSecurity, () -> true,
                forward -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                                forward ? EntangledLinkMenu.CYCLE_SECURITY : EntangledLinkMenu.CYCLE_SECURITY_BACK);
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
                                forward ? EntangledLinkMenu.CYCLE_REDSTONE : EntangledLinkMenu.CYCLE_REDSTONE_BACK);
                    }
                },
                null, null, null);

        diagram = new SideDiagram(new SideDiagram.Host() {
            @Override public SideDiagram.Look look(MachinePortMode.Side side) {
                return DeviceGeneralPanel.portLook(side, EntangledLinkBlockEntity.isConfigurableSide(side),
                        menu.portMode(side), inspectNeighbor(side));
            }
            @Override public void step(MachinePortMode.Side side, boolean forward) {
                if (minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                            (forward ? EntangledLinkMenu.CYCLE_PORT : EntangledLinkMenu.CYCLE_PORT_BACK) + side.ordinal());
                }
            }
            @Override public boolean editable() { return true; }
        }, font, leftPos + 18, topPos + 38, this::addRenderableWidget);
        diagram.show(MachinePortMode.Side.BACK);

        unpairBtn = addRenderableWidget(DarkButton.labelIcon(UiIcon.CANCEL,
                Component.translatable("screen.rematter.dimensional_link.unpair"),
                tr("unpair_hint"),
                leftPos + 148, topPos + 45, 56, 16, b -> sendUpdate(menu.customName(), true)));
        unpairBtn.setAccent(ACCENT);

        refreshWidgets();
    }

    private SideDiagram.@Nullable Neighbor inspectNeighbor(MachinePortMode.Side side) {
        if (minecraft == null || minecraft.level == null || !minecraft.level.hasChunkAt(menu.blockPos())) return null;
        var state = minecraft.level.getBlockState(menu.blockPos());
        if (!state.hasProperty(EntangledLinkBlock.FACING)) return null;
        return SideDiagram.inspectNeighbor(minecraft.level, menu.blockPos(), state.getValue(EntangledLinkBlock.FACING), side);
    }

    private void sendUpdate(String nextName, boolean clearLink) {
        menu.setClientCustomName(nextName);
        if (clearLink) menu.setClientUnpaired();
        ClientPacketDistributor.sendToServer(new EntangledLinkPayload.Update(menu.containerId, nextName, clearLink));
        refreshWidgets();
    }

    private boolean showingSides() { return tab == Tab.SETTINGS && settingsSection == SettingsSection.SIDES; }
    private boolean showingGeneral() { return tab == Tab.SETTINGS && settingsSection == SettingsSection.GENERAL; }
    private boolean showingAccess() { return tab == Tab.SETTINGS && settingsSection == SettingsSection.ACCESS; }

    private void refreshWidgets() {
        if (unpairBtn == null) return;
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

        unpairBtn.visible = unpairBtn.active = tab == Tab.MAIN && menu.hasPeer();
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
            return;
        }
        ScreenChrome.inset(g, leftPos + 8, topPos + 20, W - 16, 16);
        ScreenChrome.inset(g, leftPos + 8, topPos + 39, W - 16, 86);
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
                        EntangledLinkBlockEntity.isConfigurableSide(side), menu.portMode(side), 18, 94, 180);
            }
            return;
        }

        if (menu.redstonePaused()) {
            ScreenChrome.icon(g, UiIcon.STATUS_BLOCKED, 9, 20, WHITE);
            ScreenChrome.text(g, font, Component.translatable("status.rematter.redstone_paused").getString(), 27, 24, W - 40, WARNING);
        } else {
            int st = menu.linkState();
            UiIcon icon = st == 1 ? UiIcon.NETWORK_ONLINE : st == 2 ? UiIcon.STATUS_WAITING : UiIcon.NETWORK_OFFLINE;
            int col = st == 1 ? PRIMAL_LIGHT : st == 2 ? WARNING : MUTED;
            ScreenChrome.icon(g, icon, 9, 20, col);
            String statusText = st == 1
                    ? tr("state_linked", menu.distance(), EntangledLinks.MAX_DISTANCE).getString()
                    : st == 2
                    ? Component.translatable("screen.rematter.dimensional_link.state_remote_unloaded").getString()
                    : Component.translatable("screen.rematter.dimensional_link.state_unpaired").getString();
            ScreenChrome.text(g, font, statusText, 27, 24, W - 40, col);
        }

        if (menu.hasPeer()) {
            var p = menu.peerPos();
            ScreenChrome.text(g, font, tr("peer_coords", p.getX(), p.getY(), p.getZ()).getString(), 12, 47, 132, TEXT);
        } else {
            ScreenChrome.text(g, font, tr("pair_hint", EntangledLinks.MAX_DISTANCE).getString(), 12, 47, 192, MUTED);
        }
        ScreenChrome.text(g, font, tr("throughput", EntangledLinks.MAX_DISTANCE).getString(), 12, 66, 192, PRIMAL_LIGHT);
        ScreenChrome.twoLines(g, font, tr("upgrade_note").getString(), 12, 84, 192, MUTED);
    }
}
