package dev.latrox.rematter.energy;

import dev.latrox.rematter.content.ModContent;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TypedEntityData;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/** A cube's energy and item bays travel as one portable block-entity state, never as a second FE store. */
public final class EnergyCubeItem extends BlockItem {
    private static final int BAR_COLOR = 0xEA4848;
    private final EnergyCubeTier tier;

    public EnergyCubeItem(EnergyCubeBlock block, Properties properties) {
        super(block, properties.stacksTo(1));
        tier = block.tier();
    }

    public EnergyCubeTier tier() { return tier; }

    public int storedEnergy(ItemStack stack) {
        var data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data == null) return 0;
        var raw = data.copyTagWithoutId();
        if (!(raw.get("schema") instanceof IntTag) || raw.getIntOr("schema", -1) != EnergyCubeData.SCHEMA) return 0;
        if (!(raw.get("energy") instanceof IntTag)) return 0;
        int energy = raw.getIntOr("energy", 0);
        return energy < 0 || energy > tier.capacity() ? 0 : energy;
    }

    @Override public boolean isBarVisible(ItemStack stack) { return true; }

    @Override public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * storedEnergy(stack) / Math.max(1, tier.capacity()));
    }

    @Override public int getBarColor(ItemStack stack) { return BAR_COLOR; }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    /** Exposes standard NeoForge item FE capability directly over the portable BLOCK_ENTITY_DATA state. */
    public static @Nullable EnergyHandler itemEnergyHandler(ItemStack stack, ItemAccess access) {
        if (!(stack.getItem() instanceof EnergyCubeItem cubeItem)) return null;
        EnergyCubeTier tier = cubeItem.tier;
        return new EnergyHandler() {
            private @Nullable CompoundTag writableBase(ItemResource resource) {
                if (!resource.is(cubeItem)) return null;
                var existing = resource.get(DataComponents.BLOCK_ENTITY_DATA);
                if (existing == null) {
                    var fresh = new CompoundTag();
                    fresh.putInt("schema", EnergyCubeData.SCHEMA);
                    fresh.putInt("energy", 0);
                    fresh.putInt("side_modes", EnergyCubeSideMode.OUTPUT.ordinal());
                    var emptyItems = new ListTag();
                    for (int i = 0; i < tier.chargingSlots(); i++) emptyItems.add(new CompoundTag());
                    fresh.put("items", emptyItems);
                    return fresh;
                }
                var raw = existing.copyTagWithoutId();
                boolean itemsShape = raw.get("items") instanceof ListTag list && list.size() == tier.chargingSlots();
                if (EnergyCubeData.rejection(raw, tier, itemsShape, itemsShape ? tier.chargingSlots() : -1) != null) {
                    return null;
                }
                return raw;
            }

            @Override
            public long getAmountAsLong() {
                if (access.getAmount() != 1) return 0;
                var raw = writableBase(access.getResource());
                return raw == null ? 0 : raw.getIntOr("energy", 0);
            }

            @Override
            public long getCapacityAsLong() {
                if (access.getAmount() != 1) return 0;
                return writableBase(access.getResource()) == null ? 0 : tier.capacity();
            }

            @Override
            public int insert(int amount, TransactionContext tx) {
                TransferPreconditions.checkNonNegative(amount);
                if (access.getAmount() != 1 || amount == 0) return 0;
                ItemResource resource = access.getResource();
                var raw = writableBase(resource);
                if (raw == null) return 0;
                int current = raw.getIntOr("energy", 0);
                int added = Math.min(Math.min(amount, tier.transferRate()), tier.capacity() - current);
                if (added <= 0) return 0;
                raw.putInt("energy", current + added);
                ItemResource updated = resource.with(DataComponents.BLOCK_ENTITY_DATA,
                        TypedEntityData.of(ModContent.ENERGY_CUBE_ENTITY.get(), raw));
                return access.exchange(updated, 1, tx) == 1 ? added : 0;
            }

            @Override
            public int extract(int amount, TransactionContext tx) {
                TransferPreconditions.checkNonNegative(amount);
                if (access.getAmount() != 1 || amount == 0) return 0;
                ItemResource resource = access.getResource();
                var raw = writableBase(resource);
                if (raw == null) return 0;
                int current = raw.getIntOr("energy", 0);
                int drained = Math.min(Math.min(amount, tier.transferRate()), current);
                if (drained <= 0) return 0;
                raw.putInt("energy", current - drained);
                ItemResource updated = resource.with(DataComponents.BLOCK_ENTITY_DATA,
                        TypedEntityData.of(ModContent.ENERGY_CUBE_ENTITY.get(), raw));
                return access.exchange(updated, 1, tx) == 1 ? drained : 0;
            }
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        var data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        int energy = 0;
        if (data != null) {
            var raw = data.copyTagWithoutId();
            var decoded = context.registries() == null ? Optional.<List<ItemStack>>empty() : EnergyCubeData.items(raw, context.registries());
            var decodedDischarge = context.registries() == null ? Optional.<List<ItemStack>>empty() : EnergyCubeData.dischargeItems(raw, context.registries());
            int count = decoded.map(List::size).orElse(-1);
            boolean hasDischarge = raw.contains("discharge_items");
            int dischargeCount = hasDischarge ? decodedDischarge.map(List::size).orElse(-1) : tier.dischargingSlots();
            if (EnergyCubeData.rejection(raw, tier, decoded.isPresent(), count, decodedDischarge.isPresent(), dischargeCount) != null) {
                tooltip.accept(Component.translatable("tooltip.rematter.energy_cube.locked"));
            } else {
                energy = raw.getIntOr("energy", 0);
            }
        }
        tooltip.accept(Component.translatable("tooltip.rematter.energy_cube.capacity", energy, tier.capacity()));
        tooltip.accept(Component.translatable("tooltip.rematter.energy_cube.rate", tier.transferRate()));
        tooltip.accept(Component.translatable("tooltip.rematter.energy_cube.slots", tier.chargingSlots()));
    }
}
