package dev.latrox.rematter.client.anim;

import java.util.Arrays;

/** Continuous work motion. Progress estimates tempo and item size, never the phase of a mechanism. */
public final class WorkEnvelope {
    private double lastTime = Double.NaN, level, engagement, waiting, inactiveFor;
    private double turns, tempo = 1, targetTempo = 1, sampledAt = Double.NaN;
    private int sampledProgress, sampledDuration, rates;
    private final double[] rateWindow = {1, 1, 1};
    private final Follower follower = new Follower();

    public void sample(double time, boolean active, boolean hasJob, int progress, int duration, double baseRate) {
        if (!Double.isFinite(time)) return;
        double elapsed = Double.isNaN(lastTime) ? 0 : Math.max(0, time - lastTime);
        lastTime = time;
        double dt = Math.min(5, elapsed);
        if (Double.isNaN(sampledAt)) { sampledAt = time; sampledProgress = progress; sampledDuration = duration; }
        else if (duration != sampledDuration || progress < sampledProgress) {
            sampledAt = time; sampledProgress = progress; sampledDuration = duration;
        } else if (progress != sampledProgress && time - sampledAt >= 3) {
            rateWindow[rates++ % 3] = (progress - sampledProgress) / (time - sampledAt);
            double[] ordered = rateWindow.clone(); Arrays.sort(ordered);
            double rate = ordered[1];
            targetTempo = 1 + .10 * (Math.clamp(rate / Math.max(1, baseRate), 1, 5) - 1);
            sampledAt = time; sampledProgress = progress;
        }
        if (active) inactiveFor = 0; else inactiveFor += dt;
        // Hidden devices settle, rather than replaying an unseen start or a backlog of turns.
        if (elapsed > 20) {
            level = active ? 1 : 0; engagement = active || hasJob ? 1 : 0;
            waiting = !active && hasJob ? 1 : 0; tempo = targetTempo;
        } else {
            double previousDrive = drive();
            level = approach(level, active ? 1 : inactiveFor <= 3 ? level : 0, dt / (active ? 12 : 16));
            engagement = approach(engagement, active || hasJob ? 1 : 0, dt / (active || hasJob ? 12 : 20));
            waiting = approach(waiting, hasJob && !active && level < .001 ? 1 : 0, dt / 10);
            tempo += (targetTempo - tempo) * (1 - Math.exp(-dt / 20));
            turns += dt * (previousDrive + drive()) * .5 * tempo;
        }
        double rate = baseRate * (1 + 10 * (targetTempo - 1));
        double target = duration > 0 && duration / Math.max(1, rate) < 40 ? .5
                : duration > 0 ? (double) progress / duration : 0;
        if (elapsed > 20) follower.reset(target); else follower.advance(target, dt);
    }
    private static double approach(double current, double target, double step) {
        return current < target ? Math.min(target, current + step) : Math.max(target, current - step);
    }
    public static float ease(double value) {
        value = Math.clamp(value, 0, 1);
        return (float) (value * value * value * (value * (value * 6 - 15) + 10));
    }
    public float phase(double period) { return (float) ((turns / period) % 1); }
    public double turns() { return turns; }
    public float drive() { return ease(level); }
    public float engage() { return ease(engagement); }
    public float waiting() { return ease(waiting); }
    public float progress() { return follower.value(); }
    public float tempo() { return (float) tempo; }
}
