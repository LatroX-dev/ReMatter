package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.automation.OrderLedger;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.network.MachineProgressPayload;
import dev.latrox.rematter.network.MachineStockPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * A bounded, server-authoritative view of the open menu, sent only when values change. Stock rows and progress
 * travel in their own payloads, so a running batch never resends the stock. Clients translate every name from IDs.
 */
public record MachineSnapshot(int containerId, int kind, String status, int selected, String selectedId, int choiceCount,
                              int sampleBatchSize, long used, long capacity, boolean automation, boolean running,
                              int fluidAmount, String fluidId, boolean repeat, int queuedBatches, Preview preview,
                              String storageSource, StructureView structure, NetworkView network, boolean networkJobs,
                              String backend, OrdersView orders, List<CellView> cells) implements CustomPacketPayload {
    /** Version of the menu view payload family: this snapshot, stock changes and progress. */
    public static final String PROTOCOL = "19";
    /** Prefix of a preview ID that names the item being analyzed or decomposed. */
    public static final String ITEM_SOURCE = "item:";
    public static final int MAX_CHOICE_ID_LENGTH = 520;
    public static final int MAX_SUBSTANCE_ID_LENGTH = 512;
    public static final int MAX_ENTRIES = 512;
    public static final int MAX_PREVIEW_AMOUNTS = 64;
    public static final Type<MachineSnapshot> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "machine_snapshot"));
    public static final MachineSnapshot EMPTY = new MachineSnapshot(-1, MachineKind.ANALYZER.ordinal(), "idle", -1, "", 0, 1,
            0, 0, false, false, 0, "", false, 0, Preview.EMPTY, "none", StructureView.EMPTY, NetworkView.EMPTY, false, "", OrdersView.EMPTY, List.of());

    public MachineSnapshot {
        if (kind < 0 || kind >= MachineKind.values().length) throw new IllegalArgumentException("Invalid machine kind");
        cells = List.copyOf(cells);
        if (cells.size() != MachineCellAccess.bayCount(MachineKind.values()[kind])) throw new IllegalArgumentException("Invalid cell bay view");
        if (preview == null || structure == null || network == null || orders == null || status.isEmpty() || status.length() > 64
                || selectedId.length() > MAX_CHOICE_ID_LENGTH || choiceCount < 0 || choiceCount > MachineChoices.MAX_CHOICES
                || selected < -1 || selected >= choiceCount || sampleBatchSize < 1 || sampleBatchSize > 64
                || used < 0 || capacity < used || backend.length() > 32
                || queuedBatches < 0 || queuedBatches > MachineRunState.MAX_QUEUED_BATCHES || (repeat && queuedBatches != 0)
                || fluidAmount < 0 || fluidAmount > MachineBlockEntity.TANK_CAPACITY || fluidAmount == 0 != fluidId.isEmpty()
                || !Set.of("none", "local", "network").contains(storageSource)) throw new IllegalArgumentException("Invalid menu snapshot");
        Fields.validateOptionalId(fluidId);
        if (structure.formed()) {
            var machineKind = MachineKind.values()[kind];
            if (!IndustrialStructure.isIndustrial(machineKind) || structure.height() < IndustrialStructure.height(machineKind))
                throw new IllegalArgumentException("Structure does not match its controller");
            boolean preparation = machineKind == MachineKind.INDUSTRIAL_PREPARATOR;
            if (preparation ? structure.grinding() < 1 || structure.mixing() < 1 || structure.separation() < 1 || structure.coils() != 0 || structure.chambers() != 0
                    : structure.coils() < 1 || structure.chambers() < 1 || structure.grinding() != 0 || structure.mixing() != 0 || structure.separation() != 0)
                throw new IllegalArgumentException("Modules do not match their controller");
            int cores = preparation ? structure.grinding() : structure.chambers();
            var hardware = new IndustrialPerformance(structure.grinding(), structure.mixing(), structure.separation(), structure.coils(), structure.chambers());
            if (structure.moduleCount() != cores)
                throw new IllegalArgumentException("Structure bonuses do not match its modules");
            int base = hardware.workRate(machineKind);
            if (structure.workRate() % base != 0 || structure.workRate() / base > 5)
                throw new IllegalArgumentException("Structure work rate does not match its modules");
        }
    }

    /** Network totals never replace the local disk view or invent capacity for empty bays. */
    public record NetworkView(String status, int nodes, int storages, int cells, long used, long capacity, int types) {
        public static final int MAX_STORAGES = 64;
        public static final NetworkView EMPTY = new NetworkView("none", 0, 0, 0, 0, 0, 0);
        public NetworkView {
            if (!Set.of("none", "ready", "no_storage", "missing_cell", "unknown_schema", "network_too_large", "network_unloaded", "network_invalid").contains(status)
                    || nodes < 0 || nodes > MatterCableBlock.Tier.RESONANT.networkLimit() || storages < 0 || storages > MAX_STORAGES || storages > nodes
                    || cells < 0 || cells > storages * MachineCellAccess.DRIVE_BAYS
                    || used < 0 || capacity < used || types < 0
                    || !status.equals("ready") && (used != 0 || capacity != 0 || types != 0)
                    || status.equals("ready") && (storages == 0 || capacity == 0)) throw new IllegalArgumentException("Invalid network view");
        }
    }
    /**
     * A Network Interface's outdated and stopped orders. {@code held} names the oldest stopped delivery its owner may
     * release by hand, with what that order still holds; {@code complete} is false when the lists are cut.
     */
    public record OrdersView(int stale, int quarantined, String held, List<Amount> matter, List<Resource> resources, boolean complete) {
        public static final OrdersView EMPTY = new OrdersView(0, 0, "", List.of(), List.of(), true);
        public OrdersView {
            matter = List.copyOf(matter); resources = List.copyOf(resources);
            if (stale < 0 || stale > OrderLedger.MAX_ORDERS || quarantined < 0 || quarantined > OrderLedger.MAX_ORDERS
                    || matter.size() > MAX_PREVIEW_AMOUNTS || resources.size() > MAX_PREVIEW_AMOUNTS
                    || held.isEmpty() && (!matter.isEmpty() || !resources.isEmpty() || !complete)
                    || !held.isEmpty() && (quarantined == 0 || !UUID.fromString(held).toString().equals(held)))
                throw new IllegalArgumentException("Invalid order view");
        }
    }
    /** A detected but invalid shell can explain its failure without claiming production capacity. */
    public record StructureView(int width, int depth, int height, int workRate, int moduleCount, String diagnostic,
                                int grinding, int mixing, int separation, int coils, int chambers, int energySavingPercent) {
        public static final StructureView EMPTY = new StructureView(0, 0, 0, 0, 0, "structure", 0, 0, 0, 0, 0, 0);
        public StructureView {
            boolean noShape = width == 0 && depth == 0 && height == 0;
            int area = noShape ? 0 : (width - 2) * (depth - 2);
            int total = grinding + mixing + separation + coils + chambers;
            if (!IndustrialStructure.DIAGNOSTICS.contains(diagnostic)
                    || !noShape && (width < 3 || width > 9 || depth < 3 || depth > 9 || height < 3 || height > 9)
                    || moduleCount < 0 || moduleCount > 98 || workRate < 0 || workRate > 980
                    || grinding < 0 || grinding > 98 || mixing < 0 || mixing > 98 || separation < 0 || separation > 98
                    || coils < 0 || coils > 98 || chambers < 0 || chambers > 98
                    || energySavingPercent < 0 || energySavingPercent >= 25
                    || diagnostic.equals("ready") && (noShape || moduleCount < 1 || moduleCount > total || workRate < 2 || total > 2 * area)
                    || !diagnostic.equals("ready") && (moduleCount != 0 || workRate != 0 || total != 0 || energySavingPercent != 0))
                throw new IllegalArgumentException("Invalid structure view");
        }
        public boolean formed() { return diagnostic.equals("ready"); }
    }
    /** One Drive bay. The client names the cell from its item ID. */
    public record CellView(boolean installed, boolean readable, String itemId, long used, long capacity, int types, int maxTypes) {
        public static final CellView EMPTY = new CellView(false, false, "", 0, 0, 0, 0);
        public CellView {
            Fields.validateOptionalId(itemId);
            if (used < 0 || capacity < used || types < 0 || maxTypes < 0 || maxTypes > MatterCellData.MAX_TYPES || types > maxTypes
                    || !readable && (used != 0 || types != 0) || readable && !installed || installed == itemId.isEmpty()
                    || !installed && (capacity != 0 || maxTypes != 0)) throw new IllegalArgumentException("Invalid cell view");
        }
    }
    /** Sorting key of a stock row. */
    public record StockKey(String id, String phase) implements Comparable<StockKey> {
        @Override public int compareTo(StockKey other) {
            int byId = id.compareTo(other.id);
            return byId != 0 ? byId : phase.compareTo(other.phase);
        }
    }
    /** A stored substance. Unknown rows keep a removed definition's stock visible and are never processable. */
    public record Entry(String id, String phase, long amount, boolean known) {
        public Entry {
            Fields.validateSubstance(id, phase);
            if (amount < 0) throw new IllegalArgumentException("Invalid displayed stock");
        }
        public StockKey key() { return new StockKey(id, phase); }
    }
    public record Amount(String id, String phase, long required, long available, boolean known) {
        public Amount {
            Fields.validateSubstance(id, phase);
            if (required <= 0 || available < 0) throw new IllegalArgumentException("Invalid displayed requirement");
        }
    }
    /** An item or fluid that a network order consumes or holds besides matter. */
    public record Resource(String kind, String id, long amount) {
        public Resource {
            if (!Set.of("item", "fluid").contains(kind) || id.isEmpty() || amount <= 0) throw new IllegalArgumentException("Invalid displayed resource");
            Fields.validateOptionalId(id);
        }
    }
    public record Preview(String id, List<Amount> inputs, List<Amount> outputs, boolean complete, long energy, int ticks,
                          String outputKind, String outputId, int outputCount, List<Resource> resources) {
        public static final Preview EMPTY = new Preview("", List.of(), List.of(), true, 0, 0, "none", "", 0, List.of());
        public Preview {
            inputs = List.copyOf(inputs); outputs = List.copyOf(outputs); resources = List.copyOf(resources);
            if (id.length() > MAX_CHOICE_ID_LENGTH || outputId.length() > MAX_SUBSTANCE_ID_LENGTH
                    || inputs.size() > MAX_PREVIEW_AMOUNTS || outputs.size() > MAX_PREVIEW_AMOUNTS || resources.size() > MAX_PREVIEW_AMOUNTS
                    || energy < 0 || ticks < 0 || outputCount < 0 || outputCount > MachineBlockEntity.TANK_CAPACITY
                    || !Set.of("none", "item", "fluid", "matter", "analysis").contains(outputKind)) {
                throw new IllegalArgumentException("Invalid process preview");
            }
            Fields.validateOptionalId(outputId);
            if (outputKind.equals("item") && (outputCount < 1 || outputCount > 64 || outputId.isEmpty())) {
                throw new IllegalArgumentException("Invalid item preview");
            }
        }
    }
    /** Nested EMPTY values must not initialize the enclosing snapshot while their own initialization is in progress. */
    private static final class Fields {
        private Fields() {}
        static void validateSubstance(String id, String phase) {
            if (id.length() > MAX_SUBSTANCE_ID_LENGTH || !Set.of("solid", "liquid", "gas").contains(phase)) {
                throw new IllegalArgumentException("Invalid displayed substance");
            }
            SubstanceId.parse(id);
        }
        static void validateOptionalId(String id) {
            if (id.length() > MAX_SUBSTANCE_ID_LENGTH) throw new IllegalArgumentException("Identifier too long");
            if (!id.isEmpty()) SubstanceId.parse(id);
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, MachineSnapshot> CODEC = new StreamCodec<>() {
        @Override public MachineSnapshot decode(RegistryFriendlyByteBuf buffer) {
            int container = buffer.readVarInt();
            int kind = buffer.readVarInt();
            String status = buffer.readUtf(64);
            int selected = buffer.readVarInt();
            String selectedId = buffer.readUtf(MAX_CHOICE_ID_LENGTH);
            int choiceCount = count(buffer, MachineChoices.MAX_CHOICES);
            int sampleBatchSize = buffer.readVarInt();
            long used = buffer.readVarLong(); long capacity = buffer.readVarLong();
            boolean automation = buffer.readBoolean(); boolean running = buffer.readBoolean();
            int fluidAmount = buffer.readVarInt(); String fluidId = buffer.readUtf(MAX_SUBSTANCE_ID_LENGTH);
            boolean repeat = buffer.readBoolean(); int queued = buffer.readVarInt();
            Preview preview = new Preview(buffer.readUtf(MAX_CHOICE_ID_LENGTH), amounts(buffer), amounts(buffer), buffer.readBoolean(),
                    buffer.readVarLong(), buffer.readVarInt(), buffer.readUtf(16), buffer.readUtf(MAX_SUBSTANCE_ID_LENGTH), buffer.readVarInt(),
                    resources(buffer));
            String storageSource = buffer.readUtf(8);
            var structure = new StructureView(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readUtf(64), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
            var network = new NetworkView(buffer.readUtf(32), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarLong(), buffer.readVarLong(), buffer.readVarInt());
            boolean networkJobs = buffer.readBoolean();
            String backend = buffer.readUtf(32);
            var orders = new OrdersView(buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(36), amounts(buffer), resources(buffer), buffer.readBoolean());
            int bayCount = count(buffer, MachineCellAccess.DRIVE_BAYS); var cells = new ArrayList<CellView>(bayCount);
            for (int i = 0; i < bayCount; i++) cells.add(new CellView(buffer.readBoolean(), buffer.readBoolean(), buffer.readUtf(MAX_SUBSTANCE_ID_LENGTH),
                    buffer.readVarLong(), buffer.readVarLong(), buffer.readVarInt(), buffer.readVarInt()));
            return new MachineSnapshot(container, kind, status, selected, selectedId, choiceCount, sampleBatchSize, used, capacity,
                    automation, running, fluidAmount, fluidId, repeat, queued, preview, storageSource, structure, network, networkJobs, backend, orders, cells);
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, MachineSnapshot value) {
            buffer.writeVarInt(value.containerId); buffer.writeVarInt(value.kind); buffer.writeUtf(value.status, 64);
            buffer.writeVarInt(value.selected); buffer.writeUtf(value.selectedId, MAX_CHOICE_ID_LENGTH);
            buffer.writeVarInt(value.choiceCount); buffer.writeVarInt(value.sampleBatchSize);
            buffer.writeVarLong(value.used); buffer.writeVarLong(value.capacity);
            buffer.writeBoolean(value.automation); buffer.writeBoolean(value.running);
            buffer.writeVarInt(value.fluidAmount); buffer.writeUtf(value.fluidId, MAX_SUBSTANCE_ID_LENGTH);
            buffer.writeBoolean(value.repeat); buffer.writeVarInt(value.queuedBatches);
            Preview preview = value.preview;
            buffer.writeUtf(preview.id, MAX_CHOICE_ID_LENGTH);
            amounts(buffer, preview.inputs); amounts(buffer, preview.outputs); buffer.writeBoolean(preview.complete);
            buffer.writeVarLong(preview.energy); buffer.writeVarInt(preview.ticks); buffer.writeUtf(preview.outputKind, 16);
            buffer.writeUtf(preview.outputId, MAX_SUBSTANCE_ID_LENGTH); buffer.writeVarInt(preview.outputCount);
            resources(buffer, preview.resources);
            buffer.writeUtf(value.storageSource, 8);
            var structure = value.structure;
            buffer.writeVarInt(structure.width); buffer.writeVarInt(structure.depth); buffer.writeVarInt(structure.height);
            buffer.writeVarInt(structure.workRate); buffer.writeVarInt(structure.moduleCount); buffer.writeUtf(structure.diagnostic, 64);
            buffer.writeVarInt(structure.grinding); buffer.writeVarInt(structure.mixing); buffer.writeVarInt(structure.separation);
            buffer.writeVarInt(structure.coils); buffer.writeVarInt(structure.chambers); buffer.writeVarInt(structure.energySavingPercent);
            var network = value.network;
            buffer.writeUtf(network.status, 32); buffer.writeVarInt(network.nodes); buffer.writeVarInt(network.storages);
            buffer.writeVarInt(network.cells); buffer.writeVarLong(network.used); buffer.writeVarLong(network.capacity); buffer.writeVarInt(network.types);
            buffer.writeBoolean(value.networkJobs);
            buffer.writeUtf(value.backend, 32);
            var orders = value.orders;
            buffer.writeVarInt(orders.stale); buffer.writeVarInt(orders.quarantined); buffer.writeUtf(orders.held, 36);
            amounts(buffer, orders.matter); resources(buffer, orders.resources); buffer.writeBoolean(orders.complete);
            buffer.writeVarInt(value.cells.size());
            for (var bay : value.cells) {
                buffer.writeBoolean(bay.installed); buffer.writeBoolean(bay.readable); buffer.writeUtf(bay.itemId, MAX_SUBSTANCE_ID_LENGTH);
                buffer.writeVarLong(bay.used); buffer.writeVarLong(bay.capacity); buffer.writeVarInt(bay.types); buffer.writeVarInt(bay.maxTypes);
            }
        }
        private List<Amount> amounts(RegistryFriendlyByteBuf buffer) {
            int size = count(buffer, MAX_PREVIEW_AMOUNTS);
            List<Amount> values = new ArrayList<>(size);
            for (int i = 0; i < size; i++) values.add(new Amount(buffer.readUtf(MAX_SUBSTANCE_ID_LENGTH), buffer.readUtf(16),
                    buffer.readVarLong(), buffer.readVarLong(), buffer.readBoolean()));
            return values;
        }
        private void amounts(RegistryFriendlyByteBuf buffer, List<Amount> values) {
            buffer.writeVarInt(values.size());
            for (Amount value : values) {
                buffer.writeUtf(value.id, MAX_SUBSTANCE_ID_LENGTH); buffer.writeUtf(value.phase, 16);
                buffer.writeVarLong(value.required); buffer.writeVarLong(value.available); buffer.writeBoolean(value.known);
            }
        }
        private List<Resource> resources(RegistryFriendlyByteBuf buffer) {
            int size = count(buffer, MAX_PREVIEW_AMOUNTS);
            List<Resource> values = new ArrayList<>(size);
            for (int i = 0; i < size; i++) values.add(new Resource(buffer.readUtf(8), buffer.readUtf(MAX_SUBSTANCE_ID_LENGTH), buffer.readVarLong()));
            return values;
        }
        private void resources(RegistryFriendlyByteBuf buffer, List<Resource> values) {
            buffer.writeVarInt(values.size());
            for (var resource : values) {
                buffer.writeUtf(resource.kind, 8); buffer.writeUtf(resource.id, MAX_SUBSTANCE_ID_LENGTH); buffer.writeVarLong(resource.amount);
            }
        }
    };

    /** Bounded list length read; a negative or oversized count is rejected before any allocation. */
    public static int count(RegistryFriendlyByteBuf buffer, int maximum) {
        int value = buffer.readVarInt();
        if (value < 0 || value > maximum) throw new IllegalArgumentException("Invalid list size");
        return value;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** Registers the whole menu view family under one protocol version. */
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(PROTOCOL);
        registrar.playToClient(TYPE, CODEC, (snapshot, context) -> {
            if (context.player().containerMenu instanceof MachineMenu menu && menu.containerId == snapshot.containerId) menu.acceptSnapshot(snapshot);
        });
        registrar.playToClient(MachineStockPayload.TYPE, MachineStockPayload.CODEC, (stock, context) -> {
            if (context.player().containerMenu instanceof MachineMenu menu && menu.containerId == stock.containerId()) menu.acceptStock(stock);
        });
        registrar.playToClient(MachineProgressPayload.TYPE, MachineProgressPayload.CODEC, (progress, context) -> {
            if (context.player().containerMenu instanceof MachineMenu menu && menu.containerId == progress.containerId()) menu.acceptProgress(progress);
        });
    }
}
