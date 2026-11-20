package dev.latrox.rematter.content;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Exposes NeoForge {@code Capabilities.Fluid.ITEM} for {@link PressurizedCanisterItem} when empty or in fluid mode.
 * Canisters holding matter or an unknown schema report zero fluid capacity so contents never cross-contaminate or vanish.
 */
public final class PressurizedCanisterFluidHandler implements ResourceHandler<FluidResource> {
    private final ItemAccess access;

    private PressurizedCanisterFluidHandler(ItemAccess access) {
        this.access = access;
    }

    public static @Nullable ResourceHandler<FluidResource> of(ItemStack stack, ItemAccess access) {
        if (!(stack.getItem() instanceof PressurizedCanisterItem)) return null;
        return new PressurizedCanisterFluidHandler(access);
    }

    @Override
    public int size() {
        return 1;
    }

    private @Nullable Fluid resolveFluid(Identifier fluidId) {
        if (!BuiltInRegistries.FLUID.containsKey(fluidId)) return null;
        Fluid fluid = BuiltInRegistries.FLUID.getValue(fluidId);
        return fluid == Fluids.EMPTY ? null : fluid;
    }

    @Override
    public FluidResource getResource(int index) {
        if (index != 0 || access.getAmount() != 1) return FluidResource.EMPTY;
        ItemResource item = access.getResource();
        if (!(item.getItem() instanceof PressurizedCanisterItem)) return FluidResource.EMPTY;
        if (PressurizedCanisterData.read(item) instanceof PressurizedCanisterData.State.Fluid fluidState) {
            Fluid fluid = resolveFluid(fluidState.fluidId());
            return fluid == null ? FluidResource.EMPTY : FluidResource.of(fluid);
        }
        return FluidResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        if (index != 0 || access.getAmount() != 1) return 0;
        ItemResource item = access.getResource();
        if (!(item.getItem() instanceof PressurizedCanisterItem)) return 0;
        if (PressurizedCanisterData.read(item) instanceof PressurizedCanisterData.State.Fluid fluidState) {
            return resolveFluid(fluidState.fluidId()) == null ? 0 : fluidState.amount();
        }
        return 0;
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        if (index != 0 || access.getAmount() != 1) return 0;
        ItemResource item = access.getResource();
        if (!(item.getItem() instanceof PressurizedCanisterItem)) return 0;
        return switch (PressurizedCanisterData.read(item)) {
            case PressurizedCanisterData.State.Empty ignored ->
                    resource.isEmpty() || resource.isComponentsPatchEmpty() ? PressurizedCanisterData.CAPACITY : 0;
            case PressurizedCanisterData.State.Fluid fluidState -> {
                Fluid current = resolveFluid(fluidState.fluidId());
                if (current == null) yield 0;
                yield resource.isEmpty() || FluidResource.of(current).equals(resource)
                        ? PressurizedCanisterData.CAPACITY
                        : 0;
            }
            case PressurizedCanisterData.State.Matter ignored -> 0;
            case PressurizedCanisterData.State.Locked ignored -> 0;
        };
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        if (index != 0 || resource.isEmpty() || !resource.isComponentsPatchEmpty() || access.getAmount() != 1) {
            return false;
        }
        ItemResource item = access.getResource();
        if (!(item.getItem() instanceof PressurizedCanisterItem)) return false;
        Identifier id = BuiltInRegistries.FLUID.getKey(resource.getFluid());
        if (id == null || resource.getFluid() == Fluids.EMPTY) return false;
        return switch (PressurizedCanisterData.read(item)) {
            case PressurizedCanisterData.State.Empty ignored -> true;
            case PressurizedCanisterData.State.Fluid fluidState ->
                    resolveFluid(fluidState.fluidId()) != null && fluidState.fluidId().equals(id);
            case PressurizedCanisterData.State.Matter ignored -> false;
            case PressurizedCanisterData.State.Locked ignored -> false;
        };
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (!isValid(index, resource) || amount == 0) return 0;
        ItemResource item = access.getResource();
        Identifier id = BuiltInRegistries.FLUID.getKey(resource.getFluid());
        if (id == null) return 0;
        int current = switch (PressurizedCanisterData.read(item)) {
            case PressurizedCanisterData.State.Empty ignored -> 0;
            case PressurizedCanisterData.State.Fluid fluidState -> fluidState.amount();
            default -> -1;
        };
        if (current < 0) return 0;
        int accepted = Math.min(amount, PressurizedCanisterData.CAPACITY - current);
        if (accepted <= 0) return 0;
        ItemResource updated = PressurizedCanisterData.writeFluid(item, id, current + accepted);
        return access.exchange(updated, 1, tx) == 1 ? accepted : 0;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (index != 0 || amount == 0 || access.getAmount() != 1) return 0;
        ItemResource item = access.getResource();
        if (!(item.getItem() instanceof PressurizedCanisterItem)) return 0;
        if (!(PressurizedCanisterData.read(item) instanceof PressurizedCanisterData.State.Fluid fluidState)) {
            return 0;
        }
        Fluid storedFluid = resolveFluid(fluidState.fluidId());
        if (storedFluid == null || !FluidResource.of(storedFluid).equals(resource)) return 0;
        int drained = Math.min(amount, fluidState.amount());
        if (drained <= 0) return 0;
        ItemResource updated = PressurizedCanisterData.writeFluid(item, fluidState.fluidId(), fluidState.amount() - drained);
        return access.exchange(updated, 1, tx) == 1 ? drained : 0;
    }
}
