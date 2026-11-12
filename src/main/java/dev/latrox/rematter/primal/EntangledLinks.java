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
        var local = loaded(level, position).orElse(null);
        if (local == null || requiredOwner == null || !requiredOwner.equals(local.owner()) || local.pair() == null || local.target() == null || !withinRange(position, local.target())) return Optional.empty();
        var remote = loaded(level, local.target()).orElse(null);
        return remote != null && requiredOwner.equals(remote.owner()) && local.pair().equals(remote.pair()) && position.equals(remote.target())
                ? Optional.of(remote.getBlockPos()) : Optional.empty();
    }
    public static boolean pair(ServerLevel level, BlockPos first, BlockPos second, UUID owner) {
        if (!level.getServer().isSameThread() || !withinRange(first, second)) return false;
        var a = loaded(level, first).orElse(null); var b = loaded(level, second).orElse(null);
        if (a == null || b == null || owner == null || !owner.equals(a.owner()) || !owner.equals(b.owner())) return false;
        UUID pair = UUID.randomUUID(); a.pair(second, pair); b.pair(first, pair); return true;
    }
}
