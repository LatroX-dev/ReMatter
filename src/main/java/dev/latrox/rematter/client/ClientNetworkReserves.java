package dev.latrox.rematter.client;

import dev.latrox.rematter.content.MachineSnapshot.StockKey;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Client-side mirror of the active network owner's global substance reserves synced from the server. */
public final class ClientNetworkReserves {
    private static volatile Map<StockKey, Long> reserves = Map.of();
    private static final AtomicInteger REVISION = new AtomicInteger();

    private ClientNetworkReserves() {}

    public static void acceptServerSync(Map<StockKey, Long> next) {
        reserves = next == null ? Map.of() : Map.copyOf(next);
        REVISION.incrementAndGet();
    }

    public static void clear() {
        reserves = Map.of();
        REVISION.incrementAndGet();
    }

    public static Map<StockKey, Long> all() { return reserves; }
    public static long amount(StockKey key) { return key == null ? 0L : reserves.getOrDefault(key, 0L); }
    public static int revision() { return REVISION.get(); }
}
