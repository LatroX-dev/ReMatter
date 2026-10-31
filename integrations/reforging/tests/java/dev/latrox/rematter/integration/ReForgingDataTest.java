package dev.latrox.rematter.integration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.content.MachineKind;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
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
        registry.profiles().stream().filter(profile -> !profile.itemId().equals("minecraft:quartz"))
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
    void replicationTagsProtectExactlyAllStatefulMachinesAndPreserveExistingMembers() throws Exception {
        JsonObject machines = read("data/reforging/tags/item/rematter/stateful_machines.json");
        assertFalse(machines.get("replace").getAsBoolean());
        Set<String> actual = new HashSet<>();
        for (var value : machines.getAsJsonArray("values")) {
            JsonObject entry = value.getAsJsonObject();
            assertFalse(entry.get("required").getAsBoolean());
            assertTrue(actual.add(entry.get("id").getAsString()), "Duplicate protected item");
        }
        Set<String> expected = Arrays.stream(MachineKind.values()).map(kind -> "rematter:" + kind.id()).collect(Collectors.toSet());
        assertEquals(expected, actual);
        assertEquals(8, actual.size());
        for (String tag : Set.of("cant_be_scanned", "cant_be_disintegrated", "skip_calculation")) {
            JsonObject rule = read("data/replication/tags/item/" + tag + ".json");
            assertFalse(rule.get("replace").getAsBoolean());
            assertEquals(1, rule.getAsJsonArray("values").size());
            assertEquals("#reforging:rematter/stateful_machines", rule.getAsJsonArray("values").get(0).getAsString());
        }
    }

    @Test
    void standaloneCatalogueRemainsUnmodified() throws Exception {
        integration();
        ChemistryRegistry standalone = BuiltinChemistry.create();
        assertFalse(standalone.isKnown(CRYSTAL));
        assertEquals(Map.of(BuiltinChemistry.SILICA, 1_000L), standalone.profile("minecraft:quartz").orElseThrow().matter());
    }

    private static ChemistryRegistry integration() throws Exception {
        ChemistryRegistry base = BuiltinChemistry.create();
        JsonObject definition = read("data/reforging/rematter/substance/crystalline_silica.json");
        JsonObject profile = read("data/reforging/rematter/material_profile/quartz.json");
        assertEquals(1, definition.get("schema_version").getAsInt());
        assertEquals(1, profile.get("schema_version").getAsInt());
        assertEquals("rematter", definition.get("required_mod").getAsString());
        assertEquals("rematter", profile.get("required_mod").getAsString());
        Map<SubstanceId, Long> elements = new TreeMap<>();
        definition.getAsJsonObject("composition").entrySet().forEach(entry ->
                elements.put(SubstanceId.parse(entry.getKey()), entry.getValue().getAsBigDecimal().longValueExact()));
        Set<Phase> phases = new HashSet<>();
        definition.getAsJsonArray("phases").forEach(value -> phases.add(Phase.valueOf(value.getAsString().toUpperCase(java.util.Locale.ROOT))));
        var substance = new SubstanceDefinition(CRYSTAL.id(), definition.get("translation_key").getAsString(),
                definition.get("symbol").getAsString(), SubstanceKind.valueOf(definition.get("kind").getAsString().toUpperCase(java.util.Locale.ROOT)),
                new Composition(elements), phases);
        Map<SubstanceKey, Long> matter = new TreeMap<>();
        profile.getAsJsonArray("matter").forEach(value -> {
            var entry = value.getAsJsonObject();
            var key = SubstanceKey.of(entry.get("substance").getAsString(), Phase.valueOf(entry.get("phase").getAsString().toUpperCase(java.util.Locale.ROOT)));
            assertNull(matter.put(key, entry.get("amount").getAsBigDecimal().longValueExact()));
        });
        var quartz = new MaterialProfile(profile.get("item").getAsString(), matter,
                profile.get("decomposition_energy").getAsBigDecimal().longValueExact(),
                profile.get("synthesis_energy").getAsBigDecimal().longValueExact(),
                profile.get("ticks").getAsBigDecimal().intValueExact());
        assertEquals("minecraft:quartz", quartz.itemId());
        var builder = ChemistryRegistry.builder();
        base.substances().forEach(builder::addSubstance);
        builder.addSubstance(substance);
        base.profiles().stream().filter(value -> !value.itemId().equals(quartz.itemId())).forEach(builder::addProfile);
        builder.addProfile(quartz);
        base.processes().forEach(builder::addProcess);
        base.synthesisPolicy().entries().forEach(builder::allowSynthesis);
        return builder.build();
    }

    private static JsonObject read(String resource) throws Exception {
        var stream = ReForgingDataTest.class.getClassLoader().getResourceAsStream(resource);
        assertNotNull(stream, resource);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
