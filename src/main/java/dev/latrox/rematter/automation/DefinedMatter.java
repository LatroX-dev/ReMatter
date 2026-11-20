package dev.latrox.rematter.automation;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.SubstanceKey;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Native networks trade only substances that the current catalogue defines. Stock of an undefined substance stays
 * preserved in the cell that holds it; it is neither offered to nor accepted from a network.
 */
public final class DefinedMatter {
    private DefinedMatter() {}

    /** The offered stock without undefined substances; an all-defined stock is returned unchanged. */
    public static Map<SubstanceKey, Long> offered(ChemistryRegistry registry, Map<SubstanceKey, Long> stock) {
        if (stock.keySet().stream().allMatch(registry::isKnown)) return stock;
        var result = new TreeMap<SubstanceKey, Long>();
        stock.forEach((key, amount) -> { if (registry.isKnown(key)) result.put(key, amount); });
        return Collections.unmodifiableMap(result);
    }
}
