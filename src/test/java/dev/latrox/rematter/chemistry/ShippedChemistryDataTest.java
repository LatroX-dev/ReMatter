package dev.latrox.rematter.chemistry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.latrox.rematter.automation.AutomationPort;
import dev.latrox.rematter.automation.AutomationRecipeCatalogue;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/** Data-only validation of the packaged optional definitions, without Minecraft or FML initialization. */
class ShippedChemistryDataTest {
    @Test
    void automationCatalogueRetainsEveryShippedBatchAndOnlyApprovedSynthesis() throws Exception {
        ChemistryRegistry registry = readShippedData().build();
        Map<String, AutomationPort.Recipe> actual = new HashMap<>();
        // Activate every optional definition without claiming its external item exists in a live game.
        for (AutomationPort.Recipe recipe : AutomationRecipeCatalogue.chemistry(registry, id -> true, id -> true)) {
            assertNull(actual.put(recipe.id(), recipe), "Duplicate automation recipe: " + recipe.id());
        }
        Set<String> expectedIds = new HashSet<>();
        for (MaterialProfile profile : registry.profiles()) {
            var item = Map.of(AutomationPort.Resource.item(profile.itemId()), (long) profile.itemCount());
            var matter = automationMatter(profile.matter());
            assertAutomationRecipe(actual, expectedIds, "decompose_item", profile.itemId(), "decomposer",
                    item, matter, profile.decompositionEnergy(), profile.ticks());
            if (registry.synthesisPolicy().allowsItem(profile.itemId())) {
                assertAutomationRecipe(actual, expectedIds, "synthesize_item", profile.itemId(), "material_synthesizer",
                        matter, item, profile.synthesisEnergy(), profile.ticks());
            }
        }
        for (FluidProfile profile : registry.fluidProfiles()) {
            var fluid = Map.of(AutomationPort.Resource.fluid(profile.fluidId()), (long) profile.amount());
            var matter = automationMatter(profile.matter());
            assertAutomationRecipe(actual, expectedIds, "decompose_fluid", profile.fluidId(), "decomposer",
                    fluid, matter, profile.decompositionEnergy(), profile.ticks());
            if (registry.synthesisPolicy().allowsFluid(profile.fluidId())) {
                assertAutomationRecipe(actual, expectedIds, "synthesize_fluid", profile.fluidId(), "material_synthesizer",
                        matter, fluid, profile.synthesisEnergy(), profile.ticks());
            }
        }
        for (ProcessRecipe process : registry.processes()) {
            assertAutomationRecipe(actual, expectedIds, "process", process.id().toString(),
                    process.type() == ProcessType.ELECTROLYSIS ? "electrolyzer" : "chemical_reactor",
                    automationMatter(process.inputs()), automationMatter(process.outputs()), process.energy(), process.ticks());
        }
        assertEquals(expectedIds, actual.keySet(), "No missing batches or extra synthesis permissions");
    }

    private static Map<AutomationPort.Resource, Long> automationMatter(Map<SubstanceKey, Long> matter) {
        Map<AutomationPort.Resource, Long> result = new TreeMap<>();
        matter.forEach((key, amount) -> result.put(new AutomationPort.Resource(AutomationPort.Kind.MATTER,
                key.id().toString(), key.phase().name().toLowerCase(Locale.ROOT)), amount));
        return result;
    }

    private static void assertAutomationRecipe(Map<String, AutomationPort.Recipe> actual, Set<String> expectedIds,
            String operation, String sourceId, String worker, Map<AutomationPort.Resource, Long> inputs,
            Map<AutomationPort.Resource, Long> outputs, long energy, int ticks) {
        String id = "rematter:automation/" + operation + "/" + sourceId.replace(':', '/');
        assertTrue(expectedIds.add(id), "Expected duplicate recipe: " + id);
        AutomationPort.Recipe recipe = actual.get(id);
        assertNotNull(recipe, "Missing automation recipe: " + id);
        assertAll(id,
                () -> assertEquals(worker, recipe.workerKind()),
                () -> assertEquals(inputs, recipe.inputs()),
                () -> assertEquals(outputs, recipe.outputs()),
                () -> assertEquals(energy, recipe.energy()),
                () -> assertEquals(ticks, recipe.ticks()));
    }

