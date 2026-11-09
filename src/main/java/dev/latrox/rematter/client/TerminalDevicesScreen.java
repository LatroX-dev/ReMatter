package dev.latrox.rematter.client;

import static dev.latrox.rematter.client.UiPalette.*;

import dev.latrox.rematter.client.ScreenChrome.Icon;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.network.TerminalDeviceActionPayload;
import dev.latrox.rematter.network.TerminalDeviceActionPayload.Action;
import dev.latrox.rematter.network.TerminalDevicesPayload;
import dev.latrox.rematter.network.TerminalDevicesPayload.Device;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** A terminal session can open full machine controls or queue an already selected process. */
final class TerminalDevicesScreen extends Screen {
    private static final int ROW_PITCH = 39;
    private static final int ROW_HEIGHT = 35;
    private static final int ROW_START = 65;
    private static final int ACTION_WIDTH = 68;
    private final MachineMenu menu;
    private final List<Row> rows = new ArrayList<>();
    private List<Device> filtered = List.of();
    private TerminalDevicesPayload.Listing displayed = TerminalDevicesPayload.Listing.EMPTY;
    private EditBox search;
    private Button previous;
    private Button next;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int page;

    TerminalDevicesScreen(MachineMenu menu) {
        super(tr("devices"));
        this.menu = menu;
    }

    @Override protected void init() {
        panelWidth = Math.min(434, width - 12);
        panelHeight = Math.min(298, height - 12);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        String query = search == null ? "" : search.getValue();
        search = addRenderableWidget(new EditBox(font, left + 12, top + 29, panelWidth - 24, 17, tr("search")));
        search.setBordered(false);
        search.setMaxLength(128);
        search.setTextColor(TEXT);
        search.setHint(tr("search").copy().withStyle(style -> style.withColor(MUTED & 0xffffff)));
        search.setValue(query);
        rows.clear();
        int footer = top + panelHeight - 25;
        int count = Math.max(1, (footer - top - ROW_START - 2) / ROW_PITCH);
        for (int i = 0; i < count; i++) {
            int y = top + ROW_START + i * ROW_PITCH;
            DeviceButton device = addRenderableWidget(new DeviceButton(Button.builder(Component.empty(),
                    b -> send(((DeviceButton) b).device, Action.OPEN))
                    .bounds(left + 8, y, panelWidth - ACTION_WIDTH - 20, ROW_HEIGHT)));
            Button queue = control("queue", left + panelWidth - ACTION_WIDTH - 8, y + 5, ACTION_WIDTH, 25,
                    b -> send(device.device, Action.QUEUE_BATCH));
            rows.add(new Row(device, queue));
        }
        previous = control("previous", left + 8, footer, 24, 18, b -> { page--; refreshRows(); });
        next = control("next", left + 36, footer, 24, 18, b -> { page++; refreshRows(); });
        control("close", left + panelWidth - 58, footer, 50, 18, b -> onClose());
        search.setResponder(value -> { page = 0; filter(); });
        displayed = menu.terminalDevices();
        filter();
        setInitialFocus(search);
    }

    private Button control(String key, int x, int y, int width, int height, Button.OnPress action) {
        return addRenderableWidget(Button.builder(tr(key), action).bounds(x, y, width, height).build(PanelButton::new));
    }

    private void filter() {
        String query = search.getValue().strip().toLowerCase(Locale.ROOT);
        filtered = displayed.devices().stream()
                .filter(device -> (name(device).getString() + " " + position(device) + " " + recipe(device).getString())
                        .toLowerCase(Locale.ROOT).contains(query))
                .toList();
        refreshRows();
    }

    private void refreshRows() {
        page = Math.clamp(page, 0, pages() - 1);
        for (int i = 0; i < rows.size(); i++) {
            int index = page * rows.size() + i;
            var row = rows.get(i);
            var device = index < filtered.size() ? filtered.get(index) : null;
            row.device.setDevice(device);
            row.queue.visible = device != null && !device.recipeId().isEmpty();
            row.queue.active = device != null && device.canQueue();
            row.device.setWidth(row.queue.visible ? panelWidth - ACTION_WIDTH - 20 : panelWidth - 16);
            if (device != null) row.queue.setTooltip(Tooltip.create(tr("queue_hint", recipe(device))));
        }
        previous.visible = next.visible = pages() > 1;
        previous.active = page > 0;
        next.active = page + 1 < pages();
    }

    private int pages() { return Math.max(1, (filtered.size() + rows.size() - 1) / rows.size()); }

    private void send(Device device, Action action) {
        if (device != null && minecraft.player != null && minecraft.player.containerMenu == menu) {
            ClientPacketDistributor.sendToServer(new TerminalDeviceActionPayload(menu.containerId, device.position(),
                    action, action == Action.QUEUE_BATCH ? device.recipeId() : ""));
        }
    }

    @Override public void tick() {
        if (minecraft.player == null || minecraft.player.containerMenu != menu) { onClose(); return; }
        if (displayed != menu.terminalDevices()) {
            displayed = menu.terminalDevices();
            filter();
        }
    }

