package dev.latrox.rematter.primal;

import dev.latrox.rematter.content.ChunkRevisions;
import dev.latrox.rematter.content.CustomNames;
import dev.latrox.rematter.content.MachineAccess;
import dev.latrox.rematter.content.MachinePortMode;
import dev.latrox.rematter.content.MatterCableBlock;
import dev.latrox.rematter.content.PortAppearance;
import dev.latrox.rematter.content.PortSides;
import dev.latrox.rematter.content.RedstoneMode;
import dev.latrox.rematter.content.SecurityMode;
import java.util.List;
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
import org.jspecify.annotations.Nullable;

public final class EntangledLinkBlockEntity extends BlockEntity implements MenuProvider {
    public static final List<MachinePortMode> ALLOWED_PORT_MODES = List.of(
            MachinePortMode.AUTOMATIC, MachinePortMode.MATTER, MachinePortMode.POWER, MachinePortMode.REDSTONE, MachinePortMode.DISABLED);
    private static final int DEFAULT_PORT_MODES = PortSides.pack(
            side -> isConfigurableSide(side) ? MachinePortMode.AUTOMATIC : MachinePortMode.DISABLED);

    private @Nullable UUID owner, pair;
    private @Nullable BlockPos peer;
    private SecurityMode securityMode = SecurityMode.PUBLIC;
    private RedstoneMode redstoneMode = RedstoneMode.IGNORED;
    private boolean lastRedstoneAllowed = true;
    private String customName = "";
    private int portModes = DEFAULT_PORT_MODES;
    public EntangledLinkBlockEntity(BlockPos pos, BlockState state) { super(PrimalContent.ENTANGLED_LINK_ENTITY.get(), pos, state); }
    public @Nullable UUID owner() { return owner; }
    public SecurityMode securityMode() { return securityMode; }
    public RedstoneMode redstoneMode() { return redstoneMode; }
    public String customName() { return customName; }

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

    public int packedPortModes() { return portModes; }

    public MachinePortMode portMode(MachinePortMode.Side side) {
        return sanitizePortMode(side, PortSides.unpack(portModes, side));
    }

