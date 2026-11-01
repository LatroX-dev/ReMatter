package dev.latrox.rematter.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Stateless exterior connector. The associated controller retains every resource and permission. */
public final class MachinePortBlock extends Block implements EntityBlock {
    public static final MapCodec<MachinePortBlock> CODEC = simpleCodec(MachinePortBlock::new);
    public MachinePortBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MachinePortBlockEntity(pos, state); }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) { MatterPipeBlock.refreshLoadedAround(level, pos); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModContent.MACHINE_PORT_ENTITY.get()) return null;
        return (world, pos, blockState, entity) -> ((MachinePortBlockEntity) entity).serverTick();
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MachinePortBlockEntity port) {
            var controller = port.controller(hit.getDirection());
            if (controller != null && controller.canUse(player)) player.openMenu(controller);
        }
        return InteractionResult.SUCCESS;
    }
}
