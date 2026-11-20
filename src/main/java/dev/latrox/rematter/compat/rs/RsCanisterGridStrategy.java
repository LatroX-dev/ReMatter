package dev.latrox.rematter.compat.rs;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.node.grid.GridExtractMode;
import com.refinedmods.refinedstorage.api.network.node.grid.GridInsertMode;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.api.storage.ExtractableStorage;
import com.refinedmods.refinedstorage.api.storage.InsertableStorage;
import com.refinedmods.refinedstorage.common.api.grid.Grid;
import com.refinedmods.refinedstorage.common.api.grid.strategy.GridExtractionStrategy;
import com.refinedmods.refinedstorage.common.api.grid.strategy.GridInsertionStrategy;
import com.refinedmods.refinedstorage.common.api.support.resource.PlatformResourceKey;
import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.content.ModContent;
import dev.latrox.rematter.content.ModSounds;
import dev.latrox.rematter.content.PressurizedCanisterData;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Refined Storage Grid insertion and extraction strategies for {@code rematter:pressurized_canister}
 * without ever materializing a synthetic cursor item for raw matter.
 */
public final class RsCanisterGridStrategy {
    private RsCanisterGridStrategy() {}

    public static boolean canExtractToCanister(ItemStack carried, SubstanceKey substance) {
        if (!PressurizedCanisterData.validMatter(substance)) return false;
        if (carried.isEmpty()) return true;
        return canInsertIntoStack(carried, substance);
    }

    static boolean canInsertIntoStack(ItemStack stack, SubstanceKey substance) {
        if (!stack.is(ModContent.PRESSURIZED_CANISTER.get()) || stack.getCount() != 1) return false;
        return switch (PressurizedCanisterData.read(stack)) {
            case PressurizedCanisterData.State.Empty ignored -> true;
            case PressurizedCanisterData.State.Matter matter ->
                    matter.key().equals(substance) && matter.amount() < PressurizedCanisterData.CAPACITY;
            default -> false;
        };
    }

    public static GridExtractionStrategy extraction(AbstractContainerMenu menu, ServerPlayer player, Grid grid) {
        return (PlatformResourceKey resource, GridExtractMode mode, boolean cursor) -> {
            if (!(resource instanceof RsMatterKey matterKey)) return false;
            SubstanceKey target = matterKey.substance();
            if (!PressurizedCanisterData.validMatter(target) || !ReMatter.chemistry().isKnown(target)) {
                return true;
            }
            CanisterSlot canister = findTargetCanister(menu, player.getInventory(), target, cursor);
            if (canister == null) return true;
            boolean moved = grid.createOperations(RsMatterKey.TYPE, player)
                    .extract(matterKey, mode, canister.asInsertable());
            if (moved) {
                ModSounds.play(player.level(), player.blockPosition(), ModSounds.CANISTER_FILL);
                menu.broadcastChanges();
            }
            return true;
        };
    }

    public static GridInsertionStrategy insertion(AbstractContainerMenu menu, ServerPlayer player, Grid grid) {
        return new GridInsertionStrategy() {
            @Override
            public boolean onInsert(GridInsertMode mode, boolean tryAlternatives) {
                ItemStack carried = menu.getCarried();
                if (!carried.is(ModContent.PRESSURIZED_CANISTER.get()) || carried.getCount() != 1) return false;
                if (!(PressurizedCanisterData.read(carried) instanceof PressurizedCanisterData.State.Matter matter)) {
                    // Fluid and empty canisters fall through to Refined Storage's fluid/item strategies.
                    return false;
                }
                if (!PressurizedCanisterData.validMatter(matter.key())
                        || !ReMatter.chemistry().isKnown(matter.key())) {
                    return true;
                }
                CanisterSlot slot = new CanisterSlot(menu::getCarried, menu::setCarried);
                boolean moved = grid.createOperations(RsMatterKey.TYPE, player)
                        .insert(new RsMatterKey(matter.key()), mode, slot.asExtractable());
                if (moved) {
                    ModSounds.play(player.level(), player.blockPosition(), ModSounds.CANISTER_EMPTY);
                    menu.broadcastChanges();
                }
                return true;
            }

            @Override
            public boolean onTransfer(int slotIndex) {
                if (slotIndex < 0 || slotIndex >= menu.slots.size()) return false;
                Slot slot = menu.slots.get(slotIndex);
                if (!slot.mayPickup(player)) return false;
                ItemStack stack = slot.getItem();
                if (!stack.is(ModContent.PRESSURIZED_CANISTER.get()) || stack.getCount() != 1) return false;
                if (!(PressurizedCanisterData.read(stack) instanceof PressurizedCanisterData.State.Matter matter)) {
                    return false;
                }
                if (!PressurizedCanisterData.validMatter(matter.key())
                        || !ReMatter.chemistry().isKnown(matter.key())) {
                    return false;
                }
                CanisterSlot canister = new CanisterSlot(slot::getItem, updated -> {
                    slot.setByPlayer(updated);
                    slot.setChanged();
                });
                boolean moved = grid.createOperations(RsMatterKey.TYPE, player)
                        .insert(new RsMatterKey(matter.key()), GridInsertMode.ENTIRE_RESOURCE, canister.asExtractable());
                if (moved) {
                    ModSounds.play(player.level(), player.blockPosition(), ModSounds.CANISTER_EMPTY);
                    menu.broadcastChanges();
                    return true;
                }
                return false;
            }
        };
    }

