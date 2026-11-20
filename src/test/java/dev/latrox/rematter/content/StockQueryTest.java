package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.content.MachineSnapshot.StockKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StockQueryTest {
    private static final int LIMIT = MachineSnapshot.MAX_ENTRIES;
    private static final List<SubstanceKey> STOCK = stock();

    private static List<SubstanceKey> stock() {
        var keys = new TreeSet<SubstanceKey>();
        IntStream.range(0, 400).forEach(i -> {
            keys.add(key("test:material_%03d".formatted(i), Phase.SOLID));
            keys.add(key("test:material_%03d".formatted(i), Phase.LIQUID));
        });
        keys.add(key("test:zinc_vapor", Phase.GAS));
        return List.copyOf(keys);
    }

    private static SubstanceKey key(String id, Phase phase) { return new SubstanceKey(SubstanceId.parse(id), phase); }

    private static StockQuery query(String text, Set<String> phases, boolean favoritesOnly, Set<StockKey> favorites, Set<String> named) {
        return new StockQuery(text, phases, favoritesOnly, favorites, named);
    }

    @Test void withoutAQueryTheLowestKeysFillTheLimitInKeyOrder() {
        var selected = StockQuery.ALL.select(STOCK, LIMIT);
        assertEquals(STOCK.subList(0, LIMIT), selected);
        assertEquals(List.of(), StockQuery.ALL.select(STOCK, 0));
    }

    @Test void favoritesBeyondTheLimitAreKeptAndReturnedInKeyOrder() {
        var last = STOCK.getLast();
        var favorite = new StockKey(last.id().toString(), "gas");
        var selected = StockQuery.ALL.select(STOCK, LIMIT);
        assertFalse(selected.contains(last));
        selected = query("", Set.of(), false, Set.of(favorite), Set.of()).select(STOCK, LIMIT);
        assertEquals(LIMIT, selected.size());
        assertTrue(selected.contains(last));
        assertEquals(new ArrayList<>(new TreeSet<>(selected)), selected);
    }

    @Test void searchMatchesComeBeforeTheRestWhenTheStockExceedsTheLimit() {
        var selected = query("material_39", Set.of(), false, Set.of(), Set.of()).select(STOCK, LIMIT);
        var matches = STOCK.stream().filter(key -> key.id().toString().contains("material_39")).toList();
        assertEquals(20, matches.size());
        assertTrue(selected.containsAll(matches));
        assertEquals(LIMIT, selected.size(), "Remaining rows still fill the view for the client's own filter");
    }

    @Test void translatedNameMatchesArriveAsIdsAndRankLikeSearchMatches() {
        var zinc = key("test:zinc_vapor", Phase.GAS);
        assertFalse(query("Zinkdampf", Set.of(), false, Set.of(), Set.of()).select(STOCK, 10).contains(zinc));
        assertTrue(query("Zinkdampf", Set.of(), false, Set.of(), Set.of("test:zinc_vapor")).select(STOCK, 10).contains(zinc));
    }

    @Test void phaseFiltersAndFavoritesOnlyRankRowsThatPassEveryFilter() {
        var gas = query("", Set.of("gas"), false, Set.of(), Set.of()).select(STOCK, 1);
        assertEquals(List.of(key("test:zinc_vapor", Phase.GAS)), gas);
        var favorite = new StockKey("test:material_399", "liquid");
        var other = new StockKey("test:material_398", "solid");
        var onlyFavorites = query("", Set.of("liquid"), true, Set.of(favorite, other), Set.of()).select(STOCK, 2);
        assertEquals(List.of(key("test:material_398", Phase.SOLID), key("test:material_399", Phase.LIQUID)), onlyFavorites,
                "The favorite that passes every filter comes first, then the favorite outside the phase filter");
    }

    @Test void boundsAndIdentifiersAreValidated() {
        var key = new StockKey("test:iron", "solid");
        assertThrows(IllegalArgumentException.class, () -> query("x".repeat(StockQuery.MAX_TEXT + 1), Set.of(), false, Set.of(), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> query("", Set.of("plasma"), false, Set.of(), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> query("", Set.of(), false, Set.of(new StockKey("test:iron", "plasma")), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> query("", Set.of(), false, Set.of(new StockKey("Not An Id", "solid")), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> query("", Set.of(), false, Set.of(), Set.of("Not An Id")));
        var tooMany = new HashSet<StockKey>();
        IntStream.rangeClosed(0, StockQuery.MAX_FAVORITES).forEach(i -> tooMany.add(new StockKey("test:item_" + i, "solid")));
        assertThrows(IllegalArgumentException.class, () -> query("", Set.of(), false, tooMany, Set.of()));
        var tooManyNames = new HashSet<String>();
        IntStream.rangeClosed(0, StockQuery.MAX_NAMED).forEach(i -> tooManyNames.add("test:item_" + i));
        assertThrows(IllegalArgumentException.class, () -> query("", Set.of(), false, Set.of(), tooManyNames));
        assertDoesNotThrow(() -> query("iron", Set.of("solid"), true, Set.of(key), Set.of("test:iron")));
    }
    @Test void matchingCountIsIndependentOfTheTransmittedRowLimitAndHonorsEveryFilter() {
        var searched=query("material_39",Set.of(),false,Set.of(),Set.of());
        assertEquals(20,searched.matches(STOCK));
        assertEquals(3,searched.select(STOCK,3).size());
        assertEquals(10,query("material_39",Set.of("liquid"),false,Set.of(),Set.of()).matches(STOCK));
        var favorite=new StockKey("test:material_399","liquid");
        assertEquals(1,query("material_39",Set.of("liquid"),true,Set.of(favorite),Set.of()).matches(STOCK));
    }
    @Test void truncatedTranslatedNamesRetainTheLowerBoundFlagWithoutInventingMatches() {
        var bounded=new StockQuery("translated",Set.of("gas"),false,Set.of(),Set.of("test:zinc_vapor"),true);
        assertTrue(bounded.namesTruncated());assertEquals(1,bounded.matches(STOCK));
        assertFalse(StockQuery.ALL.namesTruncated());assertEquals(STOCK.size(),StockQuery.ALL.matches(STOCK));
    }
}
