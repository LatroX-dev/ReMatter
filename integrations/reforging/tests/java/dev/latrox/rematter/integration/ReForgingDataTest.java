package dev.latrox.rematter.integration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.content.MachineKind;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.conditions.NeverCondition;
import static org.junit.jupiter.api.Assertions.*;

class ReForgingDataTest {
    private static final SubstanceKey CRYSTAL = SubstanceKey.of("rematter:crystalline_silica", Phase.SOLID);
    private static final Path DATAPACK = Path.of("integrations/reforging/datapack");

    @Test
    void nativeBeeDupeAndQuestObtainabilityTagsAreNarrowAndAdditive() throws Exception {
        var bee = readFile(DATAPACK.resolve("data/productivebees/tags/block/dupe_blacklist.json"));
        assertFalse(bee.get("replace").getAsBoolean());
        var actual = new HashSet<String>();
        for (var element : bee.getAsJsonArray("values")) {
            var entry = element.getAsJsonObject();
            assertFalse(entry.get("required").getAsBoolean());
            assertTrue(actual.add(entry.get("id").getAsString()));
        }
        assertEquals(Set.of("rematter:deepslate_primal_ore", "rematter:nether_primal_ore", "rematter:end_primal_ore"), actual);
        var quest = readFile(DATAPACK.resolve("data/embersquesting/tags/item/obtainable.json"));
        assertFalse(quest.get("replace").getAsBoolean());
        var obtainable = new HashSet<String>();
        for (var element : quest.getAsJsonArray("values")) {
            var entry = element.getAsJsonObject();
            assertFalse(entry.get("required").getAsBoolean());
            assertTrue(obtainable.add(entry.get("id").getAsString()));
        }
        assertEquals(Set.of("rematter:matrix_crystal", "rematter:parallel_upgrade"), obtainable);
    }

    @Test
    void crystallineQuartzHasTheSameElementBalanceButASeparateIdentity() throws Exception {
        ChemistryRegistry registry = integration();
        assertNotEquals(BuiltinChemistry.SILICA, CRYSTAL);
        assertEquals(registry.elementAmounts(Map.of(BuiltinChemistry.SILICA, 1_000L)),
                registry.elementAmounts(Map.of(CRYSTAL, 1_000L)));
        assertEquals(Map.of(BuiltinChemistry.SILICA, 1_000L), registry.profile("minecraft:sand").orElseThrow().matter());
        assertEquals(Map.of(CRYSTAL, 1_000L), registry.profile("minecraft:quartz").orElseThrow().matter());
        assertTrue(registry.synthesisPolicy().allowsMachine("minecraft:quartz","material_synthesizer"), "The pack supplies a safe bonus-free grinding override");
    }

    @Test
    void sandAndEveryOtherMaterialCannotReachCrystallineSilica() throws Exception {
        ChemistryRegistry registry = integration();
        Set<SubstanceKey> reachable = new HashSet<>();
        registry.profiles().stream().filter(profile -> !profile.matter().containsKey(CRYSTAL))
                .forEach(profile -> reachable.addAll(profile.matter().keySet()));
        // Also include every externally supplied base substance, a stricter condition than sand alone.
        registry.substances().stream().filter(definition -> !definition.id().equals(CRYSTAL.id()))
                .forEach(definition -> definition.phases().forEach(phase -> reachable.add(new SubstanceKey(definition.id(), phase))));
        boolean changed;
        do {
            changed = false;
            for (ProcessRecipe process : registry.processes()) {
                if (reachable.containsAll(process.inputs().keySet())) changed |= reachable.addAll(process.outputs().keySet());
            }
        } while (changed);
        assertFalse(reachable.contains(CRYSTAL), "A process makes the protected quartz feedstock from ordinary matter");
        assertTrue(registry.processes().stream().noneMatch(process -> process.outputs().containsKey(CRYSTAL)));
    }

