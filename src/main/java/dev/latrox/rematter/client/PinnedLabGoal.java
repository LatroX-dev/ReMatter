package dev.latrox.rematter.client;

import dev.latrox.rematter.content.TerminalBatchPlanner.CatalogTarget;
import dev.latrox.rematter.content.TerminalBatchPlanner.TargetKind;
import dev.latrox.rematter.network.TerminalBatchPlanPayload;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** One personal recipe bookmark for the connected play session. Stock and plans are never cached here. */
record PinnedLabGoal(TargetKind kind, String id, String phase, int quantity) {
    private static @Nullable PinnedLabGoal current;

    static @Nullable PinnedLabGoal current() { return current; }
    static void clear() { current = null; }
    static PinnedLabGoal of(CatalogTarget target, int quantity) {
        return new PinnedLabGoal(target.kind(), target.id(), target.phase(), quantity);
    }
    void toggle() { current = equals(current) ? null : this; }

    boolean matches(TerminalBatchPlanPayload plan, int container) {
        return plan.containerId() == container && plan.targetKind() == kind && plan.targetId().equals(id)
                && plan.phase().equals(phase) && plan.batches() == quantity;
    }

    Component name() {
        return switch (kind) {
            case SUBSTANCE -> SubstanceNames.substance(id, phase, true);
            case SYNTHESIS_ITEM -> SubstanceNames.item(id);
            case SYNTHESIS_FLUID -> SubstanceNames.fluid(id);
        };
    }

    Component title() {
        return Component.translatable(kind == TargetKind.SUBSTANCE ? "screen.rematter.lab.goal_units" : "screen.rematter.lab.goal_batches", quantity, name());
    }

    static Component unlock(String sample) {
        return sample.isEmpty() ? Component.translatable("screen.rematter.terminal.request.locked_target")
                : Component.translatable("screen.rematter.lab.sample", SubstanceNames.item(sample));
    }
}
