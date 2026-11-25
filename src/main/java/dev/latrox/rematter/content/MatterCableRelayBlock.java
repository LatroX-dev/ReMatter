package dev.latrox.rematter.content;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Inline Matter Cable Valve: sits along one cable axis (facing <-> opposite) and gates Matter and/or FE flow
 * with redstone control, channel separation (Both / Power Only / Matter Only), and throughput limits.
 */
public final class MatterCableRelayBlock extends Block implements EntityBlock {
    public static final MapCodec<MatterCableRelayBlock> CODEC = simpleCodec(MatterCableRelayBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final BooleanProperty DIRECTIONAL = BooleanProperty.create("directional");
    private static final Map<Direction, VoxelShape> SHAPES = buildShapes();

    private static Map<Direction, VoxelShape> buildShapes() {
        var map = new EnumMap<Direction, VoxelShape>(Direction.class);
        VoxelShape zShape = Shapes.or(box(5, 5, 0, 11, 11, 16), box(4, 4, 2, 12, 12, 14));
        VoxelShape xShape = Shapes.or(box(0, 5, 5, 16, 11, 11), box(2, 4, 4, 14, 12, 12));
        VoxelShape yShape = Shapes.or(box(5, 0, 5, 11, 16, 11), box(4, 2, 4, 12, 14, 12));
        map.put(Direction.NORTH, zShape);
        map.put(Direction.SOUTH, zShape);
        map.put(Direction.EAST, xShape);
        map.put(Direction.WEST, xShape);
        map.put(Direction.UP, yShape);
        map.put(Direction.DOWN, yShape);
        return map;
    }

    public MatterCableRelayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, true)
                .setValue(DIRECTIONAL, false));
    }

    public static boolean connectsSide(BlockState state, Direction side) {
        Direction facing = state.getValue(FACING);
        return side == facing || side == facing.getOpposite();
    }

    /** True when the neighbor at {@code neighborPos} can attach to a cable/relay along {@code faceOnNeighbor}. */
    private static boolean isAttachableNeighbor(Level level, BlockPos neighborPos, Direction faceOnNeighbor) {
        BlockState state = level.getBlockState(neighborPos);
        Block block = state.getBlock();
        if (block instanceof MatterCableBlock) {
            return !(level instanceof ServerLevel sl && CableOwners.isSideBlocked(sl, neighborPos, faceOnNeighbor));
        }
        if (block instanceof MatterCableRelayBlock) return connectsSide(state, faceOnNeighbor);
        if (block instanceof dev.latrox.rematter.primal.EntangledLinkBlock) {
            return level.getBlockEntity(neighborPos) instanceof dev.latrox.rematter.primal.EntangledLinkBlockEntity link
                    ? link.connectsCable(faceOnNeighbor)
                    : dev.latrox.rematter.primal.EntangledLinkBlock.connectsSide(state, faceOnNeighbor);
        }
        if (block instanceof dev.latrox.rematter.primal.DimensionalEntangledLinkBlock) {
            return level.getBlockEntity(neighborPos) instanceof dev.latrox.rematter.primal.DimensionalEntangledLinkBlockEntity dlink
                    ? dlink.connectsCable(faceOnNeighbor)
                    : dev.latrox.rematter.primal.DimensionalEntangledLinkBlock.connectsSide(state, faceOnNeighbor);
        }
        if (block instanceof SpatialAnchorBlock) {
            return level.getBlockEntity(neighborPos) instanceof SpatialAnchorBlockEntity anchor
                    ? anchor.connectsCable(faceOnNeighbor)
                    : SpatialAnchorBlock.connectsSide(state, faceOnNeighbor);
        }
        if (block instanceof WirelessAccessPointBlock) return WirelessAccessPointBlock.connectsSide(state, faceOnNeighbor);
        if (block instanceof MatterLevelEmitterBlock) return MatterLevelEmitterBlock.connectsSide(state, faceOnNeighbor);
        if (block instanceof dev.latrox.rematter.energy.EnergyCubeBlock) return true;
        if (block instanceof MachinePortBlock) {
            var mode = state.getValue(MachinePortBlock.MODE);
            return MatterCablePower.allowsEndpoint(state, faceOnNeighbor)
                    && (mode.allows(MachinePortMode.MATTER) || mode.allows(MachinePortMode.POWER) || mode.allows(MachinePortMode.POWER_OUTPUT));
        }
        if (block instanceof MachineBlock machine) {
            return MatterCableConnectionPolicy.acceptsMachine(machine.kind(), state.getValue(MachineBlock.FACING), faceOnNeighbor);
        }
        return false;
    }

    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, OPEN, DIRECTIONAL); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPES.get(state.getValue(FACING)); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        boolean sneaking = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
        if (!sneaking) {
            // 1. Prefer an axis bridging TWO attachable neighbors (e.g. dropping straight into a 1-block cable gap).
            for (Direction axisDir : new Direction[]{Direction.NORTH, Direction.EAST, Direction.UP}) {
                boolean a = isAttachableNeighbor(level, pos.relative(axisDir), axisDir.getOpposite());
                boolean b = isAttachableNeighbor(level, pos.relative(axisDir.getOpposite()), axisDir);
                if (a && b) return defaultBlockState().setValue(FACING, axisDir).setValue(OPEN, true);
            }
            // 2. If clicking directly onto an attachable block (e.g. cable end or machine port), align along that face.
            Direction clicked = context.getClickedFace().getOpposite();
            if (isAttachableNeighbor(level, pos.relative(clicked), clicked.getOpposite())) {
                return defaultBlockState().setValue(FACING, clicked).setValue(OPEN, true);
            }
            // 3. Otherwise snap to any adjacent Matter Cable first, then any other attachable device!
            for (Direction d : Direction.values()) {
                if (level.getBlockState(pos.relative(d)).getBlock() instanceof MatterCableBlock) {
                    return defaultBlockState().setValue(FACING, d).setValue(OPEN, true);
                }
            }
            for (Direction d : Direction.values()) {
                if (isAttachableNeighbor(level, pos.relative(d), d.getOpposite())) {
                    return defaultBlockState().setValue(FACING, d).setValue(OPEN, true);
                }
            }
        }
        Direction fallback = sneaking
                ? context.getNearestLookingDirection().getOpposite()
                : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, fallback).setValue(OPEN, true);
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }

    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MatterCableRelayBlockEntity valve) {
            valve.refreshNow();
        }
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        if (!level.isClientSide() && state.getBlock() != oldState.getBlock()) {
            level.scheduleTick(pos, this, 1);
            if (level instanceof ServerLevel server) ChunkRevisions.touchLoaded(server, pos);
        }
    }

    @Override protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
        ChunkRevisions.touchLoaded(level, pos);
        MatterCableBlock.refreshLoadedAround(level, pos);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        MatterCableBlock.refreshLoadedAround(level, pos);
        if (level.getBlockEntity(pos) instanceof MatterCableRelayBlockEntity valve) valve.refreshNow();
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MatterCableRelayBlockEntity(pos, state);
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MatterCableRelayBlockEntity valve) {
            valve.placedBy(placer, stack);
        }
    }

    @Override protected java.util.List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        var main = new ItemStack(this);
        if (builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY) instanceof MatterCableRelayBlockEntity relay) {
            CustomNames.applyToStack(main, relay.customName());
        }
        return java.util.List.of(main);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof MatterCableRelayBlockEntity valve) {
            if (player instanceof ServerPlayer server && valve.interact(player)) {
                valve.refreshNow();
                server.openMenu(valve, valve::writeMenuInit);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
