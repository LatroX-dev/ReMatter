package dev.latrox.rematter.content;

import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatterCableConnectionPolicyTest {
    @Test void rotatedMachinesRejectFrontAndTopCablesAndTerminalOnlyUsesBack() {
        for (var front : Direction.Plane.HORIZONTAL) for (var face : Direction.values()) {
            assertEquals(face != front && face != Direction.UP,
                    MatterCableConnectionPolicy.acceptsMachine(MachineKind.DECOMPOSER, front, face, true));
            assertTrue(MatterCableConnectionPolicy.acceptsMachine(MachineKind.DECOMPOSER, front, face, false));
            for (boolean cable : new boolean[]{true, false}) {
                assertEquals(face == front.getOpposite(),
                        MatterCableConnectionPolicy.acceptsMachine(MachineKind.TERMINAL, front, face, cable));
                assertFalse(MatterCableConnectionPolicy.acceptsMachine(MachineKind.INDUSTRIAL_PREPARATOR, front, face, cable));
                assertFalse(MatterCableConnectionPolicy.acceptsMachine(MachineKind.INDUSTRIAL_REACTOR, front, face, cable));
            }
        }
    }

    @Test void unformedPortsNeverClaimAConnectionEvenWhenTheyRetainAnOldFacing() {
        for (var outward : Direction.values()) for (var towardsPort : Direction.values())
            assertFalse(MatterCableConnectionPolicy.acceptsPort(false,outward,towardsPort));
    }

    @Test void rotatedScalableShellsOnlyConnectThroughTheirActualExterior() {
        for (var shape : List.of(new IndustrialStructure.Shape(3,5,3,1),
                new IndustrialStructure.Shape(5,7,4,3),new IndustrialStructure.Shape(9,9,9,4))) {
            for (var facing : Direction.Plane.HORIZONTAL) {
                var volume=new HashSet<BlockPos>();
                for (var cell : IndustrialStructure.layout(shape))
                    volume.add(IndustrialStructure.worldPosition(BlockPos.ZERO,facing,cell));
                for (var cell : IndustrialStructure.layout(shape)) {
                    if (cell.role()!=IndustrialStructure.Role.WALL) continue;
                    var port=IndustrialStructure.worldPosition(BlockPos.ZERO,facing,cell);
                    var outward=IndustrialStructure.worldDirection(facing,cell.outward());
                    int connected=0;
                    for (var towardsPort : Direction.values()) {
                        boolean accepted=MatterCableConnectionPolicy.acceptsPort(true,outward,towardsPort);
                        var cable=port.relative(towardsPort.getOpposite());
                        assertEquals(!volume.contains(cable),accepted,"Only the exterior neighbor may attach");
                        if (accepted) connected++;
                    }
                    assertEquals(1,connected,"Each valid wall port has exactly one exterior face");
                }
            }
        }
    }
}
