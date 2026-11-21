package dev.latrox.rematter.content;

import com.google.gson.JsonParser;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.ShippedChemistry;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.client.ProcessPresentation;
import dev.latrox.rematter.client.Quantities;
import dev.latrox.rematter.compat.ChemistryViewer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Unit coverage for JEI/EMI rate formatting, Curios tags, Auto-Eject config, cross-mod compat, new materials, and industrial controllers. */
class IntegrationsAndExpansionTest {
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void quantitiesFormatEnergyRateAndDurationWithTicks() {
        assertEquals(80L, Quantities.energyRate(8_000L, 100));
        assertEquals(334L, Quantities.energyRate(1_000L, 3));
        assertEquals(0L, Quantities.energyRate(0L, 100));
        assertEquals(0L, Quantities.energyRate(8_000L, 0));
        assertEquals("unit.rematter.energy_with_rate",
                Quantities.energyWithRate(8_000L, 100).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc ? tc.getKey() : "");
        assertEquals("unit.rematter.energy",
                Quantities.energyWithRate(0L, 100).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc ? tc.getKey() : "");
        assertEquals("unit.rematter.duration_with_ticks",
                Quantities.durationWithTicks(100).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc ? tc.getKey() : "");
    }

    @Test
    void curiosSlotTagsIncludeWirelessTerminalAndPortableEnergyCell() throws Exception {
        for (String slot : List.of("curio", "charm", "belt")) {
            Path path = RESOURCES.resolve("data/curios/tags/item/" + slot + ".json");
            assertTrue(Files.isRegularFile(path), "Missing Curios tag: " + path);
            var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            assertFalse(json.get("replace").getAsBoolean());
            Set<String> values = json.getAsJsonArray("values").asList().stream()
                    .map(e -> e.getAsString()).collect(Collectors.toSet());
            assertTrue(values.contains("rematter:wireless_matter_terminal"), slot);
            assertTrue(values.contains("rematter:portable_energy_cell"), slot);
        }
        Path entitySlots = RESOURCES.resolve("data/rematter/curios/entities/rematter.json");
        assertTrue(Files.isRegularFile(entitySlots), "Missing Curios entity slot declaration");
        var entityJson = JsonParser.parseString(Files.readString(entitySlots)).getAsJsonObject();
        Set<String> slots = entityJson.getAsJsonArray("slots").asList().stream()
                .map(e -> e.getAsString()).collect(Collectors.toSet());
        assertTrue(slots.contains("charm") && slots.contains("belt"));
    }

    @Test
    void configurationCardPreservesAutoEjectToggleSafely() {
        var enabled = new ConfigurationCardData.MachineSetup(
                MachineKind.SYNTHESIZER, 0, RedstoneMode.IGNORED, true, true, true, false,
                "minecraft:glass", MachineSettings.DEFAULT, MachineSoundLevel.HIGH, null, false, true);
        var card = ConfigurationCardData.fromMachine(enabled);
        assertTrue(card.machineSetup().orElseThrow().autoEject());

        var legacy = new ConfigurationCardData.MachineSetup(
                MachineKind.SYNTHESIZER, 0, RedstoneMode.IGNORED, true, true, true, false,
                "minecraft:glass", MachineSettings.DEFAULT);
        assertFalse(legacy.autoEject());
        assertFalse(ConfigurationCardData.fromMachine(legacy).machineSetup().orElseThrow().autoEject());
    }

    @Test
    void crossModCompatRecipesAndMekanismBrineSodiumAreValid() throws Exception {
        Path compatDir = RESOURCES.resolve("data/rematter/recipe/compat");
        for (String rel : List.of(
                "enderio/alloy_smelting/rhevon.json",
                "enderio/alloy_smelting/lumivex.json",
                "enderio/alloy_smelting/virelite.json",
                "enderio/alloy_smelting/diamond_lattice.json",
                "enderio/alloy_smelting/ender_conductor.json",
                "enderio/alloy_smelting/netherite_sheath.json",
                "ftbic/macerating/rhevon.json",
                "ftbic/macerating/lumivex.json",
                "ftbic/macerating/lapis.json",
                "ftbic/macerating/amethyst.json",
                "ftbic/macerating/emerald.json",
                "energizedpower/crusher/rhevon.json",
                "energizedpower/crusher/lumivex.json",
                "energizedpower/crusher/diamond.json",
                "energizedpower/crusher/netherite.json",
                "energizedpower/crusher/lapis.json",
                "energizedpower/crusher/amethyst.json",
                "energizedpower/crusher/emerald.json",
                "mekanism/crushing/amethyst.json")) {
            Path path = compatDir.resolve(rel);
            assertTrue(Files.isRegularFile(path), "Missing compat recipe: " + rel);
            var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            var conditions = json.getAsJsonArray("neoforge:conditions");
            assertNotNull(conditions, rel);
            assertEquals("neoforge:mod_loaded", conditions.get(0).getAsJsonObject().get("type").getAsString(), rel);
        }

        var registry = ShippedChemistry.standalone();
        assertTrue(registry.fluidProfile("mekanism:brine").isPresent());
        assertTrue(registry.fluidProfile("mekanism:sodium").isPresent());
        assertTrue(registry.synthesisPolicy().entries().containsKey("rematter:brine"));
        assertTrue(registry.synthesisPolicy().entries().containsKey("rematter:sodium"));
    }

