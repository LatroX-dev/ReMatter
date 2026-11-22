package dev.latrox.rematter.client;

import dev.latrox.rematter.chemistry.Phase;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/** Visual tooltip rows pairing each ReMatter 16 px substance badge with its full name, phase, and quantity. */
public final class DiscoveryLensTooltip {
    private static final int BADGE_SIZE = 16;
    private static final int GAP = 4;
    private static final int ROW_HEIGHT = 18;
    private static final int TEXT_OFFSET_Y = 4;

    public record Data(List<DiscoveryLensInspector.KnownTrace> traces) implements TooltipComponent {
        public Data {
            traces = List.copyOf(traces);
        }
    }

    public static final class Client implements ClientTooltipComponent {
        private final List<DiscoveryLensInspector.KnownTrace> traces;

        public Client(Data data) {
            this.traces = data.traces();
        }

        @Override
        public int getHeight(Font font) {
            return traces.isEmpty() ? 0 : traces.size() * ROW_HEIGHT;
        }

        @Override
        public int getWidth(Font font) {
            int max = 0;
            for (var trace : traces) {
                int w = BADGE_SIZE + GAP + font.width(formatLabel(trace));
                if (w > max) max = w;
            }
            return max;
        }

        @Override
        public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
            int rowY = y;
            for (var trace : traces) {
                SubstanceIcon.draw(graphics, x, rowY, trace.key());
                graphics.text(font, formatLabel(trace), x + BADGE_SIZE + GAP, rowY + TEXT_OFFSET_Y,
                        phaseColor(trace.key().phase()), true);
                rowY += ROW_HEIGHT;
            }
        }
    }

    static Component formatLabel(DiscoveryLensInspector.KnownTrace trace) {
        return Component.translatable(
                "hud.rematter.discovery_lens.trace_line",
                SubstanceNames.substance(trace.key()),
                SubstanceNames.phase(trace.key().phase()),
                Quantities.number(trace.amount()));
    }

    static int phaseColor(Phase phase) {
        return switch (phase) {
            case SOLID -> UiPalette.ACCENT;
            case LIQUID -> UiPalette.LIQUID;
            case GAS -> UiPalette.GAS;
        };
    }

    private DiscoveryLensTooltip() {}
}
