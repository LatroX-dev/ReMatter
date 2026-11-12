package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.MatterInventory;
import dev.latrox.rematter.chemistry.SubstanceStack;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** One portable, exact substance inventory. A cell contains resources, not recipe permissions. */
public final class MatterCellItem extends Item {
    private final long capacity;
    public MatterCellItem(Properties properties, long capacity) {
        super(properties.stacksTo(1));
        if (capacity <= 0) throw new IllegalArgumentException("Cell capacity must be positive");
        this.capacity = capacity;
    }
    public long capacity() { return capacity; }
    public Optional<MatterInventory> open(ItemStack stack) {
        if (stack.getItem() != this || stack.getCount() != 1) return Optional.empty();
        var data = stack.get(MatterCellData.TYPE.get());
        return data == null ? Optional.of(new MatterInventory(capacity, MatterCellData.MAX_TYPES)) : data.open(capacity);
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                          Consumer<Component> tooltip, TooltipFlag flags) {
        var inventory = open(stack);
        if (inventory.isEmpty()) {
            tooltip.accept(Component.translatable("item.rematter.matter_cell.locked"));
            tooltip.accept(Component.translatable("item.rematter.matter_cell.capacity",capacity));
            return;
        }
        // One read of the opened copy keeps the total, the type count and the rows consistent.
        var snapshot=inventory.get().snapshot();
        var rows=snapshot.contents();
        long total=rows.stream().mapToLong(SubstanceStack::quantity).sum();
        tooltip.accept(Component.translatable("item.rematter.matter_cell.usage",total,capacity));
        tooltip.accept(Component.translatable("item.rematter.matter_cell.types",rows.size(),MatterCellData.MAX_TYPES));
        var status=CellStatus.of(true,true,total,capacity,rows.size(),MatterCellData.MAX_TYPES);
        tooltip.accept(Component.translatable("item.rematter.matter_cell.light",
                Component.translatable("cell_status.rematter."+status.name().toLowerCase(java.util.Locale.ROOT))));
        tooltip.accept(Component.translatable("item.rematter.matter_cell.transfer_hint"));
        var registry=ChemistryText.catalogue(context.level());
        rows.stream().limit(3).forEach(row -> tooltip.accept(ChemistryText.row(registry,row.key(),row.quantity())));
        if(rows.size()>3)tooltip.accept(Component.translatable("item.rematter.matter_cell.more",rows.size()-3));
    }
    @Override public boolean overrideOtherStackedOnMe(ItemStack stack,ItemStack other,Slot slot,ClickAction action,Player player,SlotAccess carried){
        if(action!=ClickAction.SECONDARY||!(other.getItem() instanceof MatterCellItem))return false;
        // A recognized gesture also consumes a no-op, so a full or locked cell never falls through to a vanilla swap.
        if(player.level() instanceof ServerLevel level&&!player.isSpectator()&&Transaction.getCurrentOpenedTransaction()==null){
            try(var tx=Transaction.openRoot()){
                long moved=MatterCellTransfers.move(level,other,stack,carried::get,slot::getItem,
                        ()->!player.isSpectator()&&slot.container==player.getInventory()
                                &&slot.allowModification(player)&&slot.mayPlace(stack),tx);
                if(moved>0){tx.commit();slot.setChanged();}
            }
        }
        return true;
    }

}
