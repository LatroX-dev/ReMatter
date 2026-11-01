package dev.latrox.rematter.content;

import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IndustrialStructureTest {
    private record Point(int x,int y,int depth) {}
    private static Map<Point,IndustrialStructure.Material> shell(IndustrialStructure.Shape shape) {
        var blocks=new HashMap<Point,IndustrialStructure.Material>();int ports=0;
        for(var cell:IndustrialStructure.layout(shape)) {
            var material=switch(cell.role()) {
                case CONTROLLER->IndustrialStructure.Material.CONTROLLER;
                case INTERIOR->IndustrialStructure.Material.AIR;
                case FRAME->IndustrialStructure.Material.CASING;
                case WALL->ports++<2?IndustrialStructure.Material.PORT:IndustrialStructure.Material.GLASS;
            };
            blocks.put(new Point(cell.x(),cell.y(),cell.depth()),material);
        }
        return blocks;
    }
    private static IndustrialStructure.Materials reader(Map<Point,IndustrialStructure.Material> blocks) {
        return (x,y,z)->blocks.getOrDefault(new Point(x,y,z),IndustrialStructure.Material.AIR);
    }
    @Test void raysDiscoverAsymmetricNonCubicStructuresWithoutGuessingDimensions() {
        for(var shape:List.of(new IndustrialStructure.Shape(5,4,4,1),new IndustrialStructure.Shape(4,5,6,2),new IndustrialStructure.Shape(9,9,9,7))) {
            var blocks=reader(shell(shape));
            assertEquals(shape,IndustrialStructure.detect(MachineKind.INDUSTRIAL_REACTOR,blocks).orElseThrow());
            assertTrue(IndustrialStructure.validates(MachineKind.INDUSTRIAL_REACTOR,shape,blocks));
            for(var facing:Direction.Plane.HORIZONTAL) {
                var world=new HashSet<BlockPos>();
                for(var cell:IndustrialStructure.layout(shape))world.add(IndustrialStructure.worldPosition(BlockPos.ZERO,facing,cell));
                assertEquals(shape.width()*shape.height()*shape.depth(),world.size());
                for(var cell:IndustrialStructure.layout(shape))if(cell.role()==IndustrialStructure.Role.WALL) {
                    var p=IndustrialStructure.worldPosition(BlockPos.ZERO,facing,cell);
                    assertFalse(world.contains(p.relative(IndustrialStructure.worldDirection(facing,cell.outward()))));
                }
            }
        }
    }
    @Test void maximumStructureUsesOneBoundedVolumeEvenWhenEveryWallFaceIsAPort() {
        var shape=new IndustrialStructure.Shape(9,9,9,4);var blocks=shell(shape);int ports=0;
        for(var cell:IndustrialStructure.layout(shape))if(cell.role()==IndustrialStructure.Role.WALL){blocks.put(new Point(cell.x(),cell.y(),cell.depth()),IndustrialStructure.Material.PORT);ports++;}
        assertEquals(293,ports);var reads=new AtomicInteger();
        IndustrialStructure.Materials counted=(x,y,z)->{reads.incrementAndGet();return reader(blocks).at(x,y,z);};
        var detected=IndustrialStructure.detect(MachineKind.INDUSTRIAL_PREPARATOR,counted).orElseThrow();
        assertTrue(IndustrialStructure.validates(MachineKind.INDUSTRIAL_PREPARATOR,detected,counted));
        assertTrue(reads.get()<=729+33,"Detection must not enumerate dimension combinations or rescan the volume for every port");
    }
    @Test void interiorsFramesPortsLoadednessAndMinimumHeightAllRemainMandatory() {
        var shape=new IndustrialStructure.Shape(5,4,6,2);var good=shell(shape);
        for(var cell:IndustrialStructure.layout(shape)) {
            var broken=new HashMap<>(good);var p=new Point(cell.x(),cell.y(),cell.depth());
            broken.put(p,IndustrialStructure.Material.UNLOADED);
            assertFalse(IndustrialStructure.validates(MachineKind.INDUSTRIAL_REACTOR,shape,reader(broken)));
        }
        var filled=new HashMap<>(good);filled.put(new Point(1,1,2),IndustrialStructure.Material.CASING);
        assertFalse(IndustrialStructure.validates(MachineKind.INDUSTRIAL_REACTOR,shape,reader(filled)));
        var edge=new HashMap<>(good);edge.put(new Point(-2,-1,0),IndustrialStructure.Material.PORT);
        assertFalse(IndustrialStructure.validates(MachineKind.INDUSTRIAL_REACTOR,shape,reader(edge)));
        var noPorts=new HashMap<>(good);noPorts.replaceAll((p,m)->m==IndustrialStructure.Material.PORT?IndustrialStructure.Material.GLASS:m);
        assertFalse(IndustrialStructure.validates(MachineKind.INDUSTRIAL_REACTOR,shape,reader(noPorts)));
        assertTrue(IndustrialStructure.detect(MachineKind.INDUSTRIAL_REACTOR,reader(shell(new IndustrialStructure.Shape(3,3,3,1)))).isEmpty());
        assertTrue(IndustrialStructure.detect(MachineKind.INDUSTRIAL_PREPARATOR,(x,y,z)->IndustrialStructure.Material.AIR).isEmpty());
        assertThrows(IllegalArgumentException.class,()->new IndustrialStructure.Shape(10,3,3,1));
        assertThrows(IllegalArgumentException.class,()->new IndustrialStructure.Shape(5,3,3,0));
    }
    @Test void exactSquareRootWorkRateGrowsWithoutChangingBatchQuantities() {
        assertEquals(4,new IndustrialStructure.Shape(3,3,3,1).baseWorkRate(MachineKind.INDUSTRIAL_PREPARATOR));
        assertEquals(20,new IndustrialStructure.Shape(5,5,5,2).baseWorkRate(MachineKind.INDUSTRIAL_PREPARATOR));
        assertEquals(74,new IndustrialStructure.Shape(9,9,9,4).baseWorkRate(MachineKind.INDUSTRIAL_PREPARATOR));
        assertEquals(2,new IndustrialStructure.Shape(3,4,3,1).baseWorkRate(MachineKind.INDUSTRIAL_REACTOR));
        assertEquals(26,new IndustrialStructure.Shape(9,9,9,4).baseWorkRate(MachineKind.INDUSTRIAL_REACTOR));
        for(int value=0;value<=16*343;value++) {
            int root=IndustrialStructure.integerSquareRoot(value);
            assertTrue(root*root<=value);assertTrue((root+1)*(root+1)>value);
        }
    }
    @Test void fixedShellHasActualInteriorAndAllEdgesAreFrame() {
        for (var kind : List.of(MachineKind.INDUSTRIAL_PREPARATOR, MachineKind.INDUSTRIAL_REACTOR)) {
            int height = IndustrialStructure.height(kind);
            var cells = IndustrialStructure.layout(kind);
            assertEquals(9 * height, cells.size());
            assertEquals(height - 2, cells.stream().filter(c -> c.role() == IndustrialStructure.Role.INTERIOR).count());
            assertEquals(1, cells.stream().filter(c -> c.role() == IndustrialStructure.Role.CONTROLLER).count());
            assertEquals(height == 3 ? 20 : 24, cells.stream().filter(c -> c.role() == IndustrialStructure.Role.FRAME).count());
            assertEquals(height == 3 ? 5 : 9, cells.stream().filter(c -> c.role() == IndustrialStructure.Role.WALL).count());
            for (var c : cells) if (c.role() == IndustrialStructure.Role.INTERIOR) {
                assertEquals(0, c.x()); assertEquals(1, c.depth()); assertTrue(c.y() >= 0 && c.y() <= height - 3);
            }
        }
    }
    @Test void allPortFacesPointOutsideTheFixedVolumeInEveryControllerOrientation() {
        var origin = new BlockPos(105, 101, 3);
        for (var kind : List.of(MachineKind.INDUSTRIAL_PREPARATOR, MachineKind.INDUSTRIAL_REACTOR)) {
            for (var facing : Direction.Plane.HORIZONTAL) {
                var volume = new HashSet<BlockPos>();
                for (var c : IndustrialStructure.layout(kind)) volume.add(IndustrialStructure.worldPosition(origin, facing, c));
                assertEquals(9 * IndustrialStructure.height(kind), volume.size());
                for (var c : IndustrialStructure.layout(kind)) if (c.role() == IndustrialStructure.Role.WALL) {
                    var world = IndustrialStructure.worldPosition(origin, facing, c);
                    var outward = IndustrialStructure.worldDirection(facing, c.outward());
                    assertFalse(volume.contains(world.relative(outward)));
                    assertTrue(volume.contains(world.relative(outward.getOpposite())));
                }
            }
        }
    }
    @Test void northControllerIsAboveFloorAndHasThePromisedThreeBlockDeepFootprint() {
        var origin = new BlockPos(105, 101, 3);
        var positions = IndustrialStructure.layout(MachineKind.INDUSTRIAL_PREPARATOR).stream()
                .map(cell -> IndustrialStructure.worldPosition(origin, Direction.NORTH, cell)).toList();
        assertTrue(positions.contains(new BlockPos(104, 100, 3)));
        assertTrue(positions.contains(new BlockPos(106, 102, 5)));
        assertFalse(positions.contains(new BlockPos(105, 101, 2)));
        assertThrows(IllegalArgumentException.class, () -> IndustrialStructure.height(MachineKind.REACTOR));
        assertThrows(IllegalArgumentException.class, () -> IndustrialStructure.worldPosition(origin, Direction.UP,
                IndustrialStructure.layout(MachineKind.INDUSTRIAL_PREPARATOR).getFirst()));
    }
    @Test void portsCannotReplaceTheControllerEdgesCornersOrInterior() {
        for (var kind : List.of(MachineKind.INDUSTRIAL_PREPARATOR, MachineKind.INDUSTRIAL_REACTOR)) {
            for (var c : IndustrialStructure.layout(kind)) if (c.role() == IndustrialStructure.Role.WALL) {
                int boundaries = (c.x() != 0 ? 1 : 0) + (c.depth() != 1 ? 1 : 0)
                        + (c.y() == -1 || c.y() == IndustrialStructure.height(kind) - 2 ? 1 : 0);
                assertEquals(1, boundaries);
                assertFalse(c.x() == 0 && c.y() == 0 && c.depth() == 0);
                assertNotNull(c.outward());
            }
        }
    }
}
