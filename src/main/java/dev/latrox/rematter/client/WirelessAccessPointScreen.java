package dev.latrox.rematter.client;

import static dev.latrox.rematter.client.UiPalette.*;

import dev.latrox.rematter.client.ui.DarkButton;
import dev.latrox.rematter.content.WirelessAccessPointBlockEntity;
import dev.latrox.rematter.content.WirelessAccessPointMenu;
import dev.latrox.rematter.network.ChunkMapPayload;
import dev.latrox.rematter.network.ClientChunkMaps;
import dev.latrox.rematter.network.WirelessAccessPointPayload;
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

/** Console for Wireless Access Point: Boosters, Dimensional Card, Terminal dock, Chunk Anchor tab with 5x5 map, and unified Settings tab (General & Access). */
public final class WirelessAccessPointScreen extends AbstractContainerScreen<WirelessAccessPointMenu> {
    private enum Tab { MAIN, ANCHOR, SETTINGS }
    private enum SettingsSection { GENERAL, ACCESS }

    private static final int ACCENT = BLUE;
    private DarkButton mainTab, anchorTab, settingsTab, generalSubTab, accessSubTab;
    private DeviceGeneralPanel generalPanel;
    private DeviceAccessPanel accessPanel;
    private Tab tab = Tab.MAIN;
    private SettingsSection settingsSection = SettingsSection.GENERAL;

