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
    @Test void speedUpgradesMultiplyTheBaseRateUpToTheSlotLimit() {
        assertEquals(2, MachineEnergyPolicy.speed(2, MachineEnergyPolicy.Upgrade.NONE, 4));
        assertEquals(4, MachineEnergyPolicy.speed(2, MachineEnergyPolicy.Upgrade.SPEED, 1));
        assertEquals(10, MachineEnergyPolicy.speed(2, MachineEnergyPolicy.Upgrade.SPEED, 9));
        assertEquals(1, MachineEnergyPolicy.speed(1, MachineEnergyPolicy.Upgrade.EFFICIENCY, 4));
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

    @Test void theLargestCatalogueBatchFitsTheBufferWithEverySpeedUpgrade() {
        long full = MachineEnergyPolicy.cost(MachineEnergyPolicy.MAX_BATCH_ENERGY, MachineEnergyPolicy.Upgrade.SPEED, 4, MachineKind.SYNTHESIZER, null);
        assertEquals(MachineBlockEntity.ENERGY_CAPACITY, full);
        long industrialPreparation = MachineEnergyPolicy.cost(MachineEnergyPolicy.scaled(PreparationWork.MAX_ENERGY,
                PreparationTier.energyMultiplier(MachineKind.INDUSTRIAL_PREPARATOR)), MachineEnergyPolicy.Upgrade.SPEED, 4, MachineKind.INDUSTRIAL_PREPARATOR, null);
        assertTrue(industrialPreparation <= MachineBlockEntity.ENERGY_CAPACITY);
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

    @Test void everyShippedBatchFitsTheBufferWithEverySpeedUpgrade() throws IOException {
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
                assertTrue(json.get("energy").getAsLong() <= PreparationWork.MAX_ENERGY, path);
            } else for (String field : List.of("energy", "decomposition_energy", "synthesis_energy"))
                if (json.has(field)) assertTrue(json.get(field).getAsLong() <= MachineEnergyPolicy.MAX_BATCH_ENERGY, path);
            checked++;
        }
        assertTrue(checked > 100, "Shipped catalogue documents were not found");
    }
}
