package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.MatterInventory;
import dev.latrox.rematter.chemistry.MatterNetworkInventory;
import java.lang.ref.WeakReference;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * A bounded observation of real, loaded owners. The aggregate owns no resources;
 * every unit remains in exactly one installed cell.
 */
final class MachineMatterNetwork {
    static final int MAX_NODES = MatterPipeBlock.Tier.RESONANT.networkLimit();
    static final int MAX_STORAGES = 64;
    private static final int MAX_ACTIVITY_SEGMENTS = 4;
    private static final int MAX_ACTIVITY_SEGMENT_LENGTH = 64;
    private static final int WITHDRAWAL = 1;
    private static final int DEPOSIT = 2;
    private final UUID owner;
    private final List<Node> nodes;
    private final Set<Edge> edges;
    private final Set<BlockPos> cablePositions;
    private final List<ChunkStamp> chunks;
    private final String status;
    private final int storageCount;
    private final int cellCount;
    private final List<MachineBlockEntity> stores;
    private final @Nullable MatterNetworkInventory inventory;

    private MachineMatterNetwork(UUID owner, List<Node> nodes, Set<Edge> edges, List<ChunkStamp> chunks,
                                 String status, int storageCount, List<MachineBlockEntity> stores,
                                 @Nullable MatterNetworkInventory inventory) {
        this.owner = owner;
        this.nodes = List.copyOf(nodes);
        this.edges = Set.copyOf(edges);
        this.cablePositions = nodes.stream().filter(node -> node.block instanceof MatterPipeBlock)
                .map(Node::position).collect(Collectors.toUnmodifiableSet());
        this.chunks = List.copyOf(chunks);
        this.status = status;
        this.storageCount = storageCount;
        this.cellCount = stores.stream().mapToInt(MachineBlockEntity::networkCellCount).sum();
        this.stores = List.copyOf(stores);
        this.inventory = inventory;
    }

    String status() { return status; }
    boolean ready() { return status.equals("ready") && inventory != null; }
    @Nullable MatterNetworkInventory inventory() { return inventory; }
    void markChanged() { stores.forEach(MachineBlockEntity::setChanged); }
    boolean hasCables() { return !cablePositions.isEmpty(); }

    /** One shortest-path tree over the already discovered loaded graph. No new chunks are queried. */
    void pulseActivity(ServerLevel level, BlockPos origin, List<MatterNetworkInventory.MemberActivity> activity) {
        if (!ready() || !hasCables() || activity.isEmpty()) return;
        var touched = new IdentityHashMap<MatterInventory, MatterNetworkInventory.MemberActivity>();
        activity.forEach(move -> touched.put(move.inventory(), move));
        var destinations = new LinkedHashMap<BlockPos, Integer>();
        for (var node : nodes) {
            if (node.position.equals(origin)) continue;
            int directions = 0;
            for (var member : node.inventories) {
                var move = touched.get(member);
                if (move == null) continue;
                if (move.withdrew()) directions |= WITHDRAWAL;
                if (move.deposited()) directions |= DEPOSIT;
            }
            if (directions != 0) destinations.put(node.position, directions);
        }
        if (destinations.isEmpty()) return;

        var neighbors = new HashMap<BlockPos, List<BlockPos>>();
        for (var edge : edges) {
            neighbors.computeIfAbsent(edge.first, ignored -> new ArrayList<>()).add(edge.second);
            neighbors.computeIfAbsent(edge.second, ignored -> new ArrayList<>()).add(edge.first);
        }
        neighbors.values().forEach(list -> list.sort(Comparator.naturalOrder()));
        var previous = new HashMap<BlockPos, BlockPos>();
        previous.put(origin, origin);
        var pending = new ArrayDeque<BlockPos>();
        pending.add(origin);
        while (!pending.isEmpty()) {
            var position = pending.removeFirst();
            for (var neighbor : neighbors.getOrDefault(position, List.of())) {
                if (previous.putIfAbsent(neighbor, position) == null) pending.addLast(neighbor);
            }
        }

        var emitted = new HashSet<List<BlockPos>>();
        for (var destination : destinations.entrySet()) {
            if (!previous.containsKey(destination.getKey())) continue;
            var route = new ArrayList<BlockPos>();
            for (var position = destination.getKey(); !position.equals(origin); position = previous.get(position)) {
                route.add(position);
            }
            if ((destination.getValue() & WITHDRAWAL) != 0) pulseRoute(level, route, emitted);
            if ((destination.getValue() & DEPOSIT) != 0) {
                Collections.reverse(route);
                pulseRoute(level, route, emitted);
            }
            if (emitted.size() >= MAX_ACTIVITY_SEGMENTS) return;
        }
    }

