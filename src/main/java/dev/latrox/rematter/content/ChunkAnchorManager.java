package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.primal.DimensionalEntangledLinkBlockEntity;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jspecify.annotations.Nullable;

/**
 * Server-authoritative native chunkloading coordinator backed by NeoForge's {@link TicketController}.
 * Enforces global enablement, per-player distinct chunk quotas, optional online-only gating,
 * and automatic orphan cleanup on world reload or block removal. Zero per-tick overhead when state is unchanged.
 */
public final class ChunkAnchorManager {
    public static final Identifier TICKET_ID = Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "chunk_anchor");
    public static final TicketController CONTROLLER = new TicketController(TICKET_ID, ChunkAnchorManager::validateTickets);

    public record Status(boolean active, int forcedChunks, int ownerTotalChunks, int maxChunks, String reason) {
        public static final Status IDLE = new Status(false, 0, 0, 32, "idle");
    }

    private record AnchorKey(ResourceKey<Level> dimension, BlockPos pos) {
        AnchorKey { pos = pos.immutable(); }
    }

    private record DimChunk(ResourceKey<Level> dimension, ChunkPos chunk) {}

    private record AnchorEntry(UUID owner, Set<ChunkPos> chunks) {}

    private static final Map<MinecraftServer, ServerState> STATES = new IdentityHashMap<>();

    private static final class ServerState {
        private final Map<AnchorKey, AnchorEntry> anchors = new HashMap<>();
        private final Map<UUID, Set<AnchorKey>> byOwner = new HashMap<>();
        private final Map<UUID, Set<AnchorKey>> pausedByOwner = new HashMap<>();

        int distinctOwnerChunks(UUID owner, @Nullable AnchorKey excluding, @Nullable ResourceKey<Level> extraDim, Set<ChunkPos> extraChunks) {
            var set = new HashSet<DimChunk>();
            var keys = byOwner.get(owner);
            if (keys != null) {
                for (var key : keys) {
                    if (key.equals(excluding)) continue;
                    var entry = anchors.get(key);
                    if (entry == null) continue;
                    for (var c : entry.chunks()) set.add(new DimChunk(key.dimension(), c));
                }
            }
            if (extraDim != null) {
                for (var c : extraChunks) set.add(new DimChunk(extraDim, c));
            }
            return set.size();
        }
    }

    private ChunkAnchorManager() {}

    public static void registerTicketController(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        synchronized (STATES) {
            STATES.remove(event.getServer());
        }
    }

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ChunkLoadingConfig.onlyWhileOwnerOnline()) return;
        var server = event.getEntity().level().getServer();
        if (server != null) refreshOwnerLoadedAnchors(server, event.getEntity().getUUID());
    }

    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!ChunkLoadingConfig.onlyWhileOwnerOnline()) return;
        var server = event.getEntity().level().getServer();
        if (server != null) releaseOwnerAll(server, event.getEntity().getUUID());
    }

    private static ServerState state(MinecraftServer server) {
        synchronized (STATES) {
            return STATES.computeIfAbsent(server, ignored -> new ServerState());
        }
    }

    public static int ownerChunkCount(MinecraftServer server, @Nullable UUID owner) {
        if (owner == null) return 0;
        var st = state(server);
        synchronized (st) {
            return st.distinctOwnerChunks(owner, null, null, Set.of());
        }
    }

    /** Returns every distinct chunk currently force-loaded by OTHER ReMatter anchors of {@code owner} in {@code dimension}. */
    public static Set<ChunkPos> ownerChunksInDimension(MinecraftServer server, @Nullable UUID owner,
                                                       ResourceKey<Level> dimension, @Nullable BlockPos excludingSource) {
        if (owner == null) return Set.of();
        var st = state(server);
        synchronized (st) {
            var keys = st.byOwner.get(owner);
            if (keys == null || keys.isEmpty()) return Set.of();
            var result = new HashSet<ChunkPos>();
            for (var key : keys) {
                if (!key.dimension().equals(dimension)) continue;
                if (excludingSource != null && key.pos().equals(excludingSource)) continue;
                var entry = st.anchors.get(key);
                if (entry != null) result.addAll(entry.chunks());
            }
            return Set.copyOf(result);
        }
    }

    public static Set<ChunkPos> square(BlockPos center, int radius) {
        ChunkPos base = ChunkPos.containing(center);
        int r = Math.clamp(radius, 0, 2);
        if (r == 0) return Set.of(base);
        int side = 2 * r + 1;
        var result = new HashSet<ChunkPos>(side * side);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                result.add(new ChunkPos(base.x() + dx, base.z() + dz));
            }
        }
        return Set.copyOf(result);
    }

    /** Resolves a 25-bit 5x5 chunk mask (bit index = (dz + 2) * 5 + (dx + 2)) around {@code center}. */
    public static Set<ChunkPos> fromMask(BlockPos center, int mask) {
        ChunkPos base = ChunkPos.containing(center);
        int clean = mask & ((1 << 25) - 1);
        if (clean == 0) return Set.of();
        var result = new HashSet<ChunkPos>(Integer.bitCount(clean));
        for (int gz = 0; gz < 5; gz++) {
            int dz = gz - 2;
            for (int gx = 0; gx < 5; gx++) {
                if (((clean >>> (gz * 5 + gx)) & 1) != 0) {
                    result.add(new ChunkPos(base.x() + (gx - 2), base.z() + dz));
                }
            }
        }
        return Set.copyOf(result);
    }

    public static Status sync(ServerLevel level, BlockPos pos, @Nullable UUID owner, Set<ChunkPos> desired) {
        MinecraftServer server = level.getServer();
        int max = ChunkLoadingConfig.maxChunksPerPlayer();
        if (!ChunkLoadingConfig.enabled() || max <= 0) {
            release(level, pos);
            return new Status(false, 0, ownerChunkCount(server, owner), max, "disabled");
        }
        if (owner == null || desired == null || desired.isEmpty()) {
            release(level, pos);
            return new Status(false, 0, ownerChunkCount(server, owner), max, "idle");
        }
        if (ChunkLoadingConfig.onlyWhileOwnerOnline() && server.getPlayerList().getPlayer(owner) == null) {
            AnchorKey key = new AnchorKey(level.dimension(), pos);
            var st = state(server);
            synchronized (st) {
                releaseLocked(level, st, key);
                st.pausedByOwner.computeIfAbsent(owner, ignored -> new HashSet<>()).add(key);
            }
            return new Status(false, 0, ownerChunkCount(server, owner), max, "offline");
        }

        AnchorKey key = new AnchorKey(level.dimension(), pos);
        var st = state(server);
        synchronized (st) {
            var existing = st.anchors.get(key);
            if (existing != null && existing.owner().equals(owner) && existing.chunks().equals(desired)) {
                int total = st.distinctOwnerChunks(owner, null, null, Set.of());
                if (total <= max) {
                    return new Status(true, desired.size(), total, max, "active");
                }
            }
            int projected = st.distinctOwnerChunks(owner, key, level.dimension(), desired);
            if (projected > max) {
                releaseLocked(level, st, key);
                int current = st.distinctOwnerChunks(owner, null, null, Set.of());
                return new Status(false, 0, current, max, "limit");
            }
            // Diff previous vs desired tickets to avoid redundant forceChunk calls.
            Set<ChunkPos> oldChunks = existing == null ? Set.of() : existing.chunks();
            for (ChunkPos old : oldChunks) {
                if (!desired.contains(old)) {
                    CONTROLLER.forceChunk(level, pos, old.x(), old.z(), false, true);
                }
            }
            for (ChunkPos next : desired) {
                if (!oldChunks.contains(next)) {
                    CONTROLLER.forceChunk(level, pos, next.x(), next.z(), true, true);
                }
            }
            if (existing != null && !existing.owner().equals(owner)) {
                var prevSet = st.byOwner.get(existing.owner());
                if (prevSet != null) {
                    prevSet.remove(key);
                    if (prevSet.isEmpty()) st.byOwner.remove(existing.owner());
                }
            }
            st.anchors.put(key, new AnchorEntry(owner, Set.copyOf(desired)));
            st.byOwner.computeIfAbsent(owner, ignored -> new HashSet<>()).add(key);
            return new Status(true, desired.size(), projected, max, "active");
        }
    }

    public static void release(ServerLevel level, BlockPos pos) {
        var st = state(level.getServer());
        synchronized (st) {
            releaseLocked(level, st, new AnchorKey(level.dimension(), pos));
        }
    }

    private static void releaseLocked(ServerLevel level, ServerState st, AnchorKey key) {
        var prev = st.anchors.remove(key);
        if (prev == null) return;
        var ownerKeys = st.byOwner.get(prev.owner());
        if (ownerKeys != null) {
            ownerKeys.remove(key);
            if (ownerKeys.isEmpty()) st.byOwner.remove(prev.owner());
        }
        for (ChunkPos c : prev.chunks()) {
            CONTROLLER.forceChunk(level, key.pos(), c.x(), c.z(), false, true);
        }
    }

    private static void releaseOwnerAll(MinecraftServer server, UUID owner) {
        var st = state(server);
        synchronized (st) {
            var keys = st.byOwner.get(owner);
            if (keys == null || keys.isEmpty()) return;
            var snapshot = Set.copyOf(keys);
            st.pausedByOwner.computeIfAbsent(owner, ignored -> new HashSet<>()).addAll(snapshot);
            for (var key : snapshot) {
                ServerLevel dimLevel = server.getLevel(key.dimension());
                if (dimLevel != null) releaseLocked(dimLevel, st, key);
            }
        }
    }

    private static void refreshOwnerLoadedAnchors(MinecraftServer server, UUID owner) {
        var st = state(server);
        Set<AnchorKey> known;
        synchronized (st) {
            var merged = new HashSet<AnchorKey>();
            var active = st.byOwner.get(owner);
            if (active != null) merged.addAll(active);
            var paused = st.pausedByOwner.get(owner);
            if (paused != null) merged.addAll(paused);
            known = Set.copyOf(merged);
        }
        var verified = new HashSet<AnchorKey>();
        for (var key : known) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) continue;
            // Reload a paused anchor's own chunk safely so offline-unloaded anchors reactivate on login.
            var chunk = level.getChunk(key.pos().getX() >> 4, key.pos().getZ() >> 4);
            var be = chunk.getBlockEntity(key.pos());
            if (be instanceof SpatialAnchorBlockEntity anchor) anchor.syncChunkAnchor();
            else if (be instanceof DimensionalEntangledLinkBlockEntity link) link.syncChunkAnchor();
            else if (be instanceof WirelessAccessPointBlockEntity ap) ap.syncChunkAnchor();
            else if (be instanceof MachineBlockEntity machine) machine.syncChunkAnchor();
            verified.add(key);
        }
        if (!verified.isEmpty()) {
            synchronized (st) {
                var paused = st.pausedByOwner.get(owner);
                if (paused != null) {
                    paused.removeAll(verified);
                    if (paused.isEmpty()) st.pausedByOwner.remove(owner);
                }
            }
        }
    }

    private static void validateTickets(ServerLevel level, TicketHelper helper) {
        var st = state(level.getServer());
        for (var entry : helper.getBlockTickets().entrySet()) {
            BlockPos pos = entry.getKey();
            if (!ChunkLoadingConfig.enabled()) {
                helper.removeAllTickets(pos);
                continue;
            }
            var be = level.getBlockEntity(pos);
            boolean valid = false;
            UUID owner = null;
            if (be instanceof SpatialAnchorBlockEntity anchor) {
                valid = anchor.owner() != null;
                owner = anchor.owner();
            } else if (be instanceof DimensionalEntangledLinkBlockEntity link) {
                valid = link.owner() != null && link.hasAnchorUpgrade();
                owner = link.owner();
            } else if (be instanceof WirelessAccessPointBlockEntity ap) {
                valid = ap.owner() != null && ap.hasAnchorUpgrade();
                owner = ap.owner();
            } else if (be instanceof MachineBlockEntity machine) {
                valid = machine.networkOwner() != null && machine.hasAnchorUpgrade();
                owner = machine.networkOwner();
            }
            if (!valid || owner == null) {
                helper.removeAllTickets(pos);
                continue;
            }
            // Register restored chunks in memory so per-player quotas remain exact after restart.
            var restored = new HashSet<ChunkPos>();
            entry.getValue().normal().forEach(packed -> restored.add(ChunkPos.unpack(packed)));
            entry.getValue().naturalSpawning().forEach(packed -> restored.add(ChunkPos.unpack(packed)));
            if (restored.isEmpty()) {
                helper.removeAllTickets(pos);
                continue;
            }
            AnchorKey key = new AnchorKey(level.dimension(), pos);
            synchronized (st) {
                st.anchors.put(key, new AnchorEntry(owner, Set.copyOf(restored)));
                st.byOwner.computeIfAbsent(owner, ignored -> new HashSet<>()).add(key);
            }
        }
    }
}
