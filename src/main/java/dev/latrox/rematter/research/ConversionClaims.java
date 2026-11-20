package dev.latrox.rematter.research;

import com.google.gson.JsonObject;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.primal.PrimalPricing;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** A Converter pays once into a durable, owner-bound result claim before touching ordinary storage. */
public final class ConversionClaims {
    public record View(UUID id, UUID machine, SubstanceKey target, long produced, long remaining, long primal, long energy) {}
    public record OrdinaryReceipt(SubstanceKey target, long amount) {
        public OrdinaryReceipt { if (target == null || amount <= 0 || target.id().toString().equals("rematter:primal_matter")) throw new IllegalArgumentException("Invalid ordinary output receipt"); }
    }
    public record OrdinaryCredit(UUID cellId, UUID operation, UUID claim, UUID owner, SubstanceKey target, long amount) {
        public OrdinaryCredit { if (cellId == null || operation == null || claim == null || owner == null || amount <= 0 || amount > 256_000) throw new IllegalArgumentException("Invalid ordinary cell credit"); new OrdinaryReceipt(target, amount); }
        public OrdinaryReceipt receipt() { return new OrdinaryReceipt(target, amount); }
    }
    public record OrdinaryState(Map<SubstanceKey, Long> matter, Map<UUID, OrdinaryReceipt> receipts) {
        public OrdinaryState { matter = Map.copyOf(matter); receipts = Map.copyOf(receipts); }
    }
    /** Credit exactly the requested amount to one physical receipt-capable ordinary cell, in the provided transaction. */
    @FunctionalInterface public interface OrdinaryOutput {
        Optional<UUID> credit(UUID operation, SubstanceKey target, long amount, TransactionContext transaction);
    }
    private final ResearchLedger ledger;
    public ConversionClaims(ResearchLedger ledger) { this.ledger = ledger; }
    public Optional<View> commit(UUID owner, UUID machine, UUID operation, PrimalPricing.Quote quote, long actualEnergy, ResearchSessions.Payment payment) {
        ledger.requireWritable();
        if (actualEnergy < Math.max(1, quote.energy() * 3 / 4) || actualEnergy > Math.multiplyExact(quote.energy(), 2)
                || actualEnergy > 1_200_000 || quote.primal() > 256_000 || quote.output() > 256_000) throw new IllegalArgumentException("Invalid adjusted Converter quote");
        var signature = ResearchCatalogue.hash("primal-converter-1|" + quote.target() + "|" + quote.output() + "|" + quote.primal() + "|" + actualEnergy);
        var previous = ledger.conversions.get(operation);
        if (previous != null) return previous.owner.equals(owner) && previous.machine.equals(machine) && previous.signature.equals(signature) ? Optional.of(previous.view()) : Optional.empty();
        if (ledger.conversions.size() >= 65_536) throw new IllegalStateException("Conversion claim limit");
        try (var tx = Transaction.openRoot()) {
            ledger.updateSnapshots(tx);
            if (!payment.debit(operation, quote.primal(), actualEnergy, tx)) return Optional.empty();
            if (ledger.needsCellRecord()) {
                var cell = ledger.cellChanges.get(operation); var energy = ledger.energyChanges.get(operation);
                if (cell == null || cell.delta() != -quote.primal() || energy == null || energy.energy() != actualEnergy || !energy.owner().equals(owner) || !energy.machine().equals(machine))
                    throw new IllegalStateException("Converter debit requires exact cell and energy authority receipts");
            }
            var claim = new Claim(operation, owner, machine, quote.target(), quote.output(), quote.output(), quote.primal(), actualEnergy, signature, 0);
            ledger.conversions.put(operation, claim); ledger.addPayment(operation); ledger.persistBoundary(); tx.commit(); return Optional.of(claim.view());
        }
    }
    public List<View> claims(UUID owner, UUID machine) { return ledger.conversions.values().stream().filter(c -> c.owner.equals(owner) && c.machine.equals(machine)).map(Claim::view).toList(); }
    public long claim(UUID owner, UUID claimId, long maximum, OrdinaryOutput output) {
        ledger.requireWritable(); var claim = ledger.conversions.get(claimId);
        if (maximum <= 0 || claim == null || !claim.owner.equals(owner) || claim.remaining == 0) return 0;
        long amount = Math.min(maximum, claim.remaining); UUID operation = ResearchSessions.operation(claim.id, "ordinary-output:" + claim.sequence);
        try (var tx = Transaction.openRoot()) {
            ledger.updateSnapshots(tx); var cell = output.credit(operation, claim.target, amount, tx);
            if (cell.isEmpty()) return 0;
            var credit = new OrdinaryCredit(cell.get(), operation, claim.id, owner, claim.target, amount);
            var previous = ledger.ordinaryCredits.putIfAbsent(operation, credit);
            if (previous != null && !previous.equals(credit)) throw new IllegalStateException("Converter output destination/signature changed");
            if (ledger.ordinaryCredits.size() > 262_144) throw new IllegalStateException("Ordinary output authority journal full");
            claim.remaining -= amount; claim.sequence = Math.incrementExact(claim.sequence); ledger.addPayment(operation);
            ledger.persistBoundary(); tx.commit(); return amount;
        }
    }
    public List<OrdinaryCredit> credits(UUID cellId) { return ledger.ordinaryCredits.values().stream().filter(c -> c.cellId().equals(cellId)).toList(); }
    /** Virtual reconciliation; caller preserves raw unknown contents and writes this canonical state on its next commit. */
    public Optional<OrdinaryState> reconcile(UUID cellId, Map<SubstanceKey, Long> rawMatter, Map<UUID, OrdinaryReceipt> rawReceipts, long capacity, int typeLimit) {
        try {
            var matter = new TreeMap<>(rawMatter); var receipts = new java.util.HashMap<>(rawReceipts);
            if (matter.values().stream().anyMatch(v -> v <= 0)) return Optional.empty();
            for (var credit : credits(cellId)) {
                var previous = receipts.get(credit.operation());
                if (previous != null && !previous.equals(credit.receipt())) return Optional.empty();
                if (previous == null) { matter.merge(credit.target(), credit.amount(), Math::addExact); receipts.put(credit.operation(), credit.receipt()); }
            }
            long total = 0; for (long value : matter.values()) total = Math.addExact(total, value);
            if (total > capacity || matter.size() > typeLimit || receipts.size() > 4096) return Optional.empty();
            return Optional.of(new OrdinaryState(matter, receipts));
        } catch (ArithmeticException invalid) { return Optional.empty(); }
    }
    static final class Claim {
        final UUID id, owner, machine; final SubstanceKey target; final long produced, primal, energy; final String signature;
        long remaining; int sequence;
        Claim(UUID id, UUID owner, UUID machine, SubstanceKey target, long produced, long remaining, long primal, long energy, String signature, int sequence) {
            if (id == null || owner == null || machine == null || target == null || produced < 1 || produced > 256_000 || remaining < 0 || remaining > produced || primal < produced || primal > 256_000 || energy < 1 || energy > 1_200_000 || signature.length() != 64 || sequence < 0) throw new IllegalArgumentException("Invalid Converter result claim");
            this.id = id; this.owner = owner; this.machine = machine; this.target = target; this.produced = produced; this.remaining = remaining; this.primal = primal; this.energy = energy; this.signature = signature; this.sequence = sequence;
        }
        View view() { return new View(id, machine, target, produced, remaining, primal, energy); }
        JsonObject toJson() {
            var obj = new JsonObject(); obj.addProperty("id", id.toString()); obj.addProperty("owner", owner.toString()); obj.addProperty("machine", machine.toString());
            obj.addProperty("target", target.id().toString()); obj.addProperty("phase", target.phase().name()); obj.addProperty("produced", produced); obj.addProperty("remaining", remaining); obj.addProperty("primal", primal); obj.addProperty("energy", energy); obj.addProperty("signature", signature); obj.addProperty("sequence", sequence); return obj;
        }
        static Claim fromJson(JsonObject obj) { return new Claim(UUID.fromString(obj.get("id").getAsString()), UUID.fromString(obj.get("owner").getAsString()), UUID.fromString(obj.get("machine").getAsString()), SubstanceKey.of(obj.get("target").getAsString(), dev.latrox.rematter.chemistry.Phase.valueOf(obj.get("phase").getAsString())), obj.get("produced").getAsLong(), obj.get("remaining").getAsLong(), obj.get("primal").getAsLong(), obj.get("energy").getAsLong(), obj.get("signature").getAsString(), obj.get("sequence").getAsInt()); }
    }
}
