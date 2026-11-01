package dev.latrox.rematter.automation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Server-thread-only authority for one worker. A competing interface may never replace an existing claim. */
public final class AutomationWorkerLease<O> {
    public record Claim<O>(O owner, UUID orderId) {
        public Claim { Objects.requireNonNull(owner); Objects.requireNonNull(orderId); }
    }
    private Claim<O> claim;
    private String lastCompletedBackend = "";

    public Optional<Claim<O>> current() { return Optional.ofNullable(claim); }

    public boolean tryAcquire(O owner, UUID orderId) {
        Objects.requireNonNull(owner); Objects.requireNonNull(orderId);
        if (claim != null) return matches(owner, orderId);
        claim = new Claim<>(owner, orderId);
        return true;
    }

    public boolean matches(O owner, UUID orderId) {
        return claim != null && claim.owner() == owner && claim.orderId().equals(orderId);
    }

    public boolean release(O owner, UUID orderId) {
        if (!matches(owner, orderId)) return false;
        claim = null;
        return true;
    }

    public boolean completed(O owner, UUID orderId, String backend) {
        Objects.requireNonNull(backend);
        if (backend.isEmpty() || !matches(owner,orderId)) return false;
        lastCompletedBackend = backend;
        return true;
    }

    public boolean shouldYield(String candidateBackend, boolean otherBackendReady) {
        return otherBackendReady && !lastCompletedBackend.isEmpty() && lastCompletedBackend.equals(candidateBackend);
    }
}
