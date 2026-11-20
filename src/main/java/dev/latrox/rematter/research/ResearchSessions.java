package dev.latrox.rematter.research;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.latrox.rematter.chemistry.SubstanceKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** Server-only research scheduler. A session belongs to knowledge identity, not a block or transported item. */
public final class ResearchSessions {
    public static final long DERIVE_ENERGY = 256_000;
    public static final int DERIVE_TICKS = 1_800;
    public enum Mode { DERIVE, DISCOVERY }
    public enum State { ACTIVE, PAUSED, DEFINITION_CHANGED, INELIGIBLE, COMPLETE, EXHAUSTED }
    public enum Result { WAITING, PAID, WORKING, LEARNED, REFUND_DUE, PAUSED, UNAVAILABLE }
    /** The adapter must debit FE and the explicit cell in this same transaction; no normal network fallback. */
    @FunctionalInterface public interface Payment {
        boolean debit(UUID operation, long primal, long energy, TransactionContext transaction);
    }
    /** Returns the amount actually inserted into the explicit return cell. Full cells simply return zero. */
    @FunctionalInterface public interface Refund {
        long credit(UUID operation, long maximum, TransactionContext transaction);
    }
    /** Safe for menus: no candidate order or selected Discovery family is exposed. */
    public record View(UUID id, Mode mode, int tier, String deriveTarget, State state, int paidSteps,
                       int completedSteps, int totalSteps, int elapsedTicks, int stepTicks,
                       long primalPerStep, long energyPerStep, int candidates, long refundDue, String resultFamily) {}
    private final ResearchLedger ledger;
    private final ResearchCatalogue catalogue;
    private final int minimumKnown;
    private final RandomGenerator random;
    private final java.util.function.BiConsumer<ResearchContext, MaterialResearch> onLearned;
    public ResearchSessions(ResearchLedger ledger, ResearchCatalogue catalogue, int minimumKnown, RandomGenerator random) {
        this(ledger, catalogue, minimumKnown, random, (context, family) -> {});
    }
    public ResearchSessions(ResearchLedger ledger, ResearchCatalogue catalogue, int minimumKnown, RandomGenerator random,
                            java.util.function.BiConsumer<ResearchContext, MaterialResearch> onLearned) {
        this.ledger = ledger; this.catalogue = catalogue; this.minimumKnown = minimumKnown; this.random = random; this.onLearned = java.util.Objects.requireNonNull(onLearned);
        if (minimumKnown < 0 || minimumKnown > 8_192) throw new IllegalArgumentException("Invalid Discovery minimum");
    }
    public Optional<View> beginDiscovery(ResearchContext context, DiscoveryTier tier, boolean lens, long actualStepEnergy) {
        ledger.requireWritable(); checkDiscoveryEnergy(actualStepEnergy);
        var previous = open(context.identity(), Mode.DISCOVERY, tier.ordinal() + 1, "");
        if (previous.isPresent()) return resume(context, previous.get());
        if (!lens || !ledger.discoveryAllowed(context, minimumKnown)) return Optional.empty();
        var pool = catalogue.families().stream().filter(f -> f.discoveryTier() == tier && eligible(context, f) && !effectiveKnown(context, f))
                .map(MaterialResearch::id).sorted().collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (pool.isEmpty()) return Optional.empty();
        // One equal-weight Fisher-Yates permutation per identity+tier operation, generated before any debit.
        for (int i = pool.size() - 1; i > 0; i--) { int other = random.nextInt(i + 1); var swap = pool.get(i); pool.set(i, pool.get(other)); pool.set(other, swap); }
        var session = new Session(UUID.randomUUID(), context.identity(), Mode.DISCOVERY, tier.ordinal() + 1, "", pool,
                signature(actualStepEnergy, DiscoveryTier.STEP_TICKS), tier.steps(), DiscoveryTier.STEP_PRIMAL, actualStepEnergy, DiscoveryTier.STEP_TICKS);
        put(session); return Optional.of(view(context, session));
    }
    public Optional<View> beginDerive(ResearchContext context, String familyId, boolean ownNetworkReady, long actualEnergy, int actualTicks) {
        ledger.requireWritable(); checkDerivePrice(actualEnergy, actualTicks);
        var previous = open(context.identity(), Mode.DERIVE, 0, familyId);
        if (previous.isPresent()) return resume(context, previous.get());
        var family = catalogue.family(familyId);
        if (!ownNetworkReady || family.isEmpty() || !canDerive(context, family.get())) return Optional.empty();
        var session = new Session(UUID.randomUUID(), context.identity(), Mode.DERIVE, 0, familyId, List.of(familyId),
                signature(actualEnergy, actualTicks), 1, family.get().derivePrimal(), actualEnergy, actualTicks);
        put(session); return Optional.of(view(context, session));
    }
    private void put(Session session) {
        if (ledger.sessions.size() >= 65_536) throw new IllegalStateException("Research session limit");
        ledger.sessions.put(session.id, session); ledger.changed();
    }
    public Optional<View> resume(ResearchContext context, UUID id) {
        ledger.requireWritable(); var session = ledger.sessions.get(id);
        if (session == null || !session.identity.equals(context.identity()) || closed(session)) return Optional.empty();
        if (session.state == State.PAUSED) { session.state = State.ACTIVE; ledger.changed(); }
        return Optional.of(view(context, session));
    }
    public void pause(ResearchContext context, UUID id) {
        ledger.requireWritable(); var session = ledger.sessions.get(id);
        if (session != null && session.identity.equals(context.identity()) && !closed(session)) { session.state = State.PAUSED; ledger.changed(); }
    }
    public Optional<View> session(ResearchContext context, UUID id) {
        var session = ledger.sessions.get(id);
        return session != null && session.identity.equals(context.identity()) ? Optional.of(view(context, session)) : Optional.empty();
    }
    public List<View> sessions(ResearchContext context) {
        return ledger.sessions.values().stream().filter(s -> s.identity.equals(context.identity()) && !closed(s)).map(s -> view(context, s)).toList();
    }
    public int available(ResearchContext context, DiscoveryTier tier) {
        return (int) catalogue.families().stream().filter(f -> f.discoveryTier() == tier && eligible(context, f) && !effectiveKnown(context, f)).count();
    }
    public boolean canDerive(ResearchContext context, MaterialResearch family) {
        return family.derive() && !effectiveKnown(context, family) && effectiveKnownSubstances(context).containsAll(family.substances());
    }
    /** Starting knowledge is a catalogue read view, never an earned or copied ledger grant. */
    private boolean effectiveKnown(ResearchContext context, MaterialResearch family) {
        return family.startingKnowledge() || ledger.knows(context, family.id());
    }
    private Set<SubstanceKey> effectiveKnownSubstances(ResearchContext context) {
        var known = new HashSet<>(ledger.knownSubstances(context));
        catalogue.families().stream().filter(MaterialResearch::startingKnowledge).forEach(family -> known.addAll(family.substances()));
        return known;
    }
    /** Call at most once per server tick. A saved tick claim prevents two analyzers from advancing the same session. */
    public Result advance(ResearchContext context, UUID id, long gameTick, boolean lens, boolean ownNetworkReady,
                          long actualEnergy, int actualTicks, Payment payment) {
        ledger.requireWritable(); var session = ledger.sessions.get(id);
        if (session == null || !session.identity.equals(context.identity()) || closed(session)) return Result.UNAVAILABLE;
        if (!session.signature.equals(signature(actualEnergy, actualTicks))) return pauseAs(session, State.DEFINITION_CHANGED);
        if (session.state == State.PAUSED) return Result.PAUSED;
        if (session.mode == Mode.DISCOVERY && (!lens || !ledger.discoveryAllowed(context, minimumKnown))) return pauseAs(session, State.INELIGIBLE);
        if (session.mode == Mode.DERIVE && !ownNetworkReady) return pauseAs(session, State.INELIGIBLE);
        // Removed/changed definitions pause, never masquerade as a fully researched pool and never generate a refund.
        for (var candidate : session.order) if (catalogue.family(candidate).isEmpty()) return pauseAs(session, State.DEFINITION_CHANGED);
        var unknown = session.order.stream().map(candidate -> catalogue.family(candidate).orElseThrow()).filter(f -> !effectiveKnown(context, f)).toList();
        if (unknown.isEmpty()) return exhaust(session);
        var family = unknown.stream().filter(f -> session.mode == Mode.DERIVE ? canDerive(context, f) : eligible(context, f)).findFirst();
        if (family.isEmpty()) return pauseAs(session, State.INELIGIBLE);
        if (session.lastTick == gameTick) return Result.WAITING;
        boolean paidNow = session.paidSteps == session.completedSteps;
        // Ordinary paid work changes only this session's clock. Do not snapshot the entire world ledger every tick.
        boolean finalTick = session.completedSteps + 1 == session.totalSteps && session.elapsed + 1 == session.stepTicks;
        if (!paidNow && !finalTick) {
            session.lastTick = gameTick; session.state = State.ACTIVE; session.elapsed++;
            if (session.elapsed == session.stepTicks) { session.elapsed = 0; session.completedSteps++; }
            ledger.changed(); return Result.WORKING;
        }
        try (var tx = Transaction.openRoot()) {
            ledger.updateSnapshots(tx);
            if (paidNow) {
                var operation = operation(session.id, "step:" + session.paidSteps);
                if (!ledger.hasPayment(operation)) {
                    if (!payment.debit(operation, session.primalPerStep, session.energyPerStep, tx)) return Result.WAITING;
                    if (ledger.needsCellRecord()) {
                        var cell = ledger.cellChanges.get(operation); var energy = ledger.energyChanges.get(operation);
                        if (cell == null || cell.delta() != -session.primalPerStep || !cell.identity().equals(context.identity())
                                || energy == null || energy.energy() != session.energyPerStep || !energy.owner().equals(context.owner()))
                            throw new IllegalStateException("Research debit requires exact authoritative cell/energy/identity receipts");
                    }
                    ledger.addPayment(operation);
                }
                session.paidSteps++;
            }
            session.lastTick = gameTick; session.state = State.ACTIVE;
            session.elapsed++;
            Result result = paidNow ? Result.PAID : Result.WORKING;
            if (session.elapsed == session.stepTicks) {
                session.elapsed = 0; session.completedSteps++;
                if (session.completedSteps == session.totalSteps) {
                    // Knowledge and the final paid work commit together. Another learning path was checked above.
                    ledger.learn(context, family.get(), session.mode == Mode.DERIVE ? LearningPath.DERIVATION : LearningPath.DISCOVERY);
                    session.resultFamily = family.get().id(); session.state = State.COMPLETE; result = Result.LEARNED;
                }
            }
            if (paidNow || result == Result.LEARNED) ledger.persistBoundary();
            tx.commit();
            if (result == Result.LEARNED) onLearned.accept(context, family.get());
            return result;
        }
    }
    private Result pauseAs(Session session, State state) { if (session.state != state) { session.state = state; ledger.changed(); } return Result.PAUSED; }
    private Result exhaust(Session session) {
        if (!closed(session)) {
            try (var tx = Transaction.openRoot()) {
                ledger.updateSnapshots(tx); session.state = State.EXHAUSTED;
                long spent = Math.multiplyExact(session.paidSteps, session.primalPerStep);
                if (spent > 0) {
                    var id = operation(session.id, "refund");
                    ledger.refunds.putIfAbsent(id, new RefundClaim(id, session.identity, spent, 0));
                }
                ledger.persistBoundary(); tx.commit();
            }
        }
        return session.paidSteps > 0 ? Result.REFUND_DUE : Result.UNAVAILABLE;
    }
    /** Claims are identity-bound, persistent, capacity-aware and paid through the same transaction as claim reduction. */
    public long claimRefund(ResearchContext context, UUID claimId, long maximum, Refund sink) {
        ledger.requireWritable(); var claim = ledger.refunds.get(claimId);
        if (maximum <= 0 || claim == null || !claim.identity.equals(context.identity()) || claim.remaining == 0) return 0;
        long requested = Math.min(maximum, claim.remaining);
        try (var tx = Transaction.openRoot()) {
            ledger.updateSnapshots(tx); var operation = operation(claim.id, "payout:" + claim.sequence);
            long accepted = sink.credit(operation, requested, tx);
            if (accepted < 0 || accepted > requested) throw new IllegalStateException("Invalid refund amount");
            if (accepted == 0) return 0;
            if (ledger.needsCellRecord()) {
                var cell = ledger.cellChanges.get(operation);
                if (cell == null || cell.delta() != accepted || !cell.identity().equals(context.identity())) throw new IllegalStateException("Research refund requires an exact authoritative cell/identity receipt");
            }
            claim.remaining -= accepted; claim.sequence = Math.incrementExact(claim.sequence); ledger.addPayment(operation);
            ledger.persistBoundary(); tx.commit(); return accepted;
        }
    }
    public List<UUID> refundClaims(ResearchContext context) {
        return ledger.refunds.values().stream().filter(claim -> claim.identity.equals(context.identity()) && claim.remaining > 0).map(claim -> claim.id).toList();
    }
    public long refundDue(ResearchContext context) {
        long total = 0;
        for (var claim : ledger.refunds.values()) if (claim.identity.equals(context.identity())) total = Math.addExact(total, claim.remaining);
        return total;
    }
    private Optional<Session> open(ResearchIdentity identity, Mode mode, int tier, String target) {
        return ledger.sessions.values().stream().filter(s -> s.identity.equals(identity) && s.mode == mode && s.tier == tier && s.target.equals(target) && !closed(s)).findFirst();
    }
    private Optional<View> resume(ResearchContext context, Session session) { return resume(context, session.id); }
    private static boolean closed(Session session) { return session.state == State.COMPLETE || session.state == State.EXHAUSTED; }
    private boolean eligible(ResearchContext context, MaterialResearch family) {
        return family.requiredDimension().isEmpty() || ledger.visited(context.owner()).contains(family.requiredDimension());
    }
    private String signature(long energy, int ticks) { return ResearchCatalogue.hash(catalogue.fingerprint() + "|" + minimumKnown + "|" + energy + "|" + ticks + "|research-1"); }
    private View view(ResearchContext context, Session session) {
        int count = (int)session.order.stream().map(catalogue::family).filter(Optional::isPresent).map(Optional::orElseThrow)
                .filter(f -> !effectiveKnown(context, f) && eligible(context, f)).count();
        var claim = ledger.refunds.get(operation(session.id, "refund"));
        return new View(session.id, session.mode, session.tier, session.target, session.state, session.paidSteps, session.completedSteps,
                session.totalSteps, session.elapsed, session.stepTicks, session.primalPerStep, session.energyPerStep, count, claim == null ? 0 : claim.remaining, session.state == State.COMPLETE ? session.resultFamily : "");
    }
    public static UUID operation(UUID session, String suffix) { return UUID.nameUUIDFromBytes((session + ":" + suffix).getBytes(StandardCharsets.UTF_8)); }
    private static void checkDiscoveryEnergy(long energy) { if (energy < 450_000 || energy > 1_200_000) throw new IllegalArgumentException("Invalid adjusted Discovery step energy"); }
    private static void checkDerivePrice(long energy, int ticks) { if (energy < 192_000 || energy > 512_000 || ticks < 1 || ticks > DERIVE_TICKS) throw new IllegalArgumentException("Invalid adjusted derivation price"); }
    static void validateLoaded(ResearchLedger ledger) {
        var open = new HashSet<String>();
        for (var session : ledger.sessions.values()) if (!closed(session) && !open.add(session.identity.key() + "|" + session.mode + "|" + session.tier + "|" + session.target))
            throw new IllegalArgumentException("Duplicate open research identity operation");
    }
    static final class Session {
        final UUID id; final ResearchIdentity identity; final Mode mode; final int tier; final String target;
        final List<String> order; final String signature; final int totalSteps; final long primalPerStep, energyPerStep; final int stepTicks;
        String resultFamily = ""; int paidSteps, completedSteps, elapsed; long lastTick = Long.MIN_VALUE; State state = State.ACTIVE;
        Session(UUID id, ResearchIdentity identity, Mode mode, int tier, String target, List<String> order, String signature,
                int totalSteps, long primalPerStep, long energyPerStep, int stepTicks) {
            this.id = id; this.identity = identity; this.mode = mode; this.tier = tier; this.target = target; this.order = List.copyOf(order);
            this.signature = signature; this.totalSteps = totalSteps; this.primalPerStep = primalPerStep; this.energyPerStep = energyPerStep; this.stepTicks = stepTicks;
            if (order.isEmpty() || order.size() > 8_192 || new HashSet<>(order).size() != order.size() || signature.length() != 64) throw new IllegalArgumentException("Invalid research candidates");
            order.forEach(dev.latrox.rematter.chemistry.SubstanceId::parse);
            if (mode == Mode.DISCOVERY) {
                var value = DiscoveryTier.of(tier); checkDiscoveryEnergy(energyPerStep);
                if (!target.isEmpty() || totalSteps != value.steps() || primalPerStep != DiscoveryTier.STEP_PRIMAL || stepTicks != DiscoveryTier.STEP_TICKS) throw new IllegalArgumentException("Invalid Discovery signature");
            } else {
                checkDerivePrice(energyPerStep, stepTicks);
                if (tier != 0 || totalSteps != 1 || primalPerStep < 4_000 || primalPerStep > 16_000 || !order.equals(List.of(target))) throw new IllegalArgumentException("Invalid derivation signature");
            }
        }
        JsonObject toJson() {
            var obj = new JsonObject(); obj.addProperty("id", id.toString()); obj.addProperty("identity", identity.key()); obj.addProperty("mode", mode.name());
            obj.addProperty("tier", tier); obj.addProperty("target", target); var choices = new JsonArray(); order.forEach(choices::add); obj.add("order", choices);
            obj.addProperty("signature", signature); obj.addProperty("total_steps", totalSteps); obj.addProperty("primal", primalPerStep); obj.addProperty("energy", energyPerStep); obj.addProperty("ticks", stepTicks);
            obj.addProperty("paid", paidSteps); obj.addProperty("completed", completedSteps); obj.addProperty("elapsed", elapsed); obj.addProperty("last_tick", lastTick); obj.addProperty("state", state.name()); obj.addProperty("result", resultFamily); return obj;
        }
        static Session fromJson(JsonObject obj) {
            var order = new ArrayList<String>(); obj.getAsJsonArray("order").forEach(e -> order.add(e.getAsString()));
            var session = new Session(UUID.fromString(obj.get("id").getAsString()), ResearchIdentity.parse(obj.get("identity").getAsString()), Mode.valueOf(obj.get("mode").getAsString()),
                    obj.get("tier").getAsInt(), obj.get("target").getAsString(), order, obj.get("signature").getAsString(), obj.get("total_steps").getAsInt(), obj.get("primal").getAsLong(), obj.get("energy").getAsLong(), obj.get("ticks").getAsInt());
            session.paidSteps = obj.get("paid").getAsInt(); session.completedSteps = obj.get("completed").getAsInt(); session.elapsed = obj.get("elapsed").getAsInt(); session.lastTick = obj.get("last_tick").getAsLong(); session.state = State.valueOf(obj.get("state").getAsString()); session.resultFamily = obj.get("result").getAsString();
            if (session.state == State.COMPLETE ? !session.order.contains(session.resultFamily) : !session.resultFamily.isEmpty()) throw new IllegalArgumentException("Invalid research result visibility");
            if (session.completedSteps < 0 || session.completedSteps > session.totalSteps || session.paidSteps < session.completedSteps || session.paidSteps > session.totalSteps || session.paidSteps > session.completedSteps + 1
                    || session.elapsed < 0 || session.elapsed >= session.stepTicks || session.elapsed > 0 && session.paidSteps == session.completedSteps
                    || session.state == State.COMPLETE && session.completedSteps != session.totalSteps)
                throw new IllegalArgumentException("Invalid paid research progress");
            return session;
        }
    }
    static final class RefundClaim {
        final UUID id; final ResearchIdentity identity; long remaining; int sequence;
        RefundClaim(UUID id, ResearchIdentity identity, long remaining, int sequence) {
            if (remaining < 0 || remaining > DiscoveryTier.III.primal() || sequence < 0) throw new IllegalArgumentException("Invalid refund claim");
            this.id = id; this.identity = identity; this.remaining = remaining; this.sequence = sequence;
        }
        JsonObject toJson() { var obj = new JsonObject(); obj.addProperty("id", id.toString()); obj.addProperty("identity", identity.key()); obj.addProperty("remaining", remaining); obj.addProperty("sequence", sequence); return obj; }
        static RefundClaim fromJson(JsonObject obj) { return new RefundClaim(UUID.fromString(obj.get("id").getAsString()), ResearchIdentity.parse(obj.get("identity").getAsString()), obj.get("remaining").getAsLong(), obj.get("sequence").getAsInt()); }
    }
}
