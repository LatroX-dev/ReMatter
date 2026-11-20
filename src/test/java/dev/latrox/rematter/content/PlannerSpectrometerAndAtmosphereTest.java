package dev.latrox.rematter.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.latrox.rematter.chemistry.BuiltinChemistry;
import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.Composition;
import dev.latrox.rematter.chemistry.EnvironmentSource;
import dev.latrox.rematter.chemistry.FluidProfile;
import dev.latrox.rematter.chemistry.MaterialProfile;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.ProcessRecipe;
import dev.latrox.rematter.chemistry.ProcessType;
import dev.latrox.rematter.chemistry.ReservedSubstances;
import dev.latrox.rematter.chemistry.SubstanceDefinition;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.chemistry.SubstanceKind;
import dev.latrox.rematter.chemistry.SynthesisCategory;
import dev.latrox.rematter.chemistry.SynthesisRequirement;
import dev.latrox.rematter.client.DiscoveryLensInspector;
import dev.latrox.rematter.client.DiscoveryLensInspector.SynthesisStatus;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class PlannerSpectrometerAndAtmosphereTest {
    private static ChemistryRegistry testRegistry() {
        ChemistryRegistry base = BuiltinChemistry.create();
        ChemistryRegistry.Builder b = ChemistryRegistry.builder();
        base.substances().forEach(b::addSubstance);
        base.processes().forEach(b::addProcess);

        // Add high-field diamond allotrope substance + industrial process + tier-2 diamond synthesis.
        SubstanceId diamondId = SubstanceId.parse("rematter:diamond");
        SubstanceKey diamondMatter = new SubstanceKey(diamondId, Phase.SOLID);
        b.addSubstance(new SubstanceDefinition(
                diamondId,
                "substance.rematter.diamond",
                "C",
                SubstanceKind.COMPOUND,
                new Composition(Map.of(BuiltinChemistry.CARBON.id(), 4L)),
                Set.of(Phase.SOLID)));
        b.addProcess(new ProcessRecipe(
                SubstanceId.parse("rematter:diamond_crystallization"),
                ProcessType.REACTION,
                Map.of(BuiltinChemistry.CARBON, 4_000L),
                Map.of(diamondMatter, 1_000L),
                256_000L,
                1600,
                "",
                4,
                false,
                diamondId));

        // Add profiles: allowlisted glass & diamond, decomposition-only pickaxe, advancement-gated chorus, allowlisted water fluid.
        b.addProfile(new MaterialProfile(
                "minecraft:glass",
                Map.of(BuiltinChemistry.SILICA, 1_000L),
                8_000L,
                12_000L,
                200));
        b.allowSynthesis("minecraft:glass", SynthesisCategory.NATURAL_MATERIAL);

        b.addProfile(new MaterialProfile(
                "minecraft:diamond",
                Map.of(diamondMatter, 1_000L),
                16_000L,
                64_000L,
                400));
        b.allowSynthesis(
                "minecraft:diamond",
                SynthesisCategory.RAW_MATERIAL,
                new SynthesisRequirement("material_synthesizer", 2, ""));

        b.addProfile(new MaterialProfile(
                "minecraft:chorus_fruit",
                Map.of(BuiltinChemistry.CARBON, 500L, BuiltinChemistry.WATER, 500L),
                8_000L,
                16_000L,
                200));
        b.allowSynthesis(
                "minecraft:chorus_fruit",
                SynthesisCategory.NATURAL_MATERIAL,
                new SynthesisRequirement("material_synthesizer", 1, "minecraft:end/root"));

        // Manufactured tool: decomposition profile exists, but NEVER allowlisted for synthesis.
        b.addProfile(new MaterialProfile(
                "minecraft:iron_pickaxe",
                Map.of(BuiltinChemistry.IRON, 3_000L, BuiltinChemistry.CARBON, 500L),
                12_000L,
                24_000L,
                300));

        b.addFluidProfile(new FluidProfile(
                "minecraft:water",
                1_000,
                Map.of(BuiltinChemistry.WATER, 1_000L),
                4_000L,
                8_000L,
                100));
        b.allowSynthesis("minecraft:water", SynthesisCategory.FLUID);

        // Environment sources: open-air oxygen and subterranean cavern vapor.
        b.addEnvironmentSource(new EnvironmentSource(
                SubstanceId.parse("rematter:air_oxygen"),
                Map.of(BuiltinChemistry.OXYGEN, 200L),
                16_000L,
                800,
                Set.of("minecraft:overworld"),
                true));
        b.addEnvironmentSource(new EnvironmentSource(
                SubstanceId.parse("rematter:cavern_vapor"),
                Map.of(BuiltinChemistry.STEAM, 250L),
                12_000L,
                600,
                Set.of("minecraft:overworld"),
                false));

        return b.build();
    }

    @Test
    void discoveryLensInspectorMasksUnresearchedMatterRevealsKnownTracesAndFlagsPrimalSignatures() {
        ChemistryRegistry reg = testRegistry();

        // Primal item always reports Primal signature without ordinary matter breakdown.
        var primal = DiscoveryLensInspector.inspect(
                reg,
                "rematter:deepslate_primal_ore",
                false,
                1,
                false,
                Map.of(),
                true,
                true,
                false,
                k -> false);
        assertTrue(primal.primalSignature());
        assertFalse(primal.unanalyzed());
        assertEquals(SynthesisStatus.NONE, primal.synthesisStatus());

        // Unresearched allowlisted item hides all traces and marks unanalyzed + synthesis locked.
        var lockedGlass = DiscoveryLensInspector.inspect(
                reg,
                "minecraft:glass",
                false,
                1,
                false,
                Map.of(BuiltinChemistry.SILICA, 1_000L),
                true,
                true,
                false,
                k -> false);
        assertTrue(lockedGlass.unanalyzed());
        assertTrue(lockedGlass.knownTraces().isEmpty());
        assertEquals(1, lockedGlass.hiddenTraceCount());
        assertEquals(SynthesisStatus.SYNTHESIS_LOCKED, lockedGlass.synthesisStatus());

        // Partial substance knowledge on decomposition-only damaged tool reveals known IRON, masks CARBON, stays DECOMPOSITION_ONLY.
        var partialSalvage = DiscoveryLensInspector.inspect(
                reg,
                "minecraft:iron_pickaxe",
                false,
                1,
                true,
                Map.of(BuiltinChemistry.IRON, 1_500L, BuiltinChemistry.CARBON, 250L),
                true,
                true,
                false,
                BuiltinChemistry.IRON::equals);
        assertFalse(partialSalvage.unanalyzed());
        assertTrue(partialSalvage.durabilityScaled());
        assertEquals(1, partialSalvage.knownTraces().size());
        assertEquals(BuiltinChemistry.IRON, partialSalvage.knownTraces().getFirst().key());
        assertEquals(1, partialSalvage.hiddenTraceCount());
        assertEquals(SynthesisStatus.DECOMPOSITION_ONLY, partialSalvage.synthesisStatus());

        // Unlocked synthesis item reveals all traces and reports SYNTHESIS_UNLOCKED.
        var unlockedGlass = DiscoveryLensInspector.inspect(
                reg,
                "minecraft:glass",
                false,
                1,
                false,
                Map.of(BuiltinChemistry.SILICA, 1_000L),
                true,
                true,
                true,
                k -> false);
        assertFalse(unlockedGlass.unanalyzed());
        assertEquals(1, unlockedGlass.knownTraces().size());
        assertEquals(0, unlockedGlass.hiddenTraceCount());
        assertEquals(SynthesisStatus.SYNTHESIS_UNLOCKED, unlockedGlass.synthesisStatus());

        // Atmosphere inspection filters open-air sources underground and masks unknown yields.
        var caveAtmo = DiscoveryLensInspector.inspectAtmosphere(
                reg, "minecraft:overworld", false, true, true, Set.of());
        assertEquals(1, caveAtmo.size());
        assertEquals("rematter:cavern_vapor", caveAtmo.getFirst().source().id().toString());
        assertTrue(caveAtmo.getFirst().knownYields().isEmpty());
        assertEquals(1, caveAtmo.getFirst().hiddenYieldCount());

        var skyAtmo = DiscoveryLensInspector.inspectAtmosphere(
                reg, "minecraft:overworld", true, true, true, Set.of(BuiltinChemistry.OXYGEN));
        assertEquals(2, skyAtmo.size());
        var airEntry = skyAtmo.stream()
                .filter(e -> e.source().id().toString().equals("rematter:air_oxygen"))
                .findFirst()
                .orElseThrow();
        assertEquals(1, airEntry.knownYields().size());
        assertEquals(0, airEntry.hiddenYieldCount());
    }

    @Test
    void terminalBatchPlannerBuildsMultiStepChainsReusesCoProductsAndPreventsCycles() {
        ChemistryRegistry reg = testRegistry();

        // Multi-step chain: 1x minecraft:glass requires 1,000 SILICA -> silica_synthesis (1,000 SILICON in stock + 1,000 OXYGEN)
        // -> 5 batches of air_oxygen on ATMOSPHERIC_COLLECTOR at half base FE (8,000 FE per batch instead of 16,000 FE).
        var fullNet = new TerminalBatchPlanner.NetworkContext(
                Map.of(BuiltinChemistry.SILICON, 1_000L),
                EnumSet.of(MachineKind.ATMOSPHERIC_COLLECTOR, MachineKind.REACTOR, MachineKind.SYNTHESIZER),
                0,
                1,
                "minecraft:overworld",
                EnumSet.of(MachineKind.ATMOSPHERIC_COLLECTOR, MachineKind.REACTOR),
                item -> true,
                sub -> true,
                adv -> true);

        var glassPlan = TerminalBatchPlanner.plan(
                reg, TerminalBatchPlanner.TargetKind.SYNTHESIS_ITEM, "minecraft:glass", "", 1, fullNet);
        assertEquals(TerminalBatchPlanner.Status.READY, glassPlan.status());
        assertTrue(glassPlan.executable());
        assertEquals(3, glassPlan.steps().size());

        var step0 = glassPlan.steps().get(0);
        assertEquals(TerminalBatchPlanner.StepKind.ENVIRONMENT, step0.kind());
        assertEquals(MachineKind.ATMOSPHERIC_COLLECTOR, step0.preferredMachine());
        assertEquals("rematter:air_oxygen", step0.choiceId());
        assertEquals(5, step0.batches());
        assertEquals(8_000L, step0.energyPerBatch());

        var step1 = glassPlan.steps().get(1);
        assertEquals(TerminalBatchPlanner.StepKind.PROCESS, step1.kind());
        assertEquals("rematter:silica_synthesis", step1.choiceId());
        assertEquals(1, step1.batches());

        var step2 = glassPlan.steps().get(2);
        assertEquals(TerminalBatchPlanner.StepKind.SYNTHESIS_ITEM, step2.kind());
        assertEquals(MachineKind.SYNTHESIZER, step2.preferredMachine());
        assertEquals("minecraft:glass", step2.choiceId());
        assertEquals(1, step2.batches());

        // Co-product reuse: 1,000 SOIL separated once yields 1,000 SILICA + 1,000 WATER + 1,000 CARBON;
        // requesting 1,000 mB minecraft:water uses soil_separation without infinite loops.
        var soilNet = new TerminalBatchPlanner.NetworkContext(
                Map.of(BuiltinChemistry.SOIL, 1_000L),
                EnumSet.of(MachineKind.REACTOR, MachineKind.SYNTHESIZER),
                0,
                1,
                "minecraft:overworld",
                Set.of(),
                item -> true,
                sub -> true,
                adv -> true);
        var waterPlan = TerminalBatchPlanner.plan(
                reg, TerminalBatchPlanner.TargetKind.SYNTHESIS_FLUID, "minecraft:water", "", 1, soilNet);
        assertEquals(TerminalBatchPlanner.Status.READY, waterPlan.status());
        assertEquals(2, waterPlan.steps().size());
        assertEquals("rematter:soil_separation", waterPlan.steps().get(0).choiceId());

        // Cycle safety: reversible water_electrolysis <-> water_synthesis with zero stock in the Nether (no Overworld vapor)
        // must terminate cleanly with MISSING_INPUTS instead of recursing forever.
        var emptyNetherNet = new TerminalBatchPlanner.NetworkContext(
                Map.of(),
                EnumSet.of(MachineKind.REACTOR, MachineKind.SYNTHESIZER),
                0,
                1,
                "minecraft:the_nether",
                Set.of(),
                item -> true,
                sub -> true,
                adv -> true);
        var missingPlan = TerminalBatchPlanner.plan(
                reg, TerminalBatchPlanner.TargetKind.SYNTHESIS_FLUID, "minecraft:water", "", 1, emptyNetherNet);
        assertEquals(TerminalBatchPlanner.Status.MISSING_INPUTS, missingPlan.status());
        assertFalse(missingPlan.missingInputs().isEmpty());
    }

    @Test
    void terminalBatchPlannerEnforcesSynthesisAllowlistResearchPressureFieldAndAdvancementGates() {
        ChemistryRegistry reg = testRegistry();
        var baseNet = new TerminalBatchPlanner.NetworkContext(
                Map.of(BuiltinChemistry.CARBON, 8_000L, BuiltinChemistry.WATER, 4_000L, BuiltinChemistry.IRON, 8_000L),
                EnumSet.of(MachineKind.INDUSTRIAL_REACTOR, MachineKind.SYNTHESIZER),
                0,
                1,
                "minecraft:overworld",
                Set.of(),
                item -> !"minecraft:glass".equals(item),
                sub -> true,
                adv -> false);

        // 1. Manufactured item with decomposition profile is strictly FORBIDDEN_TARGET.
        var pickaxePlan = TerminalBatchPlanner.plan(
                reg, TerminalBatchPlanner.TargetKind.SYNTHESIS_ITEM, "minecraft:iron_pickaxe", "", 1, baseNet);
        assertEquals(TerminalBatchPlanner.Status.FORBIDDEN_TARGET, pickaxePlan.status());
        assertTrue(reg.profile("minecraft:iron_pickaxe").isPresent());
        assertFalse(TerminalBatchPlanner.listTargets(reg, id -> true, id -> true, id -> true, k -> true, false)
                .stream()
                .anyMatch(t -> t.id().equals("minecraft:iron_pickaxe")));

        // 2. Unresearched allowlisted item returns REQUIRES_RESEARCH.
        var unresearchedGlass = TerminalBatchPlanner.plan(
                reg, TerminalBatchPlanner.TargetKind.SYNTHESIS_ITEM, "minecraft:glass", "", 1, baseNet);
        assertEquals(TerminalBatchPlanner.Status.REQUIRES_RESEARCH, unresearchedGlass.status());

        // 3. Advancement-gated target without advancement returns REQUIRES_ADVANCEMENT.
        var chorusBlocked = TerminalBatchPlanner.plan(
                reg, TerminalBatchPlanner.TargetKind.SYNTHESIS_ITEM, "minecraft:chorus_fruit", "", 1, baseNet);
        assertEquals(TerminalBatchPlanner.Status.REQUIRES_ADVANCEMENT, chorusBlocked.status());

        // 4. Diamond without Pressure Module (tier 1 < required tier 2) returns REQUIRES_PRESSURE.
        var diamondNoPressure = TerminalBatchPlanner.plan(
                reg, TerminalBatchPlanner.TargetKind.SYNTHESIS_ITEM, "minecraft:diamond", "", 1, baseNet);
        assertEquals(TerminalBatchPlanner.Status.REQUIRES_PRESSURE, diamondNoPressure.status());

        // 5. Diamond WITH Pressure Module tier 2, but Industrial Reactor has 0 Field Coils (< minField 4) -> REQUIRES_FIELD.
        var pressureOnlyNet = new TerminalBatchPlanner.NetworkContext(
                Map.of(BuiltinChemistry.CARBON, 8_000L),
                EnumSet.of(MachineKind.INDUSTRIAL_REACTOR, MachineKind.SYNTHESIZER),
                2,
                2,
                "minecraft:overworld",
                Set.of(),
                item -> true,
                sub -> true,
                adv -> true);
        var diamondLowField = TerminalBatchPlanner.plan(
                reg, TerminalBatchPlanner.TargetKind.SYNTHESIS_ITEM, "minecraft:diamond", "", 1, pressureOnlyNet);
        assertEquals(TerminalBatchPlanner.Status.REQUIRES_FIELD, diamondLowField.status());

        // 6. Diamond WITH Pressure Module tier 2 AND 4 Field Coils succeeds!
        var fullIndustrialNet = new TerminalBatchPlanner.NetworkContext(
                Map.of(BuiltinChemistry.CARBON, 8_000L),
                EnumSet.of(MachineKind.INDUSTRIAL_REACTOR, MachineKind.SYNTHESIZER),
                4,
                2,
                "minecraft:overworld",
                Set.of(),
                item -> true,
                sub -> true,
                adv -> true);
        var diamondReady = TerminalBatchPlanner.plan(
                reg, TerminalBatchPlanner.TargetKind.SYNTHESIS_ITEM, "minecraft:diamond", "", 1, fullIndustrialNet);
        assertEquals(TerminalBatchPlanner.Status.READY, diamondReady.status());
        assertEquals(2, diamondReady.steps().size());
        assertEquals("rematter:diamond_crystallization", diamondReady.steps().get(0).choiceId());
        assertEquals(MachineKind.INDUSTRIAL_REACTOR, diamondReady.steps().get(0).preferredMachine());
        assertEquals("minecraft:diamond", diamondReady.steps().get(1).choiceId());

        // 7. Excessive batch request fails closed.
        var overflow = TerminalBatchPlanner.plan(
                reg,
                TerminalBatchPlanner.TargetKind.SYNTHESIS_ITEM,
                "minecraft:diamond",
                "",
                TerminalBatchPlanner.MAX_REQUEST_BATCHES + 1,
                fullIndustrialNet);
        assertEquals(TerminalBatchPlanner.Status.BATCH_LIMIT_EXCEEDED, overflow.status());
    }

    @Test
    void coveredWorkersUseTheShelteredCo2SourceOrElectrolyzeWaterForOxygen() {
        var builder = ChemistryRegistry.builder();
        var base = BuiltinChemistry.create();
        base.substances().forEach(builder::addSubstance);
        base.processes().forEach(builder::addProcess);
        var overworld = Set.of("minecraft:overworld");
        builder.addEnvironmentSource(new EnvironmentSource(SubstanceId.parse("rematter:air_trace_co2"),
                Map.of(BuiltinChemistry.OXYGEN, 150L, BuiltinChemistry.CARBON_DIOXIDE, 50L), 16_000, 600, overworld, true));
        builder.addEnvironmentSource(new EnvironmentSource(SubstanceId.parse("rematter:cavern_vapor"),
                Map.of(BuiltinChemistry.STEAM, 250L, BuiltinChemistry.CARBON_DIOXIDE, 50L), 12_000, 600, overworld, false));
        var registry = builder.build();
        var collector = new TerminalBatchPlanner.NetworkContext(Map.of(), Set.of(MachineKind.ATMOSPHERIC_COLLECTOR),
                0, 1, "minecraft:overworld", Set.of(), id -> true, key -> true, id -> true);
        var co2 = TerminalBatchPlanner.plan(registry, TerminalBatchPlanner.TargetKind.SUBSTANCE,
                "rematter:carbon_dioxide", "gas", 100, collector);
        assertEquals(TerminalBatchPlanner.Status.READY, co2.status());
        assertEquals(List.of(new TerminalBatchPlanner.PlanStep(TerminalBatchPlanner.StepKind.ENVIRONMENT,
                MachineKind.ATMOSPHERIC_COLLECTOR, "rematter:cavern_vapor", 2, 6_000)), co2.steps());

        var noSky = TerminalBatchPlanner.plan(registry, TerminalBatchPlanner.TargetKind.SUBSTANCE,
                "rematter:oxygen", "gas", 100, collector);
        assertEquals(TerminalBatchPlanner.Status.REQUIRES_OPEN_AIR, noSky.status());
        assertTrue(noSky.missingMachines().isEmpty());

        var processor = new TerminalBatchPlanner.NetworkContext(Map.of(BuiltinChemistry.WATER, 1_000L), Set.of(MachineKind.REACTOR),
                0, 1, "minecraft:overworld", Set.of(), id -> true, key -> true, id -> true);
        var oxygen = TerminalBatchPlanner.plan(registry, TerminalBatchPlanner.TargetKind.SUBSTANCE,
                "rematter:oxygen", "gas", 100, processor);
        assertEquals(TerminalBatchPlanner.Status.READY, oxygen.status());
        assertEquals("rematter:water_electrolysis", oxygen.steps().getFirst().choiceId());
    }

    @Test
    void atmosphericCollectorKindExposesOpenTopBenchAndEnvironmentRole() {
        assertTrue(MachineKind.ATMOSPHERIC_COLLECTOR.openTop());
        assertTrue(MachineKind.ATMOSPHERIC_COLLECTOR.selectable());
        assertTrue(MachineKind.ATMOSPHERIC_COLLECTOR.processes());
        assertTrue(MachineKind.ATMOSPHERIC_COLLECTOR.supportsNetworkJobs());
        assertFalse(MachineKind.ATMOSPHERIC_COLLECTOR.synthesis());
        assertFalse(MachineKind.ATMOSPHERIC_COLLECTOR.generator());
        assertEquals("atmospheric_collector", MachineKind.ATMOSPHERIC_COLLECTOR.id());
    }
}
