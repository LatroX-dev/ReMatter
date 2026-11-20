package dev.latrox.rematter.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * What one machine kind sounds like, as written by the sound generator into {@code assets/rematter/machine_audio.json}:
 * the working loop, an optional free-running accent, and the motion cues fired from the mechanism phase.
 * Sound events stay plain ids here; the sound manager resolves them. All times are client ticks.
 */
public record MachineAudioProfile(String role, boolean chamber, Loop loop, @Nullable Accent accent, List<Cue> cues) {
    public static final int FORMAT = 1;

    public MachineAudioProfile {
        cues = List.copyOf(cues);
    }

    public record Loop(String event, float gain, float pitch, int fadeIn, int hold, int fadeOut) {}

    /** {@code mixing}, when present, limits the layer to a preparation that is (or is not) mixing. */
    public record Accent(String event, int interval, int offset, int jitter, float gain, @Nullable Boolean mixing) {}

    /** {@code chamber} cues follow the envelope of the chamber modules; {@code glides} enables the progress pitch. */
    public record Cue(String event, boolean chamber, double period, double phase, float gain, boolean stretch, float minDrive,
                      boolean glides, float pitchFrom, float pitchTo, @Nullable Boolean mixing) {}

    /**
     * The file's profiles by machine id. {@code rejected} names each entry that was unusable with its reason;
     * {@code failure} is set when the file as a whole was, and then there are no profiles at all.
     */
    public record Parsed(Map<String, MachineAudioProfile> machines, Map<String, String> rejected, @Nullable String failure) {
        public Parsed {
            machines = Map.copyOf(machines);
            rejected = Map.copyOf(rejected);
        }
    }

    /** The safe profile: the kind's own loop, unchanged pitch, the standard fades, nothing layered on top. */
    public static MachineAudioProfile fallback(String loopEvent) {
        return new MachineAudioProfile("", false, new Loop(loopEvent, 1.0F, 1.0F, 8, 10, 12), null, List.of());
    }

    /** The profile a machine plays with, and why it is the safe one when it is. */
    public record Choice(MachineAudioProfile profile, @Nullable String problem) {}

    /**
     * The machine's entry when the file has a usable one that names only registered sound events; otherwise the safe
     * profile around {@code loopEvent}, with the reason.
     */
    public static Choice choose(Parsed parsed, String machine, String loopEvent, Predicate<String> registered) {
        var profile = parsed.machines().get(machine);
        String problem = parsed.failure() != null ? parsed.failure()
                : profile == null ? parsed.rejected().getOrDefault(machine, "no entry")
                : profile.events().stream().filter(registered.negate()).findFirst().map(event -> "sound event " + event + " is not registered").orElse(null);
        return problem == null ? new Choice(profile, null) : new Choice(fallback(loopEvent), problem);
    }

    /** Every sound event id the profile names: the loop, the accent, then the cues in order. */
    public List<String> events() {
        var events = new ArrayList<String>();
        events.add(loop.event());
        if (accent != null) events.add(accent.event());
        for (var cue : cues) events.add(cue.event());
        return events;
    }

    /** Never throws: a broken file yields no profiles, a broken entry only loses that entry. */
    public static Parsed parse(@Nullable String json) {
        var machines = new LinkedHashMap<String, MachineAudioProfile>();
        var rejected = new LinkedHashMap<String, String>();
        try {
            JsonObject root = object(json == null ? null : JsonParser.parseString(json), "file");
            if (integer(root, "format", 0, Integer.MAX_VALUE) != FORMAT) throw new IllegalArgumentException("unsupported format");
            for (var entry : object(root.get("machines"), "machines").entrySet()) {
                try {
                    machines.put(entry.getKey(), machine(object(entry.getValue(), "entry")));
                } catch (RuntimeException invalid) {
                    rejected.put(entry.getKey(), String.valueOf(invalid.getMessage()));
                }
            }
        } catch (RuntimeException invalid) {
            return new Parsed(Map.of(), Map.of(), String.valueOf(invalid.getMessage()));
        }
        return new Parsed(machines, rejected, null);
    }

