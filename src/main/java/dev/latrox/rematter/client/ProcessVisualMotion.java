package dev.latrox.rematter.client;

/** Smooth only between acknowledged positions. Never predict work beyond the server's progress. */
public final class ProcessVisualMotion {
    private float from, target;
    private double changedAt = Double.NaN;
    private int duration;

    public float sample(double time, int progress, int newDuration, boolean active) {
        float next = newDuration > 0 ? Math.clamp((float) progress / newDuration, 0, 1) : 0;
        if (!Double.isFinite(time)) return next;
        if (Double.isNaN(changedAt) || newDuration != duration || next < target || !active) {
            from = target = next; changedAt = time; duration = newDuration;
            return next;
        }
        if (next != target) {
            from = current(time); target = next; changedAt = time;
        }
        return current(time);
    }

    private float current(double time) {
        float amount = (float) Math.clamp((time - changedAt) / 4, 0, 1);
        return from + (target - from) * amount;
    }
}
