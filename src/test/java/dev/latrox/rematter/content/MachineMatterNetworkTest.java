package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.MatterInventory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static dev.latrox.rematter.content.MachineMatterNetwork.FITS;
import static dev.latrox.rematter.content.MachineMatterNetwork.TOO_LARGE;
import static org.junit.jupiter.api.Assertions.*;

class MachineMatterNetworkTest {
    private static final int BASIC = MatterPipeBlock.Tier.BASIC.ordinal();
    private static final int REINFORCED = MatterPipeBlock.Tier.REINFORCED.ordinal();
    private static final int RESONANT = MatterPipeBlock.Tier.RESONANT.ordinal();
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Test void oneWeakerForeignCableCannotCapAStrongerNetwork() {
        assertEquals(REINFORCED, MachineMatterNetwork.nextStage(BASIC, 301, false, BASIC));
        assertEquals(FITS, MachineMatterNetwork.nextStage(REINFORCED, 300, false, RESONANT));
    }
    @Test void networksWithinTheirWeakestCableLimitFitAtOnce() {
        assertEquals(FITS, MachineMatterNetwork.nextStage(BASIC, 256, false, BASIC));
        assertEquals(FITS, MachineMatterNetwork.nextStage(BASIC, 384, false, REINFORCED));
        assertEquals(FITS, MachineMatterNetwork.nextStage(BASIC, 512, false, RESONANT));
        assertEquals(FITS, MachineMatterNetwork.nextStage(BASIC, 3, false, -1));
    }
    @Test void everyStageProgressesAndTheStrongestTierIsTheLastResort() {
        assertEquals(RESONANT, MachineMatterNetwork.nextStage(REINFORCED, 385, false, REINFORCED));
        assertEquals(TOO_LARGE, MachineMatterNetwork.nextStage(RESONANT, 513, false, RESONANT));
        assertEquals(REINFORCED, MachineMatterNetwork.nextStage(BASIC, 512, true, BASIC));
        assertEquals(TOO_LARGE, MachineMatterNetwork.nextStage(RESONANT, 512, true, RESONANT));
        assertEquals(TOO_LARGE, MachineMatterNetwork.nextStage(BASIC, 257, false, -1));
        for (int stage = BASIC; stage < TOO_LARGE; stage++)
            for (int weakest = stage; weakest <= RESONANT; weakest++) {
                int next = MachineMatterNetwork.nextStage(stage, 1000, false, weakest);
                assertTrue(next == TOO_LARGE || next > stage);
            }
    }
    @Test void cableRoutesDoNotChangeTheBatchBindingButEndpointsDo() {
        var cell = new MatterInventory(1000, 4);
        var drive = device(0, "drive", List.of(cell));
        var reactor = device(4, "reactor", List.of());
        var route = network(OWNER, List.of(drive, cable(1, BASIC), cable(2, BASIC), cable(3, BASIC), reactor));
        var detour = network(OWNER, List.of(drive, cable(1, BASIC), cable(2, RESONANT), cable(3, BASIC), cable(9, BASIC), reactor));
        assertTrue(route.sameTopology(detour));
        assertTrue(detour.sameTopology(route));
        var otherCell = network(OWNER, List.of(device(0, "drive", List.of(new MatterInventory(1000, 4))), cable(1, BASIC), reactor));
        assertFalse(route.sameTopology(otherCell));
        var withoutReactor = network(OWNER, List.of(drive, cable(1, BASIC)));
        assertFalse(route.sameTopology(withoutReactor));
        var foreign = network(UUID.fromString("00000000-0000-0000-0000-00000000000b"), List.of(drive, reactor));
        assertFalse(route.sameTopology(foreign));
        assertFalse(route.sameTopology(null));
    }
    private static MachineMatterNetwork network(UUID owner, List<MachineMatterNetwork.Node> nodes) {
        var sorted = new ArrayList<>(nodes);
        sorted.sort(java.util.Comparator.comparing(MachineMatterNetwork.Node::position));
        return new MachineMatterNetwork(owner, sorted, Set.of(), List.of(), "ready", 0, List.of(), null);
    }
    private static MachineMatterNetwork.Node device(int x, String block, List<MatterInventory> inventories) {
        return new MachineMatterNetwork.Node(new BlockPos(x, 64, 0), block, -1, null, null, null, "", "ready", inventories);
    }
    private static MachineMatterNetwork.Node cable(int x, int tier) {
        return new MachineMatterNetwork.Node(new BlockPos(x, 64, 0), "cable", tier, null, null, null, "", "", List.of());
    }
}
