package dev.latrox.rematter.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import static dev.latrox.rematter.client.UiPalette.*;

/** Pixel-aligned, font-independent controls shared by ReMatter screens and recipe panels. */
public final class ScreenChrome {
    public static final int ICON_SIZE = 12;
    private static final int LIGHT = 0xff718b90, SHADE = 0xff0a1115;

    /** Original compact glyphs; every symbol fits the same twelve-pixel square. */
    public enum Icon {
        PLAY("..#......./..##....../..###...../..####..../..#####.../..######../..#####.../..####..../..###...../..##....../..#......."),
        PAUSE(".###..###./.###..###./.###..###./.###..###./.###..###./.###..###./.###..###./.###..###./.###..###./.###..###."),
        POWER(".....###../....###.../...###..../..###...../.#######../....####../....###.../...###..../..###...../..##......"),
        INPUT("....##..../....##..../....##..../..######../...####.../....##..../........../##......##/##......##/##########"),
        OUTPUT("....##..../...####.../..######../....##..../....##..../....##..../........../##......##/##......##/##########"),
        AUTOMATION(".#######../##.....##./........#./......###./.......#../..#......./.###....../.#......../.##.....##/..#######."),
        NETWORK("....##..../....##..../..######../..#..#..#./..#..#..#./........../###.###.##/###.###.##/###.###.##"),
        SPEED(".##...##../..##...##./...##...##/....##...#/...##...##/..##...##./.##...##.."),
        EFFICIENCY("......###./....#####./...##..##./..##...#../.##..##.../.#.###..../.####...../.##......./##........"),
        RECIPE("########../##.....##./##..##.##./##.....##./##.#####../##.....##./##.###.##./##.....##./#########./.########."),
        STORAGE("##########/##......##/##.####.##/##......##/##########/##......##/##.####.##/##......##/##########"),
        CONFIGURATION("....##..../.#..##..#./.########./..##..##../###....###/###....###/..##..##../.########./.#..##..#./....##...."),
        UPGRADES("....##..../...####.../..######../.##.##.##./....##..../........../....##..../...####.../..######../.##....##."),
        REPEAT("..######../.##....##./##......#./##....###./##.....#../.#.....##./###....##/.#......##/.##....##./..######.."),
        ADD("....##..../....##..../....##..../.########./.########./....##..../....##..../....##...."),
        PREVIOUS(".....##.../....##..../...##...../..##....../.##......./..##....../...##...../....##..../.....##..."),
        NEXT("...##...../....##..../.....##.../......##../.......##./......##../.....##.../....##..../...##....."),
        SEARCH("..####..../.##..##.../##....##../##....##../.##..##.../..#####.../......##../.......##./........##"),
        CHECK("........##/.......##./......##../.##..##.../..####..../...##....."),
        BACK("...##...../..##....../.##......./#########./.##.....##/..##....##/...##...##/.......##./....####.."),
        INFO("..######../.##....##./##..##..##/##......##/##..##..##/##..##..##/##..##..##/##......##/.##....##./..######.."),
        TIME("..######../.##....##./##..##..##/##..##..##/##..#####./##......##/##......##/.##....##./..######.."),
        CRANK("......###./......###./.......#../...#####../..##....../.##.###.../##.##.##../##.#...#../.##.###.../..#####...");

        private final String[] rows;
        Icon(String pixels) { rows = pixels.split("/"); }
    }

    private ScreenChrome() {}

    public static String fit(Font font, String text, int width) {
        if (width <= 0) return "";
        if (font.width(text) <= width) return text;
        int suffix = font.width("…");
        return width < suffix ? "" : font.plainSubstrByWidth(text, width - suffix).stripTrailing() + "…";
    }

    /** Wrap within a fixed two-line area, keeping the ellipsis for the final line only. */
    public static void twoLines(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int width, int color) {
        if (font.width(text) <= width) {
            graphics.text(font, text, x, y, color, false);
            return;
        }
        String first = font.plainSubstrByWidth(text, width);
        int space = first.lastIndexOf(' ');
        if (space > 0) first = first.substring(0, space);
        graphics.text(font, first, x, y, color, false);
        graphics.text(font, fit(font, text.substring(first.length()).stripLeading(), width), x, y + 10, color, false);
    }

    public static void icon(GuiGraphicsExtractor g, Icon icon, int x, int y, int color) {
        int top = y + (ICON_SIZE - icon.rows.length) / 2;
        for (int row = 0; row < icon.rows.length; row++) {
            String pixels = icon.rows[row]; int left = x + (ICON_SIZE - pixels.length()) / 2;
            for (int column = 0; column < pixels.length();) {
                if (pixels.charAt(column) != '#') { column++; continue; }
                int start = column;
                while (column < pixels.length() && pixels.charAt(column) == '#') column++;
                g.fill(left + start, top + row, left + column, top + row + 1, color);
            }
        }
    }

    public static void panel(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        g.fill(x,y,x+width,y+height,SHADE);
        g.fill(x+1,y+1,x+width-1,y+height-1,BACKGROUND);
        g.fill(x+1,y+1,x+width-1,y+2,LIGHT);
        g.fill(x+1,y+2,x+2,y+height-2,BORDER);
        g.fill(x+2,y+2,x+width-2,y+3,HIGHLIGHT);
        g.fill(x+width-2,y+2,x+width-1,y+height-1,RECESS);
        g.fill(x+2,y+height-2,x+width-1,y+height-1,RECESS);
    }

    public static void raised(GuiGraphicsExtractor g, int x, int y, int width, int height, boolean highlighted, boolean enabled) {
        int edge = highlighted && enabled ? ACCENT : BORDER;
        int face = enabled ? highlighted ? SURFACE_ACTIVE : SURFACE : PANEL;
        g.fill(x,y,x+width,y+height,SHADE);
        g.fill(x,y,x+width-1,y+height-1,edge);
        g.fill(x+1,y+1,x+width-2,y+height-2,face);
        g.fill(x+1,y+1,x+width-2,y+2,enabled ? highlighted ? ACCENT : LIGHT : HIGHLIGHT);
        g.fill(x+1,y+2,x+2,y+height-2,enabled ? HIGHLIGHT : PANEL);
        g.fill(x+2,y+height-3,x+width-2,y+height-2,RECESS);
    }

    public static void inset(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        g.fill(x,y,x+width,y+height,HIGHLIGHT);
        g.fill(x,y,x+width-1,y+height-1,SHADE);
        g.fill(x+1,y+1,x+width-1,y+height-1,RECESS);
    }
}
