package dev.latrox.rematter.research;

import com.google.gson.JsonObject;
import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Immutable research reload with explicit exact item IDs, independent from chemical element equivalence. */
public final class ResearchCatalogue {
    public static final ResearchCatalogue EMPTY = new ResearchCatalogue(java.util.List.of());
    private final Map<String, MaterialResearch> families;
    private final Map<String, String> itemFamilies;
    private final String fingerprint;
    public ResearchCatalogue(Collection<MaterialResearch> values) {
        var map = new TreeMap<String, MaterialResearch>(); var items = new TreeMap<String, String>();
        for (var value : values) {
            if (map.putIfAbsent(value.id(), value) != null) throw new IllegalArgumentException("Duplicate research family " + value.id());
            for (var item : value.items()) if (items.putIfAbsent(item, value.id()) != null)
                throw new IllegalArgumentException("Item belongs to multiple research families: " + item);
        }
        if (map.size() > 8_192 || items.size() > ChemistryRegistry.MAX_MATERIAL_PROFILES) throw new IllegalArgumentException("Research catalogue too large");
        families = java.util.Collections.unmodifiableMap(map); itemFamilies = Map.copyOf(items);
        StringBuilder signature = new StringBuilder("research-v1;");
        map.values().forEach(value -> signature.append(value.id()).append('|').append(new TreeSet<>(value.items()))
                .append('|').append(new TreeSet<>(value.substances())).append('|').append(value.derive())
                .append('|').append(value.startingKnowledge()).append('|').append(value.discoveryTier())
                .append('|').append(value.requiredDimension()).append('|').append(value.derivePrimal()).append(';'));
        fingerprint = hash(signature.toString());
    }
    public Collection<MaterialResearch> families() { return families.values(); }
    public Optional<MaterialResearch> family(String id) { return Optional.ofNullable(families.get(id)); }
    public Optional<MaterialResearch> forItem(String itemId) { return family(itemFamilies.getOrDefault(itemId, "")); }
    public String fingerprint() { return fingerprint; }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    /** Optional profiles are filtered by the actual validated server chemistry, not a guessed mod list. */
    public static Optional<MaterialResearch> parse(String id, JsonObject json, ChemistryRegistry chemistry) {
        if (json.get("schema").getAsInt() != 1) throw new IllegalArgumentException("Unknown material research schema " + id);
        if (json.has("disabled") && json.get("disabled").getAsBoolean()) return Optional.empty();
        var items = new TreeSet<String>();
        for (var item : json.getAsJsonArray("items")) if (chemistry.profile(item.getAsString()).isPresent()) items.add(item.getAsString());
        if (items.isEmpty()) return Optional.empty();
        var substances = new TreeSet<SubstanceKey>();
        for (var entry : json.getAsJsonArray("substances")) {
            var object = entry.getAsJsonObject(); var key = SubstanceKey.of(object.get("substance").getAsString(), Phase.valueOf(object.get("phase").getAsString().toUpperCase(java.util.Locale.ROOT)));
            if (!chemistry.isKnown(key)) throw new IllegalArgumentException("Unknown research substance " + key + " in " + id);
            substances.add(key);
        }
        for (var item : items) if (!substances.equals(chemistry.profile(item).orElseThrow().matter().keySet()))
            throw new IllegalArgumentException("Family constituents differ from exact profile " + item);
        long smallest = Long.MAX_VALUE;
        for (var item : items) if (chemistry.synthesisPolicy().allowsItem(item)) {
            long sum = 0; for (long amount : chemistry.profile(item).orElseThrow().matter().values()) sum = Math.addExact(sum, amount);
            smallest = Math.min(smallest, sum);
        }
        boolean derive = json.get("derive").getAsBoolean() && smallest != Long.MAX_VALUE;
        int tier = json.get("discovery_tier").getAsInt();
        return Optional.of(new MaterialResearch(id, json.get("name").getAsString(), items, substances, derive,
                json.get("starting_knowledge").getAsBoolean(), tier == 0 ? null : DiscoveryTier.of(tier),
                json.get("requires_dimension").getAsString(), derive ? MaterialResearch.derivePrice(smallest) : 4_000));
    }
}
