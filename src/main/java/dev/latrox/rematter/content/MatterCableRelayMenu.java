package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.content.MatterCableRelayBlockEntity.ChannelMode;
import dev.latrox.rematter.content.MatterCableRelayBlockEntity.DirectionMode;
import dev.latrox.rematter.content.MatterCableRelayBlockEntity.GateMode;
import dev.latrox.rematter.network.MatterCableRelayPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/** Configuration menu for Matter Cable Relay with live S2C open/redstone/direction/filter state sync. */
public final class MatterCableRelayMenu extends AbstractContainerMenu {
    public static final int CYCLE_SECURITY = 0;
    public static final int CYCLE_SECURITY_BACK = 1;
    public static final int REVERSE_DIRECTION = 2;
    private final Player viewer;
    private final @Nullable MatterCableRelayBlockEntity valve;
    private final BlockPos position;
    private final DataSlot securityMode = DataSlot.standalone();
    private final DataSlot canManageSecurity = DataSlot.standalone();
    private GateMode gateMode;
    private ChannelMode channelMode;
    private DirectionMode directionMode;
    private Direction facing;
    private int energyLimit;
    private int matterLimit;
    private int stateMask;
    private boolean filterWhitelist;
    private List<SubstanceKey> filterSubstances;
    private boolean open;
    private boolean powered;
    private String customName = "";
    private boolean initialSent;

