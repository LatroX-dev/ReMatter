package dev.latrox.rematter.client.anim;

/** Real-time animation ticks (twenty per second), with one identical sample throughout a rendered frame. */
public final class FrameClock {
    private double ticks;
    private boolean committed;
    private static double bounded(double delta,boolean paused){
        return paused||!Double.isFinite(delta)||delta<=0?0:Math.min(delta,5);
    }
    /** Extraction precedes RenderFrameEvent.Pre in the supported renderer; preview the same frame's delta. */
    public double sample(double delta,boolean paused){return ticks+(committed?0:bounded(delta,paused));}
    public void commit(double delta,boolean paused){
        if(!committed){ticks+=bounded(delta,paused);committed=true;}
    }
    public void endFrame(){committed=false;}
}
