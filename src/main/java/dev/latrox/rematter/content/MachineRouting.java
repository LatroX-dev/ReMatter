package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.ProcessType;
import java.util.List;

/** The shared routing used by machines, viewers and external crafting patterns. */
public final class MachineRouting {
    private MachineRouting() {}
    public static MachineKind primary(ProcessType type) {
        return switch (type) {
            case ELECTROLYSIS -> MachineKind.ELECTROLYZER;
            case REACTION -> MachineKind.REACTOR;
            case SEPARATION -> MachineKind.SEPARATOR;
            case PHASE_CHANGE -> MachineKind.THERMAL_PROCESSOR;
            case CRYSTALLIZATION -> MachineKind.CRYSTALLIZER;
        };
    }
    public static boolean supports(MachineKind kind, ProcessType type) {
        return kind == primary(type) || kind == MachineKind.INDUSTRIAL_REACTOR
                && (type == ProcessType.REACTION || type == ProcessType.SEPARATION || type == ProcessType.PHASE_CHANGE);
    }
    public static List<MachineKind> machines(ProcessType type) {
        var first = primary(type);
        return supports(MachineKind.INDUSTRIAL_REACTOR, type) ? List.of(first, MachineKind.INDUSTRIAL_REACTOR) : List.of(first);
    }
}
