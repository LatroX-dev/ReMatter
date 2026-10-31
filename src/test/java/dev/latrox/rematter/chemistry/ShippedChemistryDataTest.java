package dev.latrox.rematter.chemistry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
    void allShippedDefinitionsBuildACompleteBalancedRegistry() throws Exception {
        ShippedData shipped = readShippedData();
        ChemistryRegistry defaults = BuiltinChemistry.create();
        ChemistryRegistry combined = shipped.build();

        assertEquals(144, shipped.documentCount());
        assertEquals(8, shipped.substances().size());
        assertEquals(68, shipped.profiles().size());
        assertEquals(68, shipped.permissions().size());
        assertEquals(defaults.substances().size() + shipped.substances().size(), combined.substances().size());
        assertEquals(defaults.profiles().size() + shipped.profiles().size(), combined.profiles().size());
        assertEquals(defaults.synthesisPolicy().entries().size() + shipped.permissions().size(),
                combined.synthesisPolicy().entries().size());
        assertEquals(defaults.processes().size(), combined.processes().size());

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
            OptionalProfile profile = profiles.get(permission.item());
            assertNotNull(profile, "Permission without a material profile: " + permission.item());
            assertEquals(profile.requiredMod(), permission.requiredMod(), permission.item());
            assertEquals(SynthesisCategory.RAW_MATERIAL, permission.category(), permission.item());
            String namespace = SubstanceId.parse(permission.item()).namespace();
            assertNotEquals("minecraft", namespace, "Optional data must not shadow a base-game profile");
            assertNotEquals("rematter", namespace, "Optional material entries must name the external owner");
            assertEquals(namespace, permission.requiredMod(), "Dependency does not match the actual item owner");
        }
        assertEquals(profiles.keySet(), approved, "Every shipped material requires a separate explicit permission");
    }

    @Test
    void absentOptionalModsDoNotGrantTheirProfilesOrPermissions() throws Exception {
        ShippedData shipped = readShippedData();
        ChemistryRegistry defaults = BuiltinChemistry.create();
        for (OptionalProfile profile : shipped.profiles()) {
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
            assertEquals(1, profile.matter().size(), "A pure metal profile has one constituent");
            var entry = profile.matter().entrySet().iterator().next();
            assertEquals(Phase.SOLID, entry.getKey().phase(), profile.itemId());
            SubstanceDefinition substance = registry.definition(entry.getKey().id()).orElseThrow();
            assertEquals(SubstanceKind.ELEMENT, substance.kind(), profile.itemId());
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
            assertTrue(registry.synthesisPolicy().allows(profile.itemId()), profile.itemId());
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
        var resource = ShippedChemistryDataTest.class.getClassLoader().getResource("data/rematter/rematter");
        assertNotNull(resource, "Shipped chemistry resources are missing from the test runtime");
        Path root = Path.of(resource.toURI());
        List<SubstanceDefinition> substances = new ArrayList<>();
        List<OptionalProfile> profiles = new ArrayList<>();
        List<OptionalPermission> permissions = new ArrayList<>();
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
                    String dependency = requiredMod(json, document);
                    Map<SubstanceKey, Long> matter = new TreeMap<>();
                    for (JsonElement value : json.getAsJsonArray("matter")) {
                        JsonObject entry = value.getAsJsonObject();
                        SubstanceKey key = SubstanceKey.of(entry.get("substance").getAsString(),
                                Phase.valueOf(entry.get("phase").getAsString().toUpperCase(Locale.ROOT)));
                        assertNull(matter.put(key, exactLong(entry.get("amount"))), "Duplicate profile substance: " + document);
                    }
                    MaterialProfile profile = new MaterialProfile(json.get("item").getAsString(), matter,
                            exactLong(json.get("decomposition_energy")), exactLong(json.get("synthesis_energy")),
                            Math.toIntExact(exactLong(json.get("ticks"))));
                    profiles.add(new OptionalProfile(dependency, profile));
                }
                case "synthesis" -> permissions.add(new OptionalPermission(requiredMod(json, document),
                        json.get("item").getAsString(),
                        SynthesisCategory.valueOf(json.get("category").getAsString().toUpperCase(Locale.ROOT))));
                default -> fail("Unexpected shipped chemistry document: " + relative);
            }
        }
        return new ShippedData(documents.size(), substances, profiles, permissions);
    }

    private static String requiredMod(JsonObject json, Path document) {
        assertTrue(json.has("required_mod"), "Missing optional dependency guard: " + document);
        String dependency = json.get("required_mod").getAsString();
        assertFalse(dependency.isBlank(), "Empty dependency guard: " + document);
        return dependency;
    }

    private static long exactLong(JsonElement value) {
        return value.getAsBigDecimal().longValueExact();
    }

    private record OptionalProfile(String requiredMod, MaterialProfile profile) {}
    private record OptionalPermission(String requiredMod, String item, SynthesisCategory category) {}
    private record ShippedData(int documentCount, Collection<SubstanceDefinition> substances,
                               Collection<OptionalProfile> profiles, Collection<OptionalPermission> permissions) {
        ChemistryRegistry build() {
            ChemistryRegistry.Builder builder = BuiltinChemistry.create().toBuilder();
            substances.forEach(builder::addSubstance);
            profiles.forEach(profile -> builder.addProfile(profile.profile()));
            permissions.forEach(permission -> builder.allowSynthesis(permission.item(), permission.category()));
            return builder.build();
        }
    }
}
