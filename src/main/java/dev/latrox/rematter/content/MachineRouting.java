package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.ProcessType;
import dev.latrox.rematter.chemistry.ProcessRecipe;
import java.util.List;

/** The shared routing used by machines, viewers and external crafting patterns. */
public final class MachineRouting {
    private MachineRouting() {}
    public static MachineKind primary(ProcessRecipe recipe) { return recipe.minField()>0?MachineKind.INDUSTRIAL_REACTOR:MachineKind.REACTOR; }
    public static boolean supports(MachineKind kind,ProcessRecipe recipe) {
        return recipe.minField()>0?kind==MachineKind.INDUSTRIAL_REACTOR:supports(kind,recipe.type());
    }
    public static List<MachineKind> machines(ProcessRecipe recipe) {
        return recipe.minField()>0?List.of(MachineKind.INDUSTRIAL_REACTOR):machines(recipe.type());
    }
    public static MachineKind primary(ProcessType type) {
        return MachineKind.REACTOR;
    }
    public static boolean supports(MachineKind kind, ProcessType type) {
        return kind == MachineKind.REACTOR || kind == MachineKind.INDUSTRIAL_REACTOR;
    }
    public static boolean environmentSources(MachineKind kind) {
        return kind == MachineKind.REACTOR || kind == MachineKind.INDUSTRIAL_REACTOR;
    }
    public static List<MachineKind> machines(ProcessType type) {
        var first = primary(type);
        return supports(MachineKind.INDUSTRIAL_REACTOR, type) ? List.of(first, MachineKind.INDUSTRIAL_REACTOR) : List.of(first);
    }
}
