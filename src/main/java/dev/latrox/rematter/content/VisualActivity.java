package dev.latrox.rematter.content;

/** A transient visual hold. Processing, prices and automation never read this state. */
final class VisualActivity {
    private long lastWorkTick = Long.MIN_VALUE;

    void worked(long tick) { lastWorkTick = tick; }

    boolean showing(long tick, int holdTicks) {
        return lastWorkTick != Long.MIN_VALUE && tick >= lastWorkTick && tick - lastWorkTick <= holdTicks;
    }
}
