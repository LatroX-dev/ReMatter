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
    private final Player viewer;
    private final @Nullable MatterLevelEmitterBlockEntity emitter;
    private final BlockPos position;
    private @Nullable SubstanceKey target;
    private long threshold;
    private boolean emitWhenBelow;
    private long observedStock;
    private boolean networkReady;
    private boolean emitting;
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
    }

    public BlockPos position() { return position; }
    public @Nullable SubstanceKey target() { return target; }
    public long threshold() { return threshold; }
    public boolean emitWhenBelow() { return emitWhenBelow; }
    public long observedStock() { return observedStock; }
    public boolean networkReady() { return networkReady; }
    public boolean emitting() { return emitting; }

    public void acceptState(MatterLevelEmitterPayload.State state) {
        this.target = state.target();
        this.threshold = state.threshold();
        this.emitWhenBelow = state.emitWhenBelow();
        this.observedStock = state.observedStock();
        this.networkReady = state.networkReady();
        this.emitting = state.emitting();
    }

    public void configureFromClient(ServerPlayer player, MatterLevelEmitterPayload.Configure payload) {
        if (emitter == null || !stillValid(player) || !emitter.canAccess(player)) return;
        emitter.configure(payload.target(), payload.threshold(), payload.emitWhenBelow());
        broadcastChanges();
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (emitter == null || !(viewer instanceof ServerPlayer serverPlayer)) return;
        var curTarget = emitter.target();
        long curThreshold = emitter.threshold();
        boolean curBelow = emitter.emitWhenBelow();
        long curStock = emitter.observedStock();
        boolean curReady = emitter.networkReady();
        boolean curEmitting = emitter.emitting();
        if (!initialSent || !Objects.equals(target, curTarget) || threshold != curThreshold
                || emitWhenBelow != curBelow || observedStock != curStock
                || networkReady != curReady || emitting != curEmitting) {
            initialSent = true;
            target = curTarget;
            threshold = curThreshold;
            emitWhenBelow = curBelow;
            observedStock = curStock;
            networkReady = curReady;
            emitting = curEmitting;
            PacketDistributor.sendToPlayer(serverPlayer, new MatterLevelEmitterPayload.State(
                    containerId, curTarget, curThreshold, curBelow, curStock, curReady, curEmitting));
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
