package dev.latrox.rematter.client;

import dev.latrox.rematter.client.ScreenChrome.Icon;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.content.MachinePortMode;
import java.util.EnumMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A side overview tied to the machine's existing, server-validated menu. */
final class MachinePortSettingsScreen extends Screen {
    private final MachineMenu menu;
    private final EnumMap<MachinePortMode.Side, Button> buttons = new EnumMap<>(MachinePortMode.Side.class);
    private int left, top, panelWidth, panelHeight, rowHeight, columnWidth, rowsTop;
    private boolean showHelp;

    MachinePortSettingsScreen(MachineMenu menu) {
        super(Component.translatable("screen.rematter.ports"));
        this.menu = menu;
    }

    @Override protected void init() {
        panelWidth = Math.min(width - 12, 320);
        panelHeight = Math.min(height - 12, 188);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        showHelp = panelHeight >= 166;
        rowsTop = showHelp ? 49 : 29;
        rowHeight = (panelHeight - rowsTop - 29) / 3;
        columnWidth = (panelWidth - 26) / 2;
        buttons.clear();
        for (var side : MachinePortMode.Side.values()) {
            int i = side.ordinal();
            var button = addRenderableWidget(Button.builder(label(side), pressed -> {
                if (minecraft.player != null && minecraft.player.containerMenu == menu && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MachineMenu.CYCLE_PORT + i);
                }
            }).bounds(x(side), y(side) + 11, columnWidth, rowHeight - 13)
                    .tooltip(Tooltip.create(label(side))).build(builder -> new ConsoleButton(builder, side)));
            buttons.put(side, button);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), pressed -> onClose())
                .bounds(left + (panelWidth - 62) / 2, top + panelHeight - 24, 62, 18)
                .build(builder -> new ConsoleButton(builder, null)));
    }

    private int x(MachinePortMode.Side side) { return left + 10 + (columnWidth + 6) * (side.ordinal() % 2); }
    private int y(MachinePortMode.Side side) { return top + rowsTop + rowHeight * (side.ordinal() / 2); }
    private Component label(MachinePortMode.Side side) {
        return Component.translatable("port.rematter.side_mode", side.label(), menu.portMode(side).label());
    }

    @Override public void tick() {
        if (minecraft.player == null || minecraft.player.containerMenu != menu) { onClose(); return; }
        buttons.forEach((side, button) -> {
            button.setMessage(label(side));
            button.setTooltip(Tooltip.create(label(side)));
        });
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ScreenChrome.panel(graphics, left, top, panelWidth, panelHeight);
        ScreenChrome.icon(graphics, Icon.CONFIGURATION, left + 10, top + 7, UiPalette.ACCENT);
        graphics.text(font, ScreenChrome.fit(font, title.getString(), panelWidth - 39), left + 28, top + 9, UiPalette.TEXT, false);
        graphics.fill(left + 10, top + 22, left + panelWidth - 10, top + 23, UiPalette.BORDER);
        graphics.fill(left + 10, top + 22, left + 54, top + 23, UiPalette.machineAccent(menu.kind()));
        Component help = Component.translatable("screen.rematter.ports_cycle");
        if (showHelp) {
            ScreenChrome.twoLines(graphics, font, help.getString(), left + 10, top + 27, panelWidth - 20, UiPalette.MUTED);
        } else if (mouseX >= left + 10 && mouseX < left + panelWidth - 10 && mouseY >= top + 5 && mouseY < top + 22) {
            graphics.setTooltipForNextFrame(help, mouseX, mouseY);
        }
        for (var side : MachinePortMode.Side.values()) {
            graphics.text(font, ScreenChrome.fit(font, side.label().getString(), columnWidth - 4),
                    x(side) + 2, y(side), UiPalette.TEXT, false);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private final class ConsoleButton extends Button {
        private final MachinePortMode.Side side;

        private ConsoleButton(Builder builder, MachinePortMode.Side side) {
            super(builder);
            this.side = side;
        }

        @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY(), width = getWidth(), height = getHeight();
            ScreenChrome.raised(graphics, x, y, width, height, isHoveredOrFocused(), active);
            int textLeft = x + 5;
            if (side != null) {
                int markHeight = Math.clamp(height - 6, 0, 8);
                int markY = y + (height - markHeight) / 2;
                if (markHeight > 0) graphics.fill(x + 5, markY, x + 8, markY + markHeight, menu.portMode(side).color());
                textLeft = x + 13;
            }
            Component modeLabel = side == null ? getMessage() : menu.portMode(side).label();
            String label = ScreenChrome.fit(font, modeLabel.getString(), x + width - textLeft - 5);
            if (side == null) textLeft = x + (width - font.width(label)) / 2;
            graphics.text(font, label, textLeft, y + (height - 8) / 2, active ? UiPalette.TEXT : UiPalette.MUTED, false);
        }
    }
}
