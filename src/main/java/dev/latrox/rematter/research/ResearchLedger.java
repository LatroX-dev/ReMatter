package dev.latrox.rematter.research;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/** Versioned server knowledge and operation journal. Reads never turn team knowledge into personal knowledge. */
public final class ResearchLedger {
    public static final int SCHEMA = 1;
    private static final int MAX_IDENTITIES = 65_536, MAX_ENTRIES = 32_768, MAX_OPERATIONS = 262_144;
    public enum Milestone { INDUSTRIAL_REACTOR, INDUSTRIAL_PREPARATOR }
    private final Map<ResearchIdentity, Knowledge> knowledge = new HashMap<>();
    private final Map<UUID, Set<String>> dimensions = new HashMap<>();
    private final Map<UUID, Set<Milestone>> milestones = new HashMap<>();
    private final Set<UUID> overrides = new HashSet<>();
    final Map<UUID, ResearchSessions.Session> sessions = new LinkedHashMap<>();
    final Map<UUID, ResearchSessions.RefundClaim> refunds = new LinkedHashMap<>();
    final Set<UUID> payments = new HashSet<>();
    final Map<UUID, CellChange> cellChanges = new LinkedHashMap<>();
    final Map<UUID, EnergyChange> energyChanges = new LinkedHashMap<>();
    final Map<UUID, ConversionClaims.Claim> conversions = new LinkedHashMap<>();
    final Map<UUID, ConversionClaims.OrdinaryCredit> ordinaryCredits = new LinkedHashMap<>();
    private Map<UUID, List<CellChange>> cellIndex;
    long authorityRevision;
    private boolean durable;
    private Runnable boundary = () -> {};
    private @Nullable JsonObject opaque;
    private Runnable changed = () -> {};
    private final SnapshotJournal<JsonObject> journal = new SnapshotJournal<>() {
        @Override protected JsonObject createSnapshot() { return toJson(); }
        @Override protected void revertToSnapshot(JsonObject state) { restore(fromJson(state)); }
        @Override protected void onRootCommit(JsonObject state) { changed.run(); }
    };
    public ResearchLedger() {}
    public void onChanged(Runnable callback) { changed = java.util.Objects.requireNonNull(callback); }
    public boolean writable() { return opaque == null; }
    void requireWritable() { if (!writable()) throw new IllegalStateException("Unknown research save schema; state preserved"); }
    void updateSnapshots(TransactionContext tx) { requireWritable(); journal.updateSnapshots(tx); }
    void changed() { changed.run(); }
    void authority(Runnable boundary) { this.boundary = boundary; durable = true; }
    void persistBoundary() { authorityRevision = Math.incrementExact(authorityRevision); changed(); boundary.run(); }
    boolean needsCellRecord() { return durable; }
    public record CellChange(UUID cellId, UUID operation, long delta, ResearchIdentity identity) {
        public CellChange {
            java.util.Objects.requireNonNull(cellId); java.util.Objects.requireNonNull(operation); java.util.Objects.requireNonNull(identity);
            if (delta == 0 || delta < -256_000 || delta > 256_000) throw new IllegalArgumentException("Invalid research cell payment");
        }
    }
    public record EnergyChange(UUID machine, UUID operation, UUID owner, long energy) {
        public EnergyChange {
            java.util.Objects.requireNonNull(machine); java.util.Objects.requireNonNull(operation); java.util.Objects.requireNonNull(owner);
            if (energy < 1 || energy > 1_200_000) throw new IllegalArgumentException("Invalid research energy payment");
        }
    }
    public void recordEnergyChange(EnergyChange payment, TransactionContext tx) {
        updateSnapshots(tx); var previous = energyChanges.get(payment.operation());
        if (previous != null && !previous.equals(payment)) throw new IllegalStateException("Research energy payment identity/signature changed");
        if (previous == null && energyChanges.size() >= MAX_OPERATIONS) throw new IllegalStateException("Energy authority journal full");
        energyChanges.putIfAbsent(payment.operation(), payment);
    }
    public Map<UUID, Long> outstandingEnergy(UUID machine, Map<UUID, Long> settled) {
        var owed = new LinkedHashMap<UUID, Long>();
        for (var change : energyChanges.values()) if (change.machine().equals(machine)) {
            long paid = settled.getOrDefault(change.operation(), 0L);
            if (paid < 0 || paid > change.energy()) throw new IllegalArgumentException("Invalid local research energy receipt");
            if (paid < change.energy()) owed.put(change.operation(), change.energy() - paid);
        }
        return Map.copyOf(owed);
    }
    public void recordCellChange(CellChange payment, TransactionContext tx) {
        updateSnapshots(tx);
        var previous = cellChanges.get(payment.operation());
        if (previous != null && !previous.equals(payment)) throw new IllegalStateException("Research payment identity/signature changed");
        if (previous == null && cellChanges.size() >= MAX_OPERATIONS) throw new IllegalStateException("Cell authority journal full");
        cellChanges.putIfAbsent(payment.operation(), payment); cellIndex = null;
    }
    public List<CellChange> changesForCell(UUID cellId) {
        if (cellIndex == null) {
            var index = new HashMap<UUID, List<CellChange>>();
            for (var change : cellChanges.values()) index.computeIfAbsent(change.cellId(), ignored -> new ArrayList<>()).add(change);
            index.replaceAll((id, changes) -> List.copyOf(changes)); cellIndex = Map.copyOf(index);
        }
        return cellIndex.getOrDefault(cellId, List.of());
    }
    private Knowledge mutable(ResearchIdentity id) {
        requireWritable();
        if (!knowledge.containsKey(id) && knowledge.size() >= MAX_IDENTITIES) throw new IllegalStateException("Research identity limit");
        return knowledge.computeIfAbsent(id, ignored -> new Knowledge());
    }
    public Set<String> knownFamilies(ResearchContext context) {
        var result = new TreeSet<String>();
        for (var id : context.readable()) { var found = knowledge.get(id); if (found != null) result.addAll(found.families.keySet()); }
        return Set.copyOf(result);
    }
    public boolean knows(ResearchContext context, String family) { return context.readable().stream().anyMatch(id -> knowledge.containsKey(id) && knowledge.get(id).families.containsKey(family)); }
    public Set<SubstanceKey> knownSubstances(ResearchContext context) {
        var result = new TreeSet<SubstanceKey>();
        for (var id : context.readable()) { var found = knowledge.get(id); if (found != null) result.addAll(found.substances); }
        return Set.copyOf(result);
    }
    public int earnedFamilies(ResearchContext context) {
        var result = new HashSet<String>();
        for (var id : context.readable()) {
            var found = knowledge.get(id);
            if (found != null) found.families.forEach((family, path) -> { if (path.earned()) result.add(family); });
        }
        return result.size();
    }
    public @Nullable LearningPath path(ResearchContext context, String family) {
        for (var id : context.readable()) {
            var found = knowledge.get(id); if (found != null && found.families.containsKey(family)) return found.families.get(family);
        }
        return null;
    }
    public boolean learn(ResearchContext context, MaterialResearch family, LearningPath path) {
        requireWritable(); boolean newToView = !knows(context, family.id());
        grant(mutable(ResearchIdentity.player(context.owner())), family, path);
        if (context.identity().kind() != ResearchIdentity.Kind.PLAYER) grant(mutable(context.identity()), family, path);
        changed(); return newToView;
    }
    public void grant(ResearchIdentity identity, MaterialResearch family, LearningPath path) { grant(mutable(identity), family, path); changed(); }
    private static void grant(Knowledge target, MaterialResearch family, LearningPath path) {
        var previous = target.families.get(family.id());
        if (previous == null || !previous.earned() && path.earned()) target.families.put(family.id(), path);
        target.substances.addAll(family.substances());
        if (target.families.size() > MAX_ENTRIES || target.substances.size() > MAX_ENTRIES) throw new IllegalStateException("Knowledge entry limit");
    }
    public void learnSubstances(ResearchContext context, Set<SubstanceKey> learned) {
        requireWritable();
        if (learned.stream().anyMatch(key -> key.id().toString().equals("rematter:primal_matter"))) throw new IllegalArgumentException("Reserved resource");
        mutable(ResearchIdentity.player(context.owner())).substances.addAll(learned);
        mutable(context.identity()).substances.addAll(learned); changed();
    }
    public boolean revoke(ResearchIdentity identity, String family) {
        requireWritable(); var target = knowledge.get(identity);
        boolean removed = target != null && target.families.remove(family) != null;
        if (removed) changed(); return removed;
    }
    /** Explicit membership event contribution is one-way: personal -> team, never team -> personal. */
    public void contributePersonal(UUID player, ResearchIdentity team) {
        if (team.kind() == ResearchIdentity.Kind.PLAYER) return;
        requireWritable(); var own = knowledge.get(ResearchIdentity.player(player)); if (own == null) return;
        var target = mutable(team); boolean updated = target.substances.addAll(own.substances);
        for (var entry : own.families.entrySet()) {
            var previous = target.families.get(entry.getKey());
            if (previous == null || !previous.earned() && entry.getValue().earned()) { target.families.put(entry.getKey(), entry.getValue()); updated = true; }
        }
        if (updated) changed();
    }
    public void visit(UUID owner, String dimension) {
        requireWritable(); SubstanceId.parse(dimension);
        if (dimensions.computeIfAbsent(owner, ignored -> new HashSet<>()).add(dimension)) changed();
    }
    public Set<String> visited(UUID owner) { return Set.copyOf(dimensions.getOrDefault(owner, Set.of())); }
    public void milestone(UUID owner, Milestone milestone) {
        requireWritable(); if (milestones.computeIfAbsent(owner, ignored -> new HashSet<>()).add(milestone)) changed();
    }
    public boolean hasMilestone(UUID owner, Milestone milestone) { return milestones.getOrDefault(owner, Set.of()).contains(milestone); }
    public void overrideDiscovery(UUID owner, boolean allow) { requireWritable(); if (allow ? overrides.add(owner) : overrides.remove(owner)) changed(); }
    public boolean discoveryAllowed(ResearchContext context, int minimumKnown) {
        return overrides.contains(context.owner()) || earnedFamilies(context) >= minimumKnown
                && hasMilestone(context.owner(), Milestone.INDUSTRIAL_REACTOR)
                && hasMilestone(context.owner(), Milestone.INDUSTRIAL_PREPARATOR);
    }
    boolean hasPayment(UUID id) { return payments.contains(id); }
    void addPayment(UUID id) {
        if (!payments.contains(id) && payments.size() >= MAX_OPERATIONS) throw new IllegalStateException("Research payment journal is full");
        payments.add(id);
    }
    public JsonObject toJson() {
        if (opaque != null) return opaque.deepCopy();
        var root = new JsonObject(); root.addProperty("schema", SCHEMA);
        var owners = new JsonObject();
        new TreeMap<>(knowledge.entrySet().stream().collect(java.util.stream.Collectors.toMap(e -> e.getKey().key(), Map.Entry::getValue)))
                .forEach((id, value) -> {
                    var obj = new JsonObject(); var families = new JsonObject();
                    new TreeMap<>(value.families).forEach((family, path) -> families.addProperty(family, path.name()));
                    obj.add("families", families); var keys = new JsonArray();
                    new TreeSet<>(value.substances).forEach(key -> keys.add(key.id() + "|" + key.phase().name()));
                    obj.add("substances", keys); owners.add(id, obj);
                });
        root.addProperty("authority_revision", authorityRevision);
        var conversionArray = new JsonArray(); conversions.values().forEach(claim -> conversionArray.add(claim.toJson())); root.add("conversions", conversionArray);
        var outputArray = new JsonArray(); ordinaryCredits.values().forEach(credit -> {
            var obj = new JsonObject(); obj.addProperty("cell", credit.cellId().toString()); obj.addProperty("operation", credit.operation().toString()); obj.addProperty("claim", credit.claim().toString()); obj.addProperty("owner", credit.owner().toString());
            obj.addProperty("target", credit.target().id().toString()); obj.addProperty("phase", credit.target().phase().name()); obj.addProperty("amount", credit.amount()); outputArray.add(obj);
        }); root.add("ordinary_credits", outputArray);
        var cells = new JsonArray();
        cellChanges.values().forEach(change -> {
            var obj = new JsonObject(); obj.addProperty("cell", change.cellId().toString()); obj.addProperty("operation", change.operation().toString());
            obj.addProperty("delta", change.delta()); obj.addProperty("identity", change.identity().key()); cells.add(obj);
        });
        root.add("cell_changes", cells);
        var energy = new JsonArray();
        energyChanges.values().forEach(change -> {
            var obj = new JsonObject(); obj.addProperty("machine", change.machine().toString()); obj.addProperty("operation", change.operation().toString());
            obj.addProperty("owner", change.owner().toString()); obj.addProperty("energy", change.energy()); energy.add(obj);
        }); root.add("energy_changes", energy);
        root.add("knowledge", owners); var visits = new JsonObject();
        dimensions.forEach((owner, ids) -> visits.add(owner.toString(), strings(new TreeSet<>(ids)))); root.add("dimensions", visits);
        var built = new JsonObject(); milestones.forEach((owner, ids) -> built.add(owner.toString(), strings(ids.stream().map(Enum::name).sorted().toList()))); root.add("milestones", built);
        root.add("overrides", strings(overrides.stream().map(UUID::toString).sorted().toList()));
        root.add("payments", strings(payments.stream().map(UUID::toString).sorted().toList()));
        var runs = new JsonArray(); sessions.values().forEach(session -> runs.add(session.toJson())); root.add("sessions", runs);
        var claims = new JsonArray(); refunds.values().forEach(claim -> claims.add(claim.toJson())); root.add("refunds", claims);
        return root;
    }
    private static JsonArray strings(Iterable<String> values) { var array = new JsonArray(); values.forEach(array::add); return array; }
    public static ResearchLedger fromJson(JsonObject root) {
        var result = new ResearchLedger();
        if (!root.has("schema") || root.get("schema").getAsInt() != SCHEMA) { result.opaque = root.deepCopy(); return result; }
        result.authorityRevision = root.get("authority_revision").getAsLong();
        if (result.authorityRevision < 0) throw new IllegalArgumentException("Invalid authority revision");
        bound(root.getAsJsonArray("conversions").size(), MAX_IDENTITIES);
        root.getAsJsonArray("conversions").forEach(e -> { var claim = ConversionClaims.Claim.fromJson(e.getAsJsonObject()); if (result.conversions.putIfAbsent(claim.id, claim) != null) throw new IllegalArgumentException("Duplicate conversion claim"); });
        bound(root.getAsJsonArray("ordinary_credits").size(), MAX_OPERATIONS);
        root.getAsJsonArray("ordinary_credits").forEach(e -> {
            var obj = e.getAsJsonObject(); var credit = new ConversionClaims.OrdinaryCredit(UUID.fromString(obj.get("cell").getAsString()), UUID.fromString(obj.get("operation").getAsString()), UUID.fromString(obj.get("claim").getAsString()), UUID.fromString(obj.get("owner").getAsString()), SubstanceKey.of(obj.get("target").getAsString(), Phase.valueOf(obj.get("phase").getAsString())), obj.get("amount").getAsLong());
            if (result.ordinaryCredits.putIfAbsent(credit.operation(), credit) != null) throw new IllegalArgumentException("Duplicate ordinary output credit");
        });
        bound(root.getAsJsonArray("cell_changes").size(), MAX_OPERATIONS);
        root.getAsJsonArray("cell_changes").forEach(e -> {
            var obj = e.getAsJsonObject(); var change = new CellChange(UUID.fromString(obj.get("cell").getAsString()), UUID.fromString(obj.get("operation").getAsString()), obj.get("delta").getAsLong(), ResearchIdentity.parse(obj.get("identity").getAsString()));
            if (result.cellChanges.putIfAbsent(change.operation(), change) != null) throw new IllegalArgumentException("Duplicate authority payment");
        });
        bound(root.getAsJsonArray("energy_changes").size(), MAX_OPERATIONS);
        root.getAsJsonArray("energy_changes").forEach(e -> {
            var obj = e.getAsJsonObject(); var change = new EnergyChange(UUID.fromString(obj.get("machine").getAsString()), UUID.fromString(obj.get("operation").getAsString()), UUID.fromString(obj.get("owner").getAsString()), obj.get("energy").getAsLong());
            if (result.energyChanges.putIfAbsent(change.operation(), change) != null) throw new IllegalArgumentException("Duplicate energy authority payment");
        });
        var owners = root.getAsJsonObject("knowledge"); bound(owners.size(), MAX_IDENTITIES);
        owners.entrySet().forEach(entry -> {
            var key = ResearchIdentity.parse(entry.getKey()); var obj = entry.getValue().getAsJsonObject(); var value = new Knowledge();
            var learned = obj.getAsJsonObject("families"); bound(learned.size(), MAX_ENTRIES);
            learned.entrySet().forEach(e -> { SubstanceId.parse(e.getKey()); value.families.put(e.getKey(), LearningPath.valueOf(e.getValue().getAsString())); });
            var substances = obj.getAsJsonArray("substances"); bound(substances.size(), MAX_ENTRIES);
            substances.forEach(e -> { var parts = e.getAsString().split("\\|", -1); if (parts.length != 2) throw new IllegalArgumentException("Invalid saved substance"); value.substances.add(SubstanceKey.of(parts[0], Phase.valueOf(parts[1]))); });
            result.knowledge.put(key, value);
        });
        var visits = root.getAsJsonObject("dimensions"); bound(visits.size(), MAX_IDENTITIES);
        visits.entrySet().forEach(e -> { var set = new HashSet<String>(); bound(e.getValue().getAsJsonArray().size(), MAX_ENTRIES); e.getValue().getAsJsonArray().forEach(v -> { SubstanceId.parse(v.getAsString()); set.add(v.getAsString()); }); result.dimensions.put(UUID.fromString(e.getKey()), set); });
        var built = root.getAsJsonObject("milestones"); bound(built.size(), MAX_IDENTITIES);
        built.entrySet().forEach(e -> { var set = new HashSet<Milestone>(); e.getValue().getAsJsonArray().forEach(v -> set.add(Milestone.valueOf(v.getAsString()))); result.milestones.put(UUID.fromString(e.getKey()), set); });
        bound(root.getAsJsonArray("overrides").size(), MAX_IDENTITIES); root.getAsJsonArray("overrides").forEach(e -> result.overrides.add(UUID.fromString(e.getAsString())));
        bound(root.getAsJsonArray("payments").size(), MAX_OPERATIONS); root.getAsJsonArray("payments").forEach(e -> result.payments.add(UUID.fromString(e.getAsString())));
        bound(root.getAsJsonArray("sessions").size(), MAX_IDENTITIES); root.getAsJsonArray("sessions").forEach(e -> { var session = ResearchSessions.Session.fromJson(e.getAsJsonObject()); if (result.sessions.putIfAbsent(session.id, session) != null) throw new IllegalArgumentException("Duplicate research session"); });
        bound(root.getAsJsonArray("refunds").size(), MAX_IDENTITIES); root.getAsJsonArray("refunds").forEach(e -> { var claim = ResearchSessions.RefundClaim.fromJson(e.getAsJsonObject()); if (result.refunds.putIfAbsent(claim.id, claim) != null) throw new IllegalArgumentException("Duplicate refund claim"); });
        for (var claim : result.conversions.values()) {
            long credited = 0; int count = 0;
            for (var credit : result.ordinaryCredits.values()) if (credit.claim().equals(claim.id)) {
                if (!credit.owner().equals(claim.owner) || !credit.target().equals(claim.target)) throw new IllegalArgumentException("Output credit owner/target mismatch");
                credited = Math.addExact(credited, credit.amount()); count++;
            }
            if (credited != claim.produced - claim.remaining || count != claim.sequence) throw new IllegalArgumentException("Output credit/claim balance mismatch");
        }
        for (var credit : result.ordinaryCredits.values()) if (!result.conversions.containsKey(credit.claim())) throw new IllegalArgumentException("Orphan ordinary output credit");
        ResearchSessions.validateLoaded(result);
        return result;
    }
    private static void bound(int value, int maximum) { if (value > maximum) throw new IllegalArgumentException("Research data exceeds supported limits"); }
    private void restore(ResearchLedger other) {
        knowledge.clear(); knowledge.putAll(other.knowledge); dimensions.clear(); dimensions.putAll(other.dimensions);
        milestones.clear(); milestones.putAll(other.milestones); overrides.clear(); overrides.addAll(other.overrides);
        sessions.clear(); sessions.putAll(other.sessions); refunds.clear(); refunds.putAll(other.refunds); payments.clear(); payments.addAll(other.payments); cellChanges.clear(); cellChanges.putAll(other.cellChanges); cellIndex = null; energyChanges.clear(); energyChanges.putAll(other.energyChanges); conversions.clear(); conversions.putAll(other.conversions); ordinaryCredits.clear(); ordinaryCredits.putAll(other.ordinaryCredits); authorityRevision = other.authorityRevision; opaque = other.opaque;
    }
    private static final class Knowledge {
        final Map<String, LearningPath> families = new HashMap<>();
        final Set<SubstanceKey> substances = new HashSet<>();
    }
}
