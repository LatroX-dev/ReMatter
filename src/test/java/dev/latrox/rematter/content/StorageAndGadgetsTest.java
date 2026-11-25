package dev.latrox.rematter.content;

import com.google.gson.JsonParser;
import dev.latrox.rematter.chemistry.BuiltinChemistry;
import dev.latrox.rematter.chemistry.MatterInventory;
import dev.latrox.rematter.chemistry.MatterNetworkInventory;
import dev.latrox.rematter.chemistry.ReservedSubstances;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.chemistry.VoidPolicy;
import dev.latrox.rematter.content.generation.CrystalFuelData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static dev.latrox.rematter.chemistry.BuiltinChemistry.COPPER;
import static dev.latrox.rematter.chemistry.BuiltinChemistry.HYDROGEN;
import static dev.latrox.rematter.chemistry.BuiltinChemistry.IRON;
import static dev.latrox.rematter.chemistry.BuiltinChemistry.OXYGEN;
import static dev.latrox.rematter.chemistry.BuiltinChemistry.WATER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageAndGadgetsTest {
    private static final SubstanceKey PRIMAL_KEY = new SubstanceKey(ReservedSubstances.PRIMAL_MATTER, dev.latrox.rematter.chemistry.Phase.SOLID);

    @Test
    void voidPolicyAndNetworkInventoryFillFiniteCellsFirstThenVoidAllowedOverflowOnly() {
        assertFalse(VoidPolicy.NONE.accepts(COPPER));
        assertTrue(VoidPolicy.any().accepts(COPPER));
        assertFalse(VoidPolicy.any().accepts(PRIMAL_KEY));

        var waterOnly = VoidPolicy.only(Set.of(WATER, PRIMAL_KEY));
        assertTrue(waterOnly.accepts(WATER));
        assertFalse(waterOnly.accepts(COPPER));
        assertFalse(waterOnly.accepts(PRIMAL_KEY));

        var merged = VoidPolicy.merge(List.of(VoidPolicy.NONE, waterOnly, VoidPolicy.only(Set.of(HYDROGEN))));
        assertTrue(merged.accepts(WATER) && merged.accepts(HYDROGEN) && !merged.accepts(COPPER));
        assertTrue(VoidPolicy.merge(List.of(waterOnly, VoidPolicy.any())).unrestricted());

        // Finite cell fills to exact capacity first; unpartitioned void cell absorbs only excess ordinary matter.
        var finite = new MatterInventory(100, 2);
        finite.insert(COPPER, 40);
        var unpartitionedNet = new MatterNetworkInventory(List.of(finite), VoidPolicy.any());
        assertTrue(unpartitionedNet.canApply(Map.of(), Map.of(COPPER, 90L)));
        assertTrue(unpartitionedNet.apply(Map.of(), Map.of(COPPER, 90L)));
        assertEquals(100L, finite.amount(COPPER));

        // Partitioned void policy voids WATER overflow after filling finite space, while rejecting unpartitioned IRON overflow.
        var partitionedFinite = new MatterInventory(100, 2);
        var partitionedNet = new MatterNetworkInventory(List.of(partitionedFinite), waterOnly);
        assertTrue(partitionedNet.apply(Map.of(), Map.of(WATER, 150L)));
        assertEquals(100L, partitionedFinite.amount(WATER));
        assertFalse(partitionedNet.canApply(Map.of(), Map.of(IRON, 1L)));
        assertFalse(partitionedNet.apply(Map.of(), Map.of(IRON, 1L)));
        assertEquals(Map.of(WATER, 100L), partitionedFinite.contents());

        // Pure void-only network (no finite cells mounted) accepts allowed outputs and rejects others.
        var pureVoid = new MatterNetworkInventory(List.of(), VoidPolicy.only(Set.of(OXYGEN)));
        assertTrue(pureVoid.canApply(Map.of(), Map.of(OXYGEN, 500L)));
        assertTrue(pureVoid.apply(Map.of(), Map.of(OXYGEN, 500L)));
        assertEquals(0L, pureVoid.amount(OXYGEN));
        assertFalse(pureVoid.canApply(Map.of(), Map.of(HYDROGEN, 1L)));
    }

    @Test
    void batchReservationsAllowVoidedOutputsAtZeroFreeSpaceWhileBlockingUnvoidedOutputs() {
        var book = new BatchReservations();
        var waterOnly = VoidPolicy.only(Set.of(WATER));
        var stock = Map.of(WATER, 1_000L);

        var allowed = book.reserve(
                UUID.randomUUID(), "net", Set.of("cell"), 0L,
                stock, 1_000L, Map.of(), Map.of(WATER, 250L),
                4, 400L, 100L, 4L, 0L, null, waterOnly);
        assertNotNull(allowed);
        assertEquals(4, allowed.remaining());

        var blocked = book.reserve(
                UUID.randomUUID(), "net", Set.of("cell"), 0L,
                stock, 1_000L, Map.of(), Map.of(IRON, 250L),
                4, 400L, 100L, 4L, 0L, null, waterOnly);
        assertNull(blocked);
    }

    @Test
    void configurationCardRoundTripsSoundLevelsBlueprintsAndLevelEmitterSetup() {
        // Verify 4-step forward and backward cycling on MachineSoundLevel.
        assertEquals(MachineSoundLevel.MEDIUM, MachineSoundLevel.HIGH.next());
        assertEquals(MachineSoundLevel.LOW, MachineSoundLevel.MEDIUM.next());
        assertEquals(MachineSoundLevel.MUTED, MachineSoundLevel.LOW.next());
        assertEquals(MachineSoundLevel.HIGH, MachineSoundLevel.MUTED.next());
        assertEquals(MachineSoundLevel.MUTED, MachineSoundLevel.HIGH.previous());
        assertEquals(MachineSoundLevel.LOW, MachineSoundLevel.MUTED.previous());
        assertEquals(MachineSoundLevel.MEDIUM, MachineSoundLevel.LOW.previous());
        assertEquals(MachineSoundLevel.HIGH, MachineSoundLevel.MEDIUM.previous());
        assertEquals(1.0f, MachineSoundLevel.HIGH.gain());
        assertEquals(0.65f, MachineSoundLevel.MEDIUM.gain());
        assertEquals(0.35f, MachineSoundLevel.LOW.gain());
        assertEquals(0.0f, MachineSoundLevel.MUTED.gain());

        var machineSettings = new MachineSettings(2_000L, "rematter:iron", "solid", Set.of("rematter:oxygen"));
        var mutedSetup = new ConfigurationCardData.MachineSetup(
                MachineKind.DECOMPOSER, 0, RedstoneMode.HIGH,
                true, true, true, false, "", machineSettings, true);
        var restoredMuted = ConfigurationCardData.fromMachine(mutedSetup).machineSetup().orElseThrow();
        assertTrue(restoredMuted.soundMuted());
        assertEquals(MachineSoundLevel.MUTED, restoredMuted.soundLevel());
        assertEquals(RedstoneMode.HIGH, restoredMuted.redstoneMode());

        var lowReactorBlueprint = IndustrialBlueprint.defaultFor(MachineKind.INDUSTRIAL_REACTOR)
                .adjust(IndustrialBlueprint.Parameter.CHAMBERS, 2)
                .adjust(IndustrialBlueprint.Parameter.COILS, 1)
                .adjust(IndustrialBlueprint.Parameter.PORTS, 2);
        var industrialSetup = new ConfigurationCardData.MachineSetup(
                MachineKind.INDUSTRIAL_REACTOR, 0, RedstoneMode.IGNORED,
                true, false, true, false, "", machineSettings, MachineSoundLevel.LOW, lowReactorBlueprint, true);
        var restoredIndustrial = ConfigurationCardData.fromMachine(industrialSetup).machineSetup().orElseThrow();
        assertFalse(restoredIndustrial.soundMuted());
        assertEquals(MachineSoundLevel.LOW, restoredIndustrial.soundLevel());
        assertEquals(lowReactorBlueprint, restoredIndustrial.blueprint());
        assertTrue(restoredIndustrial.structurePreview());

        var emitterSetup = new ConfigurationCardData.EmitterSetup(BuiltinChemistry.COPPER, 25_000L, true);
        var restoredEmitter = ConfigurationCardData.fromLevelEmitter(emitterSetup).levelEmitterSetup().orElseThrow();
        assertEquals(BuiltinChemistry.COPPER, restoredEmitter.target());
        assertEquals(25_000L, restoredEmitter.threshold());
        assertTrue(restoredEmitter.emitWhenBelow());

        var primalEmitter = new ConfigurationCardData.EmitterSetup(PRIMAL_KEY, -50L, false);
        var restoredPrimal = ConfigurationCardData.fromLevelEmitter(primalEmitter).levelEmitterSetup().orElseThrow();
        assertEquals(PRIMAL_KEY, restoredPrimal.target());
        assertEquals(0L, restoredPrimal.threshold());
        assertFalse(restoredPrimal.emitWhenBelow());

        var clearedEmitter = new ConfigurationCardData.EmitterSetup(null, 500L, false);
        assertNull(ConfigurationCardData.fromLevelEmitter(clearedEmitter).levelEmitterSetup().orElseThrow().target());
    }

    @Test
    void shippedCrystalFuelResourcesDefineNineDistinctEnabledYields() throws IOException {
        Path dir = Path.of("src/main/resources/data/rematter/rematter/crystal_fuel");
        var yields = new HashSet<Long>();
        int count = 0;
        try (var paths = Files.list(dir)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                var fuel = CrystalFuelData.parse(json).orElseThrow(() -> new AssertionError("Disabled fuel: " + path));
                assertEquals(1, fuel.count());
                assertTrue(yields.add(fuel.energy()), "Duplicate crystal fuel yield in " + path + ": " + fuel.energy());
                count++;
            }
        }
        assertEquals(9, count);
        assertEquals(9, yields.size());
    }

    @Test
    void soundManifestUsesOnlyInstalledReMatterClipsAndCoversEveryMachineAndSubtitle() throws Exception {
        Path assets = Path.of("src/main/resources/assets/rematter");
        var manifest = JsonParser.parseString(Files.readString(assets.resolve("sounds.json"))).getAsJsonObject();
        var rendered = JsonParser.parseString(Files.readString(
                Path.of("artwork/sound-rendered.json"))).getAsJsonObject().getAsJsonObject("clips");
        Path soundsDir = assets.resolve("sounds");

        // No vanilla clip anywhere; every referenced clip is installed with exactly the recorded bytes.
        var referenced = new HashSet<String>();
        var subtitles = new HashSet<String>();
        // A registered event that the installed selection does not use is declared with an empty list, so the game
        // does not warn about it at start. Such an event must never be named by a machine or be a working loop.
        var silentEvents = new HashSet<String>();
        for (String eventKey : manifest.keySet()) {
            var event = manifest.getAsJsonObject(eventKey);
            if (event.has("subtitle")) subtitles.add(event.get("subtitle").getAsString());
            var sounds = event.getAsJsonArray("sounds");
            if (sounds.isEmpty()) {
                silentEvents.add(eventKey);
                continue;
            }
            for (var el : sounds) {
                assertTrue(el.isJsonObject(), "Sound entry must be an object in " + eventKey);
                String clipName = el.getAsJsonObject().get("name").getAsString();
                assertFalse(clipName.startsWith("minecraft:"), "Vanilla Minecraft sound forbidden: " + clipName);
                assertTrue(clipName.startsWith("rematter:"), "Sound clip must use rematter namespace: " + clipName);
                String relClip = clipName.substring("rematter:".length());
                Path ogg = soundsDir.resolve(relClip + ".ogg");
                assertTrue(Files.isRegularFile(ogg), "Missing .ogg file for " + clipName);
                assertTrue(rendered.has(relClip), "Missing render record for " + relClip);
                String sha = java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(ogg)));
                assertEquals(rendered.getAsJsonObject(relClip).get("sha256").getAsString(), sha,
                        "Installed clip differs from the render record: " + relClip);
                referenced.add(relClip);
            }
        }
        try (var files = Files.walk(soundsDir)) {
            for (Path ogg : files.filter(path -> path.toString().endsWith(".ogg")).toList()) {
                String relClip = soundsDir.relativize(ogg).toString().replace('\\', '/');
                relClip = relClip.substring(0, relClip.length() - ".ogg".length());
                assertTrue(referenced.contains(relClip), "Installed .ogg is not referenced by sounds.json: " + relClip);
            }
        }

        // Every working loop is subtitled, and every subtitle exists in both languages.
        for (String role : List.of(
                "sinter", "analyzer", "decomposer", "synthesizer",
                "processor", "chemical_reactor", "hand_preparator", "preparator",
                "preparation_reactor", "generator", "converter", "atmospheric")) {
            String primaryKey = role + "_work";
            assertTrue(manifest.has(primaryKey), "Missing working sound event in sounds.json: " + primaryKey);
            assertFalse(silentEvents.contains(primaryKey), "Working sound has no clips: " + primaryKey);
            assertTrue(manifest.getAsJsonObject(primaryKey).has("subtitle"),
                    "Working sound must declare a localized subtitle: " + primaryKey);
        }
        for (String language : List.of("en_us", "de_de")) {
            var lang = JsonParser.parseString(Files.readString(assets.resolve("lang/" + language + ".json"))).getAsJsonObject();
            for (String subtitle : subtitles) assertTrue(lang.has(subtitle), "Missing subtitle " + subtitle + " in " + language);
        }

        // The machine audio description only names installed events and covers every machine that has audio.
        var machines = JsonParser.parseString(Files.readString(assets.resolve("machine_audio.json")))
                .getAsJsonObject().getAsJsonObject("machines");
        for (String machineKey : machines.keySet()) {
            var machine = machines.getAsJsonObject(machineKey);
            var events = new java.util.ArrayList<String>();
            events.add(machine.getAsJsonObject("loop").get("event").getAsString());
            if (machine.has("accent")) events.add(machine.getAsJsonObject("accent").get("event").getAsString());
            for (var cue : machine.getAsJsonArray("cues")) events.add(cue.getAsJsonObject().get("event").getAsString());
            for (String event : events) {
                assertTrue(event.startsWith("rematter:") && manifest.has(event.substring("rematter:".length())),
                        "machine_audio.json names an event that sounds.json lacks: " + event + " (" + machineKey + ")");
                assertFalse(silentEvents.contains(event.substring("rematter:".length())),
                        "machine_audio.json names an event without clips: " + event + " (" + machineKey + ")");
            }
        }
        // ModSounds holds registry objects and cannot be loaded here; its workingSound switch is read as text.
        String registry = Files.readString(Path.of("src/main/java/dev/latrox/rematter/content/ModSounds.java"));
        String working = registry.substring(registry.indexOf("workingSound(MachineKind kind)"), registry.indexOf("accentSound(MachineKind kind)"));
        var silent = new HashSet<String>();
        var withAudio = new HashSet<String>();
        var cases = java.util.regex.Pattern.compile("case ([A-Z_, ]+) -> (\\w+);").matcher(working);
        while (cases.find()) {
            for (String name : cases.group(1).split(",\\s*")) (cases.group(2).equals("null") ? silent : withAudio).add(name.trim());
        }
        for (MachineKind kind : MachineKind.values()) {
            assertTrue(silent.contains(kind.name()) != withAudio.contains(kind.name()), "workingSound switch not understood for " + kind);
            if (withAudio.contains(kind.name())) {
                assertTrue(machines.has(kind.id()), "machine_audio.json has no entry for " + kind.id());
            }
        }
        assertEquals(19, withAudio.size());
    }
}
