package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.ChunkRevisions.Revision;
import dev.latrox.rematter.content.preparation.PreparationTier;
import java.lang.ref.WeakReference;
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

/** Loaded cable components forward pushed FE in the caller's transaction, without storing energy. */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class MatterCablePower {
    private static final int MAX_CACHED_CABLES = 4096;
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

    /** Applies the cable's physical side rules before consulting the endpoint's FE capability. */
    static boolean allowsEndpoint(BlockState state, Direction side) {
        if (state.getBlock() instanceof MachineBlock machine) {
            return machine.kind().processes() && !PreparationTier.manual(machine.kind())
                    && MatterPipeConnectionPolicy.acceptsMachine(machine.kind(), state.getValue(MachineBlock.FACING), side, true);
        }
        if (state.getBlock() instanceof MachinePortBlock) {
            return MatterPipeConnectionPolicy.acceptsPort(state.getValue(MachinePortBlock.FORMED),
                    state.getValue(MachinePortBlock.FACING), side.getOpposite());
        }
        return !state.isAir();
    }

    private static @Nullable LevelChunk loaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
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
            return chunk != null && chunk.getBlockState(origin).getBlock() instanceof MatterPipeBlock
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
                if (incoming.getBlock() instanceof MatterPipeBlock
                        || !incoming.isAir() && !allowsEndpoint(incoming, side.getOpposite())) return 0;

                var component = cache.component(level, origin);
                if (component.endpoints == null) return 0;
                var endpoints = component.endpoints.active();
                if (endpoints.isEmpty()) return 0;
                int remaining = amount;
                int count = endpoints.size();
                int start = Math.floorMod(level.getGameTime() + origin.asLong(), count);
                for (int i = 0; i < count && remaining > 0; i++) {
                    var endpoint = endpoints.get((start + i) % count);
                    if (endpoint.position.equals(adjacent)) continue;
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
        private final List<ChunkStamp> stamps;
        private long checkedTick = Long.MIN_VALUE, checkedEpoch;

        private Component(Set<BlockPos> cables, @Nullable Endpoints endpoints, List<ChunkStamp> stamps) {
            this.cables = cables; this.endpoints = endpoints; this.stamps = stamps;
        }

        private boolean current(ServerLevel level) {
            long tick = level.getGameTime(), epoch = ChunkRevisions.epoch();
            if (checkedTick == tick && checkedEpoch == epoch) return true;
            if (!stamps.stream().allMatch(stamp -> stamp.matches(level))) return false;
            checkedTick = tick; checkedEpoch = epoch;
            return true;
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

    private record ChunkStamp(int x, int z, @Nullable WeakReference<LevelChunk> chunk, @Nullable Revision revision) {
        private boolean matches(ServerLevel level) {
            var current = level.getChunkSource().getChunkNow(x, z);
            if (chunk == null) return current == null;
            return current != null && current == chunk.get() && revision != null && revision.stable()
                    && revision.equals(ChunkRevisions.current(current));
        }
    }

    private record EndpointSide(BlockPos cable, Direction outward) {}

    private static final class Discovery {
        private final ServerLevel level;
        private final @Nullable Endpoints previous;
        private final Map<Long, ChunkStamp> stamps = new HashMap<>();
        private final Map<Long, LevelChunk> loadedChunks = new HashMap<>();
        private final Map<BlockPos, BlockState> observed = new HashMap<>();

        private Discovery(ServerLevel level, @Nullable Endpoints previous) {
            this.level = level;
            this.previous = previous;
        }

        private Component scan(BlockPos origin) {
            if (!(read(origin).getBlock() instanceof MatterPipeBlock pipe)) return result(Set.of(), null);
            int limit = pipe.tier().networkLimit();
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
                    if (state.getBlock() instanceof MatterPipeBlock cable) {
                        if (!cables.add(next)) continue;
                        limit = Math.min(limit, cable.tier().networkLimit());
                        if (cables.size() > limit) return result(cables, null);
                        queue.add(next);
                    } else if (allowsEndpoint(state, direction.getOpposite())) {
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
                    if (chunk == null) return new ChunkStamp(x, z, null, null);
                    loadedChunks.put(key, chunk);
                    return new ChunkStamp(x, z, new WeakReference<>(chunk), ChunkRevisions.current(chunk));
                });
                var chunk = loadedChunks.get(key);
                return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(position);
            });
        }
    }
}