    @Override public void onClose() {
        if (minecraft.player != null && minecraft.player.containerMenu == menu && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MachineMenu.CLOSE_TERMINAL_DEVICES);
        }
        super.onClose();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        ScreenChrome.panel(g, left, top, panelWidth, panelHeight);
        ScreenChrome.icon(g, Icon.NETWORK, left + 9, top + 7, ACCENT);
        draw(g, title, left + 26, top + 9, panelWidth - 35, TEXT);
        ScreenChrome.inset(g, left + 8, top + 25, panelWidth - 16, 21);
        draw(g, displayed.truncated() ? tr("count_truncated", filtered.size(), displayed.total()) : tr("count", filtered.size()),
                left + 9, top + 53, panelWidth - 18, displayed.truncated() ? WARNING : MUTED);
        if (filtered.isEmpty()) draw(g, emptyMessage(), left + 9, top + ROW_START + 5, panelWidth - 18, MUTED);
        if (pages() > 1) draw(g, tr("page", page + 1, pages()), left + 66, top + panelHeight - 20, panelWidth - 129, MUTED);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private Component emptyMessage() {
        if (displayed.containerId() != menu.containerId) return tr("loading");
        String network = menu.snapshot().network().status();
        if (network.equals("network_unloaded") || network.equals("network_invalid") || network.equals("network_too_large")) {
            return Component.translatable("status.rematter." + network);
        }
        return displayed.devices().isEmpty() ? tr("empty") : tr("no_matches");
    }

    private void draw(GuiGraphicsExtractor g, Component text, int x, int y, int width, int color) {
        g.text(font, ScreenChrome.fit(font, text.getString(), width), x, y, color, false);
    }

    private static Component tr(String key, Object... args) { return Component.translatable("screen.rematter.terminal." + key, args); }
    private static Component name(Device device) { return Component.translatable("block.rematter." + device.kind().id()); }
    private static Component status(Device device) { return Component.translatable("status.rematter." + device.status()); }
    private static Component recipe(Device device) {
        return device.recipeId().isEmpty() ? Component.empty() : SubstanceNames.choice(device.kind(), device.recipeId());
    }
    private static String position(Device device) {
        var pos = device.position();
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private record Row(DeviceButton device, Button queue) {}

    private class PanelButton extends Button {
        PanelButton(Builder builder) { super(builder); }

        @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
            ScreenChrome.raised(g, getX(), getY(), getWidth(), getHeight(), isHoveredOrFocused(), active);
            String label = ScreenChrome.fit(font, getMessage().getString(), getWidth() - 6);
            g.text(font, label, getX() + (getWidth() - font.width(label)) / 2, getY() + (getHeight() - 8) / 2,
                    active ? TEXT : DISABLED, false);
        }
    }

    private final class DeviceButton extends Button {
        private Device device;

        DeviceButton(Builder builder) { super(builder); }

        void setDevice(Device value) {
            device = value;
            visible = active = value != null;
            if (value == null) return;
            setMessage(name(value).copy().append(" " + position(value)));
            var details = name(value).copy().append("\n" + position(value) + "\n").append(status(value));
            if (!value.recipeId().isEmpty()) details.append("\n").append(tr("selected", recipe(value)));
            if (value.duration() > 0) details.append("\n").append(tr("progress", value.progress(), value.duration()));
            if (value.queuedBatches() > 0) details.append("\n").append(tr("queued", value.queuedBatches()));
            if(value.target()>0)details.append("\n").append(Component.translatable(value.networkTarget()?"screen.rematter.target_network_count":"screen.rematter.target_local_count",
                    value.targetStock()<0?"?":Quantities.number(value.targetStock()),Quantities.number(value.target())));
            if(value.batchCapacity()>1)details.append("\n").append(Component.translatable("screen.rematter.parallel_state",value.workingBatches(),value.batchCapacity()));
            setTooltip(Tooltip.create(details.append("\n").append(tr("open_hint"))));
        }

        @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
            if (device == null) return;
            ScreenChrome.raised(g, getX(), getY(), getWidth(), getHeight(), isHoveredOrFocused(), active);
            ScreenChrome.icon(g, device.kind().isStorage() ? Icon.STORAGE : device.kind().processes() ? Icon.PLAY : Icon.NETWORK,
                    getX() + 6, getY() + 5, ACCENT);
            int textX = getX() + 24;
            int textWidth = getWidth() - 30;
            draw(g, name(device), textX, getY() + 4, textWidth, TEXT);
            if (!device.recipeId().isEmpty()) draw(g, recipe(device), textX, getY() + 14, textWidth, MUTED);
            int statusY = getY() + (device.recipeId().isEmpty() ? 16 : 24);
            String status = device.target()>0?Component.translatable(device.networkTarget()?"screen.rematter.target_network_count":"screen.rematter.target_local_count",
                    device.targetStock()<0?"?":Quantities.compact(device.targetStock()),Quantities.compact(device.target())).getString():status(device).getString();
            String coordinates = position(device);
            int coordinatesWidth = font.width(coordinates);
            if (font.width(status) + coordinatesWidth + 12 <= textWidth) {
                g.text(font, coordinates, getX() + getWidth() - 6 - coordinatesWidth, statusY, MUTED, false);
                textWidth -= coordinatesWidth + 12;
            }
            g.text(font, ScreenChrome.fit(font, status, textWidth), textX, statusY, MUTED, false);
            if (device.duration() > 0) {
                int width = (int) Math.clamp((long) (getWidth() - 4) * device.progress() / device.duration(), 0, getWidth() - 4);
                g.fill(getX() + 1, getY() + getHeight() - 2, getX() + 1 + width, getY() + getHeight() - 1, ACCENT);
            }
        }
    }
}
