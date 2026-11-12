package dev.latrox.rematter.content;

import com.mojang.serialization.MapCodec;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * Six-way visual connections respect compact endpoints and the exterior of formed ports. A connected side whose
 * neighbour is not another cable is a device arm and ends in a flange.
 */
public final class MatterCableBlock extends PipeBlock {
    public static final MapCodec<MatterCableBlock> CODEC = simpleCodec(MatterCableBlock::new);
    /** north_device ... down_device: true while that side is connected to a machine, a port or an energy block. */
    public static final Map<Direction, BooleanProperty> DEVICE_BY_DIRECTION = deviceProperties();
    public enum Tier {
        BASIC(256,128,2_048), REINFORCED(384,1_024,16_384), RESONANT(512,8_192,131_072);

        private final int networkLimit, matterRate, energyRate;
        Tier(int networkLimit,int matterRate,int energyRate) { this.networkLimit=networkLimit;this.matterRate=matterRate;this.energyRate=energyRate; }
        public int matterRate(){return matterRate;}
        public int energyRate(){return energyRate;}
        public int networkLimit() { return networkLimit; }
    }
    private final Tier tier;
    private final Function<BlockState, VoxelShape> outlines;

    private static Map<Direction, BooleanProperty> deviceProperties() {
        var properties = new EnumMap<Direction, BooleanProperty>(Direction.class);
        for (Direction side : Direction.values()) properties.put(side, BooleanProperty.create(side.getName() + "_device"));
        return Collections.unmodifiableMap(properties);
    }

