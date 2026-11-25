package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.network.CellTransferPayload;
import dev.latrox.rematter.network.MatterLevelEmitterPayload;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/** Configuration menu for Matter Level Emitter with live S2C stock/signal sync. */
public final class MatterLevelEmitterMenu extends AbstractContainerMenu {
    public static final int CYCLE_SECURITY = 0;
    public static final int CYCLE_SECURITY_BACK = 1;
    private final Player viewer;
    private final @Nullable MatterLevelEmitterBlockEntity emitter;
    private final BlockPos position;
    private final net.minecraft.world.inventory.DataSlot securityMode = net.minecraft.world.inventory.DataSlot.standalone();
    private final net.minecraft.world.inventory.DataSlot canManageSecurity = net.minecraft.world.inventory.DataSlot.standalone();
    private @Nullable SubstanceKey target;
    private long threshold;
    private boolean emitWhenBelow;
    private long observedStock;
    private boolean networkReady;
    private boolean emitting;
    private String customName = "";
    private boolean initialSent;

    public MatterLevelEmitterMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        super(ModContent.MATTER_LEVEL_EMITTER_MENU.get(), id);
        this.viewer = playerInventory.player;
        this.emitter = null;
        this.position = buf.readBlockPos();
        this.target = buf.readBoolean() ? CellTransferPayload.readKey(buf) : null;
        this.threshold = Math.clamp(buf.readVarLong(), 0L, MatterLevelEmitterBlockEntity.MAX_THRESHOLD);
        this.emitWhenBelow = buf.readBoolean();
        this.observedStock = Math.max(0L, buf.readVarLong());
        this.networkReady = buf.readBoolean();
        this.emitting = buf.readBoolean();
        if (buf.readableBytes() > 0) this.customName = CustomNames.sanitize(buf.readUtf(CustomNames.MAX_LENGTH));
        addDataSlot(securityMode);
        addDataSlot(canManageSecurity);
    }

    public MatterLevelEmitterMenu(int id, Inventory playerInventory, MatterLevelEmitterBlockEntity emitter) {
        super(ModContent.MATTER_LEVEL_EMITTER_MENU.get(), id);
        this.viewer = playerInventory.player;
        this.emitter = emitter;
        this.position = emitter.getBlockPos();
        this.target = emitter.target();
        this.threshold = emitter.threshold();
        this.emitWhenBelow = emitter.emitWhenBelow();
        this.observedStock = emitter.observedStock();
        this.networkReady = emitter.networkReady();
        this.emitting = emitter.emitting();
        this.customName = emitter.customName();
        securityMode.set(emitter.securityMode().ordinal());
        addDataSlot(securityMode);
        canManageSecurity.set(emitter.canManageSecurity(viewer) ? 1 : 0);
        addDataSlot(canManageSecurity);
    }

    public BlockPos position() { return position; }
    public String customName() { return emitter != null ? emitter.customName() : customName; }
    public void setClientCustomName(String value) { customName = CustomNames.sanitize(value); }
    public void renameFromClient(ServerPlayer player, String nextName) {
        if (emitter == null || !stillValid(player) || !emitter.canAccess(player)) return;
        emitter.setCustomName(nextName);
        broadcastChanges();
    }
    public @Nullable SubstanceKey target() { return target; }
    public long threshold() { return threshold; }
    public boolean emitWhenBelow() { return emitWhenBelow; }
    public long observedStock() { return observedStock; }
    public boolean networkReady() { return networkReady; }
    public boolean emitting() { return emitting; }
    public SecurityMode securityMode() { return emitter != null ? emitter.securityMode() : SecurityMode.byIndex(securityMode.get()); }
    public boolean canManageSecurity() { return emitter != null ? emitter.canManageSecurity(viewer) : canManageSecurity.get() != 0; }

    @Override
    public boolean clickMenuButton(Player player, int action) {
        if (emitter == null || player.containerMenu != this || player.isSpectator() || !stillValid(player)) return false;
        if (action != CYCLE_SECURITY && action != CYCLE_SECURITY_BACK) return false;
        boolean changed = emitter.cycleSecurityMode(player, action == CYCLE_SECURITY);
        if (changed) broadcastChanges();
        return changed;
    }

    public void acceptState(MatterLevelEmitterPayload.State state) {
        this.target = state.target();
        this.threshold = state.threshold();
        this.emitWhenBelow = state.emitWhenBelow();
        this.observedStock = state.observedStock();
        this.networkReady = state.networkReady();
        this.emitting = state.emitting();
        this.customName = state.customName();
    }

    public void configureFromClient(ServerPlayer player, MatterLevelEmitterPayload.Configure payload) {
        if (emitter == null || !stillValid(player) || !emitter.canAccess(player)) return;
        emitter.configure(payload.target(), payload.threshold(), payload.emitWhenBelow());
        broadcastChanges();
    }

    @Override
    public void broadcastChanges() {
        if (emitter != null) {
            securityMode.set(emitter.securityMode().ordinal());
            canManageSecurity.set(emitter.canManageSecurity(viewer) ? 1 : 0);
        }
        super.broadcastChanges();
        if (emitter == null || !(viewer instanceof ServerPlayer serverPlayer)) return;
        var curTarget = emitter.target();
        long curThreshold = emitter.threshold();
        boolean curBelow = emitter.emitWhenBelow();
        long curStock = emitter.observedStock();
        boolean curReady = emitter.networkReady();
        boolean curEmitting = emitter.emitting();
        String curName = emitter.customName();
        if (!initialSent || !Objects.equals(target, curTarget) || threshold != curThreshold
                || emitWhenBelow != curBelow || observedStock != curStock
                || networkReady != curReady || emitting != curEmitting || !customName.equals(curName)) {
            initialSent = true;
            target = curTarget;
            threshold = curThreshold;
            emitWhenBelow = curBelow;
            observedStock = curStock;
            networkReady = curReady;
            emitting = curEmitting;
            customName = curName;
            PacketDistributor.sendToPlayer(serverPlayer, new MatterLevelEmitterPayload.State(
                    containerId, curTarget, curThreshold, curBelow, curStock, curReady, curEmitting, curName));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return emitter == null || emitter.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
