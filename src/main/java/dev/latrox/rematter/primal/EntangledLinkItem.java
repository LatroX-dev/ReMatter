package dev.latrox.rematter.primal;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

public final class EntangledLinkItem extends BlockItem {
    private static final String BINDING = "rematter_entangled_origin";
    public EntangledLinkItem(Block block, Properties properties) { super(block, properties); }
    @Override public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown() || !(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof EntangledLinkBlockEntity)) return super.useOn(context);
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        if (player.isSpectator() || !player.mayUseItemAt(context.getClickedPos(), context.getClickedFace(), context.getItemInHand())) return InteractionResult.FAIL;
        var link = EntangledLinks.loaded(level, context.getClickedPos()).orElse(null);
        if (link == null || !player.getUUID().equals(link.owner())) return InteractionResult.FAIL;
        var stack = context.getItemInHand(); var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        var saved = data.getCompound(BINDING);
        boolean paired = false;
        if (saved.isPresent() && saved.get().getString("dimension").orElse("").equals(level.dimension().identifier().toString())) {
            var origin = BlockPos.of(saved.get().getLongOr("position", 0));
            paired = EntangledLinks.pair(level, origin, context.getClickedPos(), player.getUUID());
        }
        if (paired) data.remove(BINDING);
        else { var origin = new CompoundTag(); origin.putString("dimension", level.dimension().identifier().toString()); origin.putLong("position", context.getClickedPos().asLong()); data.put(BINDING, origin); }
        if (data.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA); else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        if (player instanceof net.minecraft.server.level.ServerPlayer server) server.sendOverlayMessage(Component.translatable(paired ? "item.rematter.entangled_link.paired" : "item.rematter.entangled_link.selected", EntangledLinks.MAX_DISTANCE));
        return InteractionResult.SUCCESS;
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.rematter.entangled_link.hint", EntangledLinks.MAX_DISTANCE));
        if (stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains(BINDING)) tooltip.accept(Component.translatable("item.rematter.entangled_link.binding"));
    }
}
