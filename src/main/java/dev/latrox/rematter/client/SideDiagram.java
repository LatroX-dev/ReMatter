package dev.latrox.rematter.client;

import dev.latrox.rematter.content.MachinePortMode.Side;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The six faces of a device, unfolded around its front: Top above Front, Bottom below it, Left and
 * Right beside it, Back at the far right. A face shows what the block shows: the port plate with the ring of its
 * role (and optionally the connected neighbor block), or the plain casing of a side without a connection.
 */
final class SideDiagram {
    static final int WIDTH = 179, HEIGHT = 52;
    /** FACE_NET is the spatial recommendation; the other two compact arrangements are one-line alternatives. */
    static final Layout LAYOUT = Layout.FACE_NET;
    private static boolean showNeighbors = true;

    enum Layout { FACE_NET, COMPACT_GRID, STRIP }
    record Bounds(int x, int y, int size) {}
    record Neighbor(ItemStack icon, Component name) {}

    /** What a side shows on the block. */
    enum Face { PLATE, PLAIN, OPEN }
    /** One side as its host sees it; {@code icon} can add a directional glyph inside the port contact. */
    record Look(Face face, int ring, @Nullable UiIcon icon, boolean closed, Component tooltip, @Nullable Neighbor neighbor) {
        Look(Face face, int ring, @Nullable UiIcon icon, boolean closed, Component tooltip) {
            this(face, ring, icon, closed, tooltip, null);
        }
    }
    interface Host {
        Look look(Side side);
        void step(Side side, boolean forward);
        boolean editable();
    }

    private final EnumMap<Side, Pill> pills = new EnumMap<>(Side.class);
    private final List<AbstractWidget> allWidgets = new ArrayList<>();
    private final NeighborToggle neighborToggle;
    private final Host host;
    private Side shown = Side.BACK;

    SideDiagram(Host host, Font font, int left, int top, Consumer<AbstractWidget> add) {
        this.host = host;
        for (Side side : Side.values()) {
            var place = place(LAYOUT, side);
            var pill = new Pill(side, left + place.x(), top + place.y(), place.size());
            pills.put(side, pill);
            allWidgets.add(pill);
            add.accept(pill);
        }
        neighborToggle = new NeighborToggle(left + WIDTH - 18, top);
        allWidgets.add(neighborToggle);
        add.accept(neighborToggle);
    }

    static @Nullable Neighbor inspectNeighbor(@Nullable Level level, @Nullable BlockPos pos, @Nullable Direction front, Side side) {
        if (level == null || pos == null || front == null) return null;
        BlockPos adjPos = pos.relative(side.world(front));
        if (!level.hasChunkAt(adjPos)) return null;
        BlockState adjState = level.getBlockState(adjPos);
        if (adjState.isAir()) return null;
        Item item = adjState.getBlock().asItem();
        if (item == Items.AIR && !adjState.getFluidState().isEmpty()) {
            item = adjState.getFluidState().getType().getBucket();
        }
        ItemStack icon = item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        return new Neighbor(icon, adjState.getBlock().getName());
    }