    @Test
    void quartzRoundTripPreservesExactlyOneItemAndCannotProduceTwo() throws Exception {
        var quartz = integration().profile("minecraft:quartz").orElseThrow();
        MatterInventory inventory = new MatterInventory(10_000);
        assertTrue(inventory.apply(Map.of(), quartz.matter()));
        MatterInventory restored = MatterInventory.restore(inventory.snapshot(), Integer.MAX_VALUE);
        assertEquals(1_000L, restored.total());
        assertFalse(restored.apply(ChemistryFixtures.matterFor(quartz, 2), Map.of()));
        assertTrue(restored.apply(quartz.matter(), Map.of()));
        assertEquals(0, restored.total());
        assertFalse(restored.apply(quartz.matter(), Map.of()));
    }

    @Test
    void allQuartzFormsShareTheProtectedIdentityAndExactBatchRatios() throws Exception {
        ChemistryRegistry registry = integration();
        Map<String, Long> amounts = new TreeMap<>();
        amounts.put("minecraft:quartz", 1_000L);
        amounts.put("minecraft:quartz_block", 4_000L);
        amounts.put("minecraft:smooth_quartz", 4_000L);
        amounts.put("minecraft:quartz_bricks", 4_000L);
        amounts.put("minecraft:quartz_pillar", 4_000L);
        amounts.put("minecraft:chiseled_quartz_block", 4_000L);
        amounts.put("enderio:fused_quartz", 4_000L);
        amounts.put("rematter:virel", 1_000L);
        amounts.put("enderio:powdered_quartz", 1_000L);
        amounts.put("ftbic:quartz_dust", 1_000L);
        amounts.put("mekanism:dust_quartz", 1_000L);
        amounts.put("oritech:quartz_dust", 1_000L);
        amounts.put("thaumaturge:nugget_quartz", 1_000L);
        amounts.put("minecraft:nether_quartz_ore", 1_000L);
        amounts.put("thaumaturge:ore_quartz", 1_000L);
        amounts.put("thaumaturge:deepslate_ore_quartz", 1_000L);
        Set<String> canonicalSynth = Set.of("minecraft:quartz", "minecraft:quartz_block", "mekanism:dust_quartz", "thaumaturge:nugget_quartz");
        Set<String> aliasedDusts = Set.of("enderio:powdered_quartz", "ftbic:quartz_dust", "oritech:quartz_dust");
        Set<String> actual = registry.profiles().stream().filter(profile -> profile.matter().keySet().equals(Set.of(CRYSTAL)))
                .map(MaterialProfile::itemId).collect(Collectors.toSet());
        assertEquals(amounts.keySet(), actual);
        amounts.forEach((item, amount) -> {
            var profile = registry.profile(item).orElseThrow();
            assertEquals(Map.of(CRYSTAL, amount), profile.matter(), item);
            assertEquals(item.equals("thaumaturge:nugget_quartz") ? 9 : 1, profile.itemCount(), item);
            assertEquals(canonicalSynth.contains(item), registry.synthesisPolicy().category(item).isPresent(), item);
            if (aliasedDusts.contains(item)) {
                assertEquals("mekanism:dust_quartz", registry.synthesisPolicy().canonicalTarget(item), item);
            }
        });
    }

    @Test
    void sandElectrolysisProducesSiliconWhileQuartzIdentityRemainsProtected() throws Exception {
        ChemistryRegistry registry = integration();
        ProcessRecipe electrolysis = registry.recipe(SubstanceId.parse("rematter:silica_electrolysis")).orElseThrow();
        assertEquals(Map.of(BuiltinChemistry.SILICA, 1_000L), electrolysis.inputs());
        assertEquals(Map.of(BuiltinChemistry.SILICON, 1_000L, BuiltinChemistry.OXYGEN, 1_000L), electrolysis.outputs());
        assertEquals(64_000, electrolysis.energy());
        assertEquals(800, electrolysis.ticks());
        assertEquals(registry.elementAmounts(electrolysis.inputs()), registry.elementAmounts(electrolysis.outputs()));
        var reachable = MatterReachability.from(registry, Set.of("minecraft:sand", "minecraft:clay_ball", "minecraft:dirt",
                "minecraft:stone", "minecraft:cobblestone"), Set.of("minecraft:water", "minecraft:lava"));
        assertTrue(reachable.matter().containsAll(Set.of(BuiltinChemistry.SILICA, BuiltinChemistry.SILICON,
                BuiltinChemistry.SOIL, BuiltinChemistry.ROCK, BuiltinChemistry.LAVA, BuiltinChemistry.HYDROGEN,
                BuiltinChemistry.OXYGEN)));
        assertFalse(reachable.matter().contains(CRYSTAL));
        assertFalse(reachable.matter().contains(BuiltinChemistry.ALUMINUM));
        assertFalse(reachable.items().contains("minecraft:quartz"));
    }

