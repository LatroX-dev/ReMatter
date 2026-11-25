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
import java.util.Map;
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
    void curiosSlotTagsIncludeWirelessTerminalPortableEnergyCellAndDiscoveryLens() throws Exception {
        for (String slot : List.of("curio", "charm", "belt")) {
            Path path = RESOURCES.resolve("data/curios/tags/item/" + slot + ".json");
            assertTrue(Files.isRegularFile(path), "Missing Curios tag: " + path);
            var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            assertFalse(json.get("replace").getAsBoolean());
            Set<String> values = json.getAsJsonArray("values").asList().stream()
                    .map(e -> e.getAsString()).collect(Collectors.toSet());
            assertTrue(values.contains("rematter:wireless_matter_terminal"), slot);
            assertTrue(values.contains("rematter:portable_energy_cell"), slot);
            if (!slot.equals("belt")) {
                assertTrue(values.contains("rematter:spectrometer_lens"), slot);
                assertTrue(values.contains("rematter:discovery_lens"), slot);
            }
        }
        Path headPath = RESOURCES.resolve("data/curios/tags/item/head.json");
        assertTrue(Files.isRegularFile(headPath), "Missing Curios head tag: " + headPath);
        var headJson = JsonParser.parseString(Files.readString(headPath)).getAsJsonObject();
        assertFalse(headJson.get("replace").getAsBoolean());
        Set<String> headValues = headJson.getAsJsonArray("values").asList().stream()
                .map(e -> e.getAsString()).collect(Collectors.toSet());
        assertTrue(headValues.contains("rematter:spectrometer_lens"), "head");
        assertTrue(headValues.contains("rematter:discovery_lens"), "head");

        Path entitySlots = RESOURCES.resolve("data/rematter/curios/entities/rematter.json");
        assertTrue(Files.isRegularFile(entitySlots), "Missing Curios entity slot declaration");
        var entityJson = JsonParser.parseString(Files.readString(entitySlots)).getAsJsonObject();
        Set<String> slots = entityJson.getAsJsonArray("slots").asList().stream()
                .map(e -> e.getAsString()).collect(Collectors.toSet());
        assertTrue(slots.contains("head") && slots.contains("charm") && slots.contains("belt"));

        // Verify Spectrometer Lens is early-game shaped crafting and upgrades into Discovery Lens.
        var specRecipe = JsonParser.parseString(Files.readString(RESOURCES.resolve("data/rematter/recipe/spectrometer_lens.json"))).getAsJsonObject();
        assertEquals("minecraft:crafting_shaped", specRecipe.get("type").getAsString());
        assertEquals("rematter:ice_matrix_crystal", specRecipe.getAsJsonObject("key").get("I").getAsString());
        var discRecipe = JsonParser.parseString(Files.readString(RESOURCES.resolve("data/rematter/recipe/discovery_lens.json"))).getAsJsonObject();
        assertEquals("rematter:preparation", discRecipe.get("type").getAsString());
        assertEquals("rematter:spectrometer_lens", discRecipe.get("secondary").getAsString());
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
    void botaniaRevivedCoveragePreservesProgressionAndConservationRatios() {
        var registry = ShippedChemistry.standalone();
        var botaniaProfiles = registry.profiles().stream()
                .filter(p -> p.itemId().startsWith("botania:"))
                .toList();
        assertEquals(251, botaniaProfiles.size(), "Expected all 251 JAR-verified Botania Revived 2.0.4 item profiles");
        long synthesizableCount = botaniaProfiles.stream()
                .filter(p -> registry.synthesisPolicy().allowsItem(p.itemId()))
                .count();
        assertEquals(99L, synthesizableCount, "Only loop-free natural Botania materials may be synthesizable");

        var iron = SubstanceKey.of("rematter:iron", Phase.SOLID);
        var diamond = SubstanceKey.of("rematter:diamond", Phase.SOLID);
        var enderion = SubstanceKey.of("rematter:enderion", Phase.SOLID);
        var silica = SubstanceKey.of("rematter:silica", Phase.SOLID);
        var vitalium = SubstanceKey.of("rematter:vitalium", Phase.SOLID);
        var arcanium = SubstanceKey.of("rematter:arcanium", Phase.SOLID);
        var cellulose = SubstanceKey.of("rematter:cellulose", Phase.SOLID);
        var pigment = SubstanceKey.of("rematter:pigment", Phase.SOLID);
        var crystalSilica = SubstanceKey.of("rematter:crystalline_silica", Phase.SOLID);

        // Exact 9:1 metal ingot/nugget/block conservation (Manasteel, Elementium, Terrasteel) and recovery-only permission.
        for (String metal : List.of("manasteel", "elementium", "terrasteel")) {
            var ingot = registry.profile("botania:" + metal + "_ingot").orElseThrow();
            var nugget = registry.profile("botania:" + metal + "_nugget").orElseThrow();
            var block = registry.profile("botania:" + metal + "_block").orElseThrow();
            assertEquals(1, ingot.itemCount());
            assertEquals(9, nugget.itemCount());
            assertEquals(ingot.matter(), nugget.matter(), metal + " nugget batch must match 1 ingot");
            long ingotTotal = ingot.matter().values().stream().mapToLong(Long::longValue).sum();
            long blockTotal = block.matter().values().stream().mapToLong(Long::longValue).sum();
            assertEquals(9L * ingotTotal, blockTotal, metal + " block must equal 9 ingots");
            assertFalse(registry.synthesisPolicy().allowsItem(ingot.itemId()));
            assertFalse(registry.synthesisPolicy().allowsItem(nugget.itemId()));
            assertFalse(registry.synthesisPolicy().allowsItem(block.itemId()));
        }

        // Exact stoichiometry for Terrasteel, Gaia Spirit, Gaia Ingot, and conservative GoG Blaze Block.
        var terra = registry.profile("botania:terrasteel_ingot").orElseThrow().matter();
        assertEquals(1_000L, terra.get(iron));
        assertEquals(1_000L, terra.get(diamond));
        assertEquals(1_000L, terra.get(enderion));
        assertEquals(1_000L, terra.get(silica));

        var spirit = registry.profile("botania:life_essence").orElseThrow().matter();
        assertEquals(500L, spirit.get(vitalium));
        assertEquals(500L, spirit.get(arcanium));

        var gaiaIngot = registry.profile("botania:gaia_ingot").orElseThrow().matter();
        assertEquals(1_000L, gaiaIngot.get(iron));
        assertEquals(1_000L, gaiaIngot.get(diamond));
        assertEquals(1_000L, gaiaIngot.get(enderion));
        assertEquals(1_000L, gaiaIngot.get(silica));
        assertEquals(2_000L, gaiaIngot.get(vitalium));
        assertEquals(2_000L, gaiaIngot.get(arcanium));

        var blazePowder = registry.profile("minecraft:blaze_powder").orElseThrow().matter();
        var blazeBlock = registry.profile("botania:blaze_block").orElseThrow().matter();
        for (var entry : blazePowder.entrySet()) {
            assertEquals(9L * entry.getValue(), blazeBlock.get(entry.getKey()), "Blaze Block must match conservative 9x Blaze Powder lower bound");
        }
        assertFalse(registry.synthesisPolicy().allowsItem("botania:blaze_block"));

        // Exact 1:2:4:9 conservation across Mystical Petals, Small Flowers, Double Flowers, and Petal Blocks.
        var petal = registry.profile("botania:white_petal").orElseThrow().matter();
        var flower = registry.profile("botania:white_mystical_flower").orElseThrow().matter();
        var doubleFlower = registry.profile("botania:white_double_flower").orElseThrow().matter();
        var petalBlock = registry.profile("botania:white_petal_block").orElseThrow().matter();
        assertEquals(100L, petal.get(cellulose));
        assertEquals(1_200L, petal.get(pigment));
        assertEquals(2L * petal.get(pigment), flower.get(pigment));
        assertEquals(4L * petal.get(pigment), doubleFlower.get(pigment));
        assertEquals(9L * petal.get(pigment), petalBlock.get(pigment));
        assertTrue(registry.synthesisPolicy().allowsItem("botania:white_petal"));
        assertTrue(registry.synthesisPolicy().allowsItem("botania:white_mystical_flower"));
        assertTrue(registry.synthesisPolicy().allowsItem("botania:white_double_flower"));
        assertTrue(registry.synthesisPolicy().allowsItem("botania:white_petal_block"));
        assertEquals(Map.of(pigment, 400L), registry.profile("botania:floral_bifrost_powder").orElseThrow().matter());
        assertFalse(registry.synthesisPolicy().allowsItem("botania:floral_bifrost_powder"));

        // Exact 4:1 conservation for all 8 specialty quartz families (matching Alchemy Catalyst 1:4 deconstruction).
        for (String[] pair : List.of(
                new String[]{"quartz_dark", "dark_quartz"},
                new String[]{"quartz_mana", "mana_quartz"},
                new String[]{"quartz_blaze", "blaze_quartz"},
                new String[]{"quartz_lavender", "lavender_quartz"},
                new String[]{"quartz_red", "red_quartz"},
                new String[]{"quartz_elven", "elf_quartz"},
                new String[]{"quartz_sunny", "sunny_quartz"},
                new String[]{"quartz_shimmer", "shimmer_quartz"})) {
            assertEquals(1_000L, registry.profile("botania:" + pair[0]).orElseThrow().matter().get(crystalSilica));
            assertEquals(4_000L, registry.profile("botania:" + pair[1]).orElseThrow().matter().get(crystalSilica));
            assertFalse(registry.synthesisPolicy().allowsItem("botania:" + pair[0]));
            assertFalse(registry.synthesisPolicy().allowsItem("botania:" + pair[1]));
        }

        // Metamorphic stone & cobblestone are synthesizable; bricks and chiseled variants remain recovery-only.
        assertTrue(registry.synthesisPolicy().allowsItem("botania:metamorphic_forest_stone"));
        assertTrue(registry.synthesisPolicy().allowsItem("botania:metamorphic_forest_cobblestone"));
        assertFalse(registry.synthesisPolicy().allowsItem("botania:metamorphic_forest_bricks"));
        assertFalse(registry.synthesisPolicy().allowsItem("botania:chiseled_metamorphic_forest_bricks"));

        // Functional/generating flowers, tools, armor, baubles, and ambiguous mana_powder stay excluded.
        for (String excluded : List.of(
                "botania:pure_daisy", "botania:endoflame", "botania:hydroangeas", "botania:orechid",
                "botania:manasteel_pick", "botania:terra_sword", "botania:terrasteel_helmet",
                "botania:mana_ring", "botania:mana_powder")) {
            assertTrue(registry.profile(excluded).isEmpty(), "Should not be registered in chemistry catalogue: " + excluded);
            assertFalse(registry.synthesisPolicy().allowsItem(excluded));
        }
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

    @Test
    void endgameReactorsDeepExtractorAndUnifiedReactorIdsHonorContracts() {
        assertEquals("decomposer_reactor_controller", MachineKind.INDUSTRIAL_DECOMPOSER.id());
        assertEquals("synthesizer_reactor_controller", MachineKind.INDUSTRIAL_SYNTHESIZER.id());
        assertEquals("transmutation_reactor_controller", MachineKind.TRANSMUTATION_REACTOR.id());
        assertEquals("primal_reactor_controller", MachineKind.PRIMAL_REACTOR.id());
        assertEquals("energy_matrix_controller", MachineKind.ENERGY_MATRIX.id());
        assertEquals("energy_reactor_controller", MachineKind.ENERGY_REACTOR.id());
        assertEquals("deep_extractor", MachineKind.DEEP_EXTRACTOR.id());

        assertTrue(MachineKind.DEEP_EXTRACTOR.openTop());
        assertTrue(IndustrialStructure.isIndustrial(MachineKind.TRANSMUTATION_REACTOR));
        assertTrue(IndustrialStructure.isIndustrial(MachineKind.PRIMAL_REACTOR));
        assertTrue(IndustrialStructure.isIndustrial(MachineKind.ENERGY_MATRIX));
        assertTrue(IndustrialStructure.isIndustrial(MachineKind.ENERGY_REACTOR));
        assertEquals(3, IndustrialStructure.minFootprint(MachineKind.ENERGY_MATRIX));
        assertEquals(3, IndustrialStructure.height(MachineKind.ENERGY_MATRIX));
        assertEquals(5, IndustrialStructure.minFootprint(MachineKind.PRIMAL_REACTOR));
        assertEquals(5, IndustrialStructure.height(MachineKind.PRIMAL_REACTOR));
        assertEquals(5, IndustrialStructure.minFootprint(MachineKind.ENERGY_REACTOR));
        assertEquals(5, IndustrialStructure.height(MachineKind.ENERGY_REACTOR));

        // Packed 9x9x9 3D Energy Matrix (200 Cells + 143 Providers = 343 interior parts) stays valid in MachineSnapshot.
        var packedMatrixSv = new MachineSnapshot.StructureView(9, 9, 9, 2, 200, "ready", 0, 0, 0, 143, 200, 0);
        assertDoesNotThrow(() -> new MachineSnapshot(1, MachineKind.ENERGY_MATRIX.ordinal(), "ready", -1, "", 0, 1,
                0L, 0L, false, false, 0, "", false, 0, MachineSnapshot.Preview.EMPTY, "none", packedMatrixSv,
                MachineSnapshot.NetworkView.EMPTY, false, "", MachineSnapshot.OrdersView.EMPTY, List.of()));
        var energyReactorSv = new MachineSnapshot.StructureView(5, 5, 5, 2, 2, "ready", 0, 0, 0, 2, 2, 0);
        assertDoesNotThrow(() -> new MachineSnapshot(1, MachineKind.ENERGY_REACTOR.ordinal(), "generating", -1, "", 0, 1,
                0L, 0L, false, true, 0, "", false, 0, MachineSnapshot.Preview.EMPTY, "network", energyReactorSv,
                MachineSnapshot.NetworkView.EMPTY, false, "", MachineSnapshot.OrdersView.EMPTY, List.of()));

        var registry = ShippedChemistry.standalone();
        assertTrue(registry.environmentSource(SubstanceId.parse("rematter:deep_mineral_extraction")).orElseThrow().deepExtraction());
        assertEquals("deepslate", registry.environmentSource(SubstanceId.parse("rematter:deep_mineral_extraction")).orElseThrow().probeBelow());
        var transmuteGold = registry.processes().stream()
                .filter(p -> p.id().equals(SubstanceId.parse("rematter:transmute_gold")))
                .findFirst().orElseThrow();
        assertEquals(dev.latrox.rematter.chemistry.ProcessType.TRANSMUTATION, transmuteGold.type());
        assertTrue(MachineRouting.supports(MachineKind.TRANSMUTATION_REACTOR, transmuteGold));
        assertFalse(MachineRouting.supports(MachineKind.INDUSTRIAL_REACTOR, transmuteGold));
        assertTrue(ChemistryViewer.Category.TRANSMUTATION.machines().contains(MachineKind.TRANSMUTATION_REACTOR));
        assertTrue(ChemistryViewer.Category.DEEP_EXTRACTION.machines().contains(MachineKind.DEEP_EXTRACTOR));
    }
}
