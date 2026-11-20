package dev.latrox.rematter.compat.mekanism;

import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.content.PressurizedCanisterData;
import dev.latrox.rematter.content.PressurizedCanisterItem;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import mekanism.api.chemical.ChemicalResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Exposes {@code Capabilities.CHEMICAL.item()} for {@link PressurizedCanisterItem} using exact integral
 * {@link ChemicalMappings} ratios and synthesis export permissions.
 */
final class MekanismCanisterHandler implements ResourceHandler<ChemicalResource> {
    private final ItemAccess access;
    private final List<ChemicalResource> resources;
    private final List<ChemicalMappings.Mapping> mappings;
    private final Predicate<SubstanceKey> known;
    private final Predicate<ChemicalMappings.Mapping> exportable;

    MekanismCanisterHandler(
            ItemAccess access,
            List<ChemicalResource> resources,
            List<ChemicalMappings.Mapping> mappings,
            Predicate<SubstanceKey> known,
            Predicate<ChemicalMappings.Mapping> exportable) {
        this.access = Objects.requireNonNull(access);
        this.resources = List.copyOf(resources);
        this.mappings = List.copyOf(mappings);
        this.known = Objects.requireNonNull(known);
        this.exportable = Objects.requireNonNull(exportable);
        if (this.resources.size() != this.mappings.size()) {
            throw new IllegalArgumentException("Mismatched chemical resource mappings");
        }
    }

    @Override
    public int size() {
        return resources.size();
    }

    private boolean slotUsable(int index) {
        if (index < 0 || index >= resources.size() || access.getAmount() != 1) return false;
        if (resources.get(index).isRadioactive()) return false;
        ItemResource item = access.getResource();
        return item.getItem() instanceof PressurizedCanisterItem && known.test(mappings.get(index).matter());
    }

    @Override
    public ChemicalResource getResource(int index) {
        return getAmountAsLong(index) > 0 ? resources.get(index) : ChemicalResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        if (!slotUsable(index)) return 0;
        var mapping = mappings.get(index);
        if (!exportable.test(mapping)) return 0;
        if (PressurizedCanisterData.read(access.getResource()) instanceof PressurizedCanisterData.State.Matter matter
                && matter.key().equals(mapping.matter())) {
            return matter.amount() / mapping.unitsPerChemical();
        }
        return 0;
    }

    @Override
    public long getCapacityAsLong(int index, ChemicalResource resource) {
        if (!slotUsable(index)) return 0;
        if (!resource.isEmpty() && !resources.get(index).equals(resource)) return 0;
        var mapping = mappings.get(index);
        return switch (PressurizedCanisterData.read(access.getResource())) {
            case PressurizedCanisterData.State.Empty ignored ->
                    PressurizedCanisterData.CAPACITY / mapping.unitsPerChemical();
            case PressurizedCanisterData.State.Matter matter ->
                    matter.key().equals(mapping.matter())
                            ? PressurizedCanisterData.CAPACITY / mapping.unitsPerChemical()
                            : 0;
            case PressurizedCanisterData.State.Fluid ignored -> 0;
            case PressurizedCanisterData.State.Locked ignored -> 0;
        };
    }

    @Override
    public boolean isValid(int index, ChemicalResource resource) {
        if (!slotUsable(index) || resource.isEmpty() || !resources.get(index).equals(resource)) return false;
        var mapping = mappings.get(index);
        return switch (PressurizedCanisterData.read(access.getResource())) {
            case PressurizedCanisterData.State.Empty ignored -> true;
            case PressurizedCanisterData.State.Matter matter -> matter.key().equals(mapping.matter());
            case PressurizedCanisterData.State.Fluid ignored -> false;
            case PressurizedCanisterData.State.Locked ignored -> false;
        };
    }

    @Override
    public int insert(int index, ChemicalResource resource, int amount, TransactionContext tx) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (!isValid(index, resource) || amount == 0) return 0;
        ItemResource item = access.getResource();
        var mapping = mappings.get(index);
        int current = switch (PressurizedCanisterData.read(item)) {
            case PressurizedCanisterData.State.Empty ignored -> 0;
            case PressurizedCanisterData.State.Matter matter ->
                    matter.key().equals(mapping.matter()) ? matter.amount() : -1;
            default -> -1;
        };
        if (current < 0) return 0;
        int maxChem = (int) ((PressurizedCanisterData.CAPACITY - current) / mapping.unitsPerChemical());
        int accepted = Math.min(amount, maxChem);
        if (accepted <= 0) return 0;
        int addedUnits = Math.toIntExact(Math.multiplyExact((long) accepted, mapping.unitsPerChemical()));
        ItemResource updated = PressurizedCanisterData.writeMatter(item, mapping.matter(), current + addedUnits);
        return access.exchange(updated, 1, tx) == 1 ? accepted : 0;
    }

    @Override
    public int extract(int index, ChemicalResource resource, int amount, TransactionContext tx) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (!isValid(index, resource) || amount == 0) return 0;
        var mapping = mappings.get(index);
        if (!exportable.test(mapping)) return 0;
        ItemResource item = access.getResource();
        if (!(PressurizedCanisterData.read(item) instanceof PressurizedCanisterData.State.Matter matter)
                || !matter.key().equals(mapping.matter())) {
            return 0;
        }
        int availableChem = (int) (matter.amount() / mapping.unitsPerChemical());
        int extracted = Math.min(amount, availableChem);
        if (extracted <= 0) return 0;
        int removedUnits = Math.toIntExact(Math.multiplyExact((long) extracted, mapping.unitsPerChemical()));
        ItemResource updated = PressurizedCanisterData.writeMatter(item, mapping.matter(), matter.amount() - removedUnits);
        return access.exchange(updated, 1, tx) == 1 ? extracted : 0;
    }
}
