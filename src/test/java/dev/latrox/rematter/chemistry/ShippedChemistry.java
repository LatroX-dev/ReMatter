package dev.latrox.rematter.chemistry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.latrox.rematter.data.ChemistryDocuments;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds the registry a server would load from the built-in core plus shipped chemistry data, with every optional
 * mod treated as present. Each document is read by the same rules as the reload listener. Later roots replace earlier documents at the same resource path, like data pack priority.
 */
public final class ShippedChemistry {
    public static final Path MAIN_RESOURCES = Path.of("src/main/resources");

    private ShippedChemistry() {}

    /** The immutable registry of the standalone mod, loaded once per test run. */
    public static ChemistryRegistry standalone() {
        return Standalone.REGISTRY;
    }

    public static ChemistryRegistry load(Path... roots) {
        return load(mod -> true, roots);
    }

    public static ChemistryRegistry load(java.util.function.Predicate<String> mods, Path... roots) {
        ChemistryRegistry core = BuiltinChemistry.create();
        Map<SubstanceId, SubstanceDefinition> substances = new TreeMap<>();
        Map<String, MaterialProfile> profiles = new TreeMap<>();
        Map<String, FluidProfile> fluids = new TreeMap<>();
        Map<SubstanceId, ProcessRecipe> processes = new TreeMap<>();
        Map<String, SynthesisCategory> permissions = new TreeMap<>();
        Map<String, SynthesisRequirement> requirements = new TreeMap<>();
        Map<SubstanceId, EnvironmentSource> sources = new TreeMap<>();
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
            if (!ChemistryDocuments.modsMet(json, mods)) continue;
            ChemistryDocuments.requireSchema(json);
            boolean disabled = ChemistryDocuments.disabled(json);
            switch (directory) {
                case "substance" -> {
                    if (disabled) substances.remove(id);
                    else substances.put(id, ChemistryDocuments.substance(id, json));
                }
                case "material_profile" -> {
                    String item = unique(targets, directory, ChemistryDocuments.string(json, "item"), document.getKey());
                    if (disabled) profiles.remove(item);
                    else profiles.put(item, ChemistryDocuments.materialProfile(json));
                }
                case "fluid_profile" -> {
                    String fluid = unique(targets, directory, ChemistryDocuments.string(json, "fluid"), document.getKey());
                    if (disabled) fluids.remove(fluid);
                    else fluids.put(fluid, ChemistryDocuments.fluidProfile(json));
                }
                case "process" -> {
                    if (disabled) processes.remove(id);
                    else processes.put(id, ChemistryDocuments.process(id, json));
                }
                case "synthesis" -> {
                    String target = unique(targets, directory, ChemistryDocuments.string(json, "item"), document.getKey());
                    if (disabled) permissions.remove(target);
                    else {
                        permissions.put(target, ChemistryDocuments.synthesisCategory(json));
                        requirements.put(target, ChemistryDocuments.synthesisRequirement(json));
                    }
                }
                case "environment_source" -> {
                    if (disabled) sources.remove(id);
                    else sources.put(id, ChemistryDocuments.environmentSource(id, json));
                }
                default -> throw new IllegalStateException("Unexpected chemistry directory: " + document.getKey());
            }
        }
        var builder = ChemistryRegistry.builder();
        substances.values().forEach(builder::addSubstance);
        profiles.values().forEach(builder::addProfile);
        fluids.values().forEach(builder::addFluidProfile);
        processes.values().forEach(builder::addProcess);
        permissions.forEach((id, category) -> builder.allowSynthesis(id, category, requirements.get(id)));
        sources.values().forEach(builder::addEnvironmentSource);
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
}
