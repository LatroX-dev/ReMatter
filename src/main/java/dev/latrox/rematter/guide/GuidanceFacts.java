package dev.latrox.rematter.guide;

import com.google.gson.JsonObject;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Small durable facts let a real owner's offline machine success become a breadcrumb on the next login. */
public final class GuidanceFacts {
    private static final int MAX_OWNERS = 65536;
    private final Map<UUID, Set<GuidanceEvent>> earned = new HashMap<>();
    private JsonObject opaque;
    public boolean readable() { return opaque == null; }
    public Set<GuidanceEvent> earned(UUID owner) { return Set.copyOf(earned.getOrDefault(owner, Set.of())); }
    public boolean record(UUID owner, GuidanceEvent event) {
        java.util.Objects.requireNonNull(owner); java.util.Objects.requireNonNull(event);
        if (!readable()) return false;
        if (!earned.containsKey(owner) && earned.size() == MAX_OWNERS) return false;
        return earned.computeIfAbsent(java.util.Objects.requireNonNull(owner), ignored -> EnumSet.noneOf(GuidanceEvent.class)).add(java.util.Objects.requireNonNull(event));
    }
    public JsonObject toJson() {
        if (opaque != null) return opaque.deepCopy();
        var json = new JsonObject(); json.addProperty("schema", 1); var owners = new JsonObject();
        earned.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var events = new com.google.gson.JsonArray(); entry.getValue().stream().sorted().forEach(event -> events.add(event.name())); owners.add(entry.getKey().toString(), events);
        });
        json.add("earned", owners); return json;
    }
    public static GuidanceFacts fromJson(JsonObject json) {
        var result = new GuidanceFacts();
        try {
            if (!json.keySet().equals(Set.of("schema", "earned")) || json.get("schema").getAsBigDecimal().intValueExact() != 1) throw new IllegalArgumentException("Unknown guidance layout");
            var owners = json.getAsJsonObject("earned"); if (owners.size() > MAX_OWNERS) throw new IllegalArgumentException("Too many guidance owners");
            for (var entry : owners.entrySet()) {
                UUID owner = UUID.fromString(entry.getKey()); if (!owner.toString().equals(entry.getKey())) throw new IllegalArgumentException("Noncanonical owner");
                var values = entry.getValue().getAsJsonArray(); if (values.size() > GuidanceEvent.values().length) throw new IllegalArgumentException("Too many guidance events");
                var events = EnumSet.noneOf(GuidanceEvent.class);
                for (var value : values) if (!events.add(GuidanceEvent.valueOf(value.getAsString()))) throw new IllegalArgumentException("Duplicate guidance event");
                result.earned.put(owner, events);
            }
        } catch (RuntimeException unknown) { result.earned.clear(); result.opaque = json.deepCopy(); }
        return result;
    }
}
