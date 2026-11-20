package dev.latrox.rematter.client.anim;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrameClockTest {
    @Test void allExtractionAndRenderSamplesUseExactlyOneFrameDelta(){
        var clock=new FrameClock();
        for(int frame=1;frame<=600;frame++){
            double wanted=frame/3.0;
            assertEquals(wanted,clock.sample(1.0/3,false),.0000001);
            assertEquals(wanted,clock.sample(1.0/3,false),.0000001);
            clock.commit(1.0/3,false);clock.commit(1.0/3,false);
            assertEquals(wanted,clock.sample(1.0/3,false),.0000001);
            clock.endFrame();
        }
    }
    @Test void pauseInvalidDeltaAndLongHitchesCannotJumpOrReverseTheClock(){
        var clock=new FrameClock();clock.commit(2,false);clock.endFrame();
        for(double delta:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-10,0}){
            assertEquals(2,clock.sample(delta,false));clock.commit(delta,false);clock.endFrame();
        }
        assertEquals(2,clock.sample(100,true));clock.commit(100,true);clock.endFrame();
        assertEquals(7,clock.sample(100,false));clock.commit(100,false);clock.endFrame();
        assertEquals(7.5,clock.sample(.5,false));
    }
    @Test void oneSecondIsTwentyAnimationTicksAtBothOrdinaryAndSlowServerRates(){
        var fast=new FrameClock();var slow=new FrameClock();
        for(int frame=0;frame<60;frame++){
            // The frame delta is in real-time20Hz units; game partials and server ticks are not inputs.
            fast.commit(20.0/60,false);slow.commit(20.0/60,false);fast.endFrame();slow.endFrame();
        }
        assertEquals(20,fast.sample(0,false),.000001);assertEquals(fast.sample(0,false),slow.sample(0,false));
    }
}
