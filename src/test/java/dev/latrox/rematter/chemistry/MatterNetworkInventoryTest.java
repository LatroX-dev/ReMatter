package dev.latrox.rematter.chemistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import static dev.latrox.rematter.chemistry.BuiltinChemistry.*;
import static org.junit.jupiter.api.Assertions.*;

class MatterNetworkInventoryTest {
    @Test void sixtyFourSixBayDrivesRemainFlatAndCommitAtTheBoundWithoutDuplicatingOwners() {
        var cells = new ArrayList<MatterInventory>();
        for (int index = 0; index < 384; index++) cells.add(cell(100, 2, COPPER, 100));
        var network = new MatterNetworkInventory(cells);
        assertEquals(384, network.members().size()); assertEquals(38_400, network.total());
        var checkpoint = network.snapshot();
        assertTrue(network.apply(Map.of(COPPER, 38_400L), Map.of(WATER, 38_400L)));
        assertEquals(38_400, network.amount(WATER)); assertEquals(0, network.amount(COPPER));
        assertTrue(cells.stream().allMatch(cell -> cell.amount(WATER) == 100));
        network.restoreInPlace(checkpoint); assertEquals(checkpoint, network.snapshot());
        cells.add(new MatterInventory(100, 2));
        assertThrows(IllegalArgumentException.class, () -> new MatterNetworkInventory(cells));
    }
    @Test void sixBayPortableCellsRoundTripSeparatelyWithUnknownSubstances() {
        var unknown = SubstanceKey.of("missing:retained", Phase.GAS);
        var cells = new ArrayList<MatterInventory>();
        for (int index = 0; index < 6; index++) {
            var member = cell(64_000, 512, COPPER, 100 + index);
            member.insert(unknown, index + 1); cells.add(member);
        }
        var network = new MatterNetworkInventory(cells);
        assertEquals(615, network.amount(COPPER));
        assertTrue(network.apply(Map.of(COPPER, 570L), Map.of(WATER, 570L)));
        var restored = cells.stream().map(member -> dev.latrox.rematter.content.MatterCellData.from(member)
                .open(member.capacity()).orElseThrow()).toList();
        var reloaded = new MatterNetworkInventory(restored);
        assertEquals(network.contents(), reloaded.contents());
        assertEquals(21, reloaded.amount(unknown)); assertEquals(384_000, reloaded.capacity());
    }
    @Test void alternativeInputOwnerReleasesTheOnlyCellLargeEnoughForOutput() {
        var x=SubstanceKey.of("test:x",Phase.SOLID);var y=SubstanceKey.of("test:y",Phase.SOLID);var z=SubstanceKey.of("test:z",Phase.SOLID);
        var a=cell(10,1,x,10);var b=cell(30,2,x,10);b.insert(y,10);
        var network=new MatterNetworkInventory(List.of(a,b));var before=network.snapshot();
        assertTrue(network.canApply(Map.of(x,10L),Map.of(z,20L)));assertEquals(before,network.snapshot());
        assertTrue(network.apply(Map.of(x,10L),Map.of(z,20L)));
        assertEquals(Map.of(x,10L),a.contents());assertEquals(Map.of(y,10L,z,20L),b.contents());
    }
    @Test void connectingReadingAndSplittingNeverRelocatesPhysicalContents() {
        var a=cell(64000,512,COPPER,101);var b=cell(256000,512,WATER,203);
        var unknown=SubstanceKey.of("missing:retained",Phase.GAS);b.insert(unknown,17);
        var beforeA=a.snapshot();var beforeB=b.snapshot();var network=new MatterNetworkInventory(List.of(a,b));
        assertEquals(320000,network.capacity());assertEquals(321,network.total());assertEquals(101,network.amount(COPPER));
        assertEquals(3,network.typeCount());
        assertEquals(Map.of(COPPER,101L,WATER,203L,unknown,17L),network.contents());
        assertEquals(101,new MatterNetworkInventory(List.of(a)).amount(COPPER));
        assertEquals(220,new MatterNetworkInventory(List.of(b)).total());
        assertEquals(beforeA,a.snapshot());assertEquals(beforeB,b.snapshot());
        assertThrows(UnsupportedOperationException.class,()->network.members().clear());
        assertEquals(network.members(),new MatterNetworkInventory(List.of(a,b)).members());
    }
    @Test void reactionConsumesExactInputsAcrossCellsAndCommitsOneDistributedOutput() {
        var a=cell(1000,2,HYDROGEN,600);a.insert(OXYGEN,200);
        var b=cell(1000,2,HYDROGEN,400);b.insert(OXYGEN,300);
        var network=new MatterNetworkInventory(List.of(a,b));var before=network.snapshot();
        var inputs=Map.of(HYDROGEN,1000L,OXYGEN,500L);var outputs=Map.of(WATER,1500L);
        assertTrue(network.canApply(inputs,outputs));assertEquals(before,network.snapshot());
        assertTrue(network.apply(inputs,outputs));assertEquals(Map.of(WATER,1500L),network.contents());
        assertEquals(1000,a.total());assertEquals(500,b.total());
        assertEquals(0,network.amount(HYDROGEN));assertFalse(network.apply(inputs,outputs));
    }
    @Test void insufficientGlobalSpaceOrInputNeverMutatesAnyMember() {
        var a=cell(1000,2,HYDROGEN,600);var b=cell(1000,2,OXYGEN,499);
        var network=new MatterNetworkInventory(List.of(a,b));var before=network.snapshot();
        assertFalse(network.apply(Map.of(HYDROGEN,600L,OXYGEN,500L),Map.of(WATER,1000L)));
        assertFalse(network.apply(Map.of(HYDROGEN,600L),Map.of(WATER,2000L)));
        assertEquals(before,network.snapshot());
    }
    @Test void everyCellTypeLimitAppliesEvenWithAbundantCombinedFreeSpace() {
        var a=cell(1000,1,COPPER,1);var b=cell(1000,1,WATER,1);
        var network=new MatterNetworkInventory(List.of(a,b));var before=network.snapshot();
        assertFalse(network.hasTypeSpace(Map.of(),Map.of(HYDROGEN,1L)));
        assertFalse(network.apply(Map.of(),Map.of(HYDROGEN,1L)));assertEquals(before,network.snapshot());
        assertTrue(network.apply(Map.of(COPPER,1L),Map.of(HYDROGEN,1000L)));
        assertEquals(Map.of(HYDROGEN,1000L),a.contents());assertEquals(Map.of(WATER,1L),b.contents());
    }
    @Test void flowReassignsOutputsInsteadOfFillingTheOnlyUsableTypeSlotGreedily() {
        var zinc=SubstanceKey.of("test:zinc",Phase.SOLID);
        var a=cell(10,2,COPPER,1);var b=new MatterInventory(100,1);
        var network=new MatterNetworkInventory(List.of(a,b));
        assertTrue(network.apply(Map.of(),Map.of(COPPER,100L,zinc,9L)));
        assertEquals(Map.of(COPPER,1L,zinc,9L),a.contents());assertEquals(Map.of(COPPER,100L),b.contents());
        assertEquals(2,a.typeCount());assertEquals(1,b.typeCount());
    }
    @Test void outputsWithExclusiveExistingTypesDoNotLoseTheirOnlyCompatibleCell() {
        var a=cell(100,1,COPPER,1);var b=new MatterInventory(100,1);
        var network=new MatterNetworkInventory(List.of(a,b));
        assertTrue(network.apply(Map.of(),Map.of(COPPER,99L,WATER,100L)));
        assertEquals(100,a.amount(COPPER));assertEquals(100,b.amount(WATER));
    }
    @Test void aSmallInputPileCanReleaseTheRequiredTypeSlot() {
        var a=cell(1000,1,COPPER,900);var b=cell(1000,1,COPPER,100);
        var network=new MatterNetworkInventory(List.of(a,b));
        assertTrue(network.apply(Map.of(COPPER,100L),Map.of(WATER,1000L)));
        assertEquals(900,a.amount(COPPER));assertEquals(Map.of(WATER,1000L),b.contents());
    }
    @Test void sameInputAndOutputKeyAndDifferentPhasesRemainExact() {
        var liquidCopper=SubstanceKey.of(COPPER.id().toString(),Phase.LIQUID);
        var a=cell(1000,1,COPPER,1000);var b=new MatterInventory(1000,1);
        var network=new MatterNetworkInventory(List.of(a,b));
        assertTrue(network.apply(Map.of(COPPER,1000L),Map.of(COPPER,700L,liquidCopper,300L)));
        assertEquals(700,network.amount(COPPER));assertEquals(300,network.amount(liquidCopper));assertEquals(1000,network.total());
        assertEquals(1,a.typeCount());assertEquals(1,b.typeCount());
    }
    @Test void totalDistinctNetworkTypesAreNotLimitedToOneCells512Types() {
        var a=new MatterInventory(1000,512);var b=new MatterInventory(1000,512);
        for(int i=0;i<1024;i++) (i<512?a:b).insert(SubstanceKey.of("test:key_"+i,Phase.SOLID),1);
        var network=new MatterNetworkInventory(List.of(a,b));
        assertEquals(1024,network.typeCount());assertEquals(1024,network.contents().size());
        assertFalse(network.apply(Map.of(),Map.of(WATER,1L)));
        assertTrue(network.apply(Map.of(SubstanceKey.of("test:key_0",Phase.SOLID),1L),Map.of(WATER,1L)));
        assertEquals(512,a.typeCount());assertEquals(512,b.typeCount());assertEquals(1024,network.typeCount());
    }
    @Test void duplicateIdentitiesEmptyNetworksAndOverflowsFailClosed() {
        var a=cell(1000,512,COPPER,1000);
        assertThrows(IllegalArgumentException.class,()->new MatterNetworkInventory(List.of(a,a)));
        assertThrows(IllegalArgumentException.class,()->new MatterNetworkInventory(List.of()));
        var huge=cell(Long.MAX_VALUE,512,COPPER,Long.MAX_VALUE);
        assertThrows(ArithmeticException.class,()->new MatterNetworkInventory(List.of(huge,a)));
        var b=new MatterInventory(1000,512);var network=new MatterNetworkInventory(List.of(a,b));
        var before=network.snapshot();
        assertFalse(network.apply(Map.of(),Map.of(COPPER,Long.MAX_VALUE,WATER,1L)));
        assertEquals(before,network.snapshot());
        assertTrue(a.setCapacity(Long.MAX_VALUE));var changed=a.snapshot();
        assertFalse(network.canApply(Map.of(),Map.of(WATER,1L)));assertFalse(network.apply(Map.of(),Map.of(WATER,1L)));
        assertThrows(ArithmeticException.class,network::total);assertEquals(changed,a.snapshot());assertEquals(0,b.total());
    }
    @Test void rollbackValidatesAllMembersFirstAndPreservesTheirIdentities() {
        var a=cell(1000,2,COPPER,100);var b=cell(1000,2,WATER,100);
        var network=new MatterNetworkInventory(List.of(a,b));var checkpoint=network.snapshot();
        assertTrue(network.apply(Map.of(COPPER,17L,WATER,19L),Map.of(HYDROGEN,23L)));
        var current=network.snapshot();
        var invalid=new MatterNetworkInventory.Snapshot(List.of(checkpoint.members().get(0),
                new MatterNetworkInventory.MemberSnapshot(b,new MatterInventory.Snapshot(1000,List.of(new SubstanceStack(WATER,600),new SubstanceStack(WATER,600))))));
        assertThrows(IllegalArgumentException.class,()->network.restoreInPlace(invalid));assertEquals(current,network.snapshot());
        var replacement=MatterInventory.restore(b.snapshot(),2);
        var wrongIdentity=new MatterNetworkInventory.Snapshot(List.of(checkpoint.members().get(0),
                new MatterNetworkInventory.MemberSnapshot(replacement,checkpoint.members().get(1).state())));
        assertThrows(IllegalArgumentException.class,()->network.restoreInPlace(wrongIdentity));assertEquals(current,network.snapshot());
        network.restoreInPlace(checkpoint);assertEquals(checkpoint,network.snapshot());
        assertSame(a,network.members().get(0));assertSame(b,network.members().get(1));
    }
    @Test void overlappingNetworkViewsCannotSpendTheSameStockConcurrently() throws Exception {
        var shared=cell(1000,512,COPPER,1000);var a=new MatterInventory(1000,512);var b=new MatterInventory(1000,512);
        var first=new MatterNetworkInventory(List.of(shared,a));var second=new MatterNetworkInventory(List.of(shared,b));
        var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var one=executor.submit(()->{start.await();return first.apply(Map.of(COPPER,1000L),Map.of(WATER,1000L));});
            var two=executor.submit(()->{start.await();return second.apply(Map.of(COPPER,1000L),Map.of(HYDROGEN,1000L));});
            start.countDown();assertNotEquals(one.get(),two.get());
        }
        assertEquals(0,shared.amount(COPPER));assertEquals(1000,shared.total()+a.total()+b.total());
    }
    @Test void aBatchCommitsOnlyTheCellsItTouchesAndLeavesTheOthersUnchanged() {
        var cells = new ArrayList<MatterInventory>();
        for (int index = 0; index < 384; index++) cells.add(cell(1000, 4, COPPER, 10));
        var network = new MatterNetworkInventory(cells);
        var before = cells.stream().map(MatterInventory::revision).toList();
        assertTrue(network.canApply(Map.of(COPPER, 10L), Map.of(WATER, 15L)));
        assertEquals(before, cells.stream().map(MatterInventory::revision).toList());
        var activity = network.applyTracked(Map.of(COPPER, 10L), Map.of(WATER, 15L));
        assertNotNull(activity);
        int changed = 0;
        for (int index = 0; index < cells.size(); index++) if (cells.get(index).revision() != before.get(index)) changed++;
        assertEquals(activity.size(), changed);
        assertTrue(changed <= 2);
        assertEquals(3830, network.amount(COPPER)); assertEquals(15, network.amount(WATER));
    }
    @Test void networkRevisionAndSharedAggregatesFollowEveryMemberChange() {
        var a = cell(1000, 4, COPPER, 10); var b = cell(1000, 4, WATER, 20);
        var network = new MatterNetworkInventory(List.of(a, b));
        long revision = network.revision();
        var contents = network.contents();
        assertSame(contents, network.contents());
        assertEquals(revision, network.revision());
        assertFalse(network.apply(Map.of(COPPER, 11L), Map.of()));
        assertEquals(revision, network.revision()); assertSame(contents, network.contents());
        b.insert(COPPER, 1);
        assertNotEquals(revision, network.revision());
        assertEquals(Map.of(COPPER, 11L, WATER, 20L), network.contents());
        assertNotSame(contents, network.contents());
    }
    @Test void aggregateShortageIsRejectedBeforeAnyPlanningCopy() {
        var a = cell(100, 4, COPPER, 90); var b = cell(100, 4, WATER, 90);
        var network = new MatterNetworkInventory(List.of(a, b));
        assertFalse(network.canApply(Map.of(COPPER, 10L), Map.of(WATER, 31L)));
        assertTrue(network.canApply(Map.of(COPPER, 10L), Map.of(WATER, 30L)));
        assertFalse(network.hasTypeSpace(Map.of(COPPER, 91L), Map.of()));
    }
    private static MatterInventory cell(long capacity,int types,SubstanceKey key,long amount) {
        var cell=new MatterInventory(capacity,types);assertEquals(amount,cell.insert(key,amount));return cell;
    }
}
