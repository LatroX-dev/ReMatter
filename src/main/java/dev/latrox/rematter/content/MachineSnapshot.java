package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.SubstanceId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** A bounded, server-authoritative view of the open menu, sent only when values change. */
public record MachineSnapshot(int containerId, int kind, int energy, int progress, int duration, String status,
                              int selected, String selectedId, String selectedName, int choiceCount, int sampleBatchSize,
                              List<Entry> entries, boolean entriesComplete, long used, long capacity,
                              boolean automation, boolean running, int fluidAmount, String fluidName,
                              boolean repeat, int queuedBatches, Preview preview, CellView cell, String storageSource,
                              StructureView structure, NetworkView network, boolean networkJobs, List<CellView> cells) implements CustomPacketPayload {
    public static final int MAX_CHOICE_ID_LENGTH = 520;
    public static final int MAX_SUBSTANCE_ID_LENGTH = 512;
    public static final int MAX_ENTRIES = 512;
    public static final int MAX_PREVIEW_AMOUNTS = 64;
    public static final Type<MachineSnapshot> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "machine_snapshot"));
    public static final MachineSnapshot EMPTY = new MachineSnapshot(-1, 0, 0, 0, 0, "idle", -1, "", "", 0, 1,
            List.of(), true, 0, 0, false, false, 0, "", false, 0, Preview.EMPTY, CellView.EMPTY);
    public MachineSnapshot {
        selectedName = Fields.clipped(selectedName, 256);
        status = Fields.clipped(status, 64);
        fluidName = Fields.clipped(fluidName, 64);
        entries = List.copyOf(entries);
        cells = List.copyOf(cells);
        if(kind < 0 || kind >= MachineKind.values().length
                || cells.size() != MachineCellAccess.bayCount(MachineKind.values()[kind])
                || !(cells.isEmpty() ? CellView.EMPTY : cells.getFirst()).equals(cell))throw new IllegalArgumentException("Invalid cell bay view");
        if (preview == null || cell == null || structure == null || network == null || selectedId.length() > MAX_CHOICE_ID_LENGTH || choiceCount < 0 || choiceCount > MachineChoices.MAX_CHOICES
                || selected < -1 || selected >= choiceCount
                || sampleBatchSize < 1 || sampleBatchSize > 64 || entries.size() > MAX_ENTRIES
                || kind < 0 || kind >= MachineKind.values().length || energy < 0 || energy > MachineBlockEntity.ENERGY_CAPACITY
                || progress < 0 || duration < 0 || progress > duration || used < 0 || capacity < used
                || queuedBatches < 0 || queuedBatches > MachineRunState.MAX_QUEUED_BATCHES || (repeat && queuedBatches != 0)
                || fluidAmount < 0 || fluidAmount > 16_000
                || !Set.of("none", "local", "network").contains(storageSource)) throw new IllegalArgumentException("Invalid menu snapshot");
        if (structure.formed()) {
            var machineKind=MachineKind.values()[kind];
            if(!IndustrialStructure.isIndustrial(machineKind) || structure.height()<IndustrialStructure.height(machineKind))
                throw new IllegalArgumentException("Structure does not match its controller");
            boolean preparation = machineKind == MachineKind.INDUSTRIAL_PREPARATOR;
            if (preparation ? structure.grinding()<1 || structure.mixing()<1 || structure.separation()<1 || structure.coils()!=0 || structure.chambers()!=0
                    : structure.coils()<1 || structure.chambers()<1 || structure.grinding()!=0 || structure.mixing()!=0 || structure.separation()!=0)
                throw new IllegalArgumentException("Modules do not match their controller");
            int cores = preparation ? structure.grinding() : structure.chambers();
            var hardware = new IndustrialPerformance(structure.grinding(),structure.mixing(),structure.separation(),structure.coils(),structure.chambers());
            if (structure.moduleCount()!=cores || structure.energySavingPercent()!=hardware.energySavingPercent(machineKind))
                throw new IllegalArgumentException("Structure bonuses do not match its modules");
            int base = hardware.workRate(machineKind);
            if(structure.workRate()%base!=0 || structure.workRate()/base>5)
                throw new IllegalArgumentException("Structure work rate does not match its modules");
        }
    }
    /** Previous callers retain a primary-cell view; Drive bays are explicit in the full constructor. */
    public MachineSnapshot(int containerId, int kind, int energy, int progress, int duration, String status,
                           int selected, String selectedId, String selectedName, int choiceCount, int sampleBatchSize,
                           List<Entry> entries, boolean entriesComplete, long used, long capacity,
                           boolean automation, boolean running, int fluidAmount, String fluidName,
                           boolean repeat, int queuedBatches, Preview preview, CellView cell, String storageSource,
                           StructureView structure, NetworkView network, boolean networkJobs) {
        this(containerId,kind,energy,progress,duration,status,selected,selectedId,selectedName,choiceCount,sampleBatchSize,
                entries,entriesComplete,used,capacity,automation,running,fluidAmount,fluidName,repeat,queuedBatches,preview,cell,
                storageSource,structure,network,networkJobs,primaryCells(kind,cell));
    }
    private static List<CellView> primaryCells(int kind,CellView cell) {
        if(kind<0 || kind>=MachineKind.values().length)return List.of();
        var machine=MachineKind.values()[kind];
        if(machine.isStorage()) {
            var result=new ArrayList<CellView>(MachineCellAccess.DRIVE_BAYS);result.add(cell);
            for(int i=1;i<MachineCellAccess.DRIVE_BAYS;i++)result.add(CellView.EMPTY);
            return result;
        }
        return List.of();
    }
    public MachineSnapshot(int containerId, int kind, int energy, int progress, int duration, String status,
                           int selected, String selectedId, String selectedName, int choiceCount, int sampleBatchSize,
                           List<Entry> entries, boolean entriesComplete, long used, long capacity,
                           boolean automation, boolean running, int fluidAmount, String fluidName,
                           boolean repeat, int queuedBatches, Preview preview, CellView cell, String storageSource,
                           StructureView structure) {
        this(containerId,kind,energy,progress,duration,status,selected,selectedId,selectedName,choiceCount,sampleBatchSize,
                entries,entriesComplete,used,capacity,automation,running,fluidAmount,fluidName,repeat,queuedBatches,preview,cell,storageSource,structure,NetworkView.EMPTY,false);
    }
    public MachineSnapshot(int containerId, int kind, int energy, int progress, int duration, String status,
                           int selected, String selectedId, String selectedName, int choiceCount, int sampleBatchSize,
                           List<Entry> entries, boolean entriesComplete, long used, long capacity,
                           boolean automation, boolean running, int fluidAmount, String fluidName,
                           boolean repeat, int queuedBatches, Preview preview, CellView cell, String storageSource,
                           StructureView structure, NetworkView network) {
        this(containerId,kind,energy,progress,duration,status,selected,selectedId,selectedName,choiceCount,sampleBatchSize,
                entries,entriesComplete,used,capacity,automation,running,fluidAmount,fluidName,repeat,queuedBatches,preview,cell,storageSource,structure,network,false);
    }
    /** Network totals never replace the local disk view or invent capacity for empty bays. */
    public record NetworkView(String status, int nodes, int storages, int cells, long used, long capacity, int types) {
        public static final NetworkView EMPTY = new NetworkView("none",0,0,0,0,0,0);
        public NetworkView {
            if (!Set.of("none","ready","no_storage","missing_cell","unknown_schema","network_too_large","network_unloaded","network_invalid").contains(status)
                    || nodes<0 || nodes>MachineMatterNetwork.MAX_NODES || storages<0 || storages>64 || storages>nodes || cells<0 || cells>384 || cells>storages*6
                    || used<0 || capacity<used || types<0
                    || !status.equals("ready") && (used!=0 || capacity!=0 || types!=0)
                    || status.equals("ready") && (storages==0 || capacity==0)) throw new IllegalArgumentException("Invalid network view");
        }
    }
    /** Callers without an industrial structure retain the same menu contract. */
    public MachineSnapshot(int containerId, int kind, int energy, int progress, int duration, String status,
                           int selected, String selectedId, String selectedName, int choiceCount, int sampleBatchSize,
                           List<Entry> entries, boolean entriesComplete, long used, long capacity,
                           boolean automation, boolean running, int fluidAmount, String fluidName,
                           boolean repeat, int queuedBatches, Preview preview, CellView cell, String storageSource) {
        this(containerId,kind,energy,progress,duration,status,selected,selectedId,selectedName,choiceCount,sampleBatchSize,
                entries,entriesComplete,used,capacity,automation,running,fluidAmount,fluidName,repeat,queuedBatches,preview,cell,storageSource,StructureView.EMPTY);
    }
    public MachineSnapshot(int containerId, int kind, int energy, int progress, int duration, String status,
                           int selected, String selectedId, String selectedName, int choiceCount, int sampleBatchSize,
                           List<Entry> entries, boolean entriesComplete, long used, long capacity,
                           boolean automation, boolean running, int fluidAmount, String fluidName,
                           boolean repeat, int queuedBatches, Preview preview, CellView cell) {
        this(containerId,kind,energy,progress,duration,status,selected,selectedId,selectedName,choiceCount,sampleBatchSize,
                entries,entriesComplete,used,capacity,automation,running,fluidAmount,fluidName,repeat,queuedBatches,preview,cell,"none");
    }
    /** Compatibility constructor for callers with no cell role. */
    public MachineSnapshot(int containerId, int kind, int energy, int progress, int duration, String status,
                           int selected, String selectedId, String selectedName, int choiceCount, int sampleBatchSize,
                           List<Entry> entries, boolean entriesComplete, long used, long capacity,
                           boolean automation, boolean running, int fluidAmount, String fluidName,
                           boolean repeat, int queuedBatches, Preview preview) {
        this(containerId,kind,energy,progress,duration,status,selected,selectedId,selectedName,choiceCount,sampleBatchSize,
                entries,entriesComplete,used,capacity,automation,running,fluidAmount,fluidName,repeat,queuedBatches,preview,CellView.EMPTY);
    }
    /** A detected but invalid shell can explain its failure without claiming production capacity. */
    public record StructureView(int width, int depth, int height, int workRate, int moduleCount, String diagnostic,
                                int grinding, int mixing, int separation, int coils, int chambers, int energySavingPercent) {
        public static final StructureView EMPTY = new StructureView(0,0,0,0,0,"structure");
        /** Detected shell with no active modules. */
        public StructureView(int width, int depth, int height, int workRate, int moduleCount, String diagnostic) {
            this(width,depth,height,workRate,moduleCount,diagnostic,0,0,0,0,0,0);
        }
        public StructureView {
            boolean noShape=width==0&&depth==0&&height==0;
            int area=noShape?0:(width-2)*(depth-2);
            int total=grinding+mixing+separation+coils+chambers;
            if(!IndustrialStructure.DIAGNOSTICS.contains(diagnostic)
                    || !noShape&&(width<3||width>9||depth<3||depth>9||height<3||height>9)
                    || moduleCount<0||moduleCount>98||workRate<0||workRate>980
                    || grinding<0||grinding>98||mixing<0||mixing>98||separation<0||separation>98||coils<0||coils>98||chambers<0||chambers>98
                    || energySavingPercent<0||energySavingPercent>=25
                    || diagnostic.equals("ready")&&(noShape||moduleCount<1||moduleCount>total||workRate<2||total>2*area)
                    || !diagnostic.equals("ready")&&(moduleCount!=0||workRate!=0||total!=0||energySavingPercent!=0))throw new IllegalArgumentException("Invalid structure view");
        }
        public boolean formed() { return diagnostic.equals("ready"); }
        public int volume() { return formed() ? (width - 2) * (depth - 2) * (height - 2) : 0; }
    }
    public record CellView(boolean installed, boolean readable, String name, long capacity, int types, int maxTypes) {
        public static final CellView EMPTY = new CellView(false,false,"",0,0,0);
        public CellView {
            name=Fields.clipped(name,256);
            if(capacity<0 || types<0 || maxTypes<0 || maxTypes>MatterCellData.MAX_TYPES || types>maxTypes
                    || readable&&!installed)throw new IllegalArgumentException("Invalid cell view");
        }
    }
    public record Entry(String id, String name, String phase, long amount, long available) {
        public Entry {
            Fields.validateSubstance(id, phase);
            name = Fields.clipped(name, 256);
            if (amount < 0 || available < 0 || available > amount) throw new IllegalArgumentException("Invalid displayed stock");
        }
    }
    public record Amount(String id, String name, String phase, long required, long available) {
        public Amount {
            Fields.validateSubstance(id, phase);
            name = Fields.clipped(name, 256);
            if (required <= 0 || available < 0) throw new IllegalArgumentException("Invalid displayed requirement");
        }
    }
    public record Preview(String id, String name, List<Amount> inputs, List<Amount> outputs, boolean complete,
                          long energy, int ticks, String outputKind, String outputId, int outputCount) {
        public static final Preview EMPTY = new Preview("", "", List.of(), List.of(), true, 0, 0, "none", "", 0);
        public Preview {
            inputs = List.copyOf(inputs); outputs = List.copyOf(outputs); name = Fields.clipped(name, 256);
            if (id.length() > MAX_CHOICE_ID_LENGTH || outputId.length() > MAX_SUBSTANCE_ID_LENGTH
                    || inputs.size() > MAX_PREVIEW_AMOUNTS || outputs.size() > MAX_PREVIEW_AMOUNTS
                    || energy < 0 || ticks < 0 || outputCount < 0 || outputCount > 16_000
                    || !Set.of("none", "item", "fluid", "matter", "analysis").contains(outputKind)) {
                throw new IllegalArgumentException("Invalid process preview");
            }
            if (!outputId.isEmpty()) SubstanceId.parse(outputId);
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
        static String clipped(String value, int maximum) { return value.length() > maximum ? value.substring(0, maximum) : value; }
    }
    public static final StreamCodec<RegistryFriendlyByteBuf, MachineSnapshot> CODEC = new StreamCodec<>() {
        @Override public MachineSnapshot decode(RegistryFriendlyByteBuf buffer) {
            int container = buffer.readVarInt();
            int kind = buffer.readVarInt();
            int energy = buffer.readVarInt();
            int progress = buffer.readVarInt();
            int duration = buffer.readVarInt();
            String status = buffer.readUtf(64);
            int selected = buffer.readVarInt();
            String selectedId = buffer.readUtf(MAX_CHOICE_ID_LENGTH);
            String selectedName = buffer.readUtf(256);
            int choiceCount = count(buffer, MachineChoices.MAX_CHOICES);
            int sampleBatchSize = buffer.readVarInt();
            int entryCount = count(buffer, MAX_ENTRIES);
            List<Entry> entries = new ArrayList<>(entryCount);
            for (int i = 0; i < entryCount; i++) entries.add(new Entry(buffer.readUtf(MAX_SUBSTANCE_ID_LENGTH), buffer.readUtf(256),
                    buffer.readUtf(16), buffer.readVarLong(), buffer.readVarLong()));
            boolean entriesComplete = buffer.readBoolean();
            long used = buffer.readVarLong(); long capacity = buffer.readVarLong();
            boolean automation = buffer.readBoolean(); boolean running = buffer.readBoolean();
            int fluidAmount = buffer.readVarInt(); String fluidName = buffer.readUtf(64);
            boolean repeat = buffer.readBoolean(); int queued = buffer.readVarInt();
            Preview preview = new Preview(buffer.readUtf(MAX_CHOICE_ID_LENGTH), buffer.readUtf(256), amounts(buffer), amounts(buffer),
                    buffer.readBoolean(), buffer.readVarLong(), buffer.readVarInt(), buffer.readUtf(16), buffer.readUtf(MAX_SUBSTANCE_ID_LENGTH), buffer.readVarInt());
            CellView cell=new CellView(buffer.readBoolean(),buffer.readBoolean(),buffer.readUtf(256),buffer.readVarLong(),buffer.readVarInt(),buffer.readVarInt());
            String storageSource = buffer.readUtf(8);
            var structure = new StructureView(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),buffer.readVarInt(),buffer.readUtf(64),
                    buffer.readVarInt(),buffer.readVarInt(),buffer.readVarInt(),buffer.readVarInt(),buffer.readVarInt(),buffer.readVarInt());
            var network = new NetworkView(buffer.readUtf(32),buffer.readVarInt(),buffer.readVarInt(),buffer.readVarInt(),buffer.readVarLong(),buffer.readVarLong(),buffer.readVarInt());
            boolean networkJobs = buffer.readBoolean();
            int bayCount=count(buffer,6);var cells=new ArrayList<CellView>(bayCount);
            for(int i=0;i<bayCount;i++)cells.add(new CellView(buffer.readBoolean(),buffer.readBoolean(),buffer.readUtf(256),buffer.readVarLong(),buffer.readVarInt(),buffer.readVarInt()));
            return new MachineSnapshot(container, kind, energy, progress, duration, status, selected, selectedId, selectedName, choiceCount, sampleBatchSize,
                    entries, entriesComplete, used, capacity, automation, running, fluidAmount, fluidName, repeat, queued, preview,cell,storageSource,structure,network,networkJobs,cells);
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, MachineSnapshot value) {
            buffer.writeVarInt(value.containerId); buffer.writeVarInt(value.kind); buffer.writeVarInt(value.energy);
            buffer.writeVarInt(value.progress); buffer.writeVarInt(value.duration); buffer.writeUtf(value.status, 64);
            buffer.writeVarInt(value.selected); buffer.writeUtf(value.selectedId, MAX_CHOICE_ID_LENGTH);
            buffer.writeUtf(value.selectedName, 256); buffer.writeVarInt(value.choiceCount); buffer.writeVarInt(value.sampleBatchSize);
            buffer.writeVarInt(value.entries.size());
            for (Entry entry : value.entries) {
                buffer.writeUtf(entry.id, MAX_SUBSTANCE_ID_LENGTH); buffer.writeUtf(entry.name, 256); buffer.writeUtf(entry.phase, 16);
                buffer.writeVarLong(entry.amount); buffer.writeVarLong(entry.available);
            }
            buffer.writeBoolean(value.entriesComplete); buffer.writeVarLong(value.used); buffer.writeVarLong(value.capacity);
            buffer.writeBoolean(value.automation); buffer.writeBoolean(value.running);
            buffer.writeVarInt(value.fluidAmount); buffer.writeUtf(value.fluidName, 64);
            buffer.writeBoolean(value.repeat); buffer.writeVarInt(value.queuedBatches);
            Preview preview = value.preview;
            buffer.writeUtf(preview.id, MAX_CHOICE_ID_LENGTH); buffer.writeUtf(preview.name, 256);
            amounts(buffer, preview.inputs); amounts(buffer, preview.outputs); buffer.writeBoolean(preview.complete);
            buffer.writeVarLong(preview.energy); buffer.writeVarInt(preview.ticks); buffer.writeUtf(preview.outputKind, 16);
            buffer.writeUtf(preview.outputId, MAX_SUBSTANCE_ID_LENGTH); buffer.writeVarInt(preview.outputCount);
            buffer.writeBoolean(value.cell.installed);buffer.writeBoolean(value.cell.readable);buffer.writeUtf(value.cell.name,256);
            buffer.writeVarLong(value.cell.capacity);buffer.writeVarInt(value.cell.types);buffer.writeVarInt(value.cell.maxTypes);
            buffer.writeUtf(value.storageSource,8);
            buffer.writeVarInt(value.structure.width); buffer.writeVarInt(value.structure.depth);
            buffer.writeVarInt(value.structure.height); buffer.writeVarInt(value.structure.workRate);
            buffer.writeVarInt(value.structure.moduleCount);buffer.writeUtf(value.structure.diagnostic,64);
            buffer.writeVarInt(value.structure.grinding);buffer.writeVarInt(value.structure.mixing);buffer.writeVarInt(value.structure.separation);
            buffer.writeVarInt(value.structure.coils);buffer.writeVarInt(value.structure.chambers);buffer.writeVarInt(value.structure.energySavingPercent);
            buffer.writeUtf(value.network.status,32);buffer.writeVarInt(value.network.nodes);buffer.writeVarInt(value.network.storages);
            buffer.writeVarInt(value.network.cells);buffer.writeVarLong(value.network.used);buffer.writeVarLong(value.network.capacity);
            buffer.writeVarInt(value.network.types);buffer.writeBoolean(value.networkJobs);
            buffer.writeVarInt(value.cells.size());
            for(var bay:value.cells) {
                buffer.writeBoolean(bay.installed);buffer.writeBoolean(bay.readable);buffer.writeUtf(bay.name,256);
                buffer.writeVarLong(bay.capacity);buffer.writeVarInt(bay.types);buffer.writeVarInt(bay.maxTypes);
            }
        }
        private List<Amount> amounts(RegistryFriendlyByteBuf buffer) {
            int size = count(buffer, MAX_PREVIEW_AMOUNTS);
            List<Amount> values = new ArrayList<>(size);
            for (int i = 0; i < size; i++) values.add(new Amount(buffer.readUtf(MAX_SUBSTANCE_ID_LENGTH), buffer.readUtf(256),
                    buffer.readUtf(16), buffer.readVarLong(), buffer.readVarLong()));
            return values;
        }
        private void amounts(RegistryFriendlyByteBuf buffer, List<Amount> values) {
            buffer.writeVarInt(values.size());
            for (Amount value : values) {
                buffer.writeUtf(value.id, MAX_SUBSTANCE_ID_LENGTH); buffer.writeUtf(value.name, 256); buffer.writeUtf(value.phase, 16);
                buffer.writeVarLong(value.required); buffer.writeVarLong(value.available);
            }
        }
        private int count(RegistryFriendlyByteBuf buffer, int maximum) {
            int value = buffer.readVarInt();
            if (value < 0 || value > maximum) throw new IllegalArgumentException("Invalid snapshot list size");
            return value;
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("13").playToClient(TYPE, CODEC, (snapshot, context) -> {
            if (context.player().containerMenu instanceof MachineMenu menu && menu.containerId == snapshot.containerId) menu.acceptSnapshot(snapshot);
        });
    }
}
