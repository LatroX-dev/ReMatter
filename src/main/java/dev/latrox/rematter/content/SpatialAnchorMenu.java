package dev.latrox.rematter.content;

import dev.latrox.rematter.network.ChunkMapPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/** Synchronized container menu for Spatial Anchor (25-bit 5x5 chunk mask, redstone, 32-bit FE, chunk quota, live 5x5 map, ports, security, custom name). */
public final class SpatialAnchorMenu extends AbstractContainerMenu {
    public static final int CYCLE_SECURITY = 1;
    public static final int CYCLE_SECURITY_BACK = 2;
    public static final int CYCLE_PORT = 40;
    public static final int CYCLE_PORT_BACK = 46;

    private final @Nullable SpatialAnchorBlockEntity anchor;
    private final Player player;
    private final DataSlot maskLow = DataSlot.standalone();
    private final DataSlot maskHigh = DataSlot.standalone();
    private final DataSlot redstone = DataSlot.standalone();
    private final DataSlot energyLow = DataSlot.standalone();
    private final DataSlot energyHigh = DataSlot.standalone();
    private final DataSlot active = DataSlot.standalone();
    private final DataSlot forcedChunks = DataSlot.standalone();
    private final DataSlot ownerTotal = DataSlot.standalone();
    private final DataSlot maxChunks = DataSlot.standalone();
    private final DataSlot reasonCode = DataSlot.standalone();
    private final DataSlot security = DataSlot.standalone();
    private final DataSlot canManage = DataSlot.standalone();
    private final DataSlot[] portModes = new DataSlot[MachinePortMode.Side.values().length];
    private BlockPos blockPos = BlockPos.ZERO;
    private String customName = "";
    private boolean initialMapSent;

    public SpatialAnchorMenu(int id, Inventory inventory, @Nullable RegistryFriendlyByteBuf buf) {
        this(id, inventory, (SpatialAnchorBlockEntity) null);
        if (buf != null && buf.readableBytes() > 0) {
            blockPos = buf.readBlockPos();
            int mask = SpatialAnchorBlockEntity.sanitizeMask(buf.readVarInt());
            maskLow.set(mask & 0xFFFF);
            maskHigh.set((mask >>> 16) & 0xFFFF);
            redstone.set(buf.readVarInt());
            customName = CustomNames.sanitize(buf.readUtf(CustomNames.MAX_LENGTH));
        }
    }

    public SpatialAnchorMenu(int id, Inventory inventory, @Nullable SpatialAnchorBlockEntity anchor) {
        super(ModContent.SPATIAL_ANCHOR_MENU.get(), id);
        this.anchor = anchor;
        this.player = inventory.player;
        maskLow.set(SpatialAnchorBlockEntity.MASK_1X1 & 0xFFFF);
        maskHigh.set((SpatialAnchorBlockEntity.MASK_1X1 >>> 16) & 0xFFFF);
        for (var slot : new DataSlot[]{maskLow, maskHigh, redstone, energyLow, energyHigh, active, forcedChunks, ownerTotal, maxChunks, reasonCode, security, canManage}) {
            addDataSlot(slot);
        }
        for (var side : MachinePortMode.Side.values()) {
            var slot = DataSlot.standalone();
            slot.set((SpatialAnchorBlockEntity.isConfigurableSide(side) ? MachinePortMode.AUTOMATIC : MachinePortMode.DISABLED).ordinal());
            portModes[side.ordinal()] = addDataSlot(slot);
        }
        if (anchor != null) {
            blockPos = anchor.getBlockPos();
            customName = anchor.customName();
            syncFromAnchor();
        }
    }

    private void syncFromAnchor() {
        if (anchor == null) return;
        var st = anchor.anchorStatus();
        int stored = anchor.energy();
        int mask = anchor.chunkMask();
        maskLow.set(mask & 0xFFFF);
        maskHigh.set((mask >>> 16) & 0xFFFF);
        redstone.set(anchor.redstoneMode().ordinal());
        energyLow.set(stored & 0xFFFF);
        energyHigh.set((stored >>> 16) & 0xFFFF);
        active.set(st.active() ? 1 : 0);
        forcedChunks.set(st.forcedChunks());
        ownerTotal.set(st.ownerTotalChunks());
        maxChunks.set(st.maxChunks());
        reasonCode.set(encodeReason(st.reason()));
        security.set(anchor.securityMode().ordinal());
        canManage.set(anchor.canManageSecurity(player) ? 1 : 0);
        for (var side : MachinePortMode.Side.values()) {
            portModes[side.ordinal()].set(anchor.portMode(side).ordinal());
        }
        customName = anchor.customName();
    }

