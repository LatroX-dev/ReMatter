package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.BuiltinChemistry;
import dev.latrox.rematter.chemistry.SubstanceKey;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BatchReservationsTest {
    private static final SubstanceKey IRON=BuiltinChemistry.IRON,WATER=BuiltinChemistry.WATER;
    private final BatchReservations book=new BatchReservations();
    private BatchReservations.Ticket reserve(UUID worker,Object topology,Set<?> members,long tick,
            Map<SubstanceKey,Long> stock,long capacity,Map<SubstanceKey,Long> inputs,Map<SubstanceKey,Long> outputs,
            long energy,long physical,long target,SubstanceKey key) {
        return book.reserve(worker,topology,members,tick,stock,capacity,inputs,outputs,4,energy,100,physical,target,key);
    }
    @Test void twoWorkersCannotPromiseTheSameInputs() {
        var stock=Map.of(IRON,300L);var input=Map.of(IRON,100L);
        var first=reserve(UUID.randomUUID(),"net",Set.of("cell"),0,stock,1000,input,Map.of(),200,4,0,null);
        assertEquals(2,first.remaining());
        var second=reserve(UUID.randomUUID(),"net",Set.of("cell"),0,stock,1000,input,Map.of(),400,4,0,null);
        assertEquals(1,second.remaining());
        assertNull(reserve(UUID.randomUUID(),"net",Set.of("cell"),0,stock,1000,input,Map.of(),400,4,0,null));
    }
    @Test void targetCountsPlannedOutputsAndAllowsAnExactFinalBatch() {
        var a=reserve(UUID.randomUUID(),"net",Set.of("cell"),0,Map.of(WATER,500L),2000,Map.of(),Map.of(WATER,250L),100,4,1000,WATER);
        assertEquals(1,a.remaining());
        var b=reserve(UUID.randomUUID(),"net",Set.of("cell"),0,Map.of(WATER,500L),2000,Map.of(),Map.of(WATER,250L),400,4,1000,WATER);
        assertEquals(1,b.remaining());assertEquals(500,book.planned("net",WATER));
        assertNull(reserve(UUID.randomUUID(),"net",Set.of("cell"),0,Map.of(WATER,500L),2000,Map.of(),Map.of(WATER,250L),400,4,1000,WATER));
    }
    @Test void aBatchThatWouldOvershootATargetDoesNotStart() {
        assertNull(reserve(UUID.randomUUID(),"net",Set.of("cell"),0,Map.of(WATER,900L),2000,Map.of(),Map.of(WATER,250L),400,4,1000,WATER));
        assertNull(reserve(UUID.randomUUID(),"net",Set.of("cell"),0,Map.of(WATER,1000L),2000,Map.of(),Map.of(WATER,250L),400,4,1000,WATER));
    }
    @Test void energyPhysicalInputsAndOutputCapacityIndependentlyLimitParallelWork() {
        var worker=UUID.randomUUID();
        assertEquals(2,reserve(worker,"net",Set.of("cell"),0,Map.of(),1000,Map.of(),Map.of(WATER,100L),250,4,0,null).remaining());
        assertEquals(1,reserve(worker,"net",Set.of("cell"),0,Map.of(),1000,Map.of(),Map.of(WATER,100L),400,1,0,null).remaining());
        assertEquals(1,reserve(worker,"net",Set.of("cell"),0,Map.of(WATER,850L),1000,Map.of(),Map.of(WATER,100L),400,4,0,null).remaining());
    }
    @Test void topologySplitInvalidatesOldTicketsBeforeAnyNewBooking() {
        var old=reserve(UUID.randomUUID(),"joined",Set.of("a","b"),0,Map.of(IRON,400L),1000,Map.of(IRON,100L),Map.of(),400,4,0,null);
        var next=reserve(UUID.randomUUID(),"split",Set.of("a"),1,Map.of(IRON,200L),500,Map.of(IRON,100L),Map.of(),400,4,0,null);
        assertFalse(old.valid());assertEquals(2,next.remaining());
        assertThrows(IllegalStateException.class,()->book.completed(old));
    }
    @Test void unrelatedNetworksDoNotBlockEachOtherAndRetiredWorkersReleaseTheirPromises() {
        var worker=UUID.randomUUID();
        var old=reserve(worker,"a",Set.of("a"),0,Map.of(IRON,100L),500,Map.of(IRON,100L),Map.of(),100,1,0,null);
        assertNotNull(reserve(UUID.randomUUID(),"b",Set.of("b"),0,Map.of(IRON,100L),500,Map.of(IRON,100L),Map.of(),100,1,0,null));
        book.release(worker);assertFalse(old.valid());
        assertNotNull(reserve(worker,"a",Set.of("a"),1,Map.of(IRON,100L),500,Map.of(IRON,100L),Map.of(),100,1,0,null));
    }
    @Test void eachCompletedChargeReleasesOnlyItsOwnPromise() {
        var ticket=reserve(UUID.randomUUID(),"net",Set.of("a"),0,Map.of(),500,Map.of(),Map.of(WATER,100L),400,4,0,null);
        book.completed(ticket);assertEquals(3,ticket.remaining());assertEquals(300,book.planned("net",WATER));
        book.completed(ticket);book.completed(ticket);book.completed(ticket);assertFalse(ticket.valid());assertEquals(0,book.planned("net",WATER));
    }
}
