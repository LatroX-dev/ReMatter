package dev.latrox.rematter.content;

/** Four discrete per-machine audio volume states: 100%, 65%, 35%, and Mute (0%). */
public enum MachineSoundLevel {
    HIGH(3, 1.00F, 100, "high"),
    MEDIUM(2, 0.65F, 65, "medium"),
    LOW(1, 0.35F, 35, "low"),
    MUTED(0, 0.00F, 0, "muted");

    private final int level;
    private final float gain;
    private final int percent;
    private final String id;

    MachineSoundLevel(int level, float gain, int percent, String id) {
        this.level = level;
        this.gain = gain;
        this.percent = percent;
        this.id = id;
    }

    public int level() {
        return level;
    }

    public float gain() {
        return gain;
    }

    public int percent() {
        return percent;
    }

    public String id() {
        return id;
    }

    public boolean muted() {
        return this == MUTED;
    }

    /** Steps downward through the 3 audible volume levels and then Mute: 100% -> 65% -> 35% -> Mute -> 100%. */
    public MachineSoundLevel next() {
        return switch (this) {
            case HIGH -> MEDIUM;
            case MEDIUM -> LOW;
            case LOW -> MUTED;
            case MUTED -> HIGH;
        };
    }

    /** Steps in reverse order: 100% -> Mute -> 35% -> 65% -> 100%. */
    public MachineSoundLevel previous() {
        return switch (this) {
            case HIGH -> MUTED;
            case MUTED -> LOW;
            case LOW -> MEDIUM;
            case MEDIUM -> HIGH;
        };
    }

    public static MachineSoundLevel byLevel(int value) {
        return switch (value) {
            case 0 -> MUTED;
            case 1 -> LOW;
            case 2 -> MEDIUM;
            default -> value < 0 ? MUTED : HIGH;
        };
    }
}
