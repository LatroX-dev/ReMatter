package dev.latrox.rematter.compat;

import dev.latrox.rematter.chemistry.ChemistryFixtures;
import dev.latrox.rematter.chemistry.BuiltinChemistry;
import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.Composition;
import dev.latrox.rematter.chemistry.MaterialProfile;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.ProcessType;
import dev.latrox.rematter.chemistry.ShippedChemistry;
import dev.latrox.rematter.chemistry.SubstanceDefinition;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.chemistry.SubstanceKind;
import dev.latrox.rematter.content.MachineKind;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static dev.latrox.rematter.chemistry.BuiltinChemistry.*;
import static org.junit.jupiter.api.Assertions.*;

class ChemistryViewerTest {
    private static List<ChemistryViewer.Entry> entries(ChemistryRegistry registry) {
        return ChemistryViewer.entries(registry, id -> !id.getPath().startsWith("missing"), id -> true);
    }

    private static ChemistryViewer.Entry process(List<ChemistryViewer.Entry> entries, String path) {
        return entries.stream().filter(entry -> entry.id().getPath().equals("/" + entry.category().id() + "/process/rematter/" + path))
                .findFirst().orElseThrow();
    }

    @Test void everyProcessIsShownUnderItsOwnProcessTypeAndRunsWhereItRunsToday() {
        var registry = BuiltinChemistry.create();
        var entries = entries(registry);
        assertEquals(ChemistryViewer.Category.ELECTROLYSIS, process(entries, "water_electrolysis").category());
        assertEquals(ChemistryViewer.Category.PHASE_CHANGE, process(entries, "water_evaporation").category());
        assertEquals(ChemistryViewer.Category.SEPARATION, process(entries, "mineral_separation").category());
        for (var recipe : registry.processes()) {
            var shown = process(entries, recipe.id().path());
            assertEquals(recipe.type().name(), shown.category().name());
        }
        assertEquals(List.of(MachineKind.REACTOR, MachineKind.INDUSTRIAL_REACTOR), ChemistryViewer.Category.ELECTROLYSIS.machines());
        for (var type : Set.of(ProcessType.REACTION, ProcessType.SEPARATION, ProcessType.PHASE_CHANGE, ProcessType.CRYSTALLIZATION)) {
            assertEquals(dev.latrox.rematter.content.MachineRouting.machines(type), ChemistryViewer.Category.valueOf(type.name()).machines());
        }
    }

    @Test void recipeIdsAreStableUniqueAndSynthetic() {
        var first = entries(ShippedChemistry.standalone());
        var second = entries(ShippedChemistry.load(ShippedChemistry.MAIN_RESOURCES));
        assertEquals(first.stream().map(ChemistryViewer.Entry::id).toList(), second.stream().map(ChemistryViewer.Entry::id).toList());
        var unique = new HashSet<Identifier>();
        for (var entry : first) {
            assertTrue(unique.add(entry.id()), entry.id().toString());
            assertEquals("rematter", entry.id().getNamespace());
            assertTrue(entry.id().getPath().startsWith("/" + entry.category().id() + "/"), entry.id().toString());
        }
        assertEquals(Identifier.parse("rematter:/decomposition/item/minecraft/raw_copper"), ChemistryViewer.recipeId(
                ChemistryViewer.Category.DECOMPOSITION, "item", Identifier.parse("minecraft:raw_copper")));
        assertEquals(Identifier.parse("rematter:/synthesis/fluid/minecraft/water"), ChemistryViewer.recipeId(
                ChemistryViewer.Category.SYNTHESIS, "fluid", Identifier.parse("minecraft:water")));
    }

    @Test void onlyMatterThatAShownRecipeReachesIsListed() {
        var orphan = SubstanceKey.of("example:orphanite", Phase.SOLID);
        var modOnly = SubstanceKey.of("example:missing_essence", Phase.SOLID);
        var registry = ChemistryFixtures.copyOf(ShippedChemistry.standalone())
                .addSubstance(element(orphan.id()))
                .addSubstance(element(modOnly.id()))
                .addProfile(new MaterialProfile("example:missing_block", Map.of(modOnly, 9_000L), 1_000, 2_000, 20))
                .build();
        var entries = entries(registry);
        var listed = ChemistryViewer.substances(entries);
        assertFalse(listed.contains(orphan));
        assertFalse(listed.contains(modOnly));
        assertTrue(entries.stream().noneMatch(entry -> entry.id().getPath().contains("missing_block")));
        assertTrue(listed.containsAll(List.of(WATER, HYDROGEN, OXYGEN, COPPER)));
        assertEquals(listed.stream().sorted().distinct().toList(), listed);
        for (var key : listed) assertTrue(registry.isKnown(key), key.toString());
    }

    @Test void decompositionExplainsWhetherSynthesisIsAllowedAndKeepsTheExactBatch() {
        var entries = entries(ShippedChemistry.standalone());
        var copper = entries.stream().filter(entry -> entry.id().getPath().equals("/decomposition/item/minecraft/raw_copper")).findFirst().orElseThrow();
        assertEquals(ChemistryViewer.Category.DECOMPOSITION, copper.category());
        assertTrue(copper.consumed().isPresent() && copper.produced().isEmpty() && copper.inputs().isEmpty());
        assertFalse(copper.outputs().isEmpty());
        assertTrue(entries.stream().noneMatch(entry -> entry.id().getPath().equals("/synthesis/item/minecraft/raw_copper")));
        assertTrue(entries.stream().filter(entry -> entry.category() == ChemistryViewer.Category.SYNTHESIS)
                .allMatch(entry -> entry.synthesisAllowed() && entry.produced().isPresent() && entry.outputs().isEmpty()));
    }

    private static SubstanceDefinition element(SubstanceId id) {
        return new SubstanceDefinition(id, "substance." + id.namespace() + "." + id.path(), "", SubstanceKind.ELEMENT,
                new Composition(Map.of(id, 1L)), Set.of(Phase.SOLID));
    }
}
