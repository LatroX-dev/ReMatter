package dev.latrox.rematter.client;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceDefinition;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.chemistry.SubstanceKind;
import dev.latrox.rematter.network.ClientChemistry;
import java.awt.Color;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A 16 px material badge with a stable pattern, catalogue notation and phase marker. */
public final class SubstanceIcon {
    private static final Set<String> FICTIONAL_CONSTITUENTS = Set.of(
            "arcanium", "enderion", "cinderium", "netherium", "quantium", "astrion", "vitalium");

    private static final int MAX_STYLES = 32_768;
    private static final Map<SubstanceKey, Style> STYLES = new ConcurrentHashMap<>();

    static { ClientChemistry.addListener(catalogue -> STYLES.clear()); }

    private SubstanceIcon() {}

    /** Everything a badge needs from the catalogue. It is computed once per key until the catalogue changes. */
    record Style(boolean known, SubstanceKind kind, String first, String second, int facets, boolean fictional,
                 int color, int shadow, int light) {}

    public static void draw(GuiGraphicsExtractor graphics, int x, int y, SubstanceKey key) {
        Font font = Minecraft.getInstance().font;
        Style style = style(key, font::width);
        int shadow = style.shadow(), light = style.light();

        graphics.fill(x + 1, y, x + 15, y + 16, UiPalette.RECESS);
        graphics.fill(x, y + 1, x + 16, y + 15, UiPalette.RECESS);
        if (!style.known()) {
            // An undefined substance keeps a fixed warning badge instead of a signature it no longer has.
            graphics.fill(x + 1, y, x + 15, y + 1, style.color());
            graphics.fill(x + 1, y + 15, x + 15, y + 16, style.color());
            graphics.fill(x, y + 1, x + 1, y + 15, style.color());
            graphics.fill(x + 15, y + 1, x + 16, y + 15, style.color());
        }
        switch (style.kind()) {
            case ELEMENT -> {
                graphics.fill(x + 6, y + 1, x + 10, y + 13, shadow);
                graphics.fill(x + 3, y + 4, x + 13, y + 10, shadow);
            }
            case ALLOTROPE -> {
                graphics.fill(x + 3, y + 3, x + 11, y + 8, shadow);
                graphics.fill(x + 5, y + 7, x + 13, y + 12, shadow);
            }
            case COMPOUND -> {
                graphics.fill(x + 2, y + 3, x + 8, y + 11, shadow);
                graphics.fill(x + 8, y + 4, x + 14, y + 12, shadow);
            }
            case MIXTURE -> {
                graphics.fill(x + 2, y + 6, x + 14, y + 11, shadow);
                graphics.fill(x + 5, y + 2, x + 11, y + 12, shadow);
            }
        }

        // The centre is the bright core. The other eight facets encode a stable visual signature.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                int facet = index < 4 ? index : index - 1;
                int shade = index == 4 ? light : ((style.facets() & (1 << facet)) == 0 ? shadow : style.color());
                int left = x + 4 + col * 3;
                int top = y + 3 + row * 3;
                graphics.fill(left, top, left + 2, top + 2, shade);
            }
        }
        if (style.fictional()) {
            graphics.fill(x + 12, y + 1, x + 14, y + 2, light);
            graphics.fill(x + 13, y + 2, x + 14, y + 3, light);
        }
        drawSymbol(graphics, font, x, y, style);
        drawPhase(graphics, x, y + 14, key.phase());
    }

    static Style style(SubstanceKey key, ToIntFunction<String> width) {
        Style style = STYLES.get(key);
        if (style != null) return style;
        if (STYLES.size() >= MAX_STYLES) STYLES.clear();
        style = compute(ClientChemistry.current(), key, width);
        STYLES.put(key, style);
        return style;
    }

    static Style compute(ChemistryRegistry registry, SubstanceKey key, ToIntFunction<String> width) {
        var definition = registry.definition(key.id()).orElse(null);
        if (definition == null || !registry.isKnown(key)) {
            int color = UiPalette.WARNING;
            return new Style(false, SubstanceKind.MIXTURE, "?", "", 0, false, color,
                    blend(color, UiPalette.RECESS, 1, 3), blend(color, UiPalette.TEXT, 2, 1));
        }
        String symbol = SubstanceLabels.iconLabel(definition);
        int symbolWidth = width.applyAsInt(symbol);
        int split = symbolWidth > 22 ? formulaBreak(width, symbol, symbolWidth) : 0;
        boolean fictional = isFictional(key.id(), definition);
        long fingerprint = fingerprint(key.id());
        int color = accent(fingerprint, key.id(), fictional);
        return new Style(true, definition.kind(), split == 0 ? symbol : symbol.substring(0, split), split == 0 ? "" : symbol.substring(split),
                (int) (fingerprint >>> 32) & 0xff, fictional, color, blend(color, UiPalette.RECESS, 1, 3), blend(color, UiPalette.TEXT, 2, 1));
    }

    private static void drawSymbol(GuiGraphicsExtractor graphics, Font font, int x, int y, Style style) {
        // Formula text is never shortened. Two rows break at an element boundary; the complete
        // notation is scaled as one group so even a long compound stays inside its 16 px badge.
        int widest=Math.max(font.width(style.first()),font.width(style.second()));
        boolean twoLines=!style.second().isEmpty();
        float scale=Math.min(twoLines?.625F:1F,14F/Math.max(1,widest));
        int color=style.known()?UiPalette.TEXT:UiPalette.WARNING;
        graphics.fill(x+1,y+2,x+15,y+13,UiPalette.BADGE_BACKGROUND);
        var pose=graphics.pose();
        pose.pushMatrix();
        pose.translate(x+8F,y+(twoLines?2F:4F));
        pose.scale(scale,scale);
        graphics.text(font,style.first(),-font.width(style.first())/2,0,color,false);
        if(twoLines)graphics.text(font,style.second(),-font.width(style.second())/2,9,color,false);
        pose.popMatrix();
    }

    private static int formulaBreak(ToIntFunction<String> width, String formula, int fullWidth) {
        int split = 0;
        int widest = fullWidth;
        for (int i = 1; i < formula.length(); i++) {
            char next = formula.charAt(i);
            if (next == '(' || (Character.isUpperCase(next) && formula.charAt(i - 1) != '(')) {
                int candidate = Math.max(width.applyAsInt(formula.substring(0, i)), width.applyAsInt(formula.substring(i)));
                if (candidate < widest) {
                    split = i;
                    widest = candidate;
                }
            }
        }
        return split;
    }

    private static void drawPhase(GuiGraphicsExtractor graphics, int x, int y, Phase phase) {
        switch (phase) {
            case SOLID -> graphics.fill(x + 4, y, x + 12, y + 1, UiPalette.ACCENT_BRIGHT);
            case LIQUID -> {
                graphics.fill(x + 4, y, x + 7, y + 1, UiPalette.LIQUID);
                graphics.fill(x + 9, y, x + 12, y + 1, UiPalette.LIQUID);
            }
            case GAS -> {
                for (int offset = 4; offset <= 10; offset += 3) {
                    graphics.fill(x + offset, y, x + offset + 2, y + 1, UiPalette.GAS);
                }
            }
        }
    }

    private static boolean isFictional(SubstanceId id, SubstanceDefinition definition) {
        return isFictionalConstituent(id)
                || (definition != null && definition.composition().elements().keySet().stream()
                        .anyMatch(SubstanceIcon::isFictionalConstituent));
    }

    private static boolean isFictionalConstituent(SubstanceId id) {
        return id.namespace().equals("rematter") && FICTIONAL_CONSTITUENTS.contains(id.path());
    }

    private static int accent(long fingerprint, SubstanceId id, boolean fictional) {
        if (id.namespace().equals("rematter")) {
            switch (id.path()) {
                case "iron": return UiPalette.SUBSTANCE_IRON;
                case "copper": return UiPalette.SUBSTANCE_COPPER;
                case "gold": return UiPalette.SUBSTANCE_GOLD;
                case "water": return UiPalette.SUBSTANCE_WATER;
                case "mineral_mix": return UiPalette.SUBSTANCE_MINERAL;
                default: break;
            }
        }
        float hue = (fingerprint & 0xffff) / 65536.0f;
        return Color.HSBtoRGB(hue, fictional ? 0.72f : 0.38f, fictional ? 0.97f : 0.78f);
    }

    private static int blend(int first, int second, int firstWeight, int secondWeight) {
        int total = firstWeight + secondWeight;
        int red = (((first >>> 16) & 255) * firstWeight + ((second >>> 16) & 255) * secondWeight) / total;
        int green = (((first >>> 8) & 255) * firstWeight + ((second >>> 8) & 255) * secondWeight) / total;
        int blue = ((first & 255) * firstWeight + (second & 255) * secondWeight) / total;
        return 0xff000000 | (red << 16) | (green << 8) | blue;
    }

    private static long fingerprint(SubstanceId id) {
        // Resource IDs are ASCII. FNV-1a keeps the badge stable across clients and launches.
        long result = 0xcbf29ce484222325L;
        String value = id.toString();
        for (int i = 0; i < value.length(); i++) {
            result = (result ^ value.charAt(i)) * 0x100000001b3L;
        }
        return result;
    }
}