    private void pulseRoute(ServerLevel level, List<BlockPos> route, Set<List<BlockPos>> emitted) {
        var segment = new ArrayList<BlockPos>();
        for (int i = 0; i <= route.size() && emitted.size() < MAX_ACTIVITY_SEGMENTS; i++) {
            if (i < route.size() && cablePositions.contains(route.get(i))) {
                if (segment.size() < MAX_ACTIVITY_SEGMENT_LENGTH) segment.add(route.get(i));
            } else if (!segment.isEmpty()) {
                var path = List.copyOf(segment);
                if (emitted.add(path)) MatterCableActivity.pulse(level, path);
                segment.clear();
            }
        }
    }

    List<MachineBlockEntity> workers() {
        return nodes.stream().map(Node::entity).filter(MachineBlockEntity.class::isInstance)
                .map(MachineBlockEntity.class::cast).filter(machine -> machine.kind().processes()).toList();
    }
    List<MachineBlockEntity> manageableMachines() {
        if (!managementAvailable()) return List.of();
        return nodes.stream().map(Node::entity).filter(MachineBlockEntity.class::isInstance)
                .map(MachineBlockEntity.class::cast).filter(machine -> machine.kind() != MachineKind.TERMINAL).toList();
    }
    boolean canManage(MachineBlockEntity device) {
        return managementAvailable() && device.kind() != MachineKind.TERMINAL
                && nodes.stream().anyMatch(node -> node.entity == device);
    }
    private boolean managementAvailable() {
        // The route can be complete even when the owner needs to install or recover a cell.
        return switch (status) {
            case "ready", "no_storage", "missing_cell", "unknown_schema" -> true;
            default -> false;
        };
    }
    List<MachineBlockEntity> interfaces() {
        return nodes.stream().map(Node::entity).filter(MachineBlockEntity.class::isInstance)
                .map(MachineBlockEntity.class::cast).filter(machine -> machine.kind() == MachineKind.NETWORK_INTERFACE).toList();
    }
    MachineSnapshot.NetworkView view() {
        return new MachineSnapshot.NetworkView(status, nodes.size(), storageCount, cellCount,
                ready() ? inventory.total() : 0, ready() ? inventory.capacity() : 0,
                ready() ? inventory.typeCount() : 0);
    }

    /** Membership includes routes, endpoints, owners, mounted inventories and industrial associations. */
    boolean sameTopology(MachineMatterNetwork other) {
        return other != null && Objects.equals(owner, other.owner) && status.equals(other.status)
                && nodes.equals(other.nodes) && edges.equals(other.edges);
    }

    /** Revisions include observed non-network boundary blocks, so a new branch invalidates this cache. */
    boolean stillValid(MachineBlockEntity origin) {
        if (!origin.networkLive() || !Objects.equals(owner, origin.networkOwner())
                || !(origin.getLevel() instanceof ServerLevel level)) return false;
        if (!chunks.stream().allMatch(stamp -> stamp.matches(level))) return false;
        for (Node node : nodes) if (!node.matches(level, owner)) return false;
        if (inventory != null) {
            try { inventory.capacity(); }
            catch (ArithmeticException | IllegalStateException invalid) { return false; }
        }
        return nodes.stream().anyMatch(node -> node.entity == origin);
    }

    static MachineMatterNetwork discover(MachineBlockEntity origin) {
        if (!origin.networkLive() || !(origin.getLevel() instanceof ServerLevel level))
            return new MachineMatterNetwork(origin.networkOwner(), List.of(), Set.of(), List.of(),
                    "network_invalid", 0, List.of(), null);
        return new Scan(level, origin.networkOwner()).run(origin);
    }

