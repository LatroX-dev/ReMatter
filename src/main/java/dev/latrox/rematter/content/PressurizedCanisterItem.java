package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.MatterStore;
import dev.latrox.rematter.chemistry.SubstanceKey;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Reusable 16,000-unit pressurized container that holds either a single non-reserved liquid/gas substance
 * or a single physical fluid (16 buckets), bridging terminals, drives, machines, Mekanism chemical tanks,
 * and world fluid blocks without bypassing decomposition or synthesis permissions.
 */
public final class PressurizedCanisterItem extends Item {
    public static final int CAPACITY = PressurizedCanisterData.CAPACITY;
    private static final int MATTER_BAR_COLOR = 0x57CDDB;
    private static final int FLUID_BAR_COLOR = 0x3E9AE8;
    private static final int LOCKED_BAR_COLOR = 0xEA4848;

    public PressurizedCanisterItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public record ExtractedMatter(SubstanceKey key, int amount) {}

    /** Quantities from a single committed transfer on the server thread. */
    public record Deposit(int stored, int voided) {
        static final Deposit NONE = new Deposit(0, 0);

        public int accepted() {
            return stored + voided;
        }

        static Deposit committed(int accepted, long previous, long current) {
            int stored = Math.toIntExact(current - previous);
            return new Deposit(stored, accepted - stored);
        }

        public void show(Player player, SubstanceKey key) {
            var name = ChemistryText.name(ChemistryText.catalogue(player.level()), key);
            player.sendOverlayMessage(voided > 0
                    ? Component.translatable("message.rematter.pressurized_canister.overflow", name, stored, voided)
                    : Component.translatable("message.rematter.pressurized_canister.deposited", stored, name));
        }
    }

