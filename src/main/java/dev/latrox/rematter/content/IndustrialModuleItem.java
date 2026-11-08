package dev.latrox.rematter.content;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Placement and function stay visible even before a chamber has been assembled. */
public final class IndustrialModuleItem extends BlockItem {
    private final IndustrialModuleKind kind;
    public IndustrialModuleItem(IndustrialModuleBlock block, Properties properties) {
        super(block, properties);
        kind=block.kind();
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                         Consumer<Component> tooltip, TooltipFlag flags) {
        super.appendHoverText(stack,context,display,tooltip,flags);
        tooltip.accept(Component.translatable("item.rematter.module."+kind.id()+".mount").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.rematter.module."+kind.id()+".benefit").withStyle(ChatFormatting.AQUA));
    }
}
