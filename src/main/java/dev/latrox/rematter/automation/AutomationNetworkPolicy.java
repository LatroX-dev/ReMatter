package dev.latrox.rematter.automation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * One eligible provider per native backend. The highest incumbency wins, so an interface that holds work
 * or a live lease is never displaced by a newcomer; the caller's stable physical order breaks ties.
 */
public final class AutomationNetworkPolicy {
    private AutomationNetworkPolicy() {}

    public static <T> Optional<T> elected(List<T> ordered, String backend, Function<T, String> backendOf,
                                          Predicate<T> eligible, ToIntFunction<T> incumbency) {
        Objects.requireNonNull(ordered); Objects.requireNonNull(backend);
        Objects.requireNonNull(backendOf); Objects.requireNonNull(eligible); Objects.requireNonNull(incumbency);
        if (backend.isEmpty()) return Optional.empty();
        T elected = null;
        int best = Integer.MIN_VALUE;
        for (T candidate : ordered) {
            if (!eligible.test(candidate) || !backend.equals(backendOf.apply(candidate))) continue;
            int rank = incumbency.applyAsInt(candidate);
            if (rank > best) { elected = candidate; best = rank; }
        }
        return Optional.ofNullable(elected);
    }
}
