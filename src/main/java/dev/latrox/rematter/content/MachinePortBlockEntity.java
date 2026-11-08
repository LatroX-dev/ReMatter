package dev.latrox.rematter.content;

import java.lang.ref.WeakReference;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/** No owned inventory, tank or energy: every operation reaches one live, uniquely associated controller. */
public final class MachinePortBlockEntity extends BlockEntity {
    private MachinePortMode mode = MachinePortMode.AUTOMATIC;
    private MachineBlockEntity observed;
    private boolean observedAutomation;
    private @Nullable Direction observedOutward;
    private long nextCheck;
    public MachinePortBlockEntity(BlockPos pos, BlockState state) { super(ModContent.MACHINE_PORT_ENTITY.get(), pos, state); }
    public MachinePortMode mode() { return mode; }
    boolean acceptsMatter() { return mode.allows(MachinePortMode.MATTER); }
    boolean cycleMode(Player player, Direction side) {
        var target = controller(side);
        if (target == null || !target.canAccess(player) || !Container.stillValidBlockEntity(this, player)) return false;
        var next = mode.next(target.kind(), true);
        if (mode == next) return false;
        mode = next;
        setChanged();
        nextCheck = level.getGameTime();
        serverTick();
        return true;
    }
    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (mode != MachinePortMode.AUTOMATIC) output.putString("port_mode", mode.getSerializedName());
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        mode = input.getString("port_mode").map(MachinePortMode::load).orElse(MachinePortMode.AUTOMATIC);
    }
    private boolean live() {
        return !isRemoved() && level instanceof ServerLevel server && server.getServer().isSameThread()
                && level.hasChunkAt(worldPosition) && level.getBlockEntity(worldPosition) == this;
    }
    /** Null side is a graph lookup only; capability providers require an actual exterior side. */
    public @Nullable MachineBlockEntity controller(@Nullable Direction side) {
        if (!live()) return null;
        var candidate = IndustrialStructure.associatedController(this);
        if (candidate == null) return null;
        var formation = IndustrialStructure.inspect(candidate);
        if (!formation.formed()) return null;
        return formation.ports().stream().anyMatch(port -> port.position().equals(worldPosition)
                && (side == null || side == port.outward())) ? candidate : null;
    }
    public @Nullable Direction outward() {
        var controller = controller(null);
        if (controller == null) return null;
        return IndustrialStructure.inspect(controller).ports().stream().filter(port -> port.position().equals(worldPosition))
                .map(IndustrialStructure.Port::outward).findFirst().orElse(null);
    }
    private boolean attached(MachineBlockEntity expected, Direction side) {
        if (!live() || expected.getLevel() != level) return false;
        var formation = IndustrialStructure.inspect(expected);
        return formation.formed() && formation.ports().stream().anyMatch(port -> port.position().equals(worldPosition) && port.outward() == side);
    }
    public void serverTick() {
        if (!live() || level.getGameTime() < nextCheck) return;
        nextCheck = level.getGameTime() + 5;
        var current = controller(null);
        Direction outward = current == null ? null : IndustrialStructure.inspect(current).ports().stream()
                .filter(port -> port.position().equals(worldPosition)).map(IndustrialStructure.Port::outward).findFirst().orElse(null);
        boolean automation = current != null && current.automation();
        boolean associationChanged = current != observed || automation != observedAutomation || outward != observedOutward;
        if (associationChanged) {
            observed = current; observedAutomation = automation; observedOutward = outward;
        }
        BlockState state = getBlockState();
        BlockState visible = state.setValue(MachinePortBlock.FORMED,outward != null).setValue(MachinePortBlock.MODE, mode);
        if (outward != null) visible = visible.setValue(MachinePortBlock.FACING,outward);
        if (visible != state) {
            // This mirror carries no permissions. Avoid loading a neighboring chunk just to repair a cable arm.
            level.setBlock(worldPosition,visible,Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        if (associationChanged || visible != state) {
            level.invalidateCapabilities(worldPosition);
            MatterPipeBlock.refreshLoadedAround((ServerLevel)level,worldPosition);
        }
    }
    public @Nullable EnergyHandler energy(@Nullable Direction side) {
        if (side == null || !mode.allows(MachinePortMode.POWER)) return null;
        var target = controller(side);
        if (target == null) return null;
        var reference = new WeakReference<>(target);
        return new PortEnergyHandler(target.energy(), () -> {
            var expected = reference.get();
            return expected != null && mode.allows(MachinePortMode.POWER) && attached(expected, side);
        });
    }
    public @Nullable ResourceHandler<ItemResource> items(@Nullable Direction side) {
        if (side == null) return null;
        var target = controller(side);
        return target == null || !target.automation()
                || !mode.allows(MachinePortMode.ITEM_INPUT) && !mode.allows(MachinePortMode.ITEM_OUTPUT) ? null
                : new GuardedResources<>(target, side, target.industrialItems(), ItemResource.EMPTY,
                        MachinePortMode.ITEM_INPUT, MachinePortMode.ITEM_OUTPUT);
    }
    public @Nullable ResourceHandler<FluidResource> fluids(@Nullable Direction side) {
        if (side == null) return null;
        var target = controller(side);
        // Neither industrial recipe type currently accepts new fluids. Preserve a drain path for legacy contents.
        return target == null || !target.automation() || !mode.allows(MachinePortMode.FLUID_OUTPUT)
                || target.fluids().getAmountAsLong(0) == 0 ? null
                : new GuardedResources<>(target, side, target.automatedFluids(), FluidResource.EMPTY,
                        null, MachinePortMode.FLUID_OUTPUT);
    }
    private final class GuardedResources<T extends Resource> implements ResourceHandler<T> {
        private final WeakReference<MachineBlockEntity> target;
        private final Direction side;
        private final ResourceHandler<T> delegate;
        private final T empty;
        private final int size;
        private final @Nullable MachinePortMode input;
        private final MachinePortMode output;
        GuardedResources(MachineBlockEntity machine, Direction side, ResourceHandler<T> delegate, T empty,
                         @Nullable MachinePortMode input, MachinePortMode output) {
            this.target = new WeakReference<>(machine); this.side = side; this.delegate = delegate; this.empty = empty; this.size = delegate.size();
            this.input = input; this.output = output;
        }
        private boolean allowed(int index) {
            Objects.checkIndex(index, size);
            var expected = target.get();
            return expected != null && expected.automation() && attached(expected, side)
                    && (mode.allows(output) || input != null && mode.allows(input));
        }
        @Override public int size() { return size; }
        @Override public T getResource(int index) { return allowed(index) ? delegate.getResource(index) : empty; }
        @Override public long getAmountAsLong(int index) { return allowed(index) ? delegate.getAmountAsLong(index) : 0; }
        @Override public long getCapacityAsLong(int index, T resource) { return allowed(index) ? delegate.getCapacityAsLong(index, resource) : 0; }
        @Override public boolean isValid(int index, T resource) { return allowed(index) && input != null && mode.allows(input) && delegate.isValid(index, resource); }
        @Override public int insert(int index, T resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            return allowed(index) && input != null && mode.allows(input) ? delegate.insert(index, resource, amount, transaction) : 0;
        }
        @Override public int extract(int index, T resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            return allowed(index) && mode.allows(output) ? delegate.extract(index, resource, amount, transaction) : 0;
        }
    }
}
