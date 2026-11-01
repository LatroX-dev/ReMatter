package dev.latrox.rematter.automation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/** One eligible provider per native backend; the caller supplies stable physical-position order. */
public final class AutomationNetworkPolicy {
    private AutomationNetworkPolicy() {}

    public static <T> Optional<T> elected(List<T> ordered, String backend,
                                          Function<T, String> backendOf, Predicate<T> eligible) {
        Objects.requireNonNull(ordered); Objects.requireNonNull(backend);
        Objects.requireNonNull(backendOf); Objects.requireNonNull(eligible);
        if (backend.isEmpty()) return Optional.empty();
        for (T candidate : ordered) {
            if (eligible.test(candidate) && backend.equals(backendOf.apply(candidate))) return Optional.of(candidate);
        }
        return Optional.empty();
    }
}
