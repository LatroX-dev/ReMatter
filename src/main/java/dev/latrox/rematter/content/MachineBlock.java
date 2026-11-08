package dev.latrox.rematter.content;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

public final class MachineBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private static final BooleanProperty LEFT_CABLE = BooleanProperty.create("left_cable");
    private static final BooleanProperty RIGHT_CABLE = BooleanProperty.create("right_cable");
    private static final BooleanProperty BACK_CABLE = BooleanProperty.create("back_cable");
    private static final BooleanProperty BOTTOM_CABLE = BooleanProperty.create("bottom_cable");
    private static final Map<MachinePortMode.Side, BooleanProperty> CABLE_PORTS = Map.of(
            MachinePortMode.Side.LEFT, LEFT_CABLE, MachinePortMode.Side.RIGHT, RIGHT_CABLE,
            MachinePortMode.Side.BACK, BACK_CABLE, MachinePortMode.Side.BOTTOM, BOTTOM_CABLE);
    private static final Map<Direction, VoxelShape> CABLE_PORT_SHAPES = Map.of(
            Direction.NORTH, box(5, 5, 0, 11, 11, 2), Direction.SOUTH, box(5, 5, 14, 11, 11, 16),
            Direction.WEST, box(0, 5, 5, 2, 11, 11), Direction.EAST, box(14, 5, 5, 16, 11, 11),
            Direction.DOWN, box(5, 0, 5, 11, 2, 11));
    private final MachineKind kind;
    private final MapCodec<MachineBlock> codec;
    private final Map<Direction, VoxelShape> shapes;

    public MachineBlock(MachineKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        this.codec = simpleCodec(p -> new MachineBlock(kind, p));
        this.shapes = Shapes.rotateHorizontal(outline(kind));
        BlockState state = stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false);
        for (BooleanProperty cablePort : CABLE_PORTS.values()) state = state.setValue(cablePort, false);
        registerDefaultState(state);
    }
    public MachineKind kind() { return kind; }
    @Override protected MapCodec<? extends Block> codec() { return codec; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE, LEFT_CABLE, RIGHT_CABLE, BACK_CABLE, BOTTOM_CABLE);
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction front = state.getValue(FACING);
        VoxelShape shape = shapes.get(front);
        for (var port : CABLE_PORTS.entrySet()) {
            if (state.getValue(port.getValue())) shape = Shapes.or(shape, CABLE_PORT_SHAPES.get(port.getKey().world(front)));
        }
        return shape;
    }
    private static VoxelShape outline(MachineKind kind) {
        if (IndustrialStructure.isIndustrial(kind)) return Shapes.block();
        VoxelShape base = box(1,0,1,15,3.2,15);
        return switch (kind) {
            case ANALYZER -> Shapes.or(base,box(3.2,3.7,2.7,12.8,4.6,12.3),
                    box(1.7,3.2,10.6,3.7,12.3,14.3),box(3,12,9.7,11.7,14.7,14.3),
                    box(12,3.2,10.8,14,9,14.3));
            case SYNTHESIZER -> Shapes.or(base,box(3.7,4.1,3.1,12.3,5,11.7),
                    box(1.4,3.2,2.6,4.6,10.9,5.8),box(11.4,3.2,2.6,14.6,10.9,5.8),
                    box(6.4,3.2,10.8,9.6,15.1,14));
            case DECOMPOSER -> Shapes.or(base,box(3.4,3.8,3,12.6,4.7,12.2),
                    box(1.6,3.2,4.3,5.2,13,12.5),box(10.8,3.2,4.3,14.4,13,12.5));
            case PREPARATOR -> Shapes.or(base,box(2.8,5.1,2.5,13.2,6,12.9),
                    box(1.2,6.2,4,2.8,8.4,10.5),box(13.2,6.2,4,14.8,8.4,10.5),
                    box(2.15,3.2,10.9,4.25,13.8,14.1),box(6.95,3.2,10.9,9.05,13.8,14.1),
                    box(11.75,3.2,10.9,13.85,13.8,14.1));
            case HAND_PREPARATOR -> Shapes.or(base,box(1.5,3.2,3,3.5,9.2,13.5),box(12.5,3.2,3,14.5,9.2,13.5),
                    box(2.2,9.1,3.8,4,13.2,12.4),box(12,9.1,3.8,13.8,13.2,12.4),
                    box(3.5,3.3,4.2,12.5,5.2,11.8),box(13.4,10.3,5.2,15.95,15.2,6.8),
                    box(4,9.6,4.4,12,12.4,11),box(3.6,6.85,4.7,12.4,7.15,10.7));
            case ELECTROLYZER -> Shapes.or(base,box(2,3.2,4.5,6.6,14.5,11.5),
                    box(9.4,3.2,4.5,14,14.5,11.5),box(6.9,3.2,11.5,9.1,14.6,14.8));
            case REACTOR -> Shapes.or(base,box(3.1,3.9,3.1,12.9,4.8,12.9),
                    box(1.7,4.5,10.7,3.1,13.4,12.4),box(12.9,4.5,10.7,14.3,13.4,12.4),
                    box(2.7,13.1,5.8,3.5,13.6,10.2),box(12.5,13.1,5.8,13.3,13.6,10.2),
                    box(5.8,13.1,2.7,10.2,13.6,3.5),box(5.5,13.1,11.4,10.5,14.5,13.6));
            case SINTER_FURNACE -> Shapes.or(base,box(.7,1.4,1.5,15.3,3.7,15),
                    box(1.1,3.7,5,3.6,12.5,14.6),box(12.4,3.7,5,14.9,12.5,14.6),
                    box(3.6,3.7,12.6,12.4,12.5,14.6),box(2.1,12.5,5,13.9,14.2,14.6),
                    box(5.3,14.2,7,10.7,15.5,12),box(3.5,3.7,.65,12.5,4.6,4.65));
            case DRIVE -> Shapes.or(base,box(1,2.1,1.8,2.3,15.5,14.8),box(13.7,2.1,1.8,15,15.5,14.8),
                    box(2.3,2.1,12.7,13.7,14.4,14.8),box(2.3,14.4,1.8,13.7,15.5,14.8),
                    box(7.25,2.1,1.8,8.75,14.4,12.7),box(2.3,3.15,2,13.7,13.1,12.7));
            case TERMINAL -> Shapes.or(base,box(6.8,3.2,10.5,9.2,8.4,13.2),
                    box(1.5,7.1,10.2,14.5,15.4,12.8),box(2.2,4.5,1.7,13.8,5.7,10.4));
            case NETWORK_INTERFACE -> Shapes.or(base,box(2,3.2,8.8,14,14.8,14),box(6.7,4.2,2.8,9.3,13.4,9),
                    box(2.9,3.2,3.8,5.9,14.5,10.8),box(10.1,3.2,3.8,13.1,14.5,10.8));
            default -> base;
        };
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        if (!level.isClientSide() && state.getBlock() != oldState.getBlock() && !IndustrialStructure.isIndustrial(kind)) {
            // A neighboring cable may acquire its arm only after the machine is placed.
            level.scheduleTick(pos, this, 1);
        }
    }
    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
            BlockPos pos, Direction side, BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        return withCablePort(state, side, hasCableArm(neighbor, side));
    }
    private static boolean hasCableArm(BlockState neighbor, Direction towardCable) {
        return neighbor.getBlock() instanceof MatterCableBlock
                && neighbor.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(towardCable.getOpposite()));
    }
    BlockState withCablePort(BlockState state, Direction towardCable, boolean connected) {
        Direction front = state.getValue(FACING);
        var property = CABLE_PORTS.get(MachinePortMode.Side.local(front, towardCable));
        if (property == null) return state;
        return state.setValue(property, connected && MatterCableConnectionPolicy.acceptsMachine(kind, front, towardCable, true));
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        MatterCableBlock.refreshLoadedAround(level, pos);
        if (IndustrialStructure.isIndustrial(kind)) return;
        BlockState current = level.getBlockState(pos);
        BlockState updated = current;
        Direction front = current.getValue(FACING);
        for (var face : CABLE_PORTS.keySet()) {
            Direction side = face.world(front);
            BlockPos neighborPos = pos.relative(side);
            var neighborChunk = level.getChunkSource().getChunkNow(neighborPos.getX() >> 4, neighborPos.getZ() >> 4);
            if (neighborChunk != null) updated = withCablePort(updated, side, hasCableArm(neighborChunk.getBlockState(neighborPos), side));
        }
        if (updated != current) level.setBlock(pos, updated, UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MachineBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModContent.MACHINE_ENTITY.get()) return null;
        return (world, pos, blockState, entity) -> ((MachineBlockEntity) entity).serverTick();
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            machine.clearIndustrialProtectionClaim();
            machine.placedBy(placer);
        }
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            if (!level.isClientSide() && machine.interact(player)) {
                if (player.isShiftKeyDown() && MachinePortMode.configurable(kind)) {
                    if (machine.cyclePort(player, hit.getDirection())) {
                        var side = MachinePortMode.Side.local(state.getValue(FACING), hit.getDirection());
                        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                                "port.rematter.side_mode", side.label(), machine.portMode(side).label()));
                    }
                } else player.openMenu(machine);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof MachineBlockEntity machine && !level.isClientSide() && machine.interact(player)) {
            if (FluidUtil.interactWithFluidHandler(player, hand, pos, machine.fluids(), null)) return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }
    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof MachineBlockEntity machine) {
            return storedDrops(machine, builder.getLevel().registryAccess());
        }
        return List.of(new ItemStack(this));
    }
    private List<ItemStack> storedDrops(MachineBlockEntity machine, HolderLookup.Provider registries) {
        ItemStack drop = new ItemStack(this);
        // Placement loads custom BE data and item components through separate vanilla paths.
        drop.applyComponents(machine.collectComponents());
        var data=machine.saveCustomOnly(registries);
        // A machine item is a portable container: the next placer becomes its owner.
        data.remove("owner");
        data.remove("industrial_claim");
        drop.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(machine.getType(), data));
        return List.of(drop);
    }
    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData, Player player) {
        // Vanilla appends full block data after this call, so an empty result must stop foreign data picks.
        if (includeData && level.getBlockEntity(pos) instanceof MachineBlockEntity machine && !machine.canAccess(player)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(this);
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative() && level.getBlockEntity(pos) instanceof MachineBlockEntity machine && machine.hasStoredResources()) {
            for (var drop : storedDrops(machine, level.registryAccess())) popResource(level, pos, drop);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
