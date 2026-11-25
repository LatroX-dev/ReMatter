package dev.latrox.rematter.content;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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

/** Network transceiver bridging a Matter Cable network to linked handheld Wireless Matter Terminals. */
public final class WirelessAccessPointBlock extends Block implements EntityBlock {
    public static final MapCodec<WirelessAccessPointBlock> CODEC = simpleCodec(WirelessAccessPointBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(
            Shapes.or(box(2, 0, 2, 14, 2.5, 14), box(3.5, 2.5, 3.5, 12.5, 15.5, 12.5)));

    public WirelessAccessPointBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    /** Wireless Access Point connects cleanly from below only (freestanding transceiver tower). */
    public static boolean connectsSide(BlockState state, Direction side) {
        return side == Direction.DOWN;
    }

    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, ACTIVE); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPES.get(state.getValue(FACING)); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        if (!level.isClientSide() && state.getBlock() != oldState.getBlock()) level.scheduleTick(pos, this, 1);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        MatterCableBlock.refreshLoadedAround(level, pos);
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new WirelessAccessPointBlockEntity(pos, state); }

    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModContent.WIRELESS_ACCESS_POINT_ENTITY.get()) return null;
        return (world, pos, blockState, entity) -> ((WirelessAccessPointBlockEntity) entity).serverTick();
    }

    @Override protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        ChunkAnchorManager.release(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof WirelessAccessPointBlockEntity ap) ap.placedBy(placer, stack);
    }

    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                                    net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()
                && (stack.is(ModContent.DIMENSIONAL_CARD.get()) || stack.is(ModContent.ANCHOR_UPGRADE.get()))
                && level.getBlockEntity(pos) instanceof WirelessAccessPointBlockEntity ap) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            if (!ap.interact(player)) return InteractionResult.FAIL;
            int slot = stack.is(ModContent.DIMENSIONAL_CARD.get())
                    ? WirelessAccessPointBlockEntity.SLOT_RANGE
                    : WirelessAccessPointBlockEntity.SLOT_ANCHOR;
            if (!ap.getItem(slot).isEmpty()) return InteractionResult.FAIL;
            ap.setItem(slot, stack.copyWithCount(1));
            if (!player.getAbilities().instabuild) stack.shrink(1);
            ModSounds.play(server, pos, ModSounds.CONFIG_CARD_WRITE);
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.sendOverlayMessage(Component.translatable("message.rematter.upgrade_installed", stack.getHoverName()));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof WirelessAccessPointBlockEntity ap) {
            if (player instanceof net.minecraft.server.level.ServerPlayer server && ap.interact(player)) {
                ap.syncChunkAnchor();
                server.openMenu(ap, ap::writeMenuInit);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override protected java.util.List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        var drops = new java.util.ArrayList<ItemStack>();
        var main = new ItemStack(this);
        if (builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY) instanceof WirelessAccessPointBlockEntity ap) {
            CustomNames.applyToStack(main, ap.customName());
            drops.add(main);
            drops.addAll(ap.dropContentsList());
        } else {
            drops.add(main);
        }
        return drops;
    }

    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative() && level.getBlockEntity(pos) instanceof WirelessAccessPointBlockEntity ap) {
            for (ItemStack extra : ap.dropContentsList()) popResource(level, pos, extra);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
