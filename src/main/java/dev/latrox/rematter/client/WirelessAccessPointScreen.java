package dev.latrox.rematter.client;

import dev.latrox.rematter.content.WirelessAccessPointBlockEntity;
import dev.latrox.rematter.content.WirelessAccessPointMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** AE2-style console for Wireless Access Point showing status, FE reserve, range, FE/t draw, Boosters, and Terminal dock. */
public final class WirelessAccessPointScreen extends AbstractContainerScreen<WirelessAccessPointMenu> {
    public WirelessAccessPointScreen(WirelessAccessPointMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 216, 216);
    }

    private static Component tr(String key, Object... args) { return Component.translatable("screen.rematter.wireless_ap." + key, args); }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractBackground(g, mx, my, partial);
        ScreenChrome.panel(g, leftPos, topPos, imageWidth, imageHeight);
        g.fill(leftPos + 8, topPos + 17, leftPos + 208, topPos + 18, UiPalette.BORDER);
        g.fill(leftPos + 8, topPos + 17, leftPos + 56, topPos + 18, UiPalette.PORT_ENERGY_INPUT);

        ScreenChrome.inset(g, leftPos + 18, topPos + 34, 180, 12);
        int fill = (int) (176L * menu.energy() / Math.max(1, menu.capacity()));
        g.fill(leftPos + 20, topPos + 36, leftPos + 20 + fill, topPos + 44, UiPalette.PORT_ENERGY_INPUT);

        for (int i = 0; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            ScreenChrome.slot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
            if (!slot.hasItem()) {
                if (i == WirelessAccessPointBlockEntity.SLOT_BOOSTER) {
                    ScreenChrome.icon(g, UiIcon.PLUS, leftPos + slot.x, topPos + slot.y, UiPalette.GHOST);
                } else if (i == WirelessAccessPointBlockEntity.SLOT_TERMINAL) {
                    ScreenChrome.icon(g, UiIcon.OPEN, leftPos + slot.x, topPos + slot.y, UiPalette.GHOST);
                }
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        ScreenChrome.text(g, font, title.getString(), 8, 6, 200, UiPalette.TEXT);

        boolean online = menu.online();
        boolean netReady = menu.networkReady();
        UiIcon statusIcon = online && netReady ? UiIcon.NETWORK_ONLINE : online ? UiIcon.STATUS_WAITING : UiIcon.NETWORK_OFFLINE;
        int statusColor = online && netReady ? UiPalette.ACCENT_BRIGHT : online ? UiPalette.WARNING : UiPalette.MUTED;
        Component statusText = !online ? tr("status_offline") : netReady ? tr("status_online") : tr("status_no_storage");
        ScreenChrome.icon(g, statusIcon, 16, 19, OnlineIconTint(online, netReady));
        ScreenChrome.text(g, font, statusText.getString(), 34, 23, 164, statusColor);

        ScreenChrome.icon(g, UiIcon.LOCATE, 16, 48, UiPalette.PORT_ENERGY_INPUT);
        ScreenChrome.text(g, font, tr("range", Quantities.number(menu.effectiveRange()), menu.boosters()).getString(),
                34, 52, 164, UiPalette.TEXT);

        ScreenChrome.icon(g, UiIcon.ENERGY, 16, 63, UiPalette.PORT_ENERGY_INPUT);
        ScreenChrome.text(g, font, tr("drain", Quantities.number(menu.effectiveDrain())).getString(),
                34, 67, 164, UiPalette.MUTED);

        ScreenChrome.text(g, font, tr("booster_label").getString(), 48, 106, 64, UiPalette.MUTED);
        ScreenChrome.text(g, font, tr("terminal_label").getString(), 108, 106, 84, UiPalette.MUTED);
        ScreenChrome.text(g, font, Component.translatable("screen.rematter.inventory").getString(), 18, 123, 180, UiPalette.MUTED);
    }

    private static int OnlineIconTint(boolean online, boolean netReady) {
        if (!online) return UiPalette.MUTED;
        return netReady ? UiPalette.ACCENT_BRIGHT : UiPalette.WHITE;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractRenderState(g, mx, my, partial);
        int x = mx - leftPos, y = my - topPos;
        if (x >= 18 && x < 198 && y >= 34 && y < 48) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    tr("reserve", Quantities.number(menu.energy()), Quantities.number(menu.capacity())),
                    tr("drain", Quantities.number(menu.effectiveDrain()))), mx, my);
        }
        var boosterSlot = menu.slots.get(WirelessAccessPointBlockEntity.SLOT_BOOSTER);
        if (!boosterSlot.hasItem() && x >= boosterSlot.x && x < boosterSlot.x + 16 && y >= boosterSlot.y && y < boosterSlot.y + 16) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    tr("booster_slot"),
                    tr("booster_slot_hint", WirelessAccessPointBlockEntity.RANGE_PER_BOOSTER, WirelessAccessPointBlockEntity.MAX_RANGE)), mx, my);
        }
        var terminalSlot = menu.slots.get(WirelessAccessPointBlockEntity.SLOT_TERMINAL);
        if (!terminalSlot.hasItem() && x >= terminalSlot.x && x < terminalSlot.x + 16 && y >= terminalSlot.y && y < terminalSlot.y + 16) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    tr("terminal_slot"),
                    tr("terminal_slot_hint")), mx, my);
        }
    }
}
