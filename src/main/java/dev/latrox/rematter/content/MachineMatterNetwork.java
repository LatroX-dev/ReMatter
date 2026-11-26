package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.MatterInventory;
import dev.latrox.rematter.chemistry.MatterNetworkInventory;
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
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * A bounded observation of real, loaded owners across one or more linked dimensions, shared by every machine in it.
 * The aggregate owns no resources; every unit remains in exactly one installed cell.
 */
final class MachineMatterNetwork {
    static final int MAX_NODES = MatterCableBlock.Tier.RESONANT.networkLimit();
    /** Blocks one exploration may read, so ownerless cable branches only raise the cost of a scan. */
    static final int SCAN_BUDGET = 8 * MAX_NODES;
    static final int MAX_STORAGES = 64;
    static final int FITS = -1;
    static final int TOO_LARGE = MatterCableBlock.Tier.values().length;
    private static final int MAX_ACTIVITY_SEGMENTS = 4;
    private static final int MAX_ACTIVITY_SEGMENT_LENGTH = 64;
    private static final int WITHDRAWAL = 1;
    private static final int DEPOSIT = 2;
    private static final UUID UNOWNED_PLANNING_KEY = new UUID(0L, 0L);
    private final @Nullable UUID owner;
    private final SecurityMode originMode;
    private final List<Node> nodes;
    private final List<Node> endpoints;
    private final List<Node> associated;
    private final Set<BlockEntity> members;
    private final List<MachineBlockEntity> machines;
    private final List<MachineBlockEntity> workers;
    private final List<MachineBlockEntity> interfaces;
    private final Set<Edge> edges;
    private final Set<DimPos> cablePositions;
    private final List<ChunkStamp> chunks;
    private final String status;
    private final int storageCount;
    private final int cellCount;
    private final boolean hasThrottlingRelays;
    private final List<MachineBlockEntity> stores;
    private final @Nullable MatterNetworkInventory inventory;
    private final @Nullable Set<MachineBlockEntity> perspectiveMachines;
    private final Map<MachineBlockEntity, RelayMemberPolicy> storePolicies;
    private long checkedTick = Long.MIN_VALUE, checkedEpoch, rereadTick = Long.MIN_VALUE;

    record ChunkStamp(ServerLevel level, ChunkRevisions.Stamp stamp) {
        boolean matches() { return stamp.matches(level); }
    }

    private record ZoneEdge(int target, MatterCableRelayBlockEntity.FilterRule rule) {}

    /** Directed relay-zone graph supporting O(V+E) reachability per substance key without exponential path enumeration. */
    private static final class ZoneGraph {
        private final Map<Integer, List<ZoneEdge>> adj;
        private final int zoneCount;

        private ZoneGraph(Map<Integer, List<ZoneEdge>> adj, int zoneCount) {
            this.adj = adj;
            this.zoneCount = zoneCount;
        }

        boolean canReach(int start, int goal, java.util.function.Predicate<MatterCableRelayBlockEntity.FilterRule> allowed) {
            if (start == goal) return true;
            if (start < 0 || goal < 0 || start >= zoneCount || goal >= zoneCount) return false;
            boolean[] seen = new boolean[zoneCount];
            var queue = new ArrayDeque<Integer>();
            seen[start] = true;
            queue.add(start);
            while (!queue.isEmpty()) {
                int cur = queue.removeFirst();
                for (var edge : adj.getOrDefault(cur, List.of())) {
                    if (seen[edge.target] || !allowed.test(edge.rule)) continue;
                    if (edge.target == goal) return true;
                    seen[edge.target] = true;
                    queue.addLast(edge.target);
                }
            }
            return false;
        }
    }

    private static final class RelayMemberPolicy implements MatterNetworkInventory.MemberPolicy {
        static final RelayMemberPolicy UNRESTRICTED = new RelayMemberPolicy(
                null, -1, -1, true, true, true, true, true, true);

        private final @Nullable ZoneGraph graph;
        private final int originZone;
        private final int storeZone;
        private final boolean anyWithdraw;
        private final boolean anyDeposit;
        private final boolean withdrawUnrestricted;
        private final boolean depositUnrestricted;
        private final boolean withdrawPrimal;
        private final boolean depositPrimal;
        private final Map<dev.latrox.rematter.chemistry.SubstanceKey, Boolean> withdrawCache = new HashMap<>();
        private final Map<dev.latrox.rematter.chemistry.SubstanceKey, Boolean> depositCache = new HashMap<>();

        private RelayMemberPolicy(@Nullable ZoneGraph graph, int originZone, int storeZone,
                                  boolean anyWithdraw, boolean anyDeposit,
                                  boolean withdrawUnrestricted, boolean depositUnrestricted,
                                  boolean withdrawPrimal, boolean depositPrimal) {
            this.graph = graph;
            this.originZone = originZone;
            this.storeZone = storeZone;
            this.anyWithdraw = anyWithdraw;
            this.anyDeposit = anyDeposit;
            this.withdrawUnrestricted = withdrawUnrestricted;
            this.depositUnrestricted = depositUnrestricted;
            this.withdrawPrimal = withdrawPrimal;
            this.depositPrimal = depositPrimal;
        }

        static RelayMemberPolicy of(ZoneGraph graph, int originZone, int storeZone) {
            boolean wu = graph.canReach(storeZone, originZone, MatterCableRelayBlockEntity.FilterRule::isUnrestricted);
            boolean du = graph.canReach(originZone, storeZone, MatterCableRelayBlockEntity.FilterRule::isUnrestricted);
            if (wu && du) return UNRESTRICTED;
            boolean anyW = wu || graph.canReach(storeZone, originZone, r -> true);
            boolean anyD = du || graph.canReach(originZone, storeZone, r -> true);
            boolean wp = wu || (anyW && graph.canReach(storeZone, originZone, MatterCableRelayBlockEntity.FilterRule::allowsPrimal));
            boolean dp = du || (anyD && graph.canReach(originZone, storeZone, MatterCableRelayBlockEntity.FilterRule::allowsPrimal));
            return new RelayMemberPolicy(graph, originZone, storeZone, anyW, anyD, wu, du, wp, dp);
        }

        boolean isUnrestricted() { return this == UNRESTRICTED; }

        @Override public boolean canWithdraw(dev.latrox.rematter.chemistry.SubstanceKey key) {
            if (withdrawUnrestricted) return true;
            if (!anyWithdraw || graph == null) return false;
            return withdrawCache.computeIfAbsent(key, k -> graph.canReach(storeZone, originZone, r -> r.allowsOrdinary(k)));
        }

        @Override public boolean canDeposit(dev.latrox.rematter.chemistry.SubstanceKey key) {
            if (depositUnrestricted) return true;
            if (!anyDeposit || graph == null) return false;
            return depositCache.computeIfAbsent(key, k -> graph.canReach(originZone, storeZone, r -> r.allowsOrdinary(k)));
        }

        @Override public boolean allowsAnyWithdraw() { return anyWithdraw; }
        @Override public boolean allowsAnyDeposit() { return anyDeposit; }
        boolean canWithdrawPrimal() { return withdrawPrimal; }
        boolean canDepositPrimal() { return depositPrimal; }
    }

