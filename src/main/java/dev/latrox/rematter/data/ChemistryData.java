package dev.latrox.rematter.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.network.CatalogWireCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.function.BiConsumer;

/** Builds and validates a complete immutable catalogue before publishing a reload. */
public final class ChemistryData extends SimplePreparableReloadListener<ChemistryRegistry> {
    private static final int MAX_DOCUMENT_CHARS = 262_144;
    private static volatile ChemistryRegistry current = BuiltinChemistry.create();

    public static ChemistryRegistry current() {
        return current;
    }

    public static void register(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "chemistry"), new ChemistryData());
    }

    @Override
    protected ChemistryRegistry prepare(ResourceManager manager, ProfilerFiller profiler) {
        ChemistryRegistry defaults = BuiltinChemistry.create();
        Map<SubstanceId, SubstanceDefinition> substances = new TreeMap<>();
        Map<String, MaterialProfile> profiles = new TreeMap<>();
        Map<String, FluidProfile> fluids = new TreeMap<>();
        Map<SubstanceId, ProcessRecipe> processes = new TreeMap<>();
        Map<String, SynthesisCategory> synthesis = new TreeMap<>(defaults.synthesisPolicy().entries());
        Map<String, Identifier> profileSources = new HashMap<>();
        Map<String, Identifier> fluidSources = new HashMap<>();
        Map<String, Identifier> synthesisSources = new HashMap<>();
        defaults.substances().forEach(s -> substances.put(s.id(), s));
        defaults.profiles().forEach(p -> profiles.put(p.itemId(), p));
        defaults.fluidProfiles().forEach(p -> fluids.put(p.fluidId(), p));
        defaults.processes().forEach(p -> processes.put(p.id(), p));

        readDirectory(manager, "rematter/substance", (id, json) -> {
            SubstanceId key = SubstanceId.parse(id.toString());
            if (disabled(json)) {
                substances.remove(key);
                return;
            }
            Map<SubstanceId, Long> composition = new TreeMap<>();
            object(json, "composition").entrySet().forEach(e ->
                    composition.put(SubstanceId.parse(e.getKey()), positiveLong(e.getValue(), "composition")));
            EnumSet<Phase> phases = EnumSet.noneOf(Phase.class);
            for (JsonElement phase : array(json, "phases")) {
                phases.add(enumValue(Phase.class, phase.getAsString()));
            }
            substances.put(key, new SubstanceDefinition(key, string(json, "translation_key"),
                    json.has("symbol") ? string(json, "symbol") : "", enumValue(SubstanceKind.class, string(json, "kind")),
                    new Composition(composition), phases));
        });

        readDirectory(manager, "rematter/material_profile", (id, json) -> {
            String item = string(json, "item");
            requireUniqueTarget(profileSources, item, id);
            if (disabled(json)) {
                profiles.remove(item);
                if (synthesis.get(item) == SynthesisCategory.RAW_MATERIAL || synthesis.get(item) == SynthesisCategory.NATURAL_MATERIAL) {
                    synthesis.remove(item);
                }
                return;
            }
            Identifier itemId = Identifier.parse(item);
            if (!BuiltInRegistries.ITEM.containsKey(itemId) || item.equals("minecraft:air")) {
                throw new IllegalArgumentException("Unknown item " + item + "; optional profiles need required_mod");
            }
            profiles.put(item, new MaterialProfile(item,
                    json.has("item_count") ? Math.toIntExact(positiveLong(json, "item_count")) : 1, amounts(json, "matter"),
                    positiveLong(json, "decomposition_energy"), positiveLong(json, "synthesis_energy"),
                    Math.toIntExact(positiveLong(json, "ticks"))));
        });

        readDirectory(manager, "rematter/fluid_profile", (id, json) -> {
            String fluid = string(json, "fluid");
            requireUniqueTarget(fluidSources, fluid, id);
            if (disabled(json)) {
                fluids.remove(fluid);
                if (synthesis.get(fluid) == SynthesisCategory.FLUID) synthesis.remove(fluid);
                return;
            }
            fluids.put(fluid, new FluidProfile(fluid, Math.toIntExact(positiveLong(json, "amount")),
                    amounts(json, "matter"), positiveLong(json, "decomposition_energy"),
                    positiveLong(json, "synthesis_energy"), Math.toIntExact(positiveLong(json, "ticks"))));
        });

        readDirectory(manager, "rematter/process", (id, json) -> {
            SubstanceId key = SubstanceId.parse(id.toString());
            if (disabled(json)) {
                processes.remove(key);
                return;
            }
            processes.put(key, new ProcessRecipe(key, enumValue(ProcessType.class, string(json, "type")),
                    amounts(json, "inputs"), amounts(json, "outputs"), positiveLong(json, "energy"),
                    Math.toIntExact(positiveLong(json, "ticks"))));
        });

        readDirectory(manager, "rematter/synthesis", (id, json) -> {
            String item = string(json, "item");
            requireUniqueTarget(synthesisSources, item, id);
            if (disabled(json)) {
                synthesis.remove(item);
                return;
            }
            synthesis.put(item, enumValue(SynthesisCategory.class, string(json, "category")));
        });

        ChemistryRegistry.Builder builder = ChemistryRegistry.builder();
        substances.values().forEach(builder::addSubstance);
        profiles.values().forEach(builder::addProfile);
        fluids.values().forEach(builder::addFluidProfile);
        processes.values().forEach(builder::addProcess);
        synthesis.forEach(builder::allowSynthesis);
        ChemistryRegistry result = builder.build();
        SynthesisOutputs.validate(result);
        CatalogWireCodec.encode(result);
        return result;
    }

    @Override
    protected void apply(ChemistryRegistry validated, ResourceManager manager, ProfilerFiller profiler) {
        current = validated;
        ReMatter.LOGGER.info("Loaded {} substances, {} material profiles, and {} chemical processes",
                validated.substances().size(), validated.profiles().size(), validated.processes().size());
    }

    private static void readDirectory(ResourceManager manager, String directory, BiConsumer<Identifier, JsonObject> consume) {
        Map<Identifier, Resource> resources = new TreeMap<>(manager.listResources(directory, id -> id.getPath().endsWith(".json")));
        if (resources.size() > ChemistryRegistry.MAX_SYNTHESIS_ENTRIES) {
            throw new IllegalArgumentException("Too many chemistry documents in " + directory);
        }
        for (var entry : resources.entrySet()) {
            Identifier source = entry.getKey();
            String path = source.getPath().substring(directory.length() + 1, source.getPath().length() - 5);
            Identifier id = Identifier.fromNamespaceAndPath(source.getNamespace(), path);
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseString(readBounded(reader)).getAsJsonObject();
                if (json.has("required_mod") && !ModList.get().isLoaded(string(json, "required_mod"))) {
                    continue;
                }
                if (json.has("required_item") && !BuiltInRegistries.ITEM.containsKey(Identifier.parse(string(json, "required_item")))) {
                    continue;
                }
                if (positiveLong(json, "schema_version") != 1) {
                    throw new IllegalArgumentException("Unsupported chemistry schema version");
                }
                consume.accept(id, json);
            } catch (RuntimeException | IOException error) {
                throw new IllegalArgumentException("Invalid ReMatter data in " + source + ": " + error.getMessage(), error);
            }
        }
    }

    private static String readBounded(Reader reader) throws IOException {
        StringBuilder text = new StringBuilder();
        char[] buffer = new char[4096];
        int length;
        while ((length = reader.read(buffer)) != -1) {
            if (text.length() + length > MAX_DOCUMENT_CHARS) {
                throw new IOException("Chemistry document exceeds " + MAX_DOCUMENT_CHARS + " characters");
            }
            text.append(buffer, 0, length);
        }
        return text.toString();
    }

    private static Map<SubstanceKey, Long> amounts(JsonObject json, String field) {
        TreeMap<SubstanceKey, Long> result = new TreeMap<>();
        for (JsonElement element : array(json, field)) {
            JsonObject entry = element.getAsJsonObject();
            SubstanceKey key = SubstanceKey.of(string(entry, "substance"), enumValue(Phase.class, string(entry, "phase")));
            long amount = positiveLong(entry, "amount");
            if (result.putIfAbsent(key, amount) != null) {
                throw new IllegalArgumentException("Duplicate substance/phase in " + field + ": " + key);
            }
        }
        return result;
    }

    private static boolean disabled(JsonObject json) {
        if (!json.has("disabled")) {
            return false;
        }
        JsonElement value = json.get("disabled");
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Expected boolean: disabled");
        }
        return value.getAsBoolean();
    }

    private static void requireUniqueTarget(Map<String, Identifier> sources, String target, Identifier id) {
        Identifier previous = sources.putIfAbsent(target, id);
        if (previous != null) {
            throw new IllegalArgumentException("Conflicting definitions for " + target + " in " + previous + " and " + id);
        }
    }

    private static String string(JsonObject json, String field) {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Expected string: " + field);
        }
        return value.getAsString();
    }

    private static JsonObject object(JsonObject json, String field) {
        if (!json.has(field) || !json.get(field).isJsonObject()) {
            throw new IllegalArgumentException("Expected object: " + field);
        }
        return json.getAsJsonObject(field);
    }

    private static JsonArray array(JsonObject json, String field) {
        if (!json.has(field) || !json.get(field).isJsonArray()) {
            throw new IllegalArgumentException("Expected array: " + field);
        }
        return json.getAsJsonArray(field);
    }

    private static long positiveLong(JsonObject json, String field) {
        return positiveLong(json.get(field), field);
    }

    private static long positiveLong(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Expected integer: " + field);
        }
        long result = value.getAsBigDecimal().longValueExact();
        if (result <= 0) {
            throw new IllegalArgumentException("Expected positive integer: " + field);
        }
        return result;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    }
}
