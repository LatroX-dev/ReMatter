package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.ReservedSubstances;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.network.CellTransferPayload;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jspecify.annotations.Nullable;

/** Evaluates Matter Network stock against a configured threshold and drives directional redstone power. */
public final class MatterLevelEmitterBlockEntity extends BlockEntity implements MenuProvider {
    public static final long MAX_THRESHOLD = 1_000_000_000_000L;
    public static final long DEFAULT_THRESHOLD = 1_000L;

    private @Nullable UUID owner;
    private String customName = "";
    private @Nullable SubstanceKey target;
    private long threshold = DEFAULT_THRESHOLD;
    private boolean emitWhenBelow;
    private long observedStock;
    private boolean networkReady;
    private @Nullable MachineMatterNetwork cachedNetwork;

    public MatterLevelEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.MATTER_LEVEL_EMITTER_ENTITY.get(), pos, state);
    }

    public @Nullable UUID owner() { return owner; }
    public String customName() { return customName; }
    public void setCustomName(String next) {
        String clean = CustomNames.sanitize(next);
        if (customName.equals(clean)) return;
        customName = clean;
        setChanged();
    }
    public @Nullable SubstanceKey target() { return target; }
    public long threshold() { return threshold; }
    public boolean emitWhenBelow() { return emitWhenBelow; }
    public long observedStock() { return observedStock; }
    public boolean networkReady() { return networkReady; }
    public boolean emitting() { return getBlockState().getValue(MatterLevelEmitterBlock.ACTIVE); }

    boolean networkLive() {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread() || isRemoved() || owner == null) return false;
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return chunk != null && chunk.getBlockEntity(worldPosition) == this;
    }

    List<Direction> connectionSides() {
        return List.of(Direction.DOWN, getBlockState().getValue(MatterLevelEmitterBlock.FACING).getOpposite());
    }

    MachineMatterNetwork matterNetwork(boolean deep) {
        if (cachedNetwork == null || !cachedNetwork.current(this, deep)) {
            cachedNetwork = MachineMatterNetwork.discover(this);
        }
        return cachedNetwork;
    }

    private SecurityMode securityMode = SecurityMode.PUBLIC;

    public SecurityMode securityMode() { return securityMode; }

    public boolean canManageSecurity(Player player) { return MachineAccess.canManage(owner, player); }

    public boolean setSecurityMode(Player player, SecurityMode next) {
        if (!networkLive() || player.isSpectator() || !canManageSecurity(player) || next == null) return false;
        if (securityMode == next) return true;
        securityMode = next;
        cachedNetwork = null;
        setChanged();
        touchTopology();
        refreshOutputNow();
        return true;
    }

    public boolean cycleSecurityMode(Player player, boolean forward) {
        return setSecurityMode(player, forward ? securityMode.next() : securityMode.previous());
    }

    public boolean canRemove(Player player) {
        return canAccess(player);
    }

    public boolean canAccess(Player player) {
        return MachineAccess.allows(level, owner, securityMode, player);
    }

    public boolean stillValid(Player player) {
        return canAccess(player) && Container.stillValidBlockEntity(this, player);
    }

    public void placedBy(@Nullable LivingEntity placer) {
        placedBy(placer, net.minecraft.world.item.ItemStack.EMPTY);
    }

    public void placedBy(@Nullable LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        if (securityMode == SecurityMode.PUBLIC) {
            securityMode = MachineAccess.placementDefault(level, placer);
        }
        String fromItem = CustomNames.fromStack(stack);
        if (!fromItem.isEmpty()) {
            customName = fromItem;
            setChanged();
        }
        assignOwner(placer instanceof Player player && !(player instanceof FakePlayer) ? player.getUUID() : null);
    }

    public boolean interact(Player player) {
        if (owner == null && !(player instanceof FakePlayer) && !player.isSpectator()) assignOwner(player.getUUID());
        boolean allowed = canAccess(player);
        if (!allowed && !player.level().isClientSide()) player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
        return allowed;
    }

    private void assignOwner(@Nullable UUID next) {
        if (Objects.equals(owner, next)) return;
        owner = next;
        cachedNetwork = null;
        setChanged();
        touchTopology();
    }

    public void afterRotation(ServerLevel server, Direction previousFacing) {
        cachedNetwork = null;
        setChanged();
        touchTopology();
        if (getBlockState().getBlock() instanceof MatterLevelEmitterBlock emitterBlock) {
            emitterBlock.notifySignalTarget(server, worldPosition, previousFacing);
            emitterBlock.notifySignalTarget(server, worldPosition, getBlockState().getValue(MatterLevelEmitterBlock.FACING));
        }
        refreshOutputNow();
    }

    private void touchTopology() {
        if (level instanceof ServerLevel server) {
            ChunkRevisions.touchLoaded(server, worldPosition);
            MatterCableBlock.refreshLoadedAround(server, worldPosition);
        }
    }

    public void configure(@Nullable SubstanceKey nextTarget, long nextThreshold, boolean nextEmitBelow) {
        long clamped = Math.clamp(nextThreshold, 0L, MAX_THRESHOLD);
        if (nextTarget != null && nextTarget.id().toString().length() > CellPartitionData.MAX_ID_LENGTH) return;
        if (Objects.equals(target, nextTarget) && threshold == clamped && emitWhenBelow == nextEmitBelow) return;
        target = nextTarget;
        threshold = clamped;
        emitWhenBelow = nextEmitBelow;
        setChanged();
        refreshOutputNow();
    }

    public void serverTick() {
        if (!networkLive()) {
            networkReady = false;
            observedStock = 0L;
            updateActiveState(false);
            return;
        }
        refreshOutputNow();
    }

    public void refreshOutputNow() {
        if (!(level instanceof ServerLevel)) return;
        boolean shouldEmit = evaluateCondition();
        updateActiveState(shouldEmit);
    }

    private boolean evaluateCondition() {
        if (!networkLive()) {
            networkReady = false;
            observedStock = 0L;
            return false;
        }
        var network = matterNetwork(false);
        if (target == null) {
            networkReady = network.ready();
            observedStock = 0L;
            return false;
        }
        if (ReservedSubstances.reserved(target)) {
            networkReady = network.managementAvailable();
            if (!networkReady) {
                observedStock = 0L;
                return false;
            }
            observedStock = network.primalAmount();
        } else {
            var inv = network.ready() ? network.inventory() : null;
            networkReady = inv != null;
            if (inv == null) {
                observedStock = 0L;
                return false;
            }
            observedStock = inv.amount(target);
        }
        return emitWhenBelow ? observedStock < threshold : observedStock >= threshold;
    }

    private void updateActiveState(boolean active) {
        if (!(level instanceof ServerLevel server)) return;
        BlockState state = getBlockState();
        if (state.getValue(MatterLevelEmitterBlock.ACTIVE) != active) {
            Direction facing = state.getValue(MatterLevelEmitterBlock.FACING);
            server.setBlock(worldPosition, state.setValue(MatterLevelEmitterBlock.ACTIVE, active), Block.UPDATE_ALL);
            if (state.getBlock() instanceof MatterLevelEmitterBlock emitterBlock) {
                emitterBlock.notifySignalTarget(server, worldPosition, facing);
            }
        }
    }

    public void writeMenuInit(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
        buf.writeBoolean(target != null);
        if (target != null) CellTransferPayload.writeKey(buf, target);
        buf.writeVarLong(threshold);
        buf.writeBoolean(emitWhenBelow);
        buf.writeVarLong(observedStock);
        buf.writeBoolean(networkReady);
        buf.writeBoolean(emitting());
        buf.writeUtf(customName, CustomNames.MAX_LENGTH);
    }

    @Override public void setRemoved() {
        cachedNetwork = null;
        if (level instanceof ServerLevel server) ChunkRevisions.touchLoaded(server, worldPosition);
        super.setRemoved();
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("schema", 1);
        if (owner != null) output.putString("owner", owner.toString());
        if (securityMode != SecurityMode.PUBLIC) output.putString("security", securityMode.getSerializedName());
        if (!customName.isEmpty()) output.putString("custom_name", customName);
        if (target != null) {
            output.putString("target_id", target.id().toString());
            output.putString("target_phase", target.phase().name().toLowerCase(Locale.ROOT));
        }
        output.putLong("threshold", threshold);
        output.putBoolean("emit_below", emitWhenBelow);
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        cachedNetwork = null;
        owner = null;
        securityMode = SecurityMode.PUBLIC;
        customName = "";
        target = null;
        threshold = DEFAULT_THRESHOLD;
        emitWhenBelow = false;
        observedStock = 0L;
        networkReady = false;
        if (input.getIntOr("schema", -1) != 1) return;
        input.getString("owner").ifPresent(raw -> {
            try { owner = UUID.fromString(raw); } catch (IllegalArgumentException ignored) { owner = null; }
        });
        securityMode = input.getString("security").filter(SecurityMode::validName).map(SecurityMode::load).orElse(SecurityMode.PUBLIC);
        customName = CustomNames.sanitize(input.getStringOr("custom_name", ""));
        var rawId = input.getString("target_id");
        var rawPhase = input.getString("target_phase");
        if (rawId.isPresent() && rawPhase.isPresent() && rawId.get().length() <= CellPartitionData.MAX_ID_LENGTH) {
            try {
                target = new SubstanceKey(SubstanceId.parse(rawId.get()), Phase.valueOf(rawPhase.get().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                target = null;
            }
        }
        threshold = Math.clamp(input.getLongOr("threshold", DEFAULT_THRESHOLD), 0L, MAX_THRESHOLD);
        emitWhenBelow = input.getBooleanOr("emit_below", false);
        if (level instanceof ServerLevel) touchTopology();
    }

    @Override public Component getDisplayName() {
        return CustomNames.displayName(customName, "block.rematter.matter_level_emitter");
    }

    @Override public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return canAccess(player) ? new MatterLevelEmitterMenu(id, inventory, this) : null;
    }
}
