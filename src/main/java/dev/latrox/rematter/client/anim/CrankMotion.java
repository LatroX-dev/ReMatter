package dev.latrox.rematter.client.anim;

/** One full turn per server-accepted crank. Recipe completion and progress resets are not crank events. */
public final class CrankMotion {
    private double lastTime=Double.NaN,angle,target,velocity;
    private int sequence;
    public float sample(double time,int acceptedSequence){
        if(!Double.isFinite(time))return rotation();
        if(Double.isNaN(lastTime)){lastTime=time;sequence=acceptedSequence;return rotation();}
        double elapsed=Math.max(0,time-lastTime),dt=Math.min(5,elapsed);lastTime=time;
        // Signed subtraction deliberately also handles the int serial wrapping once.
        int accepted=acceptedSequence-sequence;sequence=acceptedSequence;
        if(elapsed>20){target=angle;velocity=0;return rotation();}
        if(accepted>0)target+=360.0*Math.min(accepted,32);
        double omega=2.0/3,delta=angle-target,change=(velocity+omega*delta)*dt,decay=Math.exp(-omega*dt);
        double wanted=target+(delta+change)*decay;
        velocity=(velocity-omega*change)*decay;
        angle=Math.max(angle,Math.min(target,Math.min(wanted,angle+dt*72)));
        return rotation();
    }
    private float rotation(){return (float)(angle%360);}
    public double travelled(){return angle;}
    public double target(){return target;}
}
