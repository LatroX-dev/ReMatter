package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.preparation.PreparationTier;
import dev.latrox.rematter.network.MachineProgressPayload;
import dev.latrox.rematter.network.MachineStockPayload;
import dev.latrox.rematter.network.TerminalDeviceActionPayload;
import dev.latrox.rematter.network.TerminalDevicesPayload;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

public final class MachineMenu extends AbstractContainerMenu {
    public static final int FIRST_PLAYER_SLOT = 14;
    public static final int PLAYER_SLOTS_X = 18;
    public static final int PLAYER_SLOTS_Y = 134;
    private static final int INPUT_SLOT_X = 25;
    private static final int SLOT_ROW_Y = 43;
    public static final int OPEN_TERMINAL_DEVICES = 10;
    public static final int RETURN_TO_TERMINAL = 11;
    public static final int CLOSE_TERMINAL_DEVICES = 12;
    public static final int CYCLE_REDSTONE = 13;
    public static final int TOGGLE_SOUND_MUTE = 14;
    public static final int CYCLE_SOUND_BACK = 15;
    public static final int TOGGLE_STRUCTURE_PREVIEW = 16;
    public static final int OPEN_TERMINAL_MONITOR = 17;
    public static final int CLOSE_TERMINAL_MONITOR = 18;
    public static final int CONSOLIDATE_DRIVE_CELLS = 19;
    public static final int TOGGLE_AUTO_EJECT = 20;
    /** Six ids step a side forward; the six after them step it backward. */
    public static final int CYCLE_PORT = 40;
    public static final int CYCLE_PORT_BACK = 46;
    public enum View { MAIN, MACHINES, MONITOR, CONFIGURATION, RESEARCH }
    private final Container inventory;
    private final @Nullable MachineBlockEntity machine;
    private final @Nullable TerminalAnchor terminal;
    private final boolean wirelessRoot;
    private final @Nullable MachinePortBlockEntity port;
    private final Player player;
    private final DataSlot remoteMenu = DataSlot.standalone();
    private final DataSlot wirelessTerminal = DataSlot.standalone();
    private final DataSlot lockedPlayerSlot = DataSlot.standalone();
    private final DataSlot redstoneMode = DataSlot.standalone();
    private final DataSlot soundVolume = DataSlot.standalone();
    private final DataSlot structurePreview = DataSlot.standalone();
    private final DataSlot autoEject = DataSlot.standalone();
    private final DataSlot[] portModes = new DataSlot[MachinePortMode.Side.values().length];
    private boolean wirelessBatteryDepleted;
    /** Server: the last views sent. Client: the last views received. */
    private MachineSnapshot snapshot = MachineSnapshot.EMPTY;
    private MachineProgressPayload progress = MachineProgressPayload.EMPTY;
    private final SyncedRows<MachineSnapshot.StockKey, MachineSnapshot.Entry> stockRows = new SyncedRows<>(MachineSnapshot.Entry::key);
    private final SyncedRows<BlockPos, TerminalDevicesPayload.Device> deviceRows = new SyncedRows<>(TerminalDevicesPayload.Device::position);
    private Stock stock = Stock.EMPTY;
    /** Server: the viewer's stock query, its cached rows and the rows last diffed against the client. */
    private StockQuery stockQuery = StockQuery.ALL;
    private final MachineSnapshots.StockView stockView = new MachineSnapshots.StockView();
    private MachineSnapshots.@Nullable Stock sentStock;
    private TerminalDevicesPayload.Listing terminalDevices = TerminalDevicesPayload.Listing.EMPTY;
    private dev.latrox.rematter.network.TerminalMonitorPayload terminalMonitor = dev.latrox.rematter.network.TerminalMonitorPayload.EMPTY;
    private dev.latrox.rematter.network.@Nullable TerminalMonitorPayload sentMonitor;
    private dev.latrox.rematter.network.TerminalBatchPlanPayload terminalBatchPlan = dev.latrox.rematter.network.TerminalBatchPlanPayload.EMPTY;
    public dev.latrox.rematter.network.TerminalBatchPlanPayload terminalBatchPlan() { return terminalBatchPlan; }
    public void acceptTerminalBatchPlan(dev.latrox.rematter.network.TerminalBatchPlanPayload value) { terminalBatchPlan = value; }
    private final NetworkRateTracker rateTracker = new NetworkRateTracker();
    private boolean stockSent, devicesSent;
    private boolean observingTerminal;
    private boolean observingMonitor;
    private long lastTerminalAction = Long.MIN_VALUE;
    private long nextSnapshot;
    private long nextStructureHint;
    private View view = View.MAIN;
    private com.google.gson.JsonObject extras=new com.google.gson.JsonObject();
    private String sentExtras="";
    private long nextExtras;
    public com.google.gson.JsonObject extras(){return extras;}
    public void acceptExtras(dev.latrox.rematter.network.MachineExtraPayload payload){extras=payload.data();}
    public boolean extendedAction(ServerPlayer user,dev.latrox.rematter.network.MachineActionPayload action){
        if(machine==null||user.containerMenu!=this||!stillValid(user)||!machine.canAccess(user)||user.isSpectator())return false;
        boolean changed=machine.extensions().action(user,action);nextExtras=0;nextStructureHint=0;nextSnapshot=0;broadcastChanges();return changed;
    }
    private dev.latrox.rematter.network.StructureHintPayload structureHint=dev.latrox.rematter.network.StructureHintPayload.EMPTY;
    public dev.latrox.rematter.network.StructureHintPayload structureHint() {return structureHint;}
    public void acceptStructureHint(dev.latrox.rematter.network.StructureHintPayload value) {structureHint=value;}
    private dev.latrox.rematter.network.MachineSettingsPayload settings=dev.latrox.rematter.network.MachineSettingsPayload.EMPTY;
    public dev.latrox.rematter.network.MachineSettingsPayload settings() {return settings;}
    public void acceptSettings(dev.latrox.rematter.network.MachineSettingsPayload value) {settings=value;}
    public boolean configure(ServerPlayer user,MachineSettings value) {
        if(machine==null||user.containerMenu!=this||!stillValid(user)||!machine.configure(user,value))return false;
        broadcastChanges();return true;
    }

