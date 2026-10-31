package dev.latrox.rematter.compat.mekanism;

import mekanism.api.chemical.ChemicalResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.List;

/** Direct adapter for the canonical NeoForge chemical capability used by the 2.11 port. */
final class MekanismChemicalHandler implements ResourceHandler<ChemicalResource> {
    private final ChemicalTransfers transfers;
    private final List<ChemicalResource> resources;

    MekanismChemicalHandler(ChemicalTransfers transfers, List<ChemicalResource> resources) {
        this.transfers = transfers;
        this.resources = List.copyOf(resources);
        if (this.resources.size() != transfers.size() || this.resources.stream().anyMatch(ChemicalResource::isEmpty)) {
            throw new IllegalArgumentException("Every mapped chemical must be registered");
        }
    }

    @Override
    public int size() {
        return transfers.size();
    }

    @Override
    public ChemicalResource getResource(int index) {
        return getAmountAsLong(index) > 0 ? resources.get(index) : ChemicalResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        return resources.get(index).isRadioactive() ? 0 : transfers.amount(index);
    }

    @Override
    public long getCapacityAsLong(int index, ChemicalResource resource) {
        if (resources.get(index).isRadioactive()) return 0;
        if (resource.isEmpty()) return transfers.capacity(index);
        return isValid(index, resource) ? transfers.capacity(index) : 0;
    }

    @Override
    public boolean isValid(int index, ChemicalResource resource) {
        return !resource.isRadioactive() && resources.get(index).equals(resource) && transfers.isEnabled(index);
    }

    @Override
    public int insert(int index, ChemicalResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (!isValid(index, resource)) return 0;
        return transfers.insert(index, transfers.chemicalId(index), amount, transaction);
    }

    @Override
    public int extract(int index, ChemicalResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (!isValid(index, resource)) return 0;
        return transfers.extract(index, transfers.chemicalId(index), amount, transaction);
    }
}
