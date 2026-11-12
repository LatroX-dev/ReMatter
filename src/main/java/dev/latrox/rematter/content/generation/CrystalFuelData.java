package dev.latrox.rematter.content.generation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

/** Closed fuel catalogue. Data may price or disable the nine matrix crystals, never expand the fuel category. */
public final class CrystalFuelData extends SimplePreparableReloadListener<Map<String, CrystalFuelData.Fuel>> {
    public static final Set<String> ALLOWED = Set.of("rematter:matrix_crystal", "rematter:ice_matrix_crystal", "rematter:copper_matrix_crystal",
            "rematter:leaf_matrix_crystal", "rematter:blue_matrix_crystal", "rematter:violet_matrix_crystal", "rematter:amber_matrix_crystal",
            "rematter:ember_matrix_crystal", "rematter:mint_matrix_crystal");
    private static volatile Map<String, Fuel> current = Map.of();
    public record Fuel(String itemId, int count, long energy) {
        public Fuel {
            if (!ALLOWED.contains(itemId) || count < 1 || count > 64 || energy < 1 || energy > 1_000_000_000L)
                throw new IllegalArgumentException("Invalid or unapproved crystal fuel");
        }
    }
    public static Optional<Fuel> fuel(ItemStack stack) {
        if (stack.isEmpty() || !stack.getComponentsPatch().isEmpty()) return Optional.empty();
        var fuel = current.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        return fuel != null && stack.getCount() >= fuel.count() ? Optional.of(fuel) : Optional.empty();
    }
    public static Map<String, Fuel> fuels() { return current; }
    public static void register(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath("rematter", "crystal_fuels"), new CrystalFuelData());
    }
    public static Optional<Fuel> parse(JsonObject json) {
        if (json.get("schema_version") == null || json.get("schema_version").getAsBigDecimal().intValueExact() != 1)
            throw new IllegalArgumentException("Unsupported crystal fuel schema");
        String item = json.get("item").getAsString();
        if (!ALLOWED.contains(item)) throw new IllegalArgumentException("Unapproved crystal fuel: " + item);
        if (json.has("disabled") && json.get("disabled").getAsBoolean() || json.has("enabled") && !json.get("enabled").getAsBoolean()) return Optional.empty();
        var count = json.has("count") ? json.get("count").getAsBigDecimal().intValueExact() : 1;
        long energy = json.get("energy").getAsBigDecimal().longValueExact();
        return Optional.of(new Fuel(item, count, energy));
    }
    @Override protected Map<String, Fuel> prepare(ResourceManager manager, ProfilerFiller profiler) {
        var files = new TreeMap<>(manager.listResources("rematter/crystal_fuel", id -> id.getPath().endsWith(".json")));
        if (files.size() > 64) throw new IllegalArgumentException("Too many crystal fuel documents");
        var fuels = new TreeMap<String, Fuel>(); var targets = new java.util.HashSet<String>();
        for (var entry : files.entrySet()) {
            try (var reader = entry.getValue().openAsReader()) {
                var json = JsonParser.parseString(readBounded(reader)).getAsJsonObject();
                String item = json.get("item").getAsString();
                if (!targets.add(item)) throw new IllegalArgumentException("Duplicate crystal fuel target: " + item);
                var fuel = parse(json);
                if (fuel.isPresent() && !BuiltInRegistries.ITEM.containsKey(Identifier.parse(item))) throw new IllegalArgumentException("Unknown crystal fuel item: " + item);
                fuel.ifPresent(value -> fuels.put(item, value));
            } catch (RuntimeException | IOException invalid) {
                throw new IllegalArgumentException("Invalid crystal fuel " + entry.getKey(), invalid);
            }
        }
        return Collections.unmodifiableMap(fuels);
    }
    private static String readBounded(Reader reader) throws IOException {
        var text = new StringBuilder(); var chunk = new char[1024]; int count;
        while ((count = reader.read(chunk)) != -1) { if (text.length() + count > 4096) throw new IOException("Crystal fuel document too large"); text.append(chunk, 0, count); }
        return text.toString();
    }
    @Override protected void apply(Map<String, Fuel> fuels, ResourceManager manager, ProfilerFiller profiler) { current = Map.copyOf(fuels); }
}
