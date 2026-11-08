package dev.latrox.rematter.chemistry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Follows every route ReMatter itself offers from a set of starting items and fluids: decomposition, chemical
 * processes, synthesis permissions and preparation recipes. Native crafting of other mods is not modelled.
 */
public final class MatterReachability {
    private static final Path CATALOGUE = Path.of("tools/data/material_catalogue.json");
    private static final List<String> SLOTS = List.of("primary", "secondary", "tertiary");

    private MatterReachability() {}

    public record Reachable(Set<String> items, Set<String> fluids, Set<SubstanceKey> matter) {}

    public static Reachable from(ChemistryRegistry registry, Set<String> startItems, Set<String> startFluids) {
        Map<String, Set<String>> tags = tags();
        List<JsonObject> preparations = preparations();
        Set<String> items = new HashSet<>(startItems);
        Set<String> fluids = new HashSet<>(startFluids);
        Set<SubstanceKey> matter = new HashSet<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (MaterialProfile profile : registry.profiles()) {
                if (items.contains(profile.itemId())) changed |= matter.addAll(profile.matter().keySet());
                else if (registry.synthesisPolicy().allowsItem(profile.itemId()) && matter.containsAll(profile.matter().keySet())) {
                    changed |= items.add(profile.itemId());
                }
            }
            for (FluidProfile profile : registry.fluidProfiles()) {
                if (fluids.contains(profile.fluidId())) changed |= matter.addAll(profile.matter().keySet());
                else if (registry.synthesisPolicy().allowsFluid(profile.fluidId()) && matter.containsAll(profile.matter().keySet())) {
                    changed |= fluids.add(profile.fluidId());
                }
            }
            for (ProcessRecipe process : registry.processes()) {
                if (matter.containsAll(process.inputs().keySet())) changed |= matter.addAll(process.outputs().keySet());
            }
            for (JsonObject recipe : preparations) {
                boolean available = SLOTS.stream().filter(recipe::has).allMatch(slot -> {
                    String ingredient = recipe.get(slot).getAsString();
                    return ingredient.startsWith("#")
                            ? tags.getOrDefault(ingredient.substring(1), Set.of()).stream().anyMatch(items::contains)
                            : items.contains(ingredient);
                });
                if (available) changed |= items.add(recipe.getAsJsonObject("result").get("id").getAsString());
            }
        }
        return new Reachable(items, fluids, matter);
    }

    private static List<JsonObject> preparations() {
        List<JsonObject> result = new ArrayList<>();
        for (Path file : json(ShippedChemistry.MAIN_RESOURCES.resolve("data"))) {
            if (!file.toString().replace('\\', '/').contains("/recipe/")) continue;
            JsonObject recipe = ShippedChemistry.read(file);
            if (recipe.has("type") && recipe.get("type").getAsString().equals("rematter:preparation")) result.add(recipe);
        }
        return result;
    }

    /** Tag members from the reviewed catalogue and from the item tags shipped with ReMatter. */
    public static Map<String, Set<String>> tags() {
        Map<String, Set<String>> direct = new HashMap<>();
        for (JsonElement row : ShippedChemistry.read(CATALOGUE).getAsJsonArray("items")) {
            JsonObject item = row.getAsJsonObject();
            JsonObject evidence = item.getAsJsonObject("evidence");
            if (!evidence.has("tags")) continue;
            for (JsonElement tag : evidence.getAsJsonArray("tags")) {
                direct.computeIfAbsent(tag.getAsString(), key -> new HashSet<>()).add(item.get("item").getAsString());
            }
        }
        Path data = ShippedChemistry.MAIN_RESOURCES.resolve("data");
        for (Path file : json(data)) {
            Path relative = data.relativize(file);
            if (relative.getNameCount() < 4 || !relative.getName(1).toString().equals("tags") || !relative.getName(2).toString().equals("item")) continue;
            String path = relative.subpath(3, relative.getNameCount()).toString().replace('\\', '/');
            String tag = relative.getName(0) + ":" + path.substring(0, path.length() - 5);
            for (JsonElement value : ShippedChemistry.read(file).getAsJsonArray("values")) {
                String member = value.isJsonObject() ? value.getAsJsonObject().get("id").getAsString() : value.getAsString();
                direct.computeIfAbsent(tag, key -> new HashSet<>()).add(member);
            }
        }
        Map<String, Set<String>> resolved = new HashMap<>();
        direct.keySet().forEach(tag -> resolved.put(tag, resolve(tag, direct, new HashSet<>())));
        return resolved;
    }

    private static Set<String> resolve(String tag, Map<String, Set<String>> direct, Set<String> visiting) {
        Set<String> members = new HashSet<>();
        if (!visiting.add(tag)) return members;
        for (String member : direct.getOrDefault(tag, Set.of())) {
            if (member.startsWith("#")) members.addAll(resolve(member.substring(1), direct, visiting));
            else members.add(member);
        }
        return members;
    }

    private static List<Path> json(Path root) {
        try (var files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".json")).sorted().toList();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
