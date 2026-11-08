package dev.latrox.rematter.chemistry;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatterInventoryCellLimitTest {
    private static SubstanceKey key(String id) { return new SubstanceKey(SubstanceId.parse("test:"+id),Phase.SOLID); }
    @Test void countLimitRejectsNewTypesButAtomicReplacementCanFreeAType() {
        var store=new MatterInventory(1000,2);var a=key("a");var b=key("b");var c=key("c");
        assertEquals(100,store.insert(a,100));assertEquals(100,store.insert(b,100));
        var before=store.snapshot();assertEquals(0,store.insert(c,1));assertEquals(before,store.snapshot());
        assertFalse(store.apply(Map.of(),Map.of(c,1L)));
        assertTrue(store.apply(Map.of(a,100L),Map.of(c,100L)));
        assertEquals(Map.of(b,100L,c,100L),store.contents());assertEquals(2,store.typeCount());
    }
    @Test void reservationOwnsBothVolumeAndItsFutureTypeSlot() {
        var store=new MatterInventory(1000,2);var a=key("a");var b=key("b");var c=key("c");store.insert(a,1);
        var token=store.reserve(Map.of(),Map.of(b,1L)).orElseThrow();
        assertEquals(0,store.insert(c,1));assertTrue(store.reserve(Map.of(),Map.of(c,1L)).isEmpty());
        assertTrue(store.commit(token));assertEquals(Map.of(a,1L,b,1L),store.contents());
        assertFalse(store.commit(token));assertEquals(2,store.typeCount());
    }
    @Test void cancelledReservationAndFailedTransferDoNotLoseSourceMatter() {
        var source=new MatterInventory(1000);var target=new MatterInventory(1000,1);var a=key("a");var b=key("b");source.insert(a,10);
        var token=target.reserve(Map.of(),Map.of(b,1L)).orElseThrow();
        assertEquals(0,source.transferTo(target,a,10));assertEquals(10,source.amount(a));
        assertTrue(target.cancel(token));assertEquals(10,source.transferTo(target,a,10));assertEquals(0,source.total());
    }
    @Test void restoreAndRollbackRetainTheMountedCellLimit() {
        var small=new MatterInventory(1000,1);small.insert(key("a"),1);var original=small.snapshot();
        var large=new MatterInventory(1000);large.insert(key("a"),1);large.insert(key("b"),1);
        assertThrows(IllegalArgumentException.class,()->small.restoreInPlace(large.snapshot()));assertEquals(original,small.snapshot());
        small.extract(key("a"),1);small.restoreInPlace(original);assertEquals(1,small.maximumTypes());
        assertEquals(0,small.insert(key("b"),1));
    }
    @Test void inventoryWithoutATypeLimitRestoresWithoutOne() {
        var unlimited=new MatterInventory(1_000_000);
        for(int i=0;i<600;i++)assertEquals(1,unlimited.insert(key("substance_"+i),1));
        assertEquals(600,MatterInventory.restore(unlimited.snapshot()).typeCount());
    }
}