    /** Every arrangement fits the existing settings area, including the smallest supported screens. */
    static Bounds place(Layout layout, Side side) {
        if (layout == Layout.FACE_NET) {
            int size = 17, left = (WIDTH - 4 * size) / 2;
            return switch (side) {
                case TOP -> new Bounds(left + size, 0, size);
                case LEFT -> new Bounds(left, size, size);
                case FRONT -> new Bounds(left + size, size, size);
                case RIGHT -> new Bounds(left + 2 * size, size, size);
                case BACK -> new Bounds(left + 3 * size, size, size);
                case BOTTOM -> new Bounds(left + size, 2 * size, size);
            };
        }
        if (layout == Layout.COMPACT_GRID) {
            int index = switch (side) { case LEFT -> 0; case FRONT -> 1; case RIGHT -> 2;
                case TOP -> 3; case BACK -> 4; case BOTTOM -> 5; };
            int size = 22, gap = 3;
            return new Bounds((WIDTH - 3 * size - 2 * gap) / 2 + index % 3 * (size + gap),
                    (HEIGHT - 2 * size - gap) / 2 + index / 3 * (size + gap), size);
        }
        int index = switch (side) { case LEFT -> 0; case FRONT -> 1; case RIGHT -> 2;
            case BACK -> 3; case TOP -> 4; case BOTTOM -> 5; };
        int size = 24, gap = 3;
        return new Bounds((WIDTH - 6 * size - 5 * gap) / 2 + index * (size + gap), (HEIGHT - size) / 2, size);
    }
    Iterable<? extends AbstractWidget> widgets() { return allWidgets; }
    /** The side under the pointer, or the one last changed. */
    Side shown() {
        for (var entry : pills.entrySet()) if (entry.getValue().visible && entry.getValue().pointerHovered()) return entry.getKey();
        for (var entry : pills.entrySet()) if (entry.getValue().visible && entry.getValue().isFocused()) return entry.getKey();
        return shown;
    }
    void show(Side side) { shown = side; }
    void update(boolean visible) {
        neighborToggle.visible = visible;
        neighborToggle.active = visible;
        neighborToggle.refreshTooltip();
        for (var entry : pills.entrySet()) {
            var pill = entry.getValue(); var look = host.look(entry.getKey());
            pill.look = look; pill.visible = visible;
            pill.active = visible && !look.closed() && host.editable();
            Component tip = look.neighbor() == null
                    ? look.tooltip()
                    : look.tooltip().copy().append("\n").append(Component.translatable("screen.rematter.side_neighbor", look.neighbor().name()));
            pill.setMessage(tip);
            pill.setTooltip(Tooltip.create(tip));
        }
    }

    /** The port plate of the block at twelve pixels: seam, ring in the role colour, well and contact. */
    static void plate(GuiGraphicsExtractor g, int x, int y, int ring) {
        plate(g, x, y, ring, null);
    }

    static void plate(GuiGraphicsExtractor g, int x, int y, int ring, @Nullable UiIcon icon) {
        g.fill(x, y, x + 12, y + 12, UiPalette.SEAM);
        for (int py = 1; py < 11; py++) for (int px = 1; px < 11; px++) {
            if (px >= 3 && px < 9 && py >= 3 && py < 9) continue;
            int color = icon == UiIcon.EXCHANGE
                    ? (px + py < 12 ? UiPalette.PORT_ENERGY_INPUT : UiPalette.PORT_ENERGY_OUTPUT)
                    : ring;
            if (px == 1 || py == 1) color = UiPalette.tint(color, .3F);
            g.fill(x + px, y + py, x + px + 1, y + py + 1, color);
        }
        g.fill(x + 3, y + 3, x + 9, y + 9, UiPalette.SEAM);
        g.fill(x + 4, y + 4, x + 8, y + 8, UiPalette.RECESS);
        g.fill(x + 5, y + 5, x + 7, y + 7, UiPalette.CONTACT);
    }

