package dev.latrox.rematter.content;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatterCablePowerTest {
    @Test void theCableBudgetEvictsOnlyTheLeastRecentlyUsedComponents() {
        var index = new MatterCablePower.ComponentIndex<String>(100);
        index.put("a", line(0, 40)); index.put("b", line(100, 40));
        assertEquals("a", index.get(new BlockPos(0, 0, 0)));
        index.put("c", line(200, 40));
        assertEquals("a", index.get(new BlockPos(39, 0, 0)));
        assertNull(index.get(new BlockPos(100, 0, 0)));
        assertEquals("c", index.get(new BlockPos(200, 0, 0)));
    }
    @Test void aRescannedComponentDisplacesEveryComponentItOverlapsAsAWhole() {
        var index = new MatterCablePower.ComponentIndex<String>(1000);
        index.put("left", line(0, 10)); index.put("right", line(20, 10));
        var merged = line(0, 30);
        index.put("merged", merged);
        for (var position : merged) assertEquals("merged", index.get(position));
        index.put("split", line(0, 5));
        assertNull(index.get(new BlockPos(10, 0, 0)));
        assertEquals("split", index.get(new BlockPos(4, 0, 0)));
    }
    @Test void removingAStaleComponentKeepsTheOthersAndOversizedOnesAreNotIndexed() {
        var index = new MatterCablePower.ComponentIndex<String>(50);
        index.put("kept", line(0, 20)); index.put("stale", line(100, 20));
        index.remove("stale");
        assertNull(index.get(new BlockPos(100, 0, 0)));
        assertEquals("kept", index.get(new BlockPos(0, 0, 0)));
        index.put("huge", line(200, 51));
        assertNull(index.get(new BlockPos(200, 0, 0)));
        assertEquals("kept", index.get(new BlockPos(19, 0, 0)));
    }
    @Test void pushedFeReachesOnlyTheNearestOwnersDevices() {
        var owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var stranger = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var supply = List.of(candidate("decomposer", owner, 2, 0), candidate("synthesizer", owner, 9, 1),
                candidate("tap", stranger, 5, 2), candidate("unclaimed", null, 1, 3));
        assertEquals(List.of("decomposer", "synthesizer"), MatterCablePower.recipients(supply));
        var tie = List.of(candidate("stranger", stranger, 2, 1), candidate("owner", owner, 2, 0));
        assertEquals(List.of("owner"), MatterCablePower.recipients(tie));
        assertEquals(List.of(), MatterCablePower.recipients(List.of(candidate("unclaimed", null, 1, 0))));
    }
    @Test void onlyChunkBorderColumnsAreRepairedAfterLoading() {
        assertTrue(MatterPipeConnections.borderColumn(0)); assertTrue(MatterPipeConnections.borderColumn(15));
        assertTrue(MatterPipeConnections.borderColumn(-1)); assertTrue(MatterPipeConnections.borderColumn(-16));
        assertFalse(MatterPipeConnections.borderColumn(7)); assertFalse(MatterPipeConnections.borderColumn(-8));
    }
    private static MatterCablePower.Candidate<String> candidate(String name, UUID owner, int distance, int x) {
        return new MatterCablePower.Candidate<>(name, owner, distance, new BlockPos(x, 0, 0));
    }
    private static Set<BlockPos> line(int start, int length) {
        var positions = new HashSet<BlockPos>();
        for (int x = start; x < start + length; x++) positions.add(new BlockPos(x, 0, 0));
        return Set.copyOf(positions);
    }
}
