package dev.latrox.rematter.client;

import dev.latrox.rematter.content.MachinePortMode.Side;
import java.util.EnumMap;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The six sides of a device, unfolded around its front and named: Top above the Front, Bottom below it, Left and
 * Right beside it, Back at the far right. A side shows what the block shows: the port plate with the ring of its
 * role, or the plain casing of a side without a connection. A side that can never be a connection is drawn sunk
 * and cannot be clicked. A left click steps a role forward; a right click or Shift steps it back.
 */
final class SideDiagram {
    static final int WIDTH = 179, HEIGHT = 52;
    private static final int PILL = 16, STEP = 18, COLUMN = 45, SIDE = 44, POLE = 64;

    /** What a side shows on the block. */
    enum Face { PLATE, PLAIN, OPEN }
    /** One side as its host sees it; {@code icon} replaces the plate glyph where a role has no ring colour. */
    record Look(Face face, int ring, @Nullable UiIcon icon, boolean closed, Component tooltip) {}
    interface Host {
        Look look(Side side);
        void step(Side side, boolean forward);
        boolean editable();
    }

    private final EnumMap<Side, Pill> pills = new EnumMap<>(Side.class);
    private final Host host;
    private Side shown = Side.BACK;

    SideDiagram(Host host, Font font, int left, int top, Consumer<AbstractWidget> add) {
        this.host = host;
        for (Side side : Side.values()) {
            int[] place = place(side);
            var pill = new Pill(side, font, left + place[0], top + place[1], place[2]);
            pills.put(side, pill); add.accept(pill);
        }
    }
    /** Left, front, right and back share one row; top and bottom stand over and under the front. */
    private static int[] place(Side side) {
        return switch (side) {
            case TOP -> new int[]{COLUMN, 0, POLE};
            case LEFT -> new int[]{0, STEP, SIDE};
            case FRONT -> new int[]{COLUMN, STEP, SIDE};
            case RIGHT -> new int[]{2 * COLUMN, STEP, SIDE};
            case BACK -> new int[]{3 * COLUMN, STEP, SIDE};
            case BOTTOM -> new int[]{COLUMN, 2 * STEP, POLE};
        };
    }
    Iterable<? extends AbstractWidget> widgets() { return pills.values(); }
    /** The side under the pointer, or the one last changed. */
    Side shown() {
        for (var entry : pills.entrySet()) if (entry.getValue().visible && entry.getValue().isHoveredOrFocused()) return entry.getKey();
        return shown;
    }
    void show(Side side) { shown = side; }
    void update(boolean visible) {
        for (var entry : pills.entrySet()) {
            var pill = entry.getValue(); var look = host.look(entry.getKey());
            pill.look = look; pill.visible = visible;
            pill.active = visible && !look.closed() && host.editable();
            pill.setTooltip(Tooltip.create(look.tooltip()));
        }
    }

    /** The port plate of the block at twelve pixels: seam, ring in the role colour, well and contact. */
    static void plate(GuiGraphicsExtractor g, int x, int y, int ring) {
        g.fill(x, y, x + 12, y + 12, UiPalette.SEAM);
        g.fill(x + 1, y + 1, x + 11, y + 11, ring);
        // Lit from the top left like the ring of the block, so the neutral ring reads on the graphite button too.
        g.fill(x + 1, y + 1, x + 11, y + 2, UiPalette.tint(ring, .3F));
        g.fill(x + 1, y + 1, x + 2, y + 11, UiPalette.tint(ring, .3F));
        g.fill(x + 3, y + 3, x + 9, y + 9, UiPalette.SEAM);
        g.fill(x + 4, y + 4, x + 8, y + 8, UiPalette.RECESS);
        g.fill(x + 5, y + 5, x + 7, y + 7, UiPalette.CONTACT);
    }
    /** The plain casing face: outline, lit edge and four bolts. */
    static void plain(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 12, y + 12, UiPalette.SEAM);
        g.fill(x + 1, y + 1, x + 11, y + 11, UiPalette.SURFACE);
        g.fill(x + 1, y + 1, x + 11, y + 2, UiPalette.HIGHLIGHT);
        g.fill(x + 1, y + 1, x + 2, y + 11, UiPalette.HIGHLIGHT);
        for (int bolt = 0; bolt < 4; bolt++) {
            int bx = x + (bolt % 2 == 0 ? 3 : 8), by = y + (bolt < 2 ? 3 : 8);
            g.fill(bx, by, bx + 1, by + 1, UiPalette.PANEL);
        }
    }
    /** The open working area of a bench: the casing with its niche cut in from above. */
    static void open(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 12, y + 12, UiPalette.SEAM);
        g.fill(x + 1, y + 1, x + 11, y + 11, UiPalette.SURFACE);
        g.fill(x + 3, y, x + 9, y + 8, UiPalette.RECESS);
        g.fill(x + 1, y + 1, x + 2, y + 11, UiPalette.HIGHLIGHT);
    }

    private final class Pill extends Button {
        private final Side side; private final Font font;
        private Look look;
        Pill(Side side, Font font, int x, int y, int width) {
            super(Button.builder(side.label(), button -> { }).bounds(x, y, width, PILL));
            this.side = side; this.font = font;
        }
        @Override protected boolean isValidClickButton(MouseButtonInfo info) { return info.button() == 0 || info.button() == 1; }
        @Override public void onPress(InputWithModifiers input) {
            boolean backward = input.hasShiftDown() || input instanceof MouseButtonEvent click && click.button() == 1;
            shown = side;
            host.step(side, !backward);
        }
        @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            if (look == null) return;
            // A side without any connection is part of the casing, not a control.
            if (look.closed()) ScreenChrome.inset(g, x, y, w, h);
            else ScreenChrome.raised(g, x, y, w, h, isHoveredOrFocused() || shown == side, active);
            boolean lit = !look.closed() && look.face() == Face.PLATE;
            if (look.icon() != null && lit) ScreenChrome.icon(g, look.icon(), x, y, look.ring());
            else switch (look.face()) {
                case PLATE -> plate(g, x + 2, y + 2, look.ring());
                case PLAIN -> plain(g, x + 2, y + 2);
                case OPEN -> open(g, x + 2, y + 2);
            }
            ScreenChrome.text(g, font, getMessage().getString(), x + 16, y + 4, w - 17, lit && active ? UiPalette.TEXT : UiPalette.MUTED);
        }
    }
}
