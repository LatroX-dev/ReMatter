package dev.latrox.rematter.celltransfer;

import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;

/** Portable Cells retain all quantities and per-Cell policies; the station adds no resource currency. */
public final class CellTransferStationItem extends BlockItem {
    public CellTransferStationItem(CellTransferStationBlock block,Properties properties){super(block,properties.stacksTo(1));}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,TooltipDisplay display,Consumer<Component> tooltip,TooltipFlag flag){
        tooltip.accept(Component.translatable("tooltip.rematter.cell_transfer_station.usage"));
        var component=stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if(component!=null){
            var decoded=context.registries()==null?java.util.Optional.<CellTransferData.Loaded>empty():CellTransferData.read(component.copyTagWithoutId(),context.registries());
            if(decoded.isEmpty())tooltip.accept(Component.translatable("tooltip.rematter.cell_transfer_station.locked"));
            else tooltip.accept(Component.translatable("tooltip.rematter.cell_transfer_station.stored_cells",decoded.get().items().stream().filter(s->!s.isEmpty()).count()));
        }
    }
}