    @Test
    void replicationTagsProtectExactlyAllStatefulMachinesAndCellsAndPreserveExistingMembers() throws Exception {
        JsonObject machines = read("data/reforging/tags/item/rematter/stateful_machines.json");
        assertFalse(machines.get("replace").getAsBoolean());
        Set<String> actual = new HashSet<>();
        for (var value : machines.getAsJsonArray("values")) {
            JsonObject entry = value.getAsJsonObject();
            assertFalse(entry.get("required").getAsBoolean());
            assertTrue(actual.add(entry.get("id").getAsString()), "Duplicate protected item");
        }
        Set<String> expected = Arrays.stream(MachineKind.values()).map(kind -> "rematter:" + kind.id()).collect(Collectors.toSet());
        Arrays.stream(dev.latrox.rematter.energy.EnergyCubeTier.values()).forEach(tier->expected.add("rematter:"+tier.id()));
        expected.add("rematter:cell_transfer_station");
        expected.addAll(Set.of("rematter:matter_cell_4m", "rematter:matter_cell_16m", "rematter:matter_cell_64m",
                "rematter:matter_cell_256m", "rematter:void_matter_cell",
                "rematter:recovered_matter", "rematter:automation_pattern", "rematter:configuration_card",
                "rematter:wireless_access_point", "rematter:wireless_matter_terminal",
                "rematter:portable_energy_cell", "rematter:matter_level_emitter", "rematter:pressurized_canister"));
        assertEquals(expected, actual);
        JsonObject primal = read("data/reforging/tags/item/rematter/primal_items.json");
        assertFalse(primal.get("replace").getAsBoolean());
        Set<String> primalIds = new HashSet<>();
        for (var value : primal.getAsJsonArray("values")) {
            var entry = value.getAsJsonObject(); assertFalse(entry.get("required").getAsBoolean());
            assertTrue(primalIds.add(entry.get("id").getAsString()));
        }
        assertEquals(dev.latrox.rematter.chemistry.ReservedSubstances.primalItems(), primalIds);
        JsonObject crystalFuels = read("data/reforging/tags/item/rematter/crystal_fuels.json");
        assertFalse(crystalFuels.get("replace").getAsBoolean());
        Set<String> fuelIds = new HashSet<>();
        for (var value : crystalFuels.getAsJsonArray("values")) {
            var entry = value.getAsJsonObject(); assertFalse(entry.get("required").getAsBoolean());
            assertTrue(fuelIds.add(entry.get("id").getAsString()));
        }
        assertEquals(dev.latrox.rematter.content.generation.CrystalFuelData.ALLOWED, fuelIds);
        for (String tag : Set.of("cant_be_scanned", "cant_be_disintegrated", "skip_calculation")) {
            JsonObject rule = read("data/replication/tags/item/" + tag + ".json");
            assertFalse(rule.get("replace").getAsBoolean());
            assertEquals(3, rule.getAsJsonArray("values").size());
            assertEquals("#reforging:rematter/stateful_machines", rule.getAsJsonArray("values").get(0).getAsString());
            assertEquals("#reforging:rematter/primal_items", rule.getAsJsonArray("values").get(1).getAsString());
            assertEquals("#reforging:rematter/crystal_fuels", rule.getAsJsonArray("values").get(2).getAsString());
        }
    }

