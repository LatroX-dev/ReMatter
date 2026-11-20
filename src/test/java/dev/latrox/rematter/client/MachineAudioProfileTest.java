package dev.latrox.rematter.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MachineAudioProfileTest {
    private static final String VALID = """
            {"format": 1, "active_set": "test", "machines": {
              "material_preparator": {
                "role": "preparator", "set": "test", "position": "block",
                "loop": {"event": "rematter:preparator_work", "gain": 0.9, "pitch": 0.88, "fade_in": 3, "hold": 6, "fade_out": 14},
                "accent": {"event": "rematter:preparator_accent", "interval": 60, "offset": 30, "jitter": 20, "gain": 0.5, "mixing": true},
                "cues": [
                  {"event": "rematter:preparator_cycle", "source": "machine", "period": 24, "phase": 0.25, "gain": 0.85,
                   "stretch": true, "min_drive": 0.6, "mixing": false},
                  {"event": "rematter:synthesizer_cycle", "source": "chamber", "period": 20, "phase": 0.0, "gain": 1.0,
                   "stretch": false, "min_drive": 0.0, "progress_pitch": [0.94, 1.08]}
                ]
              },
              "chemical_reactor_controller": {
                "position": "chamber",
                "loop": {"event": "rematter:chemical_reactor_work", "gain": 1.0, "pitch": 1.0, "fade_in": 8, "hold": 10, "fade_out": 12}
              }
            }}""";

    @Test void validFileYieldsEveryValue() {
        var parsed = MachineAudioProfile.parse(VALID);
        assertNull(parsed.failure());
        assertTrue(parsed.rejected().isEmpty());
        assertEquals(2, parsed.machines().size());
        var preparator = parsed.machines().get("material_preparator");
        assertEquals("preparator", preparator.role());
        assertFalse(preparator.chamber());
        assertEquals(new MachineAudioProfile.Loop("rematter:preparator_work", .9F, .88F, 3, 6, 14), preparator.loop());
        assertEquals(new MachineAudioProfile.Accent("rematter:preparator_accent", 60, 30, 20, .5F, true), preparator.accent());
        assertEquals(List.of(
                new MachineAudioProfile.Cue("rematter:preparator_cycle", false, 24, .25, .85F, true, .6F, false, 1, 1, false),
                new MachineAudioProfile.Cue("rematter:synthesizer_cycle", true, 20, 0, 1, false, 0, true, .94F, 1.08F, null)),
                preparator.cues());
    }
    @Test void optionalBlocksMayBeAbsent() {
        var reactor = MachineAudioProfile.parse(VALID).machines().get("chemical_reactor_controller");
        assertTrue(reactor.chamber());
        assertEquals("", reactor.role());
        assertNull(reactor.accent());
        assertTrue(reactor.cues().isEmpty());
        var bare = MachineAudioProfile.parse("""
                {"format": 1, "machines": {"analyzer": {"position": "block", "cues": [],
                  "loop": {"event": "rematter:analyzer_work", "gain": 1, "pitch": 1, "fade_in": 8, "hold": 10, "fade_out": 12},
                  "accent": {"event": "rematter:analyzer_accent", "interval": 80, "offset": 30, "jitter": 6, "gain": 0.34}}}}""");
        assertNull(bare.failure());
        var accent = bare.machines().get("analyzer").accent();
        assertNotNull(accent);
        assertNull(accent.mixing());
        assertEquals(6, accent.jitter());
    }
    @Test void garbageNeverThrowsAndLeavesNoProfiles() {
        for (String garbage : new String[]{null, "", "   ", "not json", "{", "[]", "42", "{\"format\": 1}", "{\"format\": 2, \"machines\": {}}",
                "{\"format\": \"1\", \"machines\": {}}", "{\"format\": 1, \"machines\": []}", "{\"machines\": {}}"}) {
            var parsed = MachineAudioProfile.parse(garbage);
            assertNotNull(parsed.failure(), String.valueOf(garbage));
            assertTrue(parsed.machines().isEmpty(), String.valueOf(garbage));
        }
    }
    @Test void aBrokenEntryOnlyLosesThatEntry() {
        String loop = "\"loop\": {\"event\": \"rematter:analyzer_work\", \"gain\": 1, \"pitch\": 1, \"fade_in\": 8, \"hold\": 10, \"fade_out\": 12}";
        String cue = "{\"event\": \"rematter:analyzer_cycle\", \"source\": \"machine\", \"period\": 48, \"phase\": 0, \"gain\": 1, \"stretch\": true, \"min_drive\": 0.6}";
        String[] broken = {
                "42",
                "{\"position\": \"block\"}",
                "{\"position\": \"ceiling\", " + loop + "}",
                "{\"position\": \"block\", \"loop\": {\"event\": \"\", \"gain\": 1, \"pitch\": 1, \"fade_in\": 8, \"hold\": 10, \"fade_out\": 12}}",
                "{\"position\": \"block\", \"loop\": {\"event\": \"rematter:analyzer_work\", \"gain\": 1, \"pitch\": 1, \"fade_in\": 0, \"hold\": 10, \"fade_out\": 12}}",
                "{\"position\": \"block\", \"loop\": {\"event\": \"rematter:analyzer_work\", \"gain\": 1, \"pitch\": 1, \"fade_in\": 8.5, \"hold\": 10, \"fade_out\": 12}}",
                "{\"position\": \"block\", \"loop\": {\"event\": \"rematter:analyzer_work\", \"gain\": 1, \"pitch\": 9, \"fade_in\": 8, \"hold\": 10, \"fade_out\": 12}}",
                "{\"position\": \"block\", \"loop\": {\"event\": \"rematter:analyzer_work\", \"gain\": \"loud\", \"pitch\": 1, \"fade_in\": 8, \"hold\": 10, \"fade_out\": 12}}",
                "{\"position\": \"block\", " + loop + ", \"accent\": {\"event\": \"rematter:analyzer_accent\", \"interval\": 0, \"offset\": 30, \"jitter\": 6, \"gain\": 1}}",
                "{\"position\": \"block\", " + loop + ", \"accent\": {\"event\": \"rematter:analyzer_accent\", \"interval\": 80, \"offset\": 30, \"jitter\": 6, \"gain\": 1, \"mixing\": \"yes\"}}",
                "{\"position\": \"block\", " + loop + ", \"cues\": {}}",
                "{\"position\": \"block\", " + loop + ", \"cues\": [" + cue.replace("\"period\": 48", "\"period\": 0") + "]}",
                "{\"position\": \"block\", " + loop + ", \"cues\": [" + cue.replace("\"machine\"", "\"moon\"") + "]}",
                "{\"position\": \"block\", " + loop + ", \"cues\": [" + cue.replace("\"min_drive\": 0.6", "\"min_drive\": 1.5") + "]}",
                "{\"position\": \"block\", " + loop + ", \"cues\": [" + cue.replace(", \"stretch\": true", "") + "]}",
                "{\"position\": \"block\", " + loop + ", \"cues\": [" + cue.replace("\"gain\": 1", "\"gain\": 1, \"progress_pitch\": [1]") + "]}",
                "{\"position\": \"block\", " + loop + ", \"cues\": [" + cue.replace("\"gain\": 1", "\"gain\": 1, \"progress_pitch\": [1, \"up\"]") + "]}",
        };
        for (String entry : broken) {
            var parsed = MachineAudioProfile.parse("{\"format\": 1, \"machines\": {\"decomposer\": " + entry
                    + ", \"analyzer\": {\"position\": \"block\", " + loop + ", \"cues\": [" + cue + "]}}}");
            assertNull(parsed.failure(), entry);
            assertEquals(java.util.Set.of("decomposer"), parsed.rejected().keySet(), entry);
            assertFalse(parsed.rejected().get("decomposer").isBlank(), entry);
            assertEquals(java.util.Set.of("analyzer"), parsed.machines().keySet(), entry);
        }
    }
    @Test void aMachineWithoutAUsableProfileGetsTheSafeOne() {
        var plain = MachineAudioProfile.fallback("rematter:x_work");
        java.util.function.Predicate<String> all = event -> true;
        // Garbage, a missing resource, a missing entry, a rejected entry and an unregistered event all end the same way.
        for (String file : new String[]{null, "garbage", "{\"format\": 3, \"machines\": {}}"}) {
            var choice = MachineAudioProfile.choose(MachineAudioProfile.parse(file), "material_preparator", "rematter:x_work", all);
            assertEquals(plain, choice.profile(), String.valueOf(file));
            assertNotNull(choice.problem(), String.valueOf(file));
        }
        var parsed = MachineAudioProfile.parse(VALID);
        assertEquals(new MachineAudioProfile.Choice(plain, "no entry"), MachineAudioProfile.choose(parsed, "analyzer", "rematter:x_work", all));
        var rejected = MachineAudioProfile.parse("{\"format\": 1, \"machines\": {\"analyzer\": {\"position\": \"block\"}}}");
        assertEquals(new MachineAudioProfile.Choice(plain, "loop is not an object"), MachineAudioProfile.choose(rejected, "analyzer", "rematter:x_work", all));
        for (String missing : List.of("rematter:preparator_work", "rematter:preparator_accent", "rematter:preparator_cycle", "rematter:synthesizer_cycle")) {
            var choice = MachineAudioProfile.choose(parsed, "material_preparator", "rematter:x_work", event -> !event.equals(missing));
            assertEquals(new MachineAudioProfile.Choice(plain, "sound event " + missing + " is not registered"), choice);
        }
        var usable = MachineAudioProfile.choose(parsed, "material_preparator", "rematter:x_work", all);
        assertNull(usable.problem());
        assertSame(parsed.machines().get("material_preparator"), usable.profile());
        assertEquals(List.of("rematter:preparator_work", "rematter:preparator_accent", "rematter:preparator_cycle", "rematter:synthesizer_cycle"),
                usable.profile().events());
    }
    @Test void fallbackIsThePlainLoopWithTheStandardFades() {
        var plain = MachineAudioProfile.fallback("rematter:sinter_work");
        assertEquals(new MachineAudioProfile.Loop("rematter:sinter_work", 1, 1, 8, 10, 12), plain.loop());
        assertFalse(plain.chamber());
        assertNull(plain.accent());
        assertTrue(plain.cues().isEmpty());
    }
    @Test void shippedFileAndArchivedSetParseWithoutRejections() throws IOException {
        var shipped = MachineAudioProfile.parse(Files.readString(Path.of("src/main/resources/assets/rematter/machine_audio.json")));
        assertNull(shipped.failure());
        assertTrue(shipped.rejected().isEmpty(), shipped.rejected().toString());
        assertEquals(13, shipped.machines().size());
        // The archived set carries the behaviour before motion cues: timer accents with jitter 6 and three pitched kinds.
        var archive = com.google.gson.JsonParser.parseString(Files.readString(Path.of("artwork/sound-sets/studio-v10/set.json"))).getAsJsonObject();
        var wrapped = new com.google.gson.JsonObject();
        wrapped.addProperty("format", 1);
        wrapped.add("machines", archive.get("machine_audio"));
        var archived = MachineAudioProfile.parse(wrapped.toString());
        assertNull(archived.failure());
        assertTrue(archived.rejected().isEmpty(), archived.rejected().toString());
        assertEquals(13, archived.machines().size());
        for (var profile : archived.machines().values()) {
            assertTrue(profile.cues().isEmpty());
            assertFalse(profile.chamber());
            assertNotNull(profile.accent());
            assertEquals(6, profile.accent().jitter());
            assertEquals(new MachineAudioProfile.Loop(profile.loop().event(), 1, profile.loop().pitch(), 8, 10, 12), profile.loop());
        }
        assertEquals(.94F, archived.machines().get("chemical_reactor_controller").loop().pitch());
        assertEquals(.88F, archived.machines().get("preparation_reactor_controller").loop().pitch());
        assertEquals(.90F, archived.machines().get("industrial_crystal_generator").loop().pitch());
        assertEquals(new MachineAudioProfile.Accent("rematter:hand_preparator_accent", 28, 12, 6, .42F, null),
                archived.machines().get("hand_preparator").accent());
    }
}
