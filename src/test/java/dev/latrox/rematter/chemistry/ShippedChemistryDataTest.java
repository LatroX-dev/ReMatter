package dev.latrox.rematter.chemistry;

import com.google.gson.JsonObject;
import dev.latrox.rematter.automation.AutomationPort;
import dev.latrox.rematter.automation.AutomationRecipeCatalogue;
import dev.latrox.rematter.data.ChemistryDocuments;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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
    /** One ingot or dust of a pure element. */
    private static final long MATERIAL_UNIT = 1_000;
    @Test
    void automationCatalogueRetainsEveryShippedBatchAndOnlyApprovedSynthesis() throws Exception {
        ChemistryRegistry registry = ShippedChemistry.standalone();
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
                assertAutomationRecipe(actual, expectedIds, "synthesize_item", profile.itemId(), registry.synthesisPolicy().requirement(profile.itemId()).machine(),
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
                    dev.latrox.rematter.content.MachineRouting.primary(process.type()).id(),
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
        ChemistryRegistry combined = ShippedChemistry.standalone();

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
                assertNotNull(profile, "Permission without a profile: " + permission.item());
                assertEquals(profile.requiredMod(), permission.requiredMod(), permission.item());
            }
            if (permission.category() == SynthesisCategory.FLUID) {
                OptionalFluid fluid = shipped.fluids().stream().filter(value -> value.profile().fluidId().equals(permission.item()))
                        .findFirst().orElseThrow(() -> new AssertionError("Permission without a fluid profile: " + permission.item()));
                assertEquals(fluid.requiredMod(), permission.requiredMod(), permission.item());
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
    void builtInCoreLeavesEveryProfileAndPermissionToTheGuardedCatalogueData() {
        ChemistryRegistry core = BuiltinChemistry.create();
        assertTrue(core.profiles().isEmpty(), "Item profiles belong to the material catalogue");
        assertTrue(core.fluidProfiles().isEmpty(), "Fluid profiles belong to the material catalogue");
        assertTrue(core.synthesisPolicy().entries().isEmpty(), "Synthesis permissions belong to the material catalogue");
        assertFalse(core.processes().isEmpty());
    }

    @Test
    void shippedMetalFormsHaveConsistentMatterValuesAcrossIntegrations() throws Exception {
        ShippedData shipped = readShippedData();
        ChemistryRegistry registry = ShippedChemistry.standalone();
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
            assertEquals(new Composition(Map.of(substance.id(), 1L)), substance.composition(), profile.itemId());
            Long previous = amountPerForm.putIfAbsent(entry.getKey(), entry.getValue());
            if (previous != null) {
                assertEquals(previous, entry.getValue(), "Unequal dust/ingot values for " + profile.itemId());
            }
            assertEquals(MATERIAL_UNIT, entry.getValue().longValue(), profile.itemId());
        }
    }

    @Test
    void eachShippedMaterialRoundTripsThroughPersistenceWithoutChangingUnrelatedMatter() throws Exception {
        ShippedData shipped = readShippedData();
        ChemistryRegistry registry = ShippedChemistry.standalone();
        for (OptionalProfile optional : shipped.profiles()) {
            MaterialProfile profile = optional.profile();
            long materialTotal = MatterAmounts.total(profile.matter());
            MatterInventory inventory = new MatterInventory(Math.addExact(materialTotal, 17));
            inventory.insert(BuiltinChemistry.WATER, 17);
            Map<SubstanceKey, Long> before = inventory.contents();

            assertTrue(inventory.apply(Map.of(), profile.matter()), profile.itemId());
            assertEquals(Math.addExact(materialTotal, 17), inventory.total());
            inventory = MatterInventory.restore(inventory.snapshot(), Integer.MAX_VALUE);
            assertTrue(inventory.apply(profile.matter(), Map.of()), profile.itemId());
            assertEquals(before, inventory.contents(), profile.itemId());
            assertEquals(17, inventory.total(), profile.itemId());
        }
    }

    @Test
    void combinedBaseAndOptionalInventoryKeepsEveryAliasQuantityAcrossRestart() throws Exception {
        ShippedData shipped = readShippedData();
        Collection<MaterialProfile> allProfiles = ShippedChemistry.standalone().profiles();
        Map<SubstanceKey, Long> expected = new TreeMap<>();
        for (MaterialProfile profile : allProfiles) {
            profile.matter().forEach((key, amount) -> expected.merge(key, amount, Math::addExact));
        }
        long expectedTotal = MatterAmounts.total(expected);
        MatterInventory inventory = new MatterInventory(expectedTotal);
        for (MaterialProfile profile : allProfiles) {
            assertTrue(inventory.apply(Map.of(), profile.matter()), profile.itemId());
        }
        MatterInventory restored = MatterInventory.restore(inventory.snapshot(), Integer.MAX_VALUE);
        assertEquals(expected, restored.contents());
        assertEquals(expectedTotal, restored.total());
        assertEquals(0, restored.freeSpace());
    }

    /** Every shipped document, read by the reload listener's rules, with the dependency guard each one declares. */
    private static ShippedData readShippedData() throws IOException {
        Path root = ShippedChemistry.MAIN_RESOURCES.resolve("data/rematter/rematter");
        assertTrue(Files.isDirectory(root), "Shipped main chemistry resources are missing");
        List<SubstanceDefinition> substances = new ArrayList<>();
        List<OptionalProfile> profiles = new ArrayList<>();
        List<OptionalFluid> fluids = new ArrayList<>();
        List<OptionalPermission> permissions = new ArrayList<>();
        Set<String> disabledSynthesis = new HashSet<>();
        Set<SubstanceId> substanceIds = new HashSet<>();
        List<Path> documents;
        try (var paths = Files.walk(root)) {
            documents = paths.filter(path -> path.toString().endsWith(".json")).sorted().toList();
        }
        for (Path document : documents) {
            JsonObject json = ShippedChemistry.read(document);
            ChemistryDocuments.requireSchema(json);
            Path relative = root.relativize(document);
            String directory = relative.getName(0).toString();
            switch (directory) {
                case "substance" -> {
                    SubstanceId id = idOf(relative);
                    assertTrue(substanceIds.add(id), "Duplicate shipped substance ID: " + id);
                    substances.add(ChemistryDocuments.substance(id, json));
                }
                case "material_profile" -> {
                    MaterialProfile profile = ChemistryDocuments.materialProfile(json);
                    profiles.add(new OptionalProfile(requiredMod(json, profile.itemId(), document), profile));
                }
                case "fluid_profile" -> {
                    FluidProfile profile = ChemistryDocuments.fluidProfile(json);
                    fluids.add(new OptionalFluid(requiredMod(json, profile.fluidId(), document), profile));
                }
                case "process" -> assertEquals(ChemistryDocuments.process(idOf(relative), json),
                        ShippedChemistry.standalone().recipe(idOf(relative)).orElseThrow(), relative.toString());
                case "environment_source" -> assertEquals(ChemistryDocuments.environmentSource(idOf(relative),json),
                        ShippedChemistry.standalone().environmentSource(idOf(relative)).orElseThrow());
                case "synthesis" -> {
                    String item = ChemistryDocuments.string(json, "item");
                    String dependency = requiredMod(json, item, document);
                    if (ChemistryDocuments.disabled(json)||!ChemistryDocuments.modsMet(json,mod->true)) assertTrue(disabledSynthesis.add(item), "Duplicate disabled permission");
                    else permissions.add(new OptionalPermission(dependency, item, ChemistryDocuments.synthesisCategory(json)));
                }
                default -> fail("Unexpected shipped chemistry document: " + relative);
            }
        }
        return new ShippedData(documents.size(), substances, profiles, fluids, permissions);
    }

    private static SubstanceId idOf(Path relative) {
        String path = relative.subpath(1, relative.getNameCount()).toString().replace('\\', '/');
        return SubstanceId.parse("rematter:" + path.substring(0, path.length() - 5));
    }

    private static String requiredMod(JsonObject json, String target, Path document) {
        String namespace = SubstanceId.parse(target).namespace();
        if (!json.has("required_mod") && (namespace.equals("minecraft") || namespace.equals("rematter"))) return "";
        assertTrue(json.has("required_mod"), "Missing optional dependency guard: " + document);
        String dependency = json.get("required_mod").getAsString();
        assertFalse(dependency.isBlank(), "Empty dependency guard: " + document);
        return dependency;
    }

    private record OptionalProfile(String requiredMod, MaterialProfile profile) {}
    private record OptionalFluid(String requiredMod, FluidProfile profile) {}
    private record OptionalPermission(String requiredMod, String item, SynthesisCategory category) {}
    private record ShippedData(int documentCount, Collection<SubstanceDefinition> substances,
                               Collection<OptionalProfile> profiles, Collection<OptionalFluid> fluids,
                               Collection<OptionalPermission> permissions) {}
}
