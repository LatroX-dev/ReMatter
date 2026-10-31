package dev.latrox.rematter.network;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Client display catalogue, deliberately separate from integrated-server authoritative data. */
public final class ClientChemistry {
    private static volatile ChemistryRegistry current = ChemistryRegistry.builder().build();
    private static final CopyOnWriteArrayList<Consumer<ChemistryRegistry>> LISTENERS = new CopyOnWriteArrayList<>();
    private ClientChemistry() {}

    public static ChemistryRegistry current() { return current; }
    public static void addListener(Consumer<ChemistryRegistry> listener) { LISTENERS.add(Objects.requireNonNull(listener)); }
    public static void removeListener(Consumer<ChemistryRegistry> listener) { LISTENERS.remove(listener); }
    public static void accept(ChemistryRegistry catalogue) {
        current = Objects.requireNonNull(catalogue);
        LISTENERS.forEach(listener -> listener.accept(catalogue));
    }
    public static void clear() { accept(ChemistryRegistry.builder().build()); }
}
