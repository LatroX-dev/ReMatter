package dev.latrox.rematter.client;

import dev.latrox.rematter.chemistry.ShippedChemistry;
import dev.latrox.rematter.chemistry.SubstanceDefinition;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SubstanceBadgeTest {
    @Test
    void derivedBadgesNeverMatchAnotherSubstanceSymbolOrBadge() {
        Map<String, String> owners = new HashMap<>();
        var substances = ShippedChemistry.standalone().substances();
        for (SubstanceDefinition definition : substances) {
            String symbol = definition.symbol().strip();
            if (!symbol.isEmpty()) owners.putIfAbsent(symbol.toLowerCase(Locale.ROOT), definition.id().toString());
        }
        for (SubstanceDefinition definition : substances) {
            if (!definition.symbol().isBlank()) continue;
            String badge = SubstanceLabels.iconLabel(definition).toLowerCase(Locale.ROOT);
            String previous = owners.putIfAbsent(badge, definition.id().toString());
            assertNull(previous, definition.id() + " shares its badge with " + previous);
        }
    }
}
