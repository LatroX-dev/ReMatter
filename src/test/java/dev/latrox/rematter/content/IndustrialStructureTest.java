package dev.latrox.rematter.content;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static dev.latrox.rematter.content.IndustrialStructure.Material.*;
import static org.junit.jupiter.api.Assertions.*;

class IndustrialStructureTest {
    private record Point(int x,int y,int depth) {}
    private static Map<Point,IndustrialStructure.Material> shell(IndustrialStructure.Shape shape) {
        var blocks=new HashMap<Point,IndustrialStructure.Material>();int ports=0;
        for(var cell:IndustrialStructure.layout(shape)) blocks.put(new Point(cell.x(),cell.y(),cell.depth()),switch(cell.role()) {
            case CONTROLLER->CONTROLLER; case INTERIOR->AIR; case FRAME->CASING; case WALL->ports++<2?PORT:GLASS;
        });
        return blocks;
    }
    private static List<IndustrialStructure.Material> parts(MachineKind kind) {
        return kind==MachineKind.INDUSTRIAL_PREPARATOR?List.of(MIXING_BASIN,SEPARATION_UNIT,GRINDING_UNIT):List.of(FIELD_COIL,REACTION_CHAMBER);
    }
    private static void column(Map<Point,IndustrialStructure.Material> blocks,MachineKind kind,int x,int z) {
        var parts=parts(kind);for(int y=0;y<parts.size();y++)blocks.put(new Point(x,y,z),parts.get(y));
    }
    private static IndustrialStructure.Materials reader(Map<Point,IndustrialStructure.Material> blocks) {
        return (x,y,z)->blocks.getOrDefault(new Point(x,y,z),AIR);
    }
    private static IndustrialStructure.Inspection inspect(MachineKind kind,IndustrialStructure.Shape shape,Map<Point,IndustrialStructure.Material> blocks) {
        return IndustrialStructure.inspectLayout(kind,shape,reader(blocks));
    }
    @Test void raysTraverseRealModulesAtTheSeedAndEveryRayWithoutGuessingDimensions() {
        for(var kind:List.of(MachineKind.INDUSTRIAL_PREPARATOR,MachineKind.INDUSTRIAL_REACTOR)) {
            for(var shape:List.of(new IndustrialStructure.Shape(5,5,4,1),new IndustrialStructure.Shape(4,6,6,2),new IndustrialStructure.Shape(9,9,9,7))) {
                var blocks=shell(shape);
                for(int x=1-shape.controllerColumn();x<shape.width()-shape.controllerColumn()-1;x++)
                    for(int z=1;z<shape.depth()-1;z++)column(blocks,kind,x,z);
                assertEquals(shape,IndustrialStructure.detect(kind,reader(blocks)).orElseThrow());
                assertEquals((shape.width()-2)*(shape.depth()-2),inspect(kind,shape,blocks).moduleCount());
                for(var facing:Direction.Plane.HORIZONTAL) {
                    var world=new HashSet<BlockPos>();
                    for(var cell:IndustrialStructure.layout(shape))world.add(IndustrialStructure.worldPosition(BlockPos.ZERO,facing,cell));
                    assertEquals(shape.width()*shape.height()*shape.depth(),world.size());
                    for(var cell:IndustrialStructure.layout(shape))if(cell.role()==IndustrialStructure.Role.WALL)
                        assertFalse(world.contains(IndustrialStructure.worldPosition(BlockPos.ZERO,facing,cell)
                                .relative(IndustrialStructure.worldDirection(facing,cell.outward()))));
                }
            }
        }
    }
    @Test void maximumPortsAndFortyNineColumnsStillRequireOnlyOneBoundedVolumeRead() {
        var shape=new IndustrialStructure.Shape(9,9,9,4);var blocks=shell(shape);int ports=0;
        for(var cell:IndustrialStructure.layout(shape))if(cell.role()==IndustrialStructure.Role.WALL){blocks.put(new Point(cell.x(),cell.y(),cell.depth()),PORT);ports++;}
        for(int x=-3;x<=3;x++)for(int z=1;z<=7;z++)column(blocks,MachineKind.INDUSTRIAL_PREPARATOR,x,z);
        assertEquals(293,ports);var reads=new AtomicInteger();
        IndustrialStructure.Materials counted=(x,y,z)->{reads.incrementAndGet();return reader(blocks).at(x,y,z);};
        var detected=IndustrialStructure.detect(MachineKind.INDUSTRIAL_PREPARATOR,counted).orElseThrow();
        var result=IndustrialStructure.inspectLayout(MachineKind.INDUSTRIAL_PREPARATOR,detected,counted);
        assertTrue(result.formed());assertEquals(49,result.moduleCount());assertEquals(147,result.modules().size());
        assertTrue(reads.get()<=729+33,"No dimension guessing or full scan per port/module");
        assertEquals(980,IndustrialStructure.workRate(MachineKind.INDUSTRIAL_PREPARATOR,result.moduleCount())*5);
    }
    @Test void emptyOldShellsAndShortPreparatorsPauseInsteadOfGrantingFreeCapacity() {
        for(var kind:List.of(MachineKind.INDUSTRIAL_PREPARATOR,MachineKind.INDUSTRIAL_REACTOR)) {
            var shape=new IndustrialStructure.Shape(5,IndustrialStructure.height(kind),5,2);var blocks=shell(shape);
            var before=new HashMap<>(blocks);
            assertEquals("structure_modules_missing",inspect(kind,shape,blocks).diagnostic());
            assertEquals(before,blocks,"Inspection never rewrites an existing shell");
        }
        for(int height:List.of(3,4)) {
            var shape=new IndustrialStructure.Shape(3,height,3,1);var blocks=shell(shape);
            assertEquals(shape,IndustrialStructure.detect(MachineKind.INDUSTRIAL_PREPARATOR,reader(blocks)).orElseThrow());
            assertEquals("structure_too_short",inspect(MachineKind.INDUSTRIAL_PREPARATOR,shape,blocks).diagnostic());
        }
    }
    @Test void missingReversedForeignFloatingAndSecondStacksAreRejected() {
        for(var kind:List.of(MachineKind.INDUSTRIAL_PREPARATOR,MachineKind.INDUSTRIAL_REACTOR)) {
            var shape=new IndustrialStructure.Shape(5,9,5,2);var good=shell(shape);column(good,kind,0,1);
            for(int y=0;y<parts(kind).size();y++) {
                var missing=new HashMap<>(good);missing.put(new Point(0,y,1),AIR);
                assertEquals("structure_module_incomplete",inspect(kind,shape,missing).diagnostic());
            }
            var reversed=new HashMap<>(good);reversed.put(new Point(0,0,1),parts(kind).get(1));reversed.put(new Point(0,1,1),parts(kind).getFirst());
            assertEquals("structure_module_position",inspect(kind,shape,reversed).diagnostic());
            var foreign=new HashMap<>(good);foreign.put(new Point(0,0,1),kind==MachineKind.INDUSTRIAL_PREPARATOR?FIELD_COIL:MIXING_BASIN);
            assertEquals("structure_module_type",inspect(kind,shape,foreign).diagnostic());
            var floating=shell(shape);for(int y=0;y<parts(kind).size();y++)floating.put(new Point(0,y+1,1),parts(kind).get(y));
            assertEquals("structure_module_position",inspect(kind,shape,floating).diagnostic());
            var doubled=new HashMap<>(good);for(int y=0;y<parts(kind).size();y++)doubled.put(new Point(0,y+3,1),parts(kind).get(y));
            assertEquals("structure_module_position",inspect(kind,shape,doubled).diagnostic());
            var junk=new HashMap<>(good);junk.put(new Point(1,3,2),OTHER);
            assertEquals("structure_interior",inspect(kind,shape,junk).diagnostic());
        }
    }
    @Test void biggerEmptySpaceNeverBoostsRateButEveryAdditionalColumnDoes() {
        for(var kind:List.of(MachineKind.INDUSTRIAL_PREPARATOR,MachineKind.INDUSTRIAL_REACTOR)) {
            for(var shape:List.of(new IndustrialStructure.Shape(3,IndustrialStructure.height(kind),3,1),new IndustrialStructure.Shape(9,9,9,4))) {
                var blocks=shell(shape);column(blocks,kind,0,1);var result=inspect(kind,shape,blocks);
                assertTrue(result.formed());assertEquals(1,result.moduleCount());
                assertEquals(kind==MachineKind.INDUSTRIAL_PREPARATOR?4:2,IndustrialStructure.workRate(kind,result.moduleCount()));
            }
            int previous=0;
            for(int count=1;count<=49;count++){int rate=IndustrialStructure.workRate(kind,count);assertTrue(rate>previous);previous=rate;}
            assertThrows(IllegalArgumentException.class,()->IndustrialStructure.workRate(kind,50));
            assertThrows(IllegalArgumentException.class,()->IndustrialStructure.workRate(kind,-1));
        }
    }
    @Test void exactLayoutIdentityDetectsRelocationDespiteUnchangedCountAndRate() {
        var shape=new IndustrialStructure.Shape(5,5,5,2);var first=shell(shape);var moved=shell(shape);
        column(first,MachineKind.INDUSTRIAL_PREPARATOR,0,1);column(moved,MachineKind.INDUSTRIAL_PREPARATOR,1,2);
        var a=inspect(MachineKind.INDUSTRIAL_PREPARATOR,shape,first);var b=inspect(MachineKind.INDUSTRIAL_PREPARATOR,shape,moved);
        assertEquals(a.moduleCount(),b.moduleCount());
        assertNotEquals(IndustrialStructure.moduleFingerprint(a.modules()),IndustrialStructure.moduleFingerprint(b.modules()));
        var reverse=new ArrayList<>(a.modules());Collections.reverse(reverse);
        assertEquals(IndustrialStructure.moduleFingerprint(a.modules()),IndustrialStructure.moduleFingerprint(reverse));
        assertThrows(UnsupportedOperationException.class,()->a.modules().clear());
    }
    @Test void loadednessFramesPortsAndBoundsRemainMandatoryWithValidModules() {
        var kind=MachineKind.INDUSTRIAL_REACTOR;var shape=new IndustrialStructure.Shape(5,4,6,2);var good=shell(shape);column(good,kind,0,1);
        for(var cell:IndustrialStructure.layout(shape)) {
            var broken=new HashMap<>(good);broken.put(new Point(cell.x(),cell.y(),cell.depth()),UNLOADED);
            assertEquals("structure_unloaded",inspect(kind,shape,broken).diagnostic());
        }
        var edge=new HashMap<>(good);edge.put(new Point(-2,-1,0),PORT);
        assertEquals("structure_frame",inspect(kind,shape,edge).diagnostic());
        var noPorts=new HashMap<>(good);noPorts.replaceAll((p,m)->m==PORT?GLASS:m);
        assertEquals("structure_ports",inspect(kind,shape,noPorts).diagnostic());
        assertTrue(IndustrialStructure.detect(kind,(x,y,z)->AIR).isEmpty());
        var unknown=new HashMap<>(good);unknown.put(new Point(0,0,1),OTHER);
        assertTrue(IndustrialStructure.detect(kind,reader(unknown)).isEmpty(),"Unknown contents cannot be reinterpreted as an outer wall");
        assertThrows(IllegalArgumentException.class,()->new IndustrialStructure.Shape(10,5,3,1));
        assertThrows(IllegalArgumentException.class,()->new IndustrialStructure.Shape(5,5,3,0));
    }
    @Test void minimumShellsFitTheWholeColumnAndOnlyFaceCentersCanBePorts() {
        for(var kind:List.of(MachineKind.INDUSTRIAL_PREPARATOR,MachineKind.INDUSTRIAL_REACTOR)) {
            int height=IndustrialStructure.height(kind);var cells=IndustrialStructure.layout(kind);
            assertEquals(9*height,cells.size());
            assertEquals(parts(kind).size(),cells.stream().filter(c->c.role()==IndustrialStructure.Role.INTERIOR).count());
            assertEquals(4*height+8,cells.stream().filter(c->c.role()==IndustrialStructure.Role.FRAME).count());
            assertEquals(4*height-7,cells.stream().filter(c->c.role()==IndustrialStructure.Role.WALL).count());
            for(var c:cells)if(c.role()==IndustrialStructure.Role.WALL) {
                int boundaries=(c.x()!=0?1:0)+(c.depth()!=1?1:0)+(c.y()==-1||c.y()==height-2?1:0);
                assertEquals(1,boundaries);assertNotNull(c.outward());
            }
        }
    }
    @Test void repairClaimRetainsItsOriginalFacingAndBoundsAcrossNbtWithoutFormingAnEmptyShell() {
        var shape=new IndustrialStructure.Shape(5,6,4,1);var origin=new BlockPos(170,101,8);
        for(var facing:Direction.Plane.HORIZONTAL) {
            var claim=new IndustrialStructure.ProtectionClaim(shape,facing);
            var restored=IndustrialStructure.ProtectionClaim.restore(claim.save()).orElseThrow();
            assertEquals(claim,restored);
            for(var cell:IndustrialStructure.layout(shape))assertTrue(restored.contains(origin,IndustrialStructure.worldPosition(origin,facing,cell)));
            assertFalse(restored.contains(origin,origin.relative(facing)));
            assertFalse(inspect(MachineKind.INDUSTRIAL_PREPARATOR,restored.shape(),shell(shape)).formed());
        }
    }
    @Test void untrustedClaimSchemaDimensionsAndVerticalFacingCannotExpandProtection() {
        var valid=new IndustrialStructure.ProtectionClaim(new IndustrialStructure.Shape(5,5,5,2),Direction.NORTH).save();
        for(var entry:Map.of("schema",2,"width",10,"height",2,"depth",Integer.MAX_VALUE,"column",0).entrySet()) {
            var invalid=valid.copy();invalid.putInt(entry.getKey(),entry.getValue());
            assertTrue(IndustrialStructure.ProtectionClaim.restore(invalid).isEmpty());
        }
        for(var facing:List.of("up","down","invalid","")) {
            var invalid=valid.copy();invalid.putString("facing",facing);
            assertTrue(IndustrialStructure.ProtectionClaim.restore(invalid).isEmpty());
        }
    }
}
