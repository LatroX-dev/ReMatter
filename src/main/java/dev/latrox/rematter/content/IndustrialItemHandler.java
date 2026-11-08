package dev.latrox.rematter.content;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** Maps controller-owned slots, sharing VanillaContainerWrapper's journals across every exterior port. */
final class IndustrialItemHandler implements ResourceHandler<ItemResource> {
    private final ResourceHandler<ItemResource> delegate;
    private final BooleanSupplier allowed;
    private final BooleanSupplier input;
    private final BooleanSupplier output;
    private final int[] slots;
    IndustrialItemHandler(ResourceHandler<ItemResource> delegate, BooleanSupplier allowed, boolean preparation) {
        this(delegate, allowed, allowed, preparation);
    }
    IndustrialItemHandler(ResourceHandler<ItemResource> delegate, BooleanSupplier input, BooleanSupplier output, boolean preparation) {
        this.delegate = delegate;
        this.allowed = () -> input.getAsBoolean() || output.getAsBoolean();
        this.input = input;
        this.output = output;
        this.slots = preparation ? new int[]{0, 3, 4, 1} : new int[]{0, 1};
    }
    private int slot(int index) { return slots[Objects.checkIndex(index, slots.length)]; }
    @Override public int size() { return slots.length; }
    @Override public ItemResource getResource(int index) { int slot = slot(index); return allowed.getAsBoolean() ? delegate.getResource(slot) : ItemResource.EMPTY; }
    @Override public long getAmountAsLong(int index) { int slot = slot(index); return allowed.getAsBoolean() ? delegate.getAmountAsLong(slot) : 0; }
    @Override public long getCapacityAsLong(int index, ItemResource resource) { int slot = slot(index); return allowed.getAsBoolean() ? delegate.getCapacityAsLong(slot, resource) : 0; }
    @Override public boolean isValid(int index, ItemResource resource) { int slot = slot(index); return input.getAsBoolean() && slot != 1 && delegate.isValid(slot, resource); }
    @Override public int insert(int index, ItemResource resource, int amount, TransactionContext tx) {
        int slot = slot(index); TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return input.getAsBoolean() && slot != 1 ? delegate.insert(slot, resource, amount, tx) : 0;
    }
    @Override public int extract(int index, ItemResource resource, int amount, TransactionContext tx) {
        int slot = slot(index); TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return output.getAsBoolean() && slot == 1 ? delegate.extract(slot, resource, amount, tx) : 0;
    }
}
