package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.chemistry.VoidPolicy;
import dev.latrox.rematter.network.TerminalMonitorPayload;
import dev.latrox.rematter.network.TerminalMonitorPayload.MonitorRow;
import dev.latrox.rematter.network.TerminalMonitorPayload.RowKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Collects live {@link TerminalMonitorPayload} diagnostics from a {@link MachineMatterNetwork}
 * and rolling {@link NetworkRateTracker}.
 */
public final class TerminalMonitorCollector {
    private TerminalMonitorCollector() {}

    public static TerminalMonitorPayload collect(
            MachineMatterNetwork network, NetworkRateTracker rateTracker, long nowTick, int containerId) {
        var netView = network.view();
        var inventory = network.ready() ? network.inventory() : null;
        var chemistry = ReMatter.chemistry();

        int drives = 0;
        int finiteCells = 0;
        int voidCells = 0;
        boolean voidUnrestricted = false;
        Set<SubstanceKey> voidFilteredKeys = new LinkedHashSet<>();

        for (MachineBlockEntity machine : network.machines()) {
            if (machine.kind() != MachineKind.DRIVE) continue;
            drives++;
            for (int bay = 0; bay < MachineCellAccess.DRIVE_BAYS; bay++) {
                ItemStack stack = machine.getItem(MachineCellAccess.slotForBay(MachineKind.DRIVE, bay));
                if (stack.getItem() instanceof MatterCellItem) {
                    finiteCells++;
                } else if (stack.getItem() instanceof VoidMatterCellItem) {
                    voidCells++;
                    VoidMatterCellItem.policy(stack).ifPresent(policy -> {
                        if (policy.unrestricted()) {
                            // handled below via flag
                        } else {
                            voidFilteredKeys.addAll(policy.allowed());
                        }
                    });
                    if (VoidMatterCellItem.policy(stack).map(VoidPolicy::unrestricted).orElse(false)) {
                        voidUnrestricted = true;
                    }
                }
            }
        }

        int totalWorkers = 0;
        int activeWorkers = 0;
        List<MonitorRow> targetRows = new ArrayList<>();
        for (MachineBlockEntity worker : network.workers()) {
            totalWorkers++;
            var deviceInfo = worker.terminalDevice();
            if (deviceInfo.status().equals("active") || deviceInfo.workingBatches() > 0) {
                activeWorkers++;
            }
            var settings = worker.settings();
            if (settings.target() > 0) {
                String subId = settings.targetSubstance();
                String phase = settings.targetPhase();
                boolean known = true;
                if (!subId.isEmpty()) {
                    try {
                        known = chemistry.isKnown(SubstanceKey.of(subId,
                                dev.latrox.rematter.chemistry.Phase.valueOf(phase.toUpperCase(Locale.ROOT))));
                    } catch (IllegalArgumentException invalid) {
                        known = false;
                    }
                }
                long current = worker.targetStock();
                boolean capReached = current >= settings.target();
                if (targetRows.size() < TerminalMonitorPayload.MAX_ROWS) {
                    targetRows.add(new MonitorRow(
                            RowKind.TARGET_CAP,
                            subId,
                            phase,
                            known,
                            worker.kind().id(),
                            current,
                            settings.target(),
                            !capReached,
                            false));
                }
            }
        }

        int totalEmitters = 0;
        int activeEmitters = 0;
        List<MonitorRow> emitterRows = new ArrayList<>();
        for (BlockEntity entity : network.memberEntities()) {
            if (!(entity instanceof MatterLevelEmitterBlockEntity emitter)) continue;
            totalEmitters++;
            boolean emitting = emitter.emitting();
            if (emitting) activeEmitters++;
            SubstanceKey target = emitter.target();
            String subId = target == null ? "" : target.id().toString();
            String phase = target == null ? "" : target.phase().name().toLowerCase(Locale.ROOT);
            boolean known = target == null || chemistry.isKnown(target);
            if (emitterRows.size() < TerminalMonitorPayload.MAX_ROWS) {
                emitterRows.add(new MonitorRow(
                        RowKind.LEVEL_EMITTER,
                        subId,
                        phase,
                        known,
                        "",
                        emitter.observedStock(),
                        emitter.threshold(),
                        emitting,
                        emitter.emitWhenBelow()));
            }
        }

        Map<SubstanceKey, Long> rates = rateTracker.sampleRatesPerMinute(inventory, nowTick);
        List<Map.Entry<SubstanceKey, Long>> sortedRates = new ArrayList<>(rates.entrySet());
        sortedRates.sort(Comparator.<Map.Entry<SubstanceKey, Long>>comparingLong(e -> Math.abs(e.getValue()))
                .reversed()
                .thenComparing(Map.Entry::getKey));

        List<MonitorRow> rows = new ArrayList<>();
        for (var entry : sortedRates) {
            if (rows.size() >= TerminalMonitorPayload.MAX_ROWS) break;
            SubstanceKey key = entry.getKey();
            long rate = entry.getValue();
            long stock = inventory == null ? 0L : inventory.amount(key);
            rows.add(new MonitorRow(
                    RowKind.RATE,
                    key.id().toString(),
                    key.phase().name().toLowerCase(Locale.ROOT),
                    chemistry.isKnown(key),
                    "",
                    stock,
                    rate,
                    rate > 0,
                    false));
        }
        for (MonitorRow row : targetRows) {
            if (rows.size() >= TerminalMonitorPayload.MAX_ROWS) break;
            rows.add(row);
        }
        for (MonitorRow row : emitterRows) {
            if (rows.size() >= TerminalMonitorPayload.MAX_ROWS) break;
            rows.add(row);
        }
        if (voidCells > 0 && rows.size() < TerminalMonitorPayload.MAX_ROWS) {
            if (voidUnrestricted) {
                rows.add(new MonitorRow(
                        RowKind.VOID_OVERFLOW, "", "", true, "", 0, voidCells, true, false));
            } else {
                for (SubstanceKey key : voidFilteredKeys) {
                    if (rows.size() >= TerminalMonitorPayload.MAX_ROWS) break;
                    long stock = inventory == null ? 0L : inventory.amount(key);
                    rows.add(new MonitorRow(
                            RowKind.VOID_OVERFLOW,
                            key.id().toString(),
                            key.phase().name().toLowerCase(Locale.ROOT),
                            chemistry.isKnown(key),
                            "",
                            stock,
                            voidCells,
                            true,
                            false));
                }
            }
        }

        return new TerminalMonitorPayload(
                containerId,
                netView.nodes(),
                drives,
                finiteCells,
                voidCells,
                voidUnrestricted,
                activeWorkers,
                totalWorkers,
                activeEmitters,
                totalEmitters,
                netView.used(),
                netView.capacity(),
                rows);
    }
}
