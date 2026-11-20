package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.VoidPolicy;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Mounted overflow sink cartridge for the Matter Drive.
 * Finite Matter Cells always fill to capacity first; only remaining excess of accepted substances is discarded.
 */
public final class VoidMatterCellItem extends Item {
    public VoidMatterCellItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static Optional<VoidPolicy> policy(ItemStack stack) {
        if (!(stack.getItem() instanceof VoidMatterCellItem) || stack.getCount() != 1) return Optional.empty();
        return CellPartitionData.read(stack)
                .map(spec -> spec.restricted() ? VoidPolicy.only(spec.allowed()) : VoidPolicy.any());
    }

    public static CellStatus status(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof VoidMatterCellItem)) return CellStatus.ABSENT;
        var resolved = policy(stack);
        if (resolved.isEmpty()) return CellStatus.LOCKED;
        var value = resolved.get();
        return !value.unrestricted() && value.allowed().isEmpty() ? CellStatus.EMPTY : CellStatus.STORED;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flags) {
        var partition = CellPartitionData.read(stack);
        if (partition.isEmpty()) {
            tooltip.accept(Component.translatable("item.rematter.matter_cell.locked"));
            return;
        }
        var spec = partition.get();
        var led = status(stack);
        tooltip.accept(Component.translatable("item.rematter.void_matter_cell.overflow_note"));
        tooltip.accept(Component.translatable("item.rematter.matter_cell.light",
                Component.translatable("cell_status.rematter." + led.name().toLowerCase(Locale.ROOT))));
        tooltip.accept(Component.translatable("item.rematter.matter_cell.transfer_hint"));
        if (!spec.restricted()) {
            tooltip.accept(Component.translatable("item.rematter.void_matter_cell.partition_any"));
        } else if (spec.allowed().isEmpty()) {
            tooltip.accept(Component.translatable("item.rematter.void_matter_cell.partition_empty"));
        } else {
            tooltip.accept(Component.translatable("item.rematter.void_matter_cell.partition_only", spec.allowed().size()));
            var registry = ChemistryText.catalogue(context.level());
            spec.allowed().stream().limit(3).forEach(key -> tooltip.accept(ChemistryText.name(registry, key)));
            if (spec.allowed().size() > 3) {
                tooltip.accept(Component.translatable("item.rematter.matter_cell.more", spec.allowed().size() - 3));
            }
        }
    }
}
