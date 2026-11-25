package dev.latrox.rematter.content;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/** Server-authoritative FE-powered chunkloader supporting 1x1, 3x3, 5x5 presets, custom 5x5 chunk masks, and configurable ports. */
public final class SpatialAnchorBlockEntity extends BlockEntity implements MenuProvider {
    public static final List<MachinePortMode> ALLOWED_PORT_MODES = List.of(
            MachinePortMode.AUTOMATIC, MachinePortMode.POWER, MachinePortMode.REDSTONE, MachinePortMode.DISABLED);
    private static final int DEFAULT_PORT_MODES = PortSides.pack(
            side -> isConfigurableSide(side) ? MachinePortMode.AUTOMATIC : MachinePortMode.DISABLED);
    public static final int CAPACITY = 128_000;
    public static final int MAX_INSERT = 8_192;
    public static final int DRAIN_PER_CHUNK = 16;
    public static final int CENTER_BIT = 12;
    public static final int MASK_1X1 = 1 << CENTER_BIT;
    public static final int MASK_3X3 = buildSquareMask(1);
    public static final int MASK_5X5 = (1 << 25) - 1;
    public static final int VALID_MASK_BITS = MASK_5X5;
    public static final int DRAIN_1X1 = DRAIN_PER_CHUNK;
    public static final int DRAIN_3X3 = 9 * DRAIN_PER_CHUNK;
    public static final int DRAIN_5X5 = 25 * DRAIN_PER_CHUNK;

    private static int buildSquareMask(int radius) {
        int mask = 0;
        for (int gz = 2 - radius; gz <= 2 + radius; gz++) {
            for (int gx = 2 - radius; gx <= 2 + radius; gx++) {
                mask |= 1 << (gz * 5 + gx);
            }
        }
        return mask;
    }

    public static int presetMask(int radius) {
        return switch (Math.clamp(radius, 0, 2)) {
            case 0 -> MASK_1X1;
            case 1 -> MASK_3X3;
            default -> MASK_5X5;
        };
    }

