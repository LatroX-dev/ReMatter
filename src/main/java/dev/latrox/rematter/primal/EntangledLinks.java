package dev.latrox.rematter.primal;

import dev.latrox.rematter.content.MachineAccess;
import dev.latrox.rematter.content.MatterCableBlock;
import dev.latrox.rematter.content.SecurityMode;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Graph edge helpers for same-dimension Entangled Links (Basic tier, 64-block range) and
 * cross-dimension Dimensional Entangled Links (Resonant tier, unlimited distance).
 * Neither supplies implicit chunk loading; every lookup uses {@code getChunkNow}.
 */
public final class EntangledLinks {
    public static final int MAX_DISTANCE = 64;

    public record DimPos(ResourceKey<Level> dimension, BlockPos pos) implements Comparable<DimPos> {
        public DimPos {
            Objects.requireNonNull(dimension);
            pos = pos.immutable();
        }

        public @Nullable ServerLevel resolveLevel(MinecraftServer server) {
            return server.getLevel(dimension);
        }

        @Override
        public int compareTo(DimPos other) {
            int cmp = dimension.identifier().compareTo(other.dimension.identifier());
            return cmp != 0 ? cmp : pos.compareTo(other.pos);
        }
    }

    private EntangledLinks() {}

    public static Optional<EntangledLinkBlockEntity> loaded(ServerLevel level, BlockPos position) {
        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        if (chunk == null || !(chunk.getBlockEntity(position) instanceof EntangledLinkBlockEntity link) || link.isRemoved()) return Optional.empty();
        return Optional.of(link);
    }

    public static Optional<DimensionalEntangledLinkBlockEntity> loadedDimensional(ServerLevel level, BlockPos position) {
        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        if (chunk == null || !(chunk.getBlockEntity(position) instanceof DimensionalEntangledLinkBlockEntity link) || link.isRemoved()) return Optional.empty();
        return Optional.of(link);
    }

    public static boolean withinRange(BlockPos first, BlockPos second) {
        return !first.equals(second) && first.distSqr(second) <= (long) MAX_DISTANCE * MAX_DISTANCE;
    }

    public static Optional<BlockPos> peer(ServerLevel level, BlockPos position, UUID requiredOwner) {
        return peer(level, position, requiredOwner, SecurityMode.PUBLIC);
    }

    public static Optional<BlockPos> peer(ServerLevel level, BlockPos position, UUID requiredOwner, SecurityMode requiredMode) {
        var local = loaded(level, position).orElse(null);
        if (local == null || requiredOwner == null
                || !local.redstoneAllows()
                || !MachineAccess.canConnect(level, requiredOwner, requiredMode, local.owner(), local.securityMode())
                || local.pair() == null || local.target() == null || !withinRange(position, local.target())) return Optional.empty();
        var remote = loaded(level, local.target()).orElse(null);
        return remote != null
                && remote.redstoneAllows()
                && MachineAccess.canConnect(level, requiredOwner, requiredMode, remote.owner(), remote.securityMode())
                && MachineAccess.canConnect(level, local.owner(), local.securityMode(), remote.owner(), remote.securityMode())
                && local.pair().equals(remote.pair()) && position.equals(remote.target())
                ? Optional.of(remote.getBlockPos()) : Optional.empty();
    }

    /** Resolves a loaded cross-dimension peer without ever forcing the remote chunk to load. */
    public static Optional<DimPos> dimensionalPeer(ServerLevel level, BlockPos position, UUID requiredOwner, SecurityMode requiredMode) {
        var local = loadedDimensional(level, position).orElse(null);
        if (local == null || requiredOwner == null
                || !local.redstoneAllows()
                || !MachineAccess.canConnect(level, requiredOwner, requiredMode, local.owner(), local.securityMode())
                || local.pair() == null || local.peerDimension() == null || local.peerPos() == null) {
            return Optional.empty();
        }
        if (local.peerDimension().equals(level.dimension()) && local.peerPos().equals(position)) {
            return Optional.empty();
        }
        ServerLevel targetLevel = level.getServer().getLevel(local.peerDimension());
        if (targetLevel == null) return Optional.empty();
        var remote = loadedDimensional(targetLevel, local.peerPos()).orElse(null);
        if (remote == null
                || !remote.redstoneAllows()
                || !MachineAccess.canConnect(targetLevel, requiredOwner, requiredMode, remote.owner(), remote.securityMode())
                || !MachineAccess.canConnect(level, local.owner(), local.securityMode(), remote.owner(), remote.securityMode())
                || !local.pair().equals(remote.pair())
                || !level.dimension().equals(remote.peerDimension())
                || !position.equals(remote.peerPos())) {
            return Optional.empty();
        }
        return Optional.of(new DimPos(targetLevel.dimension(), remote.getBlockPos()));
    }

