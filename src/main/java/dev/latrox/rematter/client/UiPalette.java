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
    public static final int SEAM = 0xff13161b;
    public static final int CONTACT = 0xffae6f49;
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

    public static final int GHOST = 0x99adbec0;
    public static final int PRIMAL = 0xff8a5cff;
    public static final int PRIMAL_CORE = 0xffffffff;
    public static final int PRIMAL_CORE_SOFT = 0xffefe6ff;
    public static final int PRIMAL_LIGHT = 0xffae8eff;
    public static final int PRIMAL_TEAL = 0xff46e0c8;
    public static final int PRIMAL_COOL = 0xff5cc8ff;
    public static final int PRIMAL_WARM = 0xffff6ad5;
    public static final int PRIMAL_GOLD = 0xffffb37a;
    public static final int PRIMAL_GHOST = 0x998a5cff;
    public static final int PRESSURE_GHOST = 0xccefb47d;
    public static final int AMBER = 0xffe3a94b;
    public static final int VIOLET = 0xffb381e8;
    public static final int EMBER = 0xffea4848;
    public static final int BLUE = 0xff6794f1;
    /** Functional port legends, independent of a device's recipe identity and cable tier. */
    public static final int PORT_AUTOMATIC = 0xff64727e;
    public static final int PORT_ENERGY_INPUT = 0xff4fe18e;
    public static final int PORT_ENERGY_OUTPUT = 0xffea4848;
    public static final int CABLE_BASIC = 0xdc894f;
    public static final int CABLE_REINFORCED = 0x54bfac;
    public static final int CABLE_RESONANT = 0xbe8fbb;
    public static final int ENERGY_GLINT = 0xe4decc;
    public static final int WHITE = 0xffffffff;
    public static final int STRUCTURE_HINT = 0xffffb45c;
    public static final int STRUCTURE_BOUNDS = 0x66adbec0;
    public static final int STRUCTURE_HINT_SHELL = 0xaaadbec0;
    public static final int STRUCTURE_HINT_GLASS = 0xaa7bbde5;
    public static final int STRUCTURE_HINT_PORT = 0xcc52d7c8;
    public static final int STRUCTURE_HINT_CLEAR = 0xddea4848;
    public static final int BADGE_BACKGROUND = 0xc00a0d11;
    public static final int SUBSTANCE_IRON = 0xffadbec8;
    public static final int SUBSTANCE_COPPER = 0xffd58c5c;
    public static final int SUBSTANCE_GOLD = 0xffe2be55;
    public static final int SUBSTANCE_WATER = 0xff62b5e4;
    public static final int SUBSTANCE_MINERAL = 0xffa8937c;

    public static int machineAccent(MachineKind kind) {
        return switch(kind) {
            case ANALYZER -> 0xffd7d1bc;
            case DECOMPOSER, INDUSTRIAL_DECOMPOSER -> 0xffdc894f;
            case SYNTHESIZER, INDUSTRIAL_SYNTHESIZER -> 0xff7dd27f;
            case NETWORK_INTERFACE -> 0xff6794f1;
            case PRIMAL_CONVERTER, PRIMAL_REACTOR -> PRIMAL;
            case CRYSTAL_GENERATOR, INDUSTRIAL_CRYSTAL_GENERATOR, ENERGY_MATRIX, ENERGY_REACTOR -> EMBER;
            case REACTOR,INDUSTRIAL_REACTOR,TRANSMUTATION_REACTOR -> 0xffb381e8;
            case PREPARATOR,INDUSTRIAL_PREPARATOR -> AMBER;
            case HAND_PREPARATOR -> MUTED;
            case SINTER_FURNACE -> 0xffea4848;
            case DRIVE,TERMINAL,ATMOSPHERIC_COLLECTOR,DEEP_EXTRACTOR -> 0xff57cddb;
        };
    }

    public static int moduleAccent(dev.latrox.rematter.content.IndustrialModuleKind kind) {
        return switch(kind) {
            case GRINDING_UNIT,SEPARATION_UNIT,MIXING_BASIN -> AMBER;
            case FIELD_COIL,REACTION_CHAMBER -> VIOLET;
            case PARTICLE_EMITTER, REACTOR_FUEL_CORE -> EMBER;
            case REACTOR_INDUCTION_COIL -> BLUE;
            case PRIMAL_CONDENSER_CORE -> PRIMAL;
        };
    }
    /** Ping-pong sweep across the five Primal stops (Teal -> Cool -> Body -> Warm -> Gold -> back). */
    public static int primalSpectrum(float cycle) {
        float t = cycle - (float) Math.floor(cycle);
        float pos = (t < .5F ? t * 2F : (1F - t) * 2F) * 4F;
        int[] stops = {PRIMAL_TEAL, PRIMAL_COOL, PRIMAL, PRIMAL_WARM, PRIMAL_GOLD};
        int i = Math.min(3, (int) pos);
        return mix(stops[i], stops[i + 1], pos - i);
    }
    public static int modulePulse(dev.latrox.rematter.content.IndustrialModuleKind kind) {
        int hue=moduleAccent(kind);
        int r=((hue>>16)&255),g=((hue>>8)&255),b=hue&255;
        return 255<<24 | Math.round(r+(255-r)*.45F)<<16 | Math.round(g+(255-g)*.45F)<<8 | Math.round(b+(255-b)*.45F);
    }
    /** A colour mixed toward white, as the palette module lightens a lit edge. */
    public static int tint(int color, float amount) {
        int r=(color>>16)&255,g=(color>>8)&255,b=color&255;
        return color&0xff000000 | Math.round(r+(255-r)*amount)<<16 | Math.round(g+(255-g)*amount)<<8 | Math.round(b+(255-b)*amount);
    }
    public static int brightness(float brightness) {
        int value=(int)(255*Math.clamp(brightness,0,1));
        return 255<<24|value<<16|value<<8|value;
    }

    /** Rendered hardware uses the same neutral and status colours as the baked family. */
    public static final int MOTION_STEEL = 0xffc3ccce;
    public static final int WAITING_LIGHT = 0xff54b8f1;
    public static int multiply(int color, float brightness) {
        brightness=Math.clamp(brightness,0,1);
        return color&0xff000000 | Math.round(((color>>16)&255)*brightness)<<16
                | Math.round(((color>>8)&255)*brightness)<<8 | Math.round((color&255)*brightness);
    }
    public static int mix(int first,int second,float amount) {
        amount=Math.clamp(amount,0,1);
        int r=Math.round(((first>>16)&255)*(1-amount)+((second>>16)&255)*amount);
        int g=Math.round(((first>>8)&255)*(1-amount)+((second>>8)&255)*amount);
        int b=Math.round((first&255)*(1-amount)+(second&255)*amount);
        return WHITE&0xff000000 | r<<16 | g<<8 | b;
    }
    public static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | (Math.clamp(alpha, 0, 255) << 24);
    }
    public static int rgb(int color) {
        return color & 0x00ffffff;
    }

    private UiPalette() {}
}
