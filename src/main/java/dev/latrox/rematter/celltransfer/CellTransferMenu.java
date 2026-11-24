package dev.latrox.rematter.celltransfer;

import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.content.ModContent;
import dev.latrox.rematter.content.SecurityMode;
import dev.latrox.rematter.network.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/** Two physical Cell slots (0 = Source, 1 = Target), a revision-checked selection and persistent per-Cell policies. */
public final class CellTransferMenu extends AbstractContainerMenu {
    public static final int WIDTH = 256, HEIGHT = 224, FIRST_PLAYER_SLOT = 2, PLAYER_X = 48, PLAYER_Y = 143, HOTBAR_Y = 201;
    public static final int CYCLE_SECURITY = 1, CYCLE_SECURITY_BACK = 2;
    public static final int[] CELL_X = {16, 170};
    public static final int CELL_Y = 39;
    private final Container inventory;
    private final @Nullable CellTransferStationBlockEntity station;
    private final Player player;
    private final BlockPos position;
    private final DataSlot securityMode = DataSlot.standalone();
    private final DataSlot canManageSecurity = DataSlot.standalone();
    private @Nullable CellTransferPayload received, sent;
    private long nextUpdate, acknowledgement;
    private boolean partitionView;
    public long acknowledgement() { return acknowledgement; }
    public boolean partitionView() { return partitionView; }
    public void setPartitionView(boolean partitionView) { this.partitionView = partitionView; }
    public CellTransferMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(id, inventory, new SimpleContainer(FIRST_PLAYER_SLOT), null, data.readBlockPos());
    }
    public CellTransferMenu(int id, Inventory inventory, CellTransferStationBlockEntity station) {
        this(id, inventory, station, station, station.getBlockPos());
    }
    private CellTransferMenu(int id, Inventory playerInventory, Container inventory, @Nullable CellTransferStationBlockEntity station, BlockPos position) {
        super(ModContent.CELL_TRANSFER_MENU.get(), id);
        this.inventory = inventory;
        this.station = station;
        this.player = playerInventory.player;
        this.position = position;
        for (int i = 0; i < FIRST_PLAYER_SLOT; i++) {
            addSlot(new Slot(inventory, i, CELL_X[i], CELL_Y) {
                @Override public boolean isActive() { return !partitionView; }
                @Override public boolean mayPlace(ItemStack stack) {
                    return !stationLocked() && !player.isSpectator() && (station == null || station.canAccess(player))
                            && !stack.isEmpty() && CellTransferData.isCell(stack);
                }
                @Override public boolean mayPickup(Player actor) {
                    return !actor.isSpectator() && !stationLocked() && (station == null || station.canAccess(actor));
                }
                @Override public int getMaxStackSize() { return 1; }
            });
        }
        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                addSlot(new Slot(playerInventory, column + row * 9 + 9, PLAYER_X + column * 18, PLAYER_Y + row * 18));
        for (int column = 0; column < 9; column++)
            addSlot(new Slot(playerInventory, column, PLAYER_X + column * 18, HOTBAR_Y));
        securityMode.set(station != null ? station.securityMode().ordinal() : SecurityMode.PUBLIC.ordinal());
        addDataSlot(securityMode);
        canManageSecurity.set(station != null && station.canManageSecurity(player) ? 1 : 0);
        addDataSlot(canManageSecurity);
    }
    public SecurityMode securityMode() { return station != null ? station.securityMode() : SecurityMode.byIndex(securityMode.get()); }
    public boolean canManageSecurity() { return station != null ? station.canManageSecurity(player) : canManageSecurity.get() != 0; }
    private boolean stationLocked() {
        return station != null ? station.locked()
                : received != null && received.locked() && received.slots().stream().allMatch(v -> v.kind() == 0);
    }
    private CellTransferPayload view() {
        if (station != null) return station.snapshot(containerId);
        if (received != null) return received;
        return new CellTransferPayload(containerId, 0, "insert_cells", false, false, false,
                CellTransferSelection.ALL, Collections.nCopies(FIRST_PLAYER_SLOT, CellTransferPayload.SlotView.EMPTY), List.of(), 0, 0, 0, 0);
    }
    public long revision() { return view().revision(); }
    public String status() { return view().status(); }
    public boolean canTransfer() { return view().canTransfer(); }
    public boolean locked() { return station != null ? station.locked() : received != null && received.locked(); }
    public boolean partial() { return view().partial(); }
    public boolean selectedAll() { return view().selection().all(); }
    public boolean selectedPrimal() { return view().selection().primalSelected(); }
    public Set<SubstanceKey> selection() { return view().selection().keys(); }
    public List<CellTransferPayload.Entry> available() { return view().available(); }
    public List<CellTransferPayload.SlotView> slotViews() { return view().slots(); }
    public long movableOrdinary() { return view().movableOrdinary(); }
    public long movablePrimal() { return view().movablePrimal(); }
    public long movedOrdinaryLast() { return view().movedOrdinaryLast(); }
    public long movedPrimalLast() { return view().movedPrimalLast(); }
    public long movedLast() { return Math.addExact(movedOrdinaryLast(), movedPrimalLast()); }
    public long sourceAmount() { return total(0, 1, 1, false); }
    public long targetAmount() { return total(1, 2, 1, false); }
    public long sourceCapacity() { return total(0, 1, 1, true); }
    public long targetCapacity() { return total(1, 2, 1, true); }
    public long primalSourceAmount() { return total(0, 1, 2, false); }
    public long primalTargetAmount() { return total(1, 2, 2, false); }
    public boolean primal() { return slotViews().stream().anyMatch(s -> s.kind() == 2); }
    private long total(int first, int end, int kind, boolean capacity) {
        long total = 0;
        var slots = slotViews();
        for (int i = first; i < end; i++)
            if (slots.get(i).kind() == kind) total = Math.addExact(total, capacity ? slots.get(i).capacity() : slots.get(i).amount());
        return total;
    }
    public void accept(CellTransferPayload payload) {
        if (payload.containerId() == containerId) { received = payload; acknowledgement++; }
    }
    public boolean configure(Player actor, CellTransferConfigPayload action) {
        boolean changed = station != null && actor.containerMenu == this && action.containerId() == containerId && stillValid(actor) && station.configure(actor, action);
        sent = null; nextUpdate = 0; broadcastChanges(); return changed;
    }
    @Override public boolean clickMenuButton(Player actor, int action) {
        if ((action == CYCLE_SECURITY || action == CYCLE_SECURITY_BACK) && station != null && actor.containerMenu == this && stillValid(actor)) {
            boolean changed = station.cycleSecurityMode(actor, action == CYCLE_SECURITY);
            if (changed) { sent = null; nextUpdate = 0; broadcastChanges(); }
            return changed;
        }
        return action == 0 && sent != null && configure(actor, new CellTransferConfigPayload(containerId, sent.revision(), CellTransferConfigPayload.Action.TRANSFER, -1, null, false));
    }
    @Override public boolean stillValid(Player actor) { return station == null || station.stillValid(actor); }
    @Override public ItemStack quickMoveStack(Player actor, int index) {
        if (index < 0 || index >= slots.size() || actor.isSpectator() || stationLocked() || station != null && (!station.canAccess(actor) || !stillValid(actor))) return ItemStack.EMPTY;
        var slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(actor)) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var before = stack.copy();
        if (index < FIRST_PLAYER_SLOT) {
            if (!moveItemStackTo(stack, FIRST_PLAYER_SLOT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!CellTransferData.isCell(stack) || !moveItemStackTo(stack, 0, FIRST_PLAYER_SLOT, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(actor, stack);
        return before;
    }
    @Override public void broadcastChanges() {
        if (station != null) {
            securityMode.set(station.securityMode().ordinal());
            canManageSecurity.set(station.canManageSecurity(player) ? 1 : 0);
        }
        super.broadcastChanges();
        if (station == null || !(player instanceof ServerPlayer server) || !stillValid(player)) return;
        long time = server.level().getGameTime();
        if (time < nextUpdate) return;
        nextUpdate = time + 4;
        var next = station.snapshot(containerId);
        if (!next.equals(sent)) { PacketDistributor.sendToPlayer(server, next); sent = next; }
    }
}