    @Test
    void expandedMaterialCoveragePreservesRatiosAndSynthesisBoundaries() {
        var registry = ShippedChemistry.standalone();
        var cellulose = SubstanceKey.of("rematter:cellulose", Phase.SOLID);
        var beeswax = SubstanceKey.of("rematter:beeswax", Phase.SOLID);

        // Vanilla cellulose: 6:1 log-to-plank ratio (Mekanism sawing safe), planks are decomposition-only.
        var oakLog = registry.profile("minecraft:oak_log").orElseThrow();
        var oakPlanks = registry.profile("minecraft:oak_planks").orElseThrow();
        assertEquals(1_200L, oakLog.matter().get(cellulose));
        assertEquals(200L, oakPlanks.matter().get(cellulose));
        assertTrue(registry.synthesisPolicy().allowsItem("minecraft:oak_log"));
        assertFalse(registry.synthesisPolicy().allowsItem("minecraft:oak_planks"), "Planks must remain decomposition-only");

        // Beeswax & honeycomb (4:1 block) + Productive Bees (9:1 block) conservation.
        assertEquals(1_000L, registry.profile("minecraft:honeycomb").orElseThrow().matter().get(beeswax));
        assertEquals(4_000L, registry.profile("minecraft:honeycomb_block").orElseThrow().matter().get(beeswax));
        assertEquals(1_000L, registry.profile("productivebees:wax").orElseThrow().matter().get(beeswax));
        assertEquals(9_000L, registry.profile("productivebees:wax_block").orElseThrow().matter().get(beeswax));

        // FTBIC plutonium blocks exact 9:1 ratios, synthesis disabled.
        var plutonium = SubstanceKey.of("rematter:plutonium", Phase.SOLID);
        assertEquals(1_000L, registry.profile("ftbic:plutonium_ingot").orElseThrow().matter().get(plutonium));
        assertEquals(9_000L, registry.profile("ftbic:plutonium_block").orElseThrow().matter().get(plutonium));
        assertEquals(9_000L, registry.profile("ftbic:plutonium_raw_block").orElseThrow().matter().get(plutonium));
        assertTrue(registry.synthesisPolicy().allowsItem("ftbic:plutonium_block"));
        assertFalse(registry.synthesisPolicy().allowsItem("ftbic:plutonium_raw_block"));

        // Powah, Just Dire Things, and AdvancedAE exact 9:1 block conservation and safe synthesis permissions.
        assertEquals(9L * registry.profile("powah:steel_energized").orElseThrow().matter().values().stream().mapToLong(Long::longValue).sum(),
                registry.profile("powah:energized_steel_block").orElseThrow().matter().values().stream().mapToLong(Long::longValue).sum());
        assertFalse(registry.synthesisPolicy().allowsItem("powah:steel_energized"));
        assertFalse(registry.synthesisPolicy().allowsItem("powah:crystal_niotic"));
        assertFalse(registry.synthesisPolicy().allowsItem("powah:crystal_spirited"));
        assertEquals(9L * registry.profile("justdirethings:ferricore_ingot").orElseThrow().matter().values().stream().mapToLong(Long::longValue).sum(),
                registry.profile("justdirethings:ferricore_block").orElseThrow().matter().values().stream().mapToLong(Long::longValue).sum());
        assertEquals(4_050L, registry.profile("justdirethings:celestigem_block").orElseThrow().matter().values().stream().mapToLong(Long::longValue).sum());
        assertFalse(registry.synthesisPolicy().allowsItem("justdirethings:celestigem"));
        assertFalse(registry.synthesisPolicy().allowsItem("justdirethings:ferricore_block"));
        assertEquals(9L * registry.profile("advanced_ae:quantum_alloy").orElseThrow().matter().values().stream().mapToLong(Long::longValue).sum(),
                registry.profile("advanced_ae:quantum_alloy_block").orElseThrow().matter().values().stream().mapToLong(Long::longValue).sum());
        assertFalse(registry.synthesisPolicy().allowsItem("advanced_ae:quantum_alloy"));
    }

