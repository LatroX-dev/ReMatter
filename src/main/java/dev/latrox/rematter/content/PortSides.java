package dev.latrox.rematter.content;

import dev.latrox.rematter.content.MachinePortMode.Side;
import net.neoforged.neoforge.model.data.ModelProperty;
import org.jspecify.annotations.Nullable;

/**
 * Which sides of a device can be a connection at all. Cables, every capability, direct neighbour links, the side
 * configuration, the saved data, the update tag and the drawn port plates read this one table.
 */
public final class PortSides {
    /** How the closed top of a sealed device behaves. */
    public enum SealedTop {
        /** A normal connection that starts as Automatic, like the other sides. */
        CONNECTION,
        /** A normal connection that starts Disabled: the top stays a plain face until the owner enables it. */
        OPT_IN,
        /** Never a connection. */
        CLOSED
    }
    /** The one switch for the top of sealed devices. The asset generators read this line. */
    public static final SealedTop SEALED_TOP = SealedTop.OPT_IN;

    /** Why a side carries no connection; the settings screen names the reason. */
    public enum Closed { FRONT, OPEN_TOP, CLOSED_TOP, BACK_ONLY, STRUCTURE }

    /** Appearance of all six sides: four bits per side, the ordinal of its {@link PortAppearance}. */
    public static final ModelProperty<Integer> PLATES = new ModelProperty<>();

    private PortSides() {}

    public static boolean available(MachineKind kind, Side side) { return closed(kind, side) == null; }

    /** The reason a side is closed, or null for a side that can be a connection. */
    public static @Nullable Closed closed(MachineKind kind, Side side) {
        if (IndustrialStructure.isIndustrial(kind)) return Closed.STRUCTURE;
        if (side == Side.FRONT) return Closed.FRONT;
        if (kind == MachineKind.TERMINAL) return side == Side.BACK ? null : Closed.BACK_ONLY;
        if (side != Side.TOP) return null;
        if (kind.openTop()) return Closed.OPEN_TOP;
        return SEALED_TOP == SealedTop.CLOSED ? Closed.CLOSED_TOP : null;
    }

    /** The role of a side nobody has configured. */
    public static MachinePortMode initial(MachineKind kind, Side side) {
        if (!available(kind, side)) return MachinePortMode.DISABLED;
        boolean optIn = side == Side.TOP && SEALED_TOP == SealedTop.OPT_IN && kind != MachineKind.TERMINAL;
        return optIn ? MachinePortMode.DISABLED : MachinePortMode.AUTOMATIC;
    }

    /** Energy Cubes are the exception: their four identical sides, top and bottom are all connections. */
    public static boolean energyCube(Side side) { return true; }

    public static int pack(java.util.function.Function<Side, MachinePortMode> roles) {
        int packed = 0;
        for (Side side : Side.values()) packed |= roles.apply(side).ordinal() << (4 * side.ordinal());
        return packed;
    }
    public static MachinePortMode unpack(int packed, Side side) {
        return MachinePortMode.byIndex(packed >>> (4 * side.ordinal()) & 15);
    }
    public static int packAppearances(java.util.function.Function<Side, PortAppearance> appearances) {
        int packed = 0;
        for (Side side : Side.values()) packed |= appearances.apply(side).ordinal() << (4 * side.ordinal());
        return packed;
    }
    public static PortAppearance appearance(int packed, Side side) {
        return PortAppearance.byIndex(packed >>> (4 * side.ordinal()) & 15);
    }
}
