package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.preparation.PreparationTier;
import dev.latrox.rematter.energy.EnergyCubeBlock;
import dev.latrox.rematter.energy.EnergyCubeBlockEntity;
import dev.latrox.rematter.primal.DimensionalEntangledLinkBlock;
import dev.latrox.rematter.primal.DimensionalEntangledLinkBlockEntity;
import dev.latrox.rematter.primal.EntangledLinkBlock;
import dev.latrox.rematter.primal.EntangledLinkBlockEntity;
import dev.latrox.rematter.primal.EntangledLinks;
import dev.latrox.rematter.primal.EntangledLinks.DimPos;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
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
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Loaded cable components forward pushed FE in the caller's transaction across single or cross-dimension
 * links, without storing energy. Both cable traversal and recipients are restricted by cable security/ownership.
 */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class MatterCablePower {
    private static final int MAX_CACHED_CABLES = 2 * MachineMatterNetwork.SCAN_BUDGET;
    private static final int MAX_CACHED_ORIGINS = 64;
    private static final UUID UNOWNED_PUBLIC_GROUP = new UUID(0L, 0L);
    public static final int POWERED_HOLD_TICKS = 40;
    private static final int ENERGIZE_REFRESH_INTERVAL = 20;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Map<MinecraftServer, NetworkCache> CACHES = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Map<DimPos, Long>> POWERED_CABLES = new IdentityHashMap<>();

    private MatterCablePower() {}

    public static @Nullable EnergyHandler capability(Level level, BlockPos pos, @Nullable Direction side) {
        if (!(level instanceof ServerLevel server) || side == null) return null;
        return new Connection(server, pos.immutable(), side);
    }

    /** Marks every Matter Cable in a connected component as energized (bright traces) while a live FE source feeds it. */
    public static void energizeIfCable(@Nullable EnergyHandler handler) {
        if (handler instanceof Connection conn) {
            conn.energizeComponent();
        }
    }

    private static Map<DimPos, Long> poweredMap(MinecraftServer server) {
        synchronized (POWERED_CABLES) {
            return POWERED_CABLES.computeIfAbsent(server, ignored -> new HashMap<>());
        }
    }

    private static void markCablesPowered(MinecraftServer server, Iterable<DimPos> nodes) {
        var map = poweredMap(server);
        for (var node : nodes) {
            ServerLevel segLevel = node.resolveLevel(server);
            if (segLevel == null) continue;
            var chunk = loaded(segLevel, node.pos());
            if (chunk == null) continue;
            BlockState state = chunk.getBlockState(node.pos());
            if (!(state.getBlock() instanceof MatterCableBlock)) continue;
            long now = segLevel.getGameTime();
            synchronized (map) {
                map.put(node, now);
            }
            if (!state.getValue(MatterCableBlock.POWERED)) {
                segLevel.setBlock(node.pos(), state.setValue(MatterCableBlock.POWERED, true),
                        net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE);
                segLevel.scheduleTick(node.pos(), state.getBlock(), POWERED_HOLD_TICKS + 2);
            }
        }
    }

    static boolean onCableTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.getValue(MatterCableBlock.POWERED)) return false;
        var map = poweredMap(level.getServer());
        DimPos key = new DimPos(level.dimension(), pos);
        long now = level.getGameTime();
        Long last;
        synchronized (map) {
            last = map.get(key);
        }
        if (last != null && now - last < POWERED_HOLD_TICKS) {
            int remaining = (int) Math.max(1L, POWERED_HOLD_TICKS - (now - last)) + 2;
            level.scheduleTick(pos, state.getBlock(), remaining);
            return true;
        }
        synchronized (map) {
            map.remove(key);
        }
        level.setBlock(pos, state.setValue(MatterCableBlock.POWERED, false),
                net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE);
        return true;
    }

    static void clearPowered(ServerLevel level, BlockPos pos) {
        var server = level.getServer();
        synchronized (POWERED_CABLES) {
            var map = POWERED_CABLES.get(server);
            if (map != null) {
                synchronized (map) {
                    map.remove(new DimPos(level.dimension(), pos));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel server) {
            synchronized (CACHES) {
                var cache = CACHES.get(server.getServer());
                if (cache != null) cache.clear();
            }
            synchronized (POWERED_CABLES) {
                var map = POWERED_CABLES.get(server.getServer());
                if (map != null) {
                    synchronized (map) {
                        var dim = server.dimension();
                        map.keySet().removeIf(k -> k.dimension().equals(dim));
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        synchronized (CACHES) {
            CACHES.remove(event.getServer());
        }
        synchronized (POWERED_CABLES) {
            POWERED_CABLES.remove(event.getServer());
        }
    }

    private static NetworkCache cache(ServerLevel level) {
        synchronized (CACHES) {
            return CACHES.computeIfAbsent(level.getServer(), ignored -> new NetworkCache());
        }
    }

    /** Applies the cable's physical side rules before consulting the endpoint's FE capability. Any other block may push FE in. */
    static boolean allowsEndpoint(BlockState state, Direction side) {
        if (state.getBlock() instanceof MachineBlock machine) {
            return (machine.kind().processes() || machine.kind().generator()) && !PreparationTier.manual(machine.kind())
                    && MatterCableConnectionPolicy.acceptsMachine(machine.kind(), state.getValue(MachineBlock.FACING), side);
        }
        if (state.getBlock() instanceof MachinePortBlock) {
            return MatterCableConnectionPolicy.acceptsPort(state.getValue(MachinePortBlock.FORMED),
                    state.getValue(MachinePortBlock.FACING), side.getOpposite());
        }
        if (state.getBlock() instanceof WirelessAccessPointBlock) {
            return WirelessAccessPointBlock.connectsSide(state, side);
        }
        if (state.getBlock() instanceof SpatialAnchorBlock) {
            return SpatialAnchorBlock.connectsSide(state, side);
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
        if (entity instanceof EnergyCubeBlockEntity cube) return cube.owner();
        if (entity instanceof WirelessAccessPointBlockEntity ap) return ap.owner();
        if (entity instanceof SpatialAnchorBlockEntity anchor) return anchor.owner();
        if (entity instanceof MachinePortBlockEntity port) {
            var controller = port.controller(null);
            return controller == null ? null : controller.networkOwner();
        }
        return null;
    }

    private static SecurityMode endpointMode(ServerLevel level, BlockPos position) {
        var entity = level.getBlockEntity(position);
        if (entity instanceof MachineBlockEntity machine) return machine.securityMode();
        if (entity instanceof EnergyCubeBlockEntity cube) return cube.securityMode();
        if (entity instanceof WirelessAccessPointBlockEntity ap) return ap.securityMode();
        if (entity instanceof SpatialAnchorBlockEntity anchor) return anchor.securityMode();
        if (entity instanceof MachinePortBlockEntity port) {
            var controller = port.controller(null);
            return controller == null ? SecurityMode.PRIVATE : controller.securityMode();
        }
        return SecurityMode.PUBLIC;
    }

    record Candidate<T>(T endpoint, @Nullable UUID owner, int distance, BlockPos position) {}

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
        private final DimPos originKey;
        private final Direction side;
        private Optional<Flow> flow = Optional.empty();

        private Connection(ServerLevel level, BlockPos origin, Direction side) {
            this.level = level;
            this.origin = origin;
            this.originKey = new DimPos(level.dimension(), origin);
            this.side = side;
        }

        @Override public long getAmountAsLong() { return 0; }

        @Override public long getCapacityAsLong() {
            var chunk = loaded(level, origin);
            if (chunk == null) return 0;
            var state = chunk.getBlockState(origin);
            if (state.getBlock() instanceof MatterCableBlock cable
                    && (CableOwners.owner(level, origin) != null || CableOwners.mode(level, origin) == SecurityMode.PUBLIC)) {
                return cable.tier().energyRate();
            }
            var be = chunk.getBlockEntity(origin);
            if (be instanceof EntangledLinkBlockEntity link
                    && (link.owner() != null || link.securityMode() == SecurityMode.PUBLIC)) {
                return MatterCableBlock.Tier.BASIC.energyRate();
            }
            if (be instanceof DimensionalEntangledLinkBlockEntity dlink
                    && (dlink.owner() != null || dlink.securityMode() == SecurityMode.PUBLIC)) {
                return MatterCableBlock.Tier.RESONANT.energyRate();
            }
            return 0;
        }

        @Override public int extract(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            return 0;
        }

        @Override public int insert(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            if (amount == 0 || !level.getServer().isSameThread()) return 0;
            var cache = cache(level);
            if (cache.routing) return 0;
            cache.routing = true;
            try {
                if (level.getBlockState(origin).getBlock() instanceof MatterCableBlock && CableOwners.isSideBlocked(level, origin, side)) return 0;
                var adjacent = origin.relative(side);
                var incomingChunk = loaded(level, adjacent);
                if (incomingChunk == null) return 0;
                var incoming = incomingChunk.getBlockState(adjacent);
                var incomingEntity = incomingChunk.getBlockEntity(adjacent);
                if (incoming.getBlock() instanceof MatterCableBlock || incoming.getBlock() instanceof MatterCableRelayBlock
                        || incomingEntity instanceof EntangledLinkBlockEntity
                        || incomingEntity instanceof DimensionalEntangledLinkBlockEntity
                        || !incoming.isAir() && !allowsEndpoint(incoming, side.getOpposite())) return 0;

                var component = cache.component(level, originKey);
                if (component.endpoints == null) return 0;
                @Nullable UUID cableOwner = component.owner;
                if (cableOwner == null && component.mode != SecurityMode.PUBLIC) return 0;
                if (incomingEntity instanceof EnergyCubeBlockEntity cube
                        && !MachineAccess.canConnectCableToDevice(level, cableOwner, component.mode, cube.owner(), cube.securityMode())) return 0;
                DimPos adjacentKey = new DimPos(level.dimension(), adjacent);
                var routes = component.routes(originKey);
                UUID routingGroup = cableOwner != null ? cableOwner : UNOWNED_PUBLIC_GROUP;
                var candidates = new ArrayList<Candidate<Endpoint>>();
                for (var endpoint : component.endpoints.active()) {
                    if (endpoint.key.equals(adjacentKey)
                            || !MachineAccess.canConnectCableToDevice(endpoint.level, cableOwner, component.mode,
                                    owner(endpoint.level, endpoint.key.pos()), endpointMode(endpoint.level, endpoint.key.pos()))) continue;
                    int distance = endpoint.faces.stream().mapToInt(face -> routes.distance(face.cable)).min().orElse(Integer.MAX_VALUE);
                    if (distance == Integer.MAX_VALUE) continue;
                    candidates.add(new Candidate<>(endpoint, routingGroup, distance, endpoint.key.pos()));
                }
                var endpoints = recipients(candidates);
                if (endpoints.isEmpty()) return 0;
                int remaining = amount, count = endpoints.size();
                int start = Math.floorMod(level.getGameTime() + origin.asLong(), count);
                for (int i = 0; i < count && remaining > 0; i++) {
                    var endpoint = endpoints.get((start + i) % count);
                    var faces = endpoint.faces.stream().sorted(Comparator.<Face>comparingInt(face -> routes.rate(face.cable)).reversed()
                            .thenComparingInt(face -> routes.distance(face.cable)).thenComparing(Face::cable)).toList();
                    for (var face : faces) {
                        var path = routes.path(face.cable);
                        int rate = routes.rate(face.cable);
                        if (path.isEmpty() || rate == 0) continue;
                        int offered = Math.min(remaining, rate);
                        for (var node : path) {
                            offered = Math.min(offered, cache.transportBudget(level.getGameTime(), node)
                                    .remaining(level.getGameTime(), component.rates.get(node)));
                        }
                        if (offered == 0) continue;
                        var handler = face.capability.cache.getCapability();
                        if (handler == null || handler instanceof Connection) continue;
                        int available = offered;
                        int moved = NativeCableEnergy.transfer(rate, () -> handler.insert(available, transaction));
                        if (moved < 0 || moved > offered) throw new IllegalStateException("Invalid native FE transfer result");
                        if (moved == 0) continue;
                        for (var node : path) {
                            cache.transportBudget(level.getGameTime(), node)
                                    .spend(level.getGameTime(), moved, component.rates.get(node), transaction);
                        }
                        remaining -= moved;
                        if (flow.isEmpty()) {
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

        private void energizeComponent() {
            if (!level.getServer().isSameThread()) return;
            if (level.getBlockState(origin).getBlock() instanceof MatterCableBlock && CableOwners.isSideBlocked(level, origin, side)) return;
            var cache = cache(level);
            if (cache.routing) return;
            var component = cache.component(level, originKey);
            if ((component.owner == null && component.mode != SecurityMode.PUBLIC) || component.cables.isEmpty()) return;
            long now = level.getGameTime();
            if (!component.hasOneWayRelays) {
                if (component.lastEnergizeTick != Long.MIN_VALUE && now - component.lastEnergizeTick < ENERGIZE_REFRESH_INTERVAL) return;
                component.lastEnergizeTick = now;
                markCablesPowered(level.getServer(), component.cables);
            } else {
                Long prev = component.lastEnergizeByOrigin.get(originKey);
                if (prev != null && now - prev < ENERGIZE_REFRESH_INTERVAL) return;
                component.lastEnergizeByOrigin.put(originKey, now);
                markCablesPowered(level.getServer(), component.routes(originKey).reachableNodes());
            }
        }

        @Override protected Optional<Flow> createSnapshot() { return flow; }
        @Override protected void revertToSnapshot(Optional<Flow> snapshot) { flow = snapshot; }

        @Override protected void onRootCommit(Optional<Flow> originalState) {
            var committed = flow;
            flow = Optional.empty();
            if (committed.isEmpty()) return;
            var activity = committed.get();
            if (!activity.component.current(level)) return;
            var path = activity.component.path(originKey, activity.otherCable);
            if (path.isEmpty()) return;
            markCablesPowered(level.getServer(), path);
            if (!MatterCableActivity.canTrackEnergy(level)) return;
            var segment = new ArrayList<BlockPos>();
            ResourceKey<Level> segDim = null;
            for (var node : path) {
                if (segDim == null || segDim.equals(node.dimension())) {
                    segDim = node.dimension();
                    segment.add(node.pos());
                } else {
                    ServerLevel segLevel = level.getServer().getLevel(segDim);
                    if (segLevel != null && !segment.isEmpty()) MatterCableActivity.energy(segLevel, List.copyOf(segment));
                    segment.clear();
                    segDim = node.dimension();
                    segment.add(node.pos());
                }
            }
            if (segDim != null && !segment.isEmpty()) {
                ServerLevel segLevel = level.getServer().getLevel(segDim);
                if (segLevel != null) MatterCableActivity.energy(segLevel, List.copyOf(segment));
            }
        }
    }

    private record Flow(Component component, DimPos otherCable) {}
    private record Endpoint(ServerLevel level, DimPos key, List<Face> faces) {}
    private record Face(DimPos cable, FaceCapability capability) {}

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
        private final Map<DimPos, Endpoint> byPosition = new HashMap<>();
        private @Nullable List<Endpoint> active;

        private void add(ServerLevel level, DimPos key, List<EndpointSide> sides, @Nullable Endpoints previous) {
            List<Face> faces = sides.stream().map(side -> {
                var capability = previous == null ? null : previous.capability(key, side.cable);
                if (capability == null) capability = new FaceCapability(level, key.pos(), side.outward);
                capability.user = this;
                return new Face(side.cable, capability);
            }).toList();
            var endpoint = new Endpoint(level, key, faces);
            candidates.add(endpoint);
            byPosition.put(key, endpoint);
        }

        private @Nullable FaceCapability capability(DimPos position, DimPos cable) {
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
                if (!faces.isEmpty()) found.add(new Endpoint(candidate.level, candidate.key, faces));
            }
            active = List.copyOf(found);
            return active;
        }
    }

    /** BlockPos-keyed LRU index (kept package-private for unit tests). */
    static final class ComponentIndex<C> {
        private final int budget;
        private final Map<BlockPos, C> byCable = new HashMap<>();
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

    private static final class DimComponentIndex<C> {
        private final int budget;
        private final Map<DimPos, C> byCable = new HashMap<>();
        private final Map<C, Set<DimPos>> recent = new LinkedHashMap<>(16, 0.75F, true);
        private int cables;

        DimComponentIndex(int budget) { this.budget = budget; }

        @Nullable C get(DimPos cable) {
            var component = byCable.get(cable);
            if (component != null) recent.get(component);
            return component;
        }
        void put(C component, Set<DimPos> positions) {
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
        void clear() {
            byCable.clear();
            recent.clear();
            cables = 0;
        }
    }

    private static final class NetworkCache {
        private long budgetTick = Long.MIN_VALUE;
        private final Map<DimPos, TransportBudget> transportBudgets = new HashMap<>();
        private final DimComponentIndex<Component> indexed = new DimComponentIndex<>(MAX_CACHED_CABLES);
        private boolean routing;

        TransportBudget transportBudget(long tick, DimPos node) {
            if (budgetTick != tick) { budgetTick = tick; transportBudgets.clear(); }
            return transportBudgets.computeIfAbsent(node, ignored -> new TransportBudget());
        }

        private Component component(ServerLevel originLevel, DimPos origin) {
            var cached = indexed.get(origin);
            if (cached != null && cached.current(originLevel)) return cached;
            if (cached != null) indexed.remove(cached);
            var component = new Discovery(originLevel.getServer(), cached == null ? null : cached.endpoints).scan(origin);
            if (!component.cables.isEmpty()) indexed.put(component, component.cables);
            return component;
        }

        void clear() {
            indexed.clear();
            transportBudgets.clear();
            budgetTick = Long.MIN_VALUE;
        }
    }

    private static final class TransportBudget extends SnapshotJournal<TransportBudget.State> {
        private record State(long tick, int spent) {}
        private State state = new State(Long.MIN_VALUE, 0);
        int remaining(long tick, int rate) { return Math.max(0, rate - (state.tick == tick ? state.spent : 0)); }
        void spend(long tick, int amount, int rate, TransactionContext transaction) {
            if (amount < 0 || amount > remaining(tick, rate)) throw new IllegalStateException("Native path allowance exceeded");
            updateSnapshots(transaction);
            state = new State(tick, (state.tick == tick ? state.spent : 0) + amount);
        }
        @Override protected State createSnapshot() { return state; }
        @Override protected void revertToSnapshot(State snapshot) { state = snapshot; }
    }

    private record ChunkStamp(ServerLevel level, ChunkRevisions.Stamp stamp) {
        boolean matches() { return stamp.matches(level); }
    }

    private record LinkState(ServerLevel level, BlockPos pos, EntangledLinkBlockEntity entity, @Nullable UUID owner, SecurityMode mode,
                             int portModes, boolean redstoneAllowed, Optional<BlockPos> peer) {
        boolean current() {
            var link = EntangledLinks.loaded(level, pos).orElse(null);
            return link == entity && link.packedPortModes() == portModes
                    && link.redstoneAllows() == redstoneAllowed
                    && MachineAccess.canLinkCable(level, owner, mode, link.owner(), link.securityMode())
                    && peer.equals(EntangledLinks.peer(level, pos, owner, mode));
        }
    }

    private record DimLinkState(ServerLevel level, BlockPos pos, DimensionalEntangledLinkBlockEntity entity, @Nullable UUID owner,
                                SecurityMode mode, long revision, Optional<DimPos> peer) {
        boolean current() {
            var link = EntangledLinks.loadedDimensional(level, pos).orElse(null);
            return link == entity && link.stateRevision() == revision
                    && MachineAccess.canLinkCable(level, owner, mode, link.owner(), link.securityMode())
                    && peer.equals(EntangledLinks.dimensionalPeer(level, pos, owner, mode));
        }
    }

    private record RelayState(ServerLevel level, MatterCableRelayBlockEntity entity, @Nullable UUID owner, SecurityMode mode, long revision) {
        boolean current() {
            return !entity.isRemoved() && entity.stateRevision() == revision && entity.allowsPower()
                    && MachineAccess.canLinkCable(level, owner, mode, entity.owner(), entity.securityMode());
        }
    }

    private static final class Component {
        private final Set<DimPos> cables;
        private final @Nullable UUID owner;
        private final SecurityMode mode;
        private final @Nullable Endpoints endpoints;
        private final List<ChunkStamp> stamps;
        private final Map<DimPos, List<DimPos>> neighbors;
        private final Map<DimPos, Integer> rates;
        private final List<LinkState> links;
        private final List<DimLinkState> dimLinks;
        private final List<RelayState> relays;
        private final boolean hasOneWayRelays;
        private final Map<DimPos, Routes> routes = new LinkedHashMap<>(16, 0.75F, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<DimPos, Routes> eldest) { return size() > MAX_CACHED_ORIGINS; }
        };
        private final Map<DimPos, Long> lastEnergizeByOrigin = new LinkedHashMap<>(16, 0.75F, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<DimPos, Long> eldest) { return size() > MAX_CACHED_ORIGINS; }
        };
        private long checkedTick = Long.MIN_VALUE, checkedEpoch;
        private long lastEnergizeTick = Long.MIN_VALUE;

        private Component(Set<DimPos> cables, @Nullable UUID owner, SecurityMode mode, @Nullable Endpoints endpoints,
                          List<ChunkStamp> stamps, Map<DimPos, List<DimPos>> neighbors, Map<DimPos, Integer> rates,
                          List<LinkState> links, List<DimLinkState> dimLinks, List<RelayState> relays, boolean hasOneWayRelays) {
            this.cables = cables;
            this.owner = owner;
            this.mode = mode;
            this.endpoints = endpoints;
            this.stamps = stamps;
            this.neighbors = neighbors;
            this.rates = rates;
            this.links = links;
            this.dimLinks = dimLinks;
            this.relays = relays;
            this.hasOneWayRelays = hasOneWayRelays;
        }

        private boolean current(ServerLevel originLevel) {
            if (!links.isEmpty()) {
                for (int i = 0, n = links.size(); i < n; i++) if (!links.get(i).current()) return false;
            }
            if (!dimLinks.isEmpty()) {
                for (int i = 0, n = dimLinks.size(); i < n; i++) if (!dimLinks.get(i).current()) return false;
            }
            if (!relays.isEmpty()) {
                for (int i = 0, n = relays.size(); i < n; i++) if (!relays.get(i).current()) return false;
            }
            long tick = originLevel.getGameTime(), epoch = ChunkRevisions.epoch();
            if (checkedTick == tick && checkedEpoch == epoch) return true;
            for (int i = 0, n = stamps.size(); i < n; i++) if (!stamps.get(i).matches()) return false;
            checkedTick = tick;
            checkedEpoch = epoch;
            return true;
        }

        private Routes routes(DimPos origin) { return routes.computeIfAbsent(origin, start -> new Routes(start, rates, neighbors)); }
        private List<DimPos> path(DimPos source, DimPos target) { return routes(source).path(target); }
    }

    private static final class Routes {
        private record Visit(DimPos node, int rate, int distance) {}
        private final DimPos origin;
        private final Map<DimPos, Integer> rates = new HashMap<>(), distances = new HashMap<>();
        private final Map<DimPos, DimPos> previous = new HashMap<>();

        private Routes(DimPos origin, Map<DimPos, Integer> limits, Map<DimPos, List<DimPos>> neighbors) {
            this.origin = origin;
            var queue = new PriorityQueue<Visit>(Comparator.comparingInt(Visit::rate).reversed()
                    .thenComparingInt(Visit::distance).thenComparing(Visit::node));
            int initial = limits.getOrDefault(origin, 0);
            if (initial == 0) return;
            rates.put(origin, initial);
            distances.put(origin, 0);
            previous.put(origin, origin);
            queue.add(new Visit(origin, initial, 0));
            while (!queue.isEmpty()) {
                var current = queue.remove();
                if (current.rate != rates.get(current.node) || current.distance != distances.get(current.node)) continue;
                for (var next : neighbors.getOrDefault(current.node, List.of())) {
                    int rate = Math.min(current.rate, limits.getOrDefault(next, 0)), distance = current.distance + 1;
                    if (rate > rates.getOrDefault(next, 0) || rate == rates.getOrDefault(next, 0) && distance < distances.getOrDefault(next, Integer.MAX_VALUE)) {
                        rates.put(next, rate);
                        distances.put(next, distance);
                        previous.put(next, current.node);
                        queue.add(new Visit(next, rate, distance));
                    }
                }
            }
        }

        int rate(DimPos target) { return rates.getOrDefault(target, 0); }
        int distance(DimPos target) { return distances.getOrDefault(target, Integer.MAX_VALUE); }
        Set<DimPos> reachableNodes() { return rates.keySet(); }
        List<DimPos> path(DimPos target) {
            if (!previous.containsKey(target)) return List.of();
            var result = new ArrayList<DimPos>();
            for (var node = target; !node.equals(origin); node = previous.get(node)) result.add(node);
            result.add(origin);
            Collections.reverse(result);
            return result;
        }
    }

    private record EndpointSide(DimPos cable, Direction outward) {}
    private record ChunkKey(ResourceKey<Level> dimension, long packed) {}

    private static final class Discovery {
        private final MinecraftServer server;
        private final @Nullable Endpoints previous;
        private final Map<ChunkKey, ChunkStamp> stamps = new HashMap<>();
        private final Map<ChunkKey, LevelChunk> loadedChunks = new HashMap<>();
        private final Map<DimPos, BlockState> observed = new HashMap<>();
        private final Map<DimPos, List<DimPos>> neighbors = new HashMap<>();
        private final Map<DimPos, Integer> rates = new HashMap<>();
        private final Map<DimPos, LinkState> links = new HashMap<>();
        private final Map<DimPos, DimLinkState> dimLinks = new HashMap<>();
        private final Map<DimPos, RelayState> relays = new HashMap<>();
        private boolean hasOneWayRelays;
        private @Nullable UUID cableOwner;
        private SecurityMode cableMode = SecurityMode.PUBLIC;

        private Discovery(MinecraftServer server, @Nullable Endpoints previous) {
            this.server = server;
            this.previous = previous;
        }

        private Component scan(DimPos origin) {
            ServerLevel originLevel = origin.resolveLevel(server);
            if (originLevel == null) return emptyComponent();
            var initial = read(origin);
            if (initial.getBlock() instanceof MatterCableBlock) {
                cableOwner = CableOwners.owner(originLevel, origin.pos());
                cableMode = CableOwners.mode(originLevel, origin.pos());
            } else {
                var initLink = EntangledLinks.loaded(originLevel, origin.pos()).orElse(null);
                if (initLink != null) {
                    cableOwner = initLink.owner();
                    cableMode = initLink.securityMode();
                } else {
                    var initDimLink = EntangledLinks.loadedDimensional(originLevel, origin.pos()).orElse(null);
                    cableOwner = initDimLink == null ? null : initDimLink.owner();
                    cableMode = initDimLink == null ? SecurityMode.PUBLIC : initDimLink.securityMode();
                }
            }
            if ((cableOwner == null && cableMode != SecurityMode.PUBLIC) || !transport(origin)) return result(originLevel, Set.of(), null);

            var cables = new LinkedHashSet<DimPos>();
            var ends = new LinkedHashMap<DimPos, List<EndpointSide>>();
            var endLevels = new HashMap<DimPos, ServerLevel>();
            var queue = new ArrayDeque<DimPos>();
            cables.add(origin);
            queue.add(origin);
            while (!queue.isEmpty()) {
                var position = queue.removeFirst();
                var curState = read(position);
                ServerLevel curLevel = position.resolveLevel(server);
                var curRelay = relays.get(position);
                var curLink = links.get(position);
                var curDimLink = dimLinks.get(position);
                boolean curIsCable = curState.getBlock() instanceof MatterCableBlock;
                int curBlocked = curIsCable && curLevel != null ? CableOwners.blockedSides(curLevel, position.pos()) : 0;
                for (var direction : DIRECTIONS) {
                    if ((curBlocked & (1 << direction.get3DDataValue())) != 0) continue;
                    if (curRelay != null && !curRelay.entity.connectsDirection(direction)) continue;
                    if (curLink != null && !curLink.entity.allowsPower(direction)) continue;
                    if (curDimLink != null && !curDimLink.entity.allowsPower(direction)) continue;
                    var next = new DimPos(position.dimension(), position.pos().relative(direction));
                    var state = read(next);
                    if (state.getBlock() instanceof MatterCableBlock && curLevel != null
                            && CableOwners.isSideBlocked(curLevel, next.pos(), direction.getOpposite())) continue;
                    if (transport(next)) {
                        var nextRelay = relays.get(next);
                        if (nextRelay != null && !nextRelay.entity.connectsDirection(direction.getOpposite())) continue;
                        var nextLink = links.get(next);
                        if (nextLink != null && !nextLink.entity.allowsPower(direction.getOpposite())) continue;
                        var nextDimLink = dimLinks.get(next);
                        if (nextDimLink != null && !nextDimLink.entity.allowsPower(direction.getOpposite())) continue;
                        boolean allowForward = (curRelay == null || !curRelay.entity.isOneWay() || direction == curRelay.entity.outputSide())
                                && (nextRelay == null || !nextRelay.entity.isOneWay() || direction.getOpposite() == nextRelay.entity.inputSide());
                        boolean allowBackward = (nextRelay == null || !nextRelay.entity.isOneWay() || direction.getOpposite() == nextRelay.entity.outputSide())
                                && (curRelay == null || !curRelay.entity.isOneWay() || direction == curRelay.entity.inputSide());
                        if (allowForward) directedEdge(position, next);
                        if (allowBackward) directedEdge(next, position);
                        if (cables.add(next)) {
                            if (cables.size() > MachineMatterNetwork.SCAN_BUDGET) return result(originLevel, cables, null);
                            queue.add(next);
                        }
                    } else if (curRelay == null
                            && (state.getBlock() instanceof MachineBlock || state.getBlock() instanceof MachinePortBlock
                                || state.getBlock() instanceof EnergyCubeBlock || state.getBlock() instanceof WirelessAccessPointBlock
                                || state.getBlock() instanceof SpatialAnchorBlock)
                            && allowsEndpoint(state, direction.getOpposite())) {
                        ServerLevel nextLevel = next.resolveLevel(server);
                        if (nextLevel != null) {
                            if (state.getBlock() instanceof SpatialAnchorBlock
                                    && nextLevel.getBlockEntity(next.pos()) instanceof SpatialAnchorBlockEntity anchor
                                    && !anchor.allowsPower(direction.getOpposite())) {
                                continue;
                            }
                            endLevels.putIfAbsent(next, nextLevel);
                            ends.computeIfAbsent(next, ignored -> new ArrayList<>()).add(new EndpointSide(position, direction.getOpposite()));
                        }
                    }
                }
                var link = links.get(position);
                if (link != null && link.peer.isPresent()) {
                    var peer = new DimPos(position.dimension(), link.peer.get());
                    if (transport(peer)) {
                        edge(position, peer);
                        if (cables.add(peer)) {
                            if (cables.size() > MachineMatterNetwork.SCAN_BUDGET) return result(originLevel, cables, null);
                            queue.add(peer);
                        }
                    }
                }
                var dlink = dimLinks.get(position);
                if (dlink != null && dlink.peer.isPresent()) {
                    var peer = dlink.peer.get();
                    if (transport(peer)) {
                        edge(position, peer);
                        if (cables.add(peer)) {
                            if (cables.size() > MachineMatterNetwork.SCAN_BUDGET) return result(originLevel, cables, null);
                            queue.add(peer);
                        }
                    }
                }
            }
            var endpoints = new Endpoints();
            ends.forEach((key, sides) -> endpoints.add(endLevels.get(key), key, sides, previous));
            return result(originLevel, cables, endpoints);
        }

        private void directedEdge(DimPos from, DimPos to) {
            var list = neighbors.computeIfAbsent(from, ignored -> new ArrayList<>());
            if (!list.contains(to)) list.add(to);
        }

        private void edge(DimPos first, DimPos second) {
            directedEdge(first, second);
            directedEdge(second, first);
        }

        private boolean transport(DimPos key) {
            ServerLevel level = key.resolveLevel(server);
            if (level == null) return false;
            BlockPos pos = key.pos();
            var state = read(key);
            if (state.getBlock() instanceof MatterCableBlock cable) {
                if (!MachineAccess.canLinkCable(level, cableOwner, cableMode, CableOwners.owner(level, pos), CableOwners.mode(level, pos))) return false;
                rates.put(key, cable.tier().energyRate());
                return true;
            }
            if (state.getBlock() instanceof MatterCableRelayBlock) {
                var chunk = loaded(level, pos);
                if (chunk == null || !(chunk.getBlockEntity(pos) instanceof MatterCableRelayBlockEntity relay)) return false;
                if (!relay.allowsPower() || !MachineAccess.canLinkCable(level, cableOwner, cableMode, relay.owner(), relay.securityMode())) return false;
                int rate = relay.effectiveEnergyRate();
                if (rate <= 0) return false;
                if (relay.isOneWay()) hasOneWayRelays = true;
                rates.put(key, rate);
                relays.put(key, new RelayState(level, relay, cableOwner, cableMode, relay.stateRevision()));
                return true;
            }
            var link = EntangledLinks.loaded(level, pos).orElse(null);
            if (link != null) {
                if (!MachineAccess.canLinkCable(level, cableOwner, cableMode, link.owner(), link.securityMode())) return false;
                rates.put(key, MatterCableBlock.Tier.BASIC.energyRate());
                links.put(key, new LinkState(level, pos, link, cableOwner, cableMode, link.packedPortModes(),
                        link.redstoneAllows(), EntangledLinks.peer(level, pos, cableOwner, cableMode)));
                return true;
            }
            var dlink = EntangledLinks.loadedDimensional(level, pos).orElse(null);
            if (dlink != null) {
                if (!MachineAccess.canLinkCable(level, cableOwner, cableMode, dlink.owner(), dlink.securityMode())) return false;
                rates.put(key, MatterCableBlock.Tier.RESONANT.energyRate());
                dimLinks.put(key, new DimLinkState(level, pos, dlink, cableOwner, cableMode, dlink.stateRevision(),
                        EntangledLinks.dimensionalPeer(level, pos, cableOwner, cableMode)));
                return true;
            }
            return false;
        }

        private Component emptyComponent() {
            return new Component(Set.of(), null, SecurityMode.PUBLIC, null, List.of(), Map.of(), Map.of(), List.of(), List.of(), List.of(), false);
        }

        private Component result(ServerLevel originLevel, Set<DimPos> cables, @Nullable Endpoints endpoints) {
            var adjacency = new HashMap<DimPos, List<DimPos>>();
            neighbors.forEach((pos, list) -> adjacency.put(pos, List.copyOf(list)));
            var component = new Component(Set.copyOf(cables), cableOwner, cableMode, endpoints, List.copyOf(stamps.values()),
                    Map.copyOf(adjacency), Map.copyOf(rates), List.copyOf(links.values()),
                    List.copyOf(dimLinks.values()), List.copyOf(relays.values()), hasOneWayRelays);
            return component.current(originLevel) ? component : emptyComponent();
        }

        private BlockState read(DimPos key) {
            return observed.computeIfAbsent(key, dimPos -> {
                ServerLevel level = dimPos.resolveLevel(server);
                if (level == null) return Blocks.AIR.defaultBlockState();
                BlockPos position = dimPos.pos();
                int x = position.getX() >> 4, z = position.getZ() >> 4;
                ChunkKey ck = new ChunkKey(dimPos.dimension(), (long) x << 32 | (z & 0xffffffffL));
                stamps.computeIfAbsent(ck, ignored -> {
                    var chunk = level.getChunkSource().getChunkNow(x, z);
                    if (chunk != null) loadedChunks.put(ck, chunk);
                    return new ChunkStamp(level, ChunkRevisions.Stamp.of(x, z, chunk));
                });
                var chunk = loadedChunks.get(ck);
                return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(position);
            });
        }
    }
}
