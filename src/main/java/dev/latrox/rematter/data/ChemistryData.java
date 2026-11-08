package dev.latrox.rematter.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.content.preparation.PreparationRecipe;
import dev.latrox.rematter.mixin.ReloadableServerResourcesAccessor;
import dev.latrox.rematter.network.CatalogWireCodec;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.resource.VanillaServerListeners;

import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/** Builds and validates a complete immutable catalogue before publishing a reload. */
public final class ChemistryData extends SimplePreparableReloadListener<ChemistryRegistry> {
    private static final int MAX_DOCUMENT_CHARS = 262_144;
    private static final Identifier RELOAD_ID = Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "chemistry");
    private static volatile ChemistryRegistry current = BuiltinChemistry.create();
    private final Map<Item, DataComponentMap> itemDefaults;
    private final RecipeManager recipes;

    private ChemistryData(Map<Item, DataComponentMap> itemDefaults, RecipeManager recipes) {
        this.itemDefaults = Map.copyOf(itemDefaults);
        this.recipes = recipes;
    }

    public static ChemistryRegistry current() {
        return current;
    }

    public static void register(AddServerReloadListenersEvent event) {
        var resources = (ReloadableServerResourcesAccessor) event.getServerResources();
        var itemDefaults = new HashMap<Item, DataComponentMap>();
        for (var pending : resources.rematter$getPendingComponents()) {
            if (pending.key().equals(Registries.ITEM)) {
                pending.forEach((holder, components) -> itemDefaults.put((Item) holder.value(), components));
                break;
            }
        }
        event.addListener(RELOAD_ID, new ChemistryData(itemDefaults, event.getServerResources().getRecipeManager()));
        event.addDependency(VanillaServerListeners.RECIPES, RELOAD_ID);
    }

    @Override
    protected ChemistryRegistry prepare(ResourceManager manager, ProfilerFiller profiler) {
        ChemistryRegistry core = BuiltinChemistry.create();
        Map<SubstanceId, SubstanceDefinition> substances = new TreeMap<>();
        Map<String, MaterialProfile> profiles = new TreeMap<>();
        Map<String, FluidProfile> fluids = new TreeMap<>();
        Map<SubstanceId, ProcessRecipe> processes = new TreeMap<>();
        Map<String, SynthesisCategory> synthesis = new TreeMap<>();
        Map<String, Identifier> profileSources = new HashMap<>();
        Map<String, Identifier> fluidSources = new HashMap<>();
        Map<String, Identifier> synthesisSources = new HashMap<>();
        core.substances().forEach(s -> substances.put(s.id(), s));
        core.processes().forEach(p -> processes.put(p.id(), p));

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
        SynthesisOutputs.validate(result, this::defaultsFor);
        CatalogWireCodec.encode(result);
        return result;
    }

    @Override
    protected void apply(ChemistryRegistry validated, ResourceManager manager, ProfilerFiller profiler) {
        for (var holder : recipes.getRecipes()) {
            if (holder.value() instanceof PreparationRecipe preparation) {
                try {
                    preparation.validateOutput(defaultsFor(preparation.outputItem()));
                } catch (IllegalArgumentException error) {
                    throw new IllegalArgumentException("Invalid preparation result in " + holder.id().identifier(), error);
                }
            }
        }
        current = validated;
        ReMatter.LOGGER.info("Loaded {} substances, {} material profiles, and {} chemical processes",
                validated.substances().size(), validated.profiles().size(), validated.processes().size());
    }

    private DataComponentMap defaultsFor(Item item) {
        return Objects.requireNonNull(itemDefaults.get(item), () -> "Missing reload item components for " + item);
    }

    private static void readDirectory(ResourceManager manager, String directory, BiConsumer<Identifier, JsonObject> consume) {
        Map<Identifier, List<Resource>> resources = new TreeMap<>(manager.listResourceStacks(directory, id -> id.getPath().endsWith(".json")));
        if (resources.size() > ChemistryRegistry.MAX_SYNTHESIS_ENTRIES) {
            throw new IllegalArgumentException("Too many chemistry documents in " + directory);
        }
        for (var entry : resources.entrySet()) {
            Identifier source = entry.getKey();
            String path = source.getPath().substring(directory.length() + 1, source.getPath().length() - 5);
            Identifier id = Identifier.fromNamespaceAndPath(source.getNamespace(), path);
            // A conditional pack override falls through to the next applicable definition.
            for (Resource resource : entry.getValue().reversed()) {
                try (Reader reader = resource.openAsReader()) {
                    JsonObject json = JsonParser.parseString(readBounded(reader)).getAsJsonObject();
                    if (!prerequisitesMet(directory, json, ModList.get()::isLoaded,
                            item -> BuiltInRegistries.ITEM.containsKey(Identifier.parse(item)),
                            fluid -> BuiltInRegistries.FLUID.containsKey(Identifier.parse(fluid)))) continue;
                    if (positiveLong(json, "schema_version") != 1) {
                        throw new IllegalArgumentException("Unsupported chemistry schema version");
                    }
                    consume.accept(id, json);
                    break;
                } catch (RuntimeException | IOException error) {
                    throw new IllegalArgumentException("Invalid ReMatter data in " + source + ": " + error.getMessage(), error);
                }
            }
        }
    }

    static boolean prerequisitesMet(String directory, JsonObject json, Predicate<String> modLoaded,
                                    Predicate<String> itemRegistered, Predicate<String> fluidRegistered) {
        String requiredMod = json.has("required_mod") ? string(json, "required_mod") : null;
        if (requiredMod != null && !modLoaded.test(requiredMod)) return false;
        if (json.has("required_item") && !itemRegistered.test(string(json, "required_item"))) return false;
        if (json.has("required_fluid") && !fluidRegistered.test(string(json, "required_fluid"))) return false;
        if (requiredMod == null || requiredMod.equals(ReMatter.MOD_ID) || requiredMod.equals("minecraft") || disabled(json)) return true;

        return switch (directory) {
            case "rematter/material_profile" -> targetAvailable(requiredMod, string(json, "item"), itemRegistered);
            case "rematter/fluid_profile" -> targetAvailable(requiredMod, string(json, "fluid"), fluidRegistered);
            case "rematter/synthesis" -> switch (enumValue(SynthesisCategory.class, string(json, "category"))) {
                case RAW_MATERIAL, NATURAL_MATERIAL -> targetAvailable(requiredMod, string(json, "item"), itemRegistered);
                case FLUID -> targetAvailable(requiredMod, string(json, "item"), fluidRegistered);
                default -> true;
            };
            default -> true;
        };
    }

    private static boolean targetAvailable(String requiredMod, String target, Predicate<String> registered) {
        return !Identifier.parse(target).getNamespace().equals(requiredMod) || registered.test(target);
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
