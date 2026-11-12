package dev.latrox.rematter.content;

import dev.latrox.rematter.content.preparation.PreparationTier;
import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/** An automatic face offers every role the machine supports. An assigned face has one explicit role. */
public enum MachinePortMode implements StringRepresentable {
    // Ring colours are the PORT_MODES of the family palette: the block, the Machine Port and the screen share them.
    AUTOMATIC(0x4D5A64, 0xBFCAD5),
    ITEM_INPUT(0xDC894F),
    ITEM_OUTPUT(0xEA4848),
    FLUID_INPUT(0x6794F1),
    FLUID_OUTPUT(0xB381E8),
    POWER(0xE3A94B),
    MATTER(0x57CDDB),
    CHEMICAL(0x7DD27F),
    DISABLED(0x13161B, 0x8F9AA6);

    public static final com.mojang.serialization.Codec<MachinePortMode> CODEC = StringRepresentable.fromEnum(MachinePortMode::values);
    private final int ring, text;
    MachinePortMode(int ring) { this(ring, ring); }
    MachinePortMode(int ring, int text) { this.ring = ring; this.text = text; }
    /** The colour of the ring on the block and in the side diagram. */
    public int ring() { return 0xff000000 | ring; }
    /** A readable tint of the role for labels and icons; the two neutral roles are too dark to read as text. */
    public int color() { return 0xff000000 | text; }
    @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    public Component label() { return Component.translatable("port.rematter.mode." + getSerializedName()).withColor(text); }
    /** One sentence on what a side with this role does. */
    public Component meaning() { return Component.translatable("port.rematter.meaning." + getSerializedName()); }
    public boolean allows(MachinePortMode role) { return this == AUTOMATIC || this == role; }
    /** Whether the kind has a side configuration at all; {@link PortSides} decides which of its sides take part. */
    public static boolean configurable(MachineKind kind) { return kind != MachineKind.TERMINAL && !IndustrialStructure.isIndustrial(kind); }
    public static boolean configurable(MachineKind kind, Side side) { return configurable(kind) && PortSides.available(kind, side); }
    public static MachinePortMode byIndex(int index) {
        return index >= 0 && index < values().length ? values()[index] : DISABLED;
    }
    static MachinePortMode load(String id) {
        for (MachinePortMode mode : values()) if (mode.getSerializedName().equals(id)) return mode;
        return DISABLED;
    }
    /** The next role this kind supports, forward or backward through the fixed order. */
    MachinePortMode step(MachineKind kind, boolean forward) {
        var modes = values();
        for (int i = 1; i <= modes.length; i++) {
            var candidate = modes[Math.floorMod(ordinal() + (forward ? i : -i), modes.length)];
            if (candidate.supported(kind)) return candidate;
        }
        return AUTOMATIC;
    }
    boolean supported(MachineKind kind) {
        return switch (this) {
            case AUTOMATIC, DISABLED, MATTER -> true;
            case ITEM_INPUT -> kind.generator() || kind==MachineKind.PRIMAL_CONVERTER || PreparationTier.isPreparation(kind) || kind == MachineKind.DECOMPOSER || kind == MachineKind.ANALYZER;
            case ITEM_OUTPUT -> PreparationTier.isPreparation(kind) || kind.synthesis();
            case FLUID_INPUT -> kind == MachineKind.DECOMPOSER;
            case FLUID_OUTPUT -> kind == MachineKind.DECOMPOSER || kind.synthesis();
            case POWER -> (kind.generator()||kind.processes()) && !PreparationTier.manual(kind);
            case CHEMICAL -> kind.isStorage();
        };
    }

    /** Sides rotate with a compact machine, including when it is packed and placed again. */
    public enum Side implements StringRepresentable {
        FRONT, RIGHT, BACK, LEFT, TOP, BOTTOM;

        public static final com.mojang.serialization.Codec<Side> CODEC = StringRepresentable.fromEnum(Side::values);
        @Override public String getSerializedName() { return key(); }

        public Direction world(Direction front) {
            return switch (this) {
                case FRONT -> front;
                case RIGHT -> front.getCounterClockWise();
                case BACK -> front.getOpposite();
                case LEFT -> front.getClockWise();
                case TOP -> Direction.UP;
                case BOTTOM -> Direction.DOWN;
            };
        }
        public static Side local(Direction front, Direction world) {
            for (Side side : values()) if (side.world(front) == world) return side;
            throw new IllegalArgumentException("Invalid machine face");
        }
        String key() { return name().toLowerCase(Locale.ROOT); }
        public Component label() { return Component.translatable("port.rematter.side." + key()); }
    }
}
