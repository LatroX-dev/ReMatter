package dev.latrox.rematter.content;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** Precision workshop wrench with explicit Ports, Rotate and Dismantle modes switched via Sneak + Right-Click. */
public final class MatterWrenchItem extends Item {
    public MatterWrenchItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static WrenchMode mode(ItemStack stack) {
        return stack.getOrDefault(WrenchMode.TYPE.get(), WrenchMode.PORTS);
    }

    public static WrenchMode cycleMode(ItemStack stack, @Nullable Player player, @Nullable Level level) {
        WrenchMode next = mode(stack).next();
        if (next == WrenchMode.PORTS) stack.remove(WrenchMode.TYPE.get());
        else stack.set(WrenchMode.TYPE.get(), next);
        if (player != null && level != null && !level.isClientSide()) {
            player.sendOverlayMessage(Component.translatable("message.rematter.wrench_mode", next.coloredLabel()));
            float modePitch = switch (next) {
                case PORTS -> 1.00F;
                case ROTATE -> 1.07F;
                case DISMANTLE -> 0.90F;
            };
            ModSounds.play(level, player.blockPosition(), ModSounds.WRENCH_MODE, 1.0F, modePitch);
        }
        return next;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.rematter.matter_wrench.named", super.getName(stack), mode(stack).coloredLabel());
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside());
        return WrenchInteractions.interact(context.getPlayer(), context.getLevel(), context.getClickedPos(), context.getItemInHand(), hit);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.isSpectator() || !player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!level.isClientSide()) cycleMode(player.getItemInHand(hand), player, level);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        WrenchMode current = mode(stack);
        tooltip.accept(Component.translatable("tooltip.rematter.matter_wrench.mode", current.coloredLabel()).withStyle(ChatFormatting.GRAY));
        tooltip.accept(current.hint().copy().withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.rematter.matter_wrench.switch_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
