package dev.latrox.rematter.network;

import dev.latrox.rematter.content.generation.CrystalFuelData;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Client display mirror of server-synchronized Crystal Generator fuels. */
public final class ClientCrystalFuels {
    private static volatile Map<String, CrystalFuelData.Fuel> current = Map.of();
    private static volatile boolean synced;
    private static final CopyOnWriteArrayList<Consumer<Map<String, CrystalFuelData.Fuel>>> LISTENERS = new CopyOnWriteArrayList<>();

    private ClientCrystalFuels() {}

    public static Map<String, CrystalFuelData.Fuel> current() {
        return synced ? current : CrystalFuelData.fuels();
    }

    public static Optional<CrystalFuelData.Fuel> fuel(String itemId) {
        return Optional.ofNullable(current().get(itemId));
    }

    public static void addListener(Consumer<Map<String, CrystalFuelData.Fuel>> listener) {
        LISTENERS.add(Objects.requireNonNull(listener));
    }

    public static void removeListener(Consumer<Map<String, CrystalFuelData.Fuel>> listener) {
        LISTENERS.remove(listener);
    }

    public static void accept(Map<String, CrystalFuelData.Fuel> fuels) {
        var copy = Map.copyOf(Objects.requireNonNull(fuels));
        current = copy;
        synced = true;
        LISTENERS.forEach(listener -> listener.accept(copy));
    }

    public static void clear() {
        current = Map.of();
        synced = false;
        LISTENERS.forEach(listener -> listener.accept(Map.of()));
    }
}
