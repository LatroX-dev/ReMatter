package dev.latrox.rematter.content;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Passive Matter Network stock sensor emitting directional strong and weak redstone power (15)
 * out of its front face when the configured substance meets the >= or < threshold.
 */
public final class MatterLevelEmitterBlock extends Block implements EntityBlock {
    public static final MapCodec<MatterLevelEmitterBlock> CODEC = simpleCodec(MatterLevelEmitterBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(
            Shapes.or(box(2, 0, 2, 14, 3, 14), box(4, 3, 4, 12, 11, 16), box(5.5, 5.5, 0.5, 10.5, 10.5, 4)));

    public MatterLevelEmitterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    public static boolean connectsSide(BlockState state, Direction side) {
        return side == Direction.DOWN || side == state.getValue(FACING).getOpposite();
    }

    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, ACTIVE); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPES.get(state.getValue(FACING)); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }

    @Override protected boolean isSignalSource(BlockState state) { return true; }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(ACTIVE) && state.getValue(FACING).getOpposite() == direction ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    public void notifySignalTarget(Level level, BlockPos pos, Direction facing) {
        level.updateNeighborsAt(pos, this);
        level.updateNeighborsAt(pos.relative(facing), this);
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        if (!level.isClientSide() && state.getBlock() != oldState.getBlock()) {
            level.scheduleTick(pos, this, 1);
            notifySignalTarget(level, pos, state.getValue(FACING));
        }
    }

    @Override protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
        notifySignalTarget(level, pos, state.getValue(FACING));
        MatterCableBlock.refreshLoadedAround(level, pos);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        MatterCableBlock.refreshLoadedAround(level, pos);
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MatterLevelEmitterBlockEntity(pos, state);
    }

    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModContent.MATTER_LEVEL_EMITTER_ENTITY.get()) return null;
        return (world, pos, blockState, entity) -> ((MatterLevelEmitterBlockEntity) entity).serverTick();
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MatterLevelEmitterBlockEntity emitter) {
            emitter.placedBy(placer);
        }
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof MatterLevelEmitterBlockEntity emitter) {
            if (player instanceof ServerPlayer server && emitter.interact(player)) {
                emitter.refreshOutputNow();
                server.openMenu(emitter, emitter::writeMenuInit);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