    private static void neighborPlate(GuiGraphicsExtractor g, int x, int y, int w, int h, Look look, ItemStack icon) {
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, UiPalette.SEAM);
        if (look.face() == Face.PLATE) {
            if (look.icon() == UiIcon.EXCHANGE) {
                g.fill(x + 1, y + 1, x + w - 1, y + h / 2, UiPalette.PORT_ENERGY_INPUT);
                g.fill(x + 1, y + h / 2, x + w - 1, y + h - 1, UiPalette.PORT_ENERGY_OUTPUT);
            } else {
                g.fill(x + 1, y + 1, x + w - 1, y + h - 1, look.ring());
            }
        } else {
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, UiPalette.BORDER);
        }
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, UiPalette.RECESS);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x + (w - 11F) / 2F, y + (h - 11F) / 2F);
        pose.scale(11F / 16F, 11F / 16F);
        g.fakeItem(icon, 0, 0);
        pose.popMatrix();
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

    /** A neutral front display anchors the unfolded net without printing shortened direction names. */
    private static void front(GuiGraphicsExtractor g, int x, int y) {
        plain(g, x, y);
        g.fill(x + 3, y + 3, x + 9, y + 7, UiPalette.RECESS);
        g.fill(x + 4, y + 4, x + 8, y + 5, UiPalette.MUTED);
        g.fill(x + 3, y + 9, x + 6, y + 10, UiPalette.BORDER);
        g.fill(x + 8, y + 9, x + 9, y + 10, UiPalette.MUTED);
    }

    /** Cube arrows point into the contact for input, away for output, and both ways for bidirectional I/O. */
    private static void flow(GuiGraphicsExtractor g, UiIcon icon, int x, int y) {
        if (icon == UiIcon.SLOT_INPUT) {
            g.fill(x + 5, y + 3, x + 7, y + 6, UiPalette.TEXT);
            g.fill(x + 4, y + 5, x + 8, y + 6, UiPalette.TEXT);
            g.fill(x + 5, y + 6, x + 7, y + 7, UiPalette.TEXT);
        } else if (icon == UiIcon.SLOT_OUTPUT) {
            g.fill(x + 5, y + 5, x + 7, y + 8, UiPalette.TEXT);
            g.fill(x + 4, y + 4, x + 8, y + 5, UiPalette.TEXT);
            g.fill(x + 5, y + 3, x + 7, y + 4, UiPalette.TEXT);
        } else if (icon == UiIcon.EXCHANGE) {
            g.fill(x + 5, y + 2, x + 7, y + 4, UiPalette.TEXT);
            g.fill(x + 4, y + 3, x + 8, y + 4, UiPalette.TEXT);
            g.fill(x + 4, y + 8, x + 8, y + 9, UiPalette.TEXT);
            g.fill(x + 5, y + 9, x + 7, y + 10, UiPalette.TEXT);
        }
    }

    private final class NeighborToggle extends Button {
        NeighborToggle(int x, int y) {
            super(Button.builder(Component.translatable("screen.rematter.side_neighbors_on"), b -> {
                showNeighbors = !showNeighbors;
            }).bounds(x, y, 18, 18));
            refreshTooltip();
        }
        void refreshTooltip() {
            Component state = Component.translatable(showNeighbors ? "screen.rematter.side_neighbors_on" : "screen.rematter.side_neighbors_off");
            setMessage(state);
            setTooltip(Tooltip.create(state.copy().append("\n").append(Component.translatable("screen.rematter.side_neighbors_hint"))));
        }
        @Override public void onPress(InputWithModifiers input) {
            super.onPress(input);
            refreshTooltip();
        }
        @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            ScreenChrome.raised(g, x, y, w, h, isHoveredOrFocused() || showNeighbors, active);
            ScreenChrome.icon(g, UiIcon.PREVIEW, x + (w - 16) / 2, y + (h - 16) / 2, showNeighbors ? UiPalette.ACCENT_BRIGHT : UiPalette.MUTED);
        }
    }

    private final class Pill extends Button {
        private final Side side;
        private Look look;
        Pill(Side side, int x, int y, int size) {
            super(Button.builder(side.label(), button -> { }).bounds(x, y, size, size));
            this.side = side;
        }
        boolean pointerHovered() { return isHovered; }
        @Override protected boolean isValidClickButton(MouseButtonInfo info) { return info.button() == 0 || info.button() == 1; }
        @Override public void onPress(InputWithModifiers input) {
            if (look == null || look.closed() || !host.editable()) return;
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
            if (showNeighbors && look.neighbor() != null && !look.neighbor().icon().isEmpty()) {
                neighborPlate(g, x, y, w, h, look, look.neighbor().icon());
                if (side == Side.FRONT && !look.closed()) {
                    int gx = x + (w - 12) / 2, gy = y + (h - 12) / 2;
                    g.fill(gx + 4, gy + 11, gx + 8, gy + 12, UiPalette.CONTACT);
                }
                return;
            }
            int gx = x + (w - 12) / 2, gy = y + (h - 12) / 2;
            switch (look.face()) {
                case PLATE -> {
                    plate(g, gx, gy, look.ring(), look.icon());
                    if (look.icon() != null) flow(g, look.icon(), gx, gy);
                    if (side == Side.FRONT && !look.closed()) g.fill(gx + 4, gy + 11, gx + 8, gy + 12, UiPalette.CONTACT);
                }
                case PLAIN -> {
                    if (look.closed() && side == Side.FRONT) front(g, gx, gy);
                    else {
                        plain(g, gx, gy);
                        if (side == Side.FRONT && !look.closed()) g.fill(gx + 4, gy + 10, gx + 8, gy + 11, UiPalette.CONTACT);
                    }
                }
                case OPEN -> open(g, gx, gy);
            }
        }
    }
}