    MachineMatterNetwork(UUID owner, SecurityMode originMode, List<Node> nodes, Set<Edge> edges, List<ChunkStamp> chunks,
                         String status, int storageCount, List<MachineBlockEntity> stores,
                         @Nullable MatterNetworkInventory inventory) {
        this(owner, originMode, nodes, edges, chunks, status, storageCount, stores, inventory, null, Map.of());
    }

    MachineMatterNetwork(UUID owner, SecurityMode originMode, List<Node> nodes, Set<Edge> edges, List<ChunkStamp> chunks,
                         String status, int storageCount, List<MachineBlockEntity> stores,
                         @Nullable MatterNetworkInventory inventory,
                         @Nullable Set<MachineBlockEntity> perspectiveMachines,
                         Map<MachineBlockEntity, RelayMemberPolicy> storePolicies) {
        this.owner = owner;
        this.originMode = Objects.requireNonNull(originMode);
        this.nodes = List.copyOf(nodes);
        this.endpoints = this.nodes.stream().filter(node -> !node.cable()).toList();
        this.associated = this.nodes.stream().filter(node -> node.entity instanceof MachinePortBlockEntity
                || node.entity instanceof MatterCableRelayBlockEntity
                || node.entity instanceof EntangledLinkBlockEntity
                || node.entity instanceof DimensionalEntangledLinkBlockEntity
                || node.entity instanceof MachineBlockEntity machine && IndustrialStructure.isIndustrial(machine.kind())).toList();
        var entities = Collections.newSetFromMap(new IdentityHashMap<BlockEntity, Boolean>());
        this.nodes.stream().map(Node::entity).filter(Objects::nonNull).forEach(entities::add);
        this.members = Collections.unmodifiableSet(entities);
        this.machines = endpoints.stream().map(Node::entity).filter(MachineBlockEntity.class::isInstance)
                .map(MachineBlockEntity.class::cast).toList();
        this.workers = machines.stream().filter(machine -> machine.kind().processes()).toList();
        this.interfaces = machines.stream().filter(machine -> machine.kind() == MachineKind.NETWORK_INTERFACE).toList();
        this.edges = Set.copyOf(edges);
        this.cablePositions = this.nodes.stream().filter(Node::cable).map(Node::key).filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
        this.chunks = List.copyOf(chunks);
        this.status = status;
        this.storageCount = storageCount;
        this.cellCount = stores.stream().mapToInt(MachineBlockEntity::networkCellCount).sum();
        boolean throttled = false;
        for (Node n : this.associated) {
            if (n.entity instanceof MatterCableRelayBlockEntity r && r.matterLimit() > 0) { throttled = true; break; }
        }
        this.hasThrottlingRelays = throttled;
        this.stores = List.copyOf(stores);
        this.inventory = inventory;
        this.perspectiveMachines = perspectiveMachines;
        this.storePolicies = storePolicies;
    }

    /** True when {@code machine} sits in the same unrestricted relay zone as this network's scan origin. */
    boolean sharesPerspective(MachineBlockEntity machine) {
        return perspectiveMachines == null || perspectiveMachines.contains(machine);
    }

    private RelayMemberPolicy policyFor(MachineBlockEntity store) {
        if (storePolicies.isEmpty()) return RelayMemberPolicy.UNRESTRICTED;
        return storePolicies.getOrDefault(store, RelayMemberPolicy.UNRESTRICTED);
    }

    List<MachineBlockEntity> depositStores(dev.latrox.rematter.chemistry.SubstanceKey target) {
        if (storePolicies.isEmpty()) return stores;
        return stores.stream().filter(s -> policyFor(s).canDeposit(target)).toList();
    }

    String status() { return status; }
    boolean ready() { return status.equals("ready") && inventory != null; }
    @Nullable MatterNetworkInventory inventory() { return inventory; }
    void markChanged() { stores.forEach(MachineBlockEntity::setChanged); }
    boolean hasCables() { return !cablePositions.isEmpty(); }

    private static @Nullable DimPos machineKey(MachineBlockEntity machine) {
        return machine.getLevel() instanceof ServerLevel sl ? new DimPos(sl.dimension(), machine.getBlockPos()) : null;
    }

    private final Map<DimPos, MatterCableBlock.Tier> throughputTiers = new HashMap<>();
    private final Map<DimPos, Integer> matterRates = new HashMap<>();
    private @Nullable Map<DimPos, Integer> cachedNodeRates;
    private @Nullable Map<DimPos, MatterCableRelayBlockEntity> cachedThrottlingRelays;
    private @Nullable Map<DimPos, List<DimPos>> cachedForwardAdj;
    private @Nullable Map<DimPos, List<DimPos>> cachedReverseAdj;

    private void ensureRoutingGraph() {
        if (cachedNodeRates != null) return;
        var nodeRates = new HashMap<DimPos, Integer>();
        var throttling = new HashMap<DimPos, MatterCableRelayBlockEntity>();
        var oneWay = new HashMap<DimPos, MatterCableRelayBlockEntity>();
        for (var node : nodes) {
            if (node.cable()) nodeRates.put(node.key, MatterCableBlock.Tier.values()[node.cableTier].matterRate());
            else if (node.entity instanceof EntangledLinkBlockEntity) nodeRates.put(node.key, MatterCableBlock.Tier.BASIC.matterRate());
            else if (node.entity instanceof DimensionalEntangledLinkBlockEntity) nodeRates.put(node.key, MatterCableBlock.Tier.RESONANT.matterRate());
            else if (node.entity instanceof MatterCableRelayBlockEntity relay) {
                int lim = relay.effectiveMatterRate();
                if (lim > 0 && lim < Integer.MAX_VALUE) {
                    nodeRates.put(node.key, lim);
                    throttling.put(node.key, relay);
                }
                if (relay.isOneWay()) oneWay.put(node.key, relay);
            }
        }
        var fwd = new HashMap<DimPos, List<DimPos>>();
        var rev = new HashMap<DimPos, List<DimPos>>();
        for (var edge : edges) {
            addDirectedEdge(edge.first, edge.second, oneWay, fwd, rev);
            addDirectedEdge(edge.second, edge.first, oneWay, fwd, rev);
        }
        cachedNodeRates = nodeRates;
        cachedThrottlingRelays = throttling;
        cachedForwardAdj = fwd;
        cachedReverseAdj = rev;
    }

    private static void addDirectedEdge(DimPos from, DimPos to, Map<DimPos, MatterCableRelayBlockEntity> oneWay,
                                        Map<DimPos, List<DimPos>> fwd, Map<DimPos, List<DimPos>> rev) {
        var fromRelay = oneWay.get(from);
        if (fromRelay != null) {
            DimPos outPos = new DimPos(from.dimension(), from.pos().relative(fromRelay.outputSide()));
            if (!outPos.equals(to)) return;
        }
        var toRelay = oneWay.get(to);
        if (toRelay != null) {
            DimPos inPos = new DimPos(to.dimension(), to.pos().relative(toRelay.inputSide()));
            if (!inPos.equals(from)) return;
        }
        fwd.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
        rev.computeIfAbsent(to, k -> new ArrayList<>()).add(from);
    }