    @Test
    void allShippedDefinitionsBuildACompleteBalancedRegistry() throws Exception {
        ShippedData shipped = readShippedData();
        ChemistryRegistry defaults = BuiltinChemistry.create();
        ChemistryRegistry combined = shipped.build();

        assertTrue(shipped.documentCount() >= 144, "Keep the existing optional material coverage");
        assertTrue(shipped.substances().size() >= 8);
        assertTrue(shipped.profiles().size() >= 68);
        assertTrue(combined.substances().size() >= defaults.substances().size());
        assertTrue(combined.profiles().size() >= defaults.profiles().size());
        shipped.substances().forEach(value -> assertEquals(value, combined.definition(value.id()).orElseThrow()));
        shipped.profiles().forEach(value -> assertEquals(value.profile(), combined.profile(value.profile().itemId()).orElseThrow()));
        shipped.fluids().forEach(value -> assertEquals(value.profile(), combined.fluidProfile(value.profile().fluidId()).orElseThrow()));

        for (ProcessRecipe recipe : combined.processes()) {
            assertEquals(combined.elementAmounts(recipe.inputs()), combined.elementAmounts(recipe.outputs()),
                    recipe.id().toString());
        }
    }

    @Test
    void foreignProfilesAndPermissionsHaveMatchingExplicitDependencies() throws Exception {
        ShippedData shipped = readShippedData();
        Map<String, OptionalProfile> profiles = new HashMap<>();
        shipped.profiles().forEach(profile -> assertNull(profiles.put(profile.profile().itemId(), profile),
                "Duplicate foreign material profile"));
        Set<String> approved = new HashSet<>();
        for (OptionalPermission permission : shipped.permissions()) {
            assertTrue(approved.add(permission.item()), "Duplicate approval for " + permission.item());
            assertTrue(permission.category().permitted(), permission.item());
            if (permission.category() == SynthesisCategory.RAW_MATERIAL || permission.category() == SynthesisCategory.NATURAL_MATERIAL) {
                OptionalProfile profile = profiles.get(permission.item());
                if (profile == null) assertTrue(BuiltinChemistry.create().profile(permission.item()).isPresent(), "Permission without a profile");
                else assertEquals(profile.requiredMod(), permission.requiredMod(), permission.item());
            }
        }
        // Decomposition-only components and ores deliberately need no synthesis entry.
        for (OptionalProfile profile : shipped.profiles()) {
            String namespace = SubstanceId.parse(profile.profile().itemId()).namespace();
            if (!namespace.equals("minecraft") && !namespace.equals("rematter")) {
                assertFalse(profile.requiredMod().isBlank(), "Foreign profile requires an explicit dependency");
            }
        }
    }

    @Test
    void absentOptionalModsDoNotGrantTheirProfilesOrPermissions() throws Exception {
        ShippedData shipped = readShippedData();
        ChemistryRegistry defaults = BuiltinChemistry.create();
        for (OptionalProfile profile : shipped.profiles()) {
            String namespace = SubstanceId.parse(profile.profile().itemId()).namespace();
            if (namespace.equals("minecraft") || namespace.equals("rematter")) continue;
            assertTrue(defaults.profile(profile.profile().itemId()).isEmpty(), profile.profile().itemId());
            assertFalse(defaults.synthesisPolicy().allows(profile.profile().itemId()), profile.profile().itemId());
        }
    }

