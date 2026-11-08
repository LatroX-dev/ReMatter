package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.MatterInventory;
import dev.latrox.rematter.chemistry.SubstanceStack;
import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.network.ClientChemistry;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

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
        var registry=context.level()!=null&&context.level().isClientSide()?ClientChemistry.current():ReMatter.chemistry();
        rows.stream().limit(3).forEach(row -> {
            var key=row.key();
            var phase=Component.translatable("item.rematter.matter_cell.phase."+key.phase().name().toLowerCase(Locale.ROOT));
            var definition=registry.definition(key.id()).orElse(null);
            // A missing definition keeps its stored amount and stays visibly unknown until it is resolved.
            Component name=definition==null?Component.translatable("substance.rematter.unknown",key.id().toString(),phase)
                    :Component.translatableWithFallback(definition.translationKey(),key.id().toString());
            if (definition != null && !definition.symbol().isBlank()) name = Component.translatable("item.rematter.matter_cell.symbol",name,definition.symbol().strip());
            tooltip.accept(Component.translatable("item.rematter.matter_cell.row",name,phase,row.quantity()));
        });
        if(rows.size()>3)tooltip.accept(Component.translatable("item.rematter.matter_cell.more",rows.size()-3));
    }
}
