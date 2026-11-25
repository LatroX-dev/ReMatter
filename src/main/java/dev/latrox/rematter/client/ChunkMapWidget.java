package dev.latrox.rematter.client;

import static dev.latrox.rematter.client.UiPalette.*;

import dev.latrox.rematter.content.ChunkMapSnapshot;
import dev.latrox.rematter.content.MachineKind;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Reusable 5x5 top-down chunk coverage map widget (terrain relief, chunk grid, active/planned anchor highlights,
 * other owned anchors, player marker, and full chunk/block coordinate hover tooltips).
 */
public final class ChunkMapWidget {
    private static final int GRID = ChunkMapSnapshot.GRID;
    private static final int SUB = ChunkMapSnapshot.SUB;
    private static final int HALF = ChunkMapSnapshot.HALF;

    private ChunkMapWidget() {}

    public record ChunkCell(int gx, int gz, int bitIndex, boolean center) {}

    public static int pixelSize(int cellPx) {
        return GRID * SUB * cellPx + 4;
    }

    public static @Nullable ChunkCell cellAt(int localX, int localY, int mapX, int mapY, int cellPx) {
        int chunkPx = SUB * cellPx;
        int ox = mapX + 2;
        int oy = mapY + 2;
        int inner = GRID * chunkPx;
        if (localX < ox || localX >= ox + inner || localY < oy || localY >= oy + inner) return null;
        int gx = (localX - ox) / chunkPx;
        int gz = (localY - oy) / chunkPx;
        return new ChunkCell(gx, gz, gz * GRID + gx, gx == HALF && gz == HALF);
    }

    public static void draw(GuiGraphicsExtractor g, int x, int y, int cellPx,
                            @Nullable ChunkMapSnapshot map, boolean anchorActive, int accent) {
        draw(g, x, y, cellPx, map, anchorActive, accent, -1);
    }

    public static void draw(GuiGraphicsExtractor g, int x, int y, int cellPx,
                            @Nullable ChunkMapSnapshot map, boolean anchorActive, int accent, int hoverBit) {
        int chunkPx = SUB * cellPx;
        int inner = GRID * chunkPx;
        ScreenChrome.inset(g, x, y, inner + 4, inner + 4);
        int ox = x + 2;
        int oy = y + 2;
        g.fill(ox, oy, ox + inner, oy + inner, RECESS);

        if (map == null) {
            // Subtle dark waiting checkerboard while initial map packet arrives.
            for (int gz = 0; gz < GRID; gz++) {
                for (int gx = 0; gx < GRID; gx++) {
                    int cx = ox + gx * chunkPx;
                    int cy = oy + gz * chunkPx;
                    if (((gx + gz) & 1) == 0) g.fill(cx, cy, cx + chunkPx, cy + chunkPx, PANEL);
                }
            }
            return;
        }

        int activeFill = withAlpha(accent, 76);
        int plannedFill = withAlpha(accent, 38);
        int otherFill = withAlpha(WARNING, 54);
        int gridColor = withAlpha(BORDER, 136);

        // 1. Terrain cells (4x4 samples per chunk)
        for (int gz = 0; gz < GRID; gz++) {
            for (int gx = 0; gx < GRID; gx++) {
                int cx = ox + gx * chunkPx;
                int cy = oy + gz * chunkPx;
                byte flag = map.flagAt(gx, gz);
                boolean loaded = (flag & ChunkMapSnapshot.FLAG_LOADED) != 0;
                if (loaded) {
                    for (int sz = 0; sz < SUB; sz++) {
                        for (int sx = 0; sx < SUB; sx++) {
                            int col = map.colorAt(gx, gz, sx, sz);
                            int px = cx + sx * cellPx;
                            int py = cy + sz * cellPx;
                            g.fill(px, py, px + cellPx, py + cellPx, col == 0 ? RECESS : col);
                        }
                    }
                } else {
                    g.fill(cx, cy, cx + chunkPx, cy + chunkPx, ((gx + gz) & 1) == 0 ? RECESS : PANEL);
                }
            }
        }

        // 2. Chunk grid lines
        for (int i = 1; i < GRID; i++) {
            g.fill(ox + i * chunkPx, oy, ox + i * chunkPx + 1, oy + inner, gridColor);
            g.fill(ox, oy + i * chunkPx, ox + inner, oy + i * chunkPx + 1, gridColor);
        }

        // 3. Coverage highlights (other anchors first, then THIS device's coverage on top)
        for (int gz = 0; gz < GRID; gz++) {
            for (int gx = 0; gx < GRID; gx++) {
                int cx = ox + gx * chunkPx;
                int cy = oy + gz * chunkPx;
                byte flag = map.flagAt(gx, gz);
                boolean isOther = (flag & ChunkMapSnapshot.FLAG_COVERED_OTHER) != 0;
                boolean isThis = (flag & ChunkMapSnapshot.FLAG_COVERED_THIS) != 0;
                if (isOther && !isThis) {
                    g.fill(cx + 1, cy + 1, cx + chunkPx - 1, cy + chunkPx - 1, otherFill);
                    outline(g, cx + 1, cy + 1, chunkPx - 2, chunkPx - 2, WARNING);
                }
                if (isThis) {
                    g.fill(cx + 1, cy + 1, cx + chunkPx - 1, cy + chunkPx - 1, anchorActive ? activeFill : plannedFill);
                    outline(g, cx, cy, chunkPx, chunkPx, anchorActive ? ACCENT_BRIGHT : accent);
                }
                if (hoverBit >= 0 && gz * GRID + gx == hoverBit) {
                    outline(g, cx, cy, chunkPx, chunkPx, WHITE);
                }
            }
        }

        // 4. Center device pin (always at center chunk (HALF, HALF))
        int centerPx = ox + HALF * chunkPx + chunkPx / 2;
        int centerPy = oy + HALF * chunkPx + chunkPx / 2;
        g.fill(centerPx - 2, centerPy - 2, centerPx + 2, centerPy + 2, RECESS);
        g.fill(centerPx - 1, centerPy - 1, centerPx + 1, centerPy + 1, WHITE);

        // 5. Player position marker if inside 5x5 chunk window
        if (map.playerSameDim()) {
            int pdx = map.playerChunkX() - map.centerChunkX();
            int pdz = map.playerChunkZ() - map.centerChunkZ();
            if (Math.abs(pdx) <= HALF && Math.abs(pdz) <= HALF) {
                int px = ox + (pdx + HALF) * chunkPx + chunkPx - 4;
                int py = oy + (pdz + HALF) * chunkPx + 2;
                g.fill(px - 1, py - 1, px + 3, py + 3, RECESS);
                g.fill(px, py, px + 2, py + 2, machineAccent(MachineKind.SYNTHESIZER));
            }
        }
    }