    public WirelessAccessPointScreen(WirelessAccessPointMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 216, 216);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.rematter.wireless_ap." + key, args);
    }

    @Override
    protected void init() {
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
                () -> { settingsSection = SettingsSection.GENERAL; refresh(); });
        accessSubTab = DeviceGeneralPanel.createSubTab(this::addRenderableWidget, UiIcon.ACCESS,
                Component.translatable("screen.rematter.settings.section.access"),
                Component.translatable("screen.rematter.settings.section_hint.access"),
                leftPos + 32, topPos + 20, ACCENT,
                () -> { settingsSection = SettingsSection.ACCESS; refresh(); });

        generalPanel = new DeviceGeneralPanel(font, this::addRenderableWidget, leftPos + 8, topPos + 38, imageWidth - 16, ACCENT, title,
                menu::securityMode, menu::canManageSecurity, () -> true,
                forward -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                                forward ? WirelessAccessPointMenu.CYCLE_SECURITY : WirelessAccessPointMenu.CYCLE_SECURITY_BACK);
                    }
                },
                menu::customName,
                clean -> {
                    menu.setClientCustomName(clean);
                    ClientPacketDistributor.sendToServer(new WirelessAccessPointPayload.Rename(menu.containerId, clean));
                },
                null, null);

        accessPanel = new DeviceAccessPanel(font, this::addRenderableWidget, leftPos + 8, topPos + 40, imageWidth - 16, ACCENT,
                () -> true,
                menu::redstoneMode,
                forward -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                                forward ? WirelessAccessPointMenu.CYCLE_REDSTONE : WirelessAccessPointMenu.CYCLE_REDSTONE_BACK);
                    }
                },
                null, null, null);
        refresh();
    }

    private void setTab(Tab next) {
        tab = next;
        menu.setAnchorTab(next == Tab.ANCHOR);
        menu.setDeviceTab(next == Tab.SETTINGS);
        if (next == Tab.ANCHOR) {
            ClientPacketDistributor.sendToServer(new ChunkMapPayload.Request(menu.containerId));
        }
        refresh();
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

    @Override
    protected boolean hasClickedOutside(double mx, double my, int x, int y) {
        for (var r : viewerExclusionAreas()) {
            if (mx >= r.getX() && mx < r.getX() + r.getWidth() && my >= r.getY() && my < r.getY() + r.getHeight()) return false;
        }
        return super.hasClickedOutside(mx, my, x, y);
    }

    private boolean showingGeneral() { return tab == Tab.SETTINGS && settingsSection == SettingsSection.GENERAL; }
    private boolean showingAccess() { return tab == Tab.SETTINGS && settingsSection == SettingsSection.ACCESS; }

    private void refresh() {
        menu.setAnchorTab(tab == Tab.ANCHOR);
        menu.setDeviceTab(tab == Tab.SETTINGS);
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
        if (accessSubTab != null) {
            accessSubTab.visible = accessSubTab.active = inSettings;
            accessSubTab.setAccent(ACCENT);
            accessSubTab.setSelected(showingAccess());
        }
        if (generalPanel != null) generalPanel.refresh(showingGeneral(), ACCENT);
        if (accessPanel != null) accessPanel.refresh(showingAccess(), ACCENT);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (generalPanel != null && generalPanel.handlesKey(event)) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (generalPanel != null && generalPanel.handlesChar(event)) return true;
        return super.charTyped(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractBackground(g, mx, my, partial);
        ScreenChrome.panel(g, leftPos, topPos, imageWidth, imageHeight);
        g.fill(leftPos + 8, topPos + 17, leftPos + 208, topPos + 18, BORDER);
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
            ScreenChrome.inset(g, leftPos + 18, topPos + 38, 180, 12);
            int fill = (int) (176L * menu.energy() / Math.max(1, menu.capacity()));
            g.fill(leftPos + 20, topPos + 40, leftPos + 20 + fill, topPos + 48, ACCENT);
        }

        for (int i = 0; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            if (!slot.isActive()) continue;
            ScreenChrome.slot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
            if (i == WirelessAccessPointBlockEntity.SLOT_RANGE || i == WirelessAccessPointBlockEntity.SLOT_ANCHOR) {
                g.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y, UPGRADE);
            }
            if (!slot.hasItem()) {
                if (i == WirelessAccessPointBlockEntity.SLOT_RANGE) {
                    ScreenChrome.icon(g, UiIcon.NETWORK_ONLINE, leftPos + slot.x, topPos + slot.y, GHOST);
                } else if (i == WirelessAccessPointBlockEntity.SLOT_TERMINAL) {
                    ScreenChrome.icon(g, UiIcon.OPEN, leftPos + slot.x, topPos + slot.y, GHOST);
                } else if (i == WirelessAccessPointBlockEntity.SLOT_ANCHOR) {
                    ScreenChrome.icon(g, UiIcon.LOCATE, leftPos + slot.x, topPos + slot.y, GHOST);
                }
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        DeviceGeneralPanel.drawTitle(g, font, menu.customName(), title, imageWidth - 16);

        if (tab == Tab.SETTINGS) {
            String sectionTitle = Component.translatable(showingGeneral()
                    ? "screen.rematter.settings.section.general"
                    : "screen.rematter.settings.section.access").getString();
            ScreenChrome.text(g, font, sectionTitle, 58, 25, imageWidth - 66, TEXT);
            if (showingGeneral()) {
                if (generalPanel != null) generalPanel.extractLabels(g, leftPos, topPos);
            } else {
                if (accessPanel != null) accessPanel.extractLabels(g, leftPos, topPos);
            }
        } else if (tab == Tab.ANCHOR) {
            drawAnchorLabels(g);
        } else {
            if (menu.redstonePaused()) {
                ScreenChrome.icon(g, UiIcon.STATUS_BLOCKED, 16, 20, WHITE);
                ScreenChrome.text(g, font, Component.translatable("status.rematter.redstone_paused").getString(), 34, 24, 164, WARNING);
            } else {
                boolean online = menu.online();
                boolean netReady = menu.networkReady();
                UiIcon statusIcon = online && netReady ? UiIcon.NETWORK_ONLINE : online ? UiIcon.STATUS_WAITING : UiIcon.NETWORK_OFFLINE;
                int statusColor = online && netReady ? ACCENT_BRIGHT : online ? WARNING : MUTED;
                Component statusText = !online ? tr("status_offline") : netReady ? tr("status_online") : tr("status_no_storage");
                ScreenChrome.icon(g, statusIcon, 16, 20, onlineIconTint(online, netReady));
                ScreenChrome.text(g, font, statusText.getString(), 34, 24, 164, statusColor);
            }

            String rangeStr = menu.hasDimensionalCard()
                    ? tr("range_dimensional").getString()
                    : tr("range_short", Quantities.number(menu.effectiveRange())).getString();
            ScreenChrome.text(g, font, rangeStr, 18, 54, 102, menu.hasDimensionalCard() ? ACCENT_BRIGHT : TEXT);
            String rightTelemetry = menu.hasAnchorUpgrade()
                    ? tr("drain_anchor_short", Quantities.number(menu.effectiveDrain()),
                            menu.anchorActive() ? tr("anchor_on").getString() : tr("anchor_off").getString()).getString()
                    : tr("drain", Quantities.number(menu.effectiveDrain())).getString();
            ScreenChrome.text(g, font, rightTelemetry, 122, 54, 76, MUTED);

            drawCenteredSlotLabel(g, tr("range_slot_short").getString(), 72, 100, 68);
            drawCenteredSlotLabel(g, tr("terminal_short").getString(), 144, 100, 68);
        }
        ScreenChrome.text(g, font, Component.translatable("screen.rematter.inventory").getString(), 18, 123, 180, MUTED);
    }

    private void drawAnchorLabels(GuiGraphicsExtractor g) {
        boolean installed = menu.hasAnchorUpgrade();
        boolean active = menu.anchorActive();
        int total = menu.anchorTotal();
        int max = Math.max(1, menu.anchorMax());
        ScreenChrome.text(g, font, Component.translatable("item.rematter.anchor_upgrade").getString(), 102, 30, 80, TEXT);

        ScreenChrome.icon(g, installed && active ? UiIcon.STATUS_WORKING : installed ? UiIcon.STATUS_BLOCKED : UiIcon.LOCATE,
                100, 51, installed ? -1 : MUTED);
        String anchorLine = !installed
                ? Component.translatable("screen.rematter.ui.upgrade_anchor_empty").getString()
                : active
                ? Component.translatable("screen.rematter.ui.upgrade_anchor_active", menu.anchorForced(), total, max).getString()
                : Component.translatable("screen.rematter.ui.upgrade_anchor_inactive", total, max).getString();
        ScreenChrome.text(g, font, anchorLine, 117, 55, 88, installed && active ? ACCENT : installed ? WARNING : MUTED);

        ScreenChrome.text(g, font, Component.translatable("screen.rematter.spatial_anchor.quota", total, max).getString(),
                102, 68, 102, total >= max && !active ? WARNING : TEXT);
        int line = 0;
        for (var part : font.split(tr("anchor_slot_hint", WirelessAccessPointBlockEntity.ANCHOR_DRAIN), 102)) {
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

    private void drawCenteredSlotLabel(GuiGraphicsExtractor g, String text, int centerX, int y, int maxW) {
        int w = Math.min(maxW, font.width(text));
        ScreenChrome.text(g, font, text, centerX - w / 2, y, maxW, MUTED);
    }

    private static int onlineIconTint(boolean online, boolean netReady) {
        if (!online) return MUTED;
        return netReady ? ACCENT_BRIGHT : WHITE;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractRenderState(g, mx, my, partial);
        if (tab == Tab.SETTINGS) return;
        int x = mx - leftPos, y = my - topPos;
        if (tab == Tab.ANCHOR) {
            var mapTip = ChunkMapWidget.tooltip(x, y, 10, 22, 4,
                    ClientChunkMaps.forContainer(menu.containerId), menu.anchorActive());
            if (mapTip != null) {
                g.setComponentTooltipForNextFrame(font, mapTip, mx, my);
                return;
            }
            var anchorSlot = menu.slots.get(WirelessAccessPointBlockEntity.SLOT_ANCHOR);
            if (anchorSlot.isActive() && insideSlot(x, y, anchorSlot.x, anchorSlot.y)) {
                if (!anchorSlot.hasItem()) {
                    g.setComponentTooltipForNextFrame(font, List.of(
                            Component.translatable("item.rematter.anchor_upgrade"),
                            tr("anchor_slot_hint", WirelessAccessPointBlockEntity.ANCHOR_DRAIN)), mx, my);
                } else {
                    g.setComponentTooltipForNextFrame(font, List.of(
                            Component.translatable("item.rematter.anchor_upgrade"),
                            tr("anchor_quota", menu.anchorTotal(), menu.anchorMax())), mx, my);
                }
            }
            return;
        }
        if (x >= 18 && x < 198 && y >= 38 && y < 52) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    tr("reserve", Quantities.number(menu.energy()), Quantities.number(menu.capacity())),
                    tr("drain", Quantities.number(menu.effectiveDrain()))), mx, my);
        }
        var rangeSlot = menu.slots.get(WirelessAccessPointBlockEntity.SLOT_RANGE);
        if (rangeSlot.isActive() && !rangeSlot.hasItem() && insideSlot(x, y, rangeSlot.x, rangeSlot.y)) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    tr("range_slot"),
                    tr("booster_slot_hint", WirelessAccessPointBlockEntity.RANGE_PER_BOOSTER,
                            WirelessAccessPointBlockEntity.MAX_RANGE, WirelessAccessPointBlockEntity.BOOSTER_DRAIN),
                    tr("dimensional_slot_hint", WirelessAccessPointBlockEntity.DIMENSIONAL_DRAIN)), mx, my);
        }
        var terminalSlot = menu.slots.get(WirelessAccessPointBlockEntity.SLOT_TERMINAL);
        if (terminalSlot.isActive() && !terminalSlot.hasItem() && insideSlot(x, y, terminalSlot.x, terminalSlot.y)) {
            g.setComponentTooltipForNextFrame(font, List.of(tr("terminal_slot"), tr("terminal_slot_hint")), mx, my);
        }
    }

    private static boolean insideSlot(int x, int y, int sx, int sy) {
        return x >= sx && x < sx + 16 && y >= sy && y < sy + 16;
    }
}