    @Test
    void standaloneCatalogueRemainsUnmodified() throws Exception {
        ChemistryRegistry before = ShippedChemistry.load(ShippedChemistry.MAIN_RESOURCES);
        ChemistryRegistry pack = integration();
        ChemistryRegistry after = ShippedChemistry.load(ShippedChemistry.MAIN_RESOURCES);
        assertEquals(java.util.List.copyOf(before.profiles()), java.util.List.copyOf(after.profiles()));
        assertEquals(java.util.List.copyOf(before.substances()), java.util.List.copyOf(pack.substances()),
                "The integration shares the standalone substance identities");
        assertEquals(java.util.List.copyOf(before.profiles()), java.util.List.copyOf(pack.profiles()));
        assertEquals(before.synthesisPolicy().entries(), after.synthesisPolicy().entries());
        var exceptions=Set.of("minecraft:quartz","minecraft:obsidian");
        pack.synthesisPolicy().entries().forEach((item, category) -> {
            if(!exceptions.contains(item))assertEquals(before.synthesisPolicy().entries().get(item),category,"Pack integration must not widen synthesis: "+item);
            else {
                assertNull(before.synthesisPolicy().entries().get(item));
                try {assertEquals("none",readFile(Path.of("integrations/reforging/datapack/data/enderio/recipe/sag_milling/"+item.substring(10)+".json")).get("bonus").getAsString());}
                catch(Exception error) {throw new AssertionError(error);}
            }
        });
    }

    @Test
    void quartzGrindingCannotMultiplyRecoveredMaterialWithGrindingBalls() throws Exception {
        JsonObject recipe = readFile(Path.of("integrations/reforging/datapack/data/enderio/recipe/sag_milling/quartz.json"));
        assertEquals("enderio:sag_milling", recipe.get("type").getAsString());
        assertEquals("#c:gems/quartz", recipe.get("input").getAsString());
        assertEquals("none", recipe.get("bonus").getAsString());
        assertEquals(2_400, recipe.get("energy").getAsInt());
        assertEquals(1, recipe.getAsJsonArray("outputs").size());
        JsonObject result = recipe.getAsJsonArray("outputs").get(0).getAsJsonObject();
        assertFalse(result.has("chance"));
        JsonObject item = result.getAsJsonObject("item");
        assertEquals("enderio:powdered_quartz", item.get("id").getAsString());
        assertEquals(1, item.has("count") ? item.get("count").getAsInt() : 1);
        assertFalse(Files.exists(Path.of("integrations/reforging/datapack/data/enderio/recipe/sag_milling/quartz_ore.json")),
                "Ore enrichment remains separate");
    }

    @Test
    void existingEnergyRoutesRemainLossyAtMaximumReMatterEfficiency() throws Exception {
        ChemistryRegistry registry = integration();
        long hydrogenCost = minimumCost(registry.recipe(SubstanceId.parse("rematter:water_electrolysis")).orElseThrow().energy(),MachineKind.REACTOR);
        assertTrue(hydrogenCost > 2_000, "1,000 hydrogen units return 2,000 FE in the target gas generator");
        long evaporationCost = minimumCost(registry.recipe(SubstanceId.parse("rematter:water_evaporation")).orElseThrow().energy(),MachineKind.REACTOR,MachineKind.INDUSTRIAL_REACTOR);
        assertTrue(evaporationCost > 11_580, "Conservative upper bound for 1,000 steam in the target turbines");
        var stone = registry.profile("minecraft:stone").orElseThrow();
        var blending = registry.recipe(SubstanceId.parse("rematter:mineral_blending")).orElseThrow();
        var melting = registry.recipe(SubstanceId.parse("rematter:mineral_melting")).orElseThrow();
        var lava = registry.fluidProfile("minecraft:lava").orElseThrow();
        assertEquals(stone.matter(), blending.inputs());
        assertEquals(blending.outputs(), melting.inputs());
        assertEquals(melting.outputs(), lava.matter());
        long lavaCost = minimumCost(stone.decompositionEnergy(),MachineKind.DECOMPOSER) + minimumCost(blending.energy(),MachineKind.REACTOR,MachineKind.INDUSTRIAL_REACTOR)
                + minimumCost(melting.energy(),MachineKind.REACTOR,MachineKind.INDUSTRIAL_REACTOR) + minimumCost(lava.synthesisEnergy(),MachineKind.SYNTHESIZER);
        assertTrue(lavaCost > 100_000, "A target Magmator returns 100,000 FE per 1,000 mB lava");
    }

