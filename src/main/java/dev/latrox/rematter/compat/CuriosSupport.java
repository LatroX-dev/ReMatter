package dev.latrox.rematter.compat;

import dev.latrox.rematter.ReMatter;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

/** Zero-dependency facade for optional Curios API integration; fails closed if Curios is absent or incompatible. */
public final class CuriosSupport {
    private static volatile boolean enabled;

    public record CurioSlot(String identifier, int index, ItemStack stack) {}

    private CuriosSupport() {}

    public static void register(IEventBus modBus) {
        if (!ModList.get().isLoaded("curios")) return;
        try {
            dev.latrox.rematter.compat.curios.CuriosCompat.register(modBus);
            enabled = true;
        } catch (LinkageError incompatible) {
            enabled = false;
            ReMatter.LOGGER.warn("Curios integration is unavailable for this API version", incompatible);
        }
    }

    public static boolean isEnabled() { return enabled; }

    public static Optional<CurioSlot> findWirelessTerminal(Player player) {
        if (!enabled) return Optional.empty();
        try {
            return dev.latrox.rematter.compat.curios.CuriosCompat.findWirelessTerminal(player);
        } catch (LinkageError incompatible) {
            enabled = false;
            return Optional.empty();
        }
    }

    public static ItemStack curioStack(Player player, String identifier, int index) {
        if (!enabled || identifier == null || index < 0) return ItemStack.EMPTY;
        try {
            return dev.latrox.rematter.compat.curios.CuriosCompat.curioStack(player, identifier, index);
        } catch (LinkageError incompatible) {
            enabled = false;
            return ItemStack.EMPTY;
        }
    }

    public static int chargeCuriosItems(Player player, ItemStack batteryStack, int budget) {
        if (!enabled || budget <= 0) return 0;
        try {
            return dev.latrox.rematter.compat.curios.CuriosCompat.chargeCuriosItems(player, batteryStack, budget);
        } catch (LinkageError incompatible) {
            enabled = false;
            return 0;
        }
    }
}
