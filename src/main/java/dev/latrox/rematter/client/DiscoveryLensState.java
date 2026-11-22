package dev.latrox.rematter.client;

import dev.latrox.rematter.compat.CuriosSupport;
import dev.latrox.rematter.content.ModContent;
import dev.latrox.rematter.primal.PrimalContent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side toggle and possession state for ReMatter spectrometer optics
 * ({@code rematter:spectrometer_lens} and its upgraded {@code rematter:discovery_lens}).
 * Supports wearing either lens in Curios slots, holding it in either hand, or carrying it in the inventory.
 */
public final class DiscoveryLensState {
    private static volatile boolean enabled = true;

    private DiscoveryLensState() {}

    public static boolean enabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean toggle() {
        boolean next = !enabled;
        enabled = next;
        return next;
    }

    public static void reset() {
        enabled = true;
    }

    public static boolean shouldShowHud(boolean lensEnabled, boolean heldInHand, boolean equippedInCurios) {
        return lensEnabled && (heldInHand || equippedInCurios);
    }

    public static boolean shouldShowTooltip(boolean lensEnabled, boolean carriedInInventory, boolean equippedInCurios) {
        return lensEnabled && (carriedInInventory || equippedInCurios);
    }

    public static boolean isSpectrometerLens(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.is(ModContent.SPECTROMETER_LENS.get()) || stack.is(PrimalContent.DISCOVERY_LENS.get());
    }

    public static ItemStack activeHudLens(Player player) {
        if (!enabled || player == null) return ItemStack.EMPTY;
        ItemStack main = player.getMainHandItem();
        if (isSpectrometerLens(main)) return main;
        ItemStack off = player.getOffhandItem();
        if (isSpectrometerLens(off)) return off;
        return CuriosSupport.findSpectrometerLens(player);
    }

    public static boolean heldInHand(Player player) {
        if (player == null) return false;
        return isSpectrometerLens(player.getMainHandItem()) || isSpectrometerLens(player.getOffhandItem());
    }

    public static boolean carriedInInventory(Player player) {
        if (player == null) return false;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isSpectrometerLens(inv.getItem(i))) return true;
        }
        return false;
    }

    public static boolean equippedInCurios(Player player) {
        return player != null && CuriosSupport.hasSpectrometerLens(player);
    }

    public static boolean carriedOrEquipped(Player player) {
        return carriedInInventory(player) || equippedInCurios(player);
    }

    public static boolean hudActive(Player player) {
        return shouldShowHud(enabled, heldInHand(player), equippedInCurios(player));
    }

    public static boolean tooltipActive(Player player) {
        return shouldShowTooltip(enabled, carriedInInventory(player), equippedInCurios(player));
    }
}
