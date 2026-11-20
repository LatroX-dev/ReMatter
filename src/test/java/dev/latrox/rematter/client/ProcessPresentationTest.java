package dev.latrox.rematter.client;

import dev.latrox.rematter.chemistry.ChemistryFixtures;
import dev.latrox.rematter.chemistry.BuiltinChemistry;
import dev.latrox.rematter.chemistry.MaterialProfile;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.ShippedChemistry;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.chemistry.SynthesisCategory;
import dev.latrox.rematter.content.MachineKind;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static dev.latrox.rematter.chemistry.BuiltinChemistry.*;
import static org.junit.jupiter.api.Assertions.*;

class ProcessPresentationTest {
    @Test void productUsesExactBatchWithoutRoundingOrMultiplyingRecipeCost() {
        var profile = new MaterialProfile("example:copper_nugget", 9, Map.of(COPPER, 1_000L), 8_000, 16_000, 100);
        var registry = ChemistryFixtures.copyOf(BuiltinChemistry.create()).addProfile(profile)
                .allowSynthesis(profile.itemId(), SynthesisCategory.RAW_MATERIAL).build();
        var product = ProcessPresentation.resolve(registry, MachineKind.SYNTHESIZER, profile.itemId(), "Copper nuggets").orElseThrow();
        assertEquals("item", product.outputKind());
        assertEquals(profile.itemId(), product.outputId());
        assertEquals(9, product.outputCount());
        assertEquals(Map.of(COPPER, 1_000L), product.inputs());
        assertTrue(product.outputs().isEmpty());
        assertEquals(16_000, product.energy());
        assertEquals(100, product.ticks());
        assertThrows(UnsupportedOperationException.class, () -> product.inputs().put(COPPER, 111L));
    }

    @Test void decompositionProfileAndGasPermissionDoNotInventItemOutputs() {
        var registry = ShippedChemistry.standalone();
        assertTrue(registry.profile("minecraft:raw_copper").isPresent());
        assertTrue(ProcessPresentation.resolve(registry, MachineKind.SYNTHESIZER, "minecraft:raw_copper", "Raw copper").isEmpty());
        assertTrue(ProcessPresentation.resolve(registry, MachineKind.SYNTHESIZER, HYDROGEN.id().toString(), "Hydrogen").isEmpty());
        assertTrue(ProcessPresentation.resolve(registry, MachineKind.SYNTHESIZER, "example:machine", "Machine").isEmpty());
    }

    @Test void fluidPreviewKeepsMillibucketsAndCannotMasqueradeAsAnItem() {
        var registry = ShippedChemistry.standalone();
        var fluid = ProcessPresentation.resolve(registry, MachineKind.SYNTHESIZER, "fluid:minecraft:water", "Water").orElseThrow();
        assertEquals("fluid", fluid.outputKind());
        assertEquals("minecraft:water", fluid.outputId());
        assertEquals(1_000, fluid.outputCount());
        assertEquals(Map.of(WATER, 1_000L), fluid.inputs());
        assertTrue(ProcessPresentation.resolve(registry, MachineKind.SYNTHESIZER, "minecraft:water", "Water").isEmpty());
        assertTrue(ProcessPresentation.resolve(registry, MachineKind.SYNTHESIZER, "fluid:example:absent", "Absent").isEmpty());
    }

    @Test void processesExposeMatterOutputsOnlyOnTheMatchingMachine() {
        var registry = BuiltinChemistry.create();
        var process = ProcessPresentation.resolve(registry, MachineKind.REACTOR, "rematter:water_electrolysis", "Water electrolysis").orElseThrow();
        assertEquals("matter", process.outputKind());
        assertEquals("", process.outputId());
        assertEquals(Map.of(WATER, 1_000L), process.inputs());
        assertEquals(Map.of(HYDROGEN, 1_000L, OXYGEN, 500L), process.outputs());
        assertTrue(ProcessPresentation.resolve(registry, MachineKind.REACTOR, process.id(), process.name()).isPresent());
        assertTrue(ProcessPresentation.resolve(registry, MachineKind.DECOMPOSER, process.id(), process.name()).isEmpty());
        assertTrue(ProcessPresentation.resolve(registry, MachineKind.REACTOR, "rematter:water_synthesis", "Water synthesis").isPresent());
        assertTrue(ProcessPresentation.resolve(registry, MachineKind.INDUSTRIAL_REACTOR, "rematter:water_synthesis", "Water synthesis").isPresent());
    }

    @Test void stockLookupKeepsPhasesSeparateAndMissingPartialRowsUnknown() {
        var steam = new SubstanceKey(WATER.id(), Phase.GAS);
        var amounts = Map.of(WATER, 375L, steam, 20L);
        assertEquals(375, ProcessPresentation.available(amounts, false, WATER).orElseThrow());
        assertEquals(20, ProcessPresentation.available(amounts, false, steam).orElseThrow());
        assertTrue(ProcessPresentation.available(amounts, false, COPPER).isEmpty());
        assertEquals(0, ProcessPresentation.available(amounts, true, COPPER).orElseThrow());
        assertEquals(Map.of(WATER, 375L, steam, 20L), amounts);
    }
}
