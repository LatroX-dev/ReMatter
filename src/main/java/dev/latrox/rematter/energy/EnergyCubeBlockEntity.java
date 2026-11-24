package dev.latrox.rematter.energy;

import dev.latrox.rematter.content.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.*;
import net.neoforged.neoforge.transfer.item.*;
import net.neoforged.neoforge.transfer.transaction.*;
import org.jspecify.annotations.Nullable;

/** A server-owned FE buffer with cross-mod Charge and Discharge item bays. */
public final class EnergyCubeBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    private int bidirectionalPushDepth;
    private final EnergyCubeTier tier;
    private final NonNullList<ItemStack> items;
    private final ResourceHandler<ItemResource> itemHandler;
    private final SimpleEnergyHandler energy;
    private final EnergyCubeBudget inputBudget, outputBudget;
    private final EnergyCubeFlow flow = new EnergyCubeFlow();
    private final long[] lastDirectInputTick = {-1, -1, -1, -1, -1, -1};
    private final DirectInputJournal[] directInputJournals = new DirectInputJournal[Direction.values().length];
    private @Nullable UUID owner;
    private @Nullable CompoundTag unsupportedData;
    // FRONT is ordinal zero. Every other face starts as an input.
    private int sideModes = EnergyCubeSideMode.OUTPUT.ordinal();

    private final class DirectInputJournal extends SnapshotJournal<Long> {
        private final int sideIndex;
        private long pending = -1;
        private DirectInputJournal(int sideIndex) { this.sideIndex = sideIndex; }
        private void mark(long tick, TransactionContext tx) { updateSnapshots(tx); pending = tick; }
        @Override protected Long createSnapshot() { return pending; }
        @Override protected void revertToSnapshot(Long snapshot) { pending = snapshot; }
        @Override protected void onRootCommit(Long originalState) {
            if (pending >= 0) { lastDirectInputTick[sideIndex] = pending; pending = -1; }
        }
    }

    public EnergyCubeBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.ENERGY_CUBE_ENTITY.get(), pos, state);
        tier = ((EnergyCubeBlock) state.getBlock()).tier();
        items = NonNullList.withSize(tier.totalSlots(), ItemStack.EMPTY);
        inputBudget = new EnergyCubeBudget(tier.transferRate());
        outputBudget = new EnergyCubeBudget(tier.transferRate());
        for (var dir : Direction.values()) directInputJournals[dir.ordinal()] = new DirectInputJournal(dir.ordinal());
        energy = new SimpleEnergyHandler(tier.capacity(), tier.capacity(), tier.capacity()) {
            @Override protected void onEnergyChanged(int previous) { setChanged(); publishCharge(); }
        };
        // The native stack handler copies its constructor list. A Container wrapper keeps one authoritative
        // inventory and journals component changes together with charging, discharging, pickup and automation.
        itemHandler = VanillaContainerWrapper.of(this);
    }

    public EnergyCubeTier tier() { return tier; }
    public int energy() { return (int) energy.getAmountAsLong(); }
    public boolean locked() { return unsupportedData != null; }
    public @Nullable UUID owner() { return owner; }
    private SecurityMode securityMode = SecurityMode.PUBLIC;
    public SecurityMode securityMode() { return securityMode; }
    public boolean canManageSecurity(Player player) { return MachineAccess.canManage(owner, player); }
    public boolean setSecurityMode(Player player, SecurityMode next) {
        if (!live() || player.isSpectator() || !canManageSecurity(player) || next == null) return false;
        if (securityMode == next) return true;
        securityMode = next;
        setChanged();
        touchTopology();
        return true;
    }
    public boolean cycleSecurityMode(Player player, boolean forward) {
        return setSecurityMode(player, forward ? securityMode.next() : securityMode.previous());
    }
    public int packedSideModes() { return sideModes; }
    public EnergyCubeSideMode sideMode(MachinePortMode.Side side) { return EnergyCubeSideMode.values()[(sideModes >>> (side.ordinal() * 2)) & 3]; }
    public EnergyCubeSideMode sideMode(Direction side) { return sideMode(local(side)); }

    public boolean isChargeSlot(int slot) { return slot >= 0 && slot < tier.chargingSlots(); }
    public boolean isDischargeSlot(int slot) { return slot >= tier.chargingSlots() && slot < tier.totalSlots(); }

    private MachinePortMode.Side local(Direction side) {
        var front = getBlockState().getValue(EnergyCubeBlock.FACING);
        for (var candidate : MachinePortMode.Side.values()) if (candidate.world(front) == side) return candidate;
        throw new IllegalArgumentException("Unknown cube face");
    }

    private boolean installed() {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread() || isRemoved()) return false;
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return chunk != null && chunk.getBlockEntity(worldPosition) == this;
    }

    private boolean live() { return installed() && !locked() && owner != null; }

    public boolean canRemove(Player player) {
        return canAccess(player);
    }

    public boolean canAccess(Player player) {
        return MachineAccess.allows(level, owner, securityMode, player);
    }

    public void placedBy(@Nullable LivingEntity placer) {
        if (securityMode == SecurityMode.PUBLIC && !locked()) {
            securityMode = MachineAccess.placementDefault(level, placer);
        }
        assignOwner(placer instanceof Player player ? player.getUUID() : null);
        publishCharge();
    }

    public boolean interact(Player player) {
        if (owner == null && !(player instanceof FakePlayer) && !player.isSpectator()) assignOwner(player.getUUID());
        return canAccess(player);
    }

    private void assignOwner(@Nullable UUID next) {
        if (Objects.equals(owner, next)) return;
        owner = next;
        setChanged();
        touchTopology();
    }

    public boolean cycleSide(Player player, MachinePortMode.Side side) { return cycleSide(player, side, true); }

    public boolean cycleSide(Player player, MachinePortMode.Side side, boolean forward) {
        if (!live() || player.isSpectator() || !canAccess(player) || !Container.stillValidBlockEntity(this, player) || !PortSides.energyCube(side)) return false;
        var next = forward ? sideMode(side).next() : sideMode(side).previous();
        int shift = side.ordinal() * 2;
        sideModes = (sideModes & ~(3 << shift)) | next.ordinal() << shift;
        setChanged();
        touchTopology();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        return true;
    }

    public boolean applyCopiedSides(Player player, int packed) {
        if (!live() || player.isSpectator() || !canAccess(player) || !Container.stillValidBlockEntity(this, player) || !EnergyCubeData.validModes(packed)) return false;
        if (sideModes == packed) return true;
        sideModes = packed;
        setChanged();
        touchTopology();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        return true;
    }

    public void afterRotation(ServerLevel server) {
        setChanged();
        touchTopology();
        server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putInt("side_modes", sideModes);
        return tag;
    }

    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public void handleUpdateTag(ValueInput input) {
        int shown = sideModes;
        sideModes = input.getIntOr("side_modes", shown);
        if (sideModes != shown && level != null && level.isClientSide()) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_IMMEDIATE);
        }
    }

    @Override public void onDataPacket(net.minecraft.network.Connection connection, ValueInput input) { handleUpdateTag(input); }

    /** Direction selects only the seat's appearance; the server's side modes still authorize every transfer. */
    @Override public net.neoforged.neoforge.model.data.ModelData getModelData() {
        return net.neoforged.neoforge.model.data.ModelData.of(PortSides.PLATES, PortSides.packAppearances(side -> switch (sideMode(side)) {
            case INPUT -> PortAppearance.ENERGY_INPUT;
            case OUTPUT -> PortAppearance.ENERGY_OUTPUT;
            case INPUT_OUTPUT -> PortAppearance.ENERGY_INPUT_OUTPUT;
            case DISABLED -> PortAppearance.DISABLED;
        }));
    }

    private void touchTopology() {
        if (level instanceof ServerLevel server) {
            ChunkRevisions.touchLoaded(server, worldPosition);
            server.invalidateCapabilities(worldPosition);
            MatterCableBlock.refreshLoadedAround(server, worldPosition);
        }
    }

    private boolean isDirectNeighborPush(Direction side) {
        if (!(level instanceof ServerLevel server)) return false;
        BlockPos neighbor = worldPosition.relative(side);
        var chunk = server.getChunkSource().getChunkNow(neighbor.getX() >> 4, neighbor.getZ() >> 4);
        if (chunk == null) return false;
        return !(chunk.getBlockState(neighbor).getBlock() instanceof MatterCableBlock)
                && !(chunk.getBlockEntity(neighbor) instanceof dev.latrox.rematter.primal.EntangledLinkBlockEntity);
    }

    public @Nullable EnergyHandler sidedEnergy(@Nullable Direction side) {
        if (side == null || sideMode(side) == EnergyCubeSideMode.DISABLED || !(level instanceof ServerLevel)) return null;
        return new EnergyHandler() {
            public long getAmountAsLong() { return live() && sideMode(side) != EnergyCubeSideMode.DISABLED ? energy.getAmountAsLong() : 0; }
            public long getCapacityAsLong() { return live() && sideMode(side) != EnergyCubeSideMode.DISABLED ? tier.capacity() : 0; }
            public int insert(int amount, TransactionContext tx) {
                TransferPreconditions.checkNonNegative(amount);
                var mode = sideMode(side);
                if (!live() || !mode.allowsInput()) return 0;
                if (mode == EnergyCubeSideMode.INPUT_OUTPUT && bidirectionalPushDepth > 0) return 0;
                int moved = inputBudget.transfer(level.getGameTime(), amount, tx, n -> energy.insert(n, tx));
                if (moved > 0 && mode == EnergyCubeSideMode.INPUT_OUTPUT && isDirectNeighborPush(side))
                    directInputJournals[side.ordinal()].mark(level.getGameTime(), tx);
                return moved;
            }
            public int extract(int amount, TransactionContext tx) {
                TransferPreconditions.checkNonNegative(amount);
                return live() && sideMode(side).allowsOutput() ? drain(amount, tx) : 0;
            }
        };
    }

    private int accept(int amount, TransactionContext tx) {
        return live() ? inputBudget.transfer(level.getGameTime(), amount, tx, n -> energy.insert(n, tx)) : 0;
    }

    private int drain(int amount, TransactionContext tx) {
        return live() ? outputBudget.transfer(level.getGameTime(), amount, tx, n -> energy.extract(n, tx)) : 0;
    }

    private final EnergyHandler dischargingSink = new EnergyHandler() {
        public long getAmountAsLong() { return energy(); }
        public long getCapacityAsLong() { return tier.capacity(); }
        public int insert(int amount, TransactionContext tx) { TransferPreconditions.checkNonNegative(amount); return accept(amount, tx); }
        public int extract(int amount, TransactionContext tx) { TransferPreconditions.checkNonNegative(amount); return 0; }
    };

    private final EnergyHandler chargingSource = new EnergyHandler() {
        public long getAmountAsLong() { return energy(); }
        public long getCapacityAsLong() { return tier.capacity(); }
        public int insert(int amount, TransactionContext tx) { TransferPreconditions.checkNonNegative(amount); return 0; }
        public int extract(int amount, TransactionContext tx) { TransferPreconditions.checkNonNegative(amount); return drain(amount, tx); }
    };

    /** Automation follows face roles: Charge slots fill depleted items; Discharge slots drain charged batteries. */
    public @Nullable ResourceHandler<ItemResource> sidedItems(@Nullable Direction side) {
        if (side == null || sideMode(side) == EnergyCubeSideMode.DISABLED || !(level instanceof ServerLevel)) return null;
        return new ResourceHandler<>() {
            private boolean available() { return live() && sideMode(side) != EnergyCubeSideMode.DISABLED; }
            public int size() { return itemHandler.size(); }
            public ItemResource getResource(int index) { return available() ? itemHandler.getResource(index) : ItemResource.EMPTY; }
            public long getAmountAsLong(int index) { return available() ? itemHandler.getAmountAsLong(index) : 0; }
            public long getCapacityAsLong(int index, ItemResource resource) { return available() ? 1 : 0; }
            public boolean isValid(int index, ItemResource resource) {
                if (!available() || !sideMode(side).allowsInput() || !itemHandler.isValid(index, resource)) return false;
                ItemStack candidate = resource.toStack();
                if (isDischargeSlot(index)) return canDischargeStack(candidate);
                return sideMode(side) != EnergyCubeSideMode.INPUT_OUTPUT || !fullyChargedStack(candidate);
            }
            public int insert(int index, ItemResource resource, int amount, TransactionContext tx) {
                TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
                return isValid(index, resource) ? itemHandler.insert(index, resource, amount, tx) : 0;
            }
            public int extract(int index, ItemResource resource, int amount, TransactionContext tx) {
                TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
                if (!available() || !sideMode(side).allowsOutput()) return 0;
                boolean readyToLeave = isDischargeSlot(index) ? depletedForDischarge(index) : fullyCharged(index);
                return readyToLeave ? itemHandler.extract(index, resource, amount, tx) : 0;
            }
        };
    }

    public static boolean chargeable(ItemStack stack) {
        return !stack.isEmpty() && ItemAccess.forStack(stack.copyWithCount(1)).getCapability(Capabilities.Energy.ITEM) != null;
    }

    public static boolean dischargeable(ItemStack stack) {
        return chargeable(stack);
    }

    public static boolean fullyChargedStack(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var handler = ItemAccess.forStack(stack.copyWithCount(1)).getCapability(Capabilities.Energy.ITEM);
        return handler != null && handler.getCapacityAsLong() > 0 && handler.getAmountAsLong() >= handler.getCapacityAsLong();
    }

    public static boolean canDischargeStack(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var handler = ItemAccess.forStack(stack.copyWithCount(1)).getCapability(Capabilities.Energy.ITEM);
        if (handler == null || handler.getAmountAsLong() <= 0) return false;
        try (var sim = Transaction.open(Transaction.getCurrentOpenedTransaction())) {
            return handler.extract(1, sim) > 0;
        }
    }

    private boolean fullyCharged(int index) {
        var handler = ItemAccess.forHandlerIndexStrict(itemHandler, index).getCapability(Capabilities.Energy.ITEM);
        return handler != null && handler.getCapacityAsLong() > 0 && handler.getAmountAsLong() >= handler.getCapacityAsLong();
    }

    private boolean depletedForDischarge(int index) {
        var handler = ItemAccess.forHandlerIndexStrict(itemHandler, index).getCapability(Capabilities.Energy.ITEM);
        if (handler == null || handler.getAmountAsLong() <= 0) return true;
        try (var sim = Transaction.open(Transaction.getCurrentOpenedTransaction())) {
            return handler.extract(1, sim) == 0;
        }
    }

    public void serverTick() {
        if (!live()) return;
        long gameTime = level.getGameTime();

        // 1. Drain items in Discharge Slots into the Cube buffer using the shared input allowance.
        int dischargeCount = tier.dischargingSlots();
        int dischargeOffset = tier.chargingSlots();
        if (energy() < tier.capacity() && dischargeCount > 0) {
            int start = Math.floorMod(gameTime, dischargeCount);
            var pendingDischarge = new ArrayList<Integer>();
            for (int i = 0; i < dischargeCount; i++) {
                int slot = dischargeOffset + (start + i) % dischargeCount;
                if (items.get(slot).isEmpty()) continue;
                var source = ItemAccess.forHandlerIndexStrict(itemHandler, slot).getCapability(Capabilities.Energy.ITEM);
                if (source != null && source.getAmountAsLong() > 0) pendingDischarge.add(slot);
            }
            for (int i = 0; i < pendingDischarge.size() && energy() < tier.capacity() && inputBudget.remaining(gameTime) > 0; i++) {
                var source = ItemAccess.forHandlerIndexStrict(itemHandler, pendingDischarge.get(i)).getCapability(Capabilities.Energy.ITEM);
                int offer = inputBudget.remaining(gameTime) / (pendingDischarge.size() - i);
                if (source != null && offer > 0) {
                    try (var tx = Transaction.openRoot()) {
                        if (EnergyHandlerUtil.move(source, dischargingSink, offer, tx) > 0) tx.commit();
                    }
                }
            }
        }

        // 2. Push power out to neighbors first; item charging shares the remaining output allowance.
        if (energy() > 0) for (var side : Direction.values()) {
            var mode = sideMode(side);
            if (!mode.allowsOutput()) continue;
            if (mode == EnergyCubeSideMode.INPUT_OUTPUT && lastDirectInputTick[side.ordinal()] >= 0
                    && gameTime - lastDirectInputTick[side.ordinal()] <= 1) continue;
            BlockPos target = worldPosition.relative(side);
            if (level.getChunkSource().getChunkNow(target.getX() >> 4, target.getZ() >> 4) == null) continue;
            if (level.getBlockEntity(target) instanceof EnergyCubeBlockEntity cube
                    && !MachineAccess.canConnect(level, owner, securityMode, cube.owner, cube.securityMode)) continue;
            var sink = level.getCapability(Capabilities.Energy.BLOCK, target, side.getOpposite());
            if (sink != null) {
                if (mode == EnergyCubeSideMode.INPUT_OUTPUT) bidirectionalPushDepth++;
                try (var tx = Transaction.openRoot()) {
                    if (EnergyHandlerUtil.move(chargingSource, sink, tier.transferRate(), tx) > 0) tx.commit();
                } finally {
                    if (mode == EnergyCubeSideMode.INPUT_OUTPUT) bidirectionalPushDepth--;
                }
            }
        }

        // 3. Charge items in Charge Slots from the remaining output allowance.
        int count = tier.chargingSlots(), start = Math.floorMod(gameTime, count);
        var pending = new ArrayList<Integer>();
        for (int i = 0; i < count; i++) {
            int slot = (start + i) % count;
            if (items.get(slot).isEmpty()) continue;
            var sink = ItemAccess.forHandlerIndexStrict(itemHandler, slot).getCapability(Capabilities.Energy.ITEM);
            if (sink != null && sink.getAmountAsLong() < sink.getCapacityAsLong()) pending.add(slot);
        }
        for (int i = 0; i < pending.size() && energy() > 0 && outputBudget.remaining(gameTime) > 0; i++) {
            var sink = ItemAccess.forHandlerIndexStrict(itemHandler, pending.get(i)).getCapability(Capabilities.Energy.ITEM);
            int offer = outputBudget.remaining(gameTime) / (pending.size() - i);
            if (sink != null && offer > 0) {
                try (var tx = Transaction.openRoot()) {
                    if (EnergyHandlerUtil.move(chargingSource, sink, offer, tx) > 0) tx.commit();
                }
            }
        }

        publishCharge();
        var shownFlow = flow.sample(gameTime, energy());
        if (getBlockState().getValue(EnergyCubeBlock.FLOW) != shownFlow)
            level.setBlock(worldPosition, getBlockState().setValue(EnergyCubeBlock.FLOW, shownFlow), 2);
    }

    private void publishCharge() {
        if (!installed()) return;
        int current = energy();
        int charge = locked() || current <= 0 ? 0 : Math.min(8, 1 + (int) (7L * current / tier.capacity()));
        if (getBlockState().getValue(EnergyCubeBlock.CHARGE) != charge)
            level.setBlock(worldPosition, getBlockState().setValue(EnergyCubeBlock.CHARGE, charge), 2);
        level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
    }

    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {}

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (locked()) { output.store(EnergyCubeData.RAW, unsupportedData); return; }
        output.putInt("schema", EnergyCubeData.SCHEMA);
        output.putInt("energy", energy());
        output.putInt("side_modes", sideModes);
        if (owner != null) output.putString("owner", owner.toString());
        if (securityMode != SecurityMode.PUBLIC) output.putString("security", securityMode.getSerializedName());
        int chargeCount = tier.chargingSlots();
        output.store("items", ItemStack.OPTIONAL_CODEC.listOf(), List.copyOf(items.subList(0, chargeCount)));
        var dischargeSubList = items.subList(chargeCount, tier.totalSlots());
        if (dischargeSubList.stream().anyMatch(stack -> !stack.isEmpty())) {
            output.store("discharge_items", ItemStack.OPTIONAL_CODEC.listOf(), List.copyOf(dischargeSubList));
        }
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        var raw = input.read(EnergyCubeData.RAW).orElseGet(CompoundTag::new);
        var decodedCharge = EnergyCubeData.items(raw, input.lookup());
        var decodedDischarge = EnergyCubeData.dischargeItems(raw, input.lookup());
        boolean hasDischargeTag = raw.contains("discharge_items");
        String rejection = EnergyCubeData.rejection(
                raw, tier,
                decodedCharge.isPresent(), decodedCharge.map(List::size).orElse(-1),
                decodedDischarge.isPresent(), hasDischargeTag ? decodedDischarge.map(List::size).orElse(-1) : tier.dischargingSlots());
        unsupportedData = rejection == null ? null : raw;
        owner = raw.getString("owner").map(EnergyCubeData::owner).orElse(null);
        securityMode = SecurityMode.PUBLIC;
        if (!locked()) {
            securityMode = raw.getString("security").filter(SecurityMode::validName).map(SecurityMode::load).orElse(SecurityMode.PUBLIC);
            energy.set(raw.getIntOr("energy", 0));
            sideModes = raw.getIntOr("side_modes", 0);
            items.replaceAll(stack -> ItemStack.EMPTY);
            var chargeStacks = decodedCharge.orElseThrow();
            for (int i = 0; i < chargeStacks.size(); i++) items.set(i, chargeStacks.get(i));
            var dischargeStacks = decodedDischarge.orElse(List.of());
            for (int i = 0; i < dischargeStacks.size(); i++) items.set(tier.chargingSlots() + i, dischargeStacks.get(i));
        } else {
            energy.set(0);
            items.replaceAll(stack -> ItemStack.EMPTY);
        }
        if (level instanceof ServerLevel) touchTopology();
    }

    public boolean hasStoredContents() {
        return locked() || energy() > 0 || items.stream().anyMatch(stack -> !stack.isEmpty());
    }

    @Override public void setRemoved() {
        if (level instanceof ServerLevel server) ChunkRevisions.touchLoaded(server, worldPosition);
        super.setRemoved();
    }

    @Override public Component getDisplayName() { return Component.translatable("block.rematter." + tier.id()); }

    @Override public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return canAccess(player) ? new EnergyCubeMenu(id, inventory, this) : null;
    }

    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }

    @Override public ItemStack removeItem(int slot, int amount) {
        if (locked() || Transaction.getCurrentOpenedTransaction() != null) return ItemStack.EMPTY;
        var result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override public ItemStack removeItemNoUpdate(int slot) {
        return locked() || Transaction.getCurrentOpenedTransaction() != null ? ItemStack.EMPTY : ContainerHelper.takeItem(items, slot);
    }

    @Override public void setItem(int slot, ItemStack stack) {
        if (locked() || Transaction.getCurrentOpenedTransaction() != null) return;
        if (!stack.isEmpty() && (stack.getCount() != 1 || !canPlaceItem(slot, stack))) return;
        items.set(slot, stack);
        setChanged();
    }

    @Override public void setItem(int slot, ItemStack stack, boolean insideTransaction) {
        if (insideTransaction && !locked()) items.set(slot, stack);
        else setItem(slot, stack);
    }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (locked() || slot < 0 || slot >= items.size()) return false;
        return isDischargeSlot(slot) ? dischargeable(stack) : chargeable(stack);
    }

    /** Vanilla hoppers reach WorldlyContainer directly, without the NeoForge capability gate. */
    @Override public int[] getSlotsForFace(Direction side) {
        if (!live() || sideMode(side) == EnergyCubeSideMode.DISABLED) return new int[0];
        return java.util.stream.IntStream.range(0, items.size()).toArray();
    }

    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (!live() || !sideMode(side).allowsInput() || !canPlaceItem(slot, stack)) return false;
        if (isDischargeSlot(slot)) return canDischargeStack(stack);
        return sideMode(side) != EnergyCubeSideMode.INPUT_OUTPUT || !fullyChargedStack(stack);
    }

    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (!live() || !sideMode(side).allowsOutput()) return false;
        return isDischargeSlot(slot) ? depletedForDischarge(slot) : fullyCharged(slot);
    }

    @Override public int getMaxStackSize() { return 1; }

    @Override public void clearContent() {
        if (!locked() && Transaction.getCurrentOpenedTransaction() == null) {
            items.replaceAll(stack -> ItemStack.EMPTY);
            setChanged();
        }
    }

    @Override public boolean stillValid(Player player) {
        return canAccess(player) && Container.stillValidBlockEntity(this, player);
    }
}