    public static Optional<BlockPos> linkedPeerForPlayer(ServerLevel level, BlockPos position, Player player) {
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
        var a = loaded(level, first).orElse(null);
        var b = loaded(level, second).orElse(null);
        if (a == null || b == null || owner == null
                || !MachineAccess.canConnect(level, owner, SecurityMode.PUBLIC, a.owner(), a.securityMode())
                || !MachineAccess.canConnect(level, owner, SecurityMode.PUBLIC, b.owner(), b.securityMode())
                || !MachineAccess.canConnect(level, a.owner(), a.securityMode(), b.owner(), b.securityMode())) return false;
        UUID pair = UUID.randomUUID();
        a.pair(second, pair);
        b.pair(first, pair);
        return true;
    }

    public static boolean pair(ServerLevel level, BlockPos first, BlockPos second, Player player) {
        if (!level.getServer().isSameThread() || !withinRange(first, second)) return false;
        var a = loaded(level, first).orElse(null);
        var b = loaded(level, second).orElse(null);
        if (a == null || b == null || !a.canAccess(player) || !b.canAccess(player)
                || !MachineAccess.canConnect(level, a.owner(), a.securityMode(), b.owner(), b.securityMode())) return false;
        UUID pair = UUID.randomUUID();
        a.pair(second, pair);
        b.pair(first, pair);
        return true;
    }

    public static boolean pairDimensional(ServerLevel firstLevel, BlockPos firstPos, ServerLevel secondLevel, BlockPos secondPos, UUID owner) {
        if (!firstLevel.getServer().isSameThread()) return false;
        if (firstLevel.dimension().equals(secondLevel.dimension()) && firstPos.equals(secondPos)) return false;
        var a = loadedDimensional(firstLevel, firstPos).orElse(null);
        var b = loadedDimensional(secondLevel, secondPos).orElse(null);
        if (a == null || b == null || owner == null
                || !MachineAccess.canConnect(firstLevel, owner, SecurityMode.PUBLIC, a.owner(), a.securityMode())
                || !MachineAccess.canConnect(secondLevel, owner, SecurityMode.PUBLIC, b.owner(), b.securityMode())
                || !MachineAccess.canConnect(firstLevel, a.owner(), a.securityMode(), b.owner(), b.securityMode())) {
            return false;
        }
        UUID pair = UUID.randomUUID();
        a.pair(secondLevel.dimension(), secondPos, pair);
        b.pair(firstLevel.dimension(), firstPos, pair);
        MatterCableBlock.refreshLoadedAround(firstLevel, firstPos);
        MatterCableBlock.refreshLoadedAround(secondLevel, secondPos);
        return true;
    }

    public static boolean pairDimensional(ServerLevel firstLevel, BlockPos firstPos, ServerLevel secondLevel, BlockPos secondPos, Player player) {
        if (!firstLevel.getServer().isSameThread()) return false;
        if (firstLevel.dimension().equals(secondLevel.dimension()) && firstPos.equals(secondPos)) return false;
        var a = loadedDimensional(firstLevel, firstPos).orElse(null);
        var b = loadedDimensional(secondLevel, secondPos).orElse(null);
        if (a == null || b == null || !a.canAccess(player) || !b.canAccess(player)
                || !MachineAccess.canConnect(firstLevel, a.owner(), a.securityMode(), b.owner(), b.securityMode())) {
            return false;
        }
        UUID pair = UUID.randomUUID();
        a.pair(secondLevel.dimension(), secondPos, pair);
        b.pair(firstLevel.dimension(), firstPos, pair);
        MatterCableBlock.refreshLoadedAround(firstLevel, firstPos);
        MatterCableBlock.refreshLoadedAround(secondLevel, secondPos);
        return true;
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

    public static LinkDiagnostic inspectDimensional(ServerLevel level, DimensionalEntangledLinkBlockEntity link) {
        if (link.pair() == null || link.peerDimension() == null || link.peerPos() == null) {
            return new LinkDiagnostic(LinkState.UNPAIRED, 0);
        }
        int dist = level.dimension().equals(link.peerDimension())
                ? (int) Math.round(Math.sqrt(link.getBlockPos().distSqr(link.peerPos())))
                : -1;
        if (link.owner() != null && dimensionalPeer(level, link.getBlockPos(), link.owner(), link.securityMode()).isPresent()) {
            return new LinkDiagnostic(LinkState.PAIRED, dist);
        }
        return new LinkDiagnostic(LinkState.INACTIVE, dist);
    }
}