    MatterCableBlock.Tier throughputTier(MachineBlockEntity machine) {
        DimPos originKey = machineKey(machine);
        if (originKey == null) return MatterCableBlock.Tier.BASIC;
        return throughputTiers.computeIfAbsent(originKey, origin -> {
            var tiers = new HashMap<DimPos, Integer>();
            var neighbors = new HashMap<DimPos, List<DimPos>>();
            for (var node : nodes) {
                if (node.cable()) tiers.put(node.key, node.cableTier);
                else if (node.entity instanceof EntangledLinkBlockEntity) tiers.put(node.key, 0);
                else if (node.entity instanceof DimensionalEntangledLinkBlockEntity) tiers.put(node.key, MatterCableBlock.Tier.RESONANT.ordinal());
            }
            for (var edge : edges) {
                neighbors.computeIfAbsent(edge.first, k -> new ArrayList<>()).add(edge.second);
                neighbors.computeIfAbsent(edge.second, k -> new ArrayList<>()).add(edge.first);
            }
            var destinations = stores.stream().map(MachineMatterNetwork::machineKey).filter(Objects::nonNull).collect(Collectors.toSet());
            return MatterCableBlock.Tier.values()[ControllerThroughput.bestTier(origin, destinations, tiers, neighbors)];
        });
    }

    int matterRate(MachineBlockEntity machine) {
        if (!hasThrottlingRelays && storePolicies.isEmpty()) return throughputTier(machine).matterRate();
        DimPos originKey = machineKey(machine);
        if (originKey == null) return MatterCableBlock.Tier.BASIC.matterRate();
        return matterRates.computeIfAbsent(originKey, origin -> {
            ensureRoutingGraph();
            int fallback = MatterCableBlock.Tier.BASIC.matterRate();
            var depositDests = new HashSet<DimPos>();
            var withdrawDests = new HashSet<DimPos>();
            for (var store : stores) {
                DimPos sk = machineKey(store);
                if (sk == null) continue;
                var pol = policyFor(store);
                if (pol.allowsAnyDeposit()) depositDests.add(sk);
                if (pol.allowsAnyWithdraw()) withdrawDests.add(sk);
            }
            int best = 0;
            if (!depositDests.isEmpty()) {
                best = Math.max(best, ControllerThroughput.bestRoute(origin, depositDests, cachedNodeRates, cachedForwardAdj, fallback).rate());
            }
            if (!withdrawDests.isEmpty()) {
                best = Math.max(best, ControllerThroughput.bestRoute(origin, withdrawDests, cachedNodeRates, cachedReverseAdj, fallback).rate());
            }
            return best <= 0 ? fallback : best;
        });
    }

    /**
     * Advances a machine's Matter transfer budget using the actual participating source/target Drives and
     * enforcing shared per-tick Matter/t caps on any inline throttling relays along the traversed routes.
     */
    boolean advanceTransfer(MachineBlockEntity machine, ControllerThroughput work, String signature,
                            long units, long gameTick,
                            Map<dev.latrox.rematter.chemistry.SubstanceKey, Long> inputs,
                            Map<dev.latrox.rematter.chemistry.SubstanceKey, Long> outputs) {
        if (!ready()) return false;
        if (!hasThrottlingRelays && storePolicies.isEmpty()) {
            return work.advance(signature, units, gameTick, throughputTier(machine).matterRate());
        }
        DimPos origin = machineKey(machine);
        if (origin == null) return work.advance(signature, units, gameTick, MatterCableBlock.Tier.BASIC.matterRate());
        ensureRoutingGraph();
        int fallback = MatterCableBlock.Tier.BASIC.matterRate();
        int rate = Integer.MAX_VALUE;
        var relaysOnRoutes = Collections.newSetFromMap(new IdentityHashMap<MatterCableRelayBlockEntity, Boolean>());

        if (!inputs.isEmpty()) {
            var withStock = new HashSet<DimPos>();
            var allEligible = new HashSet<DimPos>();
            for (var store : stores) {
                DimPos sk = machineKey(store);
                if (sk == null) continue;
                var pol = policyFor(store);
                if (!pol.allowsAnyWithdraw()) continue;
                var local = store.matterStore();
                for (var key : inputs.keySet()) {
                    if (!pol.canWithdraw(key)) continue;
                    allEligible.add(sk);
                    if (local != null && local.amount(key) > 0L) withStock.add(sk);
                }
            }
            var dests = withStock.isEmpty() ? allEligible : withStock;
            if (!dests.isEmpty()) {
                var route = ControllerThroughput.bestRoute(origin, dests, cachedNodeRates, cachedReverseAdj, fallback);
                if (route.reachable()) {
                    rate = Math.min(rate, route.rate());
                    collectRelays(route.path(), relaysOnRoutes);
                }
            }
        }
        if (!outputs.isEmpty()) {
            var eligible = new HashSet<DimPos>();
            for (var store : stores) {
                DimPos sk = machineKey(store);
                if (sk == null) continue;
                var pol = policyFor(store);
                if (!pol.allowsAnyDeposit()) continue;
                for (var key : outputs.keySet()) {
                    if (pol.canDeposit(key)) { eligible.add(sk); break; }
                }
            }
            if (!eligible.isEmpty()) {
                var route = ControllerThroughput.bestRoute(origin, eligible, cachedNodeRates, cachedForwardAdj, fallback);
                if (route.reachable()) {
                    rate = Math.min(rate, route.rate());
                    collectRelays(route.path(), relaysOnRoutes);
                }
            }
        }
        if (rate == Integer.MAX_VALUE) {
            rate = matterRate(machine);
            var anyDests = stores.stream().map(MachineMatterNetwork::machineKey).filter(Objects::nonNull).collect(Collectors.toSet());
            var fwdRoute = ControllerThroughput.bestRoute(origin, anyDests, cachedNodeRates, cachedForwardAdj, fallback);
            var revRoute = ControllerThroughput.bestRoute(origin, anyDests, cachedNodeRates, cachedReverseAdj, fallback);
            if (fwdRoute.rate() >= revRoute.rate() && fwdRoute.reachable()) collectRelays(fwdRoute.path(), relaysOnRoutes);
            else if (revRoute.reachable()) collectRelays(revRoute.path(), relaysOnRoutes);
        }
        if (relaysOnRoutes.isEmpty()) {
            return work.advance(signature, units, gameTick, Math.max(1, rate));
        }
        long sharedAllowance = Long.MAX_VALUE;
        for (var relay : relaysOnRoutes) {
            sharedAllowance = Math.min(sharedAllowance, relay.remainingMatterBudget(gameTick));
        }
        long accepted = work.advanceBounded(signature, units, gameTick, Math.max(1, rate), sharedAllowance);
        if (accepted > 0L) {
            for (var relay : relaysOnRoutes) {
                relay.spendMatterBudget(gameTick, accepted);
            }
        }
        return work.isComplete();
    }

    private void collectRelays(List<DimPos> path, Set<MatterCableRelayBlockEntity> target) {
        if (cachedThrottlingRelays == null || cachedThrottlingRelays.isEmpty()) return;
        for (var p : path) {
            var relay = cachedThrottlingRelays.get(p);
            if (relay != null) target.add(relay);
        }
    }

