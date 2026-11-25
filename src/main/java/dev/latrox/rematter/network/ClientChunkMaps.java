package dev.latrox.rematter.network;

import dev.latrox.rematter.content.ChunkMapSnapshot;
import org.jspecify.annotations.Nullable;

/** Lightweight client-side holder for the most recently received 5x5 chunk coverage map. */
public final class ClientChunkMaps {
    private static int activeContainerId = -1;
    private static @Nullable ChunkMapSnapshot latest;

    private ClientChunkMaps() {}

    public static void accept(int containerId, ChunkMapSnapshot map) {
        activeContainerId = containerId;
        latest = map;
    }

    public static @Nullable ChunkMapSnapshot forContainer(int containerId) {
        return activeContainerId == containerId ? latest : null;
    }

    public static void clear() {
        activeContainerId = -1;
        latest = null;
    }
}
