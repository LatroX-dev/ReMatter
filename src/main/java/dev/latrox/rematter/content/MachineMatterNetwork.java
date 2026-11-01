package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.MatterInventory;
import dev.latrox.rematter.chemistry.MatterNetworkInventory;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * A bounded observation of real, loaded owners. The aggregate owns no resources;
 * every unit remains in exactly one installed cell or preserved legacy storage.
 */
final class MachineMatterNetwork {
    static final int MAX_NODES = 256;
    static final int MAX_STORAGES = 64;
    private final UUID owner;
    private final List<Node> nodes;
    private final Set<Edge> edges;
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
        this.chunks = List.copyOf(chunks);
        this.status = status;
        this.storageCount = storageCount;
        this.cellCount = (int) stores.stream().filter(MachineBlockEntity::networkUsesCell).count();
        this.stores = List.copyOf(stores);
        this.inventory = inventory;
    }

    String status() { return status; }
    boolean ready() { return status.equals("ready") && inventory != null; }
    @Nullable MatterNetworkInventory inventory() { return inventory; }
    void markChanged() { stores.forEach(MachineBlockEntity::setChanged); }
    List<MachineBlockEntity> workers() {
        return nodes.stream().map(Node::entity).filter(MachineBlockEntity.class::isInstance)
                .map(MachineBlockEntity.class::cast).filter(machine -> machine.kind().processes()).toList();
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
                        String structure, String localStatus, @Nullable MatterInventory inventory) {
        boolean matches(ServerLevel level, UUID owner) {
            if (level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) == null
                    || level.getBlockState(position).getBlock() != block || level.getBlockEntity(position) != entity) return false;
            if (entity instanceof MachineBlockEntity machine) {
                if (!machine.networkLive() || !Objects.equals(owner, machine.networkOwner())) return false;
                if (machine.kind().isStorage() && (!localStatus.equals(machine.networkStorageStatus())
                        || inventory != (localStatus.equals("ready") ? machine.matterInventory() : null))) return false;
                if (IndustrialStructure.isIndustrial(machine.kind())) {
                    var formation = IndustrialStructure.inspect(machine);
                    return formation.formed() && structure.equals(formation.layoutFingerprint());
                }
            } else if (entity instanceof MachinePortBlockEntity port) {
                return !port.isRemoved() && port.controller(null) == controller && port.outward() == outward;
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
                    if (next == null || !acceptsFrom(next, node)) continue;
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
            List<MachineBlockEntity> stores = storageNodes.stream().filter(node -> node.inventory != null)
                    .map(node -> (MachineBlockEntity)node.entity).toList();
            if (storageNodes.size() > MAX_STORAGES) diagnostic = "network_too_large";
            if (diagnostic.equals("ready") && storageNodes.stream().anyMatch(node -> node.localStatus.equals("unknown_schema"))) diagnostic = "unknown_schema";
            if (diagnostic.equals("ready") && stores.isEmpty()) diagnostic = storageNodes.isEmpty() ? "no_storage" : "missing_cell";
            if (diagnostic.equals("ready") && !chunks.values().stream().allMatch(stamp -> stamp.matches(level))) diagnostic = "network_invalid";
            MatterNetworkInventory inventory = null;
            if (diagnostic.equals("ready")) {
                try { inventory = new MatterNetworkInventory(stores.stream().map(MachineBlockEntity::matterInventory).toList()); }
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
            if (state.is(ModContent.MATTER_PIPE.get())) result = new Node(position, state.getBlock(), entity, null, null, "", "", null);
            else if (entity instanceof MachineBlockEntity machine && machine.networkLive() && Objects.equals(owner, machine.networkOwner())) {
                String structure = "";
                if (IndustrialStructure.isIndustrial(machine.kind())) {
                    var formation = IndustrialStructure.inspect(machine);
                    if (!formation.formed()) { observed.put(position, null); return null; }
                    structure = formation.layoutFingerprint();
                }
                String status = machine.kind().isStorage() ? machine.networkStorageStatus() : "";
                result = new Node(position, state.getBlock(), machine, null, null, structure, status,
                        status.equals("ready") ? machine.matterInventory() : null);
            } else if (entity instanceof MachinePortBlockEntity port) {
                var controller = port.controller(null); Direction outward = port.outward();
                if (controller != null && outward != null && Objects.equals(owner, controller.networkOwner()))
                    result = new Node(position, state.getBlock(), port, controller, outward, "", "", null);
            }
            observed.put(position, result);
            return result;
        }

        private List<BlockPos> neighbors(Node node) {
            if (node.entity instanceof MachinePortBlockEntity) return List.of(node.controller.getBlockPos(), node.position.relative(node.outward));
            if (node.entity instanceof MachineBlockEntity machine && IndustrialStructure.isIndustrial(machine.kind()))
                return IndustrialStructure.inspect(machine).ports().stream().map(IndustrialStructure.Port::position).toList();
            return java.util.Arrays.stream(Direction.values()).map(node.position::relative).toList();
        }
        private boolean acceptsFrom(Node target, Node source) {
            if (target.entity instanceof MachinePortBlockEntity)
                return source.position.equals(target.controller.getBlockPos()) || source.position.equals(target.position.relative(target.outward));
            if (target.entity instanceof MachineBlockEntity machine && IndustrialStructure.isIndustrial(machine.kind()))
                return source.entity instanceof MachinePortBlockEntity && source.controller == machine;
            return true;
        }
    }
}
