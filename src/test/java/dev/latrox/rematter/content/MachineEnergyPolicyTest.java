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
                MachineKind.REACTOR, MachineKind.PREPARATOR, MachineKind.SINTER_FURNACE, MachineKind.ATMOSPHERIC_COLLECTOR)) {
            assertEquals(32_000, MachineEnergyPolicy.baseEnergyCapacity(compact));
            assertEquals(32_000, MachineEnergyPolicy.energyCapacity(compact, 0));
            assertEquals(64_000, MachineEnergyPolicy.energyCapacity(compact, 1));
            assertEquals(128_000, MachineEnergyPolicy.energyCapacity(compact, 2));
            assertEquals(256_000, MachineEnergyPolicy.energyCapacity(compact, 3));
            assertEquals(512_000, MachineEnergyPolicy.energyCapacity(compact, 4));
            assertEquals(512_000, MachineEnergyPolicy.energyCapacity(compact, 9));
        }
        for (var industrial : List.of(MachineKind.INDUSTRIAL_REACTOR, MachineKind.INDUSTRIAL_PREPARATOR,
                MachineKind.INDUSTRIAL_DECOMPOSER, MachineKind.INDUSTRIAL_SYNTHESIZER)) {
            assertEquals(128_000, MachineEnergyPolicy.baseEnergyCapacity(industrial));
            assertEquals(128_000, MachineEnergyPolicy.energyCapacity(industrial, 0));
            assertEquals(256_000, MachineEnergyPolicy.energyCapacity(industrial, 1));
            assertEquals(512_000, MachineEnergyPolicy.energyCapacity(industrial, 2));
            assertEquals(1_024_000, MachineEnergyPolicy.energyCapacity(industrial, 3));
            assertEquals(2_048_000, MachineEnergyPolicy.energyCapacity(industrial, 4));
        }
        assertEquals(32_000, MachineEnergyPolicy.energyCapacity(MachineKind.CRYSTAL_GENERATOR, 4));
        assertEquals(128_000, MachineEnergyPolicy.energyCapacity(MachineKind.INDUSTRIAL_CRYSTAL_GENERATOR, 4));
        assertEquals(128_000, MachineEnergyPolicy.energyCapacity(MachineKind.PRIMAL_CONVERTER, 4));
        for (var unpowered : List.of(MachineKind.DRIVE, MachineKind.TERMINAL, MachineKind.NETWORK_INTERFACE, MachineKind.HAND_PREPARATOR)) {
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
        assertEquals(2_400_000L, full);
        assertEquals(MachineEnergyPolicy.MAX_BATCH_ENERGY, MachineEnergyPolicy.combined(MachineEnergyPolicy.MAX_BATCH_ENERGY, 4, 4));
        long industrialPreparation = MachineEnergyPolicy.combined(MachineEnergyPolicy.scaled(PreparationWork.MAX_ENERGY,
                PreparationTier.energyMultiplier(MachineKind.INDUSTRIAL_PREPARATOR)), 4, 4);
        assertTrue(industrialPreparation <= MachineBlockEntity.MAX_ENERGY_CAPACITY);
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