    public MatterCableRelayMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        super(ModContent.MATTER_CABLE_RELAY_MENU.get(), id);
        this.viewer = playerInventory.player;
        this.valve = null;
        this.position = buf.readBlockPos();
        this.gateMode = GateMode.byIndex(buf.readVarInt());
        this.channelMode = ChannelMode.byIndex(buf.readVarInt());
        this.directionMode = DirectionMode.byIndex(buf.readVarInt());
        int fOrd = buf.readVarInt();
        Direction[] dirs = Direction.values();
        this.facing = fOrd >= 0 && fOrd < dirs.length ? dirs[fOrd] : Direction.NORTH;
        this.energyLimit = Math.clamp(buf.readVarInt(), 0, MatterCableRelayBlockEntity.MAX_ENERGY_LIMIT);
        this.matterLimit = Math.clamp(buf.readVarInt(), 0, MatterCableRelayBlockEntity.MAX_MATTER_LIMIT);
        this.stateMask = buf.readVarInt() & MatterCableRelayBlockEntity.STATE_ALL;
        this.filterWhitelist = buf.readBoolean();
        int count = Math.clamp(buf.readVarInt(), 0, MatterCableRelayBlockEntity.MAX_FILTER_ENTRIES);
        var loaded = new ArrayList<SubstanceKey>(count);
        Phase[] phases = Phase.values();
        for (int i = 0; i < count; i++) {
            String rawId = buf.readUtf(CellPartitionData.MAX_ID_LENGTH);
            int pOrd = buf.readVarInt();
            if (pOrd >= 0 && pOrd < phases.length) {
                try {
                    loaded.add(SubstanceKey.of(rawId, phases[pOrd]));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        this.filterSubstances = MatterCableRelayBlockEntity.sanitizeFilter(loaded);
        this.open = buf.readBoolean();
        this.powered = buf.readBoolean();
        if (buf.readableBytes() > 0) this.customName = CustomNames.sanitize(buf.readUtf(CustomNames.MAX_LENGTH));
        addDataSlot(securityMode);
        addDataSlot(canManageSecurity);
    }

    public MatterCableRelayMenu(int id, Inventory playerInventory, MatterCableRelayBlockEntity valve) {
        super(ModContent.MATTER_CABLE_RELAY_MENU.get(), id);
        this.viewer = playerInventory.player;
        this.valve = valve;
        this.position = valve.getBlockPos();
        this.gateMode = valve.gateMode();
        this.channelMode = valve.channelMode();
        this.directionMode = valve.directionMode();
        this.facing = valve.outputSide();
        this.energyLimit = valve.energyLimit();
        this.matterLimit = valve.matterLimit();
        this.stateMask = valve.stateMask();
        this.filterWhitelist = valve.filterWhitelist();
        this.filterSubstances = valve.filterSubstances();
        this.open = valve.isOpen();
        this.powered = valve.redstonePowered();
        this.customName = valve.customName();
        securityMode.set(valve.securityMode().ordinal());
        addDataSlot(securityMode);
        canManageSecurity.set(valve.canManageSecurity(viewer) ? 1 : 0);
        addDataSlot(canManageSecurity);
    }

    public BlockPos position() { return position; }
    public GateMode gateMode() { return gateMode; }
    public ChannelMode channelMode() { return channelMode; }
    public DirectionMode directionMode() { return directionMode; }
    public Direction facing() { return valve != null ? valve.outputSide() : facing; }
    public int energyLimit() { return energyLimit; }
    public int matterLimit() { return matterLimit; }
    public int stateMask() { return stateMask; }
    public boolean filterWhitelist() { return filterWhitelist; }
    public List<SubstanceKey> filterSubstances() { return filterSubstances; }
    public boolean isOpen() { return open; }
    public boolean redstonePowered() { return powered; }
    public String customName() { return valve != null ? valve.customName() : customName; }
    public SecurityMode securityMode() { return valve != null ? valve.securityMode() : SecurityMode.byIndex(securityMode.get()); }
    public boolean canManageSecurity() { return valve != null ? valve.canManageSecurity(viewer) : canManageSecurity.get() != 0; }

    @Override
    public boolean clickMenuButton(Player player, int action) {
        if (valve == null || player.containerMenu != this || player.isSpectator() || !stillValid(player)) return false;
        boolean changed;
        if (action == CYCLE_SECURITY || action == CYCLE_SECURITY_BACK) {
            changed = valve.cycleSecurityMode(player, action == CYCLE_SECURITY);
        } else if (action == REVERSE_DIRECTION) {
            changed = valve.reverseDirection(player);
        } else {
            return false;
        }
        if (changed) broadcastChanges();
        return changed;
    }

    public void acceptState(MatterCableRelayPayload.State state) {
        this.gateMode = state.gateMode();
        this.channelMode = state.channelMode();
        this.directionMode = state.directionMode();
        this.facing = state.facing();
        this.energyLimit = state.energyLimit();
        this.matterLimit = state.matterLimit();
        this.stateMask = state.stateMask();
        this.filterWhitelist = state.filterWhitelist();
        this.filterSubstances = state.filterSubstances();
        this.open = state.open();
        this.powered = state.powered();
        this.customName = state.customName();
    }

    public void configureFromClient(ServerPlayer player, MatterCableRelayPayload.Configure payload) {
        if (valve == null || !stillValid(player) || !valve.canAccess(player)) return;
        valve.configure(payload.gateMode(), payload.channelMode(), payload.directionMode(),
                payload.energyLimit(), payload.matterLimit(),
                payload.stateMask(), payload.filterWhitelist(), payload.filterSubstances(),
                payload.customName());
        broadcastChanges();
    }

    @Override
    public void broadcastChanges() {
        if (valve != null) {
            securityMode.set(valve.securityMode().ordinal());
            canManageSecurity.set(valve.canManageSecurity(viewer) ? 1 : 0);
        }
        super.broadcastChanges();
        if (valve == null || !(viewer instanceof ServerPlayer serverPlayer)) return;
        var g = valve.gateMode();
        var c = valve.channelMode();
        var d = valve.directionMode();
        var f = valve.outputSide();
        int e = valve.energyLimit();
        int m = valve.matterLimit();
        int sm = valve.stateMask();
        boolean wl = valve.filterWhitelist();
        var fs = valve.filterSubstances();
        boolean o = valve.isOpen();
        boolean p = valve.redstonePowered();
        String n = valve.customName();
        if (!initialSent || gateMode != g || channelMode != c || directionMode != d || facing != f
                || energyLimit != e || matterLimit != m || stateMask != sm || filterWhitelist != wl
                || !filterSubstances.equals(fs) || open != o || powered != p || !customName.equals(n)) {
            initialSent = true;
            gateMode = g;
            channelMode = c;
            directionMode = d;
            facing = f;
            energyLimit = e;
            matterLimit = m;
            stateMask = sm;
            filterWhitelist = wl;
            filterSubstances = fs;
            open = o;
            powered = p;
            customName = n;
            PacketDistributor.sendToPlayer(serverPlayer,
                    new MatterCableRelayPayload.State(containerId, g, c, d, f, e, m, sm, wl, fs, o, p, n));
        }
    }

    @Override public boolean stillValid(Player player) { return valve == null || valve.stillValid(player); }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
