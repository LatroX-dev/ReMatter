package dev.latrox.rematter.energy;

import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;

/** A cube's energy and charging inventory travel as one portable block-entity state, never as a second FE store. */
public final class EnergyCubeItem extends BlockItem {
    private final EnergyCubeTier tier;
    public EnergyCubeItem(EnergyCubeBlock block,Properties properties){super(block,properties.stacksTo(1));tier=block.tier();}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,TooltipDisplay display,Consumer<Component> tooltip,TooltipFlag flag){
        var data=stack.get(DataComponents.BLOCK_ENTITY_DATA);
        int energy=0;
        if(data!=null){
            var raw=data.copyTagWithoutId();
            var decoded=context.registries()==null?java.util.Optional.<java.util.List<ItemStack>>empty():EnergyCubeData.items(raw,context.registries());
            int count=decoded.map(java.util.List::size).orElse(-1);
            if(EnergyCubeData.rejection(raw,tier,decoded.isPresent(),count)!=null)tooltip.accept(Component.translatable("tooltip.rematter.energy_cube.locked"));
            else energy=raw.getIntOr("energy",0);
        }
        tooltip.accept(Component.translatable("tooltip.rematter.energy_cube.capacity",energy,tier.capacity()));
        tooltip.accept(Component.translatable("tooltip.rematter.energy_cube.rate",tier.transferRate()));
        tooltip.accept(Component.translatable("tooltip.rematter.energy_cube.slots",tier.chargingSlots()));
    }
}
