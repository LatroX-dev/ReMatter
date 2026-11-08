package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.MatterInventory;
import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.network.ClientChemistry;
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
        var matter=inventory.get();
        tooltip.accept(Component.translatable("item.rematter.matter_cell.usage",matter.total(),capacity));
        tooltip.accept(Component.translatable("item.rematter.matter_cell.types",matter.typeCount(),MatterCellData.MAX_TYPES));
        var registry=context.level()!=null&&context.level().isClientSide()?ClientChemistry.current():ReMatter.chemistry();
        matter.contents().entrySet().stream().limit(3).forEach(entry -> {
            var definition=registry.definition(entry.getKey().id()).orElse(null);
            Component name=definition==null?Component.literal(entry.getKey().id().toString())
                    :Component.translatableWithFallback(definition.translationKey(),entry.getKey().id().toString());
            if (definition != null && !definition.symbol().isBlank()) name = name.copy().append(" (" + definition.symbol().strip() + ")");
            tooltip.accept(name.copy().append(" · "+entry.getKey().phase().name().toLowerCase(java.util.Locale.ROOT)+" · "+entry.getValue()+" u"));
        });
        if(matter.typeCount()>3)tooltip.accept(Component.translatable("item.rematter.matter_cell.more",matter.typeCount()-3));
    }
}
