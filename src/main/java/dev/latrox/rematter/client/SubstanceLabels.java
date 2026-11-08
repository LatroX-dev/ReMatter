package dev.latrox.rematter.client;

import dev.latrox.rematter.chemistry.SubstanceDefinition;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.network.ClientChemistry;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;

/** Display notation from the synchronized chemistry catalogue, with short badge labels for mixtures. */
public final class SubstanceLabels {
    private static final Pattern ID_WORD = Pattern.compile("[a-z0-9]+");
    private static final Map<String, String> BADGE_CODES = Map.of(
            "burnt_otherrock", "BOR", "burnt_otherstone", "BOS",
            "prosperity_essence", "PSE", "prosperity_gemstone", "PSG",
            "insanium_essence", "ISE", "insanium_gemstone", "ISG",
            "fluxite", "FLX");

    private SubstanceLabels() {}

    public static String iconLabel(SubstanceId id) {
        return ClientChemistry.current().definition(id).map(SubstanceLabels::iconLabel).orElse("");
    }

    public static String iconLabel(String id) {
        try { return iconLabel(SubstanceId.parse(id)); }
        catch (IllegalArgumentException unknown) { return ""; }
    }

    static String iconLabel(SubstanceDefinition definition) {
        String symbol = definition.symbol().strip();
        if (!symbol.isEmpty()) return symbol;

        // A label for a material without a formula is derived from its stable ID.
        String path = definition.id().path();
        if (definition.id().namespace().equals("rematter")) {
            String code = BADGE_CODES.get(path);
            if (code != null) return code;
        }
        var words = ID_WORD.matcher(path.substring(path.lastIndexOf('/') + 1));
        if (!words.find()) return "?";
        String first = words.group();
        if (!words.find()) return first.substring(0, Math.min(3, first.length())).toUpperCase(Locale.ROOT);
        String second = words.group();
        String abbreviation = words.find()
                ? new String(new char[]{first.charAt(0), second.charAt(0), words.group().charAt(0)})
                : first.substring(0, Math.min(2, first.length())) + second.charAt(0);
        return abbreviation.toUpperCase(Locale.ROOT);
    }

    public static Component withSymbol(SubstanceId id, Component name) {
        return appendSymbol(iconLabel(id), name);
    }

    public static Component withSymbol(String id, Component name) {
        return appendSymbol(iconLabel(id), name);
    }

    private static Component appendSymbol(String symbol, Component name) {
        return symbol.isEmpty() ? name : name.copy().append(" (" + symbol + ")");
    }

    static String shortLabel(String id, String name) {
        return prefixed(iconLabel(id), name);
    }

    static String prefixed(String symbol, String name) {
        return symbol.isEmpty() ? name : symbol + " · " + name;
    }

    public static boolean matchesSymbol(String symbol, String query) {
        return !symbol.isEmpty() && !query.isBlank() && (symbol.equalsIgnoreCase(query)
                || Normalizer.normalize(symbol, Normalizer.Form.NFKC)
                        .equalsIgnoreCase(Normalizer.normalize(query, Normalizer.Form.NFKC)));
    }
}
