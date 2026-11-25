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
            case CONTROLLER->CONTROLLER; case INTERIOR->AIR; case FRAME->CASING;
            case WALL->cell.outward().getAxis().isVertical()?CASING:ports++<2?PORT:GLASS;
        });
        return blocks;
    }
    private static List<IndustrialStructure.Material> parts(MachineKind kind) {
        return kind==MachineKind.INDUSTRIAL_PREPARATOR?List.of(MIXING_BASIN,SEPARATION_UNIT,GRINDING_UNIT):List.of(FIELD_COIL,REACTION_CHAMBER);
    }
    private static void assembly(Map<Point,IndustrialStructure.Material> blocks,MachineKind kind,IndustrialStructure.Shape shape,int x,int z) {
        var parts=parts(kind);
        blocks.put(new Point(x,0,z),parts.getFirst());
        blocks.put(new Point(x,shape.height()-3,z),parts.getLast());
        if (kind==MachineKind.INDUSTRIAL_PREPARATOR) blocks.put(new Point(x,0,z+1),SEPARATION_UNIT);
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
                    for(int z=1;z<shape.depth()-1;z++) {
                        blocks.put(new Point(x,0,z),parts(kind).getFirst());
                        blocks.put(new Point(x,shape.height()-3,z),parts(kind).getLast());
                    }
                if (kind==MachineKind.INDUSTRIAL_PREPARATOR) blocks.put(new Point(0,0,1),SEPARATION_UNIT);
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
    @Test void maximumPortsAndBothFullMountingSurfacesRequireOnlyOneBoundedVolumeRead() {
        var shape=new IndustrialStructure.Shape(9,9,9,4);var blocks=shell(shape);int ports=0;
        for(var cell:IndustrialStructure.layout(shape))if(cell.role()==IndustrialStructure.Role.WALL){blocks.put(new Point(cell.x(),cell.y(),cell.depth()),PORT);ports++;}
        for(int x=-3;x<=3;x++)for(int z=1;z<=7;z++) {
            blocks.put(new Point(x,0,z),MIXING_BASIN);
            blocks.put(new Point(x,shape.height()-3,z),GRINDING_UNIT);
        }
        blocks.put(new Point(0,0,1),SEPARATION_UNIT);
        assertEquals(293,ports);var reads=new AtomicInteger();
        IndustrialStructure.Materials counted=(x,y,z)->{reads.incrementAndGet();return reader(blocks).at(x,y,z);};
        var detected=IndustrialStructure.detect(MachineKind.INDUSTRIAL_PREPARATOR,counted).orElseThrow();
        var result=IndustrialStructure.inspectLayout(MachineKind.INDUSTRIAL_PREPARATOR,detected,counted);
        assertTrue(result.formed());assertEquals(49,result.moduleCount());assertEquals(98,result.modules().size());
        assertTrue(reads.get()<=729+33,"No dimension guessing or full scan per port/module");
        assertEquals(4,result.performance().workRate(MachineKind.INDUSTRIAL_PREPARATOR));
        assertEquals(48,result.performance().mixing());assertEquals(49,result.performance().grinding());
        assertEquals(1,result.performance().precision());
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
    @Test void missingForeignAndUnsupportedModulesAreRejected() {
        for(var kind:List.of(MachineKind.INDUSTRIAL_PREPARATOR,MachineKind.INDUSTRIAL_REACTOR)) {
            var shape=new IndustrialStructure.Shape(5,9,5,2);var good=shell(shape);assembly(good,kind,shape,0,1);
            for (var entry : good.entrySet()) if (entry.getValue().module()) {
                var missing=new HashMap<>(good);missing.put(entry.getKey(),AIR);
                assertEquals("structure_module_incomplete",inspect(kind,shape,missing).diagnostic());
            }
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
    @Test void biggerEmptySpaceNeverBoostsRate() {
        for(var kind:List.of(MachineKind.INDUSTRIAL_PREPARATOR,MachineKind.INDUSTRIAL_REACTOR)) {
            int depth=kind==MachineKind.INDUSTRIAL_PREPARATOR?4:3;
            for(var shape:List.of(new IndustrialStructure.Shape(3,IndustrialStructure.height(kind),depth,1),new IndustrialStructure.Shape(9,9,9,4))) {
                var blocks=shell(shape);assembly(blocks,kind,shape,0,1);var result=inspect(kind,shape,blocks);
                assertTrue(result.formed());assertEquals(1,result.moduleCount());
                assertEquals(kind==MachineKind.INDUSTRIAL_PREPARATOR?4:2,result.performance().workRate(kind));
            }
        }
    }
    @Test void exactLayoutIdentityDetectsRelocationDespiteUnchangedCountAndRate() {
        var shape=new IndustrialStructure.Shape(5,5,5,2);var first=shell(shape);var moved=shell(shape);
        assembly(first,MachineKind.INDUSTRIAL_PREPARATOR,shape,0,1);assembly(moved,MachineKind.INDUSTRIAL_PREPARATOR,shape,1,2);
        var a=inspect(MachineKind.INDUSTRIAL_PREPARATOR,shape,first);var b=inspect(MachineKind.INDUSTRIAL_PREPARATOR,shape,moved);
        assertEquals(a.moduleCount(),b.moduleCount());
        assertNotEquals(IndustrialStructure.moduleFingerprint(a.modules()),IndustrialStructure.moduleFingerprint(b.modules()));
        var reverse=new ArrayList<>(a.modules());Collections.reverse(reverse);
        assertEquals(IndustrialStructure.moduleFingerprint(a.modules()),IndustrialStructure.moduleFingerprint(reverse));
        assertThrows(UnsupportedOperationException.class,()->a.modules().clear());
    }
    @Test void openPreparatorsKeepTheReactionVolumeClearAndCountIndependentParts() {
        var kind = MachineKind.INDUSTRIAL_PREPARATOR;
        var shape = new IndustrialStructure.Shape(7, 7, 5, 3);
        var blocks = shell(shape);
        blocks.put(new Point(-1, 0, 2), MIXING_BASIN);
        blocks.put(new Point(0, 0, 2), SEPARATION_UNIT);
        blocks.put(new Point(1, 0, 2), SEPARATION_UNIT);
        blocks.put(new Point(0, 4, 2), GRINDING_UNIT);
        var formed = inspect(kind, shape, blocks);
        assertTrue(formed.formed()); assertEquals(1, formed.moduleCount());
        assertEquals(4, formed.performance().workRate(kind));
        assertEquals(0, formed.performance().energySavingPercent(kind));
        assertEquals(AIR, reader(blocks).at(0, 2, 2));
        blocks.put(new Point(1, 4, 2), GRINDING_UNIT);
        assertEquals(4, inspect(kind, shape, blocks).performance().workRate(kind));
        assertEquals(2,inspect(kind,shape,blocks).performance().grinding());
        blocks.put(new Point(2, 0, 2), MIXING_BASIN);
        assertEquals(4, inspect(kind, shape, blocks).performance().workRate(kind));
        assertEquals(2,inspect(kind,shape,blocks).performance().mixing());
        blocks.put(new Point(0, 4, 2), AIR);
        blocks.put(new Point(0, 3, 2), GRINDING_UNIT);
        assertEquals("structure_module_position", inspect(kind, shape, blocks).diagnostic());
    }
    @Test void openReactorsUseCrownCoresAndFloorCoilsWithoutUncountedBonusSpace() {
        var kind = MachineKind.INDUSTRIAL_REACTOR;
        var shape = new IndustrialStructure.Shape(5, 6, 5, 2);
        var blocks = shell(shape);
        blocks.put(new Point(-1, 0, 2), FIELD_COIL);
        blocks.put(new Point(1, 0, 2), FIELD_COIL);
        blocks.put(new Point(0, 3, 2), REACTION_CHAMBER);
        var formed = inspect(kind, shape, blocks);
        assertTrue(formed.formed()); assertEquals(1, formed.moduleCount());
        assertEquals(2, formed.performance().workRate(kind));
        assertEquals(0, formed.performance().energySavingPercent(kind));
        blocks.put(new Point(1, 3, 2), REACTION_CHAMBER);
        assertEquals(2, inspect(kind, shape, blocks).performance().workRate(kind));
        assertEquals(32_000,inspect(kind,shape,blocks).performance().processRoom());
    }
    @Test void everyModuleFunctionAcceptsEitherSurfaceButNeverAnUnsupportedMiddlePosition() {
        var shape = new IndustrialStructure.Shape(5, 6, 5, 2);
        int crown = shape.height() - 3;
        for (var kind : List.of(MachineKind.INDUSTRIAL_PREPARATOR, MachineKind.INDUSTRIAL_REACTOR)) {
            var required = parts(kind);
            // Every combination of floor and ceiling for the minimum complete set, not just one preferred order.
            for (int mounts = 0; mounts < (1 << required.size()); mounts++) {
                var blocks = shell(shape);
                for (int index = 0; index < required.size(); index++)
                    blocks.put(new Point(index - 1, (mounts & (1 << index)) == 0 ? 0 : crown, 2), required.get(index));
                var formed = inspect(kind, shape, blocks);
                assertTrue(formed.formed(), kind + " mount combination " + mounts);
                assertEquals(kind == MachineKind.INDUSTRIAL_PREPARATOR ? 4 : 2, formed.performance().workRate(kind));
                for (int moved = 0; moved < required.size(); moved++) {
                    var unsupported = new HashMap<>(blocks);
                    unsupported.put(new Point(moved - 1, (mounts & (1 << moved)) == 0 ? 0 : crown, 2), AIR);
                    unsupported.put(new Point(moved - 1, 1, 2), required.get(moved));
                    assertFalse(inspect(kind, shape, unsupported).formed(), required.get(moved) + " cannot float in the middle");
                }
            }
        }
    }
    @Test void bothFullSurfacesCanExceedFortyNineOfOnePartWithoutLosingCountsOrBounds() {
        var shape = new IndustrialStructure.Shape(9, 9, 9, 4);
        for (var kind : List.of(MachineKind.INDUSTRIAL_PREPARATOR, MachineKind.INDUSTRIAL_REACTOR)) {
            var blocks = shell(shape);
            var core = kind == MachineKind.INDUSTRIAL_PREPARATOR ? GRINDING_UNIT : REACTION_CHAMBER;
            for (int y : new int[]{0, shape.height() - 3})
                for (int x = -3; x <= 3; x++) for (int z = 1; z <= 7; z++) blocks.put(new Point(x, y, z), core);
            blocks.put(new Point(-3, 0, 1), kind == MachineKind.INDUSTRIAL_PREPARATOR ? SEPARATION_UNIT : FIELD_COIL);
            if (kind == MachineKind.INDUSTRIAL_PREPARATOR) blocks.put(new Point(-2, 0, 1), MIXING_BASIN);
            var formed = inspect(kind, shape, blocks);
            assertTrue(formed.formed()); assertEquals(98, formed.modules().size());
            assertEquals(kind == MachineKind.INDUSTRIAL_PREPARATOR ? 96 : 97, formed.moduleCount());
            assertEquals(kind==MachineKind.INDUSTRIAL_PREPARATOR?4:2,formed.performance().workRate(kind));
            assertEquals(kind==MachineKind.INDUSTRIAL_PREPARATOR?20:10,5*formed.performance().workRate(kind));
            assertEquals(0, formed.performance().energySavingPercent(kind));
        }
    }
    @Test void minimumHeightReactorsAllowVerticallyAlignedMountsButTallerReactorsRejectUnsupportedColumns() {
        var shape = new IndustrialStructure.Shape(5, 4, 5, 2);
        var kind = MachineKind.INDUSTRIAL_REACTOR;
        var aligned = shell(shape);
        assembly(aligned,kind,shape,0,2);
        assertTrue(inspect(kind,shape,aligned).formed());
        var taller = new IndustrialStructure.Shape(5, 5, 5, 2);
        var unsupported = shell(taller);
        unsupported.put(new Point(0,0,2),FIELD_COIL);
        unsupported.put(new Point(0,1,2),REACTION_CHAMBER);
        assertEquals("structure_module_position",inspect(kind,taller,unsupported).diagnostic());
    }
    @Test void aThreeByThreePreparationFootprintCannotHoldThreeMountedModules() {
        var shape = new IndustrialStructure.Shape(3, 5, 3, 1);
        var blocks = shell(shape);
        blocks.put(new Point(0,0,1),MIXING_BASIN);
        blocks.put(new Point(0,2,1),GRINDING_UNIT);
        assertEquals("structure_module_incomplete",inspect(MachineKind.INDUSTRIAL_PREPARATOR,shape,blocks).diagnostic());
        blocks.put(new Point(0,1,1),SEPARATION_UNIT);
        assertEquals("structure_module_position",inspect(MachineKind.INDUSTRIAL_PREPARATOR,shape,blocks).diagnostic());
    }
    @Test void loadednessFramesPortsAndBoundsRemainMandatoryWithValidModules() {
        var kind=MachineKind.INDUSTRIAL_REACTOR;var shape=new IndustrialStructure.Shape(5,4,6,2);var good=shell(shape);assembly(good,kind,shape,0,1);
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
    @Test void reactorRequiresCasingOnBothHorizontalFaces() {
        var kind=MachineKind.INDUSTRIAL_REACTOR;var shape=new IndustrialStructure.Shape(3,4,3,1);
        var good=shell(shape);assembly(good,kind,shape,0,1);
        assertTrue(inspect(kind,shape,good).formed());
        for(int y:new int[]{-1,shape.height()-2})for(var material:List.of(GLASS,PORT)) {
            var broken=new HashMap<>(good);broken.put(new Point(0,y,1),material);
            assertEquals("structure_endcaps",inspect(kind,shape,broken).diagnostic());
        }
    }
    @Test void narrowShellsClassifyOnlyFaceCentersAsWallPanels() {
        for(var kind:List.of(MachineKind.INDUSTRIAL_PREPARATOR,MachineKind.INDUSTRIAL_REACTOR)) {
            int height=IndustrialStructure.height(kind);var cells=IndustrialStructure.layout(new IndustrialStructure.Shape(3,height,3,1));
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
    @Test void aForeignShellNeverTakesAClaimedPortAndTheSoleClaimantKeepsIt() {
        var shape=new IndustrialStructure.Shape(5,5,5,2);var facing=Direction.NORTH;
        var a=new BlockPos(0,64,0);var b=new BlockPos(-4,64,0);
        var shared=IndustrialStructure.worldPosition(a,facing,new IndustrialStructure.Cell(-2,1,2,IndustrialStructure.Role.WALL,Direction.WEST));
        var claimA=new IndustrialStructure.ProtectionClaim(shape,facing);
        assertTrue(claimA.contains(a,shared));assertFalse(new IndustrialStructure.ProtectionClaim(shape,facing).contains(b,new BlockPos(5,64,2)));
        UUID ownerA=UUID.randomUUID(),ownerB=UUID.randomUUID();
        var incumbent=new IndustrialStructure.Claimant(a,ownerA,Set.of(shared),claimA);
        var newcomer=new IndustrialStructure.Claimant(b,ownerB,Set.of(shared),null);
        assertTrue(IndustrialStructure.keepsPorts(incumbent,List.of(newcomer)));
        assertFalse(IndustrialStructure.keepsPorts(newcomer,List.of(incumbent)));
        var repairing=new IndustrialStructure.Claimant(a,ownerA,Set.of(),claimA);
        assertFalse(IndustrialStructure.keepsPorts(newcomer,List.of(repairing)));
        var unclaimed=new IndustrialStructure.Claimant(a,ownerA,Set.of(shared),null);
        assertFalse(IndustrialStructure.keepsPorts(unclaimed,List.of(newcomer)));
        assertFalse(IndustrialStructure.keepsPorts(newcomer,List.of(unclaimed)));
    }
    @Test void aForeignShellNeverFormsOverAClaimedChamberSoItCannotLockItsParts() {
        var shape=new IndustrialStructure.Shape(5,5,5,2);var facing=Direction.NORTH;
        var a=new BlockPos(0,64,0);var sharedWall=new BlockPos(-4,64,0);var apart=new BlockPos(-5,64,0);
        var claimA=new IndustrialStructure.ProtectionClaim(shape,facing);var shell=new IndustrialStructure.ProtectionClaim(shape,facing);
        assertTrue(claimA.overlaps(a,shell,sharedWall));assertFalse(claimA.overlaps(a,shell,apart));
        UUID ownerA=UUID.randomUUID(),ownerB=UUID.randomUUID();
        var incumbent=new IndustrialStructure.Claimant(a,ownerA,Set.of(),claimA);
        var newcomer=new IndustrialStructure.Claimant(sharedWall,ownerB,Set.of(),null);
        assertFalse(IndustrialStructure.clearOfForeignClaims(newcomer,shell,List.of(incumbent)));
        assertTrue(IndustrialStructure.clearOfForeignClaims(new IndustrialStructure.Claimant(apart,ownerB,Set.of(),null),shell,List.of(incumbent)));
        assertTrue(IndustrialStructure.clearOfForeignClaims(incumbent,claimA,List.of(newcomer)));
        var sameOwner=new IndustrialStructure.Claimant(sharedWall,ownerA,Set.of(),null);
        assertTrue(IndustrialStructure.clearOfForeignClaims(sameOwner,shell,List.of(incumbent)));
    }
    @Test void shellsOfOneOwnerThatShareAPortBothStopWhateverTheirClaims() {
        var shape=new IndustrialStructure.Shape(5,5,5,2);var facing=Direction.NORTH;
        var a=new BlockPos(0,64,0);var b=new BlockPos(-4,64,0);var port=new BlockPos(-2,65,2);var owner=UUID.randomUUID();
        var first=new IndustrialStructure.Claimant(a,owner,Set.of(port),new IndustrialStructure.ProtectionClaim(shape,facing));
        var second=new IndustrialStructure.Claimant(b,owner,Set.of(port),null);
        assertFalse(IndustrialStructure.keepsPorts(first,List.of(second)));
        assertFalse(IndustrialStructure.keepsPorts(second,List.of(first)));
        var separate=new IndustrialStructure.Claimant(b,owner,Set.of(new BlockPos(-6,65,2)),null);
        assertTrue(IndustrialStructure.keepsPorts(first,List.of(separate)));
    }
    @Test void energyMatrixAllows3DMixedTierCellsAndProvidersAndTopBottomPorts() {
        // 3x3x3 has only 1 interior block -> rejected because it cannot hold both a Cell and a Provider.
        var tiny = new IndustrialStructure.Shape(3, 3, 3, 1);
        var tinyBlocks = shell(tiny);
        tinyBlocks.put(new Point(0, 0, 1), ENERGY_MATRIX_CELL);
        assertFalse(inspect(MachineKind.ENERGY_MATRIX, tiny, tinyBlocks).formed());

        // 4x3x3 has 2 interior blocks -> valid minimal Energy Matrix!
        var minimal = new IndustrialStructure.Shape(4, 3, 3, 1);
        var minBlocks = shell(minimal);
        minBlocks.put(new Point(0, 0, 1), ENERGY_MATRIX_CELL);
        minBlocks.put(new Point(1, 0, 1), ENERGY_MATRIX_PROVIDER);
        // Place a port on top roof panel and bottom floor panel — allowed on Energy Matrix!
        minBlocks.put(new Point(0, 1, 1), PORT);
        minBlocks.put(new Point(1, -1, 1), PORT);
        var minResult = inspect(MachineKind.ENERGY_MATRIX, minimal, minBlocks);
        assertTrue(minResult.formed(), minResult.diagnostic());
        assertEquals(64_000_000L, minResult.performance().matrixCapacity());
        assertEquals(262_144L, minResult.performance().matrixTransferRate());

        // 5x5x5 with mixed tiers stacked across floor, middle (y=1), and upper (y=2) interior layers!
        var big = new IndustrialStructure.Shape(5, 5, 5, 2);
        var bigBlocks = shell(big);
        bigBlocks.put(new Point(-1, 0, 2), ENERGY_MATRIX_CELL);
        bigBlocks.put(new Point(-1, 1, 2), REINFORCED_ENERGY_MATRIX_CELL);
        bigBlocks.put(new Point(-1, 2, 2), RESONANT_ENERGY_MATRIX_CELL);
        bigBlocks.put(new Point(0, 1, 2), PRIMAL_ENERGY_MATRIX_CELL);
        bigBlocks.put(new Point(1, 0, 2), ENERGY_MATRIX_PROVIDER);
        bigBlocks.put(new Point(1, 1, 2), REINFORCED_ENERGY_MATRIX_PROVIDER);
        bigBlocks.put(new Point(1, 2, 2), RESONANT_ENERGY_MATRIX_PROVIDER);
        bigBlocks.put(new Point(0, 2, 2), PRIMAL_ENERGY_MATRIX_PROVIDER);
        var bigResult = inspect(MachineKind.ENERGY_MATRIX, big, bigBlocks);
        assertTrue(bigResult.formed(), bigResult.diagnostic());
        assertEquals(4, bigResult.performance().matrixCells());
        assertEquals(4, bigResult.performance().matrixProviders());
        assertEquals(37_440_000_000L, bigResult.performance().matrixCapacity());
        assertEquals(22_282_240L, bigResult.performance().matrixTransferRate());
        assertEquals(8, bigResult.modules().size());

        // Rejects foreign Reactor modules inside Energy Matrix cavity
        var foreign = new HashMap<>(bigBlocks);
        foreign.put(new Point(0, 0, 1), REACTOR_FUEL_CORE);
        assertEquals("structure_module_type", inspect(MachineKind.ENERGY_MATRIX, big, foreign).diagnostic());
    }
    @Test void energyReactorRequiresFuelCoresAndInductionCoilsIn5x5x5Shell() {
        var shape = new IndustrialStructure.Shape(5, 5, 5, 2);
        var blocks = shell(shape);
        blocks.put(new Point(-1, 0, 2), REACTOR_FUEL_CORE);
        blocks.put(new Point(1, 0, 2), REACTOR_FUEL_CORE);
        blocks.put(new Point(0, 2, 2), REACTOR_INDUCTION_COIL);
        blocks.put(new Point(1, 2, 2), REACTOR_INDUCTION_COIL);
        var formed = inspect(MachineKind.ENERGY_REACTOR, shape, blocks);
        assertTrue(formed.formed(), formed.diagnostic());
        assertEquals(2, formed.performance().fuelCores());
        assertEquals(2, formed.performance().inductionCoils());
        assertEquals(1_024_000_000L, formed.performance().reactorCapacity());
        assertEquals(2_097_152L, formed.performance().reactorMaxOutputRate());

        // Rejects floating middle module, foreign Matrix Cell, glass over/under a mounted module, and top/bottom endcap port
        var floating = new HashMap<>(blocks);
        floating.put(new Point(0, 1, 2), REACTOR_FUEL_CORE);
        assertEquals("structure_module_position", inspect(MachineKind.ENERGY_REACTOR, shape, floating).diagnostic());
        var foreign = new HashMap<>(blocks);
        foreign.put(new Point(0, 0, 2), ENERGY_MATRIX_CELL);
        assertEquals("structure_module_type", inspect(MachineKind.ENERGY_REACTOR, shape, foreign).diagnostic());
        var roofPort = new HashMap<>(blocks);
        roofPort.put(new Point(0, 3, 2), PORT);
        assertEquals("structure_endcaps", inspect(MachineKind.ENERGY_REACTOR, shape, roofPort).diagnostic());

        // Mixed roof/floor Glass + Casing: Glass is allowed over/under open interior spots, rejected directly over/under modules!
        var mixedRoofFloor = new HashMap<>(blocks);
        mixedRoofFloor.put(new Point(0, -1, 2), GLASS);  // (0,0,2) is AIR -> allowed!
        mixedRoofFloor.put(new Point(-1, 3, 2), GLASS);  // (-1,2,2) is AIR -> allowed!
        mixedRoofFloor.put(new Point(0, 3, 1), GLASS);   // (0,2,1) is AIR -> allowed!
        assertTrue(inspect(MachineKind.ENERGY_REACTOR, shape, mixedRoofFloor).formed(),
                "Open roof and floor spots without adjacent modules must accept Glass");

        var glassUnderCore = new HashMap<>(mixedRoofFloor);
        glassUnderCore.put(new Point(-1, -1, 2), GLASS); // (-1,0,2) holds REACTOR_FUEL_CORE -> must be Casing!
        assertEquals("structure_endcaps", inspect(MachineKind.ENERGY_REACTOR, shape, glassUnderCore).diagnostic());

        var glassOverCoil = new HashMap<>(mixedRoofFloor);
        glassOverCoil.put(new Point(0, 3, 2), GLASS);    // (0,2,2) holds REACTOR_INDUCTION_COIL -> must be Casing!
        assertEquals("structure_endcaps", inspect(MachineKind.ENERGY_REACTOR, shape, glassOverCoil).diagnostic());

        // Energy Matrix: Glass allowed on open floor/roof (and under/over middle-only cells), rejected directly under/over floor/ceiling cells or providers!
        var mShape = new IndustrialStructure.Shape(5, 5, 5, 2);
        var mBlocks = shell(mShape);
        mBlocks.put(new Point(-1, 0, 2), ENERGY_MATRIX_CELL);     // touches floor (-1,-1,2)
        mBlocks.put(new Point(0, 1, 2), PRIMAL_ENERGY_MATRIX_CELL); // floating middle layer -> touches neither floor nor roof
        mBlocks.put(new Point(1, 2, 2), ENERGY_MATRIX_PROVIDER);  // touches roof (1,3,2)
        mBlocks.put(new Point(0, -1, 2), GLASS);                  // below middle-only cell -> AIR at y=0 -> allowed!
        mBlocks.put(new Point(0, 3, 2), GLASS);                   // above middle-only cell -> AIR at y=2 -> allowed!
        mBlocks.put(new Point(-1, 3, 2), GLASS);                  // AIR at y=2 -> allowed!
        mBlocks.put(new Point(1, -1, 2), GLASS);                  // AIR at y=0 -> allowed!
        assertTrue(inspect(MachineKind.ENERGY_MATRIX, mShape, mBlocks).formed(),
                "Energy Matrix must allow Glass on floor/roof wherever no Cell/Provider directly touches it");

        var glassUnderMatrixCell = new HashMap<>(mBlocks);
        glassUnderMatrixCell.put(new Point(-1, -1, 2), GLASS);
        assertEquals("structure_endcaps", inspect(MachineKind.ENERGY_MATRIX, mShape, glassUnderMatrixCell).diagnostic());

        var glassOverMatrixProvider = new HashMap<>(mBlocks);
        glassOverMatrixProvider.put(new Point(1, 3, 2), GLASS);
        assertEquals("structure_endcaps", inspect(MachineKind.ENERGY_MATRIX, mShape, glassOverMatrixProvider).diagnostic());
    }
}
