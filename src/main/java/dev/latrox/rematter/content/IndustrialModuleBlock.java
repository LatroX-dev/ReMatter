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
        return Shapes.or(
                mountedBox(suspended, 0, 0, 0, 16, 2, 16),
                mountedBox(suspended, 3, 2, 3, 13, 3, 13),
                switch(kind) {
                    case GRINDING_UNIT -> Shapes.or(
                            mountedBox(suspended, 5, 3, 6, 11, 5, 10),
                            mountedBox(suspended, 11, 3, 6, 13, 11, 10),
                            mountedBox(suspended, 3, 3, 6, 5, 11, 10),
                            mountedBox(suspended, 3, 11, 5, 13, 13, 11),
                            mountedBox(suspended, 5, 7, 7.5, 11, 11, 8.5),
                            mountedBox(suspended, 5, 6, 7.5, 11, 7, 8.5),
                            mountedBox(suspended, 6, 13, 6, 10, 15, 10),
                            mountedBox(suspended, 7, 15, 7, 9, 16, 9));
                    case SEPARATION_UNIT -> Shapes.or(
                            mountedBox(suspended, 6, 3, 6, 10, 4, 10),
                            mountedBox(suspended, 3, 4, 3, 13, 5, 13),
                            mountedBox(suspended, 3, 8, 3, 13, 9, 13),
                            mountedBox(suspended, 3, 12, 3, 13, 13, 13),
                            mountedBox(suspended, 6, 5, 6, 10, 8, 10),
                            mountedBox(suspended, 6, 9, 6, 10, 12, 10),
                            mountedBox(suspended, 6, 13, 6, 10, 15, 10),
                            mountedBox(suspended, 7, 15, 7, 9, 16, 9));
                    case MIXING_BASIN -> Shapes.or(
                            mountedBox(suspended, 2, 3, 2, 14, 8, 3),
                            mountedBox(suspended, 2, 3, 13, 14, 8, 14),
                            mountedBox(suspended, 13, 3, 3, 14, 8, 13),
                            mountedBox(suspended, 2, 3, 3, 3, 8, 13),
                            mountedBox(suspended, 4, 6, 7.5, 12, 7, 8.5),
                            mountedBox(suspended, 7, 7, 7, 9, 11, 9),
                            mountedBox(suspended, 3, 3, 3, 13, 6, 13),
                            mountedBox(suspended, 6.5, 11, 6.5, 9.5, 13, 9.5),
                            mountedBox(suspended, 7, 13, 7, 9, 14, 9));
                    case FIELD_COIL -> Shapes.or(
                            mountedBox(suspended, 6, 3, 6, 10, 4, 10),
                            mountedBox(suspended, 12, 3, 6, 13, 7, 10),
                            mountedBox(suspended, 3, 3, 6, 4, 7, 10),
                            mountedBox(suspended, 2, 7, 2, 14, 10, 4),
                            mountedBox(suspended, 2, 7, 12, 14, 10, 14),
                            mountedBox(suspended, 12, 7, 4, 14, 10, 12),
                            mountedBox(suspended, 2, 7, 4, 4, 10, 12),
                            mountedBox(suspended, 6.5, 4, 6.5, 9.5, 14, 9.5),
                            mountedBox(suspended, 7, 14, 7, 9, 15, 9));
                    case REACTION_CHAMBER -> Shapes.or(
                            mountedBox(suspended, 7, 3, 7, 9, 5, 9),
                            mountedBox(suspended, 3, 5, 6.5, 13, 6, 9.5),
                            mountedBox(suspended, 12, 6, 6.5, 13, 11, 9.5),
                            mountedBox(suspended, 3, 6, 6.5, 4, 11, 9.5),
                            mountedBox(suspended, 6, 6, 7, 10, 7, 9),
                            mountedBox(suspended, 4, 7, 7, 12, 12, 9),
                            mountedBox(suspended, 6, 12, 7, 10, 13, 9),
                            mountedBox(suspended, 6, 8, 6.5, 10, 11, 7),
                            mountedBox(suspended, 6, 8, 9, 10, 11, 9.5));
                });
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
