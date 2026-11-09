package dev.latrox.rematter.client;

import dev.latrox.rematter.content.MachineKind;

/** Graphite console surfaces, with the model's family color in the title strip. */
public final class UiPalette {
    public static final int BACKGROUND = 0xff20282c;
    public static final int PANEL = 0xff182126;
    public static final int SURFACE = 0xff2b373b;
    public static final int SURFACE_ACTIVE = 0xff254b4d;
    public static final int RECESS = 0xff10191d;
    public static final int BORDER = 0xff52676c;
    public static final int HIGHLIGHT = 0xff405359;
    public static final int TEXT = 0xffedf4f3;
    public static final int MUTED = 0xffadbec0;
    public static final int ACCENT = 0xff52d7c8;
    public static final int ACCENT_BRIGHT = 0xff8becdf;
    public static final int WARNING = 0xffefb47d;
    public static final int DISABLED = 0xff829397;
    public static final int SLOT_EDGE = 0xff627b80;
    public static final int UPGRADE = 0xffa6b5d2;
    public static final int LIQUID = 0xff7bbde5;
    public static final int GAS = 0xffc5a8e5;

    public static int machineAccent(MachineKind kind) {
        return switch(kind) {
            case ANALYZER -> 0xffd7d1bc;
            case DECOMPOSER -> 0xffdc894f;
            case SYNTHESIZER -> 0xff7dd27f;
            case CRYSTALLIZER -> 0xffc794eb;
            case SEPARATOR -> 0xffe3a94b;
            case THERMAL_PROCESSOR -> 0xffea7765;
            case AIR_SEPARATOR -> 0xff83cdd9;
            case ELECTROLYZER,NETWORK_INTERFACE -> 0xff6794f1;
            case REACTOR,INDUSTRIAL_REACTOR -> 0xffb381e8;
            case PREPARATOR,INDUSTRIAL_PREPARATOR,HAND_PREPARATOR -> 0xffe3a94b;
            case SINTER_FURNACE -> 0xffea4848;
            case DRIVE,TERMINAL -> 0xff57cddb;
        };
    }

    private UiPalette() {}
}