    public MatterCableBlock(Properties properties) {
        this(Tier.BASIC, properties);
    }
    public MatterCableBlock(Tier tier, Properties properties) {
        super(4, properties);
        this.tier = tier;
        BlockState state = stateDefinition.any();
        for (var property : PROPERTY_BY_DIRECTION.values()) state = state.setValue(property, false);
        for (var property : DEVICE_BY_DIRECTION.values()) state = state.setValue(property, false);
        registerDefaultState(state);
        outlines = getShapeForEachState(MatterCableBlock::outline);
    }
    public Tier tier() { return tier; }
    @Override protected MapCodec<MatterCableBlock> codec() {
        return tier == Tier.BASIC ? CODEC : simpleCodec(properties -> new MatterCableBlock(tier, properties));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
        for (Direction side : Direction.values()) builder.add(DEVICE_BY_DIRECTION.get(side));
    }
    public static int connections(BlockState state) {
        int count = 0;
        for (var property : PROPERTY_BY_DIRECTION.values()) if (state.getValue(property)) count++;
        return count;
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return outlines.apply(state);
    }
    private static VoxelShape outline(BlockState state) {
        int arms = 0, devices = 0;
        for (Direction side : Direction.values()) {
            if (state.getValue(PROPERTY_BY_DIRECTION.get(side))) arms |= MatterCableShapes.bit(side);
            if (state.getValue(DEVICE_BY_DIRECTION.get(side))) devices |= MatterCableShapes.bit(side);
        }
        return MatterCableShapes.shape(arms, devices);
    }
    /** Sets the arm of one side and, with it, whether that arm ends in a device flange. */
    private static BlockState withSide(BlockState state, Direction side, boolean connected, BlockState neighbor) {
        return state.setValue(PROPERTY_BY_DIRECTION.get(side), connected).setValue(DEVICE_BY_DIRECTION.get(side),
                MatterCableConnectionPolicy.deviceArm(connected, neighbor.getBlock() instanceof MatterCableBlock));
    }
    private static boolean connects(LevelReader level, BlockPos position, BlockState neighbor, Direction towardsNeighbor) {
        if(level instanceof ServerLevel server) {
            var owner=CableOwners.owner(server,position.relative(towardsNeighbor.getOpposite()));
            if(owner==null)return false;
            if(neighbor.getBlock() instanceof MatterCableBlock&&!owner.equals(CableOwners.owner(server,position)))return false;
            if(level.getBlockEntity(position) instanceof MachineBlockEntity machine&&!owner.equals(machine.networkOwner()))return false;
            if(level.getBlockEntity(position) instanceof dev.latrox.rematter.primal.EntangledLinkBlockEntity link&&!owner.equals(link.owner()))return false;
            if(level.getBlockEntity(position) instanceof MachinePortBlockEntity port) {
                var controller=port.controller(null);if(controller==null||!owner.equals(controller.networkOwner()))return false;
            }
        }
        if (neighbor.getBlock() instanceof MatterCableBlock) return true;
        if(level instanceof ServerLevel server&&level.getBlockEntity(position) instanceof dev.latrox.rematter.primal.EntangledLinkBlockEntity link)return link.owner()!=null&&link.owner().equals(CableOwners.owner(server,position.relative(towardsNeighbor.getOpposite())));
        Direction outward = towardsNeighbor.getOpposite();
        if (neighbor.getBlock() instanceof MachineBlock machine) {
            if (!MatterCableConnectionPolicy.acceptsMachine(machine.kind(), neighbor.getValue(MachineBlock.FACING), outward, true))
                return false;
            return !(level.getBlockEntity(position) instanceof MachineBlockEntity entity) || entity.acceptsMatter(outward, true)
                    || MatterCablePower.allowsEndpoint(neighbor, outward) && entity.allowsPort(outward, MachinePortMode.POWER);
        }
        if (neighbor.getBlock() instanceof MachinePortBlock) {
            var mode = neighbor.getValue(MachinePortBlock.MODE);
            return MatterCablePower.allowsEndpoint(neighbor, outward)
                    && (mode.allows(MachinePortMode.MATTER) || mode.allows(MachinePortMode.POWER));
        }
        return !neighbor.isAir() && level instanceof ServerLevel server
                && server.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) != null
                && server.getCapability(Capabilities.Energy.BLOCK, position, neighbor, null, outward) != null;
    }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        if (state.getBlock() != oldState.getBlock()) {
            // NeoForge calls this after setPlacedBy when its placement / protection event succeeds.
            if(level instanceof ServerLevel server)CableOwners.finishPlacement(server,pos);
            level.invalidateCapabilities(pos);
        }
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,
            net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level,pos,state,placer,stack);
        if(level instanceof ServerLevel server&&placer instanceof net.minecraft.world.entity.player.Player player)
            CableOwners.placedBy(server,pos,player.getUUID());
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        // Also runs when a protection event restores the block captured before placement.
        CableOwners.discardPlacement(level,pos);
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
        if(!(level.getBlockState(pos).getBlock() instanceof MatterCableBlock))CableOwners.set(level,pos,null);
        level.invalidateCapabilities(pos);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction side : Direction.values()) {
            BlockPos neighborPos = context.getClickedPos().relative(side);
            BlockState neighbor = context.getLevel().getBlockState(neighborPos);
            state = withSide(state, side, connects(context.getLevel(), neighborPos, neighbor, side), neighbor);
        }
        return state;
    }
    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
            BlockPos pos, Direction side, BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        // Ownership and configured endpoints are server data. Client neighbour prediction must not undo its shape.
        if (!(level instanceof ServerLevel)) return state;
        return withSide(state, side, connects(level, neighborPos, neighbor, side), neighbor);
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        refreshLoadedAround(level, pos);
    }
    /** Restores saved connection shapes without loading the neighboring chunks. */
    static void refreshLoadedAround(ServerLevel level, BlockPos pos) {
        refreshLoadedPipe(level, pos);
        for (Direction side : Direction.values()) refreshLoadedPipe(level, pos.relative(side));
    }
    private static void refreshLoadedPipe(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return;
        BlockState state = chunk.getBlockState(pos);
        if (!(state.getBlock() instanceof MatterCableBlock)) return;
        BlockState updated = state;
        for (Direction side : Direction.values()) {
            BlockPos neighborPos = pos.relative(side);
            var neighborChunk = level.getChunkSource().getChunkNow(neighborPos.getX() >> 4, neighborPos.getZ() >> 4);
            // Keep the saved connection until that neighboring chunk is available.
            if (neighborChunk == null) continue;
            BlockState neighbor = neighborChunk.getBlockState(neighborPos);
            boolean connected = connects(level, neighborPos, neighbor, side);
            updated = withSide(updated, side, connected, neighbor);
            // This refresh bypasses neighbor shape updates, so update the matching socket here.
            if (neighbor.getBlock() instanceof MachineBlock machine) {
                BlockState withPort = machine.withCablePort(neighbor, side.getOpposite(), connected);
                if (withPort != neighbor) level.setBlock(neighborPos, withPort, UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE);
            }
        }
        if (updated != state) level.setBlock(pos, updated, UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE);
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return turned(state, rotation::rotate); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return turned(state, mirror::mirror); }
    /** An arm and its flange move to the same new side. */
    private static BlockState turned(BlockState state, UnaryOperator<Direction> turn) {
        BlockState result = state;
        for (Direction side : Direction.values()) {
            Direction target = turn.apply(side);
            result = result.setValue(PROPERTY_BY_DIRECTION.get(target), state.getValue(PROPERTY_BY_DIRECTION.get(side)))
                    .setValue(DEVICE_BY_DIRECTION.get(target), state.getValue(DEVICE_BY_DIRECTION.get(side)));
        }
        return result;
    }
}
