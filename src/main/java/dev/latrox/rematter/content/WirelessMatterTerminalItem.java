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
        if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        int lockedSlot = hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND;
        return tryOpen(serverPlayer, stack, lockedSlot, null, -1) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    /** Opens the best available Wireless Matter Terminal from Curios or player inventory via hotkey. */
    public static boolean openFromHotkey(ServerPlayer player) {
        var anchor = prepareHotkeyOpen(player);
        return anchor != null && TerminalNetwork.openRoot(anchor, player);
    }

    /** Validates and debits the best available Wireless Matter Terminal for hotkey activation. */
    static TerminalAnchor.@org.jspecify.annotations.Nullable Wireless prepareHotkeyOpen(Player player) {
        if (player.isSpectator()) return null;
        var curio = dev.latrox.rematter.compat.CuriosSupport.findWirelessTerminal(player);
        if (curio.isPresent() && isReadyCandidate(curio.get().stack())) {
            return prepareOpen(player, curio.get().stack(), -1, curio.get().identifier(), curio.get().index());
        }
        Inventory inv = player.getInventory();
        int fallbackSlot = -1;
        int linkedSlot = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack candidate = inv.getItem(i);
            if (!(candidate.getItem() instanceof WirelessMatterTerminalItem)) continue;
            if (fallbackSlot < 0) fallbackSlot = i;
            var link = candidate.get(WirelessTerminalData.LINK.get());
            boolean linked = link != null && link.target().isPresent();
            if (linked && linkedSlot < 0) linkedSlot = i;
            if (linked && energy(candidate) >= OPEN_COST) {
                return prepareOpen(player, candidate, i, null, -1);
            }
        }
        if (curio.isPresent()) {
            return prepareOpen(player, curio.get().stack(), -1, curio.get().identifier(), curio.get().index());
        }
        int chosen = linkedSlot >= 0 ? linkedSlot : fallbackSlot;
        if (chosen >= 0) {
            return prepareOpen(player, inv.getItem(chosen), chosen, null, -1);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendOverlayMessage(Component.translatable("message.rematter.wireless.none_carried"));
        }
        return null;
    }

    private static boolean isReadyCandidate(ItemStack stack) {
        var link = stack.get(WirelessTerminalData.LINK.get());
        return link != null && link.target().isPresent() && energy(stack) >= OPEN_COST;
    }

    private static boolean tryOpen(ServerPlayer serverPlayer, ItemStack stack, int lockedInventorySlot,
                                   @org.jspecify.annotations.Nullable String curioSlot, int curioIndex) {
        var anchor = prepareOpen(serverPlayer, stack, lockedInventorySlot, curioSlot, curioIndex);
        return anchor != null && TerminalNetwork.openRoot(anchor, serverPlayer);
    }

    private static TerminalAnchor.@org.jspecify.annotations.Nullable Wireless prepareOpen(
            Player player, ItemStack stack, int lockedInventorySlot,
            @org.jspecify.annotations.Nullable String curioSlot, int curioIndex) {
        if (!(player.level() instanceof ServerLevel server)) return null;
        var data = stack.get(WirelessTerminalData.LINK.get());
        var target = data == null ? null : data.target().orElse(null);
        if (target == null) {
            notifyPlayer(player, Component.translatable("message.rematter.wireless.unbound"));
            return null;
        }
        if (!target.dimension().equals(server.dimension())) {
            notifyPlayer(player, Component.translatable("message.rematter.wireless.wrong_dimension"));
            return null;
        }
        double distSqr = player.position().distanceToSqr(Vec3.atCenterOf(target.pos()));
        if (distSqr > (double) WirelessAccessPointBlockEntity.MAX_RANGE * WirelessAccessPointBlockEntity.MAX_RANGE) {
            int distance = (int) Math.ceil(Math.sqrt(distSqr));
            notifyPlayer(player, Component.translatable("message.rematter.wireless.out_of_range",
                    distance, WirelessAccessPointBlockEntity.MAX_RANGE));
            return null;
        }
        var chunk = server.getChunkSource().getChunkNow(target.pos().getX() >> 4, target.pos().getZ() >> 4);
        if (chunk == null || !(chunk.getBlockEntity(target.pos()) instanceof WirelessAccessPointBlockEntity ap) || !ap.networkLive()) {
            notifyPlayer(player, Component.translatable("message.rematter.wireless.unloaded"));
            return null;
        }
        if (!ap.withinRange(player)) {
            int distance = (int) Math.ceil(Math.sqrt(distSqr));
            notifyPlayer(player, Component.translatable("message.rematter.wireless.out_of_range",
                    distance, ap.effectiveRange()));
            return null;
        }
        if (!ap.canAccess(player)) {
            notifyPlayer(player, Component.translatable("message.rematter.private_machine"));
            return null;
        }
        if (!ap.powered()) {
            notifyPlayer(player, Component.translatable("message.rematter.wireless.ap_unpowered"));
            return null;
        }
        if (!consumeEnergy(stack, OPEN_COST)) {
            notifyPlayer(player, Component.translatable("message.rematter.wireless.low_battery"));
            return null;
        }
        return new TerminalAnchor.Wireless(ap, lockedInventorySlot, curioSlot, curioIndex);
    }

    private static void notifyPlayer(Player player, Component message) {
        if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendOverlayMessage(message);
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
