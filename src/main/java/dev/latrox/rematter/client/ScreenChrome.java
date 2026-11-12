package dev.latrox.rematter.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import static dev.latrox.rematter.client.UiPalette.*;

/** Pixel-aligned, font-independent controls shared by ReMatter screens and recipe panels. */
public final class ScreenChrome {
    public static final int ICON_SIZE = 12;
    private static final Identifier PANEL_SKIN = skin("panel"), BUTTON_SKIN = skin("button"),
            BUTTON_HOVER = skin("button_hover"), BUTTON_DISABLED = skin("button_disabled"),
            INSET_SKIN = skin("inset"), SLOT_SKIN = skin("slot");
    private static Identifier skin(String name) { return Identifier.fromNamespaceAndPath("rematter", name); }

    /** Original compact glyphs; every symbol fits the same twelve-pixel square. */
    public enum Icon {
        SEARCH("..#####.../.##...##../##.....##./##.....##./.##...##../..#####.../......##../.......##./........##"),
        CLEAR("##......##/.##....##./..##..##../...####.../....##..../...####.../..##..##../.##....##./##......##"),
        WARNING("....##..../...####.../...####.../..##..##../..##..##../.###..###./.########./###....###/##########"),
        HELP("..######../.##....##./......##../.....##.../....##..../....##..../........../....##..../....##...."),
        LOCK("..######../.##....##./.##....##./##########/##......##/##..##..##/##..##..##/##......##/##########"),
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
        CHECK("........##/.......##./......##../.##..##.../..####..../...##....."),
        BACK("...##...../..##....../.##......./#########./.##.....##/..##....##/...##...##/.......##./....####.."),
        INFO("..######../.##....##./##..##..##/##......##/##..##..##/##..##..##/##..##..##/##......##/.##....##./..######.."),
        TIME("..######../.##....##./##..##..##/##..##..##/##..#####./##......##/##......##/.##....##./..######.."),
        CRANK("......###./......###./.......#../...#####../..##....../.##.###.../##.##.##../##.#...#../.##.###.../..#####..."),
        STAR("....##..../....##..../...####.../##########/.########./..######../..##..##../.##....##./##......##"),
        SOLID("##########/##########/##......##/##......##/##......##/##......##/##########/##########"),
        LIQUID("....##..../....##..../...####.../..######../.########./##########/##########/.########./..######.."),
        GAS("..##....##/.#..#..#../#....##.../........../..##....##/.#..#..#../#....##...");

        private final int[][] runs;
        Icon(String pixels) {
            String[] rows=pixels.split("/");
            var spans=new java.util.ArrayList<int[]>();
            int top=(ICON_SIZE-rows.length)/2;
            for(int row=0;row<rows.length;row++) {
                int left=(ICON_SIZE-rows[row].length())/2;
                for(int column=0;column<rows[row].length();) {
                    if(rows[row].charAt(column)!='#'){column++;continue;}
                    int start=column;
                    while(column<rows[row].length()&&rows[row].charAt(column)=='#')column++;
                    spans.add(new int[]{left+start,top+row,left+column,top+row+1});
                }
            }
            runs=spans.toArray(int[][]::new);
        }
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
        for(int[] run:icon.runs)g.fill(x+run[0],y+run[1],x+run[2],y+run[3],color);
    }

    public static void panel(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL_SKIN, x, y, width, height);
    }

    public static void raised(GuiGraphicsExtractor g, int x, int y, int width, int height, boolean highlighted, boolean enabled) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, !enabled ? BUTTON_DISABLED : highlighted ? BUTTON_HOVER : BUTTON_SKIN,
                x, y, width, height);
    }

    public static void inset(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, INSET_SKIN, x, y, width, height);
    }

    public static void slot(GuiGraphicsExtractor g, int x, int y) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SKIN, x, y, 18, 18);
    }
}
