package dev.latrox.rematter.primal;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** A physically separate reserve. It never implements an ordinary matter storage capability. */
public final class PrimalCellItem extends Item {
    private final long capacity;
    public PrimalCellItem(Properties properties, long capacity) {
        super(properties.stacksTo(1));
        if (capacity <= 0) throw new IllegalArgumentException("Primal capacity must be positive"); this.capacity = capacity;
    }
    public long capacity() { return capacity; }
    @Override public boolean isBarVisible(ItemStack stack) { return true; }
    @Override public int getBarWidth(ItemStack stack) { var view = PrimalCells.view(stack); return view.readable() ? (int)(13L * view.amount() / capacity) : 13; }
    @Override public int getBarColor(ItemStack stack) { return PrimalCells.view(stack).readable() ? 0xc5a9f3 : 0xcb637b; }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        var view = PrimalCells.view(stack);
        tooltip.accept(Component.translatable(view.readable() ? "item.rematter.primal_cell.usage" : "item.rematter.primal_cell.locked", view.amount(), capacity));
        tooltip.accept(Component.translatable("item.rematter.primal_cell.load_hint"));
        tooltip.accept(Component.translatable("item.rematter.primal_cell.separate"));
    }
    @Override public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess carried) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player) || !slot.mayPlace(stack)
                || other.isEmpty() || !(other.getItem() instanceof PrimalCellItem) && PrimalCells.sourceUnits(other) == 0) return false;
        if (!player.level().isClientSide() && !player.isSpectator()) {
            var target = new PrimalCells.Journal(() -> stack);
            try (var transaction = Transaction.openRoot()) {
                long moved = other.getItem() instanceof PrimalCellItem
                        ? PrimalCells.transfer(new PrimalCells.Journal(() -> other), target, Long.MAX_VALUE, transaction)
                        : PrimalCells.load(target, other, other.getCount(), transaction);
                if (moved > 0) {
                    transaction.commit();
                    if(player.level() instanceof net.minecraft.server.level.ServerLevel level)
                        dev.latrox.rematter.guide.GuidanceProgress.award(level,player.getUUID(),dev.latrox.rematter.guide.GuidanceEvent.SOMETHING_OLDER);
                }
            }
            slot.setChanged();
        }
        return true;
    }
}
