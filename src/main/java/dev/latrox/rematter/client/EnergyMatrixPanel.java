package dev.latrox.rematter.client;

import static dev.latrox.rematter.client.UiPalette.*;

import dev.latrox.rematter.client.ui.DarkButton;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.network.MachineActionPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Zero-overlap Main view for the 64-bit Energy Matrix multiblock. */
final class EnergyMatrixPanel {
    private final MachineMenu menu;
    private final Font font;
    private final Consumer<AbstractWidget> addWidget;
    private final int left, top, width;
    private DarkButton autoOutputBtn;
    private boolean visible;

    EnergyMatrixPanel(MachineMenu menu, Font font, Consumer<AbstractWidget> addWidget, int left, int top, int width) {
        this.menu = menu;
        this.font = font;
        this.addWidget = addWidget;
        this.left = left;
        this.top = top;
        this.width = width;
    }

    void initWidgets() {
        autoOutputBtn = DarkButton.labelIcon(
                UiIcon.EJECT,
                tr("auto_output", Component.translatable("screen.rematter.on")),
                tr("auto_output_hint"),
                left, top + 81, 128, 18,
                b -> ClientPacketDistributor.sendToServer(
                        new MachineActionPayload(menu.containerId, MachineActionPayload.Action.TOGGLE_MATRIX_OUTPUT, "", 0)));
        autoOutputBtn.setAccent(EMBER);
        addWidget.accept(autoOutputBtn);
        tick();
    }

    void setVisible(boolean value) {
        visible = value;
        if (autoOutputBtn != null) {
            autoOutputBtn.visible = value;
            autoOutputBtn.active = value;
        }
        if (value) tick();
    }

    void tick() {
        if (autoOutputBtn == null) return;
        boolean autoOut = extraBool("matrixAutoOutput", true);
        autoOutputBtn.visible = visible;
        autoOutputBtn.active = visible;
        autoOutputBtn.setSelected(autoOut);
        autoOutputBtn.setMessage(tr("auto_output", Component.translatable("screen.rematter." + (autoOut ? "on" : "off"))));
        autoOutputBtn.setTooltip(Tooltip.create(tr("auto_output_hint")));
    }

    void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (!visible) return;
        long energy = extraLong("matrixEnergy", 0L);
        long capacity = extraLong("matrixCapacity", 0L);
        long transferRate = extraLong("matrixTransferRate", 0L);
        long inRate = extraLong("matrixInRate", 0L);
        long outRate = extraLong("matrixOutRate", 0L);
        long cells = extraLong("matrixCells", 0L);
        long providers = extraLong("matrixProviders", 0L);
        int pctTenths = capacity > 0L ? (int) Math.clamp(Math.min(energy, capacity) * 1_000L / capacity, 0L, 1_000L) : 0;

        // 1. Full-width horizontal Matrix Energy Bar (y = top..top+17 -> window 20..37)
        ScreenChrome.inset(g, left, top, width, 18);
        int innerW = width - 4;
        int fillW = capacity > 0L ? (int) Math.clamp(Math.min(energy, capacity) * innerW / capacity, 0L, innerW) : 0;
        if (fillW > 0) {
            int barCol = mix(EMBER, PRIMAL_GOLD, pctTenths / 1000F * 0.55F);
            g.fill(left + 2, top + 2, left + 2 + fillW, top + 16, barCol);
            g.fill(left + 2, top + 2, left + 2 + fillW, top + 4, tint(barCol, 0.25F));
        }
        String barText = Quantities.compact(energy) + " / " + Quantities.compact(capacity) + " FE · "
                + (pctTenths / 10) + "." + (pctTenths % 10) + "%";
        int barTw = Math.min(innerW, font.width(barText));
        ScreenChrome.text(g, font, barText, left + (width - barTw) / 2, top + 5, innerW, TEXT);

        // 2. Discharge (-) Slot 0 at window (25, 43) and Charge (+) Slot 1 at window (135, 43)
        // Panel left=leftPos+8, top=topPos+20 -> Slot 0 is left+17..left+33, top+23..top+39; Slot 1 is left+127..left+143
        ScreenChrome.text(g, font, "-", left + 5, top + 27, 10, PORT_ENERGY_INPUT);
        ScreenChrome.text(g, font, "+", left + 148, top + 27, 10, EMBER);
        // Center bridge inset between Slot 0 and Slot 1 (left+38..left+122 -> width 84, top+22..top+40)
        ScreenChrome.inset(g, left + 38, top + 22, 84, 18);
        String netFlow = inRate >= outRate
                ? "+" + Quantities.compact(inRate - outRate) + " FE/t"
                : "-" + Quantities.compact(outRate - inRate) + " FE/t";
        int netCol = inRate > outRate ? ACCENT : outRate > inRate ? WARNING : MUTED;
        int netTw = Math.min(80, font.width(netFlow));
        ScreenChrome.text(g, font, netFlow, left + 38 + (84 - netTw) / 2, top + 27, 80, netCol);

        // Right-side bay label next to Charge slot (left+158..left+200 -> width 42)
        ScreenChrome.text(g, font, tr("bay_charge_short").getString(), left + 157, top + 27, 43, MUTED);

        // 3. Telemetry inset (top+44..top+77 -> window 64..97, h=33)
        ScreenChrome.inset(g, left, top + 44, width, 33);
        String inLine = tr("in_rate", Quantities.compact(inRate)).getString();
        String outLine = tr("out_rate", Quantities.compact(outRate)).getString();
        ScreenChrome.text(g, font, inLine, left + 6, top + 49, 92, inRate > 0 ? ACCENT : MUTED);
        int outW = Math.min(92, font.width(outLine));
        ScreenChrome.text(g, font, outLine, left + width - 6 - outW, top + 49, 92, outRate > 0 ? WARNING : MUTED);

        g.fill(left + 5, top + 60, left + width - 5, top + 61, BORDER);

        String maxLine = tr("max_rate", Quantities.compact(transferRate)).getString();
        String modLine = tr("modules_short", cells, providers).getString();
        ScreenChrome.text(g, font, maxLine, left + 6, top + 64, 114, TEXT);
        int modW = Math.min(72, font.width(modLine));
        ScreenChrome.text(g, font, modLine, left + width - 6 - modW, top + 64, 72, MUTED);

        // 4. Status indicator to the right of Auto-Output button (left+132..left+200, top+81..top+99)
        String status = menu.snapshot().status();
        ScreenChrome.icon(g, StatusText.icon(status, menu.kind()), left + 132, top + 82, -1);
        ScreenChrome.text(g, font, StatusText.of(status, 0, 0).getString(), left + 149, top + 86, 51,
                status.equals("working") ? EMBER : status.equals("energy_full") ? ACCENT : MUTED);

        // Hover tooltip on the energy bar showing exact uncompacted 64-bit numbers.
        if (mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + 18) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("screen.rematter.energy_buffer", Quantities.number(energy), Quantities.number(capacity)));
            lines.add(tr("max_rate_exact", Quantities.number(transferRate)));
            lines.add(tr("flow_exact", Quantities.number(inRate), Quantities.number(outRate)));
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    private long extraLong(String key, long fallback) {
        var extras = menu.extras();
        return extras.has(key) ? extras.get(key).getAsLong() : fallback;
    }

    private boolean extraBool(String key, boolean fallback) {
        var extras = menu.extras();
        return extras.has(key) ? extras.get(key).getAsBoolean() : fallback;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.rematter.matrix." + key, args);
    }
}
