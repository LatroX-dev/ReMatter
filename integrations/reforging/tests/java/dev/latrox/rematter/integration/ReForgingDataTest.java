package dev.latrox.rematter.integration;

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
import java.util.HashMap;
import java.util.Locale;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.conditions.NeverCondition;
import static org.junit.jupiter.api.Assertions.*;

class ReForgingDataTest {
    private static final SubstanceKey CRYSTAL = SubstanceKey.of("reforging:crystalline_silica", Phase.SOLID);

    @Test
    void crystallineQuartzHasTheSameElementBalanceButASeparateIdentity() throws Exception {
        ChemistryRegistry registry = integration();
        assertNotEquals(BuiltinChemistry.SILICA, CRYSTAL);
        assertEquals(registry.elementAmounts(Map.of(BuiltinChemistry.SILICA, 1_000L)),
                registry.elementAmounts(Map.of(CRYSTAL, 1_000L)));
        assertEquals(Map.of(BuiltinChemistry.SILICA, 1_000L), registry.profile("minecraft:sand").orElseThrow().matter());
        assertEquals(Map.of(CRYSTAL, 1_000L), registry.profile("minecraft:quartz").orElseThrow().matter());
        assertTrue(registry.synthesisPolicy().allows("minecraft:quartz"));
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
        MatterInventory restored = MatterInventory.restore(inventory.snapshot());
        assertEquals(1_000L, restored.total());
        assertFalse(restored.apply(quartz.matterFor(2), Map.of()));
        assertTrue(restored.apply(quartz.matter(), Map.of()));
        assertEquals(0, restored.total());
        assertFalse(restored.apply(quartz.matter(), Map.of()));
    }

    @Test
    void allQuartzFormsShareTheProtectedIdentityAndExactBatchRatios() throws Exception {
        ChemistryRegistry registry = integration();
        Map<String, Long> amounts = Map.of(
                "minecraft:quartz", 1_000L, "minecraft:quartz_block", 4_000L,
                "enderio:powdered_quartz", 1_000L, "ftbic:quartz_dust", 1_000L,
                "mekanism:dust_quartz", 1_000L, "oritech:quartz_dust", 1_000L,
                "thaumaturge:nugget_quartz", 1_000L, "minecraft:nether_quartz_ore", 1_000L,
                "thaumaturge:ore_quartz", 1_000L, "thaumaturge:deepslate_ore_quartz", 1_000L);
        Set<String> actual = registry.profiles().stream().filter(profile -> profile.matter().containsKey(CRYSTAL))
                .map(MaterialProfile::itemId).collect(Collectors.toSet());
        assertEquals(amounts.keySet(), actual);
        amounts.forEach((item, amount) -> {
            var profile = registry.profile(item).orElseThrow();
            assertEquals(Map.of(CRYSTAL, amount), profile.matter(), item);
            assertEquals(item.equals("thaumaturge:nugget_quartz") ? 9 : 1, profile.itemCount(), item);
            assertEquals(!item.contains("ore"), registry.synthesisPolicy().allows(item), item);
        });
    }

    @Test
    void ordinarySandSoilAndRockCannotBecomeHighValueSilicon() throws Exception {
        ChemistryRegistry registry = integration();
        ProcessRecipe electrolysis = registry.recipe(SubstanceId.parse("rematter:silica_electrolysis")).orElseThrow();
        assertEquals(Map.of(CRYSTAL, 1_000L), electrolysis.inputs());
        assertEquals(Map.of(BuiltinChemistry.SILICON, 1_000L, BuiltinChemistry.OXYGEN, 1_000L), electrolysis.outputs());
        assertEquals(64_000, electrolysis.energy());
        assertEquals(400, electrolysis.ticks());
        assertEquals(registry.elementAmounts(electrolysis.inputs()), registry.elementAmounts(electrolysis.outputs()));
        Set<SubstanceKey> reachable = new HashSet<>(Set.of(BuiltinChemistry.SILICA, BuiltinChemistry.SOIL,
                BuiltinChemistry.ROCK, BuiltinChemistry.LAVA, BuiltinChemistry.WATER,
                BuiltinChemistry.HYDROGEN, BuiltinChemistry.OXYGEN, BuiltinChemistry.NITROGEN));
        boolean changed;
        do {
            changed = false;
            for (ProcessRecipe recipe : registry.processes()) {
                if (reachable.containsAll(recipe.inputs().keySet())) changed |= reachable.addAll(recipe.outputs().keySet());
            }
        } while (changed);
        assertFalse(reachable.contains(CRYSTAL));
        assertFalse(reachable.contains(BuiltinChemistry.SILICON));
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
        expected.addAll(Set.of("rematter:matter_cell_64k", "rematter:matter_cell_256k", "rematter:matter_cell_1m", "rematter:recovered_matter", "rematter:automation_pattern"));
        assertEquals(expected, actual);
        assertEquals(18, actual.size());
        for (String tag : Set.of("cant_be_scanned", "cant_be_disintegrated", "skip_calculation")) {
            JsonObject rule = read("data/replication/tags/item/" + tag + ".json");
            assertFalse(rule.get("replace").getAsBoolean());
            assertEquals(1, rule.getAsJsonArray("values").size());
            assertEquals("#reforging:rematter/stateful_machines", rule.getAsJsonArray("values").get(0).getAsString());
        }
    }

