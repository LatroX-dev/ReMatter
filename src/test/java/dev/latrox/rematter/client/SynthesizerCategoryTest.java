package dev.latrox.rematter.client;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.ShippedChemistry;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.content.MachineKind;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static dev.latrox.rematter.chemistry.BuiltinChemistry.*;
import static org.junit.jupiter.api.Assertions.*;

class SynthesizerCategoryTest {
    @Test void everyShippedSynthesisOutputPartitionsIntoConcreteCategories() {
        ChemistryRegistry registry = ShippedChemistry.standalone();
        var counts = new EnumMap<SynthesizerCategory, Integer>(SynthesizerCategory.class);
        for (var category : SynthesizerCategory.values()) counts.put(category, 0);

        int total = 0;
        for (var profile : registry.profiles()) {
            if (!registry.synthesisPolicy().allowsItem(profile.itemId())) continue;
            var product = ProcessPresentation.resolve(registry, MachineKind.SYNTHESIZER, profile.itemId(), profile.itemId()).orElseThrow();
            SynthesizerCategory category = SynthesizerCategory.classify(product);
            assertNotEquals(SynthesizerCategory.ALL, category, () -> "Expected concrete category for " + profile.itemId());
            assertTrue(SynthesizerCategory.ALL.matches(product));
            assertTrue(category.matches(product));
            counts.merge(category, 1, Integer::sum);
            total++;
        }
        for (var fluid : registry.fluidProfiles()) {
            if (!registry.synthesisPolicy().allowsFluid(fluid.fluidId())) continue;
            var product = ProcessPresentation.resolve(registry, MachineKind.SYNTHESIZER, "fluid:" + fluid.fluidId(), fluid.fluidId()).orElseThrow();
            assertEquals(SynthesizerCategory.FLUIDS, SynthesizerCategory.classify(product), () -> "Expected FLUIDS for " + fluid.fluidId());
            assertTrue(SynthesizerCategory.ALL.matches(product));
            assertTrue(SynthesizerCategory.FLUIDS.matches(product));
            counts.merge(SynthesizerCategory.FLUIDS, 1, Integer::sum);
            total++;
        }

        assertTrue(total >= 700, "Expected full unified synthesis catalogue, saw " + total);
        assertEquals(0, counts.get(SynthesizerCategory.ALL));
        for (var category : new SynthesizerCategory[]{
                SynthesizerCategory.METALS, SynthesizerCategory.CRYSTALS, SynthesizerCategory.STONE,
                SynthesizerCategory.ORGANIC, SynthesizerCategory.FLUIDS}) {
            assertTrue(counts.get(category) > 0, () -> "Expected non-empty category " + category);
        }
    }

    @Test void classifiesVanillaAndReforgingMaterialsConsistentlyWithoutStoneToCrystalLeakage() {
        ChemistryRegistry registry = ShippedChemistry.standalone();
        assertCategory(registry, "minecraft:iron_ingot", SynthesizerCategory.METALS);
        assertCategory(registry, "alltheores:bronze_ingot", SynthesizerCategory.METALS);
        assertCategory(registry, "ae2:silicon", SynthesizerCategory.METALS);

        assertCategory(registry, "minecraft:diamond", SynthesizerCategory.CRYSTALS);
        assertCategory(registry, "biomesoplenty:rose_quartz_chunk", SynthesizerCategory.CRYSTALS);
        assertCategory(registry, "minecraft:redstone", SynthesizerCategory.CRYSTALS);
        assertCategory(registry, "minecraft:glowstone_dust", SynthesizerCategory.CRYSTALS);

        // Stone/nether/end rock variants with trace crystal components must remain in STONE, not CRYSTALS.
        assertCategory(registry, "minecraft:cobblestone", SynthesizerCategory.STONE);
        assertCategory(registry, "minecraft:end_stone", SynthesizerCategory.STONE);
        assertCategory(registry, "minecraft:netherrack", SynthesizerCategory.STONE);
        assertCategory(registry, "minecraft:soul_sand", SynthesizerCategory.STONE);
        assertCategory(registry, "biomesoplenty:black_sand", SynthesizerCategory.STONE);
        assertCategory(registry, "biomesoplenty:dried_salt", SynthesizerCategory.STONE);
        assertCategory(registry, "regions_unexplored:chalk", SynthesizerCategory.STONE);
        assertCategory(registry, "regions_unexplored:mossy_stone", SynthesizerCategory.STONE);
        assertCategory(ShippedChemistry.loadUnunified(ShippedChemistry.MAIN_RESOURCES), "nautec:salt", SynthesizerCategory.STONE);

        assertCategory(registry, "minecraft:oak_log", SynthesizerCategory.ORGANIC);
        assertCategory(registry, "biomesoplenty:redwood_log", SynthesizerCategory.ORGANIC);
        assertCategory(registry, "regions_unexplored:baobab_log", SynthesizerCategory.ORGANIC);
        assertCategory(registry, "integrateddynamics:menril_log", SynthesizerCategory.ORGANIC);
        assertCategory(registry, "integrateddynamics:menril_berries", SynthesizerCategory.ORGANIC);
        assertCategory(registry, "ecologics:seashell", SynthesizerCategory.ORGANIC);

        assertCategory(registry, "fluid:minecraft:water", SynthesizerCategory.FLUIDS);
        assertCategory(registry, "fluid:minecraft:lava", SynthesizerCategory.FLUIDS);
    }

