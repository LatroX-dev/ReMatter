package dev.latrox.rematter.client;

import dev.latrox.rematter.chemistry.SubstanceDefinition;
import dev.latrox.rematter.content.MachineSnapshot;
import dev.latrox.rematter.content.StockQuery;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/** What a stock browser shows: text search, a phase filter and an optional favorites-only view, favorites always first. */
record StockFilter(String text, Set<String> phases, boolean favoritesOnly) {
    StockFilter {
        phases = Set.copyOf(phases);
        if (!StockQuery.PHASES.containsAll(phases)) throw new IllegalArgumentException("Unknown phase filter");
    }

    /**
     * The query that lets the server keep matches and favorites within its row limit. Names and formulas exist only on
     * the client, so the catalogue substances they match are sent by ID, at most {@link StockQuery#MAX_NAMED} of them.
     */
    StockQuery query(Set<MachineSnapshot.StockKey> favorites, Collection<SubstanceDefinition> catalogue,
                     Function<SubstanceDefinition, String> name, Function<SubstanceDefinition, String> symbol) {
        String query = text.strip().toLowerCase(Locale.ROOT);
        var named = new LinkedHashSet<String>();
        if (!query.isEmpty()) for (var definition : catalogue) {
            if (named.size() > StockQuery.MAX_NAMED) break;
            if (name.apply(definition).toLowerCase(Locale.ROOT).contains(query) || SubstanceLabels.matchesSymbol(symbol.apply(definition), query))
                named.add(definition.id().toString());
        }
        boolean truncated=named.size()>StockQuery.MAX_NAMED;
        if(truncated)named.removeLast();
        return new StockQuery(text, phases, favoritesOnly, favorites, named, truncated);
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
