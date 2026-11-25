package dev.latrox.rematter.primal;

import dev.latrox.rematter.content.CustomNames;
import dev.latrox.rematter.content.MachinePortMode;
import dev.latrox.rematter.content.ModContent;
import dev.latrox.rematter.content.RedstoneMode;
import dev.latrox.rematter.content.SecurityMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Synchronized container menu for regular same-dimension Entangled Link (status, peer coords, distance, unpair, ports, label, security). */
public final class EntangledLinkMenu extends AbstractContainerMenu {
    public static final int CYCLE_SECURITY = 1;
    public static final int CYCLE_SECURITY_BACK = 2;
    public static final int CYCLE_REDSTONE = 3;
    public static final int CYCLE_REDSTONE_BACK = 4;
    public static final int CYCLE_PORT = 40;
    public static final int CYCLE_PORT_BACK = 46;

    private final @Nullable EntangledLinkBlockEntity link;
    private final Player player;
    private final DataSlot linkState = DataSlot.standalone(); // 0=unpaired, 1=linked, 2=remote_unloaded
    private final DataSlot distance = DataSlot.standalone();
    private final DataSlot security = DataSlot.standalone();
    private final DataSlot canManage = DataSlot.standalone();
    private final DataSlot redstoneMode = DataSlot.standalone();
    private final DataSlot redstonePaused = DataSlot.standalone();
    private final DataSlot[] portModes = new DataSlot[MachinePortMode.Side.values().length];
    private BlockPos blockPos = BlockPos.ZERO;
    private boolean hasPeer;
    private BlockPos peerPos = BlockPos.ZERO;
    private String customName = "";

    public EntangledLinkMenu(int id, Inventory inv, @Nullable RegistryFriendlyByteBuf buf) {
        this(id, inv, (EntangledLinkBlockEntity) null);
        if (buf != null && buf.readableBytes() > 0) {
            blockPos = buf.readBlockPos();
            hasPeer = buf.readBoolean();
            if (hasPeer) peerPos = buf.readBlockPos();
            customName = CustomNames.sanitize(buf.readUtf(CustomNames.MAX_LENGTH));
        }
    }

    public EntangledLinkMenu(int id, Inventory inv, @Nullable EntangledLinkBlockEntity link) {
        super(ModContent.ENTANGLED_LINK_MENU.get(), id);
        this.link = link;
        this.player = inv.player;
        for (var s : new DataSlot[]{linkState, distance, security, canManage, redstoneMode, redstonePaused}) addDataSlot(s);
        for (var side : MachinePortMode.Side.values()) {
            var slot = DataSlot.standalone();
            slot.set((EntangledLinkBlockEntity.isConfigurableSide(side) ? MachinePortMode.AUTOMATIC : MachinePortMode.DISABLED).ordinal());
            portModes[side.ordinal()] = addDataSlot(slot);
        }
        if (link != null) {
            blockPos = link.getBlockPos();
            hasPeer = link.target() != null && link.pair() != null;
            if (hasPeer && link.target() != null) peerPos = link.target();
            customName = link.customName();
            syncFromLink();
        }
    }

    private void syncFromLink() {
        if (link == null) return;
        hasPeer = link.target() != null && link.pair() != null;
        if (hasPeer && link.target() != null) peerPos = link.target();
        int st = 0;
        int dist = 0;
        if (link.getLevel() instanceof ServerLevel server) {
            var diag = EntangledLinks.inspect(server, link);
            st = switch (diag.state()) {
                case PAIRED -> 1;
                case INACTIVE -> 2;
                case UNPAIRED, NONE -> 0;
            };
            dist = diag.distance();
        }
        linkState.set(st);
        distance.set(dist);
        security.set(link.securityMode().ordinal());
        canManage.set(link.canManageSecurity(player) ? 1 : 0);
        redstoneMode.set(link.redstoneMode().ordinal());
        redstonePaused.set(link.redstonePaused() ? 1 : 0);
        for (var side : MachinePortMode.Side.values()) {
            portModes[side.ordinal()].set(link.portMode(side).ordinal());
        }
        customName = link.customName();
    }

    @Override public void broadcastChanges() {
        syncFromLink();
        super.broadcastChanges();
    }

    @Override public boolean clickMenuButton(Player p, int button) {
        if (link == null || p.isSpectator() || !stillValid(p)) return false;
        if (button == CYCLE_SECURITY || button == CYCLE_SECURITY_BACK) {
            boolean ok = link.cycleSecurityMode(p, button == CYCLE_SECURITY);
            if (ok) broadcastChanges();
            return ok;
        }
        if (button == CYCLE_REDSTONE || button == CYCLE_REDSTONE_BACK) {
            boolean ok = link.cycleRedstoneMode(p, button == CYCLE_REDSTONE);
            if (ok) broadcastChanges();
            return ok;
        }
        if (button >= CYCLE_PORT && button < CYCLE_PORT + MachinePortMode.Side.values().length) {
            boolean ok = link.cyclePort(p, MachinePortMode.Side.values()[button - CYCLE_PORT], true);
            if (ok) broadcastChanges();
            return ok;
        }
        if (button >= CYCLE_PORT_BACK && button < CYCLE_PORT_BACK + MachinePortMode.Side.values().length) {
            boolean ok = link.cyclePort(p, MachinePortMode.Side.values()[button - CYCLE_PORT_BACK], false);
            if (ok) broadcastChanges();
            return ok;
        }
        return false;
    }

    public boolean update(ServerPlayer sp, String nextName, boolean clearLink) {
        if (link == null || sp.isSpectator() || !stillValid(sp) || !link.canAccess(sp)) return false;
        link.setCustomName(nextName);
        if (clearLink && link.getLevel() instanceof ServerLevel server) {
            link.unpairBoth(server);
            hasPeer = false;
            peerPos = BlockPos.ZERO;
        }
        broadcastChanges();
        return true;
    }

    public BlockPos blockPos() { return blockPos; }
    public boolean hasPeer() { return hasPeer && linkState.get() != 0; }
    public BlockPos peerPos() { return peerPos; }
    public int linkState() { return linkState.get(); }
    public int distance() { return distance.get(); }
    public SecurityMode securityMode() { return SecurityMode.byIndex(security.get()); }
    public boolean canManageSecurity() { return canManage.get() != 0; }
    public RedstoneMode redstoneMode() { return RedstoneMode.byIndex(redstoneMode.get()); }
    public boolean redstonePaused() { return redstonePaused.get() != 0; }
    public MachinePortMode portMode(MachinePortMode.Side side) {
        return EntangledLinkBlockEntity.sanitizePortMode(side, MachinePortMode.byIndex(portModes[side.ordinal()].get()));
    }
    public String customName() { return customName; }
    public void setClientCustomName(String value) { customName = CustomNames.sanitize(value); }
    public void setClientUnpaired() { hasPeer = false; linkState.set(0); distance.set(0); }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return link == null || link.stillValid(player); }
}