    List<MachineBlockEntity> checkpointStores() { return stores; }
    long primalAmount() {
        long sum = 0;
        for (var machine : machines) {
            if (machine.kind() == MachineKind.DRIVE && policyFor(machine).canWithdrawPrimal()) {
                sum = Math.addExact(sum, machine.drivePrimalAmount());
            }
        }
        return sum;
    }
    long primalCapacity() {
        long sum = 0;
        for (var machine : machines) {
            if (machine.kind() == MachineKind.DRIVE && policyFor(machine).canWithdrawPrimal()) {
                sum = Math.addExact(sum, machine.drivePrimalCapacity());
            }
        }
        return sum;
    }
    long primalDepositCapacity() {
        long sum = 0;
        for (var machine : machines) {
            if (machine.kind() == MachineKind.DRIVE && policyFor(machine).canDepositPrimal()) {
                sum = Math.addExact(sum, machine.drivePrimalCapacity());
            }
        }
        return sum;
    }
    long primalFreeSpace() {
        long sum = 0;
        for (var machine : machines) {
            if (machine.kind() == MachineKind.DRIVE && policyFor(machine).canDepositPrimal()) {
                sum = Math.addExact(sum, machine.drivePrimalFreeSpace());
            }
        }
        return sum;
    }
    long creditPrimal(MachineBlockEntity origin, long amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
        if (amount <= 0 || !current(origin, true)) return 0;
        long remaining = amount;
        for (var machine : machines) {
            if (machine.kind() != MachineKind.DRIVE || remaining <= 0 || !policyFor(machine).canDepositPrimal()) continue;
            long added = machine.creditDrivePrimal(remaining, tx);
            if (added > 0) remaining -= added;
        }
        return amount - remaining;
    }
    long debitPrimal(MachineBlockEntity origin, long amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
        if (amount <= 0 || !current(origin, true)) return 0;
        long remaining = amount;
        for (var machine : machines) {
            if (machine.kind() != MachineKind.DRIVE || remaining <= 0 || !policyFor(machine).canWithdrawPrimal()) continue;
            long taken = machine.debitDrivePrimal(remaining, tx);
            if (taken > 0) remaining -= taken;
        }
        return amount - remaining;
    }
    void markPrimalDrivesChanged() {
        for (var machine : machines) {
            if (machine.kind() == MachineKind.DRIVE) {
                machine.setChanged();
                machine.sendClientUpdateNow();
            }
        }
    }
    java.util.Optional<java.util.UUID> credit(MachineBlockEntity origin, java.util.UUID operation,
                                              dev.latrox.rematter.chemistry.SubstanceKey target, long amount,
                                              net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
        if (!ready() || !current(origin, true)) return java.util.Optional.empty();
        for (var store : stores) {
            if (!policyFor(store).canDeposit(target)) continue;
            var result = store.creditConvertedMatter(operation, target, amount, tx);
            if (result.isPresent()) return result;
        }
        return java.util.Optional.empty();
    }

    /** One shortest-path tree over the already discovered loaded graph. No new chunks are queried. */
    void pulseActivity(ServerLevel level, BlockPos originPos, List<MatterNetworkInventory.MemberActivity> activity) {
        if (!ready() || !hasCables() || activity.isEmpty()) return;
        DimPos origin = new DimPos(level.dimension(), originPos);
        var touched = new IdentityHashMap<MatterInventory, MatterNetworkInventory.MemberActivity>();
        activity.forEach(move -> touched.put(move.inventory(), move));
        var destinations = new LinkedHashMap<DimPos, Integer>();
        var levelsByDim = new HashMap<ResourceKey<Level>, ServerLevel>();
        for (var node : nodes) {
            levelsByDim.putIfAbsent(node.level.dimension(), node.level);
            if (node.key.equals(origin)) continue;
            int directions = 0;
            for (var member : node.inventories) {
                var move = touched.get(member);
                if (move == null) continue;
                if (move.withdrew()) directions |= WITHDRAWAL;
                if (move.deposited()) directions |= DEPOSIT;
            }
            if (directions != 0) destinations.put(node.key, directions);
        }
        if (destinations.isEmpty()) return;

        var neighbors = new HashMap<DimPos, List<DimPos>>();
        for (var edge : edges) {
            neighbors.computeIfAbsent(edge.first, ignored -> new ArrayList<>()).add(edge.second);
            neighbors.computeIfAbsent(edge.second, ignored -> new ArrayList<>()).add(edge.first);
        }
        neighbors.values().forEach(list -> list.sort(Comparator.naturalOrder()));
        var previous = new HashMap<DimPos, DimPos>();
        previous.put(origin, origin);
        var pending = new ArrayDeque<DimPos>();
        pending.add(origin);
        while (!pending.isEmpty()) {
            var position = pending.removeFirst();
            for (var neighbor : neighbors.getOrDefault(position, List.of())) {
                if (previous.putIfAbsent(neighbor, position) == null) pending.addLast(neighbor);
            }
        }

        var emitted = new HashSet<List<DimPos>>();
        for (var destination : destinations.entrySet()) {
            if (!previous.containsKey(destination.getKey())) continue;
            var route = new ArrayList<DimPos>();
            for (var position = destination.getKey(); !position.equals(origin); position = previous.get(position)) {
                route.add(position);
            }
            if ((destination.getValue() & WITHDRAWAL) != 0) pulseRoute(levelsByDim, route, emitted);
            if ((destination.getValue() & DEPOSIT) != 0) {
                Collections.reverse(route);
                pulseRoute(levelsByDim, route, emitted);
            }
            if (emitted.size() >= MAX_ACTIVITY_SEGMENTS) return;
        }
    }

    private void pulseRoute(Map<ResourceKey<Level>, ServerLevel> levelsByDim, List<DimPos> route, Set<List<DimPos>> emitted) {
        var segment = new ArrayList<DimPos>();
        ResourceKey<Level> currentDim = null;
        for (int i = 0; i <= route.size() && emitted.size() < MAX_ACTIVITY_SEGMENTS; i++) {
            DimPos step = i < route.size() ? route.get(i) : null;
            boolean isCable = step != null && cablePositions.contains(step);
            if (isCable && (currentDim == null || currentDim.equals(step.dimension()))) {
                currentDim = step.dimension();
                if (segment.size() < MAX_ACTIVITY_SEGMENT_LENGTH) segment.add(step);
            } else {
                if (!segment.isEmpty() && currentDim != null) {
                    var key = List.copyOf(segment);
                    ServerLevel segLevel = levelsByDim.get(currentDim);
                    if (segLevel != null && emitted.add(key)) {
                        MatterCableActivity.pulse(segLevel, segment.stream().map(DimPos::pos).toList());
                    }
                    segment.clear();
                    currentDim = null;
                }
                if (isCable && emitted.size() < MAX_ACTIVITY_SEGMENTS) {
                    currentDim = step.dimension();
                    segment.add(step);
                }
            }
        }
    }