    @Test void newReforgingMaterialChainsObeyConservationAndDecomposeOnlyBounds() {
        ChemistryRegistry registry = ShippedChemistry.standalone();
        SubstanceKey cellulose = SubstanceKey.of("rematter:cellulose", Phase.SOLID);

        // Integrated Dynamics Menril chain: 1 log -> up to 6 planks via sawmill -> 1,500 mB resin (1.5 blocks) + 6 chunks = 19.5 chunks max.
        var menrilLog = registry.profile("integrateddynamics:menril_log").orElseThrow();
        var menrilBlock = registry.profile("integrateddynamics:crystalized_menril_block").orElseThrow();
        var menrilChunk = registry.profile("integrateddynamics:crystalized_menril_chunk").orElseThrow();
        assertFalse(registry.synthesisPolicy().allowsItem("integrateddynamics:crystalized_menril_block"));
        assertFalse(registry.synthesisPolicy().allowsItem("integrateddynamics:crystalized_menril_chunk"));
        long logCellulose = menrilLog.matter().get(cellulose);
        double perChunk = (double) menrilChunk.matter().get(cellulose) / menrilChunk.itemCount();
        double perBlock = (double) menrilBlock.matter().get(cellulose) / menrilBlock.itemCount();
        assertEquals(perBlock, perChunk * 9.0, 0.001, "9 chunks must match 1 block");
        assertTrue(perBlock * 1.5 + perChunk * 6.0 <= logCellulose,
                "Sawmill + Mechanical Squeezer + Drying Basin chain must never exceed Menril log cellulose");

        // Regions Unexplored Ash (crafted from 4 gunpowder: carbon/nitrogen/sulfur, ZERO silica) and Ashen Dirt (2 dirt + 2 ash -> 4).
        var ash = registry.profile("regions_unexplored:ash").orElseThrow();
        var gunpowder = registry.profile("minecraft:gunpowder").orElseThrow();
        assertFalse(registry.synthesisPolicy().allowsItem("regions_unexplored:ash"));
        assertFalse(ash.matter().containsKey(SILICA), "Ash craftable from gunpowder must never create silica");
        assertTrue(ash.matter().getOrDefault(CARBON, 0L) <= 4L * gunpowder.matter().getOrDefault(CARBON, 0L));

        var ashenDirt = registry.profile("regions_unexplored:ashen_dirt").orElseThrow();
        var dirt = registry.profile("minecraft:dirt").orElseThrow();
        assertFalse(registry.synthesisPolicy().allowsItem("regions_unexplored:ashen_dirt"));
        double ashenSoilPerItem = (double) ashenDirt.matter().getOrDefault(SOIL, 0L) / ashenDirt.itemCount();
        assertTrue(4.0 * ashenSoilPerItem <= 2.0 * dirt.matter().getOrDefault(SOIL, 0L),
                "4 ashen dirt crafted from 2 dirt must not exceed 2 dirt soil");

        // Nautec alloys & Flux Dust stay decompose-only.
        assertTrue(registry.profile("nautec:cast_iron_ingot").isPresent());
        assertFalse(registry.synthesisPolicy().allowsItem("nautec:cast_iron_ingot"));
        assertTrue(registry.profile("nautec:aquarine_steel_ingot").isPresent());
        assertFalse(registry.synthesisPolicy().allowsItem("nautec:aquarine_steel_ingot"));
        assertTrue(registry.profile("fluxnetworks:flux_dust").isPresent());
        assertFalse(registry.synthesisPolicy().allowsItem("fluxnetworks:flux_dust"));
    }

