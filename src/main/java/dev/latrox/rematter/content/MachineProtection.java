package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/** A machine owner's inventory cannot be accessed indirectly by breaking the block. */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class MachineProtection {
    private MachineProtection() {}
    @SubscribeEvent public static void beforeBreak(BreakBlockEvent event) {
        if (event.getLevel().getBlockEntity(event.getPos()) instanceof MachineBlockEntity machine && !machine.canUse(event.getPlayer())) {
            event.setCanceled(true);
            event.setNotifyClient(true);
        }
    }
}
