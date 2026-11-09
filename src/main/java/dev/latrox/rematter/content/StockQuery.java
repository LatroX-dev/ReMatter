package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.content.MachineSnapshot.StockKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What a stock view asks the server to keep within its row limit: the search text, the phase filter, whether only
 * favorites are shown, the viewer's favorites and the substance IDs whose translated name or formula matches the search.
 * The server ranks rows that pass every filter first and favorites first within each group, and only then applies the
 * limit, so neither matches nor favorites are cut off by it. The client still filters the rows it receives.
 */
public record StockQuery(String text, Set<String> phases, boolean favoritesOnly, Set<StockKey> favorites, Set<String> named, boolean namesTruncated) {
    public static final int MAX_TEXT = 128;
    public static final int MAX_FAVORITES = 256;
    public static final int MAX_NAMED = 256;
    public static final Set<String> PHASES = Set.of("solid", "liquid", "gas");
    public static final StockQuery ALL = new StockQuery("", Set.of(), false, Set.of(), Set.of());

    public StockQuery(String text, Set<String> phases, boolean favoritesOnly, Set<StockKey> favorites, Set<String> named) {
        this(text,phases,favoritesOnly,favorites,named,false);
    }

    public boolean matches(SubstanceKey key) {
        String search=text.strip().toLowerCase(Locale.ROOT);String id=key.id().toString();String phase=key.phase().name().toLowerCase(Locale.ROOT);
        return (phases.isEmpty()||phases.contains(phase))&&(!favoritesOnly||favorites.contains(new StockKey(id,phase)))
                &&(search.isEmpty()||id.toLowerCase(Locale.ROOT).contains(search)||phase.contains(search)||named.contains(id));
    }
    public int matches(Iterable<SubstanceKey> keys) {int count=0;for(var key:keys)if(matches(key))count++;return count;}

    public StockQuery {
        phases = Set.copyOf(phases); favorites = Set.copyOf(favorites); named = Set.copyOf(named);
        if (text.length() > MAX_TEXT || !PHASES.containsAll(phases) || favorites.size() > MAX_FAVORITES || named.size() > MAX_NAMED)
            throw new IllegalArgumentException("Invalid stock query");
        for (var favorite : favorites) {
            if (favorite.id().length() > MachineSnapshot.MAX_SUBSTANCE_ID_LENGTH || !PHASES.contains(favorite.phase()))
                throw new IllegalArgumentException("Invalid favorite in stock query");
            SubstanceId.parse(favorite.id());
        }
        for (var id : named) {
            if (id.length() > MachineSnapshot.MAX_SUBSTANCE_ID_LENGTH) throw new IllegalArgumentException("Invalid name match in stock query");
            SubstanceId.parse(id);
        }
    }

    /**
     * Up to {@code limit} of the given keys, returned in key order. Keys that pass every filter come before the rest,
     * favorites before other keys within both groups, and lower keys first within each of these four ranks.
     */
    public List<SubstanceKey> select(Iterable<SubstanceKey> sortedKeys, int limit) {
        if (limit <= 0) return List.of();
        String search = text.strip().toLowerCase(Locale.ROOT);
        List<List<SubstanceKey>> ranks = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        for (var key : sortedKeys) {
            String id = key.id().toString();
            String phase = key.phase().name().toLowerCase(Locale.ROOT);
            boolean favorite = favorites.contains(new StockKey(id, phase));
            boolean shown = matches(key);
            var rank = ranks.get((shown ? 0 : 2) + (favorite ? 0 : 1));
            if (rank.size() < limit) rank.add(key);
        }
        var selected = new ArrayList<SubstanceKey>(limit);
        for (var rank : ranks) {
            for (var key : rank) {
                if (selected.size() == limit) break;
                selected.add(key);
            }
        }
        selected.sort(null);
        return List.copyOf(selected);
    }
}
