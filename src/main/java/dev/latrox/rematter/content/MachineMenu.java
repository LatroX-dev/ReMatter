package dev.latrox.rematter.content;

import dev.latrox.rematter.content.preparation.PreparationTier;
import dev.latrox.rematter.network.TerminalDeviceActionPayload;
import dev.latrox.rematter.network.TerminalDevicesPayload;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class MachineMenu extends AbstractContainerMenu {
    public static final int FIRST_PLAYER_SLOT = 10;
    public static final int PLAYER_SLOTS_X = 67;
    public static final int PLAYER_SLOTS_Y = 162;
    private static final int INPUT_SLOT_X = 45;
    private static final int SLOT_ROW_Y = 84;
    public static final int OPEN_TERMINAL_DEVICES = 10;
    public static final int RETURN_TO_TERMINAL = 11;
    public static final int CLOSE_TERMINAL_DEVICES = 12;
    public static final int CYCLE_PORT = 40;
    public enum View { MAIN, UPGRADES, CONFIGURATION }
    private final Container inventory;
    private final MachineBlockEntity machine;
    private final MachineBlockEntity terminal;
    private final Player player;
    private final DataSlot remoteMenu = DataSlot.standalone();
    private final DataSlot[] portModes = new DataSlot[MachinePortMode.Side.values().length];
    private MachineSnapshot snapshot = MachineSnapshot.EMPTY;
    private TerminalDevicesPayload terminalDevices = TerminalDevicesPayload.EMPTY;
    private boolean observingTerminal;
    private long lastTerminalAction = Long.MIN_VALUE;
    private long nextSnapshot;
    private View view = View.MAIN;

    public MachineMenu(int id, Inventory inventory) { this(id, inventory, new SimpleContainer(FIRST_PLAYER_SLOT), null, null); }
    public MachineMenu(int id, Inventory inventory, MachineBlockEntity machine) { this(id, inventory, machine, null); }
    MachineMenu(int id, Inventory inventory, MachineBlockEntity machine, MachineBlockEntity terminal) {
        this(id, inventory, machine, machine, terminal);
    }
    private MachineMenu(int id, Inventory playerInventory, Container inventory, MachineBlockEntity machine, MachineBlockEntity terminal) {
        super(ModContent.MACHINE_MENU.get(), id);
        this.inventory = inventory;
        this.machine = machine;
        this.terminal = terminal;
        this.player = playerInventory.player;
        // Shared fixed client/server layout: role changes never replace slots or alter their inventory identity.
        addSlot(new Slot(inventory, 0, INPUT_SLOT_X, SLOT_ROW_Y) {
            @Override public boolean mayPlace(ItemStack stack) {
                return (!diskRole() || stack.getItem() instanceof MatterCellItem && stack.getCount() == 1)
                        && inventory.canPlaceItem(0, stack);
            }
            @Override public int getMaxStackSize() { return diskRole() ? 1 : super.getMaxStackSize(); }
            @Override public boolean mayPickup(Player player) { return machine == null || machine.canAccess(player) && (!diskRole() || machine.canRemoveCell(0)); }
            @Override public boolean isActive() { return view == View.MAIN && (diskRole() || inputRole()) || view == View.CONFIGURATION && !diskRole() && hasItem(); }
        });
        addSlot(new Slot(inventory, 1, 226, SLOT_ROW_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return machine == null || machine.canAccess(player); }
            @Override public boolean isActive() { return view == View.MAIN && (PreparationTier.isPreparation(kind()) || kind() == MachineKind.SYNTHESIZER && (!snapshot.preview().outputKind().equals("fluid") || hasItem())) || view == View.CONFIGURATION && hasItem(); }
        });
        addSlot(new Slot(inventory, 2, 45, 124) {
            @Override public boolean mayPlace(ItemStack stack) { return inventory.canPlaceItem(2, stack); }
            @Override public int getMaxStackSize() { return 4; }
            @Override public boolean mayPickup(Player player) { return machine == null || machine.canAccess(player); }
            @Override public boolean isActive() { return view == View.UPGRADES && supportsUpgrades() || view == View.CONFIGURATION && !supportsUpgrades() && hasItem(); }
        });
        Container secondaryInventory = inventory.getContainerSize() > 3 ? inventory : new SimpleContainer(1);
        int secondaryIndex = inventory.getContainerSize() > 3 ? 3 : 0;
        addSlot(new Slot(secondaryInventory, secondaryIndex, 89, SLOT_ROW_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return PreparationTier.isPreparation(kind()) && secondaryInventory.canPlaceItem(secondaryIndex,stack); }
            @Override public boolean mayPickup(Player player) { return machine == null || machine.canAccess(player); }
            @Override public boolean isActive() { return view == View.MAIN && mixingRole() || view == View.CONFIGURATION && hasItem(); }
        });
        Container additiveInventory = inventory.getContainerSize() > 4 ? inventory : new SimpleContainer(1);
        int additiveIndex = inventory.getContainerSize() > 4 ? 4 : 0;
        addSlot(new Slot(additiveInventory, additiveIndex, 133, SLOT_ROW_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return mixingRole() && additiveInventory.canPlaceItem(additiveIndex, stack); }
            @Override public boolean mayPickup(Player player) { return machine == null || machine.canAccess(player); }
            @Override public boolean isActive() { return view == View.MAIN && mixingRole() || view == View.CONFIGURATION && hasItem(); }
        });
        // Every menu has the same slot identities before its first snapshot arrives.
        // The additional five slots are dormant on machines without a multi-cell Drive.
        Container extraCells = inventory.getContainerSize() >= FIRST_PLAYER_SLOT ? inventory : new SimpleContainer(FIRST_PLAYER_SLOT);
        for(int bay=1;bay<MachineCellAccess.DRIVE_BAYS;bay++) {
            int index=MachineCellAccess.slotForBay(MachineKind.DRIVE,bay);
            addSlot(new Slot(extraCells, index, INPUT_SLOT_X + MachineCellAccess.driveColumn(bay) * 25,
                    SLOT_ROW_Y + MachineCellAccess.driveRow(bay) * 20) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return kind()==MachineKind.DRIVE && stack.getItem() instanceof MatterCellItem && stack.getCount()==1
                            && extraCells.canPlaceItem(index,stack);
                }
                @Override public int getMaxStackSize() { return 1; }
                @Override public boolean mayPickup(Player player) { return machine==null || machine.canAccess(player) && machine.canRemoveCell(index); }
                @Override public boolean isActive() { return view==View.MAIN && kind()==MachineKind.DRIVE; }
            });
        }
        addStandardInventorySlots(playerInventory, PLAYER_SLOTS_X, PLAYER_SLOTS_Y);
        remoteMenu.set(terminal == null ? 0 : 1);
        addDataSlot(remoteMenu);
        for (var side : MachinePortMode.Side.values()) {
            var mode = DataSlot.standalone();
            if (machine != null) mode.set(machine.portMode(side).ordinal());
            portModes[side.ordinal()] = addDataSlot(mode);
        }
    }
    public MachineKind kind() { return machine == null ? MachineKind.values()[snapshot.kind()] : machine.kind(); }
    public MachinePortMode portMode(MachinePortMode.Side side) {
        return MachinePortMode.byIndex(portModes[side.ordinal()].get());
    }
    public boolean mixingRole() { return PreparationTier.isPreparation(kind()) && kind() != MachineKind.SINTER_FURNACE; }
    public boolean inputRole() { return kind() == MachineKind.ANALYZER || kind() == MachineKind.DECOMPOSER || PreparationTier.isPreparation(kind()); }
    public boolean diskRole() { return kind().isStorage(); }
    public int diskSlot() { return diskRole() ? 0 : -1; }
    public int diskMenuSlot(int bay) {
        if(bay<0 || bay>=diskBayCount())return -1;
        return MachineCellAccess.slotForBay(kind(),bay);
    }
    public int diskBayCount() { return diskRole()?MachineCellAccess.bayCount(kind()):0; }
    private boolean cellMenuSlot(int index) {
        return MachineCellAccess.isCellSlot(kind(),index);
    }
    public boolean supportsUpgrades() { return kind().processes() && !PreparationTier.manual(kind()); }
    public View view() { return view; }
    public void setView(View value) { view = java.util.Objects.requireNonNull(value); }
    public void setSettingsOpen(boolean value) { setView(value ? View.CONFIGURATION : View.MAIN); }
    public boolean settingsOpen() { return view != View.MAIN; }
    public MachineSnapshot snapshot() { return snapshot; }
    public void acceptSnapshot(MachineSnapshot value) { snapshot = value; }
    public boolean remote() { return remoteMenu.get() != 0; }
    public TerminalDevicesPayload terminalDevices() { return terminalDevices; }
    public void acceptTerminalDevices(TerminalDevicesPayload value) { terminalDevices = value; }
    public boolean useTerminalDevice(ServerPlayer user, TerminalDeviceActionPayload request) {
        if (!observingTerminal || machine == null || kind() != MachineKind.TERMINAL
                || user.containerMenu != this || request.containerId() != containerId
                || !TerminalNetwork.isValidAnchor(machine, user)) return false;
        var shown = terminalDevices.devices().stream()
                .filter(device -> device.position().equals(request.position())).findFirst().orElse(null);
        if (shown == null) return false;
        if (request.action() == TerminalDeviceActionPayload.Action.QUEUE_BATCH
                && (!shown.canQueue() || !shown.recipeId().equals(request.recipeId()))) return false;
        long tick = user.level().getGameTime();
        if (lastTerminalAction == tick) return false;
        lastTerminalAction = tick;
        var device = TerminalNetwork.resolve(machine, user, request.position());
        if (device == null || device.kind() != shown.kind()) return false;
        if (request.action() == TerminalDeviceActionPayload.Action.OPEN) return TerminalNetwork.open(machine, device, user);
        if (!device.queueFromTerminal(request.recipeId())) return false;
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
        if (terminal != null && !stillValid(player)) return;
        if (machine != null) for (var side : MachinePortMode.Side.values()) {
            portModes[side.ordinal()].set(machine.portMode(side).ordinal());
        }
        super.broadcastChanges();
        if (machine == null || !(player instanceof ServerPlayer serverPlayer)) return;
        long tick = player.level().getGameTime();
        if (tick < nextSnapshot) return;
        nextSnapshot = tick + 5;
        MachineSnapshot next = machine.snapshot(containerId);
        if (!snapshot.equals(next)) {
            snapshot = next;
            PacketDistributor.sendToPlayer(serverPlayer, next);
        }
        if (observingTerminal) {
            var devices = TerminalNetwork.devices(machine, player, false).stream().map(MachineBlockEntity::terminalDevice).toList();
            var list = new TerminalDevicesPayload(containerId, devices);
            if (!terminalDevices.equals(list)) {
                terminalDevices = list;
                PacketDistributor.sendToPlayer(serverPlayer, list);
            }
        }
    }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (machine == null || player.containerMenu != this || !stillValid(player)) return false;
        int sideIndex = id - CYCLE_PORT;
        if (sideIndex >= 0 && sideIndex < portModes.length) {
            if (!machine.cyclePort(MachinePortMode.Side.values()[sideIndex])) return false;
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
            case OPEN_TERMINAL_DEVICES -> {
                if (kind() != MachineKind.TERMINAL || terminal != null) return false;
                observingTerminal = true;
                terminalDevices = TerminalDevicesPayload.EMPTY;
            }
            case RETURN_TO_TERMINAL -> {
                return terminal != null && TerminalNetwork.isValidAnchor(terminal, player)
                        && player.openMenu(terminal).isPresent();
            }
            case CLOSE_TERMINAL_DEVICES -> {
                if (kind() != MachineKind.TERMINAL) return false;
                observingTerminal = false;
                terminalDevices = TerminalDevicesPayload.EMPTY;
            }
            default -> { return false; }
        }
        nextSnapshot = 0;
        broadcastChanges();
        return true;
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
        } else if (original.getItem() instanceof MatterCellItem) {
            boolean moved=false;
            for(int bay=0;bay<diskBayCount();bay++) {
                int target=diskMenuSlot(bay);
                if(slots.get(target).mayPlace(original) && moveItemStackTo(original,target,target+1,false)){moved=true;break;}
            }
            if(!moved)return ItemStack.EMPTY;
        } else if (original.is(ModContent.SPEED_UPGRADE.get()) || original.is(ModContent.EFFICIENCY_UPGRADE.get())) {
            if (!supportsUpgrades() || !slots.get(2).mayPlace(original)
                    || !moveItemStackTo(original, 2, 3, false)) return ItemStack.EMPTY;
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
        return machine == null || (terminal == null ? machine.stillValid(player) : TerminalNetwork.canManage(terminal, machine, player));
    }
}