    private static @Nullable CanisterSlot findTargetCanister(
            AbstractContainerMenu menu, Inventory inventory, SubstanceKey substance, boolean cursor) {
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()) {
            return canInsertIntoStack(carried, substance)
                    ? new CanisterSlot(menu::getCarried, menu::setCarried)
                    : null;
        }
        // When carried hand is empty (either shift-extract or empty-cursor click), fill a matching or empty canister in player inventory.
        int emptySlot = -1;
        int limit = Math.min(36, inventory.getContainerSize());
        for (int i = 0; i < limit; i++) {
            ItemStack candidate = inventory.getItem(i);
            if (!candidate.is(ModContent.PRESSURIZED_CANISTER.get()) || candidate.getCount() != 1) continue;
            switch (PressurizedCanisterData.read(candidate)) {
                case PressurizedCanisterData.State.Matter matter -> {
                    if (matter.key().equals(substance) && matter.amount() < PressurizedCanisterData.CAPACITY) {
                        int idx = i;
                        return new CanisterSlot(() -> inventory.getItem(idx), updated -> inventory.setItem(idx, updated));
                    }
                }
                case PressurizedCanisterData.State.Empty ignored -> {
                    if (emptySlot < 0) emptySlot = i;
                }
                default -> {}
            }
        }
        if (emptySlot >= 0) {
            int idx = emptySlot;
            return new CanisterSlot(() -> inventory.getItem(idx), updated -> inventory.setItem(idx, updated));
        }
        return null;
    }

    record CanisterSlot(Supplier<ItemStack> getter, Consumer<ItemStack> setter) {
        InsertableStorage asInsertable() {
            return (ResourceKey resource, long amount, Action action, Actor actor) -> {
                if (amount <= 0 || !(resource instanceof RsMatterKey matterKey)) return 0;
                SubstanceKey key = matterKey.substance();
                if (!PressurizedCanisterData.validMatter(key) || !ReMatter.chemistry().isKnown(key)) return 0;
                ItemStack current = getter.get();
                if (!current.is(ModContent.PRESSURIZED_CANISTER.get()) || current.getCount() != 1) return 0;
                int existing;
                switch (PressurizedCanisterData.read(current)) {
                    case PressurizedCanisterData.State.Empty ignored -> existing = 0;
                    case PressurizedCanisterData.State.Matter matter -> {
                        if (!matter.key().equals(key)) return 0;
                        existing = matter.amount();
                    }
                    default -> {
                        return 0;
                    }
                }
                int accepted = (int) Math.min(amount, PressurizedCanisterData.CAPACITY - existing);
                if (accepted <= 0) return 0;
                if (action == Action.EXECUTE) {
                    ItemStack updated = current.copy();
                    PressurizedCanisterData.writeMatter(updated, key, existing + accepted);
                    setter.accept(updated);
                }
                return accepted;
            };
        }

        ExtractableStorage asExtractable() {
            return (ResourceKey resource, long amount, Action action, Actor actor) -> {
                if (amount <= 0 || !(resource instanceof RsMatterKey matterKey)) return 0;
                SubstanceKey key = matterKey.substance();
                if (!PressurizedCanisterData.validMatter(key) || !ReMatter.chemistry().isKnown(key)) return 0;
                ItemStack current = getter.get();
                if (!current.is(ModContent.PRESSURIZED_CANISTER.get()) || current.getCount() != 1) return 0;
                if (!(PressurizedCanisterData.read(current) instanceof PressurizedCanisterData.State.Matter matter)
                        || !matter.key().equals(key)) {
                    return 0;
                }
                int extracted = (int) Math.min(amount, matter.amount());
                if (extracted <= 0) return 0;
                if (action == Action.EXECUTE) {
                    ItemStack updated = current.copy();
                    PressurizedCanisterData.writeMatter(updated, key, matter.amount() - extracted);
                    setter.accept(updated);
                }
                return extracted;
            };
        }
    }
}