    private static MachineAudioProfile machine(JsonObject json) {
        JsonObject loop = object(json.get("loop"), "loop");
        var cues = new ArrayList<Cue>();
        if (json.has("cues")) {
            if (!json.get("cues").isJsonArray()) throw new IllegalArgumentException("cues is not a list");
            for (var cue : json.getAsJsonArray("cues")) cues.add(cue(object(cue, "cue")));
        }
        return new MachineAudioProfile(json.has("role") ? text(json, "role") : "", choice(json, "position", "block", "chamber"),
                new Loop(text(loop, "event"), number(loop, "gain", 0, 4), number(loop, "pitch", .5F, 2),
                        integer(loop, "fade_in", 1, 1200), integer(loop, "hold", 1, 1200), integer(loop, "fade_out", 1, 1200)),
                json.has("accent") ? accent(object(json.get("accent"), "accent")) : null, cues);
    }

    private static Accent accent(JsonObject json) {
        return new Accent(text(json, "event"), integer(json, "interval", 1, 72000), integer(json, "offset", 0, 72000),
                integer(json, "jitter", 0, 72000), number(json, "gain", 0, 4), flag(json, "mixing"));
    }

    private static Cue cue(JsonObject json) {
        float from = 1, to = 1;
        boolean glides = json.has("progress_pitch");
        if (glides) {
            if (!json.get("progress_pitch").isJsonArray() || json.getAsJsonArray("progress_pitch").size() != 2) {
                throw new IllegalArgumentException("progress_pitch is not a pair");
            }
            var pair = new JsonObject();
            pair.add("from", json.getAsJsonArray("progress_pitch").get(0));
            pair.add("to", json.getAsJsonArray("progress_pitch").get(1));
            from = number(pair, "from", .25F, 4);
            to = number(pair, "to", .25F, 4);
        }
        Boolean stretch = flag(json, "stretch");
        if (stretch == null) throw new IllegalArgumentException("stretch is missing");
        return new Cue(text(json, "event"), choice(json, "source", "machine", "chamber"), real(json, "period", 1, 72000), real(json, "phase", -1024, 1024),
                number(json, "gain", 0, 4), stretch, number(json, "min_drive", 0, 1), glides, from, to, flag(json, "mixing"));
    }

    private static JsonObject object(@Nullable JsonElement json, String name) {
        if (json == null || !json.isJsonObject()) throw new IllegalArgumentException(name + " is not an object");
        return json.getAsJsonObject();
    }

    private static String text(JsonObject json, String key) {
        var value = json.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank()) {
            throw new IllegalArgumentException(key + " is not a text");
        }
        return value.getAsString();
    }

    private static float number(JsonObject json, String key, float min, float max) {
        return (float) real(json, key, min, max);
    }

    private static double real(JsonObject json, String key, double min, double max) {
        var value = json.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " is not a number");
        }
        double number = value.getAsDouble();
        if (!(number >= min && number <= max)) throw new IllegalArgumentException(key + " is out of range");
        return number;
    }

    private static int integer(JsonObject json, String key, int min, int max) {
        double number = real(json, key, min, max);
        if (number != Math.rint(number)) throw new IllegalArgumentException(key + " is not a whole number");
        return (int) number;
    }

    private static @Nullable Boolean flag(JsonObject json, String key) {
        var value = json.get(key);
        if (value == null) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(key + " is not a flag");
        return value.getAsBoolean();
    }

    /** True for the second word. */
    private static boolean choice(JsonObject json, String key, String first, String second) {
        String value = text(json, key);
        if (!value.equals(first) && !value.equals(second)) throw new IllegalArgumentException(key + " is neither " + first + " nor " + second);
        return value.equals(second);
    }
}