    @Test
    void industrialDecomposerAndSynthesizerHonorMultiblockAndRoutingContracts() {
        assertTrue(MachineKind.INDUSTRIAL_DECOMPOSER.decomposition());
        assertFalse(MachineKind.INDUSTRIAL_DECOMPOSER.synthesis());
        assertTrue(MachineKind.INDUSTRIAL_SYNTHESIZER.synthesis());
        assertFalse(MachineKind.INDUSTRIAL_SYNTHESIZER.decomposition());
        assertTrue(IndustrialStructure.isIndustrial(MachineKind.INDUSTRIAL_DECOMPOSER));
        assertTrue(IndustrialStructure.isIndustrial(MachineKind.INDUSTRIAL_SYNTHESIZER));
        assertTrue(IndustrialStructure.usesReactorModules(MachineKind.INDUSTRIAL_DECOMPOSER));
        assertTrue(IndustrialStructure.usesReactorModules(MachineKind.INDUSTRIAL_SYNTHESIZER));

        var perf = new IndustrialPerformance(0, 0, 0, 1, 1);
        assertEquals(2, perf.workRate(MachineKind.INDUSTRIAL_DECOMPOSER));
        assertEquals(2, perf.workRate(MachineKind.INDUSTRIAL_SYNTHESIZER));

        var registry = ShippedChemistry.standalone();
        assertTrue(registry.synthesisPolicy().allowsMachine("minecraft:diamond", MachineKind.INDUSTRIAL_SYNTHESIZER.id()));
        assertTrue(ProcessPresentation.resolve(registry, MachineKind.INDUSTRIAL_SYNTHESIZER, "minecraft:diamond", "Diamond").isPresent());
        assertTrue(ChemistryViewer.Category.DECOMPOSITION.machines().contains(MachineKind.INDUSTRIAL_DECOMPOSER));
        assertTrue(ChemistryViewer.Category.SYNTHESIS.machines().contains(MachineKind.INDUSTRIAL_SYNTHESIZER));
        assertTrue(MachinePortMode.ITEM_INPUT.supported(MachineKind.INDUSTRIAL_DECOMPOSER));
        assertTrue(MachinePortMode.FLUID_INPUT.supported(MachineKind.INDUSTRIAL_DECOMPOSER));
        assertTrue(MachinePortMode.ITEM_OUTPUT.supported(MachineKind.INDUSTRIAL_SYNTHESIZER));
        assertTrue(MachinePortMode.FLUID_OUTPUT.supported(MachineKind.INDUSTRIAL_SYNTHESIZER));

        // Verify MachineSnapshot accepts 4x Speed Upgrade (10x multiplier -> 20x / 40x workRate) on all formed industrial controllers.
        for (var kind : List.of(MachineKind.INDUSTRIAL_DECOMPOSER, MachineKind.INDUSTRIAL_SYNTHESIZER, MachineKind.INDUSTRIAL_REACTOR)) {
            var sv = new MachineSnapshot.StructureView(3, 3, 5, 20, 1, "ready", 0, 0, 0, 1, 1, 20);
            assertDoesNotThrow(() -> new MachineSnapshot(1, kind.ordinal(), "working", -1, "", 0, 1,
                    0L, 0L, false, false, 0, "", false, 0, MachineSnapshot.Preview.EMPTY, "network", sv,
                    MachineSnapshot.NetworkView.EMPTY, false, "", MachineSnapshot.OrdersView.EMPTY, List.of()));
        }
        var prepSv = new MachineSnapshot.StructureView(5, 3, 5, 40, 1, "ready", 1, 1, 1, 0, 0, 20);
        assertDoesNotThrow(() -> new MachineSnapshot(1, MachineKind.INDUSTRIAL_PREPARATOR.ordinal(), "working", -1, "", 0, 1,
                0L, 0L, false, false, 0, "", false, 0, MachineSnapshot.Preview.EMPTY, "none", prepSv,
                MachineSnapshot.NetworkView.EMPTY, false, "", MachineSnapshot.OrdersView.EMPTY, List.of()));
    }
}