    @Test
    void standaloneCatalogueRemainsUnmodified() throws Exception {
        ChemistryRegistry before = catalogue(false);
        ChemistryRegistry pack = integration();
        ChemistryRegistry after = catalogue(false);
        assertFalse(after.isKnown(CRYSTAL));
        assertEquals(java.util.List.copyOf(before.profiles()), java.util.List.copyOf(after.profiles()));
        assertEquals(before.synthesisPolicy().entries(), after.synthesisPolicy().entries());
        pack.synthesisPolicy().entries().forEach((item, category) ->
                assertEquals(before.synthesisPolicy().entries().get(item), category,
                        "Pack integration must not widen synthesis: " + item));
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
        long hydrogenCost = minimumCost(registry.recipe(SubstanceId.parse("rematter:water_electrolysis")).orElseThrow().energy());
        assertTrue(hydrogenCost > 2_000, "1,000 hydrogen units return 2,000 FE in the target gas generator");
        long evaporationCost = minimumCost(registry.recipe(SubstanceId.parse("rematter:water_evaporation")).orElseThrow().energy());
        assertTrue(evaporationCost > 11_580, "Conservative upper bound for 1,000 steam in the target turbines");
        var stone = registry.profile("minecraft:stone").orElseThrow();
        var blending = registry.recipe(SubstanceId.parse("rematter:mineral_blending")).orElseThrow();
        var melting = registry.recipe(SubstanceId.parse("rematter:mineral_melting")).orElseThrow();
        var lava = registry.fluidProfile("minecraft:lava").orElseThrow();
        assertEquals(stone.matter(), blending.inputs());
        assertEquals(blending.outputs(), melting.inputs());
        assertEquals(melting.outputs(), lava.matter());
        long lavaCost = minimumCost(stone.decompositionEnergy()) + minimumCost(blending.energy())
                + minimumCost(melting.energy()) + minimumCost(lava.synthesisEnergy());
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
        assertEquals(andesiteIngredients, andesite.matterFor(2));
        assertRockConservation(registry, cobble, quartz, diorite);
        assertRockConservation(registry, diorite, quartz, granite);
        assertEquals(registry.profile("minecraft:sand").orElseThrow().matterFor(4),
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
        assertEquals(pearl.matterFor(1), profile(registry, "rematter:ender_pearl_powder").matterFor(1));
        assertEquals(pearl.matterFor(1), profile(registry, "jaopca:storage_blocks.ender_pearl").matterFor(1));
        assertFalse(registry.synthesisPolicy().allows("jaopca:storage_blocks.ender_pearl"));
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
        assertFalse(recovered.apply(pearl.matterFor(2), Map.of()));
        assertTrue(recovered.apply(pearl.matter(), Map.of()));
        assertEquals(0, recovered.total());
    }

    @Test
    void dimensionMaterialsAndAlloyRecipesConserveTheirSharedConstituents() throws Exception {
        ChemistryRegistry registry = integration();
        var end = SubstanceKey.of("rematter:ender_essence", Phase.SOLID);
        var nether = SubstanceKey.of("rematter:nether_essence", Phase.SOLID);
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
                assertFalse(registry.synthesisPolicy().allows("mekanism:" + form + "_" + material));
            }
        }
    }

    private static void assertRecipeMatter(ChemistryRegistry registry, String result, int count, String... ingredients) {
        Map<SubstanceKey, Long> supplied = new TreeMap<>();
        for (String item : ingredients) {
            profile(registry, item).matterFor(1).forEach((key, amount) -> supplied.merge(key, amount, Math::addExact));
        }
        assertEquals(supplied, profile(registry, result).matterFor(count), result);
    }

    private static MaterialProfile profile(ChemistryRegistry registry, String item) {
        return registry.profile(item).orElseThrow();
    }

    @Test
    void nativeAlloyRoutesDoNotDuplicateSharedIngredients() throws Exception {
        ChemistryRegistry registry = integration();
        assertRecipeMatter(registry, "oritech:adamant_ingot", 1, "alltheores:nickel_ingot", "minecraft:diamond");
        assertEquals(profile(registry, "minecraft:copper_ingot").matterFor(3),
                profile(registry, "nautec:aquarine_copper_ingot").matterFor(24));
        assertFalse(registry.synthesisPolicy().allows("nautec:aquarine_copper_ingot"));
        assertEquals(profile(registry, "alltheores:electrum_ingot").matter(),
                profile(registry, "oritech:electrum_ingot").matter());
        assertFalse(registry.synthesisPolicy().allows("alltheores:electrum_ingot"));
        assertFalse(registry.synthesisPolicy().allows("oritech:electrum_ingot"));
    }

    @Test
    void obsidianRecoveryRequiresFourDustsAndTheOldOneDustShortcutIsDisabled() throws Exception {
        ChemistryRegistry registry = integration();
        var block = registry.profile("minecraft:obsidian").orElseThrow();
        var dust = registry.profile("mekanism:dust_obsidian").orElseThrow();
        assertEquals(1, block.itemCount());
        assertEquals(1, dust.itemCount());
        assertEquals(block.matter(), dust.matterFor(4));
        MatterInventory inventory = new MatterInventory(100_000);
        assertTrue(inventory.apply(Map.of(), dust.matterFor(3)));
        assertFalse(inventory.apply(block.matter(), Map.of()));
        assertTrue(inventory.apply(Map.of(), dust.matter()));
        assertTrue(inventory.apply(block.matter(), Map.of()));
        assertEquals(0, inventory.total());
        JsonObject override = readFile(Path.of("integrations/reforging/datapack/data/occultism/recipe/crystallize/obsidian.json"));
        var conditions = override.getAsJsonArray("neoforge:conditions");
        assertEquals(1, conditions.size());
        JsonObject condition = conditions.get(0).getAsJsonObject();
        assertEquals("neoforge:never", condition.get("type").getAsString());
        assertFalse(NeverCondition.CODEC.codec().parse(JsonOps.INSTANCE, condition).getOrThrow()
                .test(ICondition.IContext.EMPTY));
    }

    private static long minimumCost(long energy) { return Math.multiplyExact(energy, 4) / 5; }

    private static ChemistryRegistry integration() throws Exception { return catalogue(true); }

    /** Reproduce ResourceManager priority first, then reject duplicate item targets as the runtime loader does. */
    private static ChemistryRegistry catalogue(boolean withPack) throws Exception {
        ChemistryRegistry defaults = BuiltinChemistry.create();
        Map<SubstanceId, SubstanceDefinition> substances = new TreeMap<>();
        Map<String, MaterialProfile> profiles = new TreeMap<>();
        Map<String, FluidProfile> fluids = new TreeMap<>();
        Map<SubstanceId, ProcessRecipe> processes = new TreeMap<>();
        Map<String, SynthesisCategory> permissions = new TreeMap<>(defaults.synthesisPolicy().entries());
        defaults.substances().forEach(value -> substances.put(value.id(), value));
        defaults.profiles().forEach(value -> profiles.put(value.itemId(), value));
        defaults.fluidProfiles().forEach(value -> fluids.put(value.fluidId(), value));
        defaults.processes().forEach(value -> processes.put(value.id(), value));
        Map<String, Path> documents = new TreeMap<>();
        overlay(documents, Path.of("src/main/resources"));
        if (withPack) overlay(documents, Path.of("integrations/reforging/datapack"));
        Map<String, String> targets = new HashMap<>();
        for (var document : documents.entrySet()) {
            String[] parts = document.getKey().split("/", 5);
            String directory = parts[3];
            String relative = parts[4];
            SubstanceId id = SubstanceId.parse(parts[1] + ":" + relative.substring(0, relative.length() - 5));
            JsonObject json = readFile(document.getValue());
            assertEquals(1, integer(json, "schema_version"));
            boolean disabled = json.has("disabled") && json.get("disabled").getAsBoolean();
            if (directory.equals("substance")) {
                if (disabled) { substances.remove(id); continue; }
                Map<SubstanceId, Long> composition = new TreeMap<>();
                json.getAsJsonObject("composition").entrySet().forEach(entry -> composition.put(SubstanceId.parse(entry.getKey()), entry.getValue().getAsBigDecimal().longValueExact()));
                Set<Phase> phases = new HashSet<>();
                json.getAsJsonArray("phases").forEach(value -> phases.add(Phase.valueOf(value.getAsString().toUpperCase(Locale.ROOT))));
                substances.put(id, new SubstanceDefinition(id, json.get("translation_key").getAsString(), json.has("symbol") ? json.get("symbol").getAsString() : "",
                        SubstanceKind.valueOf(json.get("kind").getAsString().toUpperCase(Locale.ROOT)), new Composition(composition), phases));
            } else if (directory.equals("material_profile")) {
                String item = json.get("item").getAsString();
                assertNull(targets.put(directory + ":" + item, document.getKey()), "Duplicate item target: " + item);
                if (disabled) { profiles.remove(item); permissions.remove(item); continue; }
                profiles.put(item, new MaterialProfile(item, json.has("item_count") ? integer(json, "item_count") : 1,
                        amounts(json, "matter"), number(json, "decomposition_energy"), number(json, "synthesis_energy"), integer(json, "ticks")));
            } else if (directory.equals("fluid_profile")) {
                String fluid = json.get("fluid").getAsString();
                assertNull(targets.put(directory + ":" + fluid, document.getKey()), "Duplicate fluid target: " + fluid);
                if (disabled) { fluids.remove(fluid); permissions.remove(fluid); continue; }
                fluids.put(fluid, new FluidProfile(fluid, integer(json, "amount"), amounts(json, "matter"),
                        number(json, "decomposition_energy"), number(json, "synthesis_energy"), integer(json, "ticks")));
            } else if (directory.equals("process")) {
                if (disabled) { processes.remove(id); continue; }
                processes.put(id, new ProcessRecipe(id, ProcessType.valueOf(json.get("type").getAsString().toUpperCase(Locale.ROOT)),
                        amounts(json, "inputs"), amounts(json, "outputs"), number(json, "energy"), integer(json, "ticks")));
            } else if (directory.equals("synthesis")) {
                String item = json.get("item").getAsString();
                assertNull(targets.put(directory + ":" + item, document.getKey()), "Duplicate synthesis target: " + item);
                if (disabled) permissions.remove(item);
                else permissions.put(item, SynthesisCategory.valueOf(json.get("category").getAsString().toUpperCase(Locale.ROOT)));
            } else fail("Unexpected chemistry directory: " + directory);
        }
        var builder = ChemistryRegistry.builder();
        substances.values().forEach(builder::addSubstance);
        profiles.values().forEach(builder::addProfile);
        fluids.values().forEach(builder::addFluidProfile);
        processes.values().forEach(builder::addProcess);
        permissions.forEach(builder::allowSynthesis);
        return builder.build();
    }

    private static void overlay(Map<String, Path> documents, Path root) throws Exception {
        try (var files = Files.walk(root.resolve("data"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                String relative = root.relativize(file).toString().replace(java.io.File.separatorChar, '/');
                String[] parts = relative.split("/", 5);
                if (parts.length == 5 && parts[2].equals("rematter")) documents.put(relative, file);
            }
        }
    }

    private static Map<SubstanceKey, Long> amounts(JsonObject json, String field) {
        Map<SubstanceKey, Long> result = new TreeMap<>();
        json.getAsJsonArray(field).forEach(value -> {
            JsonObject entry = value.getAsJsonObject();
            SubstanceKey key = SubstanceKey.of(entry.get("substance").getAsString(), Phase.valueOf(entry.get("phase").getAsString().toUpperCase(Locale.ROOT)));
            assertNull(result.put(key, number(entry, "amount")));
        });
        return result;
    }

    private static long number(JsonObject json, String key) { return json.get(key).getAsBigDecimal().longValueExact(); }
    private static int integer(JsonObject json, String key) { return Math.toIntExact(number(json, key)); }
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
