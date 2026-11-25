package dev.latrox.rematter.primal;

import dev.latrox.rematter.content.ChunkRevisions;
import dev.latrox.rematter.content.CustomNames;
import dev.latrox.rematter.content.MachinePortMode;
import dev.latrox.rematter.content.MatterCableBlock;
import dev.latrox.rematter.content.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public final class EntangledLinkBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Shapes.block();

    public EntangledLinkBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /** Front and top hold the animated Primal vortex lenses; the other four faces are flush connection ports. */
    public static boolean connectsSide(BlockState state, Direction side) {
        return side != state.getValue(FACING) && side != Direction.UP;
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new EntangledLinkBlockEntity(pos, state); }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        if (!level.isClientSide() && state.getBlock() != oldState.getBlock()) {
            level.scheduleTick(pos, this, 1);
            if (level instanceof ServerLevel server) ChunkRevisions.touchLoaded(server, pos);
        }
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        MatterCableBlock.refreshLoadedAround(level, pos);
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                             @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof EntangledLinkBlockEntity link) {
            link.onNeighborRedstoneChanged();
        }
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof EntangledLinkBlockEntity link) link.placedBy(placer, stack);
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
        ChunkRevisions.touchLoaded(level, pos);
        MatterCableBlock.refreshLoadedAround(level, pos);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof EntangledLinkBlockEntity link) {
            if (!level.isClientSide() && link.interact(player)) {
                if (player.isShiftKeyDown()) {
                    var side = MachinePortMode.Side.local(state.getValue(FACING), hit.getDirection());
                    if (!EntangledLinkBlockEntity.isConfigurableSide(side)) {
                        player.sendOverlayMessage(Component.translatable(
                                side == MachinePortMode.Side.FRONT ? "port.rematter.closed.front" : "port.rematter.closed.emitter_top"));
                    } else if (link.cyclePort(player, side, true)) {
                        player.sendSystemMessage(Component.translatable("port.rematter.side_mode", side.label(), link.portMode(side).label()));
                        ModSounds.play(level, pos, ModSounds.PORT_CYCLE);
                    }
                } else if (player instanceof ServerPlayer server) {
                    server.openMenu(link, link::writeMenuInit);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
    @Override protected java.util.List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        var main = new ItemStack(this);
        if (builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY) instanceof EntangledLinkBlockEntity link) {
            CustomNames.applyToStack(main, link.customName());
        }
        return java.util.List.of(main);
    }
}
