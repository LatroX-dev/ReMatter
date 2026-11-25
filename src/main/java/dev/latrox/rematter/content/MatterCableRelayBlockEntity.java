package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.ReservedSubstances;
import dev.latrox.rematter.chemistry.SubstanceKey;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
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

/**
 * Server-authoritative inline cable relay/valve: redstone gating, Matter/FE channel separation,
 * bidirectional or one-way flow (Green Input -> Red Output), aggregate-state & substance filtering,
 * and exact FE/t & Matter/t rate limits along its axial connections.
 */
public final class MatterCableRelayBlockEntity extends BlockEntity implements MenuProvider {
    public static final int MAX_ENERGY_LIMIT = 100_000_000;
    public static final int MAX_MATTER_LIMIT = 1_000_000;
    public static final int STATE_SOLID = 1;
    public static final int STATE_LIQUID = 2;
    public static final int STATE_GAS = 4;
    public static final int STATE_PRIMAL = 8;
    public static final int STATE_ALL = STATE_SOLID | STATE_LIQUID | STATE_GAS | STATE_PRIMAL;
    public static final int MAX_FILTER_ENTRIES = 8;

    public enum GateMode {
        ALWAYS_OPEN, REDSTONE_HIGH, REDSTONE_LOW;

        public String key() { return name().toLowerCase(Locale.ROOT); }
        public GateMode next() { var v = values(); return v[(ordinal() + 1) % v.length]; }
        public static GateMode byIndex(int i) { var v = values(); return i >= 0 && i < v.length ? v[i] : ALWAYS_OPEN; }
        public static GateMode load(String raw) {
            for (var v : values()) if (v.key().equalsIgnoreCase(raw)) return v;
            return ALWAYS_OPEN;
        }
    }

    public enum ChannelMode {
        BOTH, POWER_ONLY, MATTER_ONLY;

        public String key() { return name().toLowerCase(Locale.ROOT); }
        public ChannelMode next() { var v = values(); return v[(ordinal() + 1) % v.length]; }
        public static ChannelMode byIndex(int i) { var v = values(); return i >= 0 && i < v.length ? v[i] : BOTH; }
        public static ChannelMode load(String raw) {
            for (var v : values()) if (v.key().equalsIgnoreCase(raw)) return v;
            return BOTH;
        }
    }

    public enum DirectionMode {
        BIDIRECTIONAL, ONE_WAY;

        public String key() { return name().toLowerCase(Locale.ROOT); }
        public DirectionMode next() { var v = values(); return v[(ordinal() + 1) % v.length]; }
        public static DirectionMode byIndex(int i) { var v = values(); return i >= 0 && i < v.length ? v[i] : BIDIRECTIONAL; }
        public static DirectionMode load(String raw) {
            for (var v : values()) if (v.key().equalsIgnoreCase(raw)) return v;
            return BIDIRECTIONAL;
        }
    }

    /** Immutable snapshot of a relay's Matter filter rule for zero-allocation hot-path checks. */
    public record FilterRule(int stateMask, boolean whitelist, Set<SubstanceKey> substances) {
        public static final FilterRule OPEN = new FilterRule(STATE_ALL, false, Set.of());

        public FilterRule {
            stateMask &= STATE_ALL;
            substances = Set.copyOf(Objects.requireNonNull(substances));
        }

        public boolean isUnrestricted() {
            return stateMask == STATE_ALL && !whitelist && substances.isEmpty();
        }

        public boolean allowsOrdinary(SubstanceKey key) {
            if (ReservedSubstances.reserved(key)) return false;
            int bit = switch (key.phase()) {
                case SOLID -> STATE_SOLID;
                case LIQUID -> STATE_LIQUID;
                case GAS -> STATE_GAS;
            };
            if ((stateMask & bit) == 0) return false;
            if (substances.isEmpty()) return !whitelist;
            boolean listed = substances.contains(key);
            return whitelist ? listed : !listed;
        }

        public boolean allowsPrimal() {
            return (stateMask & STATE_PRIMAL) != 0;
        }

        public static List<SubstanceKey> sanitize(List<SubstanceKey> raw) {
            if (raw == null || raw.isEmpty()) return List.of();
            var clean = new LinkedHashSet<SubstanceKey>();
            for (var key : raw) {
                if (key == null || ReservedSubstances.reserved(key)) continue;
                if (key.id().toString().length() > CellPartitionData.MAX_ID_LENGTH) continue;
                clean.add(key);
                if (clean.size() >= MAX_FILTER_ENTRIES) break;
            }
            return List.copyOf(clean);
        }
    }

