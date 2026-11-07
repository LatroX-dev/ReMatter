package dev.latrox.rematter.content;

import java.util.EnumMap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Per-face roles of one machine. Automatic faces are implicit and never stored. */
final class MachineSideConfig {
    private final EnumMap<MachinePortMode.Side, MachinePortMode> ports = new EnumMap<>(MachinePortMode.Side.class);

    MachinePortMode mode(MachinePortMode.Side side) { return ports.getOrDefault(side, MachinePortMode.AUTOMATIC); }

    boolean allowsEverywhere(MachinePortMode role) {
        for (var side : MachinePortMode.Side.values()) if (!mode(side).allows(role)) return false;
        return true;
    }

    /** Advances one face and returns its previous mode. */
    MachinePortMode cycle(MachineKind kind, MachinePortMode.Side side) {
        MachinePortMode previous = mode(side);
        MachinePortMode next = previous.next(kind, false);
        if (next == MachinePortMode.AUTOMATIC) ports.remove(side);
        else ports.put(side, next);
        return previous;
    }

    /** Four bits per face, for the client block update. */
    int visuals() {
        int packed = 0;
        for (var side : MachinePortMode.Side.values()) packed |= mode(side).ordinal() << (4 * side.ordinal());
        return packed;
    }

    void readVisuals(int packed) {
        ports.clear();
        for (var side : MachinePortMode.Side.values()) {
            MachinePortMode mode = MachinePortMode.byIndex(packed >>> (4 * side.ordinal()) & 15);
            if (mode != MachinePortMode.AUTOMATIC) ports.put(side, mode);
        }
    }

    void save(ValueOutput output) {
        if (ports.isEmpty()) return;
        var saved = output.child("ports");
        ports.forEach((side, mode) -> saved.putString(side.key(), mode.getSerializedName()));
    }

    void load(ValueInput input, MachineKind kind) {
        ports.clear();
        var saved = input.childOrEmpty("ports");
        if (!MachinePortMode.configurable(kind)) return;
        for (var side : MachinePortMode.Side.values()) {
            saved.getString(side.key()).map(MachinePortMode::load)
                    .filter(mode -> mode != MachinePortMode.AUTOMATIC).ifPresent(mode -> ports.put(side, mode));
        }
    }
}
