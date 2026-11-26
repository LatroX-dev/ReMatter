package dev.latrox.rematter.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.network.MachineExtraPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SynthesisStockPayloadTest {
    @Test void fullSynthesizerViewWithHugeKnownTargetsAndLongSubstanceIdsStaysWithinMachineExtraPayloadLimit() {
        JsonObject out = populatedView(800);
        int baseLen = out.toString().length();
        assertTrue(baseLen > 200_000, "Precondition: populated view should exceed 200k chars, saw " + baseLen);

        List<SubstanceKey> needed = new ArrayList<>();
        Map<SubstanceKey, Long> contents = new HashMap<>();
        String subPad = "a".repeat(235);
        for (int i = 0; i < 1024; i++) {
            SubstanceKey key = SubstanceKey.of(String.format("datapack:sub_%04d_%s", i, subPad), Phase.SOLID);
            needed.add(key);
            contents.put(key, 1L);
        }

        MachineExtensions.populateSynthesisStockForView(out, true, contents, needed);
        String json = out.toString();

        assertTrue(out.get("synthesisStockReady").getAsBoolean());
        assertFalse(out.get("synthesisStockComplete").getAsBoolean(),
                "Combined knownTargets + long synthesisStock must mark synthesisStockComplete=false");
        assertTrue(json.length() <= MachineExtraPayload.MAX_CHARS,
                "Combined Synthesizer extras payload must stay within MachineExtraPayload.MAX_CHARS, saw " + json.length());
        assertTrue(out.getAsJsonObject("synthesisStock").size() > 0 && out.getAsJsonObject("synthesisStock").size() < 365,
                "Stock map must fit strictly inside remaining payload headroom");
        assertDoesNotThrow(() -> new MachineExtraPayload(1, json));
    }

    @Test void viewsRightAtPayloadCapNeverOverflowInEitherStorageState() {
        // Construct a previously sendable 262,100-char view (44 chars below MAX_CHARS = 262,144).
        for (boolean storageReady : new boolean[]{true, false}) {
            JsonObject out = populatedView(1023);
            int gap = 262_100 - out.toString().length();
            assertTrue(gap >= 0, "Expected base below 262,100, got gap=" + gap);
            if (gap > 0) {
                out.addProperty("customName", out.get("customName").getAsString() + "x".repeat(gap));
            }
            assertEquals(262_100, out.toString().length());

            SubstanceKey iron = SubstanceKey.of("rematter:iron", Phase.SOLID);
            MachineExtensions.populateSynthesisStockForView(out, storageReady, Map.of(iron, 4_000L), List.of(iron));
            String json = out.toString();

            assertTrue(json.length() <= MachineExtraPayload.MAX_CHARS,
                    "Boundary payload (storageReady=" + storageReady + ") must NEVER exceed MAX_CHARS, saw " + json.length());
            assertFalse(out.has("synthesisStockReady"),
                    "When remaining headroom is smaller than wrapper overhead, stock fields must be omitted cleanly");
            assertDoesNotThrow(() -> new MachineExtraPayload(1, json));
        }

        // Also check exact edge where ready=false fits (65 chars headroom >= 58) while ready=true omits cleanly (65 < 78).
        JsonObject outNoStorageFits = populatedView(1022);
        int gap65 = (MachineExtraPayload.MAX_CHARS - 65) - outNoStorageFits.toString().length();
        assertTrue(gap65 >= 0);
        outNoStorageFits.addProperty("customName", outNoStorageFits.get("customName").getAsString() + "y".repeat(gap65));
        MachineExtensions.populateSynthesisStockForView(outNoStorageFits, false, Map.of(), List.of());
        assertTrue(outNoStorageFits.has("synthesisStockReady") && !outNoStorageFits.get("synthesisStockReady").getAsBoolean());
        assertTrue(outNoStorageFits.toString().length() <= MachineExtraPayload.MAX_CHARS);
        assertDoesNotThrow(() -> new MachineExtraPayload(1, outNoStorageFits.toString()));

        JsonObject outReadyOmitted = populatedView(1022);
        outReadyOmitted.addProperty("customName", outReadyOmitted.get("customName").getAsString() + "y".repeat(gap65));
        MachineExtensions.populateSynthesisStockForView(outReadyOmitted, true, Map.of(SubstanceKey.of("rematter:iron", Phase.SOLID), 1L),
                List.of(SubstanceKey.of("rematter:iron", Phase.SOLID)));
        assertFalse(outReadyOmitted.has("synthesisStockReady"));
        assertTrue(outReadyOmitted.toString().length() <= MachineExtraPayload.MAX_CHARS);
        assertDoesNotThrow(() -> new MachineExtraPayload(1, outReadyOmitted.toString()));
    }

    @Test void normalSynthesisStockStaysCompleteAndExact() {
        JsonObject out = new JsonObject();
        List<SubstanceKey> needed = new ArrayList<>();
        Map<SubstanceKey, Long> contents = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            SubstanceKey key = SubstanceKey.of("rematter:sub_" + i, Phase.SOLID);
            needed.add(key);
            contents.put(key, 1_000L + i);
        }

        MachineExtensions.populateSynthesisStockForView(out, true, contents, needed);

        assertTrue(out.get("synthesisStockReady").getAsBoolean());
        assertTrue(out.get("synthesisStockComplete").getAsBoolean());
        assertEquals(100, out.getAsJsonObject("synthesisStock").size());
        assertEquals(1_042L, out.getAsJsonObject("synthesisStock").get("rematter:sub_42|solid").getAsLong());
        assertDoesNotThrow(() -> new MachineExtraPayload(1, out.toString()));
    }

    private static JsonObject populatedView(int targetCount) {
        JsonObject out = new JsonObject();
        out.addProperty("blockX", 10);
        out.addProperty("blockY", 64);
        out.addProperty("blockZ", -20);
        out.addProperty("customName", "Industrial Synthesizer");
        out.addProperty("researchEnabled", true);
        JsonArray knownTargets = new JsonArray();
        String targetPad = "t".repeat(235);
        for (int i = 0; i < targetCount; i++) {
            knownTargets.add(String.format("datapack:out_%04d_%s", i, targetPad));
        }
        out.add("knownTargets", knownTargets);
        out.add("targets", new JsonArray());
        return out;
    }
}
