package dev.latrox.rematter.client.anim;

/** Critically damped progress; it never predicts progress beyond an acknowledged rising target. */
public final class Follower {
    private double value, velocity;
    public float advance(double target, double dt) {
        target = Math.clamp(target, 0, 1);
        if (!Double.isFinite(target) || !Double.isFinite(dt)) return (float) value;
        dt = Math.clamp(dt, 0, 5);
        double smooth = target < value ? 4 : 6;
        double omega = 2 / smooth;
        double displacement = value - target;
        double change = (velocity + omega * displacement) * dt;
        double decay = Math.exp(-omega * dt);
        double next = target + (displacement + change) * decay;
        velocity = (velocity - omega * change) * decay;
        if (target >= value && next > target || target < value && next < target) {
            next = target; velocity = 0;
        }
        value = Math.clamp(next, 0, 1);
        return (float) value;
    }
    public void reset(double target) { value = Math.clamp(target, 0, 1); velocity = 0; }
    public float value() { return (float) value; }
}
