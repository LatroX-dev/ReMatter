package dev.latrox.rematter.content;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** Connected crystal sockets; resource ownership remains exclusively with the controller. */
public final class IndustrialModuleBlock extends Block implements EntityBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final BooleanProperty SUSPENDED = BooleanProperty.create("suspended");
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final IndustrialModuleKind kind;
    private final MapCodec<IndustrialModuleBlock> codec;
    private final Map<Direction, VoxelShape> shapes;
    private final Map<Direction, VoxelShape> suspendedShapes;

    public IndustrialModuleBlock(IndustrialModuleKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        codec = simpleCodec(p -> new IndustrialModuleBlock(kind, p));
        shapes = Shapes.rotateHorizontal(outline(kind, false));
        suspendedShapes = Shapes.rotateHorizontal(outline(kind, true));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false).setValue(SUSPENDED, false));
    }
    public IndustrialModuleKind kind() { return kind; }
    @Override protected MapCodec<? extends Block> codec() { return codec; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, ACTIVE, SUSPENDED); }
    @Override public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face.getAxis() != Direction.Axis.Y) return null;
        BlockPos anchor = context.getClickedPos().relative(face.getOpposite());
        var level = context.getLevel();
        if (!level.hasChunkAt(anchor)) return null;
        var support = level.getBlockState(anchor);
        boolean supported = support.is(ModContent.REACTOR_CASING.get()) || support.is(ModContent.REACTOR_GLASS.get())
                || support.is(ModContent.MACHINE_PORT.get()) || support.isFaceSturdy(level, anchor, face, SupportType.FULL);
        return IndustrialModuleMount.forPlacement(face, supported)
                .map(mount -> defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                        .setValue(SUSPENDED, mount.suspended())).orElse(null);
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(SUSPENDED) ? suspendedShapes : shapes).get(state.getValue(FACING));
    }
    private static VoxelShape outline(IndustrialModuleKind kind, boolean suspended) {
        VoxelShape socket = mountedBox(suspended, 0, 0, 0, 16, 2.5, 16);
        // All functions share the same fitting; the ceiling is its exact vertical mirror.
        VoxelShape body = Shapes.or(
                mountedBox(suspended, 2.7, 2.5, 2.7, 13.3, 4.8, 13.3),
                mountedBox(suspended, 4.2, 3.6, 6.7, 5.5, 7, 9.3),
                mountedBox(suspended, 10.5, 3.6, 6.7, 11.8, 7, 9.3),
                mountedBox(suspended, 6.7, 3.6, 4.2, 9.3, 7, 5.5),
                mountedBox(suspended, 6.7, 3.6, 10.5, 9.3, 7, 11.8),
                mountedBox(suspended, 2.7, 4, 2.7, 13.3, 13.4, 13.3),
                mountedBox(suspended, 6.2, 2.35, 1.8, 9.8, 5.15, 2.2),
                mountedBox(suspended, 6.2, 2.35, 13.8, 9.8, 5.15, 14.2));
        return Shapes.or(socket, body);
    }
    private static VoxelShape mountedBox(boolean suspended, double x1, double y1, double z1, double x2, double y2, double z2) {
        return box(x1, suspended ? 16-y2 : y1, z1, x2, suspended ? 16-y1 : y2, z2);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new IndustrialModuleBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModContent.INDUSTRIAL_MODULE_ENTITY.get()) return null;
        return (world, pos, blockState, entity) -> ((IndustrialModuleBlockEntity) entity).serverTick();
    }
}
