package dev.latrox.rematter.content;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Handheld FE-powered terminal opening full Matter Terminal stock and remote machine access via a linked Access Point. */
public final class WirelessMatterTerminalItem extends Item {
    public static final int CAPACITY = 100_000;
    public static final int MAX_CHARGE_RATE = 4_000;
    public static final int OPEN_COST = 40;
    public static final int SESSION_INTERVAL_COST = 10;
    public static final int MAX_RANGE = 128;

    public WirelessMatterTerminalItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static int energy(ItemStack stack) {
        return Math.clamp(stack.getOrDefault(WirelessTerminalData.ENERGY.get(), 0), 0, CAPACITY);
    }

    public static void setEnergy(ItemStack stack, int value) {
        int clamped = Math.clamp(value, 0, CAPACITY);
        if (clamped <= 0) stack.remove(WirelessTerminalData.ENERGY.get());
        else stack.set(WirelessTerminalData.ENERGY.get(), clamped);
    }

    public static boolean consumeEnergy(ItemStack stack, int amount) {
        int current = energy(stack);
        if (current < amount) return false;
        setEnergy(stack, current - amount);
        return true;
    }

    public static ItemStack createCharged() {
        var stack = new ItemStack(ModContent.WIRELESS_MATTER_TERMINAL.get());
        setEnergy(stack, CAPACITY);
        return stack;
    }

    public static boolean withinRange(Player player, BlockPos pos) {
        return player.position().distanceToSqr(Vec3.atCenterOf(pos)) <= (double) MAX_RANGE * MAX_RANGE;
    }

    @Override public boolean isBarVisible(ItemStack stack) { return true; }
    @Override public int getBarWidth(ItemStack stack) { return Math.round(13.0F * energy(stack) / CAPACITY); }
    @Override public int getBarColor(ItemStack stack) { return 0x55B6FF; }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.isSpectator()) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        var data = stack.get(WirelessTerminalData.LINK.get());
        var target = data == null ? null : data.target().orElse(null);
        if (target == null) {
            player.sendOverlayMessage(Component.translatable("message.rematter.wireless.unbound"));
            return InteractionResult.FAIL;
        }
        if (!target.dimension().equals(server.dimension())) {
            player.sendOverlayMessage(Component.translatable("message.rematter.wireless.wrong_dimension"));
            return InteractionResult.FAIL;
        }
        double distSqr = player.position().distanceToSqr(Vec3.atCenterOf(target.pos()));
        if (distSqr > (double) WirelessAccessPointBlockEntity.MAX_RANGE * WirelessAccessPointBlockEntity.MAX_RANGE) {
            int distance = (int) Math.ceil(Math.sqrt(distSqr));
            player.sendOverlayMessage(Component.translatable("message.rematter.wireless.out_of_range",
                    distance, WirelessAccessPointBlockEntity.MAX_RANGE));
            return InteractionResult.FAIL;
        }
        var chunk = server.getChunkSource().getChunkNow(target.pos().getX() >> 4, target.pos().getZ() >> 4);
        if (chunk == null || !(chunk.getBlockEntity(target.pos()) instanceof WirelessAccessPointBlockEntity ap) || !ap.networkLive()) {
            player.sendOverlayMessage(Component.translatable("message.rematter.wireless.unloaded"));
            return InteractionResult.FAIL;
        }
        if (!ap.withinRange(player)) {
            int distance = (int) Math.ceil(Math.sqrt(distSqr));
            player.sendOverlayMessage(Component.translatable("message.rematter.wireless.out_of_range",
                    distance, ap.effectiveRange()));
            return InteractionResult.FAIL;
        }
        if (!ap.canAccess(player)) {
            player.sendOverlayMessage(Component.translatable("message.rematter.private_machine"));
            return InteractionResult.FAIL;
        }
        if (!ap.powered()) {
            player.sendOverlayMessage(Component.translatable("message.rematter.wireless.ap_unpowered"));
            return InteractionResult.FAIL;
        }
        if (!consumeEnergy(stack, OPEN_COST)) {
            player.sendOverlayMessage(Component.translatable("message.rematter.wireless.low_battery"));
            return InteractionResult.FAIL;
        }
        int lockedSlot = hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND;
        return TerminalNetwork.openRoot(new TerminalAnchor.Wireless(ap, lockedSlot), serverPlayer)
                ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.rematter.wireless_terminal.energy", energy(stack), CAPACITY).withStyle(ChatFormatting.AQUA));
        var data = stack.get(WirelessTerminalData.LINK.get());
        var target = data == null ? null : data.target().orElse(null);
        if (target == null) {
            tooltip.accept(Component.translatable("tooltip.rematter.wireless_terminal.unlinked").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.accept(Component.translatable("tooltip.rematter.wireless_terminal.linked",
                    target.pos().getX(), target.pos().getY(), target.pos().getZ()).withStyle(ChatFormatting.GREEN));
        }
    }
}
