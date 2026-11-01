package dev.latrox.rematter.content;

import java.util.function.BooleanSupplier;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** A guarded view, with all snapshots and rollback kept in the controller's single energy handler. */
final class PortEnergyHandler implements EnergyHandler {
    private final EnergyHandler delegate;
    private final BooleanSupplier attached;
    PortEnergyHandler(EnergyHandler delegate, BooleanSupplier attached) { this.delegate = delegate; this.attached = attached; }
    @Override public long getAmountAsLong() { return attached.getAsBoolean() ? delegate.getAmountAsLong() : 0; }
    @Override public long getCapacityAsLong() { return attached.getAsBoolean() ? delegate.getCapacityAsLong() : 0; }
    @Override public int insert(int amount, TransactionContext tx) {
        TransferPreconditions.checkNonNegative(amount);
        return attached.getAsBoolean() ? delegate.insert(amount, tx) : 0;
    }
    @Override public int extract(int amount, TransactionContext tx) {
        TransferPreconditions.checkNonNegative(amount);
        return attached.getAsBoolean() ? delegate.extract(amount, tx) : 0;
    }
}
