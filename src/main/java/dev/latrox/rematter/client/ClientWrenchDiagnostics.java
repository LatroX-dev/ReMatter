package dev.latrox.rematter.client;

import dev.latrox.rematter.network.WrenchDiagnosticPayload;
import org.jspecify.annotations.Nullable;

/**
 * Pure-Java client cache for server-authoritative {@link WrenchDiagnosticPayload} replies.
 * Deliberately avoids {@code net.minecraft.client.*} imports so payload registration remains dedicated-server safe.
 */
public final class ClientWrenchDiagnostics {
    private static volatile @Nullable WrenchDiagnosticPayload latest;
    private static volatile long receivedAtNanos;

    private ClientWrenchDiagnostics() {}

    public static void accept(WrenchDiagnosticPayload payload) {
        latest = payload;
        receivedAtNanos = System.nanoTime();
    }

    public static @Nullable WrenchDiagnosticPayload current(long maxAgeMillis) {
        var current = latest;
        if (current == null) return null;
        long ageMillis = (System.nanoTime() - receivedAtNanos) / 1_000_000L;
        return ageMillis <= maxAgeMillis ? current : null;
    }

    public static void clear() {
        latest = null;
        receivedAtNanos = 0L;
    }
}