    public MachineMenu(int id, Inventory inventory) { this(id, inventory, new SimpleContainer(FIRST_PLAYER_SLOT), null, null, false, null); }
    public MachineMenu(int id, Inventory inventory, net.minecraft.network.@Nullable RegistryFriendlyByteBuf buf) {
        this(id, inventory);
        if (buf == null || buf.readableBytes() <= 0) return;
        boolean initWireless = buf.readBoolean();
        if (initWireless) {
            wirelessTerminal.set(1);
            lockedPlayerSlot.set(buf.readVarInt());
            acceptSnapshot(withContainerId(MachineSnapshot.CODEC.decode(buf), id));
            return;
        }
        remoteMenu.set(buf.readBoolean() ? 1 : 0);
        lockedPlayerSlot.set(buf.readVarInt());
        redstoneMode.set(buf.readVarInt());
        soundVolume.set(buf.readVarInt());
        structurePreview.set(buf.readBoolean() ? 1 : 0);
        autoEject.set(buf.readBoolean() ? 1 : 0);
        for (var side : MachinePortMode.Side.values()) {
            portModes[side.ordinal()].set(buf.readVarInt());
        }
        var initSnapshot = withContainerId(MachineSnapshot.CODEC.decode(buf), id);
        if (buf.readBoolean()) {
            var hint = dev.latrox.rematter.network.StructureHintPayload.CODEC.decode(buf);
            structureHint = new dev.latrox.rematter.network.StructureHintPayload(
                    id, hint.position(), hint.expected(), hint.orientation(),
                    hint.entries(), hint.hasBounds(), hint.minBounds(), hint.maxBounds(), hint.showOverlay());
            String initExtras = buf.readUtf(dev.latrox.rematter.network.MachineExtraPayload.MAX_CHARS);
            extras = com.google.gson.JsonParser.parseString(initExtras).getAsJsonObject();
        }
        acceptSnapshot(initSnapshot);
    }
    public static void writeMachineInit(net.minecraft.network.RegistryFriendlyByteBuf buf, MachineBlockEntity machine, boolean remote, int lockedSlot) {
        buf.writeBoolean(false);
        buf.writeBoolean(remote);
        buf.writeVarInt(lockedSlot);
        buf.writeVarInt(machine.redstoneMode().ordinal());
        buf.writeVarInt(machine.soundLevel().level());
        buf.writeBoolean(machine.structurePreviewEnabled());
        buf.writeBoolean(machine.autoEject());
        for (var side : MachinePortMode.Side.values()) {
            buf.writeVarInt(machine.portMode(side).ordinal());
        }
        MachineSnapshot.CODEC.encode(buf, machine.snapshot(0));
        boolean industrial = IndustrialStructure.isIndustrial(machine.kind());
        buf.writeBoolean(industrial);
        if (industrial) {
            dev.latrox.rematter.network.StructureHintPayload.CODEC.encode(buf, IndustrialStructure.hintFor(0, machine));
            buf.writeUtf(machine.extensions().industrialInitView().toString(), dev.latrox.rematter.network.MachineExtraPayload.MAX_CHARS);
        }
    }
    public static void writeWirelessInit(net.minecraft.network.RegistryFriendlyByteBuf buf, TerminalAnchor.Wireless wireless) {
        buf.writeBoolean(true);
        buf.writeVarInt(wireless.lockedPlayerSlot());
        MachineSnapshot.CODEC.encode(buf, MachineSnapshots.buildWirelessTerminal(wireless.matterNetwork(false), 0));
    }
    private static MachineSnapshot withContainerId(MachineSnapshot s, int id) {
        return new MachineSnapshot(id, s.kind(), s.status(), s.selected(), s.selectedId(), s.choiceCount(),
                s.sampleBatchSize(), s.used(), s.capacity(), s.automation(), s.running(), s.fluidAmount(), s.fluidId(),
                s.repeat(), s.queuedBatches(), s.preview(), s.storageSource(), s.structure(), s.network(),
                s.networkJobs(), s.backend(), s.orders(), s.cells());
    }
    public MachineMenu(int id, Inventory inventory, MachineBlockEntity machine) { this(id, inventory, machine, machine, null, false, null); }
    MachineMenu(int id, Inventory inventory, TerminalAnchor.Wireless wireless) {
        this(id, inventory, new SimpleContainer(FIRST_PLAYER_SLOT), null, wireless, true, null);
    }
    MachineMenu(int id, Inventory inventory, MachineBlockEntity machine, MachineBlockEntity terminal) {
        this(id, inventory, machine, new TerminalAnchor.Wired(terminal));
    }
    MachineMenu(int id, Inventory inventory, MachineBlockEntity machine, TerminalAnchor terminal) {
        this(id, inventory, machine, machine, terminal, false, null);
    }
    /** A large shell's controller may be far away; such a menu stays valid at the port it was opened from. */
    MachineMenu(int id, Inventory inventory, MachineBlockEntity machine, MachinePortBlockEntity port) {
        this(id, inventory, machine, machine, null, false, port);
    }
    private MachineMenu(int id, Inventory playerInventory, Container inventory, @Nullable MachineBlockEntity machine,
                        @Nullable TerminalAnchor terminal, boolean wirelessRoot, @Nullable MachinePortBlockEntity port) {
        super(ModContent.MACHINE_MENU.get(), id);
        this.inventory = inventory;
        this.machine = machine;
        this.terminal = terminal;
        this.wirelessRoot = wirelessRoot;
        this.port = port;
        this.player = playerInventory.player;
        // Shared fixed client/server layout: role changes never replace slots or alter their inventory identity.
        addSlot(new Slot(inventory, 0, INPUT_SLOT_X, SLOT_ROW_Y) {
            @Override public boolean mayPlace(ItemStack stack) {
                return (!diskRole() || MachineCellAccess.isMountableBayCell(stack) && stack.getCount() == 1)
                        && acceptsPlacement(0, stack);
            }
            @Override public int getMaxStackSize() { return diskRole() ? 1 : super.getMaxStackSize(); }
            @Override public boolean mayPickup(Player player) { return machine == null || machine.canAccess(player) && (!diskRole() || machine.canRemoveCell(0)); }
            @Override public boolean isActive() { return view == View.MAIN && (diskRole() || inputRole() || hasItem()); }
        });
        addSlot(new Slot(inventory, 1, 135, SLOT_ROW_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return machine == null || machine.canAccess(player); }
            @Override public boolean isActive() { return view == View.MAIN && !diskRole() && (PreparationTier.isPreparation(kind()) || kind().synthesis() && !snapshot.preview().outputKind().equals("fluid") || hasItem()) || view == View.CONFIGURATION && diskRole() && hasItem(); }
        });
        addSlot(new Slot(inventory, 2, 171, 103) {
            @Override public boolean mayPlace(ItemStack stack) { return acceptsPlacement(2, stack); }
            @Override public int getMaxStackSize() { return 4; }
            @Override public int getMaxStackSize(ItemStack stack) { return 4; }
            @Override public boolean mayPickup(Player player) { return machine == null || machine.canAccess(player); }
            @Override public boolean isActive() { return view == View.MAIN && !diskRole() && (supportsUpgrades() || hasItem()) || view == View.CONFIGURATION && diskRole() && hasItem(); }
        });
        Container secondaryInventory = inventory.getContainerSize() > 3 ? inventory : new SimpleContainer(1);
        int secondaryIndex = inventory.getContainerSize() > 3 ? 3 : 0;
        addSlot(new Slot(secondaryInventory, secondaryIndex, 43, SLOT_ROW_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return PreparationTier.isPreparation(kind()) && acceptsPlacement(3, stack); }
            @Override public boolean mayPickup(Player player) { return machine == null || machine.canAccess(player); }
            @Override public boolean isActive() { return view == View.MAIN && !diskRole() && (mixingRole() || hasItem()) || view == View.CONFIGURATION && diskRole() && hasItem(); }
        });
        Container additiveInventory = inventory.getContainerSize() > 4 ? inventory : new SimpleContainer(1);
        int additiveIndex = inventory.getContainerSize() > 4 ? 4 : 0;
        addSlot(new Slot(additiveInventory, additiveIndex, 61, SLOT_ROW_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return mixingRole() && acceptsPlacement(4, stack); }
            @Override public boolean mayPickup(Player player) { return machine == null || machine.canAccess(player); }
            @Override public boolean isActive() { return view == View.MAIN && !diskRole() && (mixingRole() || hasItem()) || view == View.CONFIGURATION && diskRole() && hasItem(); }
        });
        // Every menu has the same slot identities before its first snapshot arrives.
        // The additional five slots are dormant on machines without a multi-cell Drive.
        Container extraCells = inventory;
        for(int bay=1;bay<MachineCellAccess.DRIVE_BAYS;bay++) {
            int index=MachineCellAccess.slotForBay(MachineKind.DRIVE,bay);
            addSlot(new Slot(extraCells, index, INPUT_SLOT_X + MachineCellAccess.driveColumn(bay) * 25,
                    SLOT_ROW_Y + MachineCellAccess.driveRow(bay) * 20) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return kind()==MachineKind.DRIVE && MachineCellAccess.isMountableBayCell(stack) && stack.getCount()==1
                            && extraCells.canPlaceItem(index,stack);
                }
                @Override public int getMaxStackSize() { return 1; }
                @Override public boolean mayPickup(Player player) { return machine==null || machine.canAccess(player) && machine.canRemoveCell(index); }
                @Override public boolean isActive() { return view==View.MAIN && kind()==MachineKind.DRIVE; }
            });
        }
        addSlot(serviceSlot(inventory,MachineCellAccess.EFFICIENCY_SLOT,195,83));
        addSlot(serviceSlot(inventory,MachineCellAccess.PARALLEL_SLOT,195,103));
        addSlot(serviceSlot(inventory,MachineCellAccess.PRIMAL_SLOT,195,43));
        addSlot(serviceSlot(inventory,MachineCellAccess.DISCOVERY_SLOT,195,63));
        for(int row=0;row<3;row++)for(int column=0;column<9;column++)
            addSlot(playerSlot(playerInventory,column+(row+1)*9,PLAYER_SLOTS_X+column*18,PLAYER_SLOTS_Y+row*18));
        for(int column=0;column<9;column++)
            addSlot(playerSlot(playerInventory,column,PLAYER_SLOTS_X+column*18,PLAYER_SLOTS_Y+58));
        remoteMenu.set(terminal != null && !wirelessRoot ? 1 : 0);
        addDataSlot(remoteMenu);
        wirelessTerminal.set(wirelessRoot ? 1 : 0);
        addDataSlot(wirelessTerminal);
        lockedPlayerSlot.set(terminal instanceof TerminalAnchor.Wireless w ? w.lockedPlayerSlot() : -1);
        addDataSlot(lockedPlayerSlot);
        if (machine != null) redstoneMode.set(machine.redstoneMode().ordinal());
        addDataSlot(redstoneMode);
        soundVolume.set(machine != null ? machine.soundLevel().level() : MachineSoundLevel.HIGH.level());
        addDataSlot(soundVolume);
        structurePreview.set(machine != null && machine.structurePreviewEnabled() ? 1 : 0);
        addDataSlot(structurePreview);
        autoEject.set(machine != null && machine.autoEject() ? 1 : 0);
        addDataSlot(autoEject);
        for (var side : MachinePortMode.Side.values()) {
            var mode = DataSlot.standalone();
            if (machine != null) mode.set(machine.portMode(side).ordinal());
            portModes[side.ordinal()] = addDataSlot(mode);
        }
        if (unformedIndustrial()) view = View.CONFIGURATION;
    }
    private Slot serviceSlot(Container container,int index,int x,int y) {
        return new Slot(container,index,x,y) {
            @Override public boolean mayPlace(ItemStack stack){return acceptsPlacement(index,stack);}
            @Override public boolean mayPickup(Player player){return machine==null||machine.canAccess(player);}
            @Override public int getMaxStackSize(){return index==MachineCellAccess.EFFICIENCY_SLOT?4:index==MachineCellAccess.PARALLEL_SLOT?(IndustrialStructure.isIndustrial(kind())?3:1):1;}
            @Override public boolean isActive(){return view==View.MAIN&&(index==MachineCellAccess.EFFICIENCY_SLOT?IndustrialStructure.isIndustrial(kind()):index==MachineCellAccess.PARALLEL_SLOT?IndustrialStructure.isIndustrial(kind())||kind().synthesis():index==MachineCellAccess.PRIMAL_SLOT?kind()==MachineKind.ANALYZER||kind()==MachineKind.PRIMAL_CONVERTER:kind()==MachineKind.ANALYZER)||view==View.CONFIGURATION&&diskRole()&&hasItem();}
        };
    }
    private Slot playerSlot(Inventory inventory,int containerSlot,int x,int y) {
        return new Slot(inventory,containerSlot,x,y) {
            @Override public boolean isActive() {return view==View.MAIN&&kind()!=MachineKind.TERMINAL;}
            @Override public boolean mayPickup(Player player) {return getContainerSlot() != lockedPlayerSlot.get() && super.mayPickup(player);}
            @Override public boolean mayPlace(ItemStack stack) {return getContainerSlot() != lockedPlayerSlot.get() && super.mayPlace(stack);}
        };
    }
    @Override public void clicked(int slotId, int button, ContainerInput input, Player player) {
        int locked = lockedPlayerSlot.get();
        if (locked >= 0) {
            if (input == ContainerInput.SWAP && button == locked) return;
            if (slotId >= 0 && slotId < slots.size()) {
                Slot target = slots.get(slotId);
                if (target.container == player.getInventory() && target.getContainerSlot() == locked) return;
            }
        }
        super.clicked(slotId, button, input, player);
    }
    public boolean wireless() { return wirelessTerminal.get() != 0; }
    public MachineKind kind() { return machine != null ? machine.kind() : wirelessRoot || wireless() ? MachineKind.TERMINAL : MachineKind.values()[snapshot.kind()]; }
    public boolean unformedIndustrial() {
        if (wireless()) return false;
        if (machine != null) return IndustrialStructure.isIndustrial(machine.kind()) && !IndustrialStructure.inspect(machine).formed();
        return snapshot.containerId() == containerId && IndustrialStructure.isIndustrial(kind()) && !snapshot.structure().formed();
    }
    public RedstoneMode redstoneMode() { return RedstoneMode.byIndex(redstoneMode.get()); }
    public MachineSoundLevel soundLevel() { return MachineSoundLevel.byLevel(soundVolume.get()); }
    public boolean soundMuted() { return soundLevel().muted(); }
    public boolean structurePreviewEnabled() { return structurePreview.get() != 0; }
    public boolean autoEject() { return autoEject.get() != 0; }
    private boolean acceptsPlacement(int slot, ItemStack stack) {
        if (machine != null) return inventory.canPlaceItem(slot, stack);
        if (snapshot.containerId() != containerId || snapshot.networkJobs() && slot != 2 && slot != MachineCellAccess.EFFICIENCY_SLOT && slot != MachineCellAccess.PARALLEL_SLOT) return false;
        if (diskRole() && slot == 0) return MachineCellAccess.isMountableBayCell(stack) && stack.getCount() == 1;
        if(slot==MachineCellAccess.EFFICIENCY_SLOT)return IndustrialStructure.isIndustrial(kind())&&stack.is(ModContent.EFFICIENCY_UPGRADE.get());
        if(slot==MachineCellAccess.PARALLEL_SLOT)return IndustrialStructure.isIndustrial(kind())&&stack.is(ModContent.PARALLEL_UPGRADE.get())||!IndustrialStructure.isIndustrial(kind())&&kind().synthesis()&&stack.is(ModContent.PRESSURE_MODULE.get());
        if(slot==MachineCellAccess.PRIMAL_SLOT)return (kind()==MachineKind.ANALYZER||kind()==MachineKind.PRIMAL_CONVERTER)&&stack.getItem() instanceof dev.latrox.rematter.primal.PrimalCellItem;
        if(slot==MachineCellAccess.DISCOVERY_SLOT)return kind()==MachineKind.ANALYZER&&stack.is(dev.latrox.rematter.primal.PrimalContent.DISCOVERY_LENS.get());
        if(slot==0&&(kind()==MachineKind.ANALYZER||kind()==MachineKind.PRIMAL_CONVERTER)&&(stack.getItem() instanceof dev.latrox.rematter.primal.PrimalCellItem||dev.latrox.rematter.primal.PrimalCells.sourceUnits(stack)>0))return true;
        if(slot==0&&kind().generator())return stack.getComponentsPatch().isEmpty()&&extras.has("fuelIds")&&extras.getAsJsonArray("fuelIds").asList().stream().anyMatch(value->value.getAsString().equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
        if(slot==0&&kind()==MachineKind.INDUSTRIAL_PREPARATOR&&extras.has("assay")&&extras.get("assay").getAsBoolean())return MachineOperations.profile(dev.latrox.rematter.network.ClientChemistry.current(),stack)!=null&&stack.getComponentsPatch().isEmpty();
        if((slot==3||slot==4)&&extras.has("recoveryMask")&&(extras.get("recoveryMask").getAsInt()&(1<<slot))!=0)return false;
        if (slot == 2) return MachineInputPolicy.acceptsUpgrade(kind(), stack);
        return MachineInputPolicy.accepts(kind(), slot, stack,
                new dev.latrox.rematter.content.preparation.PreparationInput(inventory.getItem(0), inventory.getItem(3), inventory.getItem(4)),
                dev.latrox.rematter.network.ClientChemistry.current(),
                dev.latrox.rematter.content.preparation.PreparationRecipes.clientRecipes().stream().filter(entry->kind()!=MachineKind.INDUSTRIAL_PREPARATOR||snapshot.structure().formed()&&entry.minPrecision()<=snapshot.structure().separation()).toList());
    }
    public MachinePortMode portMode(MachinePortMode.Side side) {
        return MachinePortMode.byIndex(portModes[side.ordinal()].get());
    }
    public boolean mixingRole() { return PreparationTier.isPreparation(kind()) && kind() != MachineKind.SINTER_FURNACE; }
    public boolean inputRole() { return kind().generator() || kind() == MachineKind.PRIMAL_CONVERTER || kind() == MachineKind.ANALYZER || kind().decomposition() || PreparationTier.isPreparation(kind()); }
    public boolean diskRole() { return kind().isStorage(); }
    public int diskMenuSlot(int bay) {
        if(bay<0 || bay>=diskBayCount())return -1;
        return MachineCellAccess.slotForBay(kind(),bay);
    }
    public int diskBayCount() { return diskRole()?MachineCellAccess.bayCount(kind()):0; }
    private boolean cellMenuSlot(int index) {
        return MachineCellAccess.isCellSlot(kind(),index);
    }
    public boolean supportsUpgrades() { return kind().processes()&&kind()!=MachineKind.PRIMAL_CONVERTER&&!PreparationTier.manual(kind()); }
    public View view() { return view; }
    public void setView(View value) {
        java.util.Objects.requireNonNull(value);
        view = unformedIndustrial() ? View.CONFIGURATION : value;
    }
    /** The client's stock rows in key order. {@code totalTypes} counts every stored type, shown or not. */
    public record Stock(int containerId, boolean readable, int totalTypes, List<MachineSnapshot.Entry> entries,int matchingTypes,int listedTypes,boolean approximate) {
        public Stock(int containerId,boolean readable,int totalTypes,List<MachineSnapshot.Entry> entries) {this(containerId,readable,totalTypes,entries,totalTypes,entries.size(),false);}
        public static final Stock EMPTY = new Stock(-1, false, 0, List.of());
        public Stock { entries = List.copyOf(entries); }
        public boolean complete() { return readable && entries.size() == totalTypes; }
        public boolean truncated() { return entries.size() < totalTypes; }
    }
    public MachineSnapshot snapshot() { return snapshot; }
    public MachineProgressPayload progress() { return progress; }
    public Stock stock() { return stock; }
    public void acceptSnapshot(MachineSnapshot value) {
        boolean wasUnformed = unformedIndustrial();
        snapshot = value;
        if (unformedIndustrial()) {
            view = View.CONFIGURATION;
        } else if (wasUnformed && view == View.CONFIGURATION) {
            view = View.MAIN;
        }
    }
    public void acceptProgress(MachineProgressPayload value) { progress = value; }
    public void acceptStock(MachineStockPayload value) {
        stockRows.apply(value.reset(), value.changed(), value.removed());
        if (stockRows.size() > MachineSnapshot.MAX_ENTRIES) stockRows.clear();
        stock = new Stock(value.containerId(), value.readable(), value.totalTypes(), stockRows.values(),value.matchingTypes(),value.listedTypes(),value.approximate());
    }
    public boolean remote() { return remoteMenu.get() != 0; }
    public TerminalDevicesPayload.Listing terminalDevices() { return terminalDevices; }
    public void acceptTerminalDevices(TerminalDevicesPayload value) {
        deviceRows.apply(value.reset(), value.changed(), value.removed());
        if (deviceRows.size() > TerminalDevicesPayload.MAX_DEVICES) deviceRows.clear();
        terminalDevices = new TerminalDevicesPayload.Listing(value.containerId(), value.total(), deviceRows.values());
    }
    public dev.latrox.rematter.network.TerminalMonitorPayload terminalMonitor() { return terminalMonitor; }
    public void acceptTerminalMonitor(dev.latrox.rematter.network.TerminalMonitorPayload value) { terminalMonitor = value; }
    public void resetTerminalMonitor() {
        sentMonitor = null;
        terminalMonitor = dev.latrox.rematter.network.TerminalMonitorPayload.EMPTY;
    }
    public boolean useTerminalCanister(ServerPlayer user, String substanceId, String phase, boolean drainToNetwork) {
        var anchor = activeTerminalAnchor();
        if (anchor == null || kind() != MachineKind.TERMINAL || user.containerMenu != this
                || !TerminalNetwork.isValidAnchor(anchor, user)) {
            return false;
        }
        dev.latrox.rematter.chemistry.SubstanceKey selected = null;
        if (!substanceId.isEmpty() && !phase.isEmpty()) {
            try {
                selected = dev.latrox.rematter.chemistry.SubstanceKey.of(
                        substanceId,
                        dev.latrox.rematter.chemistry.Phase.valueOf(phase.toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException invalid) {
                return false;
            }
        }
        var pulseOrigin = anchor instanceof TerminalAnchor.Wired wired ? wired.terminal() : null;
        int moved = PressurizedCanisterItem.transferInInventory(
                user.getInventory(), anchor.matterNetwork(true), pulseOrigin, selected, drainToNetwork);
        if (moved <= 0) return false;
        ModSounds.play(user.level(), user.blockPosition(), drainToNetwork ? ModSounds.CANISTER_EMPTY : ModSounds.CANISTER_FILL);
        nextSnapshot = 0;
        broadcastChanges();
        return true;
    }
    private @Nullable TerminalAnchor activeTerminalAnchor() {
        if (wirelessRoot && terminal != null) return terminal;
        if (machine != null && machine.kind() == MachineKind.TERMINAL && terminal == null) {
            return new TerminalAnchor.Wired(machine);
        }
        return null;
    }
    public boolean useTerminalDevice(ServerPlayer user, TerminalDeviceActionPayload request) {
        var anchor = activeTerminalAnchor();
        if (!observingTerminal || anchor == null || kind() != MachineKind.TERMINAL
                || user.containerMenu != this || request.containerId() != containerId
                || !TerminalNetwork.isValidAnchor(anchor, user)) return false;
        var shown = deviceRows.get(request.position());
        long tick = user.level().getGameTime();
        if (!TerminalDeviceRequests.permits(shown, request, tick, lastTerminalAction)) return false;
        lastTerminalAction = tick;
        var device = TerminalNetwork.resolve(anchor, user, request.position());
        if (device == null || device.kind() != shown.kind()) return false;
        if (request.action() == TerminalDeviceActionPayload.Action.OPEN) return TerminalNetwork.open(anchor, device, user);
        if (!device.queueFromTerminal(request.recipeId())) return false;
        nextSnapshot = 0;
        broadcastChanges();
        return true;
    }

    public boolean handleTerminalBatchRequest(
            ServerPlayer user, dev.latrox.rematter.network.TerminalBatchRequestPayload request) {
        if (user.containerMenu != this || request.containerId() != containerId || user.isSpectator() || !stillValid(user)) return false;
        var serverLevel = user.level();
        var anchor = activeTerminalAnchor();
        boolean pin = request.action() == dev.latrox.rematter.network.TerminalBatchRequestPayload.Action.PIN_PREVIEW;
        UUID owner;
        BlockPos anchorPos;
        MachineMatterNetwork network;
        if (pin) {
            if (machine == null || (kind() != MachineKind.DRIVE && kind() != MachineKind.REACTOR)
                    || !machine.canAccess(user)) return false;
            owner = machine.networkOwner();
            anchorPos = machine.getBlockPos();
            network = machine.matterNetwork(false);
        } else {
            if (anchor == null || kind() != MachineKind.TERMINAL || !TerminalNetwork.isValidAnchor(anchor, user)) return false;
            owner = switch (anchor) {
                case TerminalAnchor.Wired wired -> wired.terminal().networkOwner();
                case TerminalAnchor.Wireless wireless -> wireless.accessPoint().owner();
            };
            anchorPos = switch (anchor) {
                case TerminalAnchor.Wired wired -> wired.terminal().getBlockPos();
                case TerminalAnchor.Wireless wireless -> wireless.accessPoint().getBlockPos();
            };
            network = anchor.matterNetwork(true);
        }
        if (owner == null) return false;
        var chemistry = ReMatter.chemistry();
        if (request.action() == dev.latrox.rematter.network.TerminalBatchRequestPayload.Action.CANCEL) {
            TerminalJobCoordinator.cancel(serverLevel, owner, request.jobId());
        }

        TerminalBatchPlanner.PlanResult result;
        boolean targetResearchMissing = false;
        String sample = "";
        List<LabGoalMaterials.Row> materials = List.of();
        if (request.targetId().isEmpty() || request.batches() <= 0) {
            result = new TerminalBatchPlanner.PlanResult(
                    TerminalBatchPlanner.Status.FORBIDDEN_TARGET, List.of(), List.of(), List.of(), 0L, 0);
        } else {
            var ctx = TerminalJobCoordinator.buildNetworkContext(serverLevel, owner, user, network);
            result = TerminalBatchPlanner.plan(
                    chemistry, request.targetKind(), request.targetId(), request.phase(), request.batches(), ctx);
            if (result.status() == TerminalBatchPlanner.Status.REQUIRES_RESEARCH) {
                targetResearchMissing = switch (request.targetKind()) {
                    case SYNTHESIS_ITEM, SYNTHESIS_FLUID -> !ctx.allowsSynthesis().test(request.targetId());
                    case SUBSTANCE -> !ctx.knowsSubstance().test(dev.latrox.rematter.chemistry.SubstanceKey.of(request.targetId(),
                            dev.latrox.rematter.chemistry.Phase.valueOf(request.phase().toUpperCase(java.util.Locale.ROOT))));
                };
            }
            if (targetResearchMissing) {
                sample = LabGoalMaterials.researchSample(chemistry, dev.latrox.rematter.research.ResearchData.current(),
                        request.targetKind(), request.targetId(), request.phase(), ctx.knowsSubstance(),
                        id -> net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.Identifier.parse(id)));
            }
            if (pin) {
                materials = LabGoalMaterials.summarize(chemistry, result.steps(), request.targetKind(), request.targetId(),
                        request.phase(), request.batches(), ctx.stock());
            }
            if (request.action() == dev.latrox.rematter.network.TerminalBatchRequestPayload.Action.SUBMIT
                    && result.executable()) {
                int jobId = TerminalJobCoordinator.submit(
                        serverLevel, anchorPos, owner,
                        request.targetKind(), request.targetId(), request.phase(), request.batches(), result);
                if (jobId > 0) {
                    ModSounds.play(serverLevel, user.blockPosition(), ModSounds.TERMINAL_ORDER);
                    var refreshedCtx = TerminalJobCoordinator.buildNetworkContext(serverLevel, owner, user, anchor.matterNetwork(false));
                    result = TerminalBatchPlanner.plan(
                            chemistry, request.targetKind(), request.targetId(), request.phase(), request.batches(), refreshedCtx);
                }
            }
        }

        var missingRows = result.missingInputs().stream()
                .limit(dev.latrox.rematter.network.TerminalBatchPlanPayload.MAX_MISSING_ROWS)
                .map(m -> new dev.latrox.rematter.network.TerminalBatchPlanPayload.MissingRow(
                        m.key().id().toString(),
                        m.key().phase().name().toLowerCase(java.util.Locale.ROOT),
                        m.missing(),
                        m.available()))
                .toList();
        List<TerminalJobCoordinator.JobSummary> activeJobs = pin ? List.of() : TerminalJobCoordinator.jobsFor(serverLevel, owner);
        PacketDistributor.sendToPlayer(user, new dev.latrox.rematter.network.TerminalBatchPlanPayload(
                containerId,
                request.targetKind(),
                request.targetId(),
                request.phase(),
                request.batches(),
                result.status(),
                result.steps(),
                missingRows,
                result.missingMachines(),
                result.totalEnergy(),
                activeJobs,
                targetResearchMissing, sample,
                materials.subList(0, Math.min(materials.size(), dev.latrox.rematter.network.TerminalBatchPlanPayload.MAX_MATERIAL_ROWS)),
                materials.size()));
        if (!pin) {
            nextSnapshot = 0;
            broadcastChanges();
        }
        return true;
    }
    /** The next stock rows follow this query. It only decides which rows fill the limit, never whose stock the player sees. */
    public void acceptStockQuery(ServerPlayer user, StockQuery query) {
        if ((machine != null || wirelessRoot) && user.containerMenu == this) stockQuery = query;
    }
    /** The owner's confirmed release of a stopped delivery that this Network Interface still holds. */
    public boolean releaseHeldOrder(ServerPlayer user, UUID orderId) {
        if (machine == null || kind() != MachineKind.NETWORK_INTERFACE || user.containerMenu != this || !stillValid(user)
                || !machine.releaseHeldOrder(user, orderId)) return false;
        nextSnapshot = 0;
        broadcastChanges();
        return true;
    }
    public boolean selectRecipe(Player player, String id) {
        if (machine == null || player.isSpectator() || player.containerMenu != this || !stillValid(player)
                || id.length() > MachineSnapshot.MAX_CHOICE_ID_LENGTH || !machine.selectRecipe(id)) return false;
        nextSnapshot = 0;
        broadcastChanges();
        return true;
    }
    @Override public void broadcastChanges() {
        if (!stillValid(player)) return;
        if (machine != null) {
            redstoneMode.set(machine.redstoneMode().ordinal());
            soundVolume.set(machine.soundLevel().level());
            structurePreview.set(machine.structurePreviewEnabled() ? 1 : 0);
            autoEject.set(machine.autoEject() ? 1 : 0);
            for (var side : MachinePortMode.Side.values()) {
                portModes[side.ordinal()].set(machine.portMode(side).ordinal());
            }
        }
        super.broadcastChanges();
        if ((machine == null && !wirelessRoot) || !(player instanceof ServerPlayer serverPlayer)) return;
        long tick = player.level().getGameTime();
        if (tick < nextSnapshot) return;
        boolean initialSync = nextSnapshot == 0;
        nextSnapshot = tick + 5;
        if (!initialSync && terminal instanceof TerminalAnchor.Wireless wireless) {
            ItemStack held = wireless.resolveStack(player);
            if (!(held.getItem() instanceof WirelessMatterTerminalItem)
                    || !WirelessMatterTerminalItem.consumeEnergy(held, WirelessMatterTerminalItem.SESSION_INTERVAL_COST)) {
                if (held.getItem() instanceof WirelessMatterTerminalItem) {
                    WirelessMatterTerminalItem.setEnergy(held, 0);
                }
                wirelessBatteryDepleted = true;
                serverPlayer.closeContainer();
                return;
            }
        }
        try {
            sendViews(serverPlayer);
        } catch (IllegalArgumentException | ArithmeticException invalid) {
            // A view that breaks its own bounds closes this menu instead of the server tick.
            if (machine != null) {
                ReMatter.LOGGER.error("Closed the {} menu at {}: its view is invalid", machine.kind().id(), machine.getBlockPos(), invalid);
            } else {
                ReMatter.LOGGER.error("Closed the wireless terminal menu: its view is invalid", invalid);
            }
            serverPlayer.closeContainer();
        }
    }
    /** Sends each view only when it changed; stock rows and devices travel as keyed changes. */
    private void sendViews(ServerPlayer serverPlayer) {
        MachineSnapshots.Stock nextStock;
        MachineMatterNetwork terminalNetwork = null;
        if (wirelessRoot && terminal instanceof TerminalAnchor.Wireless wireless) {
            terminalNetwork = wireless.matterNetwork(false);
            var next = MachineSnapshots.buildWirelessTerminal(terminalNetwork, containerId);
            if (!snapshot.equals(next)) {
                snapshot = next;
                PacketDistributor.sendToPlayer(serverPlayer, next);
            }
            nextStock = stockView.refresh(terminalNetwork, stockQuery, MachineSnapshot.MAX_ENTRIES);
        } else if (machine != null) {
            if (machine.kind() == MachineKind.TERMINAL) {
                terminalNetwork = machine.matterNetwork(false);
            }
            var next = machine.snapshot(containerId);
            if (!snapshot.equals(next)) {
                snapshot = next;
                PacketDistributor.sendToPlayer(serverPlayer, next);
            }
            var nextProgress = machine.progress(containerId);
            if (!progress.equals(nextProgress)) {
                progress = nextProgress;
                PacketDistributor.sendToPlayer(serverPlayer, nextProgress);
            }
            if(IndustrialStructure.isIndustrial(machine.kind())&&serverPlayer.level().getGameTime()>=nextStructureHint) {
                nextStructureHint=serverPlayer.level().getGameTime()+10;
                var hint=IndustrialStructure.hintFor(containerId,machine);
                structureHint=hint;PacketDistributor.sendToPlayer(serverPlayer,hint);
            }
            var nextSettings=new dev.latrox.rematter.network.MachineSettingsPayload(containerId,machine.settings(),machine.ventedTotal(),machine.targetStock(),
                    machine.parallelRunning(),machine.parallelCapacity(),machine.crystallizationTier(),machine.canRemove(serverPlayer));
            if(!settings.equals(nextSettings)) {settings=nextSettings;PacketDistributor.sendToPlayer(serverPlayer,nextSettings);}
            if(serverPlayer.level().getGameTime()>=nextExtras){
                nextExtras=serverPlayer.level().getGameTime()+10;String json=machine.extensions().view().toString();
                if(!json.equals(sentExtras)){sentExtras=json;PacketDistributor.sendToPlayer(serverPlayer,new dev.latrox.rematter.network.MachineExtraPayload(containerId,json));}
            }
            nextStock = stockView.refresh(machine, stockQuery, MachineSnapshot.MAX_ENTRIES);
        } else {
            return;
        }
        if (nextStock != sentStock) {
            var stockChanges = stockRows.update(nextStock.entries());
            if (!stockSent || !stockChanges.isEmpty() || nextStock.readable() != stock.readable() || nextStock.totalTypes() != stock.totalTypes() || nextStock.matchingTypes()!=stock.matchingTypes()||nextStock.approximate()!=stock.approximate()) {
                PacketDistributor.sendToPlayer(serverPlayer, new MachineStockPayload(containerId, !stockSent, nextStock.readable(),
                        nextStock.totalTypes(), stockChanges.changed(), stockChanges.removed(),nextStock.matchingTypes(),nextStock.entries().size(),nextStock.approximate()));
                stock = new Stock(containerId, nextStock.readable(), nextStock.totalTypes(), List.of(),nextStock.matchingTypes(),nextStock.entries().size(),nextStock.approximate());
                stockSent = true;
            }
            sentStock = nextStock;
        }
        if (terminalNetwork != null) {
            long nowTick = serverPlayer.level().getGameTime();
            if (observingMonitor) {
                var nextMonitor = TerminalMonitorCollector.collect(terminalNetwork, rateTracker, nowTick, containerId);
                if (!nextMonitor.equals(sentMonitor)) {
                    sentMonitor = nextMonitor;
                    PacketDistributor.sendToPlayer(serverPlayer, nextMonitor);
                }
            } else {
                rateTracker.sampleRatesPerMinute(terminalNetwork.ready() ? terminalNetwork.inventory() : null, nowTick);
            }
        }
        if (!observingTerminal) return;
        var anchor = activeTerminalAnchor();
        if (anchor == null) return;
        var devices = TerminalNetwork.devices(anchor, player, false);
        var deviceChanges = deviceRows.update(devices.stream().limit(TerminalDevicesPayload.MAX_DEVICES).map(MachineBlockEntity::terminalDevice).toList());
        if (!devicesSent || !deviceChanges.isEmpty() || devices.size() != terminalDevices.total()) {
            PacketDistributor.sendToPlayer(serverPlayer, new TerminalDevicesPayload(containerId, !devicesSent, devices.size(),
                    deviceChanges.changed(), deviceChanges.removed()));
            terminalDevices = new TerminalDevicesPayload.Listing(containerId, devices.size(), List.of());
            devicesSent = true;
        }
    }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (player.containerMenu != this || !stillValid(player)) return false;
        switch (id) {
            case OPEN_TERMINAL_DEVICES -> {
                if (kind() != MachineKind.TERMINAL || activeTerminalAnchor() == null) return false;
                observingTerminal = true;
                observingMonitor = false;
                resetTerminalDevices();
                resetTerminalMonitor();
                nextSnapshot = 0;
                broadcastChanges();
                return true;
            }
            case CLOSE_TERMINAL_DEVICES -> {
                if (kind() != MachineKind.TERMINAL) return false;
                observingTerminal = false;
                resetTerminalDevices();
                nextSnapshot = 0;
                broadcastChanges();
                return true;
            }
            case OPEN_TERMINAL_MONITOR -> {
                if (kind() != MachineKind.TERMINAL || activeTerminalAnchor() == null) return false;
                observingMonitor = true;
                observingTerminal = false;
                resetTerminalDevices();
                resetTerminalMonitor();
                nextSnapshot = 0;
                broadcastChanges();
                return true;
            }
            case CLOSE_TERMINAL_MONITOR -> {
                if (kind() != MachineKind.TERMINAL) return false;
                observingMonitor = false;
                resetTerminalMonitor();
                nextSnapshot = 0;
                broadcastChanges();
                return true;
            }
            case RETURN_TO_TERMINAL -> {
                return remote() && terminal != null && player instanceof ServerPlayer serverPlayer
                        && TerminalNetwork.openRoot(terminal, serverPlayer);
            }
            default -> {}
        }
        if (machine == null) return false;
        int sideIndex = id - CYCLE_PORT;
        if (sideIndex >= 0 && sideIndex < 2 * portModes.length) {
            // The server decides: a side that is not a connection, the front above all, refuses every request.
            boolean forward = sideIndex < portModes.length;
            if (!machine.cyclePort(MachinePortMode.Side.values()[sideIndex % portModes.length], forward)) return false;
            broadcastChanges();
            return true;
        }
        switch (id) {
            case 0 -> machine.select(-1);
            case 1 -> machine.select(1);
            case 2 -> machine.toggleRunning();
            case 3 -> machine.toggleAutomation();
            case 4 -> machine.requestBatch();
            case 5 -> machine.toggleRepeat();
            case 6 -> { if (!machine.crank(player, terminal)) return false; }
            case 8 -> { if (!machine.toggleNetworkJobs()) return false; }
            case 9 -> { if (!machine.cycleAutomationBackend()) return false; }
            case CYCLE_REDSTONE -> { if (!machine.cycleRedstoneMode()) return false; }
            case TOGGLE_SOUND_MUTE -> { if (!machine.cycleSoundLevel(true)) return false; }
            case CYCLE_SOUND_BACK -> { if (!machine.cycleSoundLevel(false)) return false; }
            case TOGGLE_STRUCTURE_PREVIEW -> {
                if (!machine.toggleStructurePreview()) return false;
                nextStructureHint = 0;
                nextExtras = 0;
            }
            case CONSOLIDATE_DRIVE_CELLS -> {
                if (!machine.consolidateCells(player)) return false;
            }
            case TOGGLE_AUTO_EJECT -> {
                if (!machine.toggleAutoEject()) return false;
            }
            default -> { return false; }
        }
        nextSnapshot = 0;
        broadcastChanges();
        return true;
    }
    /** Forgets the device list on either side, so the next list arrives as a reset. */
    public void resetTerminalDevices() {
        deviceRows.clear();
        devicesSent = false;
        terminalDevices = TerminalDevicesPayload.Listing.EMPTY;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        if(cellMenuSlot(index)&&machine!=null&&!machine.prepareCellRemoval(index))return ItemStack.EMPTY;
        ItemStack original = slot.getItem();
        ItemStack result = original.copy();
        if (index < FIRST_PLAYER_SLOT) {
            if (!moveItemStackTo(original, FIRST_PLAYER_SLOT, slots.size(), true)) return ItemStack.EMPTY;
        } else if(original.getItem() instanceof dev.latrox.rematter.primal.PrimalCellItem&&(kind()==MachineKind.ANALYZER||kind()==MachineKind.PRIMAL_CONVERTER)){
            if(!slots.get(MachineCellAccess.PRIMAL_SLOT).mayPlace(original)||!moveItemStackTo(original,MachineCellAccess.PRIMAL_SLOT,MachineCellAccess.PRIMAL_SLOT+1,false))return ItemStack.EMPTY;
        } else if(original.is(dev.latrox.rematter.primal.PrimalContent.DISCOVERY_LENS.get())){
            if(!slots.get(MachineCellAccess.DISCOVERY_SLOT).mayPlace(original)||!moveItemStackTo(original,MachineCellAccess.DISCOVERY_SLOT,MachineCellAccess.DISCOVERY_SLOT+1,false))return ItemStack.EMPTY;
        } else if (MachineCellAccess.isMountableBayCell(original)) {
            boolean moved=false;
            for(int bay=0;bay<diskBayCount();bay++) {
                int target=diskMenuSlot(bay);
                if(slots.get(target).mayPlace(original) && moveItemStackTo(original,target,target+1,false)){moved=true;break;}
            }
            if(!moved)return ItemStack.EMPTY;
        } else if (original.is(ModContent.SPEED_UPGRADE.get()) || original.is(ModContent.EFFICIENCY_UPGRADE.get()) || original.is(ModContent.PARALLEL_UPGRADE.get()) || original.is(ModContent.PRESSURE_MODULE.get())) {
            int target=IndustrialStructure.isIndustrial(kind())?(original.is(ModContent.EFFICIENCY_UPGRADE.get())?MachineCellAccess.EFFICIENCY_SLOT:original.is(ModContent.PARALLEL_UPGRADE.get())?MachineCellAccess.PARALLEL_SLOT:2)
                    :kind().synthesis()&&original.is(ModContent.PRESSURE_MODULE.get())?MachineCellAccess.PRESSURE_SLOT:2;
            if(!supportsUpgrades()||!slots.get(target).mayPlace(original)||!moveItemStackTo(original,target,target+1,false))return ItemStack.EMPTY;
        } else if (PreparationTier.isPreparation(kind())) {
            if (!(slots.get(0).mayPlace(original) && moveItemStackTo(original, 0, 1, false))
                    && !(slots.get(3).mayPlace(original) && moveItemStackTo(original, 3, 4, false))
                    && !(slots.get(4).mayPlace(original) && moveItemStackTo(original, 4, 5, false))) return ItemStack.EMPTY;
        } else if (!slots.get(0).mayPlace(original) || !moveItemStackTo(original, 0, 1, false)) return ItemStack.EMPTY;
        if (original.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        if (original.getCount() == result.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, original);
        return result;
    }
    @Override public boolean stillValid(Player player) {
        if (wirelessBatteryDepleted) return false;
        if (wirelessRoot) return terminal != null && TerminalNetwork.isValidAnchor(terminal, player);
        if (machine == null) return true;
        if (terminal != null) return TerminalNetwork.canManage(terminal, machine, player);
        if (port != null) return machine.canAccess(player) && Container.stillValidBlockEntity(port, player) && port.controller(null) == machine;
        return machine.stillValid(player);
    }
}
