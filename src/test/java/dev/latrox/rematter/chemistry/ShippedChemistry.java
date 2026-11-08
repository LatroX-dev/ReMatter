package dev.latrox.rematter.chemistry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds the registry a server would load from the built-in core plus shipped chemistry data, with every optional
 * mod treated as present. Later roots replace earlier documents at the same resource path, like data pack priority.
 */
public final class ShippedChemistry {
    public static final Path MAIN_RESOURCES = Path.of("src/main/resources");

    private ShippedChemistry() {}

    /** The immutable registry of the standalone mod, loaded once per test run. */
    public static ChemistryRegistry standalone() {
        return Standalone.REGISTRY;
    }

    public static ChemistryRegistry load(Path... roots) {
        ChemistryRegistry core = BuiltinChemistry.create();
        Map<SubstanceId, SubstanceDefinition> substances = new TreeMap<>();
        Map<String, MaterialProfile> profiles = new TreeMap<>();
        Map<String, FluidProfile> fluids = new TreeMap<>();
        Map<SubstanceId, ProcessRecipe> processes = new TreeMap<>();
        Map<String, SynthesisCategory> permissions = new TreeMap<>();
        core.substances().forEach(value -> substances.put(value.id(), value));
        core.processes().forEach(value -> processes.put(value.id(), value));
        Map<String, Path> documents = new TreeMap<>();
        for (Path root : roots) overlay(documents, root);
        Map<String, String> targets = new HashMap<>();
        for (var document : documents.entrySet()) {
            String[] parts = document.getKey().split("/", 5);
            String directory = parts[3];
            String relative = parts[4];
            SubstanceId id = SubstanceId.parse(parts[1] + ":" + relative.substring(0, relative.length() - 5));
            JsonObject json = read(document.getValue());
            if (integer(json, "schema_version") != 1) throw new IllegalStateException("Unsupported schema: " + document.getKey());
            boolean disabled = json.has("disabled") && json.get("disabled").getAsBoolean();
            switch (directory) {
                case "substance" -> {
                    if (disabled) {
                        substances.remove(id);
                        continue;
                    }
                    Map<SubstanceId, Long> composition = new TreeMap<>();
                    json.getAsJsonObject("composition").entrySet()
                            .forEach(entry -> composition.put(SubstanceId.parse(entry.getKey()), exact(entry.getValue())));
                    EnumSet<Phase> phases = EnumSet.noneOf(Phase.class);
                    json.getAsJsonArray("phases").forEach(value -> phases.add(enumValue(Phase.class, value.getAsString())));
                    substances.put(id, new SubstanceDefinition(id, json.get("translation_key").getAsString(),
                            json.has("symbol") ? json.get("symbol").getAsString() : "",
                            enumValue(SubstanceKind.class, json.get("kind").getAsString()), new Composition(composition), phases));
                }
                case "material_profile" -> {
                    String item = unique(targets, directory, json.get("item").getAsString(), document.getKey());
                    if (disabled) {
                        profiles.remove(item);
                        permissions.remove(item);
                        continue;
                    }
                    profiles.put(item, new MaterialProfile(item, json.has("item_count") ? integer(json, "item_count") : 1,
                            amounts(json, "matter"), number(json, "decomposition_energy"),
                            number(json, "synthesis_energy"), integer(json, "ticks")));
                }
                case "fluid_profile" -> {
                    String fluid = unique(targets, directory, json.get("fluid").getAsString(), document.getKey());
                    if (disabled) {
                        fluids.remove(fluid);
                        permissions.remove(fluid);
                        continue;
                    }
                    fluids.put(fluid, new FluidProfile(fluid, integer(json, "amount"), amounts(json, "matter"),
                            number(json, "decomposition_energy"), number(json, "synthesis_energy"), integer(json, "ticks")));
                }
                case "process" -> {
                    if (disabled) {
                        processes.remove(id);
                        continue;
                    }
                    processes.put(id, new ProcessRecipe(id, enumValue(ProcessType.class, json.get("type").getAsString()),
                            amounts(json, "inputs"), amounts(json, "outputs"), number(json, "energy"), integer(json, "ticks")));
                }
                case "synthesis" -> {
                    String target = unique(targets, directory, json.get("item").getAsString(), document.getKey());
                    if (disabled) permissions.remove(target);
                    else permissions.put(target, enumValue(SynthesisCategory.class, json.get("category").getAsString()));
                }
                default -> throw new IllegalStateException("Unexpected chemistry directory: " + document.getKey());
            }
        }
        var builder = ChemistryRegistry.builder();
        substances.values().forEach(builder::addSubstance);
        profiles.values().forEach(builder::addProfile);
        fluids.values().forEach(builder::addFluidProfile);
        processes.values().forEach(builder::addProcess);
        permissions.forEach(builder::allowSynthesis);
        return builder.build();
    }

    public static JsonObject read(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static final class Standalone {
        private static final ChemistryRegistry REGISTRY = load(MAIN_RESOURCES);
    }

    private static void overlay(Map<String, Path> documents, Path root) {
        try (var files = Files.walk(root.resolve("data"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                String relative = root.relativize(file).toString().replace(java.io.File.separatorChar, '/');
                String[] parts = relative.split("/", 5);
                if (parts.length == 5 && parts[2].equals("rematter")) documents.put(relative, file);
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static String unique(Map<String, String> targets, String directory, String target, String document) {
        String previous = targets.put(directory + ":" + target, document);
        if (previous != null) throw new IllegalStateException("Conflicting definitions for " + target + " in " + previous + " and " + document);
        return target;
    }

    private static Map<SubstanceKey, Long> amounts(JsonObject json, String field) {
        Map<SubstanceKey, Long> result = new TreeMap<>();
        for (JsonElement value : json.getAsJsonArray(field)) {
            JsonObject entry = value.getAsJsonObject();
            SubstanceKey key = SubstanceKey.of(entry.get("substance").getAsString(), enumValue(Phase.class, entry.get("phase").getAsString()));
            if (result.put(key, number(entry, "amount")) != null) throw new IllegalStateException("Duplicate " + key + " in " + field);
        }
        return result;
    }

    private static long exact(JsonElement value) {
        return value.getAsBigDecimal().longValueExact();
    }

    private static long number(JsonObject json, String key) {
        return exact(json.get(key));
    }

    private static int integer(JsonObject json, String key) {
        return Math.toIntExact(number(json, key));
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    }
}
