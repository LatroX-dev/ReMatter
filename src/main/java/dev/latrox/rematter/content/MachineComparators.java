package dev.latrox.rematter.content;

import dev.latrox.rematter.celltransfer.CellTransferStationBlockEntity;
import dev.latrox.rematter.content.preparation.PreparationTier;
import dev.latrox.rematter.energy.EnergyCubeBlockEntity;

/** Analog 0..15 comparator signals for machines, drives, energy cubes and cell transfer stations. */
public final class MachineComparators {
    private MachineComparators() {}

    public static int scale(long amount, long capacity) {
        if (amount <= 0 || capacity <= 0) return 0;
        if (amount >= capacity) return 15;
        return 1 + (int) Math.min(14L, (14L * amount) / capacity);
    }

    public static int machine(MachineBlockEntity machine) {
        if (machine.hasUnsupportedData()) return 0;
        return switch (machine.kind()) {
            case DRIVE -> {
                if (!machine.storageStatus().equals("ready") && machine.drivePrimalCapacity() == 0) yield 0;
                long amount = Math.addExact(
                        machine.storageStatus().equals("ready") ? machine.matterStore().total() : 0L,
                        machine.drivePrimalAmount());
                long capacity = Math.addExact(
                        machine.storageStatus().equals("ready") ? machine.matterStore().capacity() : 0L,
                        machine.drivePrimalCapacity());
                yield scale(amount, capacity);
            }
            case TERMINAL, NETWORK_INTERFACE -> {
                var network = machine.matterNetwork(false);
                if (!network.ready() || network.inventory() == null) yield 0;
                yield scale(network.inventory().total(), network.inventory().capacity());
            }
            default -> {
                if (PreparationTier.manual(machine.kind())) {
                    var work = machine.work();
                    if (work.duration > 0 && work.progress > 0) {
                        yield Math.clamp(1 + (int) ((14L * work.progress) / work.duration), 1, 15);
                    }
                    yield machine.stack(0).isEmpty() && machine.stack(1).isEmpty() ? 0 : 1;
                }
                yield scale(machine.energy().getAmountAsLong(), MachineBlockEntity.ENERGY_CAPACITY);
            }
        };
    }

    public static int energyCube(EnergyCubeBlockEntity cube) {
        return cube.locked() ? 0 : scale(cube.energy(), cube.tier().capacity());
    }

    public static int cellTransferStation(CellTransferStationBlockEntity station) {
        if (station.locked()) return 0;
        var snap = station.snapshot(0);
        if (snap.locked()) return 0;
        long amount = 0, capacity = 0;
        int installed = 0;
        for (var slot : snap.slots()) {
            if (slot.kind() != 0 && slot.readable()) {
                installed++;
                amount = Math.addExact(amount, slot.amount());
                capacity = Math.addExact(capacity, slot.capacity());
            }
        }
        if (installed == 0) return 0;
        int fill = scale(amount, capacity);
        return fill > 0 ? fill : 1;
    }
}