    @Test
    void shippedMetalFormsHaveConsistentMatterValuesAcrossIntegrations() throws Exception {
        ShippedData shipped = readShippedData();
        ChemistryRegistry registry = shipped.build();
        Map<SubstanceKey, Long> amountPerForm = new HashMap<>();
        for (OptionalProfile optional : shipped.profiles()) {
            MaterialProfile profile = optional.profile();
            if (profile.matter().size() != 1) continue;
            var entry = profile.matter().entrySet().iterator().next();
            SubstanceDefinition substance = registry.definition(entry.getKey().id()).orElseThrow();
            if (substance.kind() != SubstanceKind.ELEMENT || entry.getKey().phase() != Phase.SOLID) continue;
            String path = SubstanceId.parse(profile.itemId()).path();
            String element = substance.id().path();
            if (!Set.of(element + "_ingot", element + "_dust", "ingot_" + element, "dust_" + element).contains(path)
                    || profile.itemCount() != 1) continue;
            assertEquals(Composition.element(substance.id()), substance.composition(), profile.itemId());
            Long previous = amountPerForm.putIfAbsent(entry.getKey(), entry.getValue());
            if (previous != null) {
                assertEquals(previous, entry.getValue(), "Unequal dust/ingot values for " + profile.itemId());
            }
            assertEquals(BuiltinChemistry.MATERIAL_UNIT, entry.getValue().longValue(), profile.itemId());
        }
    }

    @Test
    void eachShippedMaterialRoundTripsThroughPersistenceWithoutChangingUnrelatedMatter() throws Exception {
        ShippedData shipped = readShippedData();
        ChemistryRegistry registry = shipped.build();
        for (OptionalProfile optional : shipped.profiles()) {
            MaterialProfile profile = optional.profile();
            long materialTotal = MatterAmounts.total(profile.matter());
            MatterInventory inventory = new MatterInventory(Math.addExact(materialTotal, 17));
            inventory.insert(BuiltinChemistry.WATER, 17);
            Map<SubstanceKey, Long> before = inventory.contents();

            assertTrue(inventory.apply(Map.of(), profile.matter()), profile.itemId());
            assertEquals(Math.addExact(materialTotal, 17), inventory.total());
            inventory = MatterInventory.restore(inventory.snapshot());
            assertTrue(inventory.apply(profile.matter(), Map.of()), profile.itemId());
            assertEquals(before, inventory.contents(), profile.itemId());
            assertEquals(17, inventory.total(), profile.itemId());
        }
    }

    @Test
    void combinedBaseAndOptionalInventoryKeepsEveryAliasQuantityAcrossRestart() throws Exception {
        ShippedData shipped = readShippedData();
        Collection<MaterialProfile> allProfiles = shipped.build().profiles();
        Map<SubstanceKey, Long> expected = new TreeMap<>();
        for (MaterialProfile profile : allProfiles) {
            profile.matter().forEach((key, amount) -> expected.merge(key, amount, Math::addExact));
        }
        long expectedTotal = MatterAmounts.total(expected);
        MatterInventory inventory = new MatterInventory(expectedTotal);
        for (MaterialProfile profile : allProfiles) {
            assertTrue(inventory.apply(Map.of(), profile.matter()), profile.itemId());
        }
        MatterInventory restored = MatterInventory.restore(inventory.snapshot());
        assertEquals(expected, restored.contents());
        assertEquals(expectedTotal, restored.total());
        assertEquals(0, restored.freeSpace());
    }

