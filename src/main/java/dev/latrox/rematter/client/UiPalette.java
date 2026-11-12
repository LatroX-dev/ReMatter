package dev.latrox.rematter.client;

import dev.latrox.rematter.content.MachineKind;

/** Graphite console surfaces, with the model's family color in the title strip. */
public final class UiPalette {
    // The same BASE, SHADE, SEAM, WELL and HIGHLIGHT roles as the machine casing.
    public static final int BACKGROUND = 0xff2b2f35;
    public static final int PANEL = 0xff1d2127;
    public static final int SURFACE = 0xff2b2f35;
    public static final int SURFACE_ACTIVE = 0xff373e47;
    public static final int RECESS = 0xff0a0d11;
    public static final int BORDER = 0xff46515b;
    public static final int HIGHLIGHT = 0xff495059;
    public static final int TEXT = 0xffedf4f3;
    public static final int MUTED = 0xffadbec0;
    public static final int ACCENT = 0xff52d7c8;
    public static final int ACCENT_BRIGHT = 0xff8becdf;
    public static final int WARNING = 0xffefb47d;
    public static final int DISABLED = 0xff829397;
    public static final int SLOT_EDGE = 0xff64727e;
    public static final int UPGRADE = 0xffa6b5d2;
    public static final int LIQUID = 0xff7bbde5;
    public static final int GAS = 0xffc5a8e5;

    public static int machineAccent(MachineKind kind) {
        return switch(kind) {
            case ANALYZER -> 0xffd7d1bc;
            case DECOMPOSER -> 0xffdc894f;
            case SYNTHESIZER -> 0xff7dd27f;
            case NETWORK_INTERFACE -> 0xff6794f1;
            case PRIMAL_CONVERTER -> 0xffc5a8e5;
            case CRYSTAL_GENERATOR,INDUSTRIAL_CRYSTAL_GENERATOR -> 0xffe3a94b;
            case REACTOR,INDUSTRIAL_REACTOR -> 0xffb381e8;
            case PREPARATOR,INDUSTRIAL_PREPARATOR,HAND_PREPARATOR -> 0xffe3a94b;
            case SINTER_FURNACE -> 0xffea4848;
            case DRIVE,TERMINAL -> 0xff57cddb;
        };
    }

    private UiPalette() {}
}