    @Test
    void ordinaryRockCraftingCannotCreateMineralConstituents() throws Exception {
        ChemistryRegistry registry = integration();
        var cobble = registry.profile("minecraft:cobblestone").orElseThrow();
        var diorite = registry.profile("minecraft:diorite").orElseThrow();
        var andesite = registry.profile("minecraft:andesite").orElseThrow();
        var granite = registry.profile("minecraft:granite").orElseThrow();
        var quartz = registry.profile("minecraft:quartz").orElseThrow();
        Map<SubstanceKey, Long> andesiteIngredients = new TreeMap<>(cobble.matter());
        diorite.matter().forEach((key, amount) -> andesiteIngredients.merge(key, amount, Math::addExact));
        assertEquals(andesiteIngredients, ChemistryFixtures.matterFor(andesite, 2));
        assertRockConservation(registry, cobble, quartz, diorite);
        assertRockConservation(registry, diorite, quartz, granite);
        assertEquals(ChemistryFixtures.matterFor(registry.profile("minecraft:sand").orElseThrow(), 4),
                registry.profile("minecraft:sandstone").orElseThrow().matter());
    }

    private static void assertRockConservation(ChemistryRegistry registry, MaterialProfile rock,
                                               MaterialProfile quartz, MaterialProfile result) {
        Map<SubstanceKey, Long> ingredients = new TreeMap<>(rock.matter());
        quartz.matter().forEach((key, amount) -> ingredients.merge(key, amount, Math::addExact));
        var availableElements = registry.elementAmounts(ingredients);
        registry.elementAmounts(result.matter()).forEach((element, amount) ->
                assertTrue(amount <= availableElements.getOrDefault(element, 0L), result.itemId() + " creates " + element));
        assertFalse(result.matter().containsKey(CRYSTAL));
    }

    @Test
    void nineEnderIoPearlPowdersRecoverExactlyOnePearl() throws Exception {
        ChemistryRegistry registry = integration();
        MaterialProfile pearl = registry.profile("minecraft:ender_pearl").orElseThrow();
        MaterialProfile powder = registry.profile("enderio:powdered_ender_pearl").orElseThrow();
        MaterialProfile aeDust = registry.profile("ae2:ender_dust").orElseThrow();
        assertEquals(1, pearl.itemCount());
        assertEquals(9, powder.itemCount(), "The SAG Mill produces nine small powders per pearl");
        assertEquals(1, aeDust.itemCount(), "AE2 dust is a full pearl unit");
        assertEquals(pearl.matter(), powder.matter());
        assertEquals(pearl.matter(), aeDust.matter());
        assertEquals(ChemistryFixtures.matterFor(pearl, 1), ChemistryFixtures.matterFor(profile(registry, "rematter:ender_pearl_powder"), 1));
        assertEquals(ChemistryFixtures.matterFor(pearl, 1), ChemistryFixtures.matterFor(profile(registry, "jaopca:storage_blocks.ender_pearl"), 1));
        assertFalse(registry.synthesisPolicy().category("jaopca:storage_blocks.ender_pearl").isPresent());
        var crystallize = readFile(Path.of("integrations/reforging/datapack/data/occultism/recipe/crystallize/ender_pearl.json"));
        assertEquals("#rematter:full_ender_pearl_powders", crystallize.get("ingredient").getAsString());
        assertTrue(crystallize.get("ignore_crystallize_multiplier").getAsBoolean());
        var crush = readFile(Path.of("integrations/reforging/datapack/data/occultism/recipe/crushing/ender_pearl_dust_from_tag.json"));
        assertEquals("rematter:ender_pearl_powder", crush.getAsJsonObject("result").get("id").getAsString());
        assertTrue(crush.get("ignore_crushing_multiplier").getAsBoolean());
        var storage = Path.of("integrations/reforging/datapack/data/jaopca/recipe");
        var pack = readFile(storage.resolve("storage_blocks.to_storage_block.ender_pearl.json"));
        var unpack = readFile(storage.resolve("storage_blocks.to_material.ender_pearl.json"));
        assertEquals("enderio:powdered_ender_pearl", pack.getAsJsonObject("key").get("P").getAsString());
        assertEquals(9, pack.getAsJsonArray("pattern").asList().stream().mapToInt(line -> line.getAsString().length()).sum());
        assertEquals("enderio:powdered_ender_pearl", unpack.getAsJsonObject("result").get("id").getAsString());
        assertEquals(9, unpack.getAsJsonObject("result").get("count").getAsInt());
        MatterInventory recovered = new MatterInventory(100_000);
        assertTrue(recovered.apply(Map.of(), powder.matter()));
        assertFalse(recovered.apply(ChemistryFixtures.matterFor(pearl, 2), Map.of()));
        assertTrue(recovered.apply(pearl.matter(), Map.of()));
        assertEquals(0, recovered.total());
    }

