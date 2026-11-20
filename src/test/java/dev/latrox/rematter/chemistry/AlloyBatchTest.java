package dev.latrox.rematter.chemistry;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/** Alloy profiles recover whole native batches without rounding and never undercut a native route through synthesis. */
class AlloyBatchTest {
    private static final SubstanceKey TIN = SubstanceKey.of("rematter:tin", Phase.SOLID);
    private static final SubstanceKey NICKEL = SubstanceKey.of("rematter:nickel", Phase.SOLID);
    private static final SubstanceKey NETHERIUM = SubstanceKey.of("rematter:netherium", Phase.SOLID);
    private static final ChemistryRegistry REGISTRY = ShippedChemistry.standalone();

    @Test
    void fourBronzeRecoverTheCheaperNativeBatchExactlyAndSinglePiecesAreNotRounded() {
        MaterialProfile ingot = profile("alltheores:bronze_ingot");
        assertEquals(Map.of(BuiltinChemistry.COPPER, 3_000L, TIN, 125L), ChemistryFixtures.matterFor(ingot, 4));
        assertThrows(IllegalArgumentException.class, () -> ChemistryFixtures.matterFor(ingot, 1));
        assertEquals(ChemistryFixtures.matterFor(ingot, 36), ChemistryFixtures.matterFor(profile("alltheores:bronze_block"), 4));
        assertEquals(ChemistryFixtures.matterFor(ingot, 4), ChemistryFixtures.matterFor(profile("alltheores:bronze_nugget"), 36));
        assertTrue(REGISTRY.synthesisPolicy().allowsItem(ingot.itemId()));
    }

    @Test
    void invarAndNetheriteKeepTheSmallerShareOfEachElementAndCannotBeSynthesized() {
        MaterialProfile invar = profile("alltheores:invar_ingot");
        assertEquals(Map.of(BuiltinChemistry.IRON, 1_500L, NICKEL, 1_000L), ChemistryFixtures.matterFor(invar, 3));
        assertEquals(ChemistryFixtures.matterFor(invar, 9), profile("alltheores:invar_block").matter());
        // Oritech alloys one iron and one nickel into two invar.
        assertTrue(ChemistryFixtures.matterFor(invar, 6).get(BuiltinChemistry.IRON) <= 3_000L);
        // Two iron and one nickel yield three invar elsewhere.
        assertTrue(ChemistryFixtures.matterFor(invar, 3).get(NICKEL) <= 1_000L);
        MaterialProfile netherite = profile("minecraft:netherite_ingot");
        assertEquals(Map.of(BuiltinChemistry.GOLD, 500L, NETHERIUM, 1_000L), netherite.matter());
        assertEquals(ChemistryFixtures.matterFor(netherite, 9), profile("minecraft:netherite_block").matter());
        for (String item : List.of("alltheores:invar_ingot", "ftbic:invar_dust", "alltheores:invar_block",
                "minecraft:netherite_ingot", "minecraft:netherite_block", "mekanism:dust_netherite", "occultism:netherite_dust")) {
            assertFalse(REGISTRY.synthesisPolicy().category(item).isPresent(), item);
        }
    }

    @Test
    void oneGoldAndOneScrapAlwaysCoverARecoveredNetheriteIngot() {
        Map<SubstanceKey, Long> oritechInputs = new TreeMap<>(profile("minecraft:gold_ingot").matter());
        profile("minecraft:netherite_scrap").matter().forEach((key, amount) -> oritechInputs.merge(key, amount, Math::addExact));
        profile("minecraft:netherite_ingot").matter().forEach((key, amount) ->
                assertTrue(amount <= oritechInputs.getOrDefault(key, 0L), "Netherite recovery creates " + key));
    }

    @Test
    void gooAlloysRecoverAtMostOneTwentiethOfTheirInputBlockPerIngot() {
        assertEquals(profile("minecraft:gold_block").matter(), ChemistryFixtures.matterFor(profile("justdirethings:blazegold_ingot"), 20));
        assertEquals(profile("minecraft:iron_block").matter(), ChemistryFixtures.matterFor(profile("justdirethings:ferricore_ingot"), 20));
        assertEquals(profile("minecraft:netherite_block").matter(), ChemistryFixtures.matterFor(profile("justdirethings:eclipsealloy_ingot"), 20));
        for (String alloy : List.of("blazegold", "ferricore", "eclipsealloy")) {
            assertEquals(ChemistryFixtures.matterFor(profile("justdirethings:" + alloy + "_ingot"), 5), profile("justdirethings:raw_" + alloy).matter());
        }
    }

    private static MaterialProfile profile(String item) {
        return REGISTRY.profile(item).orElseThrow(() -> new AssertionError("Missing profile " + item));
    }
}