    private @Nullable UUID owner;
    private SecurityMode securityMode = SecurityMode.PUBLIC;
    private String customName = "";
    private GateMode gateMode = GateMode.ALWAYS_OPEN;
    private ChannelMode channelMode = ChannelMode.BOTH;
    private DirectionMode directionMode = DirectionMode.BIDIRECTIONAL;
    private int energyLimit;
    private int matterLimit;
    private int stateMask = STATE_ALL;
    private boolean filterWhitelist;
    private List<SubstanceKey> filterSubstances = List.of();
    private FilterRule filterRule = FilterRule.OPEN;
    private boolean redstonePowered;
    private boolean gateOpen = true;
    private boolean gateEvaluated = true;
    private long matterBudgetTick = Long.MIN_VALUE;
    private long matterBudgetSpent;
    private long stateRevision = 1L;
    private String revisionToken = "1";

    public MatterCableRelayBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.MATTER_CABLE_RELAY_ENTITY.get(), pos, state);
    }

    public static List<SubstanceKey> sanitizeFilter(List<SubstanceKey> raw) {
        return FilterRule.sanitize(raw);
    }

    public @Nullable UUID owner() { return owner; }
    public SecurityMode securityMode() { return securityMode; }
    public String customName() { return customName; }
    public void setCustomName(String next) {
        String clean = CustomNames.sanitize(next);
        if (customName.equals(clean)) return;
        customName = clean;
        setChanged();
    }
    public GateMode gateMode() { return gateMode; }
    public ChannelMode channelMode() { return channelMode; }
    public DirectionMode directionMode() { return directionMode; }
    public boolean isOneWay() { return directionMode == DirectionMode.ONE_WAY; }
    public Direction outputSide() { return getBlockState().getValue(MatterCableRelayBlock.FACING); }
    public Direction inputSide() { return outputSide().getOpposite(); }
    public int energyLimit() { return energyLimit; }
    public int matterLimit() { return matterLimit; }
    public int stateMask() { return stateMask; }
    public boolean filterWhitelist() { return filterWhitelist; }
    public List<SubstanceKey> filterSubstances() { return filterSubstances; }
    public FilterRule filterRule() { return filterRule; }
    public boolean hasFilter() { return !filterRule.isUnrestricted(); }
    public boolean hasRoutingConstraint() { return isOneWay() || !filterRule.isUnrestricted(); }
    public boolean redstonePowered() { ensureGateEvaluated(); return redstonePowered; }
    public boolean isOpen() { ensureGateEvaluated(); return gateOpen; }
    /** Monotonic primitive revision bumped only when routing state changes (zero allocation hot-path check). */
    public long stateRevision() { return stateRevision; }
    /** Pre-cached string form of {@link #stateRevision()}, allocated only on actual state changes. */
    public String revisionToken() { return revisionToken; }

    /**
     * Remaining shared Matter throughput budget (u/t) on this relay for {@code gameTick}.
     * Returns {@link Long#MAX_VALUE} when no Matter rate cap is configured.
     */
    long remainingMatterBudget(long gameTick) {
        if (!allowsMatter()) return 0L;
        if (matterLimit <= 0) return Long.MAX_VALUE;
        if (matterBudgetTick != gameTick) {
            matterBudgetTick = gameTick;
            matterBudgetSpent = 0L;
        }
        return Math.max(0L, matterLimit - matterBudgetSpent);
    }

    /** Consumes shared Matter throughput budget for {@code gameTick}; multiple controllers share one relay cap. */
    void spendMatterBudget(long gameTick, long units) {
        if (matterLimit <= 0 || units <= 0L) return;
        if (matterBudgetTick != gameTick) {
            matterBudgetTick = gameTick;
            matterBudgetSpent = 0L;
        }
        matterBudgetSpent = Math.min(matterLimit, matterBudgetSpent + units);
    }

    /** Evaluates redstone gate safely before first channel use after chunk load; fails closed if neighbor chunks are unloaded. */
    private void ensureGateEvaluated() {
        if (gateEvaluated) return;
        if (gateMode == GateMode.ALWAYS_OPEN) {
            gateOpen = true;
            gateEvaluated = true;
            return;
        }
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread()) return;
        for (Direction d : Direction.values()) {
            BlockPos adj = worldPosition.relative(d);
            if (server.getChunkSource().getChunkNow(adj.getX() >> 4, adj.getZ() >> 4) == null) {
                gateOpen = false;
                return;
            }
        }
        boolean powered = server.hasNeighborSignal(worldPosition);
        redstonePowered = powered;
        gateOpen = gateMode == GateMode.REDSTONE_HIGH ? powered : !powered;
        gateEvaluated = true;
    }

    private void rebuildFilterRule() {
        filterRule = (stateMask == STATE_ALL && !filterWhitelist && filterSubstances.isEmpty())
                ? FilterRule.OPEN
                : new FilterRule(stateMask, filterWhitelist, Set.copyOf(filterSubstances));
    }

    private void bumpRevision() {
        stateRevision++;
        revisionToken = Long.toString(stateRevision);
    }

    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            server.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    public boolean allowsMatter() {
        if (!networkLive()) return false;
        ensureGateEvaluated();
        return gateOpen && (channelMode == ChannelMode.BOTH || channelMode == ChannelMode.MATTER_ONLY);
    }

    public boolean allowsPower() {
        if (!networkLive()) return false;
        ensureGateEvaluated();
        return gateOpen && (channelMode == ChannelMode.BOTH || channelMode == ChannelMode.POWER_ONLY);
    }

    public int effectiveEnergyRate() {
        if (!allowsPower()) return 0;
        return energyLimit > 0 ? energyLimit : Integer.MAX_VALUE;
    }

    public int effectiveMatterRate() {
        if (!allowsMatter()) return 0;
        return matterLimit > 0 ? matterLimit : Integer.MAX_VALUE;
    }

    public List<Direction> connectionSides() {
        Direction facing = getBlockState().getValue(MatterCableRelayBlock.FACING);
        return List.of(facing, facing.getOpposite());
    }

    public boolean connectsDirection(Direction side) {
        Direction facing = getBlockState().getValue(MatterCableRelayBlock.FACING);
        return side == facing || side == facing.getOpposite();
    }

    boolean networkLive() {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread() || isRemoved() || owner == null) return false;
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return chunk != null && chunk.getBlockEntity(worldPosition) == this;
    }

    public boolean canManageSecurity(Player player) { return MachineAccess.canManage(owner, player); }

    public boolean setSecurityMode(Player player, SecurityMode next) {
        if (!networkLive() || player.isSpectator() || !canManageSecurity(player) || next == null) return false;
        if (securityMode == next) return true;
        securityMode = next;
        bumpRevision();
        setChanged();
        touchTopology();
        return true;
    }

    public boolean cycleSecurityMode(Player player, boolean forward) {
        return setSecurityMode(player, forward ? securityMode.next() : securityMode.previous());
    }

    public boolean reverseDirection(Player player) {
        if (!networkLive() || player.isSpectator() || !canAccess(player)) return false;
        if (!(level instanceof ServerLevel server)) return false;
        BlockState state = getBlockState();
        Direction flipped = state.getValue(MatterCableRelayBlock.FACING).getOpposite();
        server.setBlock(worldPosition, state.setValue(MatterCableRelayBlock.FACING, flipped), Block.UPDATE_ALL);
        afterRotation(server);
        ModSounds.play(server, worldPosition, ModSounds.WRENCH_ROTATE, 1.0F, 1.0F);
        return true;
    }

    public boolean canRemove(Player player) { return canAccess(player); }
    public boolean canAccess(Player player) { return MachineAccess.allows(level, owner, securityMode, player); }
    public boolean stillValid(Player player) { return canAccess(player) && Container.stillValidBlockEntity(this, player); }

    public void placedBy(@Nullable LivingEntity placer) {
        placedBy(placer, net.minecraft.world.item.ItemStack.EMPTY);
    }

    public void placedBy(@Nullable LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        if (securityMode == SecurityMode.PUBLIC) {
            securityMode = MachineAccess.placementDefault(level, placer);
        }
        String fromItem = CustomNames.fromStack(stack);
        if (!fromItem.isEmpty()) customName = fromItem;
        assignOwner(placer instanceof Player player && !(player instanceof FakePlayer) ? player.getUUID() : null);
        refreshNow();
        touchTopology();
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
        bumpRevision();
        setChanged();
        touchTopology();
    }

    public void afterRotation(ServerLevel server) {
        bumpRevision();
        touchTopology();
        refreshNow();
    }

    public void configure(GateMode nextGate, ChannelMode nextChannel, int nextEnergyLimit, int nextMatterLimit) {
        configure(nextGate, nextChannel, nextEnergyLimit, nextMatterLimit, customName);
    }

    public void configure(GateMode nextGate, ChannelMode nextChannel, int nextEnergyLimit, int nextMatterLimit, String nextCustomName) {
        configure(nextGate, nextChannel, directionMode, nextEnergyLimit, nextMatterLimit,
                stateMask, filterWhitelist, filterSubstances, nextCustomName);
    }

    public void configure(GateMode nextGate, ChannelMode nextChannel, DirectionMode nextDirection,
                          int nextEnergyLimit, int nextMatterLimit,
                          int nextStateMask, boolean nextWhitelist, List<SubstanceKey> nextFilter,
                          String nextCustomName) {
        GateMode g = Objects.requireNonNull(nextGate);
        ChannelMode c = Objects.requireNonNull(nextChannel);
        DirectionMode d = Objects.requireNonNull(nextDirection);
        int eLim = Math.clamp(nextEnergyLimit, 0, MAX_ENERGY_LIMIT);
        int mLim = Math.clamp(nextMatterLimit, 0, MAX_MATTER_LIMIT);
        int sMask = nextStateMask & STATE_ALL;
        List<SubstanceKey> cleanFilter = sanitizeFilter(nextFilter);
        String name = CustomNames.sanitize(nextCustomName);
        boolean routingChanged = gateMode != g || channelMode != c || directionMode != d
                || energyLimit != eLim || matterLimit != mLim
                || stateMask != sMask || filterWhitelist != nextWhitelist || !filterSubstances.equals(cleanFilter);
        boolean nameChanged = !customName.equals(name);
        if (!routingChanged && !nameChanged) return;
        customName = name;
        if (!routingChanged) {
            setChanged();
            return;
        }
        gateMode = g;
        channelMode = c;
        directionMode = d;
        energyLimit = eLim;
        matterLimit = mLim;
        stateMask = sMask;
        filterWhitelist = nextWhitelist;
        filterSubstances = cleanFilter;
        rebuildFilterRule();
        bumpRevision();
        setChanged();
        refreshNow();
        touchTopology();
    }

    public void refreshNow() {
        if (!(level instanceof ServerLevel server)) return;
        boolean powered = server.hasNeighborSignal(worldPosition);
        boolean nextOpen = switch (gateMode) {
            case ALWAYS_OPEN -> true;
            case REDSTONE_HIGH -> powered;
            case REDSTONE_LOW -> !powered;
        };
        boolean nextDirectional = isOneWay();
        redstonePowered = powered;
        boolean topologyChanged = nextOpen != gateOpen || !gateEvaluated;
        gateOpen = nextOpen;
        gateEvaluated = true;
        BlockState state = getBlockState();
        BlockState target = state
                .setValue(MatterCableRelayBlock.OPEN, nextOpen)
                .setValue(MatterCableRelayBlock.DIRECTIONAL, nextDirectional);
        if (target != state) {
            server.setBlock(worldPosition, target, Block.UPDATE_ALL);
            topologyChanged = true;
        }
        if (topologyChanged) {
            bumpRevision();
            touchTopology();
        }
    }

    private void touchTopology() {
        if (level instanceof ServerLevel server) {
            ChunkRevisions.touchLoaded(server, worldPosition);
            MatterCableBlock.refreshLoadedAround(server, worldPosition);
        }
    }

    public void writeMenuInit(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
        buf.writeVarInt(gateMode.ordinal());
        buf.writeVarInt(channelMode.ordinal());
        buf.writeVarInt(directionMode.ordinal());
        buf.writeVarInt(outputSide().ordinal());
        buf.writeVarInt(energyLimit);
        buf.writeVarInt(matterLimit);
        buf.writeVarInt(stateMask);
        buf.writeBoolean(filterWhitelist);
        buf.writeVarInt(filterSubstances.size());
        for (var key : filterSubstances) {
            buf.writeUtf(key.id().toString(), CellPartitionData.MAX_ID_LENGTH);
            buf.writeVarInt(key.phase().ordinal());
        }
        buf.writeBoolean(gateOpen);
        buf.writeBoolean(redstonePowered);
        buf.writeUtf(customName, CustomNames.MAX_LENGTH);
    }

    @Override public void setRemoved() {
        bumpRevision();
        if (level instanceof ServerLevel server) ChunkRevisions.touchLoaded(server, worldPosition);
        super.setRemoved();
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("schema", 1);
        if (owner != null) output.putString("owner", owner.toString());
        if (securityMode != SecurityMode.PUBLIC) output.putString("security", securityMode.getSerializedName());
        if (!customName.isEmpty()) output.putString("custom_name", customName);
        if (gateMode != GateMode.ALWAYS_OPEN) output.putString("gate_mode", gateMode.key());
        if (channelMode != ChannelMode.BOTH) output.putString("channel_mode", channelMode.key());
        if (directionMode != DirectionMode.BIDIRECTIONAL) output.putString("direction_mode", directionMode.key());
        if (energyLimit > 0) output.putInt("energy_limit", energyLimit);
        if (matterLimit > 0) output.putInt("matter_limit", matterLimit);
        if (stateMask != STATE_ALL) output.putInt("state_mask", stateMask);
        if (filterWhitelist) output.putBoolean("filter_whitelist", true);
        if (!filterSubstances.isEmpty()) {
            output.putInt("filter_count", filterSubstances.size());
            for (int i = 0; i < filterSubstances.size(); i++) {
                var key = filterSubstances.get(i);
                output.putString("filter_" + i, key.id() + "|" + key.phase().name().toLowerCase(Locale.ROOT));
            }
        }
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        owner = null;
        securityMode = SecurityMode.PUBLIC;
        customName = "";
        gateMode = GateMode.ALWAYS_OPEN;
        channelMode = ChannelMode.BOTH;
        directionMode = DirectionMode.BIDIRECTIONAL;
        energyLimit = 0;
        matterLimit = 0;
        stateMask = STATE_ALL;
        filterWhitelist = false;
        filterSubstances = List.of();
        filterRule = FilterRule.OPEN;
        gateOpen = true;
        gateEvaluated = true;
        if (input.getIntOr("schema", -1) != 1) return;
        input.getString("owner").ifPresent(raw -> {
            try { owner = UUID.fromString(raw); } catch (IllegalArgumentException ignored) { owner = null; }
        });
        securityMode = input.getString("security").filter(SecurityMode::validName).map(SecurityMode::load).orElse(SecurityMode.PUBLIC);
        customName = CustomNames.sanitize(input.getStringOr("custom_name", ""));
        gateMode = GateMode.load(input.getStringOr("gate_mode", "always_open"));
        gateEvaluated = gateMode == GateMode.ALWAYS_OPEN;
        gateOpen = gateEvaluated;
        channelMode = ChannelMode.load(input.getStringOr("channel_mode", "both"));
        directionMode = DirectionMode.load(input.getStringOr("direction_mode", "bidirectional"));
        energyLimit = Math.clamp(input.getIntOr("energy_limit", 0), 0, MAX_ENERGY_LIMIT);
        matterLimit = Math.clamp(input.getIntOr("matter_limit", 0), 0, MAX_MATTER_LIMIT);
        stateMask = input.getIntOr("state_mask", STATE_ALL) & STATE_ALL;
        filterWhitelist = input.getBooleanOr("filter_whitelist", false);
        int filterCount = Math.clamp(input.getIntOr("filter_count", 0), 0, MAX_FILTER_ENTRIES);
        if (filterCount > 0) {
            var loaded = new ArrayList<SubstanceKey>(filterCount);
            for (int i = 0; i < filterCount; i++) {
                String encoded = input.getStringOr("filter_" + i, "");
                var parsed = parseSubstanceEntry(encoded);
                if (parsed != null) loaded.add(parsed);
            }
            filterSubstances = sanitizeFilter(loaded);
        }
        rebuildFilterRule();
        bumpRevision();
        if (level instanceof ServerLevel) touchTopology();
    }

    private static @Nullable SubstanceKey parseSubstanceEntry(String encoded) {
        int sep = encoded.lastIndexOf('|');
        if (sep <= 0 || sep >= encoded.length() - 1) return null;
        String idRaw = encoded.substring(0, sep);
        String phaseRaw = encoded.substring(sep + 1).toUpperCase(Locale.ROOT);
        try {
            return SubstanceKey.of(idRaw, Phase.valueOf(phaseRaw));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Override public Component getDisplayName() {
        return CustomNames.displayName(customName, "block.rematter.matter_cable_relay");
    }

    @Override public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return canAccess(player) ? new MatterCableRelayMenu(id, inventory, this) : null;
    }
}
