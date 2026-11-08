package dev.latrox.rematter.client;

import dev.latrox.rematter.chemistry.Composition;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceDefinition;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKind;
import dev.latrox.rematter.content.MachineSnapshot.Entry;
import dev.latrox.rematter.content.MachineSnapshot.StockKey;
import dev.latrox.rematter.content.StockQuery;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;
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

    @Test void theQueryNamesCatalogueSubstancesByTranslatedNameOrFormula() {
        var iron = definition("rematter:iron", "Fe");
        var water = definition("rematter:water", "H₂O");
        var catalogue = List.of(iron, water);
        Function<SubstanceDefinition, String> german = definition -> definition == iron ? "Eisen" : "Wasser";
        var favorites = Set.of(STEAM.key());
        var query = new StockFilter(" eis", Set.of("solid"), true).query(favorites, catalogue, german, SubstanceDefinition::symbol);
        assertEquals(new StockQuery(" eis", Set.of("solid"), true, favorites, Set.of("rematter:iron")), query);
        assertEquals(Set.of("rematter:water"), new StockFilter("h2o", Set.of(), false).query(Set.of(), catalogue, german, SubstanceDefinition::symbol).named());
        assertEquals(Set.of(), new StockFilter("", Set.of(), false).query(Set.of(), catalogue, german, SubstanceDefinition::symbol).named());
        var many = IntStream.range(0, StockQuery.MAX_NAMED + 10).mapToObj(i -> definition("test:element_" + i, "")).toList();
        assertEquals(StockQuery.MAX_NAMED, new StockFilter("e", Set.of(), false).query(Set.of(), many, d -> "Element", d -> "").named().size());
    }

    private static SubstanceDefinition definition(String id, String symbol) {
        var key = SubstanceId.parse(id);
        return new SubstanceDefinition(key, "substance.test", symbol, SubstanceKind.ELEMENT, Composition.element(key), Set.of(Phase.SOLID));
    }

    @Test void unknownPhasesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new StockFilter("", Set.of("plasma"), false));
    }
}
