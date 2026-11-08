package dev.latrox.rematter.client;

import dev.latrox.rematter.content.MachineSnapshot.Entry;
import dev.latrox.rematter.content.MachineSnapshot.StockKey;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StockFilterTest {
    private static final Entry IRON = new Entry("rematter:iron", "solid", 10, 10, true);
    private static final Entry WATER = new Entry("rematter:water", "liquid", 20, 20, true);
    private static final Entry STEAM = new Entry("rematter:water", "gas", 5, 5, true);
    private static final Entry REMOVED = new Entry("example:removed", "solid", 7, 7, false);
    private static final List<Entry> STOCK = List.of(IRON, WATER, STEAM, REMOVED);
    private static final Function<Entry, String> NAME = entry -> switch (entry.id()) {
        case "rematter:iron" -> "Iron";
        case "rematter:water" -> "Water";
        default -> "Unknown substance";
    };
    private static final Function<String, String> SYMBOL = id -> id.equals("rematter:water") ? "H₂O" : id.equals("rematter:iron") ? "Fe" : "";

    private static List<Entry> apply(StockFilter filter, Set<StockKey> favorites) {
        return filter.apply(STOCK, favorites::contains, NAME, SYMBOL);
    }

    @Test void noFilterKeepsTheSynchronizedOrder() {
        assertEquals(STOCK, apply(new StockFilter("", Set.of(), false), Set.of()));
    }

    @Test void phaseChipsCombineAndAnEmptySelectionShowsEverything() {
        assertEquals(List.of(IRON, REMOVED), apply(new StockFilter("", Set.of("solid"), false), Set.of()));
        assertEquals(List.of(WATER, STEAM), apply(new StockFilter("", Set.of("liquid", "gas"), false), Set.of()));
    }

    @Test void favoritesComeFirstAndCanBeShownAlone() {
        var favorites = Set.of(STEAM.key(), REMOVED.key());
        assertEquals(List.of(STEAM, REMOVED, IRON, WATER), apply(new StockFilter("", Set.of(), false), favorites));
        assertEquals(List.of(STEAM, REMOVED), apply(new StockFilter("", Set.of(), true), favorites));
        assertEquals(List.of(REMOVED), apply(new StockFilter("", Set.of("solid"), true), favorites));
    }

    @Test void searchMatchesNameIdPhaseAndFormulaTogetherWithTheOtherFilters() {
        assertEquals(List.of(WATER, STEAM), apply(new StockFilter("  WAT ", Set.of(), false), Set.of()));
        assertEquals(List.of(IRON), apply(new StockFilter("fe", Set.of(), false), Set.of()));
        assertEquals(List.of(STEAM), apply(new StockFilter("h2o", Set.of("gas"), false), Set.of()));
        assertEquals(List.of(REMOVED), apply(new StockFilter("example:", Set.of(), false), Set.of()));
        assertEquals(List.of(), apply(new StockFilter("iron", Set.of("liquid"), false), Set.of()));
    }

    @Test void unknownPhasesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new StockFilter("", Set.of("plasma"), false));
    }
}
