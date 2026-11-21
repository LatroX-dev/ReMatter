package dev.latrox.rematter.compat.curios;

import dev.latrox.rematter.compat.CuriosSupport;
import dev.latrox.rematter.content.ModContent;
import dev.latrox.rematter.content.PortableEnergyCellItem;
import dev.latrox.rematter.content.WirelessMatterTerminalItem;
import dev.latrox.rematter.content.WirelessTerminalData;
import dev.latrox.rematter.energy.EnergyCubeItem;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/** Optional Curios 15.0.0 bridge for Wireless Matter Terminal and Portable Energy Cell. */
public final class CuriosCompat {
    private CuriosCompat() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(CuriosCompat::onCommonSetup);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CuriosApi.registerCurio(ModContent.WIRELESS_MATTER_TERMINAL.get(), new ICurioItem() {
                @Override public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) { return false; }
            });
            CuriosApi.registerCurio(ModContent.PORTABLE_ENERGY_CELL.get(), new ICurioItem() {
                @Override public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) { return false; }
                @Override public void curioTick(SlotContext slotContext, ItemStack stack) {
                    PortableEnergyCellItem.tickInCurio(stack, slotContext.entity());
                }
            });
        });
    }

    public static Optional<CuriosSupport.CurioSlot> findWirelessTerminal(Player player) {
        var handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler == null) return Optional.empty();
        var results = handler.findCurios(ModContent.WIRELESS_MATTER_TERMINAL.get());
        if (results.isEmpty()) return Optional.empty();
        SlotResult fallback = null;
        SlotResult linkedFallback = null;
        for (SlotResult result : results) {
            ItemStack stack = result.stack();
            if (fallback == null) fallback = result;
            var link = stack.get(WirelessTerminalData.LINK.get());
            boolean linked = link != null && link.target().isPresent();
            if (linked && linkedFallback == null) linkedFallback = result;
            if (linked && WirelessMatterTerminalItem.energy(stack) >= WirelessMatterTerminalItem.OPEN_COST) {
                return Optional.of(toSlot(result));
            }
        }
        SlotResult chosen = linkedFallback != null ? linkedFallback : fallback;
        return chosen == null ? Optional.empty() : Optional.of(toSlot(chosen));
    }

    public static ItemStack curioStack(Player player, String identifier, int index) {
        var handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler == null) return ItemStack.EMPTY;
        return handler.findCurio(identifier, index).map(SlotResult::stack).orElse(ItemStack.EMPTY);
    }

    public static int chargeCuriosItems(Player player, ItemStack batteryStack, int budget) {
        if (budget <= 0) return 0;
        var handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler == null) return 0;
        int remaining = budget;
        int sentTotal = 0;
        for (SlotResult result : handler.findCurios(stack -> !stack.isEmpty())) {
            if (remaining <= 0) break;
            ItemStack candidate = result.stack();
            if (candidate.isEmpty() || candidate == batteryStack
                    || candidate.getItem() instanceof PortableEnergyCellItem
                    || candidate.getItem() instanceof EnergyCubeItem) {
                continue;
            }
            var sink = result.getItemAccess().getCapability(Capabilities.Energy.ITEM);
            if (sink == null || sink.getAmountAsLong() >= sink.getCapacityAsLong()) continue;
            int moved = 0;
            try (var tx = Transaction.openRoot()) {
                moved = sink.insert(remaining, tx);
                if (moved > 0) tx.commit();
            }
            if (moved > 0) {
                remaining -= moved;
                sentTotal += moved;
            }
        }
        return sentTotal;
    }

    private static CuriosSupport.CurioSlot toSlot(SlotResult result) {
        return new CuriosSupport.CurioSlot(result.slotContext().identifier(), result.slotContext().index(), result.stack());
    }
}
