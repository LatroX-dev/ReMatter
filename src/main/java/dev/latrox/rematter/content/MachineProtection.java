package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.celltransfer.CellTransferStationBlockEntity;
import dev.latrox.rematter.energy.EnergyCubeBlockEntity;
import dev.latrox.rematter.energy.EnergyMatrixCellBlock;
import dev.latrox.rematter.energy.EnergyMatrixProviderBlock;
import dev.latrox.rematter.primal.DimensionalEntangledLinkBlockEntity;
import dev.latrox.rematter.primal.EntangledLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import org.jspecify.annotations.Nullable;

/**
 * A device owner's contents and network components cannot be accessed or destroyed indirectly by breaking or
 * exploding the block. Pistons cannot move owned devices, casing, or glass, and explosions spare owned devices
 * and the parts of formed or claimed chambers, since neither names a player.
 */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class MachineProtection {
    private MachineProtection() {}

    /** Shared break-permission check covering every owned ReMatter block and claimed industrial shell part. */
    public static boolean canBreak(ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity be, Player player) {
        if (state.getBlock() instanceof MatterCableBlock && !CableOwners.canRemove(level, pos, player)) {
            return false;
        }
        if (be instanceof MachineBlockEntity machine && (!machine.canRemove(player) || !machine.canRemoveCell())) {
            return false;
        }
        if (be instanceof CellTransferStationBlockEntity station && !station.canRemove(player)) return false;
        if (be instanceof EnergyCubeBlockEntity cube && !cube.canRemove(player)) return false;
        if (be instanceof WirelessAccessPointBlockEntity ap && !ap.canRemove(player)) return false;
        if (be instanceof MatterLevelEmitterBlockEntity emitter && !emitter.canRemove(player)) return false;
        if (be instanceof MatterCableRelayBlockEntity relay && !relay.canRemove(player)) return false;
        if (be instanceof SpatialAnchorBlockEntity anchor && !anchor.canRemove(player)) return false;
        if (be instanceof EntangledLinkBlockEntity link && !link.canRemove(player)) return false;
        if (be instanceof DimensionalEntangledLinkBlockEntity dlink && !dlink.canRemove(player)) return false;
        if (shellPart(state)
                && IndustrialStructure.controllersUsing(level, pos).stream().anyMatch(machine -> !machine.canRemove(player))) {
            return false;
        }
        return true;
    }

    @SubscribeEvent public static void beforeBreak(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        if (!canBreak(level, pos, level.getBlockState(pos), level.getBlockEntity(pos), event.getPlayer())) {
            event.setCanceled(true);
            event.setNotifyClient(true);
        }
    }

    @SubscribeEvent public static void beforeDetonation(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) event.getAffectedBlocks().removeIf(pos -> withstandsExplosion(level, pos));
    }

    static boolean withstandsExplosion(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof MatterCableBlock && CableOwners.owner(level, pos) != null) return true;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MachineBlockEntity machine && machine.networkOwner() != null) return true;
        if (be instanceof CellTransferStationBlockEntity station && station.owner() != null) return true;
        if (be instanceof EnergyCubeBlockEntity cube && cube.owner() != null) return true;
        if (be instanceof WirelessAccessPointBlockEntity ap && ap.owner() != null) return true;
        if (be instanceof MatterLevelEmitterBlockEntity emitter && emitter.owner() != null) return true;
        if (be instanceof MatterCableRelayBlockEntity relay && relay.owner() != null) return true;
        if (be instanceof SpatialAnchorBlockEntity anchor && anchor.owner() != null) return true;
        if (be instanceof EntangledLinkBlockEntity link && link.owner() != null) return true;
        if (be instanceof DimensionalEntangledLinkBlockEntity dlink && dlink.owner() != null) return true;
        return shellPart(state) && !IndustrialStructure.controllersUsing(level, pos).isEmpty();
    }

    static boolean shellPart(BlockState state) {
        return ModContent.isAnyShellBlock(state)
                || state.getBlock() instanceof IndustrialModuleBlock
                || state.getBlock() instanceof EnergyMatrixCellBlock
                || state.getBlock() instanceof EnergyMatrixProviderBlock;
    }
}
