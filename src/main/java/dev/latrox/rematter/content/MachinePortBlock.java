package dev.latrox.rematter.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
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

/** Exterior connector with display-only shell state; the controller retains every resource and permission. */
public final class MachinePortBlock extends Block implements EntityBlock {
    public static final MapCodec<MachinePortBlock> CODEC = simpleCodec(MachinePortBlock::new);
    /** Display-only projection; capabilities always revalidate the live controller and shell. */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final EnumProperty<MachinePortMode> MODE = EnumProperty.create("mode", MachinePortMode.class);
    public MachinePortBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED,false).setValue(FACING,Direction.NORTH).setValue(MODE, MachinePortMode.AUTOMATIC));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FORMED,FACING,MODE); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction front = context.getPlayer() == null ? context.getClickedFace() : context.getNearestLookingDirection().getOpposite();
        return defaultBlockState().setValue(FACING, front);
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation) { return state.setValue(FACING,rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state,Mirror mirror) { return state.setValue(FACING,mirror.mirror(state.getValue(FACING))); }
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
            if (controller != null && controller.interact(player)) {
                if (player.isShiftKeyDown()) {
                    if (port.cycleMode(player, hit.getDirection())) player.sendSystemMessage(
                            net.minecraft.network.chat.Component.translatable("port.rematter.mode_message", port.mode().label()));
                } else player.openMenu(new SimpleMenuProvider((id, inventory, user) -> controller.canAccess(user)
                        ? new MachineMenu(id, inventory, controller, port) : null, controller.getDisplayName()));
            }
        }
        return InteractionResult.SUCCESS;
    }
}
