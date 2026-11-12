package dev.latrox.rematter.content;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ControllerThroughputTest {
    @Test void parallelBatchesShareRateAndNewJobDoesNotInheritIdleCredit(){
        var work=new ControllerThroughput();long total=ControllerThroughput.units(List.of(1000L),List.of(1000L),4);
        for(int tick=0;tick<62;tick++)assertFalse(work.advance("four jobs",total,tick,128));
        assertEquals(64,work.remaining());assertTrue(work.advance("four jobs",total,62,128));
        work.completed();assertFalse(work.advance("next",1000,62,128));assertEquals(64,work.paid());
        assertFalse(work.advance("next",1000,10000,128));assertEquals(192,work.paid());
    }
    @Test void tierChangePaysOnlyRemainingWork(){
        var work=new ControllerThroughput();assertFalse(work.advance("same",10000,0,128));
        assertFalse(work.advance("same",10000,1,8192));assertEquals(1680,work.remaining());
        assertFalse(work.advance("same",10000,2,128));assertEquals(1552,work.remaining());
    }
    @Test void widestRouteUsesWeakestSegmentAndNeverAddsPorts(){
        var edges=new HashMap<String,List<String>>();
        connect(edges,"controller","p1");connect(edges,"p1","c1");connect(edges,"c1","drive");
        var tiers=new HashMap<String,Integer>();tiers.put("c1",0);
        assertEquals(0,ControllerThroughput.bestTier("controller",Set.of("drive"),tiers,edges));
        connect(edges,"controller","p2");connect(edges,"p2","c2");connect(edges,"c2","drive");tiers.put("c2",0);
        assertEquals(0,ControllerThroughput.bestTier("controller",Set.of("drive"),tiers,edges));
        tiers.put("c2",2);assertEquals(2,ControllerThroughput.bestTier("controller",Set.of("drive"),tiers,edges));
        connect(edges,"c2","weak");edges.get("c2").remove("drive");edges.get("drive").remove("c2");connect(edges,"weak","drive");tiers.put("weak",1);
        assertEquals(1,ControllerThroughput.bestTier("controller",Set.of("drive"),tiers,edges));
    }
    @Test void directContactIsBasicAndOverflowRejected(){
        assertEquals(0,ControllerThroughput.bestTier("machine",Set.of("drive"),Map.of(),Map.of("machine",List.of("drive"))));
        assertThrows(ArithmeticException.class,()->ControllerThroughput.units(List.of(Long.MAX_VALUE),List.of(1L),1));
        assertThrows(ArithmeticException.class,()->ControllerThroughput.units(List.of(Long.MAX_VALUE),List.of(),2));
    }
    private static void connect(Map<String,List<String>> edges,String a,String b){edges.computeIfAbsent(a,k->new ArrayList<>()).add(b);edges.computeIfAbsent(b,k->new ArrayList<>()).add(a);}
}
