package dev.latrox.rematter.chemistry;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static dev.latrox.rematter.chemistry.BuiltinChemistry.*;
import static org.junit.jupiter.api.Assertions.*;

class ChemistryRegistryTest {
    @Test
    void waterRoundTripPreservesAtomsAndReturnsExactlyTheOriginalLiquid() {
        ChemistryRegistry registry = BuiltinChemistry.create();
        ProcessRecipe electrolysis = registry.recipe(SubstanceId.parse("rematter:water_electrolysis")).orElseThrow();
        ProcessRecipe synthesis = registry.recipe(SubstanceId.parse("rematter:water_synthesis")).orElseThrow();
        assertEquals(Map.of(HYDROGEN, 1_000L, OXYGEN, 500L), electrolysis.outputs());
        assertEquals(registry.elementAmounts(electrolysis.inputs()), registry.elementAmounts(electrolysis.outputs()));

        MatterInventory inventory = new MatterInventory(2_000);
        inventory.insert(WATER, 1_000);
        assertTrue(inventory.apply(electrolysis.inputs(), electrolysis.outputs()));
        assertTrue(inventory.apply(synthesis.inputs(), synthesis.outputs()));
        assertEquals(Map.of(WATER, 1_000L), inventory.contents());
        assertTrue(electrolysis.energy() > 0 && synthesis.energy() > 0);
    }

    @Test
    void everyBuiltInProcessConservesItsExactElementVector() {
        ChemistryRegistry registry = BuiltinChemistry.create();
        for (ProcessRecipe recipe : registry.processes()) {
            assertEquals(registry.elementAmounts(recipe.inputs()), registry.elementAmounts(recipe.outputs()),
                    recipe.id().toString());
        }
    }

    @Test
    void invalidRecipesCannotEnterTheRegistry() {
        ProcessRecipe creation = new ProcessRecipe(SubstanceId.parse("example:free_copper"), ProcessType.REACTION,
                Map.of(COPPER, 1L), Map.of(COPPER, 2L), 100, 20);
        assertThrows(IllegalArgumentException.class,
                () -> ChemistryFixtures.copyOf(BuiltinChemistry.create()).addProcess(creation).build());

        SubstanceKey unknown = SubstanceKey.of("example:unknown", Phase.SOLID);
        ProcessRecipe unknownRecipe = new ProcessRecipe(SubstanceId.parse("example:unknown_process"), ProcessType.REACTION,
                Map.of(unknown, 1L), Map.of(unknown, 1L), 100, 20);
        assertThrows(IllegalArgumentException.class,
                () -> ChemistryFixtures.copyOf(BuiltinChemistry.create()).addProcess(unknownRecipe).build());
    }

    @Test
    void unsupportedPhasesAndCompositionOverflowFailValidation() {
        MaterialProfile gaseousCopper = new MaterialProfile("example:gas_copper", Map.of(
                new SubstanceKey(COPPER.id(), Phase.GAS), 1L), 100, 100, 20);
        assertThrows(IllegalArgumentException.class,
                () -> ChemistryFixtures.copyOf(BuiltinChemistry.create()).addProfile(gaseousCopper).build());
        MaterialProfile overflow = new MaterialProfile("example:too_much_water", Map.of(WATER, Long.MAX_VALUE),
                100, 100, 20);
        assertThrows(ArithmeticException.class,
                () -> ChemistryFixtures.copyOf(BuiltinChemistry.create()).addProfile(overflow).build());
    }

    @Test
    void manufacturedItemsCannotBeApprovedByCategoryOrImplicitProfile() {
        MaterialProfile machine = new MaterialProfile("example:machine", Map.of(COPPER, 1_000L), 100, 100, 20);
        ChemistryRegistry registry = ChemistryFixtures.copyOf(ShippedChemistry.standalone()).addProfile(machine).build();
        assertTrue(registry.profile("example:machine").isPresent());
        assertFalse(registry.synthesisPolicy().category("example:machine").isPresent());
        assertFalse(registry.synthesisPolicy().category("minecraft:raw_copper").isPresent());
        assertFalse(registry.synthesisPolicy().category("minecraft:copper_sword").isPresent());
        assertTrue(registry.synthesisPolicy().category("minecraft:copper_ingot").isPresent());
        for (SynthesisCategory category : Set.of(SynthesisCategory.MACHINE, SynthesisCategory.COMPONENT,
                SynthesisCategory.EQUIPMENT, SynthesisCategory.OTHER)) {
            assertThrows(IllegalArgumentException.class,
                    () -> ChemistryRegistry.builder().allowSynthesis("example:machine", category));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new SynthesisPolicy(Map.of("#c:ingots", SynthesisCategory.RAW_MATERIAL)));
    }

    @Test
    void plainItemSynthesisRequiresARealProfile() {
        assertThrows(IllegalArgumentException.class, () -> ChemistryFixtures.copyOf(BuiltinChemistry.create())
                .allowSynthesis("example:unknown_ingot", SynthesisCategory.RAW_MATERIAL).build());
    }

    @Test
    void copperBlockAndIngotProfilesCannotDuplicateMaterialThroughCrafting() {
        ChemistryRegistry registry = ShippedChemistry.standalone();
        MaterialProfile ingot = registry.profile("minecraft:copper_ingot").orElseThrow();
        MaterialProfile block = registry.profile("minecraft:copper_block").orElseThrow();
        assertEquals(ChemistryFixtures.matterFor(ingot, 9), block.matter());
        assertThrows(ArithmeticException.class, () -> ChemistryFixtures.matterFor(ingot, Long.MAX_VALUE));
    }

    @Test
    void allRequiredMaterialPhasesHaveExplicitSupportedDefinitions() {
        ChemistryRegistry registry = ShippedChemistry.standalone();
        assertTrue(registry.profile("minecraft:dirt").isPresent());
        assertTrue(registry.profile("minecraft:stone").isPresent());
        assertTrue(registry.isKnown(WATER));
        assertTrue(registry.isKnown(LAVA));
        assertTrue(registry.isKnown(HYDROGEN));
        assertTrue(registry.isKnown(STEAM));
        assertEquals(registry.definition(WATER.id()).orElseThrow().composition(),
                registry.definition(STEAM.id()).orElseThrow().composition());
    }
}
