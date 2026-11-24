package dev.latrox.rematter.primal;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** A single Basic-tier graph edge. It supplies neither chunk loading nor an additional transfer budget. */
public final class EntangledLinks {
    public static final int MAX_DISTANCE = 32;
    private EntangledLinks() {}
    public static Optional<EntangledLinkBlockEntity> loaded(ServerLevel level, BlockPos position) {
        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        if (chunk == null || !(chunk.getBlockEntity(position) instanceof EntangledLinkBlockEntity link) || link.isRemoved()) return Optional.empty();
        return Optional.of(link);
    }
    public static boolean withinRange(BlockPos first, BlockPos second) { return !first.equals(second) && first.distSqr(second) <= (long)MAX_DISTANCE * MAX_DISTANCE; }
    public static Optional<BlockPos> peer(ServerLevel level, BlockPos position, UUID requiredOwner) {
        return peer(level, position, requiredOwner, dev.latrox.rematter.content.SecurityMode.PUBLIC);
    }
    public static Optional<BlockPos> peer(ServerLevel level, BlockPos position, UUID requiredOwner,
                                          dev.latrox.rematter.content.SecurityMode requiredMode) {
        var local = loaded(level, position).orElse(null);
        if (local == null || requiredOwner == null
                || !dev.latrox.rematter.content.MachineAccess.canConnect(level, requiredOwner, requiredMode, local.owner(), local.securityMode())
                || local.pair() == null || local.target() == null || !withinRange(position, local.target())) return Optional.empty();
        var remote = loaded(level, local.target()).orElse(null);
        return remote != null
                && dev.latrox.rematter.content.MachineAccess.canConnect(level, requiredOwner, requiredMode, remote.owner(), remote.securityMode())
                && dev.latrox.rematter.content.MachineAccess.canConnect(level, local.owner(), local.securityMode(), remote.owner(), remote.securityMode())
                && local.pair().equals(remote.pair()) && position.equals(remote.target())
                ? Optional.of(remote.getBlockPos()) : Optional.empty();
    }
    public static Optional<BlockPos> linkedPeerForPlayer(ServerLevel level, BlockPos position, net.minecraft.world.entity.player.Player player) {
        var local = loaded(level, position).orElse(null);
        if (local == null || !local.canAccess(player) || local.pair() == null || local.target() == null
                || !withinRange(position, local.target())) return Optional.empty();
        var remote = loaded(level, local.target()).orElse(null);
        return remote != null && remote.canAccess(player)
                && local.pair().equals(remote.pair()) && position.equals(remote.target())
                ? Optional.of(remote.getBlockPos()) : Optional.empty();
    }
    public static boolean pair(ServerLevel level, BlockPos first, BlockPos second, UUID owner) {
        if (!level.getServer().isSameThread() || !withinRange(first, second)) return false;
        var a = loaded(level, first).orElse(null); var b = loaded(level, second).orElse(null);
        if (a == null || b == null || owner == null
                || !dev.latrox.rematter.content.MachineAccess.canConnect(level, owner, dev.latrox.rematter.content.SecurityMode.PUBLIC, a.owner(), a.securityMode())
                || !dev.latrox.rematter.content.MachineAccess.canConnect(level, owner, dev.latrox.rematter.content.SecurityMode.PUBLIC, b.owner(), b.securityMode())
                || !dev.latrox.rematter.content.MachineAccess.canConnect(level, a.owner(), a.securityMode(), b.owner(), b.securityMode())) return false;
        UUID pair = UUID.randomUUID(); a.pair(second, pair); b.pair(first, pair); return true;
    }
    public static boolean pair(ServerLevel level, BlockPos first, BlockPos second, net.minecraft.world.entity.player.Player player) {
        if (!level.getServer().isSameThread() || !withinRange(first, second)) return false;
        var a = loaded(level, first).orElse(null); var b = loaded(level, second).orElse(null);
        if (a == null || b == null || !a.canAccess(player) || !b.canAccess(player)
                || !dev.latrox.rematter.content.MachineAccess.canConnect(level, a.owner(), a.securityMode(), b.owner(), b.securityMode())) return false;
        UUID pair = UUID.randomUUID(); a.pair(second, pair); b.pair(first, pair); return true;
    }
    public enum LinkState { NONE, UNPAIRED, PAIRED, INACTIVE }
    public record LinkDiagnostic(LinkState state, int distance) {}
    public static LinkDiagnostic inspect(ServerLevel level, EntangledLinkBlockEntity link) {
        if (link.pair() == null || link.target() == null) return new LinkDiagnostic(LinkState.UNPAIRED, 0);
        int dist = (int) Math.round(Math.sqrt(link.getBlockPos().distSqr(link.target())));
        if (link.owner() != null && peer(level, link.getBlockPos(), link.owner(), link.securityMode()).isPresent()) {
            return new LinkDiagnostic(LinkState.PAIRED, dist);
        }
        return new LinkDiagnostic(LinkState.INACTIVE, dist);
    }
}