    public static int encodeReason(String reason) {
        return switch (reason) {
            case "active" -> 1;
            case "missing_power" -> 2;
            case "redstone_paused" -> 3;
            case "limit" -> 4;
            case "disabled" -> 5;
            case "offline" -> 6;
            default -> 0;
        };
    }

    public static String decodeReason(int code) {
        return switch (code) {
            case 1 -> "active";
            case 2 -> "missing_power";
            case 3 -> "redstone_paused";
            case 4 -> "limit";
            case 5 -> "disabled";
            case 6 -> "offline";
            default -> "idle";
        };
    }

    public void sendChunkMap(ServerPlayer sp) {
        if (anchor == null || !(anchor.getLevel() instanceof ServerLevel server)) return;
        initialMapSent = true;
        var map = ChunkMapSnapshot.capture(server, anchor.getBlockPos(), anchor.owner(),
                anchor.desiredChunkSet(), sp);
        PacketDistributor.sendToPlayer(sp, new ChunkMapPayload.Sync(containerId, map));
    }

    @Override public void broadcastChanges() {
        syncFromAnchor();
        super.broadcastChanges();
        if (!initialMapSent && anchor != null && player instanceof ServerPlayer sp) {
            sendChunkMap(sp);
        }
    }

    @Override public boolean clickMenuButton(Player p, int button) {
        if (anchor == null || p.isSpectator() || !stillValid(p)) return false;
        if (button == CYCLE_SECURITY || button == CYCLE_SECURITY_BACK) {
            boolean ok = anchor.cycleSecurityMode(p, button == CYCLE_SECURITY);
            if (ok) broadcastChanges();
            return ok;
        }
        if (button >= CYCLE_PORT && button < CYCLE_PORT + MachinePortMode.Side.values().length) {
            boolean ok = anchor.cyclePort(p, MachinePortMode.Side.values()[button - CYCLE_PORT], true);
            if (ok) broadcastChanges();
            return ok;
        }
        if (button >= CYCLE_PORT_BACK && button < CYCLE_PORT_BACK + MachinePortMode.Side.values().length) {
            boolean ok = anchor.cyclePort(p, MachinePortMode.Side.values()[button - CYCLE_PORT_BACK], false);
            if (ok) broadcastChanges();
            return ok;
        }
        return false;
    }

    public boolean configure(ServerPlayer p, int nextMask, RedstoneMode nextRs, String nextName) {
        if (anchor == null || p.isSpectator() || !stillValid(p) || !anchor.canAccess(p)) return false;
        anchor.configureMask(nextMask, nextRs, nextName);
        broadcastChanges();
        sendChunkMap(p);
        return true;
    }

    public BlockPos blockPos() { return blockPos; }
    public int chunkMask() {
        if (anchor != null) return anchor.chunkMask();
        return SpatialAnchorBlockEntity.sanitizeMask(((maskHigh.get() & 0xFFFF) << 16) | (maskLow.get() & 0xFFFF));
    }
    public int radius() {
        int m = chunkMask();
        if (m == SpatialAnchorBlockEntity.MASK_1X1) return 0;
        if (m == SpatialAnchorBlockEntity.MASK_3X3) return 1;
        if (m == SpatialAnchorBlockEntity.MASK_5X5) return 2;
        return -1;
    }
    public int desiredChunks() { return Integer.bitCount(chunkMask() & SpatialAnchorBlockEntity.VALID_MASK_BITS); }
    public RedstoneMode redstoneMode() {
        var values = RedstoneMode.values();
        int i = redstone.get();
        return i >= 0 && i < values.length ? values[i] : RedstoneMode.IGNORED;
    }
    public int energy() {
        if (anchor != null) return anchor.energy();
        return ((energyHigh.get() & 0xFFFF) << 16) | (energyLow.get() & 0xFFFF);
    }
    public int drainPerTick() { return desiredChunks() * SpatialAnchorBlockEntity.DRAIN_PER_CHUNK; }
    public boolean active() { return active.get() != 0; }
    public int forcedChunks() { return forcedChunks.get(); }
    public int ownerTotalChunks() { return ownerTotal.get(); }
    public int maxChunks() { return maxChunks.get(); }
    public String reason() { return decodeReason(reasonCode.get()); }
    public SecurityMode securityMode() { return SecurityMode.byIndex(security.get()); }
    public boolean canManageSecurity() { return canManage.get() != 0; }
    public MachinePortMode portMode(MachinePortMode.Side side) {
        return SpatialAnchorBlockEntity.sanitizePortMode(side, MachinePortMode.byIndex(portModes[side.ordinal()].get()));
    }
    public String customName() { return customName; }
    public void setClientCustomName(String value) { customName = CustomNames.sanitize(value); }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return anchor == null || anchor.stillValid(player); }
}
