package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Binds a Wireless Matter Terminal when right-clicking a Wireless Access Point. */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class WirelessTerminalInteractions {
    private WirelessTerminalInteractions() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof WirelessMatterTerminalItem)) return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!(level.getBlockState(pos).getBlock() instanceof WirelessAccessPointBlock)) return;
        Player player = event.getEntity();
        if (player == null || player.isSpectator()) return;
        if (level instanceof ServerLevel server) {
            if (!(server.getBlockEntity(pos) instanceof WirelessAccessPointBlockEntity ap) || !ap.interact(player)) {
                event.setCancellationResult(InteractionResult.FAIL);
                event.setCanceled(true);
                return;
            }
            stack.set(WirelessTerminalData.LINK.get(), WirelessTerminalData.of(server.dimension(), pos));
            player.sendOverlayMessage(Component.translatable("message.rematter.wireless.bound", pos.getX(), pos.getY(), pos.getZ()));
            ModSounds.play(server, pos, ModSounds.WIRELESS_LINK);
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