    public MachinePortMode portMode(Direction worldSide) {
        BlockState state = getBlockState();
        if (!state.hasProperty(EntangledLinkBlock.FACING)) return MachinePortMode.DISABLED;
        return portMode(MachinePortMode.Side.local(state.getValue(EntangledLinkBlock.FACING), worldSide));
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

    public boolean redstoneAllows() {
        if (redstoneMode == RedstoneMode.IGNORED || level == null) return true;
        boolean hasDedicated = false;
        boolean dedicatedSignal = false;
        BlockState state = getBlockState();
        if (state.hasProperty(EntangledLinkBlock.FACING)) {
            Direction front = state.getValue(EntangledLinkBlock.FACING);
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
            if (level instanceof ServerLevel server) {
                MatterCableBlock.refreshLoadedAround(server, worldPosition);
            }
        }
    }

    @Override public void onLoad() {
        super.onLoad();
        lastRedstoneAllowed = redstoneAllows();
    }

    public boolean cyclePort(Player player, MachinePortMode.Side side, boolean forward) {
        if (isRemoved() || player.isSpectator() || !canAccess(player) || !isConfigurableSide(side)) return false;
        MachinePortMode current = portMode(side);
        int idx = ALLOWED_PORT_MODES.indexOf(current);
        if (idx < 0) idx = 0;
        int nextIdx = (idx + (forward ? 1 : ALLOWED_PORT_MODES.size() - 1)) % ALLOWED_PORT_MODES.size();
        return setPortModeInternal(side, ALLOWED_PORT_MODES.get(nextIdx));
    }

    public boolean cyclePort(Player player, Direction worldSide, boolean forward) {
        BlockState state = getBlockState();
        if (!state.hasProperty(EntangledLinkBlock.FACING)) return false;
        return cyclePort(player, MachinePortMode.Side.local(state.getValue(EntangledLinkBlock.FACING), worldSide), forward);
    }

    private boolean setPortModeInternal(MachinePortMode.Side side, MachinePortMode next) {
        MachinePortMode clean = sanitizePortMode(side, next);
        int shift = 4 * side.ordinal();
        int updated = (portModes & ~(15 << shift)) | (clean.ordinal() << shift);
        if (updated == portModes) return true;
        portModes = updated;
        lastRedstoneAllowed = redstoneAllows();
        endpointChanged();
        touchTopology();
        return true;
    }

    public void afterRotation(ServerLevel server) {
        lastRedstoneAllowed = redstoneAllows();
        endpointChanged();
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
    @Override public Component getDisplayName() {
        return CustomNames.displayName(customName, "block.rematter.entangled_link");
    }
    @Override public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return canAccess(player) ? new EntangledLinkMenu(id, inventory, this) : null;
    }
    public void writeMenuInit(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
        boolean linked = pair != null && peer != null;
        buf.writeBoolean(linked);
        if (linked && peer != null) buf.writeBlockPos(peer);
        buf.writeUtf(customName, CustomNames.MAX_LENGTH);
    }
    public void setCustomName(String next) {
        String clean = CustomNames.sanitize(next);
        if (customName.equals(clean)) return;
        customName = clean;
        setChanged();
    }
    public boolean canManageSecurity(Player player) {
        return MachineAccess.canManage(owner, player);
    }
    public boolean setSecurityMode(Player player, SecurityMode next) {
        if (isRemoved() || player.isSpectator() || !canManageSecurity(player) || next == null) return false;
        if (securityMode == next) return true;
        securityMode = next;
        endpointChanged();
        if (level instanceof ServerLevel server) {
            MatterCableBlock.refreshLoadedAround(server, worldPosition);
        }
        return true;
    }
    public boolean cycleSecurityMode(Player player, boolean forward) {
        return setSecurityMode(player, forward ? securityMode.next() : securityMode.previous());
    }
    public boolean canAccess(Player player) {
        return MachineAccess.allows(level, owner, securityMode, player);
    }
    public boolean canRemove(Player player) {
        return canAccess(player);
    }
    public boolean stillValid(Player player) {
        return canAccess(player) && Container.stillValidBlockEntity(this, player);
    }
    public boolean interact(Player player) {
        if (owner == null && !(player instanceof FakePlayer) && !player.isSpectator()) {
            claim(player.getUUID());
        }
        boolean allowed = canAccess(player);
        if (!allowed && !player.level().isClientSide()) {
            player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
        }
        return allowed;
    }
    @Nullable UUID pair() { return pair; }
    @Nullable BlockPos target() { return peer; }
    void claim(UUID user) { if (owner == null) { owner = user; endpointChanged(); } }
    public void placedBy(@Nullable LivingEntity placer) {
        placedBy(placer, ItemStack.EMPTY);
    }
    public void placedBy(@Nullable LivingEntity placer, ItemStack stack) {
        if (placer instanceof Player player && !(player instanceof FakePlayer)) {
            if (securityMode == SecurityMode.PUBLIC) {
                securityMode = MachineAccess.placementDefault(level, placer);
            }
            claim(player.getUUID());
            if (level instanceof ServerLevel server) {
                MatterCableBlock.refreshLoadedAround(server, worldPosition);
            }
        }
        String fromItem = CustomNames.fromStack(stack);
        if (!fromItem.isEmpty()) {
            customName = fromItem;
            setChanged();
        }
    }
    void pair(BlockPos target, UUID id) { peer = target.immutable(); pair = id; endpointChanged(); }
    public void clearPair() {
        if (pair == null && peer == null) return;
        pair = null;
        peer = null;
        endpointChanged();
    }
    public void unpairBoth(ServerLevel server) {
        UUID oldPair = pair;
        BlockPos oldPeer = peer;
        clearPair();
        MatterCableBlock.refreshLoadedAround(server, worldPosition);
        if (oldPair != null && oldPeer != null) {
            EntangledLinks.loaded(server, oldPeer).ifPresent(remote -> {
                if (oldPair.equals(remote.pair()) && worldPosition.equals(remote.target())) {
                    remote.clearPair();
                    MatterCableBlock.refreshLoadedAround(server, oldPeer);
                }
            });
        }
    }
    private void endpointChanged() {
        setChanged();
        if (level instanceof ServerLevel server) ChunkRevisions.touchLoaded(server, worldPosition);
    }
    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output); output.putInt("schema", 1);
        if (owner != null) output.putString("owner", owner.toString());
        if (securityMode != SecurityMode.PUBLIC) output.putString("security", securityMode.getSerializedName());
        if (redstoneMode != RedstoneMode.IGNORED) output.putString("redstone_mode", redstoneMode.getSerializedName());
        if (!customName.isEmpty()) output.putString("custom_name", customName);
        if (portModes != DEFAULT_PORT_MODES) output.putInt("port_modes", portModes);
        if (pair != null && peer != null) { output.putString("pair", pair.toString()); output.putLong("peer", peer.asLong()); }
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input); owner = null; pair = null; peer = null; securityMode = SecurityMode.PUBLIC; redstoneMode = RedstoneMode.IGNORED; customName = ""; portModes = DEFAULT_PORT_MODES;
        if (input.getIntOr("schema", -1) != 1) return;
        try {
            var savedOwner = input.getString("owner"); if (savedOwner.isPresent()) owner = UUID.fromString(savedOwner.get());
            securityMode = input.getString("security").filter(SecurityMode::validName).map(SecurityMode::load).orElse(SecurityMode.PUBLIC);
            redstoneMode = RedstoneMode.loadSimple(input.getStringOr("redstone_mode", "ignored"));
            customName = CustomNames.sanitize(input.getStringOr("custom_name", ""));
            portModes = sanitizePackedPorts(input.getIntOr("port_modes", DEFAULT_PORT_MODES));
            var savedPair = input.getString("pair");
            if (savedPair.isPresent()) { pair = UUID.fromString(savedPair.get()); peer = BlockPos.of(input.getLongOr("peer", 0)); }
        } catch (IllegalArgumentException invalid) { pair = null; peer = null; }
    }
}
