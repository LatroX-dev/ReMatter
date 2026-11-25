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
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Menu for Dimensional Entangled Link (peer dimension/coords, live state, Anchor Upgrade slot, ports, custom name, security). */
public final class DimensionalEntangledLinkMenu extends AbstractContainerMenu {
    public static final int CYCLE_SECURITY = 1;
    public static final int CYCLE_SECURITY_BACK = 2;
    public static final int CYCLE_REDSTONE = 3;
    public static final int CYCLE_REDSTONE_BACK = 4;
    public static final int CYCLE_PORT = 40;
    public static final int CYCLE_PORT_BACK = 46;

    private final Container container;
    private final @Nullable DimensionalEntangledLinkBlockEntity link;
    private final Player player;
    private final DataSlot linkState = DataSlot.standalone(); // 0=unpaired, 1=paired_loaded, 2=remote_unloaded
    private final DataSlot anchorActive = DataSlot.standalone();
    private final DataSlot ownerTotal = DataSlot.standalone();
    private final DataSlot maxChunks = DataSlot.standalone();
    private final DataSlot security = DataSlot.standalone();
    private final DataSlot canManage = DataSlot.standalone();
    private final DataSlot redstoneMode = DataSlot.standalone();
    private final DataSlot redstonePaused = DataSlot.standalone();
    private final DataSlot[] portModes = new DataSlot[MachinePortMode.Side.values().length];
    private BlockPos blockPos = BlockPos.ZERO;
    private String customName = "";
    private boolean hasPeer;
    private String peerDimension = "";
    private BlockPos peerPos = BlockPos.ZERO;
    private boolean anchorTab;

    public void setAnchorTab(boolean value) { anchorTab = value; }

    public DimensionalEntangledLinkMenu(int id, Inventory inventory, @Nullable RegistryFriendlyByteBuf buf) {
        this(id, inventory, new SimpleContainer(1), null);
        if (buf != null && buf.readableBytes() > 0) {
            blockPos = buf.readBlockPos();
            customName = CustomNames.sanitize(buf.readUtf(CustomNames.MAX_LENGTH));
            hasPeer = buf.readBoolean();
            if (hasPeer) {
                peerDimension = buf.readUtf(128);
                peerPos = buf.readBlockPos();
            }
        }
    }

    public DimensionalEntangledLinkMenu(int id, Inventory inventory, DimensionalEntangledLinkBlockEntity link) {
        this(id, inventory, link, link);
    }

    private DimensionalEntangledLinkMenu(int id, Inventory inventory, Container container, @Nullable DimensionalEntangledLinkBlockEntity link) {
        super(ModContent.DIMENSIONAL_LINK_MENU.get(), id);
        this.container = container;
        this.link = link;
        this.player = inventory.player;
        addSlot(new Slot(container, DimensionalEntangledLinkBlockEntity.SLOT_ANCHOR, 186, 25) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.is(ModContent.ANCHOR_UPGRADE.get()); }
            @Override public int getMaxStackSize() { return 1; }
            @Override public boolean isActive() { return anchorTab; }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 18 + col * 18, 134 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 18 + col * 18, 192));
        }
        for (var s : new DataSlot[]{linkState, anchorActive, ownerTotal, maxChunks, security, canManage, redstoneMode, redstonePaused}) addDataSlot(s);
        for (var side : MachinePortMode.Side.values()) {
            var slot = DataSlot.standalone();
            slot.set((DimensionalEntangledLinkBlockEntity.isConfigurableSide(side) ? MachinePortMode.AUTOMATIC : MachinePortMode.DISABLED).ordinal());
            portModes[side.ordinal()] = addDataSlot(slot);
        }
        if (link != null) {
            blockPos = link.getBlockPos();
            customName = link.customName();
            hasPeer = link.pair() != null && link.peerDimension() != null && link.peerPos() != null;
            if (hasPeer) {
                peerDimension = link.peerDimension().identifier().toString();
                peerPos = link.peerPos();
            }
            syncFromLink();
        }
    }

    private void syncFromLink() {
        if (link == null) return;
        int st = 0;
        if (link.pair() != null && link.peerDimension() != null && link.peerPos() != null) {
            if (link.getLevel() instanceof ServerLevel server && link.owner() != null
                    && EntangledLinks.dimensionalPeer(server, link.getBlockPos(), link.owner(), link.securityMode()).isPresent()) {
                st = 1;
            } else {
                st = 2;
            }
        }
        linkState.set(st);
        var ast = link.anchorStatus();
        anchorActive.set(ast.active() ? 1 : 0);
        ownerTotal.set(ast.ownerTotalChunks());
        maxChunks.set(ast.maxChunks());
        security.set(link.securityMode().ordinal());
        canManage.set(link.canManageSecurity(player) ? 1 : 0);
        redstoneMode.set(link.redstoneMode().ordinal());
        redstonePaused.set(link.redstonePaused() ? 1 : 0);
        for (var side : MachinePortMode.Side.values()) {
            portModes[side.ordinal()].set(link.portMode(side).ordinal());
        }
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

    public boolean update(ServerPlayer p, String nextName, boolean clearLink) {
        if (link == null || p.isSpectator() || !stillValid(p) || !link.canAccess(p)) return false;
        link.setCustomName(nextName);
        if (clearLink) {
            if (link.getLevel() instanceof ServerLevel server) link.unpairBoth(server);
            else link.clearPair();
            hasPeer = false;
            peerDimension = "";
            peerPos = BlockPos.ZERO;
        }
        broadcastChanges();
        return true;
    }

    public void sendChunkMap(ServerPlayer sp) {
        if (link == null || !(link.getLevel() instanceof ServerLevel server)) return;
        var map = dev.latrox.rematter.content.ChunkMapSnapshot.capture(
                server, link.getBlockPos(), link.owner(),
                dev.latrox.rematter.content.ChunkAnchorManager.square(link.getBlockPos(), 0), sp);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                new dev.latrox.rematter.network.ChunkMapPayload.Sync(containerId, map));
    }

    public BlockPos blockPos() { return blockPos; }
    public String customName() { return customName; }
    public void setClientCustomName(String value) { customName = CustomNames.sanitize(value); }
    public void setClientUnpaired() { hasPeer = false; linkState.set(0); }
    public boolean hasPeer() { return hasPeer && linkState.get() != 0; }
    public String peerDimension() { return peerDimension; }
    public BlockPos peerPos() { return peerPos; }
    public int linkState() { return linkState.get(); }
    public boolean anchorActive() { return anchorActive.get() != 0; }
    public int ownerTotalChunks() { return ownerTotal.get(); }
    public int maxChunks() { return maxChunks.get(); }
    public SecurityMode securityMode() { return SecurityMode.byIndex(security.get()); }
    public boolean canManageSecurity() { return canManage.get() != 0; }
    public RedstoneMode redstoneMode() { return RedstoneMode.byIndex(redstoneMode.get()); }
    public boolean redstonePaused() { return redstonePaused.get() != 0; }
    public MachinePortMode portMode(MachinePortMode.Side side) {
        return EntangledLinkBlockEntity.sanitizePortMode(side, MachinePortMode.byIndex(portModes[side.ordinal()].get()));
    }

    @Override public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.is(ModContent.ANCHOR_UPGRADE.get())) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override public boolean stillValid(Player p) { return container.stillValid(p); }
}