    public static int storedAmount(ItemStack stack) {
        return switch (PressurizedCanisterData.read(stack)) {
            case PressurizedCanisterData.State.Matter matter -> matter.amount();
            case PressurizedCanisterData.State.Fluid fluid -> fluid.amount();
            default -> 0;
        };
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return !(PressurizedCanisterData.read(stack) instanceof PressurizedCanisterData.State.Empty);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return switch (PressurizedCanisterData.read(stack)) {
            case PressurizedCanisterData.State.Empty ignored -> 0;
            case PressurizedCanisterData.State.Locked ignored -> 13;
            case PressurizedCanisterData.State.Matter matter -> Math.round(13.0F * matter.amount() / CAPACITY);
            case PressurizedCanisterData.State.Fluid fluid -> Math.round(13.0F * fluid.amount() / CAPACITY);
        };
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return switch (PressurizedCanisterData.read(stack)) {
            case PressurizedCanisterData.State.Fluid ignored -> FLUID_BAR_COLOR;
            case PressurizedCanisterData.State.Locked ignored -> LOCKED_BAR_COLOR;
            default -> MATTER_BAR_COLOR;
        };
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    /**
     * Handles world fluid source pickup and placement when right-clicking air/water/solid blocks
     * that did not consume the interaction as a machine or fluid handler.
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.isSpectator()) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getCount() != 1) return InteractionResult.PASS;
        var state = PressurizedCanisterData.read(stack);
        if (state instanceof PressurizedCanisterData.State.Matter
                || state instanceof PressurizedCanisterData.State.Locked) {
            return InteractionResult.PASS;
        }
        boolean canPickup = state instanceof PressurizedCanisterData.State.Empty
                || (state instanceof PressurizedCanisterData.State.Fluid f && f.amount() + 1000 <= CAPACITY);
        if (canPickup) {
            BlockHitResult pickupHit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
            if (pickupHit.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = pickupHit.getBlockPos();
                Direction dir = pickupHit.getDirection();
                if (level.mayInteract(player, pos) && player.mayUseItemAt(pos, dir, stack)
                        && !level.getFluidState(pos).isEmpty()) {
                    var handler = ItemAccess.forPlayerInteraction(player, hand).getCapability(Capabilities.Fluid.ITEM);
                    if (handler != null && !FluidUtil.tryPickupFluid(handler, player, level, pos, dir).isEmpty()) {
                        return InteractionResult.SUCCESS;
                    }
                }
            }
        }
        if (state instanceof PressurizedCanisterData.State.Fluid fluid && fluid.amount() >= 1000) {
            BlockHitResult placeHit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
            if (placeHit.getType() == HitResult.Type.BLOCK) {
                BlockPos hitPos = placeHit.getBlockPos();
                Direction dir = placeHit.getDirection();
                BlockState hitState = level.getBlockState(hitPos);
                BlockPos targetPos = hitState.canBeReplaced() ? hitPos : hitPos.relative(dir);
                if (level.mayInteract(player, hitPos) && player.mayUseItemAt(targetPos, dir, stack)) {
                    var handler = ItemAccess.forPlayerInteraction(player, hand).getCapability(Capabilities.Fluid.ITEM);
                    if (handler != null && !FluidUtil.tryPlaceFluid(handler, player, level, hand, targetPos).isEmpty()) {
                        return InteractionResult.SUCCESS;
                    }
                }
            }
        }
        return InteractionResult.PASS;
    }

    /**
     * Direct block interaction with ReMatter machines:
     * - Fluid canisters (or empty canisters on fluid-bearing Decomposer/Synthesizer) defer to FluidUtil.
     * - Matter canisters deposit into the machine/network on right-click (or sneak-right-click on Terminal),
     *   and extract matching or available liquid/gas substances on sneak-right-click.
     */
    public static InteractionResult interactWithMachine(
            ItemStack stack, Level level, BlockPos pos, Player player, InteractionHand hand, @Nullable MachineBlockEntity machine) {
        if (!(stack.getItem() instanceof PressurizedCanisterItem) || stack.getCount() != 1 || player.isSpectator()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        var state = PressurizedCanisterData.read(stack);
        if (state instanceof PressurizedCanisterData.State.Locked
                || state instanceof PressurizedCanisterData.State.Fluid) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        // On a wired Matter Terminal, normal right-click opens the Terminal UI unless sneaking.
        if (machine != null && machine.kind() == MachineKind.TERMINAL && !player.isShiftKeyDown()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        // If empty and the machine currently holds fluid (and player is not sneaking to pull matter), let FluidUtil drain the fluid tank.
        if (state instanceof PressurizedCanisterData.State.Empty
                && !player.isShiftKeyDown()
                && machine != null
                && !machine.fluids().getResource(0).isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide()) {
            if (state instanceof PressurizedCanisterData.State.Matter || player.isShiftKeyDown()) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (machine == null || !machine.interact(player)) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            // Sneak + right-click: on Terminal with a non-empty canister, deposit first; otherwise top up / extract from storage.
            if (machine.kind() == MachineKind.TERMINAL && state instanceof PressurizedCanisterData.State.Matter matter) {
                if (emptyCanister(stack, machine, player, matter, pos)) return InteractionResult.SUCCESS;
            }
            SubstanceKey filter = state instanceof PressurizedCanisterData.State.Matter matter ? matter.key() : null;
            int room = state instanceof PressurizedCanisterData.State.Matter matter ? CAPACITY - matter.amount() : CAPACITY;
            if (room <= 0) return InteractionResult.SUCCESS;
            var extracted = extractFromMachine(machine, player, filter, room);
            if (extracted.isPresent()) {
                int prev = state instanceof PressurizedCanisterData.State.Matter matter ? matter.amount() : 0;
                PressurizedCanisterData.writeMatter(stack, extracted.get().key(), prev + extracted.get().amount());
                notifyExtraction(level, pos, player, extracted.get().key(), extracted.get().amount());
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.SUCCESS;
        }
        if (state instanceof PressurizedCanisterData.State.Matter matter) {
            emptyCanister(stack, machine, player, matter, pos);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    private static boolean emptyCanister(ItemStack stack, MachineBlockEntity machine, Player player,
                                         PressurizedCanisterData.State.Matter matter, BlockPos pos) {
        Deposit deposit = depositWithReceipt(machine, player, matter.key(), matter.amount());
        if (deposit.accepted() == 0) return false;
        PressurizedCanisterData.writeMatter(stack, matter.key(), matter.amount() - deposit.accepted());
        deposit.show(player, matter.key());
        ModSounds.play(player.level(), pos, ModSounds.CANISTER_EMPTY);
        return true;
    }

    public static void notifyExtraction(Level level, BlockPos pos, Player player, SubstanceKey key, int amount) {
        player.sendOverlayMessage(Component.translatable("message.rematter.pressurized_canister.extracted", amount,
                ChemistryText.name(ChemistryText.catalogue(level), key)));
        ModSounds.play(level, pos, ModSounds.CANISTER_FILL);
    }

    public static long availableSpace(MachineBlockEntity machine, SubstanceKey key) {
        if (!machine.networkLive() || !PressurizedCanisterData.validMatter(key)
                || !ReMatter.chemistry().isKnown(key)) {
            return 0;
        }
        MatterStore store = targetStore(machine);
        if (store == null) return 0;
        if (store.voidsOverflow(key)) return CAPACITY;
        return MachineBlockEntity.insertable(store, key, CAPACITY);
    }

    public static long availableStock(MachineBlockEntity machine, SubstanceKey key) {
        if (!machine.networkLive()) return 0;
        MatterStore store = targetStore(machine);
        return store == null ? 0 : store.amount(key);
    }

    private static @Nullable MatterStore targetStore(MachineBlockEntity machine) {
        if (machine.kind().isStorage()) {
            return machine.storageStatus().equals("ready") ? machine.matterStore() : null;
        }
        var net = machine.matterNetwork(false);
        return net.ready() ? net.inventory() : null;
    }

    public static int depositIntoMachine(MachineBlockEntity machine, Player player, SubstanceKey key, int maximum) {
        return depositWithReceipt(machine, player, key, maximum).accepted();
    }

    public static Deposit depositWithReceipt(MachineBlockEntity machine, Player player, SubstanceKey key, int maximum) {
        if (!machine.canAccess(player) || !machine.networkLive() || maximum <= 0
                || !PressurizedCanisterData.validMatter(key)
                || !ReMatter.chemistry().isKnown(key)
                || Transaction.getCurrentOpenedTransaction() != null) {
            return Deposit.NONE;
        }
        if (machine.kind().isStorage()) {
            if (!machine.storageStatus().equals("ready")) return Deposit.NONE;
            MatterStore store = machine.matterStore();
            int accepted = store.voidsOverflow(key)
                    ? maximum
                    : (int) Math.min(maximum, MachineBlockEntity.insertable(store, key, maximum));
            long previous = store.amount(key);
            if (accepted <= 0 || !store.apply(Map.of(), Map.of(key, (long) accepted))) return Deposit.NONE;
            machine.setChanged();
            return Deposit.committed(accepted, previous, store.amount(key));
        }
        return depositIntoNetwork(machine.matterNetwork(true), machine, key, maximum);
    }

    public static Optional<ExtractedMatter> extractFromMachine(
            MachineBlockEntity machine, Player player, @Nullable SubstanceKey filter, int maximum) {
        if (!machine.canAccess(player) || !machine.networkLive() || maximum <= 0
                || Transaction.getCurrentOpenedTransaction() != null) {
            return Optional.empty();
        }
        if (machine.kind().isStorage()) {
            if (!machine.storageStatus().equals("ready")) return Optional.empty();
            MatterStore store = machine.matterStore();
            SubstanceKey target = chooseExtractable(store, filter);
            if (target == null) return Optional.empty();
            int moved = (int) Math.min(maximum, store.amount(target));
            if (moved <= 0 || !store.apply(Map.of(target, (long) moved), Map.of())) return Optional.empty();
            machine.setChanged();
            return Optional.of(new ExtractedMatter(target, moved));
        }
        return extractFromNetwork(machine.matterNetwork(true), machine, filter, maximum);
    }

    static Deposit depositIntoNetwork(
            MachineMatterNetwork network, @Nullable MachineBlockEntity pulseOrigin, SubstanceKey key, int maximum) {
        if (!network.ready() || network.inventory() == null || maximum <= 0
                || !PressurizedCanisterData.validMatter(key)
                || !ReMatter.chemistry().isKnown(key)
                || Transaction.getCurrentOpenedTransaction() != null) {
            return Deposit.NONE;
        }
        var inventory = network.inventory();
        int accepted = inventory.voidsOverflow(key)
                ? maximum
                : (int) Math.min(maximum, MachineBlockEntity.insertable(inventory, key, maximum));
        if (accepted <= 0) return Deposit.NONE;
        long previous = inventory.amount(key);
        if (pulseOrigin != null) {
            var activity = pulseOrigin.commitNetworkMatter(network, Map.of(), Map.of(key, (long) accepted));
            if (activity == null) return Deposit.NONE;
            network.markChanged();
            pulseOrigin.publishNetworkActivity(network, activity);
            return Deposit.committed(accepted, previous, inventory.amount(key));
        }
        if (!inventory.apply(Map.of(), Map.of(key, (long) accepted))) return Deposit.NONE;
        network.markChanged();
        return Deposit.committed(accepted, previous, inventory.amount(key));
    }

    static Optional<ExtractedMatter> extractFromNetwork(
            MachineMatterNetwork network, @Nullable MachineBlockEntity pulseOrigin,
            @Nullable SubstanceKey filter, int maximum) {
        if (!network.ready() || network.inventory() == null || maximum <= 0
                || Transaction.getCurrentOpenedTransaction() != null) {
            return Optional.empty();
        }
        var inventory = network.inventory();
        SubstanceKey target = chooseExtractable(inventory, filter);
        if (target == null) return Optional.empty();
        int moved = (int) Math.min(maximum, inventory.amount(target));
        if (moved <= 0) return Optional.empty();
        if (pulseOrigin != null) {
            var activity = pulseOrigin.commitNetworkMatter(network, Map.of(target, (long) moved), Map.of());
            if (activity == null) return Optional.empty();
            network.markChanged();
            pulseOrigin.publishNetworkActivity(network, activity);
            return Optional.of(new ExtractedMatter(target, moved));
        }
        if (!inventory.apply(Map.of(target, (long) moved), Map.of())) return Optional.empty();
        network.markChanged();
        return Optional.of(new ExtractedMatter(target, moved));
    }

    private static @Nullable SubstanceKey chooseExtractable(MatterStore store, @Nullable SubstanceKey filter) {
        if (filter != null) {
            return PressurizedCanisterData.validMatter(filter) && store.amount(filter) > 0 ? filter : null;
        }
        for (var entry : store.contents().entrySet()) {
            if (entry.getValue() > 0 && PressurizedCanisterData.validMatter(entry.getKey())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Fills or drains a {@link PressurizedCanisterItem} in the player's inventory from an open wired or wireless Terminal.
     *
     * @param drainToNetwork {@code true} to empty canisters into the network; {@code false} to fill a canister with {@code selected}.
     */
    public static int transferInInventory(
            Inventory inventory, MachineMatterNetwork network, @Nullable MachineBlockEntity pulseOrigin,
            @Nullable SubstanceKey selected, boolean drainToNetwork) {
        if (!network.ready() || Transaction.getCurrentOpenedTransaction() != null) return 0;
        int size = inventory.getContainerSize();
        if (drainToNetwork) {
            for (int i = 0; i < size; i++) {
                ItemStack candidate = inventory.getItem(i);
                if (!(candidate.getItem() instanceof PressurizedCanisterItem) || candidate.getCount() != 1) continue;
                if (!(PressurizedCanisterData.read(candidate) instanceof PressurizedCanisterData.State.Matter matter)) {
                    continue;
                }
                if (selected != null && !matter.key().equals(selected)) continue;
                Deposit deposit = depositIntoNetwork(network, pulseOrigin, matter.key(), matter.amount());
                if (deposit.accepted() > 0) {
                    PressurizedCanisterData.writeMatter(candidate, matter.key(), matter.amount() - deposit.accepted());
                    inventory.setChanged();
                    deposit.show(inventory.player, matter.key());
                    return deposit.accepted();
                }
            }
            // Fallback: if selected substance wasn't in any canister, drain the first matter canister with space in network.
            if (selected != null) {
                return transferInInventory(inventory, network, pulseOrigin, null, true);
            }
            return 0;
        }
        if (selected == null || !PressurizedCanisterData.validMatter(selected)) return 0;
        // Prefer topping up a partially filled canister of the same substance before claiming an empty canister.
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < size; i++) {
                ItemStack candidate = inventory.getItem(i);
                if (!(candidate.getItem() instanceof PressurizedCanisterItem) || candidate.getCount() != 1) continue;
                var state = PressurizedCanisterData.read(candidate);
                int prev = 0;
                if (pass == 0) {
                    if (!(state instanceof PressurizedCanisterData.State.Matter matter) || !matter.key().equals(selected)
                            || matter.amount() >= CAPACITY) {
                        continue;
                    }
                    prev = matter.amount();
                } else if (!(state instanceof PressurizedCanisterData.State.Empty)) {
                    continue;
                }
                var extracted = extractFromNetwork(network, pulseOrigin, selected, CAPACITY - prev);
                if (extracted.isPresent()) {
                    PressurizedCanisterData.writeMatter(candidate, selected, prev + extracted.get().amount());
                    inventory.setChanged();
                    return extracted.get().amount();
                }
            }
        }
        return 0;
    }

    @Override
    public void appendHoverText(
            ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        switch (PressurizedCanisterData.read(stack)) {
            case PressurizedCanisterData.State.Empty ignored -> {
                tooltip.accept(Component.translatable("tooltip.rematter.pressurized_canister.empty", CAPACITY)
                        .withStyle(ChatFormatting.GRAY));
            }
            case PressurizedCanisterData.State.Locked ignored -> {
                tooltip.accept(Component.translatable("tooltip.rematter.pressurized_canister.locked")
                        .withStyle(ChatFormatting.RED));
            }
            case PressurizedCanisterData.State.Matter matter -> {
                var registry = ChemistryText.catalogue(context.level());
                tooltip.accept(Component.translatable(
                                "tooltip.rematter.pressurized_canister.matter",
                                ChemistryText.name(registry, matter.key()),
                                ChemistryText.phase(matter.key().phase()),
                                matter.amount(),
                                CAPACITY)
                        .withStyle(ChatFormatting.AQUA));
            }
            case PressurizedCanisterData.State.Fluid fluid -> {
                Identifier id = fluid.fluidId();
                Component fluidName = BuiltInRegistries.FLUID.containsKey(id)
                        && BuiltInRegistries.FLUID.getValue(id) != Fluids.EMPTY
                        ? FluidResource.of(BuiltInRegistries.FLUID.getValue(id)).getHoverName()
                        : Component.literal(id.toString());
                tooltip.accept(Component.translatable(
                                "tooltip.rematter.pressurized_canister.fluid",
                                fluidName,
                                fluid.amount(),
                                CAPACITY)
                        .withStyle(ChatFormatting.BLUE));
            }
        }
        tooltip.accept(Component.translatable("tooltip.rematter.pressurized_canister.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
