package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.ChemistryFixtures;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.latrox.rematter.chemistry.BuiltinChemistry;
import dev.latrox.rematter.chemistry.MaterialProfile;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.ShippedChemistry;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.content.preparation.PreparationTier;
import dev.latrox.rematter.content.preparation.PreparationWork;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MachineEnergyPolicyTest {
    @Test void combinedSavingsNeverExceedTheGlobalCapAcrossEverySupportedCount() {
        for(long base:new long[]{1,2,3,4,19,20,99,100,16000,1200000,Long.MAX_VALUE})
            for(int coils=1;coils<IndustrialPerformance.MAX_PARTS_PER_TYPE;coils++)
                for(int speed=0;speed<=4;speed++)for(int efficiency=0;efficiency<=4;efficiency++) {
                    var hardware=new IndustrialPerformance(0,0,0,coils,1);
                    long reference=MachineEnergyPolicy.combined(base,speed,0);
                    long price=MachineEnergyPolicy.cost(base,speed,efficiency,MachineKind.INDUSTRIAL_REACTOR,hardware);
                    assertTrue(price>=base-base/4,"Below 75% floor: base="+base+" s="+speed+" e="+efficiency);
                    assertTrue(price<=reference,"Above unmatched speed reference");
                }
        var maximum=new IndustrialPerformance(0,0,0,IndustrialPerformance.MAX_PARTS_PER_TYPE-1,1);
        assertEquals(12800,MachineEnergyPolicy.cost(16000,0,4,MachineKind.INDUSTRIAL_REACTOR,maximum));
        assertEquals(16000,MachineEnergyPolicy.cost(16000,4,4,MachineKind.INDUSTRIAL_REACTOR,maximum));
    }
    @Test void mekanismStyleCombinedPricingCancelsSpeedSurchargeOneToOneAndPreservesFloor() {
        assertEquals(1000, MachineEnergyPolicy.combined(1000, 0, 0));
        assertEquals(1250, MachineEnergyPolicy.combined(1000, 1, 0));
        assertEquals(2000, MachineEnergyPolicy.combined(1000, 4, 0));
        assertEquals(1000, MachineEnergyPolicy.combined(1000, 1, 1));
        assertEquals(1000, MachineEnergyPolicy.combined(1000, 4, 4));
        assertEquals(1250, MachineEnergyPolicy.combined(1000, 3, 2));
        assertEquals(1500, MachineEnergyPolicy.combined(1000, 4, 2));
        assertEquals(950, MachineEnergyPolicy.combined(1000, 0, 1));
        assertEquals(900, MachineEnergyPolicy.combined(1000, 2, 4));
        assertEquals(800, MachineEnergyPolicy.combined(1000, 0, 4));
        assertEquals(100, MachineEnergyPolicy.costPercent(4, 4));
        assertEquals(200, MachineEnergyPolicy.costPercent(4, 0));
        assertEquals(80, MachineEnergyPolicy.costPercent(0, 4));
        assertEquals(90, MachineEnergyPolicy.costPercent(2, 4));
        for (int s = 0; s <= 4; s++) {
            for (int e = 0; e < 4; e++) {
                assertTrue(MachineEnergyPolicy.combined(16_000, s, e + 1) <= MachineEnergyPolicy.combined(16_000, s, e));
            }
        }
    }
    @Test void energyCapacityScalesPerTierAndDoublesWithEfficiencyUpgrades() {
        for (var compact : List.of(MachineKind.ANALYZER, MachineKind.DECOMPOSER, MachineKind.SYNTHESIZER,
                MachineKind.REACTOR, MachineKind.PREPARATOR, MachineKind.SINTER_FURNACE,
                MachineKind.ATMOSPHERIC_COLLECTOR, MachineKind.DEEP_EXTRACTOR)) {
            assertEquals(64_000, MachineEnergyPolicy.baseEnergyCapacity(compact));
            assertEquals(64_000, MachineEnergyPolicy.energyCapacity(compact, 0));
            assertEquals(128_000, MachineEnergyPolicy.energyCapacity(compact, 1));
            assertEquals(256_000, MachineEnergyPolicy.energyCapacity(compact, 2));
            assertEquals(512_000, MachineEnergyPolicy.energyCapacity(compact, 3));
            assertEquals(1_024_000, MachineEnergyPolicy.energyCapacity(compact, 4));
            assertEquals(1_024_000, MachineEnergyPolicy.energyCapacity(compact, 9));
        }
        for (var industrial : List.of(MachineKind.INDUSTRIAL_REACTOR, MachineKind.INDUSTRIAL_PREPARATOR,
                MachineKind.INDUSTRIAL_DECOMPOSER, MachineKind.INDUSTRIAL_SYNTHESIZER)) {
            assertEquals(512_000, MachineEnergyPolicy.baseEnergyCapacity(industrial));
            assertEquals(512_000, MachineEnergyPolicy.energyCapacity(industrial, 0));
            assertEquals(1_024_000, MachineEnergyPolicy.energyCapacity(industrial, 1));
            assertEquals(2_048_000, MachineEnergyPolicy.energyCapacity(industrial, 2));
            assertEquals(4_096_000, MachineEnergyPolicy.energyCapacity(industrial, 3));
            assertEquals(8_192_000, MachineEnergyPolicy.energyCapacity(industrial, 4));
        }
        assertEquals(2_000_000, MachineEnergyPolicy.baseEnergyCapacity(MachineKind.TRANSMUTATION_REACTOR));
        assertEquals(32_000_000, MachineEnergyPolicy.energyCapacity(MachineKind.TRANSMUTATION_REACTOR, 4));
        assertEquals(8_000_000, MachineEnergyPolicy.baseEnergyCapacity(MachineKind.PRIMAL_REACTOR));
        assertEquals(128_000_000, MachineEnergyPolicy.energyCapacity(MachineKind.PRIMAL_REACTOR, 4));
        assertEquals(64_000, MachineEnergyPolicy.energyCapacity(MachineKind.CRYSTAL_GENERATOR, 4));
        assertEquals(512_000, MachineEnergyPolicy.energyCapacity(MachineKind.INDUSTRIAL_CRYSTAL_GENERATOR, 4));
        assertEquals(512_000, MachineEnergyPolicy.energyCapacity(MachineKind.PRIMAL_CONVERTER, 4));
        for (var unpowered : List.of(MachineKind.DRIVE, MachineKind.TERMINAL, MachineKind.NETWORK_INTERFACE,
                MachineKind.HAND_PREPARATOR, MachineKind.ENERGY_MATRIX, MachineKind.ENERGY_REACTOR)) {
            assertEquals(0, MachineEnergyPolicy.energyCapacity(unpowered, 4));
        }
    }
    @Test void speedUpgradesMultiplyTheBaseRateUpToTheSlotLimit() {
        assertEquals(2, MachineEnergyPolicy.speed(2, MachineEnergyPolicy.Upgrade.NONE, 4));
        assertEquals(4, MachineEnergyPolicy.speed(2, MachineEnergyPolicy.Upgrade.SPEED, 1));
        assertEquals(8, MachineEnergyPolicy.speed(2, MachineEnergyPolicy.Upgrade.SPEED, 2));
        assertEquals(14, MachineEnergyPolicy.speed(2, MachineEnergyPolicy.Upgrade.SPEED, 3));
        assertEquals(20, MachineEnergyPolicy.speed(2, MachineEnergyPolicy.Upgrade.SPEED, 4));
        assertEquals(20, MachineEnergyPolicy.speed(2, MachineEnergyPolicy.Upgrade.SPEED, 9));
        assertEquals(1, MachineEnergyPolicy.speed(1, MachineEnergyPolicy.Upgrade.EFFICIENCY, 4));
    }

    @Test void tickByTickEnergySumsToTheExactBatchPriceWithoutRoundingLoss() {
        for (long total : new long[]{0, 1, 7, 100, 1_000, 16_000, 921_600, 3_686_400}) {
            for (int duration : new int[]{3, 17, 200, 320, 800}) {
                for (int speed : new int[]{1, 2, 4, 7, 10, 20, 40}) {
                    int progress = 0;
                    long paid = 0;
                    while (progress < duration) {
                        long due = MachineEnergyPolicy.stepEnergy(total, progress, speed, duration, paid);
                        assertTrue(due >= 0);
                        paid += due;
                        progress = (int) Math.min(duration, (long) progress + speed);
                    }
                    assertEquals(total, paid, "total=" + total + " duration=" + duration + " speed=" + speed);
                    assertEquals(0, MachineEnergyPolicy.stepEnergy(total, duration, speed, duration, paid));
                }
            }
        }
    }

    @Test void speedRaisesTheTotalPriceAndNeverRoundsItDown() {
        assertEquals(1000, MachineEnergyPolicy.cost(1000, MachineEnergyPolicy.Upgrade.NONE, 0, MachineKind.SYNTHESIZER, null));
        assertEquals(1250, MachineEnergyPolicy.cost(1000, MachineEnergyPolicy.Upgrade.SPEED, 1, MachineKind.SYNTHESIZER, null));
        assertEquals(2000, MachineEnergyPolicy.cost(1000, MachineEnergyPolicy.Upgrade.SPEED, 4, MachineKind.SYNTHESIZER, null));
        assertEquals(2000, MachineEnergyPolicy.cost(1000, MachineEnergyPolicy.Upgrade.SPEED, 64, MachineKind.SYNTHESIZER, null));
        for (long base = 1; base < 40; base++)
            assertTrue(MachineEnergyPolicy.cost(base, MachineEnergyPolicy.Upgrade.SPEED, 1, MachineKind.REACTOR, null) >= base);
    }

    @Test void efficiencySavesAtMostTwentyPercent() {
        assertEquals(950, MachineEnergyPolicy.cost(1000, MachineEnergyPolicy.Upgrade.EFFICIENCY, 1, MachineKind.DECOMPOSER, null));
        assertEquals(800, MachineEnergyPolicy.cost(1000, MachineEnergyPolicy.Upgrade.EFFICIENCY, 9, MachineKind.DECOMPOSER, null));
    }

    @Test void industrialSupportsApplyAfterUpgradesAndTierMultipliersSaturate() {
        var supports = new IndustrialPerformance(0, 0, 0, 2, 1);
        long upgraded = MachineEnergyPolicy.cost(1000, MachineEnergyPolicy.Upgrade.SPEED, 4, MachineKind.INDUSTRIAL_REACTOR, null);
        assertEquals(supports.energyCost(MachineKind.INDUSTRIAL_REACTOR, upgraded),
                MachineEnergyPolicy.cost(1000, MachineEnergyPolicy.Upgrade.SPEED, 4, MachineKind.INDUSTRIAL_REACTOR, supports));
        assertEquals(2000, MachineEnergyPolicy.scaled(1000, 2));
        assertEquals(0, MachineEnergyPolicy.scaled(1000, 0));
        assertEquals(Long.MAX_VALUE, MachineEnergyPolicy.scaled(Long.MAX_VALUE / 2 + 1, 2));
        assertThrows(IllegalArgumentException.class, () -> MachineEnergyPolicy.scaled(-1, 2));
    }

    @Test void theLargestCatalogueBatchFitsPerTickAndUpgradedCapacities() {
        long full = MachineEnergyPolicy.combined(MachineEnergyPolicy.MAX_BATCH_ENERGY, 4, 0);
        assertEquals(32_000_000L, full);
        assertEquals(MachineEnergyPolicy.MAX_BATCH_ENERGY, MachineEnergyPolicy.combined(MachineEnergyPolicy.MAX_BATCH_ENERGY, 4, 4));
        long industrialPreparation = MachineEnergyPolicy.combined(MachineEnergyPolicy.scaled(PreparationWork.MAX_ENERGY,
                PreparationTier.energyMultiplier(MachineKind.INDUSTRIAL_PREPARATOR)), 4, 4);
        assertTrue(industrialPreparation <= MachineBlockEntity.MAX_ENERGY_CAPACITY);
    }

    @Test void primalCubeEnergyMatrixEnergyReactorAndPrimalReactorMathStayOverflowFree() {
        var primalCube = dev.latrox.rematter.energy.EnergyCubeTier.PRIMAL;
        assertEquals(4_096_000_000L, primalCube.capacity());
        assertEquals(4_194_304, primalCube.transferRate());
        assertEquals("4.10G", dev.latrox.rematter.client.Quantities.compact(4_096_000_000L));
        // Multi-tier Energy Matrix sums 64-bit capacity & transfer rate cleanly across mixed tiers
        long mixedCap = dev.latrox.rematter.energy.EnergyMatrixCellTier.BASIC.capacity()
                + dev.latrox.rematter.energy.EnergyMatrixCellTier.REINFORCED.capacity()
                + dev.latrox.rematter.energy.EnergyMatrixCellTier.RESONANT.capacity()
                + dev.latrox.rematter.energy.EnergyMatrixCellTier.PRIMAL.capacity();
        long mixedRate = dev.latrox.rematter.energy.EnergyMatrixProviderTier.BASIC.transferRate()
                + dev.latrox.rematter.energy.EnergyMatrixProviderTier.REINFORCED.transferRate()
                + dev.latrox.rematter.energy.EnergyMatrixProviderTier.RESONANT.transferRate()
                + dev.latrox.rematter.energy.EnergyMatrixProviderTier.PRIMAL.transferRate();
        var matrixPerf = IndustrialPerformance.energyMatrix(4, 4, mixedCap, mixedRate);
        assertEquals(37_440_000_000L, matrixPerf.matrixCapacity());
        assertEquals(22_282_240L, matrixPerf.matrixTransferRate());
        // Energy Reactor: Fuel Cores scale buffer + output, Induction Coils scale bandwidth + efficiency (capped at +25%),
        // Water cooling grants +20% FE & -15% fuel, Nitrogen Cryo Overdrive grants +30% FE & -20% fuel
        var reactorPerf = IndustrialPerformance.energyReactor(4, 10);
        assertEquals(2_048_000_000L, reactorPerf.reactorCapacity());
        assertEquals(10_485_760L, reactorPerf.reactorMaxOutputRate());
        assertEquals(25, EnergyReactorRecipes.coilBonusPercent(10));
        assertEquals(10, EnergyReactorRecipes.coilBonusPercent(2));
        long dryZeroFlux = EnergyReactorRecipes.effectiveOutputPerTick(EnergyReactorRecipes.CRYSTAL_FE_PER_CORE, 2, 0, 0, EnergyReactorRecipes.CoolantTier.NONE);
        long dryFullFlux = EnergyReactorRecipes.effectiveOutputPerTick(EnergyReactorRecipes.CRYSTAL_FE_PER_CORE, 2, 0, EnergyReactorRecipes.MAX_FLUX, EnergyReactorRecipes.CoolantTier.NONE);
        long waterFullFlux = EnergyReactorRecipes.effectiveOutputPerTick(EnergyReactorRecipes.CRYSTAL_FE_PER_CORE, 2, 0, EnergyReactorRecipes.MAX_FLUX, EnergyReactorRecipes.CoolantTier.WATER);
        long nitrogenFullFlux = EnergyReactorRecipes.effectiveOutputPerTick(EnergyReactorRecipes.CRYSTAL_FE_PER_CORE, 2, 0, EnergyReactorRecipes.MAX_FLUX, EnergyReactorRecipes.CoolantTier.NITROGEN);
        assertEquals(4_096L, dryZeroFlux);
        assertEquals(16_384L, dryFullFlux);
        assertEquals(19_660L, waterFullFlux);
        assertEquals(21_299L, nitrogenFullFlux);
        assertTrue(nitrogenFullFlux > waterFullFlux && waterFullFlux > dryFullFlux);
        assertEquals(170L, EnergyReactorRecipes.effectiveFuelCost(100L, 2, EnergyReactorRecipes.CoolantTier.WATER));
        assertEquals(160L, EnergyReactorRecipes.effectiveFuelCost(100L, 2, EnergyReactorRecipes.CoolantTier.NITROGEN));
        assertEquals(200L, EnergyReactorRecipes.effectiveFuelCost(100L, 2, EnergyReactorRecipes.CoolantTier.NONE));
        assertEquals(EnergyReactorRecipes.CoolantTier.WATER, EnergyReactorRecipes.CoolantTier.fromKey(EnergyReactorRecipes.WATER_COOLANT));
        assertEquals(EnergyReactorRecipes.CoolantTier.NITROGEN, EnergyReactorRecipes.CoolantTier.fromKey(EnergyReactorRecipes.NITROGEN_COOLANT));
        // Primal Reactor: real 3-phase collapse + 30% compression discount + stability scaling
        var raw = PrimalReactorRecipes.CONDENSATION_TIERS.getFirst();
        assertFalse(raw.inputs().isEmpty());
        assertEquals(3_920_000L, raw.effectiveEnergy(true));
        assertEquals(2_800L, raw.effectiveInputs(true).get(dev.latrox.rematter.chemistry.BuiltinChemistry.SILICA));
        assertEquals(100, PrimalReactorRecipes.speedMultiplierPercent(0));
        assertEquals(250, PrimalReactorRecipes.speedMultiplierPercent(PrimalReactorRecipes.MAX_STABILITY));
        assertEquals(1_000L, PrimalReactorRecipes.condensationOutput(1_000L, 0));
        assertEquals(1_500L, PrimalReactorRecipes.condensationOutput(1_000L, PrimalReactorRecipes.MAX_STABILITY));
    }

    @Test void endgameReactorAndPrimalBalancesAreThermodynamicallyClosedWithZeroInfiniteLoops() {
        // 1. Maximum Energy Reactor fuel efficiency multiplier stays strictly below 2.0x (preserving audit_crystal_fuel.py's <= 50% synthesis floor).
        double maxReactorMultiplier = (100.0 + EnergyReactorRecipes.MAX_COIL_BONUS_PERCENT + EnergyReactorRecipes.COOLANT_OUTPUT_BONUS_PERCENT)
                / (100.0 - EnergyReactorRecipes.COOLANT_FUEL_DISCOUNT_PERCENT);
        assertTrue(maxReactorMultiplier < 2.0, "Max reactor multiplier must stay < 2.0x to prevent crystal synthesis loops, got " + maxReactorMultiplier);

        // 2. No water-electrolysis infinite FE loop: hydrogen_oxygen is not an Energy Reactor fuel.
        assertTrue(EnergyReactorRecipes.MATTER_FUELS.stream().noneMatch(f -> f.id().equals("hydrogen_oxygen")),
                "Water-electrolysis hydrogen_oxygen loop must not exist");

        // 3. No Primal amplification loop & no Primal Reactor <-> Energy Reactor infinite FE loop.
        long maxPrimalOut = EnergyReactorRecipes.effectiveOutputPerTick(EnergyReactorRecipes.PRIMAL_FE_PER_CORE, 1, 10, EnergyReactorRecipes.MAX_FLUX, true);
        long minPrimalUse = EnergyReactorRecipes.effectiveFuelCost(EnergyReactorRecipes.PRIMAL_UR_PER_CORE_PER_TICK, 1, true);
        double maxFePerUr = (double) maxPrimalOut / minPrimalUse;
        double cheapestCollapseFePerUr = Double.MAX_VALUE;
        for (var tier : PrimalReactorRecipes.CONDENSATION_TIERS) {
            long maxYield = PrimalReactorRecipes.condensationOutput(tier.primalOutput(), PrimalReactorRecipes.MAX_STABILITY);
            long minEnergy = MachineEnergyPolicy.combined(tier.effectiveEnergy(true), 0, 4); // max -20% eff (floor -25%)
            long conservativeFloor = tier.effectiveEnergy(true) - tier.effectiveEnergy(true) / 4; // conservative 75% policy floor
            double minFePerUr = (double) Math.min(minEnergy, conservativeFloor) / maxYield;
            cheapestCollapseFePerUr = Math.min(cheapestCollapseFePerUr, minFePerUr);
            assertTrue(maxFePerUr < minFePerUr,
                    "Primal->FE loop in " + tier.id() + ": reactor yields " + maxFePerUr + " FE/Ur >= collapse floor " + minFePerUr);
            // Manifestable solid inputs alone must cost strictly more Primal than max condensation output (no Ur -> Solid -> Ur amplification!).
            long manifestUrCost = 0L;
            for (var entry : tier.effectiveInputs(true).entrySet()) {
                var grade = dev.latrox.rematter.primal.PrimalPricing.grade(entry.getKey().id());
                if (grade.isPresent() && entry.getKey().phase() == Phase.SOLID) {
                    manifestUrCost += grade.get().primalPerUnit * entry.getValue();
                }
            }
            if (manifestUrCost > 0L) {
                assertTrue(manifestUrCost > maxYield,
                        "Primal amplification loop in " + tier.id() + ": manifesting inputs costs " + manifestUrCost + " Ur <= output " + maxYield);
            }
        }

        // 4. No Primal-manifested Uranium -> Energy Reactor infinite FE loop.
        var uraniumFuel = EnergyReactorRecipes.MATTER_FUELS.stream().filter(f -> f.id().equals("uranium")).findFirst().orElseThrow();
        long maxUOut = EnergyReactorRecipes.effectiveOutputPerTick(uraniumFuel.fePerCorePerTick(), 1, 10, EnergyReactorRecipes.MAX_FLUX, true);
        long minUUse = EnergyReactorRecipes.effectiveFuelCost(uraniumFuel.inputsPerCorePerTick().values().iterator().next(), 1, true);
        double maxFePerU = (double) maxUOut / minUUse;
        var uGrade = dev.latrox.rematter.primal.PrimalPricing.grade(SubstanceId.parse("rematter:uranium")).orElseThrow();
        double minManifestUFe = uGrade.primalPerUnit * cheapestCollapseFePerUr + (uGrade.energy * 0.75 / 1000.0);
        assertTrue(maxFePerU < minManifestUFe,
                "Primal->Uranium->FE loop: reactor yields " + maxFePerU + " FE/u >= manifest floor " + minManifestUFe);

        // 5. No Primal-manifested Lithium -> Energy Reactor infinite FE loop.
        var liFuel = EnergyReactorRecipes.MATTER_FUELS.stream().filter(f -> f.id().equals("lithium_fusion")).findFirst().orElseThrow();
        long maxLiOut = EnergyReactorRecipes.effectiveOutputPerTick(liFuel.fePerCorePerTick(), 1, 10, EnergyReactorRecipes.MAX_FLUX, true);
        long minLiUse = EnergyReactorRecipes.effectiveFuelCost(liFuel.inputsPerCorePerTick().get(SubstanceKey.of("rematter:lithium", Phase.SOLID)), 1, true);
        double maxFePerLi = (double) maxLiOut / minLiUse;
        var liGrade = dev.latrox.rematter.primal.PrimalPricing.grade(SubstanceId.parse("rematter:lithium")).orElseThrow();
        double minManifestLiFe = liGrade.primalPerUnit * cheapestCollapseFePerUr + (liGrade.energy * 0.75 / 1000.0);
        assertTrue(maxFePerLi < minManifestLiFe,
                "Primal->Lithium->FE loop: reactor yields " + maxFePerLi + " FE/u >= manifest floor " + minManifestLiFe);

        // 6. Multi-crystal burner satisfies high FE/t demand without wasting partial crystal energy.
        var burner = new dev.latrox.rematter.content.generation.CrystalBurner();
        var ember = new dev.latrox.rematter.content.generation.CrystalFuelData.Fuel("rematter:ember_matrix_crystal", 1, 1_408L);
        var plan = burner.tickMulti(8_192L, ember, 64);
        assertEquals(6, plan.consumedItems());
        assertEquals(8_192L, plan.producedEnergy());
        assertEquals(6 * 1_408L - 8_192L, plan.nextRemaining());
        assertTrue(burner.apply(plan));
        assertEquals(256L, burner.remaining());
    }

    @Test void aCatalogueWithAnUnaffordableBatchIsRejectedAtLoad() {
        var copper = new SubstanceKey(SubstanceId.parse("rematter:copper"), Phase.SOLID);
        var affordable = new MaterialProfile("example:affordable", Map.of(copper, 1L), 10, MachineEnergyPolicy.MAX_BATCH_ENERGY, 20);
        assertDoesNotThrow(() -> MachineEnergyPolicy.requireFits(ChemistryFixtures.copyOf(BuiltinChemistry.create()).addProfile(affordable).build()));
        var expensive = new MaterialProfile("example:expensive", Map.of(copper, 1L), 10, MachineEnergyPolicy.MAX_BATCH_ENERGY + 1, 20);
        var registry = ChemistryFixtures.copyOf(BuiltinChemistry.create()).addProfile(expensive).build();
        var error = assertThrows(IllegalArgumentException.class, () -> MachineEnergyPolicy.requireFits(registry));
        assertTrue(error.getMessage().contains("example:expensive"));
    }

    @Test void everyShippedBatchFitsCatalogueLimitsAndSingleTickBaseBufferEvenAtMaxSpeed() throws IOException {
        assertDoesNotThrow(() -> MachineEnergyPolicy.requireFits(ShippedChemistry.standalone()));
        var documents = new ArrayList<Path>();
        for (var root : List.of(Path.of("src/main/resources/data"), Path.of("integrations/reforging/datapack/data")))
            try (var files = Files.walk(root)) { files.filter(file -> file.toString().endsWith(".json")).forEach(documents::add); }
        int checked = 0;
        for (var document : documents) {
            String path = document.toString().replace('\\', '/');
            if (!path.contains("/rematter/material_profile/") && !path.contains("/rematter/fluid_profile/")
                    && !path.contains("/rematter/process/") && !path.contains("/recipe/")) continue;
            if (!(JsonParser.parseString(Files.readString(document)) instanceof JsonObject json)) continue;
            if (path.contains("/recipe/")) {
                if (!json.has("type") || !json.get("type").getAsString().equals("rematter:preparation")) continue;
                long energy = json.get("energy").getAsLong();
                int ticks = json.get("ticks").getAsInt();
                assertTrue(energy <= PreparationWork.MAX_ENERGY, path);
                long maxStep = MachineEnergyPolicy.stepEnergy(MachineEnergyPolicy.combined(energy, 4, 0), 0, 10, ticks, 0);
                assertTrue(maxStep <= MatterLimits.COMPACT_MACHINE_ENERGY, "Single tick exceeds compact base buffer: " + path);
            } else {
                int ticks = json.has("ticks") ? json.get("ticks").getAsInt() : 0;
                for (String field : List.of("energy", "decomposition_energy", "synthesis_energy")) {
                    if (!json.has(field)) continue;
                    long energy = json.get(field).getAsLong();
                    assertTrue(energy <= MachineEnergyPolicy.MAX_BATCH_ENERGY, path);
                    if (ticks > 0) {
                        long maxStep = MachineEnergyPolicy.stepEnergy(MachineEnergyPolicy.combined(energy, 4, 0), 0, 10, ticks, 0);
                        assertTrue(maxStep <= MatterLimits.COMPACT_MACHINE_ENERGY, "Single tick exceeds compact base buffer: " + path + " (" + field + ")");
                    }
                }
            }
            checked++;
        }
        assertTrue(checked > 100, "Shipped catalogue documents were not found");
    }
}
