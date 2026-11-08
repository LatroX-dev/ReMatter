package dev.latrox.rematter.client;

import dev.latrox.rematter.content.MachineSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/** What a stock browser shows: text search, a phase filter and an optional favorites-only view, favorites always first. */
record StockFilter(String text, Set<String> phases, boolean favoritesOnly) {
    static final Set<String> PHASES = Set.of("solid", "liquid", "gas");

    StockFilter {
        phases = Set.copyOf(phases);
        if (!PHASES.containsAll(phases)) throw new IllegalArgumentException("Unknown phase filter");
    }

    /** An empty phase selection shows every phase. Rows keep the synchronized order within each group. */
    List<MachineSnapshot.Entry> apply(List<MachineSnapshot.Entry> entries, Predicate<MachineSnapshot.StockKey> favorite,
                                      Function<MachineSnapshot.Entry, String> name, Function<String, String> symbol) {
        String query = text.strip().toLowerCase(Locale.ROOT);
        List<MachineSnapshot.Entry> favorites = new ArrayList<>();
        List<MachineSnapshot.Entry> others = new ArrayList<>();
        for (var entry : entries) {
            if (!phases.isEmpty() && !phases.contains(entry.phase())) continue;
            boolean starred = favorite.test(entry.key());
            if (favoritesOnly && !starred) continue;
            if (!query.isEmpty() && !matches(entry, query, name, symbol)) continue;
            (starred ? favorites : others).add(entry);
        }
        favorites.addAll(others);
        return List.copyOf(favorites);
    }

    private static boolean matches(MachineSnapshot.Entry entry, String query, Function<MachineSnapshot.Entry, String> name,
                                   Function<String, String> symbol) {
        return name.apply(entry).toLowerCase(Locale.ROOT).contains(query) || entry.id().toLowerCase(Locale.ROOT).contains(query)
                || entry.phase().contains(query) || SubstanceLabels.matchesSymbol(symbol.apply(entry.id()), query);
    }
}
