package dev.latrox.rematter.celltransfer;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** A manual benchtop two-Cell dock; installed Cells and their formatting travel intact with the block. */
public final class CellTransferStationBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final MapCodec<CellTransferStationBlock> CODEC = simpleCodec(CellTransferStationBlock::new);
    private static final VoxelShape BASE_DESK = Block.box(0, 0, 0, 16, 9, 16);
    private static final VoxelShape SHAPE_NORTH = Shapes.or(BASE_DESK, Block.box(0, 9, 9, 16, 13, 16), Block.box(6.5, 9, 3, 9.5, 10, 9));
    private static final VoxelShape SHAPE_SOUTH = Shapes.or(BASE_DESK, Block.box(0, 9, 0, 16, 13, 7), Block.box(6.5, 9, 7, 9.5, 10, 13));
    private static final VoxelShape SHAPE_WEST = Shapes.or(BASE_DESK, Block.box(9, 9, 0, 16, 13, 16), Block.box(3, 9, 6.5, 9, 10, 9.5));
    private static final VoxelShape SHAPE_EAST = Shapes.or(BASE_DESK, Block.box(0, 9, 0, 7, 13, 16), Block.box(7, 9, 6.5, 13, 10, 9.5));

    public CellTransferStationBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
            default -> SHAPE_NORTH;
        };
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CellTransferStationBlockEntity(pos, state); }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CellTransferStationBlockEntity station) station.placedBy(placer);
    }
    private static int hitBay(BlockPos pos, Direction facing, BlockHitResult hit) {
        var rel = hit.getLocation().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(pos));
        Direction left = facing.getClockWise();
        double modelX = (0.5 + (rel.x - 0.5) * left.getStepX() + (rel.z - 0.5) * left.getStepZ()) * 16.0;
        return modelX >= 8.0 ? 0 : 1;
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CellTransferStationBlockEntity station)) return InteractionResult.PASS;
        Direction facing = state.getValue(FACING);
        boolean dockFace = hit.getDirection() == facing || hit.getDirection() == Direction.UP;
        if (!dockFace || player.isShiftKeyDown() || stack.isEmpty() || !CellTransferData.isCell(stack) || station.locked()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        int preferred = hitBay(pos, facing, hit);
        int target = station.getItem(preferred).isEmpty() ? preferred : station.getItem(1 - preferred).isEmpty() ? 1 - preferred : -1;
        if (target < 0) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!station.interact(player) || !station.stillValid(player) || player.isSpectator()) return InteractionResult.FAIL;
        ItemStack single = stack.copyWithCount(1);
        if (!station.canPlaceItem(target, single)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        station.setItem(target, single);
        if (!ItemStack.isSameItemSameComponents(station.getItem(target), single)) return InteractionResult.CONSUME;
        if (!player.isCreative()) stack.shrink(1);
        dev.latrox.rematter.content.ModSounds.play(level, pos, dev.latrox.rematter.content.ModSounds.CELL_INSERT);
        return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof CellTransferStationBlockEntity station) {
            Direction facing = state.getValue(FACING);
            boolean dockFace = hit.getDirection() == facing || hit.getDirection() == Direction.UP;
            if (player.isShiftKeyDown() && dockFace && !station.locked()) {
                int preferred = hitBay(pos, facing, hit);
                int target = !station.getItem(preferred).isEmpty() ? preferred : !station.getItem(1 - preferred).isEmpty() ? 1 - preferred : -1;
                if (target >= 0) {
                    if (level.isClientSide()) return InteractionResult.SUCCESS;
                    if (!station.interact(player) || !station.stillValid(player) || player.isSpectator()) return InteractionResult.FAIL;
                    ItemStack removed = station.removeItem(target, 1);
                    if (!removed.isEmpty()) {
                        if (player.getMainHandItem().isEmpty()) player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, removed);
                        else player.getInventory().placeItemBackInInventory(removed);
                        dev.latrox.rematter.content.ModSounds.play(level, pos, dev.latrox.rematter.content.ModSounds.CELL_EXTRACT);
                        return InteractionResult.SUCCESS;
                    }
                }
            }
            if (player instanceof ServerPlayer server && station.interact(player)) server.openMenu(station, b -> b.writeBlockPos(pos));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof CellTransferStationBlockEntity station
                ? dev.latrox.rematter.content.MachineComparators.cellTransferStation(station) : 0;
    }
    public ItemStack storedDrop(CellTransferStationBlockEntity station, HolderLookup.Provider registries) {
        var drop = new ItemStack(this);
        drop.applyComponents(station.collectComponents());
        var raw = station.saveCustomOnly(registries);
        raw.remove("owner");
        raw.remove("security");
        drop.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(station.getType(), raw));
        return drop;
    }
    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof CellTransferStationBlockEntity station
                ? List.of(storedDrop(station, builder.getLevel().registryAccess())) : List.of(new ItemStack(this));
    }
    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData, Player player) {
        return includeData ? ItemStack.EMPTY : new ItemStack(this);
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative() && level.getBlockEntity(pos) instanceof CellTransferStationBlockEntity station && station.hasStoredContents())
            popResource(level, pos, storedDrop(station, level.registryAccess()));
        return super.playerWillDestroy(level, pos, state, player);
    }
}
