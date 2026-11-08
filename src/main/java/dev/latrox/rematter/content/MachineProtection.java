package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * A machine owner's inventory cannot be accessed indirectly by breaking the block. Pistons cannot move casing or
 * glass, and explosions spare owned machines and the parts of formed or claimed chambers, since neither names a player.
 */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class MachineProtection {
    private MachineProtection() {}
    @SubscribeEvent public static void beforeBreak(BreakBlockEvent event) {
        if (event.getLevel().getBlockEntity(event.getPos()) instanceof MachineBlockEntity machine
                && (!machine.canUse(event.getPlayer()) || !machine.canRemoveCell())) {
            event.setCanceled(true);
            event.setNotifyClient(true);
        }
        if (event.getLevel() instanceof ServerLevel level && shellPart(level.getBlockState(event.getPos()))
                && IndustrialStructure.controllersUsing(level, event.getPos()).stream().anyMatch(machine -> !machine.canUse(event.getPlayer()))) {
            event.setCanceled(true); event.setNotifyClient(true);
        }
    }
    @SubscribeEvent public static void beforeDetonation(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) event.getAffectedBlocks().removeIf(pos -> withstandsExplosion(level, pos));
    }
    private static boolean withstandsExplosion(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof MachineBlockEntity machine && machine.networkOwner() != null) return true;
        return shellPart(level.getBlockState(pos)) && !IndustrialStructure.controllersUsing(level, pos).isEmpty();
    }
    private static boolean shellPart(BlockState state) {
        return state.is(ModContent.REACTOR_CASING.get()) || state.is(ModContent.REACTOR_GLASS.get()) || state.is(ModContent.MACHINE_PORT.get())
                || state.getBlock() instanceof IndustrialModuleBlock;
    }
}
