package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.ProcessType;
import java.util.List;

/** The shared routing used by machines, viewers and external crafting patterns. */
public final class MachineRouting {
    private MachineRouting() {}
    public static MachineKind primary(ProcessType type) {
        return MachineKind.REACTOR;
    }
    public static boolean supports(MachineKind kind, ProcessType type) {
        return kind == MachineKind.REACTOR || kind == MachineKind.INDUSTRIAL_REACTOR || switch (kind) {
            case ELECTROLYZER -> type == ProcessType.ELECTROLYSIS;
            case SEPARATOR -> type == ProcessType.SEPARATION;
            case THERMAL_PROCESSOR -> type == ProcessType.PHASE_CHANGE;
            case CRYSTALLIZER -> type == ProcessType.CRYSTALLIZATION;
            default -> false;
        };
    }
    public static boolean environmentSources(MachineKind kind) {
        return kind == MachineKind.REACTOR || kind == MachineKind.INDUSTRIAL_REACTOR || kind == MachineKind.AIR_SEPARATOR;
    }
    public static List<MachineKind> machines(ProcessType type) {
        var first = primary(type);
        return supports(MachineKind.INDUSTRIAL_REACTOR, type) ? List.of(first, MachineKind.INDUSTRIAL_REACTOR) : List.of(first);
    }
}
