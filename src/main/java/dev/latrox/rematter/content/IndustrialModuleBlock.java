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

/** Open machinery: placement, collision and rendering belong to the module, resources to its controller. */
public final class IndustrialModuleBlock extends Block implements EntityBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final IndustrialModuleKind kind;
    private final MapCodec<IndustrialModuleBlock> codec;
    private final Map<Direction, VoxelShape> shapes;

    public IndustrialModuleBlock(IndustrialModuleKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        codec = simpleCodec(p -> new IndustrialModuleBlock(kind, p));
        shapes = Shapes.rotateHorizontal(outline(kind));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }
    public IndustrialModuleKind kind() { return kind; }
    @Override protected MapCodec<? extends Block> codec() { return codec; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, ACTIVE); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return shapes.get(state.getValue(FACING)); }
    private static VoxelShape outline(IndustrialModuleKind kind) {
        VoxelShape feet = Shapes.or(box(1, 0, 1, 3, 16, 3), box(13, 0, 1, 15, 16, 3),
                box(1, 0, 13, 3, 16, 15), box(13, 0, 13, 15, 16, 15));
        return switch (kind) {
            case GRINDING_UNIT -> Shapes.or(feet, box(2, 8, 2, 14, 14, 14));
            case SEPARATION_UNIT -> Shapes.or(feet, box(2, 6, 2, 14, 9, 14));
            case MIXING_BASIN -> Shapes.or(feet, box(2, 1, 2, 14, 8, 14));
            case FIELD_COIL -> Shapes.or(feet, box(3, 1, 3, 13, 14, 13));
            case REACTION_CHAMBER -> Shapes.or(feet, box(4, 1, 4, 12, 15, 12));
        };
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new IndustrialModuleBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModContent.INDUSTRIAL_MODULE_ENTITY.get()) return null;
        return (world, pos, blockState, entity) -> ((IndustrialModuleBlockEntity) entity).serverTick();
    }
}
