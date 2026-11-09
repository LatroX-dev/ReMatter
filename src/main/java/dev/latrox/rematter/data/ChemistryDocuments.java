package dev.latrox.rematter.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.latrox.rematter.chemistry.*;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Reads single chemistry documents into definitions. The reload listener adds the resource stack, dependency guards
 * and registry checks; every reader of shipped or pack data uses these same rules.
 */
public final class ChemistryDocuments {
    public static final int SCHEMA_VERSION = 1;

    private ChemistryDocuments() {}

    public static void requireSchema(JsonObject json) {
        if (positiveLong(json, "schema_version") != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported chemistry schema version");
        }
    }

    /** An override that removes the definition at its path; the field must be a JSON boolean. */
    public static boolean disabled(JsonObject json) {
        if (!json.has("disabled")) {
            return false;
        }
        JsonElement value = json.get("disabled");
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Expected boolean: disabled");
        }
        return value.getAsBoolean();
    }

    public static SubstanceDefinition substance(SubstanceId id, JsonObject json) {
        Map<SubstanceId, Long> composition = new TreeMap<>();
        object(json, "composition").entrySet().forEach(e ->
                composition.put(SubstanceId.parse(e.getKey()), positiveLong(e.getValue(), "composition")));
        EnumSet<Phase> phases = EnumSet.noneOf(Phase.class);
        for (JsonElement phase : array(json, "phases")) {
            phases.add(enumValue(Phase.class, phase.getAsString()));
        }
        return new SubstanceDefinition(id, string(json, "translation_key"),
                json.has("symbol") ? string(json, "symbol") : "", enumValue(SubstanceKind.class, string(json, "kind")),
                new Composition(composition), phases);
    }

    public static MaterialProfile materialProfile(JsonObject json) {
        return new MaterialProfile(string(json, "item"),
                json.has("item_count") ? Math.toIntExact(positiveLong(json, "item_count")) : 1, amounts(json, "matter"),
                positiveLong(json, "decomposition_energy"), positiveLong(json, "synthesis_energy"),
                Math.toIntExact(positiveLong(json, "ticks")));
    }

    public static FluidProfile fluidProfile(JsonObject json) {
        return new FluidProfile(string(json, "fluid"), Math.toIntExact(positiveLong(json, "amount")),
                amounts(json, "matter"), positiveLong(json, "decomposition_energy"),
                positiveLong(json, "synthesis_energy"), Math.toIntExact(positiveLong(json, "ticks")));
    }

    public static ProcessRecipe process(SubstanceId id, JsonObject json) {
        return new ProcessRecipe(id, enumValue(ProcessType.class, string(json, "type")),
                amounts(json, "inputs"), amounts(json, "outputs"), positiveLong(json, "energy"),
                Math.toIntExact(positiveLong(json, "ticks")), optionalString(json, "requires_advancement", ""));
    }

    public static SynthesisRequirement synthesisRequirement(JsonObject json) {
        return new SynthesisRequirement(optionalString(json, "synthesis_machine", "material_synthesizer"),
                json.has("crystallization_tier") ? Math.toIntExact(positiveLong(json, "crystallization_tier")) : 1,
                optionalString(json, "requires_advancement", ""));
    }

    public static EnvironmentSource environmentSource(SubstanceId id, JsonObject json) {
        if (!json.has("renewable") || !json.get("renewable").isJsonPrimitive()
                || !json.get("renewable").getAsJsonPrimitive().isBoolean() || !json.get("renewable").getAsBoolean())
            throw new IllegalArgumentException("Environment sources must explicitly be renewable");
        Set<String> dimensions = new HashSet<>();
        for (JsonElement value : array(json, "dimensions")) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || !dimensions.add(value.getAsString()))
                throw new IllegalArgumentException("Invalid source dimensions");
        }
        return new EnvironmentSource(id, amounts(json, "outputs"), positiveLong(json, "energy"),
                Math.toIntExact(positiveLong(json, "ticks")), dimensions, true);
    }

    public static boolean modsMet(JsonObject json, Predicate<String> loaded) {
        if (json.has("required_mod") && !loaded.test(string(json, "required_mod"))) return false;
        if (!json.has("unavailable_with_mods")) return true;
        var entries = array(json, "unavailable_with_mods");
        if (entries.size() > 32) throw new IllegalArgumentException("Too many excluded mods");
        boolean present = false;
        for (var value : entries) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                    || !value.getAsString().matches("[a-z][a-z0-9_]{1,63}"))
                throw new IllegalArgumentException("Invalid excluded mod ID");
            present |= loaded.test(value.getAsString());
        }
        return !present;
    }

    public static String optionalString(JsonObject json, String field, String fallback) {
        return json.has(field) ? string(json, field) : fallback;
    }

    public static SynthesisCategory synthesisCategory(JsonObject json) {
        return enumValue(SynthesisCategory.class, string(json, "category"));
    }

    public static String string(JsonObject json, String field) {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Expected string: " + field);
        }
        return value.getAsString();
    }

    static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
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
}
