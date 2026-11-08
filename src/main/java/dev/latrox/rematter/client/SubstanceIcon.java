package dev.latrox.rematter.client;

import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceDefinition;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.chemistry.SubstanceKind;
import dev.latrox.rematter.network.ClientChemistry;
import java.awt.Color;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A 16 px material badge with a stable pattern, catalogue notation and phase marker. */
public final class SubstanceIcon {
    private static final Set<String> FICTIONAL_CONSTITUENTS = Set.of(
            "arcane_essence", "ender_essence", "nether_essence", "netherite_scrap", "quantum_essence", "stellar_essence", "vital_essence");

    private SubstanceIcon() {}

    public static void draw(GuiGraphicsExtractor graphics, int x, int y, SubstanceKey key) {
        var definition = ClientChemistry.current().definition(key.id()).orElse(null);
        var kind = definition == null ? SubstanceKind.MIXTURE : definition.kind();
        String symbol = definition == null ? "?" : SubstanceLabels.iconLabel(definition);
        boolean fictional = isFictional(key.id(), definition);
        long fingerprint = fingerprint(key.id());
        int color = accent(fingerprint, key.id(), fictional);
        int shadow = blend(color, UiPalette.RECESS, 1, 3);
        int light = blend(color, UiPalette.TEXT, 2, 1);

        graphics.fill(x + 1, y, x + 15, y + 16, UiPalette.RECESS);
        graphics.fill(x, y + 1, x + 16, y + 15, UiPalette.RECESS);
        switch (kind) {
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
        int facets = (int) (fingerprint >>> 32) & 0xff;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                int facet = index < 4 ? index : index - 1;
                int shade = index == 4 ? light : ((facets & (1 << facet)) == 0 ? shadow : color);
                int left = x + 4 + col * 3;
                int top = y + 3 + row * 3;
                graphics.fill(left, top, left + 2, top + 2, shade);
            }
        }
        if (fictional) {
            graphics.fill(x + 12, y + 1, x + 14, y + 2, light);
            graphics.fill(x + 13, y + 2, x + 14, y + 3, light);
        }
        drawSymbol(graphics, x, y, symbol);
        drawPhase(graphics, x, y + 14, key.phase());
    }

    private static void drawSymbol(GuiGraphicsExtractor graphics, int x, int y, String symbol) {
        Font font = Minecraft.getInstance().font;
        int width = font.width(symbol);
        int split = width > 22 ? formulaBreak(font, symbol, width) : 0;
        String first = split == 0 ? symbol : symbol.substring(0, split);
        String second = split == 0 ? "" : symbol.substring(split);
        int firstWidth = font.width(first);
        int secondWidth = font.width(second);
        int height = split == 0 ? 8 : 16;
        float scale = Math.min(1F, Math.min(14F / Math.max(1, Math.max(firstWidth, secondWidth)), 12F / height));

        int top = (int) ((14 - height * scale) / 2);
        graphics.fill(x + 1, y + top, x + 15, y + top + (int) Math.ceil(height * scale), 0xc010191d);
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(x + 8F, y + top).scale(scale);
            graphics.text(font, first, -firstWidth / 2, 0, UiPalette.TEXT, true);
            if (split != 0) graphics.text(font, second, -secondWidth / 2, 8, UiPalette.TEXT, true);
        } finally {
            pose.popMatrix();
        }
    }

    private static int formulaBreak(Font font, String formula, int width) {
        int split = 0;
        int widest = width;
        for (int i = 1; i < formula.length(); i++) {
            char next = formula.charAt(i);
            if (next == '(' || (Character.isUpperCase(next) && formula.charAt(i - 1) != '(')) {
                int candidate = Math.max(font.width(formula.substring(0, i)), font.width(formula.substring(i)));
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
                case "iron": return 0xffadbec8;
                case "copper": return 0xffd58c5c;
                case "gold": return 0xffe2be55;
                case "water": return 0xff62b5e4;
                case "mineral_mix": return 0xffa8937c;
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