    private record Edge(BlockPos first, BlockPos second) {
        static Edge of(BlockPos a, BlockPos b) { return a.compareTo(b) <= 0 ? new Edge(a, b) : new Edge(b, a); }
    }
    private record ChunkStamp(int x, int z, @Nullable WeakReference<LevelChunk> chunk, ChunkRevisions.Revision revision) {
        boolean matches(ServerLevel level) {
            var current = level.getChunkSource().getChunkNow(x, z);
            if (chunk == null) return current == null;
            return current != null && current == chunk.get() && revision.stable()
                    && revision.equals(ChunkRevisions.current(current));
        }
    }
    private record Node(BlockPos position, Object block, @Nullable BlockEntity entity,
                        @Nullable MachineBlockEntity controller, @Nullable Direction outward,
                        String structure, String localStatus, List<MatterInventory> inventories) {
        Node { inventories = List.copyOf(inventories); }
        boolean matches(ServerLevel level, UUID owner) {
            if (level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) == null
                    || level.getBlockState(position).getBlock() != block || level.getBlockEntity(position) != entity) return false;
            if (entity instanceof MachineBlockEntity machine) {
                if (!machine.networkLive() || !Objects.equals(owner, machine.networkOwner())) return false;
                if (machine.kind().isStorage() && (!localStatus.equals(machine.networkStorageStatus())
                        || !inventories.equals(machine.localMatterMembers()))) return false;
                if (IndustrialStructure.isIndustrial(machine.kind())) {
                    var formation = IndustrialStructure.inspect(machine);
                    return formation.formed() && structure.equals(formation.layoutFingerprint());
                }
            } else if (entity instanceof MachinePortBlockEntity port) {
                return !port.isRemoved() && port.acceptsMatter() && port.controller(null) == controller && port.outward() == outward;
            }
            return true;
        }
    }

    private static final class Scan {
        private final ServerLevel level;
        private final UUID owner;
        private final Map<Long, ChunkStamp> chunks = new LinkedHashMap<>();
        private final Map<BlockPos, Node> observed = new HashMap<>();
        private final Map<BlockPos, Node> connected = new HashMap<>();
        private final Set<Edge> edges = new HashSet<>();
        private boolean unloaded;
        private boolean tooLarge;

        Scan(ServerLevel level, UUID owner) { this.level = level; this.owner = owner; }

        MachineMatterNetwork run(MachineBlockEntity origin) {
            Node root = read(origin.getBlockPos());
            if (root == null) return result("network_invalid");
            ArrayDeque<Node> pending = new ArrayDeque<>();
            connected.put(root.position, root);
            pending.add(root);
            while (!pending.isEmpty() && !tooLarge) {
                Node node = pending.removeFirst();
                for (BlockPos position : neighbors(node)) {
                    Node next = read(position);
                    if (next == null || !acceptsFrom(next, node) || !acceptsFrom(node, next)) continue;
                    edges.add(Edge.of(node.position, next.position));
                    if (connected.containsKey(position)) continue;
                    if (connected.size() == MAX_NODES) { tooLarge = true; break; }
                    connected.put(position, next);
                    pending.addLast(next);
                }
            }
            if (tooLarge) return result("network_too_large");
            if (unloaded) return result("network_unloaded");
            return result("ready");
        }

        private MachineMatterNetwork result(String diagnostic) {
            List<Node> nodes = connected.values().stream().sorted(Comparator.comparing(Node::position)).toList();
            List<Node> storageNodes = nodes.stream().filter(node -> node.entity instanceof MachineBlockEntity machine && machine.kind().isStorage()).toList();
            List<MachineBlockEntity> stores = storageNodes.stream().filter(node -> !node.inventories.isEmpty())
                    .map(node -> (MachineBlockEntity)node.entity).toList();
            // The complete graph agrees on the same limit from every origin. Never
            // return overlapping partial inventories for different cable tiers.
            int cableLimit = nodes.stream().map(Node::block).filter(MatterPipeBlock.class::isInstance)
                    .mapToInt(block -> ((MatterPipeBlock) block).tier().networkLimit())
                    .min().orElse(MatterPipeBlock.Tier.BASIC.networkLimit());
            if (storageNodes.size() > MAX_STORAGES || nodes.size() > cableLimit) diagnostic = "network_too_large";
            if (diagnostic.equals("ready") && storageNodes.stream().anyMatch(node -> node.localStatus.equals("unknown_schema"))) diagnostic = "unknown_schema";
            if (diagnostic.equals("ready") && stores.isEmpty()) diagnostic = storageNodes.isEmpty() ? "no_storage" : "missing_cell";
            if (diagnostic.equals("ready") && !chunks.values().stream().allMatch(stamp -> stamp.matches(level))) diagnostic = "network_invalid";
            MatterNetworkInventory inventory = null;
            if (diagnostic.equals("ready")) {
                try { inventory = new MatterNetworkInventory(storageNodes.stream().flatMap(node -> node.inventories.stream()).toList()); }
                catch (IllegalArgumentException | ArithmeticException invalid) { diagnostic = "network_invalid"; }
            }
            // A failed/oversized observation never advertises a partial resource total.
            List<MachineBlockEntity> exposedStores = stores.size() <= MAX_STORAGES ? stores : List.of();
            return new MachineMatterNetwork(owner, nodes, edges, new ArrayList<>(chunks.values()), diagnostic,
                    Math.min(MAX_STORAGES, storageNodes.size()), exposedStores, inventory);
        }

        private @Nullable Node read(BlockPos position) {
            position = position.immutable();
            if (observed.containsKey(position)) return observed.get(position);
            int x = position.getX() >> 4, z = position.getZ() >> 4;
            long chunkKey = (long)x << 32 ^ (z & 0xffffffffL);
            LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
            chunks.computeIfAbsent(chunkKey, ignored -> new ChunkStamp(x, z,
                    chunk == null ? null : new WeakReference<>(chunk), chunk == null ? new ChunkRevisions.Revision(0, 0) : ChunkRevisions.current(chunk)));
            if (chunk == null) { unloaded = true; observed.put(position, null); return null; }
            var state = level.getBlockState(position);
            BlockEntity entity = level.getBlockEntity(position);
            Node result = null;
            if (state.getBlock() instanceof MatterPipeBlock) result = new Node(position, state.getBlock(), entity, null, null, "", "", List.of());
            else if (entity instanceof MachineBlockEntity machine && machine.networkLive() && Objects.equals(owner, machine.networkOwner())) {
                String structure = "";
                if (IndustrialStructure.isIndustrial(machine.kind())) {
                    var formation = IndustrialStructure.inspect(machine);
                    if (!formation.formed()) { observed.put(position, null); return null; }
                    structure = formation.layoutFingerprint();
                }
                String status = machine.kind().isStorage() ? machine.networkStorageStatus() : "";
                result = new Node(position, state.getBlock(), machine, null, null, structure, status,
                        machine.localMatterMembers());
            } else if (entity instanceof MachinePortBlockEntity port && port.acceptsMatter()) {
                var controller = port.controller(null); Direction outward = port.outward();
                if (controller != null && outward != null && Objects.equals(owner, controller.networkOwner()))
                    result = new Node(position, state.getBlock(), port, controller, outward, "", "", List.of());
            }
            observed.put(position, result);
            return result;
        }

        private List<BlockPos> neighbors(Node node) {
            if (node.entity instanceof MachinePortBlockEntity) return List.of(node.controller.getBlockPos(), node.position.relative(node.outward));
            if (node.entity instanceof MachineBlockEntity machine) {
                if (IndustrialStructure.isIndustrial(machine.kind()))
                    return IndustrialStructure.inspect(machine).ports().stream().map(IndustrialStructure.Port::position).toList();
                return java.util.Arrays.stream(Direction.values()).filter(side -> machine.acceptsMatter(side, false))
                        .map(node.position::relative).toList();
            }
            return java.util.Arrays.stream(Direction.values()).map(node.position::relative).toList();
        }
        private boolean acceptsFrom(Node target, Node source) {
            if (target.entity instanceof MachinePortBlockEntity)
                return source.position.equals(target.controller.getBlockPos()) || source.position.equals(target.position.relative(target.outward));
            if (target.entity instanceof MachineBlockEntity machine) {
                if (IndustrialStructure.isIndustrial(machine.kind()))
                    return source.entity instanceof MachinePortBlockEntity && source.controller == machine;
                for (Direction side : Direction.values()) if (target.position.relative(side).equals(source.position))
                    return machine.acceptsMatter(side, source.block instanceof MatterPipeBlock);
                return false;
            }
            return true;
        }
    }
}
