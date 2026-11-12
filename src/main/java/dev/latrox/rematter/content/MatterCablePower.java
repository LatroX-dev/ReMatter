package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.preparation.PreparationTier;
import dev.latrox.rematter.primal.EntangledLinkBlockEntity;
import dev.latrox.rematter.primal.EntangledLinks;
import java.util.Comparator;
import java.util.PriorityQueue;
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

    public static @Nullable EnergyHandler capability(Level level, BlockPos pos, @Nullable Direction side) {
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
            return (machine.kind().processes()||machine.kind().generator()) && !PreparationTier.manual(machine.kind())
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
            if(chunk==null)return 0;
            var state=chunk.getBlockState(origin);
            if(state.getBlock() instanceof MatterCableBlock cable&&CableOwners.owner(level,origin)!=null)return cable.tier().energyRate();
            return chunk.getBlockEntity(origin) instanceof EntangledLinkBlockEntity link&&link.owner()!=null?MatterCableBlock.Tier.BASIC.energyRate():0;
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
                if (incoming.getBlock() instanceof MatterCableBlock || incomingChunk.getBlockEntity(adjacent) instanceof EntangledLinkBlockEntity
                        || !incoming.isAir() && !allowsEndpoint(incoming, side.getOpposite())) return 0;

                var component = cache.component(level, origin);
                if (component.endpoints == null) return 0;
                UUID cableOwner=component.owner;
                if(cableOwner==null)return 0;
                var routes=component.routes(origin);
                var candidates = new ArrayList<Candidate<Endpoint>>();
                for (var endpoint : component.endpoints.active()) {
                    if (endpoint.position.equals(adjacent)||!cableOwner.equals(owner(level,endpoint.position)))continue;
                    int distance=endpoint.faces.stream().mapToInt(face->routes.distance(face.cable)).min().orElse(Integer.MAX_VALUE);
                    candidates.add(new Candidate<>(endpoint,cableOwner,distance,endpoint.position));
                }
                var endpoints = recipients(candidates);
                if (endpoints.isEmpty()) return 0;
                int remaining=amount,count=endpoints.size();
                int start=Math.floorMod(level.getGameTime()+origin.asLong(),count);
                for(int i=0;i<count&&remaining>0;i++){
                    var endpoint=endpoints.get((start+i)%count);
                    // A device may expose several faces. Prefer its widest actual route, not scan order.
                    var faces=endpoint.faces.stream().sorted(Comparator.<Face>comparingInt(face->routes.rate(face.cable)).reversed()
                            .thenComparingInt(face->routes.distance(face.cable)).thenComparing(Face::cable)).toList();
                    for(var face:faces){
                        var path=routes.path(face.cable);int rate=routes.rate(face.cable);
                        if(path.isEmpty()||rate==0)continue;
                        int offered=Math.min(remaining,rate);
                        for(var node:path)offered=Math.min(offered,cache.transportBudget(level.getGameTime(),node).remaining(level.getGameTime(),component.rates.get(node)));
                        if(offered==0)continue;
                        var handler=face.capability.cache.getCapability();
                        if(handler==null||handler instanceof Connection)continue;
                        int available=offered;
                        int moved=NativeCableEnergy.transfer(rate,()->handler.insert(available,transaction));
                        if(moved<0||moved>offered)throw new IllegalStateException("Invalid native FE transfer result");
                        if(moved==0)continue;
                        // Every real cable/link node shares one rollback-aware allowance across origins, faces,
                        // graph rescans and both directions. A Link pair therefore cannot add two budgets.
                        for(var node:path)cache.transportBudget(level.getGameTime(),node).spend(level.getGameTime(),moved,component.rates.get(node),transaction);
                        remaining-=moved;
                        if(flow.isEmpty()&&MatterCableActivity.canTrackEnergy(level)){
                            updateSnapshots(transaction);flow=Optional.of(new Flow(component,face.cable));
                        }
                        break;
                    }
                }
                return amount-remaining;
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
        private long budgetTick=Long.MIN_VALUE;
        private final Map<BlockPos,TransportBudget> transportBudgets=new HashMap<>();
        TransportBudget transportBudget(long tick,BlockPos node){
            if(budgetTick!=tick){budgetTick=tick;transportBudgets.clear();}
            return transportBudgets.computeIfAbsent(node,ignored->new TransportBudget());
        }
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

    /** One physical transport node, shared even when a re-pair changes the cached graph in this tick. */
    private static final class TransportBudget extends SnapshotJournal<TransportBudget.State>{
        private record State(long tick,int spent){}
        private State state=new State(Long.MIN_VALUE,0);
        int remaining(long tick,int rate){return Math.max(0,rate-(state.tick==tick?state.spent:0));}
        void spend(long tick,int amount,int rate,TransactionContext transaction){
            if(amount<0||amount>remaining(tick,rate))throw new IllegalStateException("Native path allowance exceeded");
            updateSnapshots(transaction);state=new State(tick,(state.tick==tick?state.spent:0)+amount);
        }
        @Override protected State createSnapshot(){return state;}
        @Override protected void revertToSnapshot(State snapshot){state=snapshot;}
    }

    /** Safe public peer observations: no lookup here may request a chunk. */
    private record LinkState(EntangledLinkBlockEntity entity,UUID owner,Optional<BlockPos> peer){
        boolean current(ServerLevel level,BlockPos pos){
            var link=EntangledLinks.loaded(level,pos).orElse(null);
            return link==entity&&owner.equals(link.owner())&&peer.equals(EntangledLinks.peer(level,pos,owner));
        }
    }

    /** One loaded cable/link graph. Adjacency includes the reciprocal virtual edge exactly once per direction. */
    private static final class Component {
        private final Set<BlockPos> cables;
        private final @Nullable UUID owner;
        private final @Nullable Endpoints endpoints;
        private final List<ChunkRevisions.Stamp> stamps;
        private final Map<BlockPos,List<BlockPos>> neighbors;
        private final Map<BlockPos,Integer> rates;
        private final Map<BlockPos,LinkState> links;
        private final Map<BlockPos,Routes> routes=new LinkedHashMap<>(16,0.75F,true){
            @Override protected boolean removeEldestEntry(Map.Entry<BlockPos,Routes> eldest){return size()>MAX_CACHED_ORIGINS;}
        };
        private long checkedTick=Long.MIN_VALUE,checkedEpoch;
        private Component(Set<BlockPos> cables,@Nullable UUID owner,@Nullable Endpoints endpoints,List<ChunkRevisions.Stamp> stamps,
                          Map<BlockPos,List<BlockPos>> neighbors,Map<BlockPos,Integer> rates,Map<BlockPos,LinkState> links){
            this.cables=cables;this.owner=owner;this.endpoints=endpoints;this.stamps=stamps;
            this.neighbors=neighbors;this.rates=rates;this.links=links;
        }
        private boolean current(ServerLevel level){
            // Also catches a peer load/unload and in-place BE data replacement, independent of local block state.
            if(!links.entrySet().stream().allMatch(entry->entry.getValue().current(level,entry.getKey())))return false;
            long tick=level.getGameTime(),epoch=ChunkRevisions.epoch();
            if(checkedTick==tick&&checkedEpoch==epoch)return true;
            if(!stamps.stream().allMatch(stamp->stamp.matches(level)))return false;
            checkedTick=tick;checkedEpoch=epoch;return true;
        }
        private Routes routes(BlockPos origin){return routes.computeIfAbsent(origin,start->new Routes(start,rates,neighbors));}
        private List<BlockPos> path(BlockPos source,BlockPos target){return routes(source).path(target);}
    }

    /** Widest path, then shortest path: its rate includes both the origin and every traversed Link/cable. */
    private static final class Routes {
        private record Visit(BlockPos node,int rate,int distance){}
        private final BlockPos origin;
        private final Map<BlockPos,Integer> rates=new HashMap<>(),distances=new HashMap<>();
        private final Map<BlockPos,BlockPos> previous=new HashMap<>();
        private Routes(BlockPos origin,Map<BlockPos,Integer> limits,Map<BlockPos,List<BlockPos>> neighbors){
            this.origin=origin;var queue=new PriorityQueue<Visit>(Comparator.comparingInt(Visit::rate).reversed()
                    .thenComparingInt(Visit::distance).thenComparing(Visit::node));
            int initial=limits.getOrDefault(origin,0);if(initial==0)return;
            rates.put(origin,initial);distances.put(origin,0);previous.put(origin,origin);queue.add(new Visit(origin,initial,0));
            while(!queue.isEmpty()){
                var current=queue.remove();
                if(current.rate!=rates.get(current.node)||current.distance!=distances.get(current.node))continue;
                for(var next:neighbors.getOrDefault(current.node,List.of())){
                    int rate=Math.min(current.rate,limits.getOrDefault(next,0)),distance=current.distance+1;
                    if(rate>rates.getOrDefault(next,0)||rate==rates.getOrDefault(next,0)&&distance<distances.getOrDefault(next,Integer.MAX_VALUE)){
                        rates.put(next,rate);distances.put(next,distance);previous.put(next,current.node);queue.add(new Visit(next,rate,distance));
                    }
                }
            }
        }
        int rate(BlockPos target){return rates.getOrDefault(target,0);}
        int distance(BlockPos target){return distances.getOrDefault(target,Integer.MAX_VALUE);}
        List<BlockPos> path(BlockPos target){
            if(!previous.containsKey(target))return List.of();var result=new ArrayList<BlockPos>();
            for(var node=target;!node.equals(origin);node=previous.get(node))result.add(node);
            result.add(origin);Collections.reverse(result);return result;
        }
    }

    private record EndpointSide(BlockPos cable, Direction outward) {}

    private static final class Discovery {
        private final ServerLevel level;
        private final @Nullable Endpoints previous;
        private final Map<Long,ChunkRevisions.Stamp> stamps=new HashMap<>();
        private final Map<Long,LevelChunk> loadedChunks=new HashMap<>();
        private final Map<BlockPos,BlockState> observed=new HashMap<>();
        private final Map<BlockPos,List<BlockPos>> neighbors=new HashMap<>();
        private final Map<BlockPos,Integer> rates=new HashMap<>();
        private final Map<BlockPos,LinkState> links=new HashMap<>();
        private @Nullable UUID cableOwner;
        private Discovery(ServerLevel level,@Nullable Endpoints previous){this.level=level;this.previous=previous;}
        private Component scan(BlockPos origin){
            var initial=read(origin);
            if(initial.getBlock() instanceof MatterCableBlock)cableOwner=CableOwners.owner(level,origin);
            else cableOwner=EntangledLinks.loaded(level,origin).map(EntangledLinkBlockEntity::owner).orElse(null);
            if(cableOwner==null||!transport(origin))return result(Set.of(),null);
            var cables=new LinkedHashSet<BlockPos>();var ends=new LinkedHashMap<BlockPos,List<EndpointSide>>();
            var queue=new ArrayDeque<BlockPos>();cables.add(origin);queue.add(origin);
            while(!queue.isEmpty()){
                var position=queue.removeFirst();
                for(var direction:DIRECTIONS){
                    var next=position.relative(direction);var state=read(next);
                    if(transport(next)){
                        edge(position,next);
                        if(cables.add(next)){if(cables.size()>MachineMatterNetwork.SCAN_BUDGET)return result(cables,null);queue.add(next);}
                    }else if((state.getBlock() instanceof MachineBlock||state.getBlock() instanceof MachinePortBlock)
                            &&allowsEndpoint(state,direction.getOpposite())){
                        ends.computeIfAbsent(next,ignored->new ArrayList<>()).add(new EndpointSide(position,direction.getOpposite()));
                    }
                }
                var link=links.get(position);
                if(link!=null&&link.peer.isPresent()){
                    var peer=link.peer.get();
                    if(transport(peer)){
                        edge(position,peer);
                        if(cables.add(peer)){if(cables.size()>MachineMatterNetwork.SCAN_BUDGET)return result(cables,null);queue.add(peer);}
                    }
                }
            }
            var endpoints=new Endpoints();ends.forEach((position,sides)->endpoints.add(level,position,sides,previous));
            return result(cables,endpoints);
        }
        private void edge(BlockPos first,BlockPos second){
            var list=neighbors.computeIfAbsent(first,ignored->new ArrayList<>());if(!list.contains(second))list.add(second);
            var reverse=neighbors.computeIfAbsent(second,ignored->new ArrayList<>());if(!reverse.contains(first))reverse.add(first);
        }
        private boolean transport(BlockPos pos){
            var state=read(pos);
            if(state.getBlock() instanceof MatterCableBlock cable){
                if(!cableOwner.equals(CableOwners.owner(level,pos)))return false;
                rates.put(pos,cable.tier().energyRate());return true;
            }
            var link=EntangledLinks.loaded(level,pos).orElse(null);
            if(link==null||!cableOwner.equals(link.owner()))return false;
            rates.put(pos,MatterCableBlock.Tier.BASIC.energyRate());
            links.put(pos,new LinkState(link,cableOwner,EntangledLinks.peer(level,pos,cableOwner)));return true;
        }
        private Component result(Set<BlockPos> cables,@Nullable Endpoints endpoints){
            var adjacency=new HashMap<BlockPos,List<BlockPos>>();neighbors.forEach((pos,list)->adjacency.put(pos,List.copyOf(list)));
            var component=new Component(Set.copyOf(cables),cableOwner,endpoints,List.copyOf(stamps.values()),Map.copyOf(adjacency),Map.copyOf(rates),Map.copyOf(links));
            return component.current(level)?component:new Component(Set.of(),null,null,List.of(),Map.of(),Map.of(),Map.of());
        }
        private BlockState read(BlockPos pos){
            return observed.computeIfAbsent(pos,position->{
                int x=position.getX()>>4,z=position.getZ()>>4;long key=(long)x<<32|(z&0xffffffffL);
                stamps.computeIfAbsent(key,ignored->{var chunk=level.getChunkSource().getChunkNow(x,z);if(chunk!=null)loadedChunks.put(key,chunk);return ChunkRevisions.Stamp.of(x,z,chunk);});
                var chunk=loadedChunks.get(key);return chunk==null?Blocks.AIR.defaultBlockState():chunk.getBlockState(position);
            });
        }
    }
}