    /** Every machine this observation describes, so one discovery can serve all of them. */
    List<MachineBlockEntity> machines() { return machines; }
    List<MachineBlockEntity> workers() { return workers; }
    Set<BlockEntity> memberEntities() { return members; }
    boolean hasWorker(MachineBlockEntity machine) { return members.contains(machine) && machine.kind().processes(); }
    List<MachineBlockEntity> manageableMachines() {
        if (!managementAvailable()) return List.of();
        return machines.stream().filter(machine -> machine.kind() != MachineKind.TERMINAL).toList();
    }
    boolean canManage(MachineBlockEntity device) {
        return managementAvailable() && device.kind() != MachineKind.TERMINAL && members.contains(device);
    }
    boolean managementAvailable() {
        return switch (status) {
            case "ready", "no_storage", "missing_cell", "unknown_schema" -> true;
            default -> false;
        };
    }
    List<MachineBlockEntity> interfaces() { return interfaces; }
    Object planningKey() { return List.of(owner == null ? UNOWNED_PLANNING_KEY : owner, status, endpoints); }
    Set<?> planningMembers() { return inventory == null ? Set.of() : Set.copyOf(inventory.members()); }
    boolean hasManualWork(MachineBlockEntity except) {
        return workers.stream().anyMatch(machine -> machine != except && machine.hasManualWork());
    }
    MachineSnapshot.NetworkView view() {
        return new MachineSnapshot.NetworkView(status, Math.min(MAX_NODES, nodes.size()), storageCount, cellCount,
                ready() ? inventory.total() : 0, ready() ? inventory.capacity() : 0,
                ready() ? inventory.typeCount() : 0);
    }

    boolean sameTopology(MachineMatterNetwork other) {
        return other != null && Objects.equals(owner, other.owner) && status.equals(other.status)
                && endpoints.equals(other.endpoints)
                && Objects.equals(inventory == null ? null : inventory.voidSignature(),
                        other.inventory == null ? null : other.inventory.voidSignature());
    }

    boolean current(MachineBlockEntity origin, boolean deep) {
        if (!members.contains(origin) || !origin.networkLive() || !Objects.equals(owner, origin.networkOwner())
                || originMode != origin.securityMode()
                || !(origin.getLevel() instanceof ServerLevel level)) return false;
        return checkLoadedNodes(level, deep);
    }

    boolean current(WirelessAccessPointBlockEntity origin, boolean deep) {
        if (!members.contains(origin) || !origin.networkLive() || !Objects.equals(owner, origin.owner())
                || originMode != origin.securityMode()
                || !(origin.getLevel() instanceof ServerLevel level)) return false;
        return checkLoadedNodes(level, deep);
    }

    boolean current(MatterLevelEmitterBlockEntity origin, boolean deep) {
        if (!members.contains(origin) || !origin.networkLive() || !Objects.equals(owner, origin.owner())
                || originMode != origin.securityMode()
                || !(origin.getLevel() instanceof ServerLevel level)) return false;
        return checkLoadedNodes(level, deep);
    }

    private boolean checkLoadedNodes(ServerLevel originLevel, boolean deep) {
        long tick = originLevel.getGameTime(), epoch = ChunkRevisions.epoch();
        if (checkedTick != tick || checkedEpoch != epoch) {
            for (int i = 0, n = chunks.size(); i < n; i++) {
                if (!chunks.get(i).matches()) return false;
            }
            for (int i = 0, n = associated.size(); i < n; i++) {
                if (!associated.get(i).matches(owner, originMode)) return false;
            }
            checkedTick = tick; checkedEpoch = epoch;
        }
        if (deep && rereadTick != tick) {
            for (int i = 0, n = nodes.size(); i < n; i++) {
                if (!nodes.get(i).matches(owner, originMode)) return false;
            }
            rereadTick = tick;
        }
        return true;
    }

    static MachineMatterNetwork discover(MachineBlockEntity origin) {
        if (!origin.networkLive() || !(origin.getLevel() instanceof ServerLevel level))
            return new MachineMatterNetwork(origin.networkOwner(), origin.securityMode(), List.of(), Set.of(), List.of(),
                    "network_invalid", 0, List.of(), null);
        return runScan(level, origin.networkOwner(), origin.securityMode(), origin.getBlockPos());
    }

    static MachineMatterNetwork discover(WirelessAccessPointBlockEntity origin) {
        if (!origin.networkLive() || !(origin.getLevel() instanceof ServerLevel level))
            return new MachineMatterNetwork(origin.owner(), origin.securityMode(), List.of(), Set.of(), List.of(),
                    "network_invalid", 0, List.of(), null);
        return runScan(level, origin.owner(), origin.securityMode(), origin.getBlockPos());
    }

    static MachineMatterNetwork discover(MatterLevelEmitterBlockEntity origin) {
        if (!origin.networkLive() || !(origin.getLevel() instanceof ServerLevel level))
            return new MachineMatterNetwork(origin.owner(), origin.securityMode(), List.of(), Set.of(), List.of(),
                    "network_invalid", 0, List.of(), null);
        return runScan(level, origin.owner(), origin.securityMode(), origin.getBlockPos());
    }

    private static MachineMatterNetwork runScan(ServerLevel level, UUID owner, SecurityMode originMode, BlockPos originPos) {
        var network = new Scan(level.getServer(), owner, originMode).run(new DimPos(level.dimension(), originPos));
        network.checkedTick = network.rereadTick = level.getGameTime();
        network.checkedEpoch = ChunkRevisions.epoch();
        return network;
    }

    static int nextStage(int stage, int nodes, boolean truncated, int weakestCable) {
        if (truncated) return TOO_LARGE;
        if (weakestCable < 0) return nodes <= MatterCableBlock.Tier.BASIC.networkLimit() ? FITS : TOO_LARGE;
        if (nodes <= MatterCableBlock.Tier.values()[weakestCable].networkLimit()) return FITS;
        return weakestCable + 1;
    }

    static <N> Set<N> routed(Map<N, N> parents, List<N> devices) {
        var result = new HashSet<N>();
        for (var device : devices) {
            N position = device;
            while (position != null && result.add(position)) position = parents.get(position);
        }
        return result;
    }

    record Edge(DimPos first, DimPos second) {
        static Edge of(DimPos a, DimPos b) { return a.compareTo(b) <= 0 ? new Edge(a, b) : new Edge(b, a); }
    }

