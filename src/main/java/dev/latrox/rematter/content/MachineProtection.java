package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/** A machine owner's inventory cannot be accessed indirectly by breaking the block. */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class MachineProtection {
    private MachineProtection() {}
    @SubscribeEvent public static void beforeBreak(BreakBlockEvent event) {
        if (event.getLevel().getBlockEntity(event.getPos()) instanceof MachineBlockEntity machine
                && (!machine.canUse(event.getPlayer()) || !machine.canRemoveCell())) {
            event.setCanceled(true);
            event.setNotifyClient(true);
        }
        if (event.getLevel() instanceof ServerLevel level) {
            var state = level.getBlockState(event.getPos());
            if ((state.is(ModContent.REACTOR_CASING.get()) || state.is(ModContent.REACTOR_GLASS.get()) || state.is(ModContent.MACHINE_PORT.get())
                    || state.getBlock() instanceof IndustrialModuleBlock)
                    && IndustrialStructure.controllersUsing(level, event.getPos(), false).stream().anyMatch(machine -> !machine.canUse(event.getPlayer()))) {
                event.setCanceled(true); event.setNotifyClient(true);
            }
        }
    }
}
