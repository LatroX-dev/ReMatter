package dev.latrox.rematter.primal;

import dev.latrox.rematter.content.ChunkAnchorManager;
import dev.latrox.rematter.content.ChunkRevisions;
import dev.latrox.rematter.content.CustomNames;
import dev.latrox.rematter.content.MachineAccess;
import dev.latrox.rematter.content.MachinePortMode;
import dev.latrox.rematter.content.MatterCableBlock;
import dev.latrox.rematter.content.ModContent;
import dev.latrox.rematter.content.PortAppearance;
import dev.latrox.rematter.content.PortSides;
import dev.latrox.rematter.content.RedstoneMode;
import dev.latrox.rematter.content.SecurityMode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.model.data.ModelData;
import org.jspecify.annotations.Nullable;

/**
 * Cross-dimension & unlimited-distance Entangled Link (Resonant 8,192 u/t & 524,288 FE/t).
 * Purely event-driven (zero BlockEntity ticks) with optional Anchor Upgrade, configurable ports, and custom label.
 */
public final class DimensionalEntangledLinkBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int SLOT_ANCHOR = 0;
    public static final List<MachinePortMode> ALLOWED_PORT_MODES = EntangledLinkBlockEntity.ALLOWED_PORT_MODES;
    private static final int DEFAULT_PORT_MODES = PortSides.pack(
            side -> isConfigurableSide(side) ? MachinePortMode.AUTOMATIC : MachinePortMode.DISABLED);

    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private @Nullable UUID owner;
    private @Nullable UUID pair;
    private @Nullable ResourceKey<Level> peerDimension;
    private @Nullable BlockPos peerPos;
    private SecurityMode securityMode = SecurityMode.PUBLIC;
    private RedstoneMode redstoneMode = RedstoneMode.IGNORED;
    private boolean lastRedstoneAllowed = true;
    private String customName = "";
    private int portModes = DEFAULT_PORT_MODES;
    private long stateRevision = 1L;
    private String revisionToken = "1";
    private ChunkAnchorManager.Status anchorStatus = ChunkAnchorManager.Status.IDLE;

    public DimensionalEntangledLinkBlockEntity(BlockPos pos, BlockState state) {
        super(PrimalContent.DIMENSIONAL_ENTANGLED_LINK_ENTITY.get(), pos, state);
    }

    public static boolean isConfigurableSide(MachinePortMode.Side side) {
        return EntangledLinkBlockEntity.isConfigurableSide(side);
    }

    private static int sanitizePackedPorts(int packed) {
        return PortSides.pack(side -> EntangledLinkBlockEntity.sanitizePortMode(side, PortSides.unpack(packed, side)));
    }

    public MachinePortMode portMode(MachinePortMode.Side side) {
        return EntangledLinkBlockEntity.sanitizePortMode(side, PortSides.unpack(portModes, side));
    }

    public MachinePortMode portMode(Direction worldSide) {
        BlockState state = getBlockState();
        if (!state.hasProperty(DimensionalEntangledLinkBlock.FACING)) return MachinePortMode.DISABLED;
        return portMode(MachinePortMode.Side.local(state.getValue(DimensionalEntangledLinkBlock.FACING), worldSide));
    }

    public boolean allowsMatter(Direction worldSide) {
        var mode = portMode(worldSide);
        return mode == MachinePortMode.AUTOMATIC || mode == MachinePortMode.MATTER;
    }

    public boolean allowsPower(Direction worldSide) {
        var mode = portMode(worldSide);
        return mode == MachinePortMode.AUTOMATIC || mode == MachinePortMode.POWER;
    }

    public boolean connectsCable(Direction worldSide) {
        return allowsMatter(worldSide) || allowsPower(worldSide);
    }

    public RedstoneMode redstoneMode() { return redstoneMode; }

    public boolean redstoneAllows() {
        if (redstoneMode == RedstoneMode.IGNORED || level == null) return true;
        boolean hasDedicated = false;
        boolean dedicatedSignal = false;
        BlockState state = getBlockState();
        if (state.hasProperty(DimensionalEntangledLinkBlock.FACING)) {
            Direction front = state.getValue(DimensionalEntangledLinkBlock.FACING);
            for (var side : MachinePortMode.Side.values()) {
                if (portMode(side) == MachinePortMode.REDSTONE) {
                    hasDedicated = true;
                    Direction worldSide = side.world(front);
                    if (!dedicatedSignal && level.getSignal(worldPosition.relative(worldSide), worldSide) > 0) {
                        dedicatedSignal = true;
                    }
                }
            }
        }
        boolean powered = hasDedicated ? dedicatedSignal : level.hasNeighborSignal(worldPosition);
        return redstoneMode == RedstoneMode.HIGH ? powered : !powered;
    }

    public boolean redstonePaused() { return !redstoneAllows(); }

    public boolean cycleRedstoneMode(Player player, boolean forward) {
        if (isRemoved() || player.isSpectator() || !canAccess(player)) return false;
        RedstoneMode next = redstoneMode.stepSimple(forward);
        if (redstoneMode == next) return true;
        redstoneMode = next;
        lastRedstoneAllowed = redstoneAllows();
        endpointChanged();
        syncLinkedState();
        if (level instanceof ServerLevel server) {
            MatterCableBlock.refreshLoadedAround(server, worldPosition);
        }
        return true;
    }

    public void onNeighborRedstoneChanged() {
        boolean now = redstoneAllows();
        if (now != lastRedstoneAllowed) {
            lastRedstoneAllowed = now;
            endpointChanged();
            syncLinkedState();
            if (level instanceof ServerLevel server) {
                MatterCableBlock.refreshLoadedAround(server, worldPosition);
            }
        }
    }

    public boolean cyclePort(Player player, MachinePortMode.Side side, boolean forward) {
        if (isRemoved() || player.isSpectator() || !canAccess(player) || !isConfigurableSide(side)) return false;
        MachinePortMode current = portMode(side);
        int idx = ALLOWED_PORT_MODES.indexOf(current);
        if (idx < 0) idx = 0;
        int nextIdx = (idx + (forward ? 1 : ALLOWED_PORT_MODES.size() - 1)) % ALLOWED_PORT_MODES.size();
        MachinePortMode clean = EntangledLinkBlockEntity.sanitizePortMode(side, ALLOWED_PORT_MODES.get(nextIdx));
        int shift = 4 * side.ordinal();
        int updated = (portModes & ~(15 << shift)) | (clean.ordinal() << shift);
        if (updated == portModes) return true;
        portModes = updated;
        lastRedstoneAllowed = redstoneAllows();
        endpointChanged();
        syncLinkedState();
        touchTopology();
        return true;
    }

    public boolean cyclePort(Player player, Direction worldSide, boolean forward) {
        BlockState state = getBlockState();
        if (!state.hasProperty(DimensionalEntangledLinkBlock.FACING)) return false;
        return cyclePort(player, MachinePortMode.Side.local(state.getValue(DimensionalEntangledLinkBlock.FACING), worldSide), forward);
    }

    public void afterRotation(ServerLevel server) {
        lastRedstoneAllowed = redstoneAllows();
        endpointChanged();
        syncLinkedState();
        touchTopology();
    }

    private void touchTopology() {
        if (level instanceof ServerLevel server) {
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
    public @Nullable UUID pair() { return pair; }
    public @Nullable ResourceKey<Level> peerDimension() { return peerDimension; }
    public @Nullable BlockPos peerPos() { return peerPos; }
    public boolean hasAnchorUpgrade() { return items.get(SLOT_ANCHOR).is(ModContent.ANCHOR_UPGRADE.get()); }
    public ChunkAnchorManager.Status anchorStatus() { return anchorStatus; }
    public long stateRevision() { return stateRevision; }
    public String revisionToken() { return revisionToken; }

    private void bumpRevision() {
        stateRevision++;
        revisionToken = Long.toString(stateRevision);
    }

    public boolean canManageSecurity(Player player) { return MachineAccess.canManage(owner, player); }
    public boolean canAccess(Player player) { return MachineAccess.allows(level, owner, securityMode, player); }
    public boolean canRemove(Player player) { return canAccess(player); }

    public boolean setSecurityMode(Player player, SecurityMode next) {
        if (isRemoved() || player.isSpectator() || !canManageSecurity(player) || next == null) return false;
        if (securityMode == next) return true;
        securityMode = next;
        endpointChanged();
        if (level instanceof ServerLevel server) MatterCableBlock.refreshLoadedAround(server, worldPosition);
        return true;
    }

    public boolean cycleSecurityMode(Player player, boolean forward) {
        return setSecurityMode(player, forward ? securityMode.next() : securityMode.previous());
    }

    public void placedBy(@Nullable LivingEntity placer, ItemStack stack) {
        if (placer instanceof Player player && !(player instanceof FakePlayer)) {
            if (securityMode == SecurityMode.PUBLIC) {
                securityMode = MachineAccess.placementDefault(level, placer);
            }
            if (owner == null) owner = player.getUUID();
        }
        String fromItem = CustomNames.fromStack(stack);
        if (!fromItem.isEmpty()) customName = fromItem;
        endpointChanged();
        syncChunkAnchor();
        if (level instanceof ServerLevel server) MatterCableBlock.refreshLoadedAround(server, worldPosition);
    }

    public boolean interact(Player player) {
        if (owner == null && !(player instanceof FakePlayer) && !player.isSpectator()) {
            owner = player.getUUID();
            endpointChanged();
        }
        boolean allowed = canAccess(player);
        if (!allowed && !player.level().isClientSide()) {
            player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
        }
        return allowed;
    }

    public void pair(ResourceKey<Level> targetDim, BlockPos targetPos, UUID id) {
        peerDimension = Objects.requireNonNull(targetDim);
        peerPos = targetPos.immutable();
        pair = Objects.requireNonNull(id);
        endpointChanged();
        syncLinkedState();
    }

    public void clearPair() {
        if (pair == null && peerPos == null && peerDimension == null) return;
        pair = null;
        peerDimension = null;
        peerPos = null;
        endpointChanged();
        syncLinkedState();
    }

    public void unpairBoth(ServerLevel server) {
        UUID oldPair = pair;
        ResourceKey<Level> oldDim = peerDimension;
        BlockPos oldPeer = peerPos;
        clearPair();
        MatterCableBlock.refreshLoadedAround(server, worldPosition);
        if (oldPair != null && oldDim != null && oldPeer != null) {
            ServerLevel targetLevel = server.getServer().getLevel(oldDim);
            if (targetLevel != null) {
                EntangledLinks.loadedDimensional(targetLevel, oldPeer).ifPresent(remote -> {
                    if (oldPair.equals(remote.pair())
                            && server.dimension().equals(remote.peerDimension())
                            && worldPosition.equals(remote.peerPos())) {
                        remote.clearPair();
                        MatterCableBlock.refreshLoadedAround(targetLevel, oldPeer);
                    }
                });
            }
        }
    }

    public void setCustomName(String next) {
        String clean = CustomNames.sanitize(next);
        if (customName.equals(clean)) return;
        customName = clean;
        setChanged();
    }

    public void syncLinkedState() {
        if (!(level instanceof ServerLevel server) || isRemoved()) return;
        boolean paired = pair != null && peerDimension != null && peerPos != null && redstoneAllows();
        BlockState state = getBlockState();
        if (state.getBlock() instanceof DimensionalEntangledLinkBlock && state.getValue(DimensionalEntangledLinkBlock.LINKED) != paired) {
            server.setBlock(worldPosition, state.setValue(DimensionalEntangledLinkBlock.LINKED, paired), Block.UPDATE_ALL);
        }
    }

    public void syncChunkAnchor() {
        if (!(level instanceof ServerLevel server) || isRemoved()) return;
        if (owner != null && hasAnchorUpgrade()) {
            anchorStatus = ChunkAnchorManager.sync(server, worldPosition, owner, ChunkAnchorManager.square(worldPosition, 0));
        } else {
            ChunkAnchorManager.release(server, worldPosition);
            anchorStatus = new ChunkAnchorManager.Status(false, 0,
                    ChunkAnchorManager.ownerChunkCount(server.getServer(), owner),
                    dev.latrox.rematter.content.ChunkLoadingConfig.maxChunksPerPlayer(), "idle");
        }
    }

    private void endpointChanged() {
        bumpRevision();
        setChanged();
        if (level instanceof ServerLevel server) ChunkRevisions.touchLoaded(server, worldPosition);
    }

    @Override public void onLoad() {
        super.onLoad();
        lastRedstoneAllowed = redstoneAllows();
        if (level instanceof ServerLevel server) {
            server.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    public java.util.List<ItemStack> dropContentsList() {
        var out = new java.util.ArrayList<ItemStack>();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) out.add(stack.copy());
        }
        return out;
    }

    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {}

    @Override public void setRemoved() {
        bumpRevision();
        if (level instanceof ServerLevel server) {
            ChunkAnchorManager.release(server, worldPosition);
            ChunkRevisions.touchLoaded(server, worldPosition);
        }
        super.setRemoved();
    }

    public void writeMenuInit(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
        buf.writeUtf(customName, CustomNames.MAX_LENGTH);
        buf.writeBoolean(peerDimension != null && peerPos != null && pair != null);
        if (peerDimension != null && peerPos != null && pair != null) {
            buf.writeUtf(peerDimension.identifier().toString(), 128);
            buf.writeBlockPos(peerPos);
        }
    }

    // --- 1-slot Anchor Upgrade Container ---
    @Override public int getContainerSize() { return 1; }
    @Override public boolean isEmpty() { return items.get(0).isEmpty(); }
    @Override public ItemStack getItem(int slot) { return slot == 0 ? items.get(0) : ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack out = ContainerHelper.removeItem(items, slot, amount);
        if (!out.isEmpty()) { setChanged(); syncChunkAnchor(); }
        return out;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        ItemStack out = ContainerHelper.takeItem(items, slot);
        if (!out.isEmpty()) { setChanged(); syncChunkAnchor(); }
        return out;
    }
    @Override public void setItem(int slot, ItemStack stack) {
        if (slot != 0) return;
        items.set(0, stack.getCount() > 1 ? stack.copyWithCount(1) : stack);
        setChanged();
        syncChunkAnchor();
    }
    @Override public int getMaxStackSize() { return 1; }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_ANCHOR && stack.is(ModContent.ANCHOR_UPGRADE.get());
    }
    @Override public boolean stillValid(Player player) { return canAccess(player) && Container.stillValidBlockEntity(this, player); }
    @Override public void clearContent() { items.clear(); setChanged(); syncChunkAnchor(); }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("schema", 1);
        if (owner != null) output.putString("owner", owner.toString());
        if (securityMode != SecurityMode.PUBLIC) output.putString("security", securityMode.getSerializedName());
        if (redstoneMode != RedstoneMode.IGNORED) output.putString("redstone_mode", redstoneMode.getSerializedName());
        if (!customName.isEmpty()) output.putString("custom_name", customName);
        if (portModes != DEFAULT_PORT_MODES) output.putInt("port_modes", portModes);
        if (pair != null && peerDimension != null && peerPos != null) {
            output.putString("pair", pair.toString());
            output.putString("peer_dim", peerDimension.identifier().toString());
            output.putLong("peer", peerPos.asLong());
        }
        ContainerHelper.saveAllItems(output, items);
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        owner = null;
        pair = null;
        peerDimension = null;
        peerPos = null;
        securityMode = SecurityMode.PUBLIC;
        redstoneMode = RedstoneMode.IGNORED;
        customName = "";
        portModes = DEFAULT_PORT_MODES;
        if (input.getIntOr("schema", -1) != 1) return;
        ContainerHelper.loadAllItems(input, items);
        try {
            var savedOwner = input.getString("owner");
            if (savedOwner.isPresent()) owner = UUID.fromString(savedOwner.get());
            securityMode = input.getString("security").filter(SecurityMode::validName).map(SecurityMode::load).orElse(SecurityMode.PUBLIC);
            redstoneMode = RedstoneMode.loadSimple(input.getStringOr("redstone_mode", "ignored"));
            customName = CustomNames.sanitize(input.getStringOr("custom_name", ""));
            portModes = sanitizePackedPorts(input.getIntOr("port_modes", DEFAULT_PORT_MODES));
            var savedPair = input.getString("pair");
            var savedDim = input.getString("peer_dim");
            if (savedPair.isPresent() && savedDim.isPresent()) {
                pair = UUID.fromString(savedPair.get());
                peerDimension = ResourceKey.create(Registries.DIMENSION, Identifier.parse(savedDim.get()));
                peerPos = BlockPos.of(input.getLongOr("peer", 0L));
            }
        } catch (RuntimeException invalid) {
            pair = null;
            peerDimension = null;
            peerPos = null;
        }
        bumpRevision();
    }

    @Override public Component getDisplayName() {
        return CustomNames.displayName(customName, "block.rematter.dimensional_entangled_link");
    }

    @Override public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return canAccess(player) ? new DimensionalEntangledLinkMenu(id, inventory, this) : null;
    }
}