    record Node(ServerLevel level, @Nullable DimPos key, BlockPos position, Object block, int cableTier, @Nullable BlockEntity entity,
                @Nullable MachineBlockEntity controller, @Nullable Direction outward,
                String structure, String localStatus, List<MatterInventory> inventories,
                dev.latrox.rematter.chemistry.VoidPolicy voidPolicy,
                long expectedRevision, @Nullable DimPos expectedDimPeer) {
        Node { inventories = List.copyOf(inventories); Objects.requireNonNull(voidPolicy, "voidPolicy"); }
        Node(BlockPos position, Object block, int cableTier, @Nullable BlockEntity entity,
             @Nullable MachineBlockEntity controller, @Nullable Direction outward,
             String structure, String localStatus, List<MatterInventory> inventories) {
            this(null, null, position, block, cableTier, entity,
                    controller, outward, structure, localStatus, inventories,
                    dev.latrox.rematter.chemistry.VoidPolicy.NONE, 0L, null);
        }
        boolean cable() { return cableTier >= 0; }

        boolean matches(UUID owner, SecurityMode originMode) {
            if (level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) == null
                    || level.getBlockState(position).getBlock() != block || level.getBlockEntity(position) != entity) return false;
            if (cable() && !MachineAccess.canLinkCable(level, owner, originMode, CableOwners.owner(level, position), CableOwners.mode(level, position))) return false;
            if (entity instanceof MachineBlockEntity machine) {
                if (!machine.networkLive() || !MachineAccess.canConnect(level, owner, originMode, machine.networkOwner(), machine.securityMode())) return false;
                if (machine.kind().isStorage() && (!localStatus.equals(machine.networkStorageStatus())
                        || !inventories.equals(machine.localMatterMembers())
                        || !voidPolicy.equals(machine.localVoidPolicy()))) return false;
                if (IndustrialStructure.isIndustrial(machine.kind())) {
                    var formation = IndustrialStructure.inspect(machine);
                    return formation.formed() && structure.equals(formation.layoutFingerprint());
                }
            } else if (entity instanceof WirelessAccessPointBlockEntity ap) {
                return ap.networkLive() && MachineAccess.canConnect(level, owner, originMode, ap.owner(), ap.securityMode());
            } else if (entity instanceof MatterLevelEmitterBlockEntity emitter) {
                return emitter.networkLive() && MachineAccess.canConnect(level, owner, originMode, emitter.owner(), emitter.securityMode());
            } else if (entity instanceof MatterCableRelayBlockEntity relay) {
                return !relay.isRemoved() && relay.allowsMatter()
                        && MachineAccess.canLinkCable(level, owner, originMode, relay.owner(), relay.securityMode())
                        && expectedRevision == relay.stateRevision();
            } else if (entity instanceof EntangledLinkBlockEntity link) {
                return MachineAccess.canLinkCable(level, owner, originMode, link.owner(), link.securityMode())
                        && localStatus.equals(EntangledLinks.peer(level, position, owner, originMode).map(p -> Long.toString(p.asLong())).orElse(""));
            } else if (entity instanceof DimensionalEntangledLinkBlockEntity dlink) {
                return !dlink.isRemoved()
                        && dlink.stateRevision() == expectedRevision
                        && MachineAccess.canLinkCable(level, owner, originMode, dlink.owner(), dlink.securityMode())
                        && Objects.equals(expectedDimPeer, EntangledLinks.dimensionalPeer(level, position, owner, originMode).orElse(null));
            } else if (entity instanceof MachinePortBlockEntity port) {
                return !port.isRemoved() && port.acceptsMatter() && port.controller(null) == controller && port.outward() == outward
                        && controller != null && MachineAccess.canConnect(level, owner, originMode, controller.networkOwner(), controller.securityMode());
            }
            return true;
        }
    }

    private record ChunkKey(ResourceKey<Level> dimension, long packed) {}

    private static final class Scan {
        private final MinecraftServer server;
        private final UUID owner;
        private final SecurityMode originMode;
        private final Map<ChunkKey, ChunkStamp> chunks = new LinkedHashMap<>();
        private final Map<DimPos, Node> observed = new HashMap<>();
        private final Set<DimPos> unloadedPositions = new HashSet<>();
        private final Map<DimPos, Node> reached = new HashMap<>();
        private final Map<DimPos, DimPos> parents = new HashMap<>();
        private final Map<DimPos, Node> connected = new HashMap<>();
        private final Set<Edge> edges = new HashSet<>();
        private @Nullable DimPos originPos;
        private boolean unloaded;
        private boolean truncated;

        Scan(MinecraftServer server, UUID owner, SecurityMode originMode) {
            this.server = server;
            this.owner = owner;
            this.originMode = originMode;
        }

        MachineMatterNetwork run(DimPos originPos) {
            this.originPos = originPos;
            Node root = read(originPos);
            if (root == null) return result("network_invalid");
            int stage = 0;
            while (stage < TOO_LARGE) {
                explore(root, stage);
                int weakest = connected.values().stream().filter(Node::cable).mapToInt(Node::cableTier).min().orElse(-1);
                int next = nextStage(stage, connected.size(), truncated, weakest);
                if (next == FITS) return result(unloaded ? "network_unloaded" : "ready");
                stage = next;
            }
            return result("network_too_large");
        }

        private void explore(Node root, int stage) {
            reached.clear(); parents.clear(); connected.clear(); edges.clear(); unloaded = false; truncated = false;
            ArrayDeque<Node> pending = new ArrayDeque<>();
            reached.put(root.key, root);
            pending.add(root);
            while (!pending.isEmpty() && !truncated) {
                Node node = pending.removeFirst();
                for (DimPos position : neighbors(node)) {
                    Node next = read(position);
                    if (unloadedPositions.contains(position)) unloaded = true;
                    if (next == null || next.cable() && next.cableTier < stage
                            || !acceptsFrom(next, node) || !acceptsFrom(node, next)) continue;
                    edges.add(Edge.of(node.key, next.key));
                    if (reached.containsKey(position)) continue;
                    if (reached.size() == SCAN_BUDGET) { truncated = true; break; }
                    reached.put(position, next);
                    parents.put(position, node.key);
                    pending.addLast(next);
                }
            }
            var devices = reached.values().stream().filter(node -> !node.cable()).map(Node::key).toList();
            for (var position : routed(parents, devices)) connected.put(position, reached.get(position));
            edges.removeIf(edge -> !connected.containsKey(edge.first) || !connected.containsKey(edge.second));
        }

        private MachineMatterNetwork result(String diagnostic) {
            List<Node> nodes = connected.values().stream().sorted(Comparator.comparing(Node::key)).toList();
            List<Node> storageNodes = nodes.stream().filter(node -> node.entity instanceof MachineBlockEntity machine && machine.kind().isStorage()).toList();
            List<MachineBlockEntity> stores = storageNodes.stream()
                    .filter(node -> !node.inventories.isEmpty() || node.voidPolicy.present())
                    .map(node -> (MachineBlockEntity) node.entity).toList();
            if (storageNodes.size() > MAX_STORAGES) diagnostic = "network_too_large";
            if (diagnostic.equals("ready") && storageNodes.stream().anyMatch(node -> node.localStatus.equals("unknown_schema"))) diagnostic = "unknown_schema";
            if (diagnostic.equals("ready") && stores.isEmpty()) diagnostic = storageNodes.isEmpty() ? "no_storage" : "missing_cell";
            if (diagnostic.equals("ready") && !chunks.values().stream().allMatch(ChunkStamp::matches)) diagnostic = "network_invalid";

            var constrainedRelays = new HashSet<DimPos>();
            var constrainedRelayNodes = new ArrayList<Node>();
            for (var node : nodes) {
                if (node.entity instanceof MatterCableRelayBlockEntity relay && relay.hasRoutingConstraint()) {
                    constrainedRelays.add(node.key);
                    constrainedRelayNodes.add(node);
                }
            }

            Set<MachineBlockEntity> perspectiveMachines = null;
            Map<MachineBlockEntity, RelayMemberPolicy> storePolicies = Map.of();
            if (!constrainedRelays.isEmpty() && originPos != null) {
                var fullAdj = new HashMap<DimPos, List<DimPos>>();
                for (var edge : edges) {
                    fullAdj.computeIfAbsent(edge.first, k -> new ArrayList<>()).add(edge.second);
                    fullAdj.computeIfAbsent(edge.second, k -> new ArrayList<>()).add(edge.first);
                }
                var zoneOf = new HashMap<DimPos, Integer>();
                int[] nextZone = {0};
                for (var node : nodes) {
                    if (constrainedRelays.contains(node.key) || zoneOf.containsKey(node.key)) continue;
                    int zId = nextZone[0]++;
                    var q = new ArrayDeque<DimPos>();
                    zoneOf.put(node.key, zId);
                    q.add(node.key);
                    while (!q.isEmpty()) {
                        var cur = q.removeFirst();
                        for (var nb : fullAdj.getOrDefault(cur, List.of())) {
                            if (!constrainedRelays.contains(nb) && zoneOf.putIfAbsent(nb, zId) == null) q.addLast(nb);
                        }
                    }
                }
                Integer originZone = zoneOf.get(originPos);
                var zoneAdj = new HashMap<Integer, List<ZoneEdge>>();
                var relayJunctions = new HashMap<Edge, Integer>();
                for (var rNode : constrainedRelayNodes) {
                    var relay = (MatterCableRelayBlockEntity) rNode.entity;
                    var dim = rNode.level.dimension();
                    var inPos = new DimPos(dim, rNode.position.relative(relay.inputSide()));
                    var outPos = new DimPos(dim, rNode.position.relative(relay.outputSide()));
                    if (!edges.contains(Edge.of(rNode.key, inPos)) || !edges.contains(Edge.of(rNode.key, outPos))) continue;
                    int inV = constrainedRelays.contains(inPos)
                            ? relayJunctions.computeIfAbsent(Edge.of(rNode.key, inPos), k -> nextZone[0]++)
                            : zoneOf.getOrDefault(inPos, -1);
                    int outV = constrainedRelays.contains(outPos)
                            ? relayJunctions.computeIfAbsent(Edge.of(rNode.key, outPos), k -> nextZone[0]++)
                            : zoneOf.getOrDefault(outPos, -1);
                    if (inV < 0 || outV < 0) continue;
                    var rule = relay.filterRule();
                    zoneAdj.computeIfAbsent(inV, k -> new ArrayList<>()).add(new ZoneEdge(outV, rule));
                    if (!relay.isOneWay()) {
                        zoneAdj.computeIfAbsent(outV, k -> new ArrayList<>()).add(new ZoneEdge(inV, rule));
                    }
                }
                if (originZone != null) {
                    var zoneGraph = new ZoneGraph(zoneAdj, nextZone[0]);
                    var sameZone = Collections.newSetFromMap(new IdentityHashMap<MachineBlockEntity, Boolean>());
                    for (var node : nodes) {
                        if (node.entity instanceof MachineBlockEntity m && originZone.equals(zoneOf.get(node.key))) {
                            sameZone.add(m);
                        }
                    }
                    perspectiveMachines = sameZone;
                    var policies = new IdentityHashMap<MachineBlockEntity, RelayMemberPolicy>();
                    for (var sNode : storageNodes) {
                        var store = (MachineBlockEntity) sNode.entity;
                        Integer sZone = zoneOf.get(sNode.key);
                        if (sZone == null || sZone.equals(originZone)) {
                            policies.put(store, RelayMemberPolicy.UNRESTRICTED);
                        } else {
                            policies.put(store, RelayMemberPolicy.of(zoneGraph, originZone, sZone));
                        }
                    }
                    storePolicies = policies;
                }
            }

            MatterNetworkInventory inventory = null;
            if (diagnostic.equals("ready")) {
                var mergedVoid = dev.latrox.rematter.chemistry.VoidPolicy.merge(storageNodes.stream().map(Node::voidPolicy).toList());
                try {
                    if (storePolicies.isEmpty()) {
                        inventory = new MatterNetworkInventory(
                                storageNodes.stream().flatMap(node -> node.inventories.stream()).toList(),
                                mergedVoid);
                    } else {
                        var invList = new ArrayList<MatterInventory>();
                        var polList = new ArrayList<MatterNetworkInventory.MemberPolicy>();
                        var voidRules = new ArrayList<MatterNetworkInventory.VoidRule>();
                        for (var sNode : storageNodes) {
                            var store = (MachineBlockEntity) sNode.entity;
                            var pol = storePolicies.getOrDefault(store, RelayMemberPolicy.UNRESTRICTED);
                            MatterNetworkInventory.MemberPolicy mp = pol.isUnrestricted()
                                    ? MatterNetworkInventory.MemberPolicy.UNRESTRICTED : pol;
                            for (var inv : sNode.inventories) {
                                invList.add(inv);
                                polList.add(mp);
                            }
                            if (sNode.voidPolicy.present()) {
                                voidRules.add(new MatterNetworkInventory.VoidRule(sNode.voidPolicy, mp));
                            }
                        }
                        inventory = new MatterNetworkInventory(invList, mergedVoid, polList, voidRules);
                    }
                } catch (IllegalArgumentException | ArithmeticException invalid) { diagnostic = "network_invalid"; }
            }
            List<MachineBlockEntity> exposedStores = stores.size() <= MAX_STORAGES ? stores : List.of();
            return new MachineMatterNetwork(owner, originMode, nodes, edges, new ArrayList<>(chunks.values()), diagnostic,
                    Math.min(MAX_STORAGES, storageNodes.size()), exposedStores, inventory, perspectiveMachines, storePolicies);
        }

        private @Nullable Node read(DimPos key) {
            if (observed.containsKey(key)) return observed.get(key);
            ServerLevel level = key.resolveLevel(server);
            if (level == null) { observed.put(key, null); return null; }
            BlockPos position = key.pos();
            int x = position.getX() >> 4, z = position.getZ() >> 4;
            ChunkKey chunkKey = new ChunkKey(key.dimension(), (long) x << 32 ^ (z & 0xffffffffL));
            LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
            chunks.computeIfAbsent(chunkKey, ignored -> new ChunkStamp(level, ChunkRevisions.Stamp.of(x, z, chunk)));
            if (chunk == null) { unloadedPositions.add(key); observed.put(key, null); return null; }
            var state = level.getBlockState(position);
            BlockEntity entity = level.getBlockEntity(position);
            Node result = null;
            var noVoid = dev.latrox.rematter.chemistry.VoidPolicy.NONE;
            if (state.getBlock() instanceof MatterCableBlock cable
                    && MachineAccess.canLinkCable(level, owner, originMode, CableOwners.owner(level, position), CableOwners.mode(level, position)))
                result = new Node(level, key, position, cable, cable.tier().ordinal(), entity, null, null, "", "", List.of(), noVoid, 0L, null);
            else if (entity instanceof MachineBlockEntity machine && machine.networkLive()
                    && MachineAccess.canConnect(level, owner, originMode, machine.networkOwner(), machine.securityMode())) {
                String structure = "";
                if (IndustrialStructure.isIndustrial(machine.kind())) {
                    var formation = IndustrialStructure.inspect(machine);
                    if (!formation.formed()) { observed.put(key, null); return null; }
                    structure = formation.layoutFingerprint();
                }
                String status = machine.kind().isStorage() ? machine.networkStorageStatus() : "";
                result = new Node(level, key, position, state.getBlock(), -1, machine, null, null, structure, status,
                        machine.localMatterMembers(), machine.localVoidPolicy(), 0L, null);
            } else if (entity instanceof WirelessAccessPointBlockEntity ap && ap.networkLive()
                    && MachineAccess.canConnect(level, owner, originMode, ap.owner(), ap.securityMode())) {
                result = new Node(level, key, position, state.getBlock(), -1, ap, null, null, "", "", List.of(), noVoid, 0L, null);
            } else if (entity instanceof MatterLevelEmitterBlockEntity emitter && emitter.networkLive()
                    && MachineAccess.canConnect(level, owner, originMode, emitter.owner(), emitter.securityMode())) {
                result = new Node(level, key, position, state.getBlock(), -1, emitter, null, null, "", "", List.of(), noVoid, 0L, null);
            } else if (entity instanceof MatterCableRelayBlockEntity relay && relay.allowsMatter()
                    && MachineAccess.canLinkCable(level, owner, originMode, relay.owner(), relay.securityMode())) {
                result = new Node(level, key, position, state.getBlock(), -1, relay, null, null, "", relay.revisionToken(), List.of(), noVoid, relay.stateRevision(), null);
            } else if (entity instanceof EntangledLinkBlockEntity link
                    && MachineAccess.canLinkCable(level, owner, originMode, link.owner(), link.securityMode())) {
                String peer = EntangledLinks.peer(level, position, owner, originMode).map(p -> Long.toString(p.asLong())).orElse("");
                result = new Node(level, key, position, state.getBlock(), -1, link, null, null, "",
                        peer + "#" + link.packedPortModes() + ":" + (link.redstoneAllows() ? 1 : 0), List.of(), noVoid, 0L, null);
            } else if (entity instanceof DimensionalEntangledLinkBlockEntity dlink
                    && !dlink.isRemoved()
                    && MachineAccess.canLinkCable(level, owner, originMode, dlink.owner(), dlink.securityMode())) {
                DimPos peer = EntangledLinks.dimensionalPeer(level, position, owner, originMode).orElse(null);
                String statusStr = peer == null ? "" : peer.dimension().identifier() + "@" + peer.pos().asLong();
                result = new Node(level, key, position, state.getBlock(), -1, dlink, null, null, "", statusStr, List.of(), noVoid, dlink.stateRevision(), peer);
            } else if (entity instanceof MachinePortBlockEntity port && port.acceptsMatter()) {
                var controller = port.controller(null);
                Direction outward = port.outward();
                if (controller != null && outward != null
                        && MachineAccess.canConnect(level, owner, originMode, controller.networkOwner(), controller.securityMode()))
                    result = new Node(level, key, position, state.getBlock(), -1, port, controller, outward, "", "", List.of(), noVoid, 0L, null);
            }
            observed.put(key, result);
            return result;
        }

        private List<DimPos> neighbors(Node node) {
            ResourceKey<Level> dim = node.level.dimension();
            if (node.entity instanceof EntangledLinkBlockEntity link) {
                var result = new ArrayList<DimPos>(5);
                for (Direction d : Direction.values()) {
                    if (link.allowsMatter(d)) result.add(new DimPos(dim, node.position.relative(d)));
                }
                EntangledLinks.peer(node.level, node.position, owner, originMode).ifPresent(p -> result.add(new DimPos(dim, p)));
                return result;
            }
            if (node.entity instanceof DimensionalEntangledLinkBlockEntity dlink) {
                var result = new ArrayList<DimPos>(5);
                for (Direction d : Direction.values()) {
                    if (dlink.allowsMatter(d)) result.add(new DimPos(dim, node.position.relative(d)));
                }
                if (node.expectedDimPeer != null) result.add(node.expectedDimPeer);
                return result;
            }
            if (node.entity instanceof MachinePortBlockEntity) {
                return List.of(new DimPos(dim, node.controller.getBlockPos()), new DimPos(dim, node.position.relative(node.outward)));
            }
            if (node.entity instanceof WirelessAccessPointBlockEntity ap) {
                return ap.connectionSides().stream().map(side -> new DimPos(dim, node.position.relative(side))).toList();
            }
            if (node.entity instanceof MatterLevelEmitterBlockEntity emitter) {
                return emitter.connectionSides().stream().map(side -> new DimPos(dim, node.position.relative(side))).toList();
            }
            if (node.entity instanceof MatterCableRelayBlockEntity valve) {
                return valve.connectionSides().stream().map(side -> new DimPos(dim, node.position.relative(side))).toList();
            }
            if (node.entity instanceof MachineBlockEntity machine) {
                if (IndustrialStructure.isIndustrial(machine.kind()))
                    return IndustrialStructure.inspect(machine).ports().stream().map(p -> new DimPos(dim, p.position())).toList();
                var result = new ArrayList<DimPos>();
                for (Direction side : Direction.values()) {
                    if (machine.acceptsMatter(side)) result.add(new DimPos(dim, node.position.relative(side)));
                }
                return result;
            }
            var result = new ArrayList<DimPos>(6);
            int blocked = node.cable() ? CableOwners.blockedSides(node.level, node.position) : 0;
            for (Direction d : Direction.values()) {
                if ((blocked & (1 << d.get3DDataValue())) == 0) result.add(new DimPos(dim, node.position.relative(d)));
            }
            return result;
        }

        private boolean acceptsFrom(Node target, Node source) {
            if (target.entity instanceof DimensionalEntangledLinkBlockEntity dlink) {
                if (target.level == source.level && target.position.distManhattan(source.position) == 1) {
                    for (Direction d : Direction.values()) {
                        if (target.position.relative(d).equals(source.position)) {
                            return dlink.allowsMatter(d);
                        }
                    }
                    return false;
                }
                return target.expectedDimPeer != null && target.expectedDimPeer.equals(source.key);
            }
            if (target.level != source.level) return false;
            if (target.entity instanceof EntangledLinkBlockEntity link) {
                if (target.position.distManhattan(source.position) == 1) {
                    for (Direction d : Direction.values()) {
                        if (target.position.relative(d).equals(source.position)) {
                            return link.allowsMatter(d);
                        }
                    }
                    return false;
                }
                return EntangledLinks.peer(target.level, target.position, owner, originMode).filter(source.position::equals).isPresent();
            }
            if (target.entity instanceof MachinePortBlockEntity)
                return source.position.equals(target.controller.getBlockPos()) || source.position.equals(target.position.relative(target.outward));
            if (target.entity instanceof WirelessAccessPointBlockEntity ap) {
                for (Direction side : ap.connectionSides()) if (target.position.relative(side).equals(source.position)) return true;
                return false;
            }
            if (target.entity instanceof MatterLevelEmitterBlockEntity emitter) {
                for (Direction side : emitter.connectionSides()) if (target.position.relative(side).equals(source.position)) return true;
                return false;
            }
            if (target.entity instanceof MatterCableRelayBlockEntity valve) {
                for (Direction side : valve.connectionSides()) if (target.position.relative(side).equals(source.position)) return true;
                return false;
            }
            if (target.entity instanceof MachineBlockEntity machine) {
                if (IndustrialStructure.isIndustrial(machine.kind()))
                    return source.entity instanceof MachinePortBlockEntity && source.controller == machine;
                for (Direction side : Direction.values()) if (target.position.relative(side).equals(source.position))
                    return machine.acceptsMatter(side);
                return false;
            }
            if (target.cable() && target.level == source.level && target.position.distManhattan(source.position) == 1) {
                int blocked = CableOwners.blockedSides(target.level, target.position);
                if (blocked != 0) {
                    for (Direction d : Direction.values()) {
                        if (target.position.relative(d).equals(source.position)) {
                            return (blocked & (1 << d.get3DDataValue())) == 0;
                        }
                    }
                }
            }
            return true;
        }
    }
}
