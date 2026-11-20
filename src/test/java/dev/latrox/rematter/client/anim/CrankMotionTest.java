package dev.latrox.rematter.client.anim;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrankMotionTest {
    @Test void eachAcceptedCrankIsOneTurnAndUnchangedUpdatesAddNothing(){
        var crank=new CrankMotion();crank.sample(0,0);
        crank.sample(1,1);assertEquals(360,crank.target());
        for(int frame=4;frame<300;frame++)crank.sample(frame/3.0,1);
        assertEquals(360,crank.travelled(),.00001);assertEquals(360,crank.target());
        crank.sample(100,2);assertEquals(720,crank.target());
    }
    @Test void fastestAcceptedCranksStayBoundedWithoutSnappingAtBatchEnds(){
        var crank=new CrankMotion();crank.sample(0,0);double previous=0;
        for(int frame=1;frame<900;frame++){
            double time=frame/3.0;int accepted=Math.min(5,1+(int)time/5);
            crank.sample(time,accepted);
            assertTrue(crank.travelled()>=previous);
            assertTrue(crank.travelled()-previous<=24.00001);
            previous=crank.travelled();
        }
        assertEquals(1800,crank.target());assertEquals(1800,crank.travelled(),.00001);
    }
    @Test void returningToViewDoesNotReplayUnseenCranksAndSerialWrapAddsOneTurn(){
        var crank=new CrankMotion();crank.sample(0,Integer.MAX_VALUE);
        crank.sample(1,Integer.MIN_VALUE);assertEquals(360,crank.target());
        double before=crank.travelled();
        crank.sample(100,Integer.MIN_VALUE+10);assertEquals(before,crank.travelled());assertEquals(before,crank.target());
    }
}