    @Test
    void dimensionMaterialsAndAlloyRecipesConserveTheirSharedConstituents() throws Exception {
        ChemistryRegistry registry = integration();
        var end = SubstanceKey.of("rematter:enderion", Phase.SOLID);
        var nether = SubstanceKey.of("rematter:cinderium", Phase.SOLID);
        assertEquals(10 * profile(registry, "minecraft:end_stone").matter().get(end),
                profile(registry, "minecraft:ender_pearl").matter().get(end));
        assertEquals(50 * profile(registry, "minecraft:netherrack").matter().get(nether),
                profile(registry, "minecraft:glowstone_dust").matter().get(nether));
        assertRecipeMatter(registry, "enderio:conductive_alloy_ingot", 2, "minecraft:iron_ingot", "minecraft:copper_ingot");
        assertRecipeMatter(registry, "enderio:dark_steel_ingot", 2, "minecraft:iron_ingot", "enderio:powdered_coal", "minecraft:obsidian");
        assertRecipeMatter(registry, "enderio:energetic_alloy_ingot", 2, "minecraft:redstone", "enderio:conductive_alloy_ingot", "minecraft:gold_ingot");
        assertRecipeMatter(registry, "enderio:vibrant_alloy_ingot", 2, "enderio:energetic_alloy_ingot", "minecraft:ender_pearl", "minecraft:glowstone_dust");
        assertRecipeMatter(registry, "enderio:pulsating_alloy_ingot", 2, "minecraft:iron_ingot", "minecraft:ender_pearl");
        assertRecipeMatter(registry, "enderio:end_steel_ingot", 2, "minecraft:end_stone", "enderio:dark_steel_ingot", "minecraft:obsidian");
        assertRecipeMatter(registry, "mekanism:ingot_refined_glowstone", 1, "minecraft:glowstone_dust");
        assertRecipeMatter(registry, "mekanism:ingot_refined_obsidian", 1, "mekanism:dust_refined_obsidian");
        for (String form : Set.of("block", "ingot", "nugget")) {
            for (String material : Set.of("refined_glowstone", "refined_obsidian")) {
                assertFalse(registry.synthesisPolicy().category("mekanism:" + form + "_" + material).isPresent());
            }
        }
    }

    private static void assertRecipeMatter(ChemistryRegistry registry, String result, int count, String... ingredients) {
        Map<SubstanceKey, Long> supplied = new TreeMap<>();
        for (String item : ingredients) {
            ChemistryFixtures.matterFor(profile(registry, item), 1).forEach((key, amount) -> supplied.merge(key, amount, Math::addExact));
        }
        assertEquals(supplied, ChemistryFixtures.matterFor(profile(registry, result), count), result);
    }

    private static MaterialProfile profile(ChemistryRegistry registry, String item) {
        return registry.profile(item).orElseThrow();
    }

    @Test
    void nativeAlloyRoutesDoNotDuplicateSharedIngredients() throws Exception {
        ChemistryRegistry registry = integration();
        assertRecipeMatter(registry, "oritech:adamant_ingot", 1, "alltheores:nickel_ingot", "minecraft:diamond");
        assertEquals(ChemistryFixtures.matterFor(profile(registry, "minecraft:copper_ingot"), 3),
                ChemistryFixtures.matterFor(profile(registry, "nautec:aquarine_copper_ingot"), 24));
        assertFalse(registry.synthesisPolicy().category("nautec:aquarine_copper_ingot").isPresent());
        assertEquals(profile(registry, "alltheores:electrum_ingot").matter(),
                profile(registry, "oritech:electrum_ingot").matter());
        assertFalse(registry.synthesisPolicy().category("alltheores:electrum_ingot").isPresent());
        assertFalse(registry.synthesisPolicy().category("oritech:electrum_ingot").isPresent());
    }

