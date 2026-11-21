package dev.latrox.rematter.content;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Pocket 4M FE battery that charges and discharges in any FE dock and optionally recharges FE-capable
 * items (including Wireless Matter Terminals and cross-mod tools/equipment) in the player's inventory.
 */
public final class PortableEnergyCellItem extends Item {
    public static final int CAPACITY = 4_000_000;
    public static final int MAX_TRANSFER = 16_000;
    private static final int BAR_COLOR = 0xEA4848;
    private static final int BAR_ACTIVE_COLOR = 0x4FE18E;

    public PortableEnergyCellItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static int energy(ItemStack stack) {
        return Math.clamp(stack.getOrDefault(PortableEnergyCellData.ENERGY.get(), 0), 0, CAPACITY);
    }

    public static void setEnergy(ItemStack stack, int value) {
        int clamped = Math.clamp(value, 0, CAPACITY);
        if (clamped <= 0) stack.remove(PortableEnergyCellData.ENERGY.get());
        else stack.set(PortableEnergyCellData.ENERGY.get(), clamped);
    }

    public static boolean autoCharge(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(PortableEnergyCellData.AUTO_CHARGE.get()));
    }

    public static void setAutoCharge(ItemStack stack, boolean enabled) {
        if (!enabled) stack.remove(PortableEnergyCellData.AUTO_CHARGE.get());
        else stack.set(PortableEnergyCellData.AUTO_CHARGE.get(), true);
    }

    public static ItemStack createCharged() {
        var stack = new ItemStack(ModContent.PORTABLE_ENERGY_CELL.get());
        setEnergy(stack, CAPACITY);
        return stack;
    }

    @Override public boolean isBarVisible(ItemStack stack) { return true; }
    @Override public int getBarWidth(ItemStack stack) { return Math.round(13.0F * energy(stack) / CAPACITY); }
    @Override public int getBarColor(ItemStack stack) { return autoCharge(stack) ? BAR_ACTIVE_COLOR : BAR_COLOR; }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.isSpectator() || !player.isShiftKeyDown()) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            boolean next = !autoCharge(stack);
            setAutoCharge(stack, next);
            player.sendOverlayMessage(Component.translatable(next
                    ? "message.rematter.portable_energy_cell.mode_on"
                    : "message.rematter.portable_energy_cell.mode_off"));
            ModSounds.play(level, player.blockPosition(), next ? ModSounds.ENERGY_CELL_ON : ModSounds.ENERGY_CELL_OFF);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        if (!autoCharge(stack) || !(entity instanceof Player player) || player.isSpectator()) return;
        if (Transaction.getCurrentOpenedTransaction() != null) return;
        chargePlayerItems(stack, player);
    }

    /** Called by the optional Curios integration when worn in a Curios slot. */
    public static void tickInCurio(ItemStack stack, net.minecraft.world.entity.LivingEntity entity) {
        if (entity.level().isClientSide() || !autoCharge(stack) || !(entity instanceof Player player) || player.isSpectator()) return;
        if (Transaction.getCurrentOpenedTransaction() != null) return;
        chargePlayerItems(stack, player);
    }

    /** Charges player inventory items and any Curios-equipped items within the per-tick transfer budget. */
    public static int chargePlayerItems(ItemStack batteryStack, Player player) {
        int stored = energy(batteryStack);
        if (stored <= 0) return 0;
        int sentInventory = chargeInventoryItems(batteryStack, player.getInventory());
        int remainingStored = energy(batteryStack);
        int remainingBudget = Math.min(remainingStored, MAX_TRANSFER - sentInventory);
        int sentCurios = 0;
        if (remainingBudget > 0) {
            sentCurios = dev.latrox.rematter.compat.CuriosSupport.chargeCuriosItems(player, batteryStack, remainingBudget);
            if (sentCurios > 0) setEnergy(batteryStack, remainingStored - sentCurios);
        }
        return sentInventory + sentCurios;
    }

    /** Distributes up to {@link #MAX_TRANSFER} FE to non-battery FE items in the inventory without ping-ponging. */
    public static int chargeInventoryItems(ItemStack batteryStack, Inventory inventory) {
        int stored = energy(batteryStack);
        if (stored <= 0) return 0;
        int budget = Math.min(stored, MAX_TRANSFER);
        int sentTotal = 0;
        var wrapper = VanillaContainerWrapper.of(inventory);
        int size = inventory.getContainerSize();
        for (int i = 0; i < size && budget > 0; i++) {
            ItemStack candidate = inventory.getItem(i);
            if (candidate.isEmpty() || candidate == batteryStack
                    || candidate.getItem() instanceof PortableEnergyCellItem
                    || candidate.getItem() instanceof dev.latrox.rematter.energy.EnergyCubeItem) {
                continue;
            }
            var sink = ItemAccess.forHandlerIndexStrict(wrapper, i).getCapability(Capabilities.Energy.ITEM);
            if (sink == null || sink.getAmountAsLong() >= sink.getCapacityAsLong()) continue;
            int moved = 0;
            try (var tx = Transaction.openRoot()) {
                moved = sink.insert(budget, tx);
                if (moved > 0) tx.commit();
            }
            if (moved > 0) {
                budget -= moved;
                sentTotal += moved;
            }
        }
        if (sentTotal > 0) {
            setEnergy(batteryStack, stored - sentTotal);
        }
        return sentTotal;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.rematter.portable_energy_cell.energy", energy(stack), CAPACITY)
                .withStyle(ChatFormatting.RED));
        boolean active = autoCharge(stack);
        tooltip.accept(Component.translatable(active
                        ? "tooltip.rematter.portable_energy_cell.auto_on"
                        : "tooltip.rematter.portable_energy_cell.auto_off")
                .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.rematter.portable_energy_cell.toggle_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
