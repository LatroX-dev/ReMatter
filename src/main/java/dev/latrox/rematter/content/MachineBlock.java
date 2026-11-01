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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
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
    private final MachineKind kind;
    private final MapCodec<MachineBlock> codec;
    private final Map<Direction, VoxelShape> shapes;

    public MachineBlock(MachineKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        this.codec = simpleCodec(p -> new MachineBlock(kind, p));
        this.shapes = Shapes.rotateHorizontal(outline(kind));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }
    public MachineKind kind() { return kind; }
    @Override protected MapCodec<? extends Block> codec() { return codec; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, ACTIVE); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return shapes.get(state.getValue(FACING)); }
    private static VoxelShape outline(MachineKind kind) {
        VoxelShape base = box(1, 0, 1, 15, 3, 15);
        if (kind != MachineKind.TERMINAL) base = Shapes.or(base,
                box(0.5, 4, 6, 2, 9, 11), box(14, 4, 6, 15.5, 9, 11), box(5, 4, 14, 11, 10, 15.5));
        return switch (kind) {
            case TERMINAL -> Shapes.or(base, box(6, 3, 7, 10, 8, 11), box(2, 8, 5, 14, 16, 11), box(3, 5, 2, 13, 7, 7));
            case SYNTHESIZER, ANALYZER -> Shapes.or(base, box(2, 3, 4, 4, 14, 14), box(12, 3, 4, 14, 14, 14), box(2, 13, 4, 14, 16, 14), box(4, 3, 4, 12, 6, 13));
            case ELECTROLYZER -> Shapes.or(base, box(2, 3, 4, 7, 15, 12), box(9, 3, 4, 14, 15, 12), box(5, 10, 8, 11, 14, 13));
            case REACTOR -> Shapes.or(base, box(3, 3, 3, 13, 14, 13), box(5, 14, 5, 11, 16, 11));
            case DECOMPOSER, PREPARATOR, HAND_PREPARATOR, INDUSTRIAL_PREPARATOR -> Shapes.or(base, box(3, 3, 3, 13, 11, 14), box(2, 11, 2, 14, 16, 14));
            case STORAGE, DRIVE, INDUSTRIAL_REACTOR -> Shapes.or(base, box(2, 3, 2, 14, 16, 14));
        };
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        MatterPipeBlock.refreshLoadedAround(level, pos);
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
            if(placer instanceof Player player)machine.claim(player);
        }
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            if (!level.isClientSide() && machine.canUse(player)) player.openMenu(machine);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof MachineBlockEntity machine && machine.canUse(player)) {
            if (!level.isClientSide() && FluidUtil.interactWithFluidHandler(player, hand, pos, machine.fluids(), null)) return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }
    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof MachineBlockEntity machine) {
            return List.of(storedDrop(machine, builder.getLevel().registryAccess()));
        }
        return List.of(new ItemStack(this));
    }
    private ItemStack storedDrop(MachineBlockEntity machine, HolderLookup.Provider registries) {
        ItemStack drop = new ItemStack(this);
        // Placement loads custom BE data and item components through separate vanilla paths.
        drop.applyComponents(machine.collectComponents());
        var data=machine.saveCustomOnly(registries);
        data.remove("industrial_claim");
        drop.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(machine.getType(), data));
        return drop;
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
            popResource(level, pos, storedDrop(machine, level.registryAccess()));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