    @Test void responsiveCategoryLayoutNeverOverflowsLeftColumnAtNarrowWidths() {
        for (int screenWidth : new int[]{200, 240, 260, 276, 320, 480}) {
            int panelWidth = Math.min(dev.latrox.rematter.client.ui.UiMetrics.SYNTH_PICKER_WIDTH, screenWidth - 12);
            int resultsWidth = (panelWidth - 36) / 2;
            var layout = SynthesizerCategory.layoutForWidth(resultsWidth);
            assertTrue(layout.perRow() >= 1 && layout.rows() >= 1);
            for (int i = 0; i < SynthesizerCategory.values().length; i++) {
                int idx = i;
                int rightEdge = layout.xOffset(idx) + layout.buttonWidth();
                assertTrue(rightEdge <= resultsWidth,
                        () -> "Button " + idx + " right edge " + rightEdge + " exceeds resultsWidth " + resultsWidth + " at screenWidth=" + screenWidth);
            }
        }
        // Standard 276px synthesis picker fits all 6 categories on 1 single row (resultsWidth=120).
        var full = SynthesizerCategory.layoutForWidth(120);
        assertEquals(6, full.perRow());
        assertEquals(1, full.rows());
        // Narrow 260px screen (panel=248, resultsWidth=106) wraps cleanly to 2 rows (5 + 1) with zero overlap!
        var narrow = SynthesizerCategory.layoutForWidth(106);
        assertEquals(5, narrow.perRow());
        assertEquals(2, narrow.rows());
    }

    @Test void craftableCheckHandlesCompletePartialSyncingAndNoStorageStates() {
        var product = new ProcessPresentation.Product(
                "example:steel", "Steel", "item", "example:steel", 1,
                Map.of(IRON, 1_000L, CARBON, 250L), Map.of(), 16_000L, 100);

        // COMPLETE state: exact full-batch match across all ingredients.
        assertTrue(SynthesizerCategory.isCraftable(product, Map.of(IRON, 1_000L, CARBON, 250L), SynthesizerCategory.StockState.COMPLETE));
        assertTrue(SynthesizerCategory.isCraftable(product, Map.of(IRON, 5_000L, CARBON, 900L, COPPER, 100L), SynthesizerCategory.StockState.COMPLETE));
        assertFalse(SynthesizerCategory.isCraftable(product, Map.of(IRON, 1_000L, CARBON, 249L), SynthesizerCategory.StockState.COMPLETE));
        assertFalse(SynthesizerCategory.isCraftable(product, Map.of(IRON, 1_000L), SynthesizerCategory.StockState.COMPLETE));

        // PARTIAL state: hides known shortages, keeps entries with unknown missing keys visible rather than falsely hiding them.
        assertTrue(SynthesizerCategory.isCraftable(product, Map.of(IRON, 1_000L), SynthesizerCategory.StockState.PARTIAL));
        assertFalse(SynthesizerCategory.isCraftable(product, Map.of(IRON, 500L), SynthesizerCategory.StockState.PARTIAL));

        // SYNCING & NO_STORAGE states return false (UI shows syncing/no-stock state).
        assertFalse(SynthesizerCategory.isCraftable(product, Map.of(IRON, 5_000L, CARBON, 900L), SynthesizerCategory.StockState.SYNCING));
        assertFalse(SynthesizerCategory.isCraftable(product, Map.of(IRON, 5_000L, CARBON, 900L), SynthesizerCategory.StockState.NO_STORAGE));
        assertFalse(SynthesizerCategory.isCraftable(null, Map.of(IRON, 5_000L), SynthesizerCategory.StockState.COMPLETE));

        var emptyInputs = new ProcessPresentation.Product(
                "example:free", "Free", "item", "example:free", 1,
                Map.of(), Map.of(), 1_000L, 20);
        assertFalse(SynthesizerCategory.isCraftable(emptyInputs, Map.of(IRON, 5_000L), SynthesizerCategory.StockState.COMPLETE));
    }

    private static void assertCategory(ChemistryRegistry registry, String id, SynthesizerCategory expected) {
        var product = ProcessPresentation.resolve(registry, MachineKind.SYNTHESIZER, id, id)
                .orElseThrow(() -> new AssertionError("Missing shipped synthesis output " + id));
        assertEquals(expected, SynthesizerCategory.classify(product), () -> "Unexpected category for " + id);
    }
}
