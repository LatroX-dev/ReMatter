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
    /** True while FE has recently flowed through or energized this cable segment (bright vs dimmed traces). */
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    /** north_device ... down_device: true while that side is connected to a machine, a port or an energy block. */
    public static final Map<Direction, BooleanProperty> DEVICE_BY_DIRECTION = deviceProperties();
    public enum Tier {
        BASIC(256,128,4_096), REINFORCED(384,1_024,65_536), RESONANT(512,8_192,524_288);

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
        BlockState state = stateDefinition.any().setValue(POWERED, false);
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
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, POWERED);
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
            BlockPos selfPos=position.relative(towardsNeighbor.getOpposite());
            if(CableOwners.isSideBlocked(server,selfPos,towardsNeighbor))return false;
            var owner=CableOwners.owner(server,selfPos);
            var mode=CableOwners.mode(server,selfPos);
            if(owner==null&&mode!=SecurityMode.PUBLIC)return false;
            if(neighbor.getBlock() instanceof MatterCableBlock) {
                if(CableOwners.isSideBlocked(server,position,towardsNeighbor.getOpposite()))return false;
                if(!MachineAccess.canLinkCable(server,owner,mode,CableOwners.owner(server,position),CableOwners.mode(server,position)))return false;
            }
            if(level.getBlockEntity(position) instanceof MachineBlockEntity machine&&!MachineAccess.canConnectCableToDevice(server,owner,mode,machine.networkOwner(),machine.securityMode()))return false;
            if(level.getBlockEntity(position) instanceof dev.latrox.rematter.energy.EnergyCubeBlockEntity cube&&!MachineAccess.canConnectCableToDevice(server,owner,mode,cube.owner(),cube.securityMode()))return false;
            if(level.getBlockEntity(position) instanceof WirelessAccessPointBlockEntity ap&&!MachineAccess.canConnectCableToDevice(server,owner,mode,ap.owner(),ap.securityMode()))return false;
            if(level.getBlockEntity(position) instanceof MatterLevelEmitterBlockEntity emitter&&!MachineAccess.canConnectCableToDevice(server,owner,mode,emitter.owner(),emitter.securityMode()))return false;
            if(level.getBlockEntity(position) instanceof MatterCableRelayBlockEntity valve&&!MachineAccess.canLinkCable(server,owner,mode,valve.owner(),valve.securityMode()))return false;
            if(level.getBlockEntity(position) instanceof SpatialAnchorBlockEntity anchor&&!MachineAccess.canConnectCableToDevice(server,owner,mode,anchor.owner(),anchor.securityMode()))return false;
            if(level.getBlockEntity(position) instanceof dev.latrox.rematter.primal.EntangledLinkBlockEntity link&&!MachineAccess.canLinkCable(server,owner,mode,link.owner(),link.securityMode()))return false;
            if(level.getBlockEntity(position) instanceof dev.latrox.rematter.primal.DimensionalEntangledLinkBlockEntity dlink&&!MachineAccess.canLinkCable(server,owner,mode,dlink.owner(),dlink.securityMode()))return false;
            if(level.getBlockEntity(position) instanceof MachinePortBlockEntity port) {
                var controller=port.controller(null);if(controller==null||!MachineAccess.canConnectCableToDevice(server,owner,mode,controller.networkOwner(),controller.securityMode()))return false;
            }
        }
        if (neighbor.getBlock() instanceof MatterCableBlock) return true;
        Direction outward = towardsNeighbor.getOpposite();
        if (neighbor.getBlock() instanceof dev.latrox.rematter.primal.EntangledLinkBlock) {
            return level.getBlockEntity(position) instanceof dev.latrox.rematter.primal.EntangledLinkBlockEntity link
                    ? link.connectsCable(outward)
                    : dev.latrox.rematter.primal.EntangledLinkBlock.connectsSide(neighbor, outward);
        }
        if (neighbor.getBlock() instanceof dev.latrox.rematter.primal.DimensionalEntangledLinkBlock) {
            return level.getBlockEntity(position) instanceof dev.latrox.rematter.primal.DimensionalEntangledLinkBlockEntity dlink
                    ? dlink.connectsCable(outward)
                    : dev.latrox.rematter.primal.DimensionalEntangledLinkBlock.connectsSide(neighbor, outward);
        }
        if (neighbor.getBlock() instanceof SpatialAnchorBlock) {
            return level.getBlockEntity(position) instanceof SpatialAnchorBlockEntity anchor
                    ? anchor.connectsCable(outward)
                    : SpatialAnchorBlock.connectsSide(neighbor, outward);
        }
        if (neighbor.getBlock() instanceof WirelessAccessPointBlock) {
            return WirelessAccessPointBlock.connectsSide(neighbor, outward);
        }
        if (neighbor.getBlock() instanceof MatterLevelEmitterBlock) {
            return MatterLevelEmitterBlock.connectsSide(neighbor, outward);
        }
        if (neighbor.getBlock() instanceof MatterCableRelayBlock) {
            return MatterCableRelayBlock.connectsSide(neighbor, outward);
        }
        if (neighbor.getBlock() instanceof MachineBlock machine) {
            if (!MatterCableConnectionPolicy.acceptsMachine(machine.kind(), neighbor.getValue(MachineBlock.FACING), outward))
                return false;
            return !(level.getBlockEntity(position) instanceof MachineBlockEntity entity) || entity.acceptsMatter(outward)
                    || MatterCablePower.allowsEndpoint(neighbor, outward)
                    && (entity.allowsPort(outward, MachinePortMode.POWER) || entity.allowsPort(outward, MachinePortMode.POWER_OUTPUT));
        }
        if (neighbor.getBlock() instanceof MachinePortBlock) {
            var mode = neighbor.getValue(MachinePortBlock.MODE);
            return MatterCablePower.allowsEndpoint(neighbor, outward)
                    && (mode.allows(MachinePortMode.MATTER) || mode.allows(MachinePortMode.POWER) || mode.allows(MachinePortMode.POWER_OUTPUT));
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
            CableOwners.placedBy(server,pos,player.getUUID(),MachineAccess.placementDefault(server,player));
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        // Also runs when a protection event restores the block captured before placement.
        CableOwners.discardPlacement(level,pos);
        MatterCablePower.clearPowered(level, pos);
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
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
            net.minecraft.world.level.redstone.@org.jspecify.annotations.Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        // External mods (e.g. Mekanism Configurator, Ender IO conduit bundles, Powah side config) may add or remove
        // their FE capability without changing BlockState, so refresh our shape on neighbor notifications too.
        if (level instanceof ServerLevel server) refreshLoadedPipe(server, pos);
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean handledPower = MatterCablePower.onCableTick(level, pos, state);
        if (handledPower) {
            refreshLoadedPipe(level, pos);
            return;
        }
        refreshLoadedAround(level, pos);
    }
    /** Restores saved connection shapes without loading the neighboring chunks. */
    public static void refreshLoadedAround(ServerLevel level, BlockPos pos) {
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

        }
        if (updated != state) {
            level.setBlock(pos, updated, UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE);
            ChunkRevisions.touchLoaded(level, pos);
        }
    }
    @Override protected net.minecraft.world.InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.phys.BlockHitResult hit) {
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty() || player.isSpectator()) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel server)) return net.minecraft.world.InteractionResult.SUCCESS;
        return toggleSideInteraction(player, server, pos, hit);
    }

    /** Picks the clicked cable arm when hitting a protruding arm, or the clicked face on the center core/hub. */
    static Direction clickedSide(BlockPos pos, net.minecraft.world.phys.BlockHitResult hit) {
        var loc = hit.getLocation();
        double dx = loc.x - (pos.getX() + 0.5);
        double dy = loc.y - (pos.getY() + 0.5);
        double dz = loc.z - (pos.getZ() + 0.5);
        double ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
        double max = Math.max(ax, Math.max(ay, az));
        if (max > 0.21) {
            if (ax >= ay && ax >= az) return dx > 0 ? Direction.EAST : Direction.WEST;
            if (ay >= ax && ay >= az) return dy > 0 ? Direction.UP : Direction.DOWN;
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
        return hit.getDirection();
    }

    static net.minecraft.world.InteractionResult toggleSideInteraction(
            net.minecraft.world.entity.player.Player player, ServerLevel server, BlockPos pos,
            net.minecraft.world.phys.BlockHitResult hit) {
        if (!CableOwners.canRemove(server, pos, player)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.rematter.private_machine"));
            return net.minecraft.world.InteractionResult.FAIL;
        }
        Direction side = clickedSide(pos, hit);
        boolean disconnected = CableOwners.toggleSideBlocked(server, pos, side, player);
        net.minecraft.network.chat.Component sideName = net.minecraft.network.chat.Component.translatable(
                "direction.rematter." + side.getName());
        player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
                disconnected ? "message.rematter.cable_side_disconnected" : "message.rematter.cable_side_connected",
                sideName));
        ModSounds.play(server, pos, ModSounds.WRENCH_CONFIGURE, 1.0F, disconnected ? 0.88F : 1.08F);
        return net.minecraft.world.InteractionResult.SUCCESS;
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
