package dev.latrox.rematter.content;

import dev.latrox.rematter.celltransfer.CellTransferStationBlockEntity;
import dev.latrox.rematter.energy.EnergyCubeBlockEntity;
import dev.latrox.rematter.network.WrenchDiagnosticPayload;
import dev.latrox.rematter.primal.DimensionalEntangledLinkBlockEntity;
import dev.latrox.rematter.primal.EntangledLinkBlockEntity;
import dev.latrox.rematter.primal.EntangledLinks;
import dev.latrox.rematter.content.preparation.PreparationTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jspecify.annotations.Nullable;

/**
 * Builds server-authoritative, ownership-gated {@link WrenchDiagnosticPayload} snapshots for ReMatter blocks.
 */
public final class WrenchDiagnostics {
    private WrenchDiagnostics() {}

    public static @Nullable WrenchDiagnosticPayload inspect(
            ServerLevel level, BlockPos pos, Direction face, Player player) {
        if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) return null;
        BlockState state = level.getBlockState(pos);
        if (!WrenchInteractions.wrenchable(state)) return null;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MachineBlockEntity machine) {
            return inspectMachine(pos, face, player, machine);
        }
        if (be instanceof MachinePortBlockEntity port) {
            MachineBlockEntity controller = port.controller(null);
            if (controller == null) {
                return new WrenchDiagnosticPayload(
                        pos, face, false, "unformed", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, -1);
            }
            return inspectMachine(pos, face, player, controller);
        }
        if (be instanceof EnergyCubeBlockEntity cube) {
            if (!cube.canAccess(player)) return WrenchDiagnosticPayload.locked(pos, face);
            var tier = cube.tier();
            String status = cube.energy() > 0 ? "ready" : "idle";
            return new WrenchDiagnosticPayload(
                    pos, face, false, status, cube.energy(), tier.capacity(), tier.transferRate(),
                    0, 0, 0, 0, 0, 0, 0, false, -1);
        }
        if (be instanceof WirelessAccessPointBlockEntity ap) {
            if (!ap.canAccess(player)) return WrenchDiagnosticPayload.locked(pos, face);
            var netView = ap.networkLive() ? ap.matterNetwork(false).view() : null;
            int nodes = netView == null ? 0 : netView.nodes();
            int cells = netView == null ? 0 : netView.cells();
            String status = ap.powered() ? (ap.matterReady() ? "ready" : "missing_network") : "missing_power";
            return new WrenchDiagnosticPayload(
                    pos, face, false, status, ap.energy(), WirelessAccessPointBlockEntity.CAPACITY,
                    ap.effectiveRange(), 0, 0, 0, nodes, cells, 0, 0, false, -1);
        }
        if (be instanceof MatterLevelEmitterBlockEntity emitter) {
            if (!emitter.canAccess(player)) return WrenchDiagnosticPayload.locked(pos, face);
            String status = emitter.emitting() ? "emitting" : (emitter.networkReady() ? "ready" : "missing_network");
            return new WrenchDiagnosticPayload(
                    pos, face, false, status, 0, 0, 0, emitter.observedStock(), 0, 0,
                    0, 0, 0, 0, emitter.emitting(), emitter.threshold());
        }
        if (be instanceof CellTransferStationBlockEntity station) {
            if (!station.canAccess(player)) return WrenchDiagnosticPayload.locked(pos, face);
            var snap = station.snapshot(0);
            return new WrenchDiagnosticPayload(
                    pos, face, false, snap.status(), 0, 0, 0, snap.movableOrdinary(), 0, 0,
                    0, 0, 0, 0, false, -1);
        }
        if (be instanceof EntangledLinkBlockEntity link) {
            if (!link.canAccess(player)) return WrenchDiagnosticPayload.locked(pos, face);
            var diag = EntangledLinks.inspect(level, link);
            String status = switch (diag.state()) {
                case PAIRED -> "paired";
                case INACTIVE -> "inactive";
                default -> "unpaired";
            };
            return new WrenchDiagnosticPayload(
                    pos, face, false, status, 0, 0, 0, 0, 0, 0, 0, 0,
                    diag.state().ordinal(), diag.distance(), false, -1);
        }
        if (be instanceof DimensionalEntangledLinkBlockEntity dlink) {
            if (!dlink.canAccess(player)) return WrenchDiagnosticPayload.locked(pos, face);
            var diag = EntangledLinks.inspectDimensional(level, dlink);
            String status = switch (diag.state()) {
                case PAIRED -> "paired";
                case INACTIVE -> "inactive";
                default -> "unpaired";
            };
            return new WrenchDiagnosticPayload(
                    pos, face, false, status, 0, 0, MatterCableBlock.Tier.RESONANT.energyRate(), 0, 0,
                    MatterCableBlock.Tier.RESONANT.matterRate(), 0, 0,
                    diag.state().ordinal(), Math.max(0, diag.distance()), dlink.hasAnchorUpgrade(), -1);
        }
        if (be instanceof SpatialAnchorBlockEntity anchor) {
            if (!anchor.canAccess(player)) return WrenchDiagnosticPayload.locked(pos, face);
            var ast = anchor.anchorStatus();
            String status = ast.active() ? "working" : ast.reason();
            return new WrenchDiagnosticPayload(
                    pos, face, false, status, anchor.energy(), SpatialAnchorBlockEntity.CAPACITY,
                    anchor.drainPerTick(), ast.forcedChunks(), ast.maxChunks(), ast.ownerTotalChunks(),
                    0, 0, 0, 0, ast.active(), -1);
        }
        if (be instanceof MatterCableRelayBlockEntity relay) {
            if (!relay.canAccess(player)) return WrenchDiagnosticPayload.locked(pos, face);
            return new WrenchDiagnosticPayload(
                    pos, face, false, relay.isOpen() ? "ready" : "redstone_paused",
                    0, 0, relay.energyLimit(), 0, 0, relay.matterLimit(),
                    0, 0, 0, 0, relay.isOpen(), -1);
        }
        if (state.getBlock() instanceof MatterCableBlock cable) {
            var cableOwner = CableOwners.owner(level, pos);
            if (!MachineAccess.allows(level, cableOwner, CableOwners.mode(level, pos), player)) return WrenchDiagnosticPayload.locked(pos, face);
            var tier = cable.tier();
            int nodes = 0;
            int cells = 0;
            for (Direction dir : Direction.values()) {
                BlockPos adj = pos.relative(dir);
                if (level.getChunkSource().getChunkNow(adj.getX() >> 4, adj.getZ() >> 4) == null) continue;
                BlockEntity adjBe = level.getBlockEntity(adj);
                if (adjBe instanceof MachineBlockEntity m && m.canAccess(player) && m.networkLive()) {
                    var nv = m.matterNetwork(false).view();
                    nodes = nv.nodes();
                    cells = nv.cells();
                    break;
                }
            }
            return new WrenchDiagnosticPayload(
                    pos, face, false, "ready", 0, 0, tier.energyRate(), 0, 0, tier.matterRate(),
                    nodes, cells, 0, 0, false, -1);
        }
        return new WrenchDiagnosticPayload(
                pos, face, false, "ready", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, -1);
    }

    private static WrenchDiagnosticPayload inspectMachine(
            BlockPos pos, Direction face, Player player, MachineBlockEntity machine) {
        var summary = machine.summaryFor(player, 0);
        if (summary == null) return WrenchDiagnosticPayload.locked(pos, face);
        var kind = machine.kind();
        boolean hasEnergy = !PreparationTier.manual(kind) && (kind.processes() || kind.generator());
        long energyCap = hasEnergy ? machine.energyCapacity() : 0;
        var netView = machine.networkLive() ? machine.matterNetwork(false).view() : null;
        int nodes = netView == null ? 0 : netView.nodes();
        int cells = netView == null ? 0 : netView.cells();
        return new WrenchDiagnosticPayload(
                pos, face, false, summary.status(),
                hasEnergy ? summary.energy() : 0, energyCap, 0,
                summary.used(), summary.capacity(), summary.types(),
                nodes, cells, 0, 0, false, -1);
    }
}