    private static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static @Nullable List<Component> tooltip(int localMouseX, int localMouseY, int mapX, int mapY, int cellPx,
                                                    @Nullable ChunkMapSnapshot map, boolean anchorActive) {
        return tooltip(localMouseX, localMouseY, mapX, mapY, cellPx, map, anchorActive, false);
    }

    public static @Nullable List<Component> tooltip(int localMouseX, int localMouseY, int mapX, int mapY, int cellPx,
                                                    @Nullable ChunkMapSnapshot map, boolean anchorActive, boolean interactive) {
        if (map == null) return null;
        var cell = cellAt(localMouseX, localMouseY, mapX, mapY, cellPx);
        if (cell == null) return null;
        int gx = cell.gx();
        int gz = cell.gz();
        int cx = map.centerChunkX() + (gx - HALF);
        int cz = map.centerChunkZ() + (gz - HALF);
        int bx0 = cx << 4, bx1 = bx0 + 15;
        int bz0 = cz << 4, bz1 = bz0 + 15;
        byte flag = map.flagAt(gx, gz);
        boolean loaded = (flag & ChunkMapSnapshot.FLAG_LOADED) != 0;
        boolean isThis = (flag & ChunkMapSnapshot.FLAG_COVERED_THIS) != 0;
        boolean isOther = (flag & ChunkMapSnapshot.FLAG_COVERED_OTHER) != 0;

        var lines = new ArrayList<Component>(5);
        lines.add(Component.translatable("screen.rematter.chunk_map.chunk_coords", cx, cz).withStyle(ChatFormatting.WHITE));
        lines.add(Component.translatable("screen.rematter.chunk_map.block_range", bx0, bx1, bz0, bz1).withStyle(ChatFormatting.GRAY));
        if (isThis) {
            lines.add(Component.translatable(anchorActive ? "screen.rematter.chunk_map.covered_active" : "screen.rematter.chunk_map.covered_planned")
                    .withStyle(anchorActive ? ChatFormatting.AQUA : ChatFormatting.GOLD));
        } else if (isOther) {
            lines.add(Component.translatable("screen.rematter.chunk_map.covered_other").withStyle(ChatFormatting.GOLD));
        } else if (!loaded) {
            lines.add(Component.translatable("screen.rematter.chunk_map.unloaded").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            lines.add(Component.translatable("screen.rematter.chunk_map.not_anchored").withStyle(ChatFormatting.GRAY));
        }
        if (interactive) {
            lines.add(Component.translatable(cell.center()
                    ? "screen.rematter.spatial_anchor.chunk_center_locked"
                    : "screen.rematter.spatial_anchor.chunk_click_toggle")
                    .withStyle(cell.center() ? ChatFormatting.GRAY : ChatFormatting.AQUA));
        }
        return lines;
    }
}