    public static int sanitizeMask(int mask) {
        return (mask & VALID_MASK_BITS) | MASK_1X1;
    }

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, MAX_INSERT, CAPACITY) {
        @Override protected void onEnergyChanged(int previous) { setChanged(); }
    };
    private final EnergyHandler inputOnly = new EnergyHandler() {
        @Override public long getAmountAsLong() { return energy.getAmountAsLong(); }
        @Override public long getCapacityAsLong() { return CAPACITY; }
        @Override public int insert(int amount, TransactionContext tx) {
            TransferPreconditions.checkNonNegative(amount);
            return energy.insert(amount, tx);
        }
        @Override public int extract(int amount, TransactionContext tx) {
            TransferPreconditions.checkNonNegative(amount);
            return 0;
        }
    };

    private @Nullable UUID owner;
    private SecurityMode securityMode = SecurityMode.PUBLIC;
    private String customName = "";
    private int portModes = DEFAULT_PORT_MODES;
    private int chunkMask = MASK_1X1;
    private RedstoneMode redstoneMode = RedstoneMode.IGNORED;
    private ChunkAnchorManager.Status lastStatus = ChunkAnchorManager.Status.IDLE;

    public SpatialAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.SPATIAL_ANCHOR_ENTITY.get(), pos, state);
    }

    public static boolean isConfigurableSide(MachinePortMode.Side side) {
        return side != MachinePortMode.Side.FRONT && side != MachinePortMode.Side.TOP;
    }

    public static MachinePortMode sanitizePortMode(MachinePortMode.Side side, MachinePortMode mode) {
        if (!isConfigurableSide(side) || mode == null) return MachinePortMode.DISABLED;
        return ALLOWED_PORT_MODES.contains(mode) ? mode : MachinePortMode.AUTOMATIC;
    }

    private static int sanitizePackedPorts(int packed) {
        return PortSides.pack(side -> sanitizePortMode(side, PortSides.unpack(packed, side)));
    }

    public MachinePortMode portMode(MachinePortMode.Side side) {
        return sanitizePortMode(side, PortSides.unpack(portModes, side));
    }

    public MachinePortMode portMode(Direction worldSide) {
        BlockState state = getBlockState();
        if (!state.hasProperty(SpatialAnchorBlock.FACING)) return MachinePortMode.DISABLED;
        return portMode(MachinePortMode.Side.local(state.getValue(SpatialAnchorBlock.FACING), worldSide));
    }

    public boolean allowsPower(Direction worldSide) {
        var mode = portMode(worldSide);
        return mode == MachinePortMode.AUTOMATIC || mode == MachinePortMode.POWER;
    }

    public boolean connectsCable(Direction worldSide) {
        return allowsPower(worldSide);
    }

    public boolean cyclePort(Player player, MachinePortMode.Side side, boolean forward) {
        if (isRemoved() || player.isSpectator() || !canAccess(player) || !isConfigurableSide(side)) return false;
        MachinePortMode current = portMode(side);
        int idx = ALLOWED_PORT_MODES.indexOf(current);
        if (idx < 0) idx = 0;
        int nextIdx = (idx + (forward ? 1 : ALLOWED_PORT_MODES.size() - 1)) % ALLOWED_PORT_MODES.size();
        MachinePortMode clean = sanitizePortMode(side, ALLOWED_PORT_MODES.get(nextIdx));
        int shift = 4 * side.ordinal();
        int updated = (portModes & ~(15 << shift)) | (clean.ordinal() << shift);
        if (updated == portModes) return true;
        portModes = updated;
        setChanged();
        touchTopology();
        syncChunkAnchor();
        return true;
    }

    public boolean cyclePort(Player player, Direction worldSide, boolean forward) {
        BlockState state = getBlockState();
        if (!state.hasProperty(SpatialAnchorBlock.FACING)) return false;
        return cyclePort(player, MachinePortMode.Side.local(state.getValue(SpatialAnchorBlock.FACING), worldSide), forward);
    }

    public void afterRotation(ServerLevel server) {
        setChanged();
        touchTopology();
        syncChunkAnchor();
    }

    private void touchTopology() {
        if (level instanceof ServerLevel server) {
            ChunkRevisions.touchLoaded(server, worldPosition);
            server.invalidateCapabilities(worldPosition);
            MatterCableBlock.refreshLoadedAround(server, worldPosition);
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putInt("port_modes", portModes);
        return tag;
    }

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public void handleUpdateTag(ValueInput input) {
        int shown = portModes;
        portModes = sanitizePackedPorts(input.getIntOr("port_modes", shown));
        if (portModes != shown && level != null && level.isClientSide()) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
        }
    }

    @Override public void onDataPacket(Connection connection, ValueInput input) { handleUpdateTag(input); }

    @Override public ModelData getModelData() {
        return ModelData.of(PortSides.PLATES, PortSides.packAppearances(side -> PortAppearance.of(portMode(side))));
    }

    public @Nullable UUID owner() { return owner; }
    public SecurityMode securityMode() { return securityMode; }
    public String customName() { return customName; }
    public int chunkMask() { return chunkMask; }
    public int radius() {
        if (chunkMask == MASK_1X1) return 0;
        if (chunkMask == MASK_3X3) return 1;
        if (chunkMask == MASK_5X5) return 2;
        return -1;
    }
    public RedstoneMode redstoneMode() { return redstoneMode; }
    public int energy() { return (int) energy.getAmountAsLong(); }
    public int desiredChunks() { return Integer.bitCount(chunkMask & VALID_MASK_BITS); }
    public int drainPerTick() { return desiredChunks() * DRAIN_PER_CHUNK; }
    public java.util.Set<net.minecraft.world.level.ChunkPos> desiredChunkSet() {
        return ChunkAnchorManager.fromMask(worldPosition, chunkMask);
    }
    public ChunkAnchorManager.Status anchorStatus() { return lastStatus; }
    public @Nullable EnergyHandler energyHandler(@Nullable Direction side) {
        if (side == null || !allowsPower(side)) return null;
        return inputOnly;
    }

    public boolean canManageSecurity(Player player) { return MachineAccess.canManage(owner, player); }
    public boolean canAccess(Player player) { return MachineAccess.allows(level, owner, securityMode, player); }
    public boolean canRemove(Player player) { return canAccess(player); }
    public boolean stillValid(Player player) { return canAccess(player) && Container.stillValidBlockEntity(this, player); }

    public boolean setSecurityMode(Player player, SecurityMode next) {
        if (isRemoved() || player.isSpectator() || !canManageSecurity(player) || next == null) return false;
        if (securityMode == next) return true;
        securityMode = next;
        setChanged();
        return true;
    }

    public boolean cycleSecurityMode(Player player, boolean forward) {
        return setSecurityMode(player, forward ? securityMode.next() : securityMode.previous());
    }

    public void placedBy(@Nullable LivingEntity placer, ItemStack stack) {
        if (securityMode == SecurityMode.PUBLIC) {
            securityMode = MachineAccess.placementDefault(level, placer);
        }
        if (placer instanceof Player player && !(player instanceof FakePlayer)) {
            owner = player.getUUID();
        }
        String fromItem = CustomNames.fromStack(stack);
        if (!fromItem.isEmpty()) customName = fromItem;
        setChanged();
        syncChunkAnchor();
    }

    public boolean interact(Player player) {
        if (owner == null && !(player instanceof FakePlayer) && !player.isSpectator()) {
            owner = player.getUUID();
            setChanged();
        }
        boolean allowed = canAccess(player);
        if (!allowed && !player.level().isClientSide()) {
            player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
        }
        return allowed;
    }

    public void configure(int nextRadius, RedstoneMode nextRedstone, String nextName) {
        configureMask(presetMask(nextRadius), nextRedstone, nextName);
    }

    public void configureMask(int nextMask, RedstoneMode nextRedstone, String nextName) {
        int cleanMask = sanitizeMask(nextMask);
        RedstoneMode rs = nextRedstone == RedstoneMode.HIGH || nextRedstone == RedstoneMode.LOW
                ? nextRedstone : RedstoneMode.IGNORED;
        String name = CustomNames.sanitize(nextName);
        if (chunkMask == cleanMask && redstoneMode == rs && customName.equals(name)) return;
        chunkMask = cleanMask;
        redstoneMode = rs;
        customName = name;
        setChanged();
        syncChunkAnchor();
    }

    public void toggleChunk(int gx, int gz) {
        if (gx < 0 || gx >= 5 || gz < 0 || gz >= 5) return;
        int bit = gz * 5 + gx;
        if (bit == CENTER_BIT) return;
        configureMask(chunkMask ^ (1 << bit), redstoneMode, customName);
    }

    public void setCustomName(String nextName) {
        configureMask(chunkMask, redstoneMode, nextName);
    }

    public void setEnergyForTest(int value) {
        energy.set(Math.clamp(value, 0, CAPACITY));
    }

    private boolean redstoneAllows(ServerLevel server) {
        if (redstoneMode == RedstoneMode.IGNORED) return true;
        boolean hasDedicated = false;
        boolean dedicatedSignal = false;
        BlockState state = getBlockState();
        if (state.hasProperty(SpatialAnchorBlock.FACING)) {
            Direction front = state.getValue(SpatialAnchorBlock.FACING);
            for (var side : MachinePortMode.Side.values()) {
                if (portMode(side) == MachinePortMode.REDSTONE) {
                    hasDedicated = true;
                    Direction worldSide = side.world(front);
                    if (!dedicatedSignal && server.getSignal(worldPosition.relative(worldSide), worldSide) > 0) {
                        dedicatedSignal = true;
                    }
                }
            }
        }
        boolean powered = hasDedicated ? dedicatedSignal : server.hasNeighborSignal(worldPosition);
        return redstoneMode == RedstoneMode.HIGH ? powered : !powered;
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel server) || isRemoved()) return;
        if (!ChunkLoadingConfig.enabled()) {
            releaseWithStatus(server, "disabled");
            return;
        }
        if (owner == null) {
            releaseWithStatus(server, "idle");
            return;
        }
        if (!redstoneAllows(server)) {
            releaseWithStatus(server, "redstone_paused");
            return;
        }
        int cost = drainPerTick();
        if (energy() < cost) {
            releaseWithStatus(server, "missing_power");
            return;
        }
        var status = ChunkAnchorManager.sync(server, worldPosition, owner, desiredChunkSet());
        lastStatus = status;
        if (status.active()) {
            try (var tx = Transaction.openRoot()) {
                energy.extract(cost, tx);
                tx.commit();
            }
            setActiveState(server, true);
        } else {
            setActiveState(server, false);
        }
    }

    public void syncChunkAnchor() {
        if (!(level instanceof ServerLevel server) || isRemoved()) return;
        if (!ChunkLoadingConfig.enabled()) {
            releaseWithStatus(server, "disabled");
            return;
        }
        if (owner == null) {
            releaseWithStatus(server, "idle");
            return;
        }
        if (!redstoneAllows(server)) {
            releaseWithStatus(server, "redstone_paused");
            return;
        }
        if (energy() < drainPerTick()) {
            releaseWithStatus(server, "missing_power");
            return;
        }
        lastStatus = ChunkAnchorManager.sync(server, worldPosition, owner, desiredChunkSet());
        setActiveState(server, lastStatus.active());
    }

    private void releaseWithStatus(ServerLevel server, String reason) {
        ChunkAnchorManager.release(server, worldPosition);
        lastStatus = new ChunkAnchorManager.Status(false, 0,
                ChunkAnchorManager.ownerChunkCount(server.getServer(), owner),
                ChunkLoadingConfig.maxChunksPerPlayer(), reason);
        setActiveState(server, false);
    }

    private void setActiveState(ServerLevel server, boolean active) {
        BlockState state = getBlockState();
        if (state.getBlock() instanceof SpatialAnchorBlock && state.getValue(SpatialAnchorBlock.ACTIVE) != active) {
            server.setBlock(worldPosition, state.setValue(SpatialAnchorBlock.ACTIVE, active), Block.UPDATE_ALL);
        }
    }

    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) syncChunkAnchor();
    }

    @Override public void setRemoved() {
        if (level instanceof ServerLevel server) ChunkAnchorManager.release(server, worldPosition);
        super.setRemoved();
    }

    public void writeMenuInit(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
        buf.writeVarInt(chunkMask);
        buf.writeVarInt(redstoneMode.ordinal());
        buf.writeUtf(customName, CustomNames.MAX_LENGTH);
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("schema", 1);
        if (owner != null) output.putString("owner", owner.toString());
        if (securityMode != SecurityMode.PUBLIC) output.putString("security", securityMode.getSerializedName());
        if (!customName.isEmpty()) output.putString("custom_name", customName);
        if (portModes != DEFAULT_PORT_MODES) output.putInt("port_modes", portModes);
        if (chunkMask != MASK_1X1) output.putInt("chunk_mask", chunkMask);
        if (redstoneMode != RedstoneMode.IGNORED) output.putString("redstone_mode", redstoneMode.getSerializedName());
        if (energy() > 0) output.putInt("energy", energy());
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        owner = null;
        securityMode = SecurityMode.PUBLIC;
        customName = "";
        portModes = DEFAULT_PORT_MODES;
        chunkMask = MASK_1X1;
        redstoneMode = RedstoneMode.IGNORED;
        energy.set(0);
        if (input.getIntOr("schema", -1) != 1) return;
        input.getString("owner").ifPresent(raw -> {
            try { owner = UUID.fromString(raw); } catch (IllegalArgumentException ignored) { owner = null; }
        });
        securityMode = input.getString("security").filter(SecurityMode::validName).map(SecurityMode::load).orElse(SecurityMode.PUBLIC);
        customName = CustomNames.sanitize(input.getStringOr("custom_name", ""));
        portModes = sanitizePackedPorts(input.getIntOr("port_modes", DEFAULT_PORT_MODES));
        int savedMask = input.getIntOr("chunk_mask", 0);
        if (savedMask != 0) {
            chunkMask = sanitizeMask(savedMask);
        } else {
            chunkMask = presetMask(input.getIntOr("radius", 0));
        }
        RedstoneMode loadedRs = RedstoneMode.load(input.getStringOr("redstone_mode", "ignored"));
        redstoneMode = loadedRs == RedstoneMode.HIGH || loadedRs == RedstoneMode.LOW ? loadedRs : RedstoneMode.IGNORED;
        energy.set(Math.clamp(input.getIntOr("energy", 0), 0, CAPACITY));
    }

    @Override public Component getDisplayName() {
        return CustomNames.displayName(customName, "block.rematter.spatial_anchor");
    }

    @Override public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return canAccess(player) ? new SpatialAnchorMenu(id, inventory, this) : null;
    }
}
