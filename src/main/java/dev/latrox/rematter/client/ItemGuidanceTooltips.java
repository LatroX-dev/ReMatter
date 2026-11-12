package dev.latrox.rematter.client;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.guide.ItemGuidance;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = ReMatter.MOD_ID, value = Dist.CLIENT)
public final class ItemGuidanceTooltips {
    private ItemGuidanceTooltips() {}
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event) {
        String item = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).toString();
        ItemGuidance.forItem(item).ifPresent(spec -> {
            event.getToolTip().add(Component.translatable(spec.role().key()).withStyle(ChatFormatting.AQUA));
            for (var hint : spec.hints()) event.getToolTip().add(Component.translatable(hint.key(), hint.arguments().toArray()).withStyle(ChatFormatting.GRAY));
        });
        if (item.equals("rematter:hand_preparator") && event.getItemStack().has(DataComponents.BLOCK_ENTITY_DATA)) {
            var data = event.getItemStack().get(DataComponents.BLOCK_ENTITY_DATA).copyTagWithoutId();
            var items = data.getList("items");
            if (items.isEmpty() || items.get().stream().anyMatch(value -> !(value instanceof net.minecraft.nbt.CompoundTag tag) || !tag.isEmpty()))
                event.getToolTip().add(Component.translatable("tooltip.rematter.guidance.hand_contents").withStyle(ChatFormatting.YELLOW));
        }
    }
}
