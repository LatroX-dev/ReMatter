package dev.latrox.rematter.content;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** Keeps cached automation handles inert when their machine is disabled, unloaded or replaced. */
public final class AutomationResourceHandler<T extends Resource> implements ResourceHandler<T> {
    private final ResourceHandler<T> delegate;
    private final BooleanSupplier input;
    private final BooleanSupplier output;
    private final int slots;
    private final T empty;

    AutomationResourceHandler(ResourceHandler<T> delegate, BooleanSupplier enabled, int slots, T empty) {
        this(delegate, enabled, enabled, slots, empty);
    }

    public AutomationResourceHandler(ResourceHandler<T> delegate, BooleanSupplier input,
                                     BooleanSupplier output, int slots, T empty) {
        this.delegate = delegate;
        this.input = input;
        this.output = output;
        this.slots = slots;
        this.empty = empty;
    }

    private boolean enabled() { return input.getAsBoolean() || output.getAsBoolean(); }

    @Override public int size() { return slots; }
    @Override public T getResource(int index) {
        Objects.checkIndex(index, slots);
        return enabled() ? delegate.getResource(index) : empty;
    }
    @Override public long getAmountAsLong(int index) {
        Objects.checkIndex(index, slots);
        return enabled() ? delegate.getAmountAsLong(index) : 0;
    }
    @Override public long getCapacityAsLong(int index, T resource) {
        Objects.checkIndex(index, slots);
        return enabled() ? delegate.getCapacityAsLong(index, resource) : 0;
    }
    @Override public boolean isValid(int index, T resource) {
        Objects.checkIndex(index, slots);
        return enabled() && delegate.isValid(index, resource);
    }
    @Override public int insert(int index, T resource, int amount, TransactionContext transaction) {
        Objects.checkIndex(index, slots);
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return input.getAsBoolean() ? delegate.insert(index, resource, amount, transaction) : 0;
    }
    @Override public int extract(int index, T resource, int amount, TransactionContext transaction) {
        Objects.checkIndex(index, slots);
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return output.getAsBoolean() ? delegate.extract(index, resource, amount, transaction) : 0;
    }
}
