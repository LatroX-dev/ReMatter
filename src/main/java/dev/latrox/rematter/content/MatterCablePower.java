package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.preparation.PreparationTier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Loaded cable components forward pushed FE in the caller's transaction, without storing energy.
 * Both cable traversal and recipients are restricted to the cable owner's UUID.
 */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class MatterCablePower {
    // A component at the scan budget is still indexed, so an oversized one is not rescanned on every push.
    private static final int MAX_CACHED_CABLES = 2 * MachineMatterNetwork.SCAN_BUDGET;
    private static final int MAX_CACHED_ORIGINS = 64;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Map<ServerLevel, NetworkCache> CACHES = new IdentityHashMap<>();

    private MatterCablePower() {}

    static @Nullable EnergyHandler capability(Level level, BlockPos pos, @Nullable Direction side) {
        if (!(level instanceof ServerLevel server) || side == null) return null;
        return new Connection(server, pos.immutable(), side);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel server) {
            synchronized (CACHES) {
                CACHES.remove(server);
            }
        }
    }

    private static NetworkCache cache(ServerLevel level) {
        synchronized (CACHES) {
            return CACHES.computeIfAbsent(level, ignored -> new NetworkCache());
        }
    }

    /** Applies the cable's physical side rules before consulting the endpoint's FE capability. Any other block may push FE in. */
    static boolean allowsEndpoint(BlockState state, Direction side) {
        if (state.getBlock() instanceof MachineBlock machine) {
            return machine.kind().processes() && !PreparationTier.manual(machine.kind())
                    && MatterCableConnectionPolicy.acceptsMachine(machine.kind(), state.getValue(MachineBlock.FACING), side, true);
        }
        if (state.getBlock() instanceof MachinePortBlock) {
            return MatterCableConnectionPolicy.acceptsPort(state.getValue(MachinePortBlock.FORMED),
                    state.getValue(MachinePortBlock.FACING), side.getOpposite());
        }
        return !state.isAir();
    }

    private static @Nullable LevelChunk loaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
    }

    /** The player an FE recipient belongs to: a machine's owner, or the owner of the chamber a port serves. */
    private static @Nullable UUID owner(ServerLevel level, BlockPos position) {
        var entity = level.getBlockEntity(position);
        if (entity instanceof MachineBlockEntity machine) return machine.networkOwner();
        if (entity instanceof MachinePortBlockEntity port) {
            var controller = port.controller(null);
            return controller == null ? null : controller.networkOwner();
        }
        return null;
    }

    record Candidate<T>(T endpoint, @Nullable UUID owner, int distance, BlockPos position) {}

    /**
     * The endpoints that receive pushed FE: all of those owned by the player whose endpoint lies nearest to where the
     * FE enters, ties broken by position. Unowned endpoints receive nothing, so a device another player attaches farther
     * away neither taps nor blocks the supply.
     */
    static <T> List<T> recipients(List<Candidate<T>> candidates) {
        Candidate<T> nearest = null;
        for (var candidate : candidates) {
            if (candidate.owner == null) continue;
            if (nearest == null || candidate.distance < nearest.distance
                    || candidate.distance == nearest.distance && candidate.position.compareTo(nearest.position) < 0) nearest = candidate;
        }
        if (nearest == null) return List.of();
        var owner = nearest.owner;
        return candidates.stream().filter(candidate -> owner.equals(candidate.owner)).map(Candidate::endpoint).toList();
    }

    private static final class Connection extends SnapshotJournal<Optional<Flow>> implements EnergyHandler {
        private final ServerLevel level;
        private final BlockPos origin;
        private final Direction side;
        private Optional<Flow> flow = Optional.empty();

        private Connection(ServerLevel level, BlockPos origin, Direction side) {
            this.level = level;
            this.origin = origin;
            this.side = side;
        }

        @Override public long getAmountAsLong() { return 0; }

        @Override public long getCapacityAsLong() {
            // A positive insertion hint keeps senders from treating the cable as full.
            // Actual accepted FE is bounded by the destination, and the cable stores none.
            var chunk = loaded(level, origin);
            return chunk != null && chunk.getBlockState(origin).getBlock() instanceof MatterCableBlock&&CableOwners.owner(level,origin)!=null
                    ? Integer.MAX_VALUE : 0;
        }

        /**
         * Cables only carry FE that a source pushes in. Nothing can pull FE out through a cable, because cables have
         * no owner and a pull would let anyone attached to the cable drain the energy stores connected to it.
         */
        @Override public int extract(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            return 0;
        }

        @Override public int insert(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            if (amount == 0 || !level.getServer().isSameThread()) return 0;
            var cache = cache(level);
            // An adjacent foreign energy proxy may forward straight back into this network.
            if (cache.routing) return 0;
            cache.routing = true;
            try {
                var adjacent = origin.relative(side);
                var incomingChunk = loaded(level, adjacent);
                if (incomingChunk == null) return 0;
                var incoming = incomingChunk.getBlockState(adjacent);
                if (incoming.getBlock() instanceof MatterCableBlock
                        || !incoming.isAir() && !allowsEndpoint(incoming, side.getOpposite())) return 0;

                var component = cache.component(level, origin);
                if (component.endpoints == null) return 0;
                UUID cableOwner=CableOwners.owner(level,origin);
                if(cableOwner==null)return 0;
                var distances = component.distances(origin);
                var candidates = new ArrayList<Candidate<Endpoint>>();
                for (var endpoint : component.endpoints.active()) {
                    if (endpoint.position.equals(adjacent)) continue;
                    if(!cableOwner.equals(owner(level,endpoint.position)))continue;
                    candidates.add(new Candidate<>(endpoint, owner(level, endpoint.position),
                            distances.getOrDefault(endpoint.position, Integer.MAX_VALUE), endpoint.position));
                }
                var endpoints = recipients(candidates);
                if (endpoints.isEmpty()) return 0;
                int remaining = amount;
                int count = endpoints.size();
                int start = Math.floorMod(level.getGameTime() + origin.asLong(), count);
                for (int i = 0; i < count && remaining > 0; i++) {
                    var endpoint = endpoints.get((start + i) % count);
                    for (var face : endpoint.faces) {
                        var handler = face.capability.cache.getCapability();
                        if (handler == null || handler instanceof Connection) continue;
                        int moved = handler.insert(remaining, transaction);
                        if (moved == 0) continue;
                        remaining -= moved;
                        if (flow.isEmpty() && MatterCableActivity.canTrackEnergy(level)) {
                            updateSnapshots(transaction);
                            flow = Optional.of(new Flow(component, face.cable));
                        }
                        break;
                    }
                }
                return amount - remaining;
            } finally {
                cache.routing = false;
            }
        }

        @Override protected Optional<Flow> createSnapshot() { return flow; }
        @Override protected void revertToSnapshot(Optional<Flow> snapshot) { flow = snapshot; }

        @Override protected void onRootCommit(Optional<Flow> originalState) {
            var committed = flow;
            flow = Optional.empty();
            if (committed.isEmpty() || !MatterCableActivity.canTrackEnergy(level)) return;
            var activity = committed.get();
            if (!activity.component.current(level)) return;
            MatterCableActivity.energy(level, activity.component.path(origin, activity.otherCable));
        }
    }

    private record Flow(Component component, BlockPos otherCable) {}
    private record Endpoint(BlockPos position, List<Face> faces) {}
    private record Face(BlockPos cable, FaceCapability capability) {}

    /**
     * The FE capability of one endpoint side. A rescan of its component keeps it, so a block change near the cables
     * does not register a new capability listener for every endpoint; an invalidation reaches the endpoints now using it.
     */
    private static final class FaceCapability {
        private final BlockCapabilityCache<EnergyHandler, @Nullable Direction> cache;
        private @Nullable Endpoints user;

        private FaceCapability(ServerLevel level, BlockPos position, Direction side) {
            cache = BlockCapabilityCache.create(Capabilities.Energy.BLOCK, level, position, side, () -> true, this::invalidate);
        }

        private void invalidate() { if (user != null) user.invalidate(); }
    }

    private static final class Endpoints {
        private final List<Endpoint> candidates = new ArrayList<>();
        private final Map<BlockPos, Endpoint> byPosition = new HashMap<>();
        private @Nullable List<Endpoint> active;

        /** Reuses the capability of each side that {@code previous}, the endpoints of the replaced scan, already watched. */
        private void add(ServerLevel level, BlockPos position, List<EndpointSide> sides, @Nullable Endpoints previous) {
            List<Face> faces = sides.stream().map(side -> {
                var capability = previous == null ? null : previous.capability(position, side.cable);
                if (capability == null) capability = new FaceCapability(level, position, side.outward);
                capability.user = this;
                return new Face(side.cable, capability);
            }).toList();
            var endpoint = new Endpoint(position, faces);
            candidates.add(endpoint);
            byPosition.put(position, endpoint);
        }

        private @Nullable FaceCapability capability(BlockPos position, BlockPos cable) {
            var endpoint = byPosition.get(position);
            if (endpoint == null) return null;
            for (var face : endpoint.faces) if (face.cable.equals(cable)) return face.capability;
            return null;
        }

        private void invalidate() { active = null; }

        private List<Endpoint> active() {
            if (active != null) return active;
            var found = new ArrayList<Endpoint>();
            for (var candidate : candidates) {
                var faces = candidate.faces.stream().filter(face -> face.capability.cache.getCapability() != null).toList();
                if (!faces.isEmpty()) found.add(new Endpoint(candidate.position, faces));
            }
            active = List.copyOf(found);
            return active;
        }
    }

    /**
     * Indexes every cable of the recently used components. A stale or displaced component is dropped as a whole,
     * and above the cable budget the least recently used components go first, so one large network never
     * evicts every other one.
     */
    static final class ComponentIndex<C> {
        private final int budget;
        private final Map<BlockPos, C> byCable = new HashMap<>();
        // Access order: the first entry is the least recently used component.
        private final Map<C, Set<BlockPos>> recent = new LinkedHashMap<>(16, 0.75F, true);
        private int cables;

        ComponentIndex(int budget) { this.budget = budget; }

        @Nullable C get(BlockPos cable) {
            var component = byCable.get(cable);
            if (component != null) recent.get(component);
            return component;
        }
        void put(C component, Set<BlockPos> positions) {
            remove(component);
            if (positions.size() > budget) return;
            for (var position : positions) {
                var displaced = byCable.get(position);
                if (displaced != null) remove(displaced);
            }
            while (cables + positions.size() > budget) remove(recent.keySet().iterator().next());
            positions.forEach(position -> byCable.put(position, component));
            recent.put(component, positions);
            cables += positions.size();
        }
        void remove(C component) {
            var positions = recent.remove(component);
            if (positions == null) return;
            for (var position : positions) byCable.remove(position, component);
            cables -= positions.size();
        }
    }

    private static final class NetworkCache {
        private final ComponentIndex<Component> indexed = new ComponentIndex<>(MAX_CACHED_CABLES);
        private boolean routing;

        private Component component(ServerLevel level, BlockPos origin) {
            var cached = indexed.get(origin);
            if (cached != null && cached.current(level)) return cached;
            if (cached != null) indexed.remove(cached);
            var component = new Discovery(level, cached == null ? null : cached.endpoints).scan(origin);
            if (!component.cables.isEmpty()) indexed.put(component, component.cables);
            return component;
        }
    }

    /** One loaded cable component. Within one game tick an unchanged epoch confirms it without reading any chunk. */
    private static final class Component {
        private final Set<BlockPos> cables;
        private final @Nullable Endpoints endpoints;
        private final List<ChunkRevisions.Stamp> stamps;
        // Access order, so only the cables that recently received FE keep their endpoint distances.
        private final Map<BlockPos, Map<BlockPos, Integer>> distances = new LinkedHashMap<>(16, 0.75F, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<BlockPos, Map<BlockPos, Integer>> eldest) {
                return size() > MAX_CACHED_ORIGINS;
            }
        };
        private long checkedTick = Long.MIN_VALUE, checkedEpoch;

        private Component(Set<BlockPos> cables, @Nullable Endpoints endpoints, List<ChunkRevisions.Stamp> stamps) {
            this.cables = cables; this.endpoints = endpoints; this.stamps = stamps;
        }

        private boolean current(ServerLevel level) {
            long tick = level.getGameTime(), epoch = ChunkRevisions.epoch();
            if (checkedTick == tick && checkedEpoch == epoch) return true;
            if (!stamps.stream().allMatch(stamp -> stamp.matches(level))) return false;
            checkedTick = tick; checkedEpoch = epoch;
            return true;
        }

        /** The cable distance from the origin to each endpoint, through the nearest of its faces. */
        private Map<BlockPos, Integer> distances(BlockPos origin) {
            return distances.computeIfAbsent(origin, start -> {
                var steps = new HashMap<BlockPos, Integer>();
                var queue = new ArrayDeque<BlockPos>();
                steps.put(start, 0);
                queue.add(start);
                while (!queue.isEmpty()) {
                    BlockPos position = queue.removeFirst();
                    int next = steps.get(position) + 1;
                    for (Direction direction : DIRECTIONS) {
                        var neighbor = position.relative(direction);
                        if (cables.contains(neighbor) && steps.putIfAbsent(neighbor, next) == null) queue.add(neighbor);
                    }
                }
                var result = new HashMap<BlockPos, Integer>();
                if (endpoints != null) for (var endpoint : endpoints.candidates) for (var face : endpoint.faces) {
                    Integer cable = steps.get(face.cable);
                    if (cable != null) result.merge(endpoint.position, cable + 1, Math::min);
                }
                return Map.copyOf(result);
            });
        }

        private List<BlockPos> path(BlockPos source, BlockPos target) {
            var previous = new HashMap<BlockPos, BlockPos>();
            var queue = new ArrayDeque<BlockPos>();
            previous.put(source, source);
            queue.add(source);
            while (!queue.isEmpty() && !previous.containsKey(target)) {
                BlockPos position = queue.removeFirst();
                for (Direction direction : DIRECTIONS) {
                    var next = position.relative(direction);
                    if (cables.contains(next) && previous.putIfAbsent(next, position) == null) queue.add(next);
                }
            }
            var result = new ArrayList<BlockPos>();
            if (!previous.containsKey(target)) return result;
            for (BlockPos position = target; !position.equals(source); position = previous.get(position)) {
                result.add(position);
            }
            result.add(source);
            Collections.reverse(result);
            return result;
        }
    }

    private record EndpointSide(BlockPos cable, Direction outward) {}

    private static final class Discovery {
        private final ServerLevel level;
        private final @Nullable Endpoints previous;
        private final Map<Long, ChunkRevisions.Stamp> stamps = new HashMap<>();
        private final Map<Long, LevelChunk> loadedChunks = new HashMap<>();
        private final Map<BlockPos, BlockState> observed = new HashMap<>();

        private Discovery(ServerLevel level, @Nullable Endpoints previous) {
            this.level = level;
            this.previous = previous;
        }

        private Component scan(BlockPos origin) {
            if (!(read(origin).getBlock() instanceof MatterCableBlock)) return result(Set.of(), null);
            UUID cableOwner=CableOwners.owner(level,origin);
            if(cableOwner==null)return result(Set.of(),null);
            var cables = new LinkedHashSet<BlockPos>();
            var ends = new LinkedHashMap<BlockPos, List<EndpointSide>>();
            var queue = new ArrayDeque<BlockPos>();
            cables.add(origin);
            queue.add(origin);
            while (!queue.isEmpty()) {
                var position = queue.removeFirst();
                for (Direction direction : DIRECTIONS) {
                    var next = position.relative(direction);
                    var state = read(next);
                    if (state.getBlock() instanceof MatterCableBlock) {
                        if(!cableOwner.equals(CableOwners.owner(level,next)))continue;
                        if (!cables.add(next)) continue;
                        if (cables.size() > MachineMatterNetwork.SCAN_BUDGET) return result(cables, null);
                        queue.add(next);
                    } else if ((state.getBlock() instanceof MachineBlock || state.getBlock() instanceof MachinePortBlock)
                            && allowsEndpoint(state, direction.getOpposite())) {
                        ends.computeIfAbsent(next, ignored -> new ArrayList<>()).add(new EndpointSide(position, direction.getOpposite()));
                    }
                }
            }
            var endpoints = new Endpoints();
            ends.forEach((position, sides) -> endpoints.add(level, position, sides, previous));
            return result(cables, endpoints);
        }

        private Component result(Set<BlockPos> cables, @Nullable Endpoints endpoints) {
            var component = new Component(Set.copyOf(cables), endpoints, List.copyOf(stamps.values()));
            return component.current(level) ? component : new Component(Set.of(), null, List.of());
        }

        private BlockState read(BlockPos pos) {
            return observed.computeIfAbsent(pos, position -> {
                int x = position.getX() >> 4;
                int z = position.getZ() >> 4;
                long key = (long) x << 32 | (z & 0xffffffffL);
                stamps.computeIfAbsent(key, ignored -> {
                    var chunk = level.getChunkSource().getChunkNow(x, z);
                    if (chunk != null) loadedChunks.put(key, chunk);
                    return ChunkRevisions.Stamp.of(x, z, chunk);
                });
                var chunk = loadedChunks.get(key);
                return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(position);
            });
        }
    }
}
