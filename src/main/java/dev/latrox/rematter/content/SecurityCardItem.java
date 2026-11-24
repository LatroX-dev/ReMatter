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

/**
 * Dedicated Security Card for stamping {@link SecurityMode} onto individual devices (Right-Click) or
 * across an entire connected ReMatter network (Sneak + Right-Click). Mode cycles via Right-Click in air.
 */
public final class SecurityCardItem extends Item {
    public SecurityCardItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static SecurityMode mode(ItemStack stack) {
        return stack.getOrDefault(SecurityMode.TYPE.get(), SecurityMode.PUBLIC);
    }

    public static SecurityMode cycleMode(ItemStack stack, boolean forward, @Nullable Player player, @Nullable Level level) {
        SecurityMode next = forward ? mode(stack).next() : mode(stack).previous();
        if (next == SecurityMode.PUBLIC) stack.remove(SecurityMode.TYPE.get());
        else stack.set(SecurityMode.TYPE.get(), next);
        if (player != null && level != null && !level.isClientSide()) {
            player.sendOverlayMessage(Component.translatable("message.rematter.security_card.mode", next.coloredLabel()));
            float pitch = switch (next) {
                case PUBLIC -> 1.06F;
                case TRUSTED -> 1.00F;
                case PRIVATE -> 0.90F;
            };
            ModSounds.play(level, player.blockPosition(), ModSounds.CONFIG_CARD_READ, 1.0F, pitch);
        }
        return next;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.rematter.security_card.named", super.getName(stack), mode(stack).coloredLabel());
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside());
        return SecurityCardInteractions.interact(context.getPlayer(), context.getLevel(), context.getClickedPos(), context.getItemInHand(), hit);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.isSpectator()) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            cycleMode(player.getItemInHand(hand), !player.isShiftKeyDown(), player, level);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        SecurityMode current = mode(stack);
        tooltip.accept(Component.translatable("tooltip.rematter.security_card.mode", current.coloredLabel()).withStyle(ChatFormatting.GRAY));
        tooltip.accept(current.meaning().copy().withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.rematter.security_card.use_hint").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("tooltip.rematter.security_card.cycle_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
