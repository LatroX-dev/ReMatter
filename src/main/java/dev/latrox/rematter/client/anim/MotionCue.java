package dev.latrox.rematter.client.anim;

/**
 * One cue point on a mechanism cycle. The cue index is {@code floor(turns / period - phase)}; a sound belongs to
 * every increase of that index, so it lands on the same spot of the visible stroke at any tempo.
 */
public final class MotionCue {
    private final double period, phase;
    private boolean primed;
    private long index;

    public MotionCue(double period, double phase) {
        this.period = period;
        this.phase = phase;
    }

    public static long index(double turns, double period, double phase) {
        return (long) Math.floor(turns / period - phase);
    }

    /**
     * Takes one sample per client tick. The first sample only records where the mechanism stands. Later samples
     * return true when the index rose, once however many cycles a single step skipped, and only at sufficient drive;
     * a crossing below that drive is spent, never replayed.
     */
    public boolean advance(double turns, float drive, float minDrive) {
        if (!Double.isFinite(turns)) return false;
        long next = index(turns, period, phase);
        boolean crossed = primed && next > index;
        primed = true;
        index = next;
        return crossed && drive >= minDrive;
    }

    /** The next sample starts over, for a cue that changes the mechanism it listens to. */
    public void reset() {
        primed = false;
    }

    /** Playback speed of a cue clip: the loop pitch, the mechanism tempo when it stretches, and a progress glide. */
    public static float pitch(float loopPitch, boolean stretch, float tempo, boolean glides, float from, float to, float progress) {
        float pitch = loopPitch * (stretch ? tempo : 1.0F);
        if (glides) pitch *= from + (to - from) * Math.clamp(progress, 0.0F, 1.0F);
        return Float.isFinite(pitch) ? Math.clamp(pitch, 0.5F, 2.0F) : 1.0F;
    }
}
