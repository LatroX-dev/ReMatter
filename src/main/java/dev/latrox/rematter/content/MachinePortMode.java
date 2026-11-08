package dev.latrox.rematter.content;

import dev.latrox.rematter.content.preparation.PreparationTier;
import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/** An automatic face keeps the old capabilities. An assigned face has one explicit role. */
public enum MachinePortMode implements StringRepresentable {
    AUTOMATIC(0xBFCAD5),
    ITEM_INPUT(0xE4AF66),
    ITEM_OUTPUT(0xEA4848),
    FLUID_INPUT(0x6DBEFF),
    FLUID_OUTPUT(0xA59EFF),
    POWER(0xEAD664),
    MATTER(0x67E4CE),
    CHEMICAL(0x9DE08B),
    DISABLED(0x8F9AA6);

    private final int color;
    MachinePortMode(int color) { this.color = color; }
    public int color() { return 0xff000000 | color; }
    @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    public Component label() { return Component.translatable("port.rematter.mode." + getSerializedName()).withColor(color); }
    public boolean allows(MachinePortMode role) { return this == AUTOMATIC || this == role; }
    public static boolean configurable(MachineKind kind) { return kind != MachineKind.TERMINAL && !IndustrialStructure.isIndustrial(kind); }
    public static MachinePortMode byIndex(int index) {
        return index >= 0 && index < values().length ? values()[index] : DISABLED;
    }
    static MachinePortMode load(String id) {
        for (MachinePortMode mode : values()) if (mode.getSerializedName().equals(id)) return mode;
        return DISABLED;
    }
    MachinePortMode next(MachineKind kind, boolean industrial) {
        var modes = values();
        for (int i = 1; i <= modes.length; i++) {
            var candidate = modes[(ordinal() + i) % modes.length];
            if (candidate.supported(kind, industrial)) return candidate;
        }
        return AUTOMATIC;
    }
    private boolean supported(MachineKind kind, boolean industrial) {
        return switch (this) {
            case AUTOMATIC, DISABLED, MATTER -> true;
            case ITEM_INPUT -> PreparationTier.isPreparation(kind) || kind == MachineKind.DECOMPOSER || kind == MachineKind.ANALYZER;
            case ITEM_OUTPUT -> PreparationTier.isPreparation(kind) || kind == MachineKind.SYNTHESIZER;
            case FLUID_INPUT -> kind == MachineKind.DECOMPOSER;
            case FLUID_OUTPUT -> kind == MachineKind.DECOMPOSER || kind == MachineKind.SYNTHESIZER || industrial;
            case POWER -> kind.processes() && !PreparationTier.manual(kind);
            case CHEMICAL -> kind.isStorage();
        };
    }

    /** Sides rotate with a compact machine, including when it is packed and placed again. */
    public enum Side {
        FRONT, RIGHT, BACK, LEFT, TOP, BOTTOM;

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
        static Side local(Direction front, Direction world) {
            for (Side side : values()) if (side.world(front) == world) return side;
            throw new IllegalArgumentException("Invalid machine face");
        }
        String key() { return name().toLowerCase(Locale.ROOT); }
        public Component label() { return Component.translatable("port.rematter.side." + key()); }
    }
}
