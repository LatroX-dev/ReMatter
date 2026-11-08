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
    private boolean visuallyFormed;
    private int visualProgress, publishedProgress;
    private int targetX = 1, targetY = 1, targetZ = 1;

    public boolean visuallyFormed() { return visuallyFormed; }
    public float visualProgress() { return visualProgress / 64.0F; }
    /** Target in unmirrored, facing-local block coordinates. */
    public float beamTargetX() { return targetX / 2.0F; }
    public float beamTargetY() { return targetY / 2.0F; }
    public float beamTargetZ() { return targetZ / 2.0F; }

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
    public void setWorking(MachineBlockEntity owner, boolean active, IndustrialStructure.Shape shape, IndustrialStructure.Module module) {
        if (!live() || !liveController(owner) || !module.position().equals(worldPosition) || module.kind() != kind()) return;
        if (controller.get() != null && controller.get() != owner && liveController(controller.get())) return;
        controller = new WeakReference<>(owner);
        boolean suspended = IndustrialModuleMount.atInteriorHeight(module.y(), shape.height() - 3)
                .map(IndustrialModuleMount::suspended).orElse(false);
        var facing = owner.getBlockState().getValue(MachineBlock.FACING);
        var right = facing.getClockWise(); var back = facing.getOpposite();
        int localX = shape.width() - 1 - 2 * shape.controllerColumn() - 2 * module.x();
        int localZ = shape.depth() - 1 - 2 * module.depth();
        int worldX = localX * right.getStepX() + localZ * back.getStepX();
        int worldZ = localX * right.getStepZ() + localZ * back.getStepZ();
        var moduleFacing = getBlockState().getValue(IndustrialModuleBlock.FACING);
        var moduleRight = moduleFacing.getClockWise(); var moduleBack = moduleFacing.getOpposite();
        int x = 1 + worldX * moduleRight.getStepX() + worldZ * moduleRight.getStepZ();
        int y = shape.height() - 2 - 2 * module.y();
        int z = 1 + worldX * moduleBack.getStepX() + worldZ * moduleBack.getStepZ();
        updateVisual(active, active ? owner.preparationStage() : -1, active && owner.preparationMixing(), true,
                suspended, owner.visualDuration() == 0 ? 0 : (int)((long)owner.visualProgress() * 64 / owner.visualDuration()), x, y, z);
    }
    public void clearBinding(MachineBlockEntity owner) {
        if (!live() || controller.get() != null && controller.get() != owner) return;
        controller.clear();
        updateVisual(false, -1, false, false, getBlockState().getValue(IndustrialModuleBlock.SUSPENDED), 0, 1, 1, 1);
    }
    public void serverTick() {
        if (!live()) return;
        var owner = controller.get();
        if (!liveController(owner)) {
            controller.clear();
            updateVisual(false, -1, false, false, getBlockState().getValue(IndustrialModuleBlock.SUSPENDED), 0, 1, 1, 1);
        } else if (getBlockState().getValue(IndustrialModuleBlock.ACTIVE) && !owner.isModuleWorking(worldPosition)) {
            // A stop never invents progress, and retains the current suspended reaction display.
            updateVisual(false, -1, false, visuallyFormed, getBlockState().getValue(IndustrialModuleBlock.SUSPENDED),
                    visualProgress, targetX, targetY, targetZ);
        }
    }
    private void updateVisual(boolean active, int stage, boolean mixing, boolean formed, boolean suspended,
                              int progress, int x, int y, int z) {
        stage = Math.clamp(stage, -1, 2); progress = Math.clamp(progress, 0, 64);
        var state = getBlockState();
        boolean changed = preparationStage != stage || preparationMixing != mixing || visuallyFormed != formed
                || targetX != x || targetY != y || targetZ != z;
        preparationStage = stage; preparationMixing = mixing; visuallyFormed = formed;
        visualProgress = progress; targetX = x; targetY = y; targetZ = z;
        var next = state.setValue(IndustrialModuleBlock.ACTIVE, active).setValue(IndustrialModuleBlock.SUSPENDED, suspended);
        if (next != state) {
            publishedProgress = progress;
            level.setBlock(worldPosition, next, Block.UPDATE_CLIENTS);
        } else if (changed || publishedProgress != progress && (level.getGameTime() % 4 == 0 || !active || progress == 64)) {
            publishedProgress = progress;
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("stage", preparationStage);
        tag.putBoolean("mixing", preparationMixing);
        tag.putBoolean("formed", visuallyFormed); tag.putInt("progress", visualProgress);
        tag.putInt("target_x", targetX); tag.putInt("target_y", targetY); tag.putInt("target_z", targetZ);
        return tag;
    }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void handleUpdateTag(ValueInput input) {
        preparationStage = Math.clamp(input.getIntOr("stage", -1), -1, 2);
        preparationMixing = input.getBooleanOr("mixing", false);
        visuallyFormed = input.getBooleanOr("formed", false);
        visualProgress = Math.clamp(input.getIntOr("progress", 0), 0, 64);
        targetX = Math.clamp(input.getIntOr("target_x", 1), -18, 18);
        targetY = Math.clamp(input.getIntOr("target_y", 1), -18, 18);
        targetZ = Math.clamp(input.getIntOr("target_z", 1), -18, 18);
    }
    @Override public void onDataPacket(Connection connection, ValueInput input) { handleUpdateTag(input); }
}
