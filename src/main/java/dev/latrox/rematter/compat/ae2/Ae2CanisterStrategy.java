package dev.latrox.rematter.compat.ae2;

import appeng.api.behaviors.ContainerItemStrategy;
import appeng.api.config.Actionable;
import appeng.api.stacks.GenericStack;
import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.ModContent;
import dev.latrox.rematter.content.ModSounds;
import dev.latrox.rematter.content.PressurizedCanisterData;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Allows filling and emptying {@code rematter:pressurized_canister} directly on AE2 ME Terminals,
 * Crafting Terminals, Wireless Terminals, and generic MatterKey filter/config slots.
 */
public final class Ae2CanisterStrategy implements ContainerItemStrategy<MatterKey, Ae2CanisterStrategy.Context> {
    public record Context(Supplier<ItemStack> getter, Consumer<ItemStack> setter) {
        ItemStack get() { return getter.get(); }
        void set(ItemStack stack) { setter.accept(stack); }
    }

    @Override
    public @Nullable GenericStack getContainedStack(ItemStack stack) {
        if (!stack.is(ModContent.PRESSURIZED_CANISTER.get()) || stack.getCount() != 1) return null;
        if (!(PressurizedCanisterData.read(stack) instanceof PressurizedCanisterData.State.Matter matter)) return null;
        if (!PressurizedCanisterData.validMatter(matter.key()) || !ReMatter.chemistry().isKnown(matter.key())) return null;
        return new GenericStack(MatterKey.of(matter.key()), matter.amount());
    }

    @Override
    public @Nullable Context findCarriedContext(Player player, AbstractContainerMenu menu) {
        ItemStack carried = menu.getCarried();
        if (!carried.is(ModContent.PRESSURIZED_CANISTER.get()) || carried.getCount() != 1) return null;
        return new Context(menu::getCarried, menu::setCarried);
    }

    @Override
    public @Nullable Context findPlayerSlotContext(Player player, int slot) {
        var inventory = player.getInventory();
        if (slot < 0 || slot >= inventory.getContainerSize()) return null;
        ItemStack stack = inventory.getItem(slot);
        if (!stack.is(ModContent.PRESSURIZED_CANISTER.get()) || stack.getCount() != 1) return null;
        return new Context(() -> inventory.getItem(slot), updated -> inventory.setItem(slot, updated));
    }

    @Override
    public @Nullable GenericStack getExtractableContent(Context context) {
        return getContainedStack(context.get());
    }

    @Override
    public long extract(Context context, MatterKey what, long amount, Actionable mode) {
        if (amount <= 0 || !PressurizedCanisterData.validMatter(what.substance())
                || !ReMatter.chemistry().isKnown(what.substance())) {
            return 0;
        }
        ItemStack current = context.get();
        if (!current.is(ModContent.PRESSURIZED_CANISTER.get()) || current.getCount() != 1) return 0;
        if (!(PressurizedCanisterData.read(current) instanceof PressurizedCanisterData.State.Matter matter)
                || !matter.key().equals(what.substance())) {
            return 0;
        }
        int moved = (int) Math.min(amount, matter.amount());
        if (moved <= 0) return 0;
        if (mode == Actionable.MODULATE) {
            ItemStack updated = current.copy();
            PressurizedCanisterData.writeMatter(updated, matter.key(), matter.amount() - moved);
            context.set(updated);
        }
        return moved;
    }

    @Override
    public long insert(Context context, MatterKey what, long amount, Actionable mode) {
        if (amount <= 0 || !PressurizedCanisterData.validMatter(what.substance())
                || !ReMatter.chemistry().isKnown(what.substance())) {
            return 0;
        }
        ItemStack current = context.get();
        if (!current.is(ModContent.PRESSURIZED_CANISTER.get()) || current.getCount() != 1) return 0;
        int existing;
        switch (PressurizedCanisterData.read(current)) {
            case PressurizedCanisterData.State.Empty ignored -> existing = 0;
            case PressurizedCanisterData.State.Matter matter -> {
                if (!matter.key().equals(what.substance())) return 0;
                existing = matter.amount();
            }
            default -> {
                return 0;
            }
        }
        int room = PressurizedCanisterData.CAPACITY - existing;
        int accepted = (int) Math.min(amount, room);
        if (accepted <= 0) return 0;
        if (mode == Actionable.MODULATE) {
            ItemStack updated = current.copy();
            PressurizedCanisterData.writeMatter(updated, what.substance(), existing + accepted);
            context.set(updated);
        }
        return accepted;
    }

    @Override
    public void playFillSound(Player player, MatterKey what) {
        ModSounds.play(player.level(), player.blockPosition(), ModSounds.CANISTER_FILL);
    }

    @Override
    public void playEmptySound(Player player, MatterKey what) {
        ModSounds.play(player.level(), player.blockPosition(), ModSounds.CANISTER_EMPTY);
    }
}