    @Test
    void obsidianRecoveryRequiresFourDustsAndOneDustShortcutsAreDisabled() throws Exception {
        ChemistryRegistry registry = integration();
        var block = registry.profile("minecraft:obsidian").orElseThrow();
        var dust = registry.profile("mekanism:dust_obsidian").orElseThrow();
        assertEquals(1, block.itemCount());
        assertEquals(1, dust.itemCount());
        assertEquals(block.matter(), ChemistryFixtures.matterFor(dust, 4));
        MatterInventory inventory = new MatterInventory(100_000);
        assertTrue(inventory.apply(Map.of(), ChemistryFixtures.matterFor(dust, 3)));
        assertFalse(inventory.apply(block.matter(), Map.of()));
        assertTrue(inventory.apply(Map.of(), dust.matter()));
        assertTrue(inventory.apply(block.matter(), Map.of()));
        assertEquals(0, inventory.total());
        for (String shortcut : Set.of("occultism/recipe/crystallize/obsidian.json", "occultism/recipe/crafting/gray_paste/obsidian.json")) {
            assertDisabled(readFile(DATAPACK.resolve("data/" + shortcut)));
        }
        JsonObject milling = readFile(DATAPACK.resolve("data/enderio/recipe/sag_milling/obsidian.json"));
        assertEquals("#c:obsidians", milling.get("input").getAsString());
        assertEquals("none", milling.get("bonus").getAsString());
        assertEquals(1, milling.getAsJsonArray("outputs").size());
        JsonObject powder = milling.getAsJsonArray("outputs").get(0).getAsJsonObject().getAsJsonObject("item");
        assertEquals("enderio:powdered_obsidian", powder.get("id").getAsString());
        assertEquals(block.matter(), ChemistryFixtures.matterFor(registry.profile(powder.get("id").getAsString()).orElseThrow(), powder.get("count").getAsInt()));
    }

    @Test
    void ironAndEnderPearlsCannotBecomeNetheriteThroughEasyNetherite() throws Exception {
        assertDisabled(readFile(DATAPACK.resolve("data/netherite/recipe/netherite.json")));
        assertFalse(integration().synthesisPolicy().category("minecraft:netherite_ingot").isPresent());
    }

