package dev.latrox.rematter.content;

import java.lang.ref.WeakReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;

/** A transient animation binding, never a second resource store or a persisted authority. */
public final class IndustrialModuleBlockEntity extends BlockEntity {
    private WeakReference<MachineBlockEntity> controller = new WeakReference<>(null);
    private int preparationStage = -1;
    private boolean preparationMixing;

    public IndustrialModuleBlockEntity(BlockPos pos, BlockState state) { super(ModContent.INDUSTRIAL_MODULE_ENTITY.get(), pos, state); }
    public IndustrialModuleKind kind() { return ((IndustrialModuleBlock) getBlockState().getBlock()).kind(); }
    public int preparationStage() { return preparationStage; }
    public boolean preparationMixing() { return preparationMixing; }
    private boolean live() {
        return !isRemoved() && level instanceof ServerLevel server && server.getServer().isSameThread()
                && level.hasChunkAt(worldPosition) && level.getBlockEntity(worldPosition) == this;
    }
    private boolean liveController(MachineBlockEntity candidate) {
        return candidate != null && !candidate.isRemoved() && candidate.getLevel() == level
                && level.hasChunkAt(candidate.getBlockPos()) && level.getBlockEntity(candidate.getBlockPos()) == candidate;
    }
    /** Called only after the controller validates its complete formation. No per-module rescan here. */
    public void setWorking(MachineBlockEntity owner, boolean active) {
        if (!live() || !liveController(owner)) return;
        // A late stop from a previous formation must not stop a newly bound controller.
        if (!active && controller.get() != null && controller.get() != owner) return;
        if (controller.get() != owner) controller = new WeakReference<>(owner);
        updateVisual(active, active ? owner.preparationStage() : -1, active && owner.preparationMixing());
    }
    public void serverTick() {
        if (!live()) return;
        var owner = controller.get();
        boolean active = getBlockState().getValue(IndustrialModuleBlock.ACTIVE);
        if (!liveController(owner)) {
            controller.clear();
            updateVisual(false, -1, false);
        } else if (active) {
            // Work can stop between controller ticks after a break, unload or invalid layout.
            boolean working = owner.isModuleWorking(worldPosition);
            updateVisual(working, working ? owner.preparationStage() : -1, working && owner.preparationMixing());
        }
    }
    private void updateVisual(boolean active, int stage, boolean mixing) {
        stage = Math.clamp(stage, -1, 2);
        var state = getBlockState();
        boolean changed = preparationStage != stage || preparationMixing != mixing;
        preparationStage = stage;
        preparationMixing = mixing;
        if (state.getValue(IndustrialModuleBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(IndustrialModuleBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        } else if (changed) level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("stage", preparationStage);
        tag.putBoolean("mixing", preparationMixing);
        return tag;
    }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void handleUpdateTag(ValueInput input) {
        preparationStage = Math.clamp(input.getIntOr("stage", -1), -1, 2);
        preparationMixing = input.getBooleanOr("mixing", false);
    }
    @Override public void onDataPacket(Connection connection, ValueInput input) { handleUpdateTag(input); }
}