    private static ShippedData readShippedData() throws IOException, URISyntaxException {
        Path root = Path.of("src/main/resources/data/rematter/rematter");
        assertTrue(Files.isDirectory(root), "Shipped main chemistry resources are missing");
        List<SubstanceDefinition> substances = new ArrayList<>();
        List<OptionalProfile> profiles = new ArrayList<>();
        List<OptionalFluid> fluids = new ArrayList<>();
        List<ProcessRecipe> processes = new ArrayList<>();
        List<OptionalPermission> permissions = new ArrayList<>();
        Set<String> disabledSynthesis = new HashSet<>();
        Set<SubstanceId> substanceIds = new HashSet<>();
        List<Path> documents;
        try (var paths = Files.walk(root)) {
            documents = paths.filter(path -> path.toString().endsWith(".json")).sorted().toList();
        }
        for (Path document : documents) {
            JsonObject json;
            try (Reader reader = Files.newBufferedReader(document, StandardCharsets.UTF_8)) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }
            assertEquals(1, json.get("schema_version").getAsBigDecimal().intValueExact(), document.toString());
            Path relative = root.relativize(document);
            String directory = relative.getName(0).toString();
            switch (directory) {
                case "substance" -> {
                    String path = relative.subpath(1, relative.getNameCount()).toString().replace('\\', '/');
                    SubstanceId id = SubstanceId.parse("rematter:" + path.substring(0, path.length() - 5));
                    assertTrue(substanceIds.add(id), "Duplicate shipped substance ID: " + id);
                    Map<SubstanceId, Long> composition = new TreeMap<>();
                    json.getAsJsonObject("composition").entrySet().forEach(entry ->
                            composition.put(SubstanceId.parse(entry.getKey()), exactLong(entry.getValue())));
                    Set<Phase> phases = new HashSet<>();
                    json.getAsJsonArray("phases").forEach(phase -> phases.add(Phase.valueOf(phase.getAsString().toUpperCase(Locale.ROOT))));
                    substances.add(new SubstanceDefinition(id, json.get("translation_key").getAsString(),
                            json.get("symbol").getAsString(),
                            SubstanceKind.valueOf(json.get("kind").getAsString().toUpperCase(Locale.ROOT)),
                            new Composition(composition), phases));
                }
                case "material_profile" -> {
                    String dependency = requiredMod(json, json.get("item").getAsString(), document);
                    Map<SubstanceKey, Long> matter = new TreeMap<>();
                    for (JsonElement value : json.getAsJsonArray("matter")) {
                        JsonObject entry = value.getAsJsonObject();
                        SubstanceKey key = SubstanceKey.of(entry.get("substance").getAsString(),
                                Phase.valueOf(entry.get("phase").getAsString().toUpperCase(Locale.ROOT)));
                        assertNull(matter.put(key, exactLong(entry.get("amount"))), "Duplicate profile substance: " + document);
                    }
                    MaterialProfile profile = new MaterialProfile(json.get("item").getAsString(),
                            json.has("item_count") ? Math.toIntExact(exactLong(json.get("item_count"))) : 1, matter,
                            exactLong(json.get("decomposition_energy")), exactLong(json.get("synthesis_energy")),
                            Math.toIntExact(exactLong(json.get("ticks"))));
                    profiles.add(new OptionalProfile(dependency, profile));
                }
                case "fluid_profile" -> {
                    String id = json.get("fluid").getAsString();
                    fluids.add(new OptionalFluid(requiredMod(json, id, document), new FluidProfile(id,
                            Math.toIntExact(exactLong(json.get("amount"))), amounts(json, "matter"),
                            exactLong(json.get("decomposition_energy")), exactLong(json.get("synthesis_energy")),
                            Math.toIntExact(exactLong(json.get("ticks"))))));
                }
                case "process" -> {
                    String path = relative.subpath(1, relative.getNameCount()).toString().replace('\\', '/');
                    processes.add(new ProcessRecipe(SubstanceId.parse("rematter:" + path.substring(0, path.length() - 5)),
                            ProcessType.valueOf(json.get("type").getAsString().toUpperCase(Locale.ROOT)),
                            amounts(json, "inputs"), amounts(json, "outputs"), exactLong(json.get("energy")),
                            Math.toIntExact(exactLong(json.get("ticks")))));
                }
                case "synthesis" -> {
                    String dependency = requiredMod(json, json.get("item").getAsString(), document);
                    if (json.has("disabled") && json.get("disabled").getAsBoolean()) {
                        assertTrue(disabledSynthesis.add(json.get("item").getAsString()), "Duplicate disabled permission");
                    } else permissions.add(new OptionalPermission(dependency, json.get("item").getAsString(),
                            SynthesisCategory.valueOf(json.get("category").getAsString().toUpperCase(Locale.ROOT))));
                }
                default -> fail("Unexpected shipped chemistry document: " + relative);
            }
        }
        return new ShippedData(documents.size(), substances, profiles, fluids, processes, permissions, disabledSynthesis);
    }

    private static String requiredMod(JsonObject json, String target, Path document) {
        String namespace = SubstanceId.parse(target).namespace();
        if (!json.has("required_mod") && (namespace.equals("minecraft") || namespace.equals("rematter"))) return "";
        assertTrue(json.has("required_mod"), "Missing optional dependency guard: " + document);
        String dependency = json.get("required_mod").getAsString();
        assertFalse(dependency.isBlank(), "Empty dependency guard: " + document);
        return dependency;
    }

    private static Map<SubstanceKey, Long> amounts(JsonObject json, String field) {
        Map<SubstanceKey, Long> result = new TreeMap<>();
        for (JsonElement value : json.getAsJsonArray(field)) {
            JsonObject entry = value.getAsJsonObject();
            assertNull(result.put(SubstanceKey.of(entry.get("substance").getAsString(),
                    Phase.valueOf(entry.get("phase").getAsString().toUpperCase(Locale.ROOT))), exactLong(entry.get("amount"))));
        }
        return result;
    }

    private static long exactLong(JsonElement value) {
        return value.getAsBigDecimal().longValueExact();
    }

    private record OptionalProfile(String requiredMod, MaterialProfile profile) {}
    private record OptionalFluid(String requiredMod, FluidProfile profile) {}
    private record OptionalPermission(String requiredMod, String item, SynthesisCategory category) {}
    private record ShippedData(int documentCount, Collection<SubstanceDefinition> substances,
                               Collection<OptionalProfile> profiles, Collection<OptionalFluid> fluids,
                               Collection<ProcessRecipe> processes, Collection<OptionalPermission> permissions, Set<String> disabledSynthesis) {
        ChemistryRegistry build() {
            var defaults = BuiltinChemistry.create();
            Map<SubstanceId, SubstanceDefinition> allSubstances = new TreeMap<>();
            Map<String, MaterialProfile> allProfiles = new TreeMap<>();
            Map<String, FluidProfile> allFluids = new TreeMap<>();
            Map<SubstanceId, ProcessRecipe> allProcesses = new TreeMap<>();
            Map<String, SynthesisCategory> allPermissions = new TreeMap<>(defaults.synthesisPolicy().entries());
            defaults.substances().forEach(value -> allSubstances.put(value.id(), value));
            defaults.profiles().forEach(value -> allProfiles.put(value.itemId(), value));
            defaults.fluidProfiles().forEach(value -> allFluids.put(value.fluidId(), value));
            defaults.processes().forEach(value -> allProcesses.put(value.id(), value));
            Set<String> targets = new HashSet<>();
            substances.forEach(value -> allSubstances.put(value.id(), value));
            profiles.forEach(value -> { assertTrue(targets.add("item:" + value.profile().itemId())); allProfiles.put(value.profile().itemId(), value.profile()); });
            fluids.forEach(value -> { assertTrue(targets.add("fluid:" + value.profile().fluidId())); allFluids.put(value.profile().fluidId(), value.profile()); });
            processes.forEach(value -> { assertTrue(targets.add("process:" + value.id())); allProcesses.put(value.id(), value); });
            permissions.forEach(value -> { assertTrue(targets.add("synthesis:" + value.item())); allPermissions.put(value.item(), value.category()); });
            disabledSynthesis.forEach(id -> { assertTrue(targets.add("synthesis:" + id)); allPermissions.remove(id); });
            ChemistryRegistry.Builder builder = ChemistryRegistry.builder();
            allSubstances.values().forEach(builder::addSubstance);
            allProfiles.values().forEach(builder::addProfile);
            allFluids.values().forEach(builder::addFluidProfile);
            allProcesses.values().forEach(builder::addProcess);
            allPermissions.forEach(builder::allowSynthesis);
            return builder.build();
        }
    }
}