    @Test
    void learnReMatterQuestsFormACompleteAcyclicHexGridWithValidItems() throws Exception {
        Path questDir = Path.of("integrations/reforging/quests/learn_rematter");
        Path rematterItems = Path.of("src/main/resources/assets/rematter/items");
        Path rematterRecipes = Path.of("src/main/resources/data/rematter/recipe");
        assertTrue(Files.isRegularFile(questDir.resolve("chapter.json")));
        Set<String> standardCraftOutputs = new HashSet<>();
        try (var recipes = Files.walk(rematterRecipes)) {
            for (Path recipePath : recipes.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject doc = readFile(recipePath);
                if (!"rematter:preparation".equals(doc.get("type").getAsString()) && doc.has("result")) {
                    JsonElement res = doc.get("result");
                    standardCraftOutputs.add(res.isJsonObject() ? res.getAsJsonObject().get("id").getAsString() : res.getAsString());
                }
            }
        }
        Set<String> taggedObtainable = new HashSet<>();
        readFile(DATAPACK.resolve("data/embersquesting/tags/item/obtainable.json"))
                .getAsJsonArray("values")
                .forEach(el -> taggedObtainable.add(el.isJsonObject() ? el.getAsJsonObject().get("id").getAsString() : el.getAsString()));
        Map<String, Set<String>> deps = new TreeMap<>();
        Set<String> coords = new HashSet<>();
        try (var files = Files.list(questDir)) {
            for (Path file : files.filter(p -> p.getFileName().toString().matches("[0-9]{2}_.+\\.json")).toList()) {
                String raw = Files.readString(file);
                assertFalse(raw.contains("...") || raw.contains("\u2026"), "Ellipsis in " + file);
                JsonObject quest = JsonParser.parseString(raw).getAsJsonObject();
                String id = "learn_rematter/" + file.getFileName().toString().replace(".json", "");
                assertEquals("hexagon", quest.get("shape").getAsString(), id);
                assertEquals(0.9, quest.get("size").getAsDouble(), 1e-6, id);
                double x = quest.get("x").getAsDouble();
                double y = quest.get("y").getAsDouble();
                assertEquals(0.0, Math.abs(Math.IEEEremainder(x, 3.6)), 1e-6, id + " x");
                assertEquals(0.0, Math.abs(Math.IEEEremainder(y, 3.6)), 1e-6, id + " y");
                assertTrue(coords.add(x + "," + y), "Duplicate quest coordinate at " + x + "," + y);
                String icon = quest.get("icon").getAsString();
                if (icon.startsWith("rematter:")) {
                    assertTrue(Files.isRegularFile(rematterItems.resolve(icon.substring(9) + ".json")), "Missing icon " + icon);
                }
                assertFalse(quest.getAsJsonArray("tasks").isEmpty(), id);
                for (var taskEl : quest.getAsJsonArray("tasks")) {
                    var task = taskEl.getAsJsonObject();
                    if (task.get("type").getAsString().equals("advancement")) {
                        assertEquals("learn_rematter/14_lava_practice", id);
                        assertTrue(quest.get("optional").getAsBoolean());
                        assertEquals("rematter:progression/synthesized_lava", task.get("advancement").getAsString());
                        continue;
                    }
                    assertEquals("item", task.get("type").getAsString());
                    String item = task.get("item").getAsString();
                    if (item.startsWith("rematter:")) {
                        assertTrue(Files.isRegularFile(rematterItems.resolve(item.substring(9) + ".json")), "Missing task item " + item);
                        assertTrue(standardCraftOutputs.contains(item) || taggedObtainable.contains(item),
                                id + " task item " + item + " missing from standard recipes and embersquesting:obtainable");
                    }
                }
                Set<String> parentIds = new HashSet<>();
                quest.getAsJsonObject("deps").getAsJsonArray("all").forEach(d -> parentIds.add(d.getAsString()));
                deps.put(id, parentIds);
            }
        }
        assertEquals(14, deps.size());
        assertEquals(Set.of("learn_rematter/06_thermal"), deps.get("learn_rematter/14_lava_practice"));
        assertTrue(deps.values().stream().noneMatch(parents -> parents.contains("learn_rematter/14_lava_practice")));
        deps.forEach((id, parents) -> parents.forEach(parent -> assertTrue(deps.containsKey(parent), id + " -> unknown " + parent)));
        Set<String> resolved = new HashSet<>();
        boolean progressed;
        do {
            progressed = false;
            for (var entry : deps.entrySet()) {
                if (!resolved.contains(entry.getKey()) && resolved.containsAll(entry.getValue())) {
                    progressed |= resolved.add(entry.getKey());
                }
            }
        } while (progressed);
        assertEquals(deps.keySet(), resolved, "Quest dependency cycle detected");
    }

    private static void assertDisabled(JsonObject override) {
        var never = override.getAsJsonArray("neoforge:conditions").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(condition -> condition.get("type").getAsString().equals("neoforge:never")).toList();
        assertEquals(1, never.size());
        assertFalse(NeverCondition.CODEC.codec().parse(JsonOps.INSTANCE, never.get(0)).getOrThrow()
                .test(ICondition.IContext.EMPTY));
    }

    private static long minimumCost(long energy,MachineKind... machines) {
        var maximal=new dev.latrox.rematter.content.IndustrialPerformance(0,0,0,dev.latrox.rematter.content.IndustrialPerformance.MAX_PARTS_PER_TYPE-1,1);
        long best=Long.MAX_VALUE;
        for(var kind:machines)for(var upgrade:dev.latrox.rematter.content.MachineEnergyPolicy.Upgrade.values())for(int count=0;count<=4;count++)
            best=Math.min(best,dev.latrox.rematter.content.MachineEnergyPolicy.cost(energy,upgrade,count,kind,kind==MachineKind.INDUSTRIAL_REACTOR?maximal:null));
        return best;
    }

    private static ChemistryRegistry integration() {
        return ShippedChemistry.load(ShippedChemistry.MAIN_RESOURCES, DATAPACK);
    }

    private static JsonObject readFile(Path path) throws Exception {
        try (var reader = Files.newBufferedReader(path)) { return JsonParser.parseReader(reader).getAsJsonObject(); }
    }

    private static JsonObject read(String resource) throws Exception {
        var stream = ReForgingDataTest.class.getClassLoader().getResourceAsStream(resource);
        assertNotNull(stream, resource);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
