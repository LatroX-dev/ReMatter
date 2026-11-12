package dev.latrox.rematter.content;

import java.util.EnumMap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Per-side roles of one machine. Only the owner's choices are stored; a side nobody configured has the initial
 * role of {@link PortSides}, and a side that can never be a connection is always Disabled.
 */
final class MachineSideConfig {
    private final EnumMap<MachinePortMode.Side, MachinePortMode> ports = new EnumMap<>(MachinePortMode.Side.class);

    MachinePortMode mode(MachineKind kind, MachinePortMode.Side side) {
        if (!PortSides.available(kind, side)) return MachinePortMode.DISABLED;
        return ports.getOrDefault(side, PortSides.initial(kind, side));
    }

    /** Steps one side to its next or previous role and returns the role it had, or null when the side is fixed. */
    @Nullable MachinePortMode cycle(MachineKind kind, MachinePortMode.Side side, boolean forward) {
        if (!MachinePortMode.configurable(kind, side)) return null;
        MachinePortMode previous = mode(kind, side);
        set(kind, side, previous.step(kind, forward));
        return previous;
    }

    private void set(MachineKind kind, MachinePortMode.Side side, MachinePortMode mode) {
        if (mode == PortSides.initial(kind, side)) ports.remove(side);
        else ports.put(side, mode);
    }

    /** Four bits per side, for the client block update and the port plates. */
    int visuals(MachineKind kind) { return PortSides.pack(side -> mode(kind, side)); }

    void readVisuals(MachineKind kind, int packed) {
        ports.clear();
        for (var side : MachinePortMode.Side.values())
            if (PortSides.available(kind, side)) set(kind, side, PortSides.unpack(packed, side));
    }

    void save(ValueOutput output) {
        if (ports.isEmpty()) return;
        var saved = output.child("ports");
        ports.forEach((side, mode) -> saved.putString(side.key(), mode.getSerializedName()));
    }

    /** A role saved for a side that is no longer a connection, or one the kind does not support, is dropped. */
    void load(ValueInput input, MachineKind kind) {
        ports.clear();
        var saved = input.childOrEmpty("ports");
        for (var side : MachinePortMode.Side.values()) {
            if (!MachinePortMode.configurable(kind, side)) continue;
            saved.getString(side.key()).map(MachinePortMode::load).filter(mode -> mode.supported(kind))
                    .ifPresent(mode -> set(kind, side, mode));
        }
    }
}
