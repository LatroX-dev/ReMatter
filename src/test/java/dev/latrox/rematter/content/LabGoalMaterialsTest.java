package dev.latrox.rematter.content;

import static dev.latrox.rematter.chemistry.BuiltinChemistry.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.latrox.rematter.chemistry.BuiltinChemistry;
import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.MaterialProfile;
import dev.latrox.rematter.content.TerminalBatchPlanner.PlanStep;
import dev.latrox.rematter.content.TerminalBatchPlanner.StepKind;
import dev.latrox.rematter.content.TerminalBatchPlanner.TargetKind;
import dev.latrox.rematter.research.MaterialResearch;
import dev.latrox.rematter.research.ResearchCatalogue;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class LabGoalMaterialsTest {
    @Test void aDirectUnresearchedSubstanceRequestCannotExposeOrQueueSteps() {
        var ctx = new TerminalBatchPlanner.NetworkContext(Map.of(SILICA, 1000L), Set.of(MachineKind.REACTOR),
                0, 1, "minecraft:overworld", Set.of(), id -> true, key -> false, id -> true);
        var result = TerminalBatchPlanner.plan(BuiltinChemistry.create(), TargetKind.SUBSTANCE,
                "rematter:silicon", "solid", 100, ctx);
        assertEquals(TerminalBatchPlanner.Status.REQUIRES_RESEARCH, result.status());
        assertEquals(List.of(), result.steps());
        assertEquals(List.of(), result.missingInputs());
    }

    @Test void extrapolatesOnlyExternalInputsAndRemainingCoproductsAcrossSteps() {
        var steps = List.of(new PlanStep(StepKind.PROCESS, MachineKind.REACTOR, "rematter:mineral_separation", 1, 48000),
                new PlanStep(StepKind.PROCESS, MachineKind.REACTOR, "rematter:silica_electrolysis", 1, 64000));
        var materials = LabGoalMaterials.summarize(BuiltinChemistry.create(), steps, TargetKind.SUBSTANCE, "rematter:silicon",
                "solid", 187, Map.of(ROCK, 1500L, SILICA, 200L));
        assertEquals(List.of(new LabGoalMaterials.Row("rematter:alumina", "solid", 0, 0, 1000),
                new LabGoalMaterials.Row("rematter:calcium_oxide", "solid", 0, 0, 1000),
                new LabGoalMaterials.Row("rematter:mineral_mix", "solid", 1000, 1500, 0),
                new LabGoalMaterials.Row("rematter:oxygen", "gas", 0, 0, 1000),
                new LabGoalMaterials.Row("rematter:silicon", "solid", 0, 0, 813)), materials);
    }

    @Test void researchExampleBelongsToTheLoadedFamilyAndExistsAsAnItem() {
        var chemistry = ChemistryRegistry.builder();
        BuiltinChemistry.create().substances().forEach(chemistry::addSubstance);
        for (String item : List.of("minecraft:sand", "minecraft:glass", "minecraft:sandstone")) {
            chemistry.addProfile(new MaterialProfile(item, Map.of(SILICA, 1000L), 8000, 16000, 400));
        }
        var catalogue = new ResearchCatalogue(List.of(new MaterialResearch("rematter:silica", "Sand & Glass",
                Set.of("minecraft:sand", "minecraft:glass", "minecraft:sandstone"), Set.of(SILICA), false, false, null, "", 4000)));
        var registry = chemistry.build();
        assertEquals("minecraft:sand", LabGoalMaterials.researchSample(registry, catalogue, TargetKind.SYNTHESIS_ITEM,
                "minecraft:glass", "", ignored -> false, ignored -> true));
        assertEquals("minecraft:sandstone", LabGoalMaterials.researchSample(registry, catalogue, TargetKind.SYNTHESIS_ITEM,
                "minecraft:glass", "", ignored -> false, id -> id.equals("minecraft:sandstone")));
        assertEquals("", LabGoalMaterials.researchSample(registry, ResearchCatalogue.EMPTY, TargetKind.SYNTHESIS_ITEM,
                "minecraft:glass", "", ignored -> false, ignored -> true));
    }
}
