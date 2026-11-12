package dev.latrox.rematter.research;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.latrox.rematter.primal.PrimalCellData;
import dev.latrox.rematter.content.MatterCellData;
import dev.latrox.rematter.chemistry.MatterInventory;
import dev.latrox.rematter.primal.PrimalCells;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Durable authority for research cell payments. The post-state is force-written before the transaction returns
 * to world saving. A stale physical cell is reconciled through its stable ID and immutable operation receipts.
 * Filesystem/codec errors fail closed; an unknown save is never overwritten with an empty account.
 */
final class ResearchAuthority {
    private static final Map<MinecraftServer, ResearchAuthority> ACTIVE = new java.util.concurrent.ConcurrentHashMap<>();
    private final ResearchSavedData data;
    private final Path path;
    private boolean failed;
    private ResearchAuthority(MinecraftServer server, ResearchSavedData data) {
        this.data = data; path = server.getWorldPath(LevelResource.ROOT).resolve("data/rematter/research-authority.json");
        try {
            if (!data.ledger().writable()) throw new IOException("Unknown research SavedData schema; raw state preserved");
            if (Files.exists(path)) {
                if (Files.size(path) > 64 * 1024 * 1024) throw new IOException("Research authority exceeds supported size");
                var durable = readState(path);
                if (durable.authorityRevision > data.ledger().authorityRevision) data.replaceLedger(durable);
                else if (durable.authorityRevision < data.ledger().authorityRevision) throw new IOException("Research SavedData is ahead of its durable authority");
            } else if (data.ledger().authorityRevision > 0) throw new IOException("Missing durable research authority");
        } catch (IOException | RuntimeException problem) { failed = true; dev.latrox.rematter.ReMatter.LOGGER.error("Research authority is locked; preserve its files for recovery", problem); }
        data.ledger().authority(this::persist);
    }
    static void attach(MinecraftServer server, ResearchSavedData data) {
        if (!ACTIVE.containsKey(server)) {
            ACTIVE.put(server, new ResearchAuthority(server, data));
            PrimalCells.installAuthority((state, stack) -> {
                for (var entry : ACTIVE.entrySet()) if (entry.getKey().isSameThread()) return entry.getValue().reconcile(state, stack.getItem() instanceof dev.latrox.rematter.primal.PrimalCellItem cell ? cell.capacity() : -1);
                return Optional.of(state); // Client rendering has no authority and never edits cells.
            });
            PrimalCells.installReceiptLookup((cellId, operation) -> {
                for (var entry : ACTIVE.entrySet()) if (entry.getKey().isSameThread()) {
                    if (entry.getValue().failed) return Optional.empty();
                    var changes = entry.getValue().data.ledger().changesForCell(cellId);
                    for (int i = 0; i < changes.size(); i++) {
                        var change = changes.get(i);
                        if (change.operation().equals(operation)) return Optional.of(new PrimalCells.Receipt(cellId, change.delta(), i + 1L));
                    }
                }
                return Optional.empty();
            });
            MatterCellData.installAuthority((state, capacity) -> {
                for (var entry : ACTIVE.entrySet()) if (entry.getKey().isSameThread()) {
                    if (entry.getValue().failed) return Optional.empty();
                    return reconcile(entry.getValue().data.ledger(), state, capacity);
                }
                return Optional.of(state);
            });
        }
    }
    static boolean healthy(MinecraftServer server) { var value = ACTIVE.get(server); return value != null && !value.failed; }
    static void stopped(MinecraftServer server) { ACTIVE.remove(server); }
    private Optional<PrimalCellData.State> reconcile(PrimalCellData.State state, long capacity) {
        if (failed) return Optional.empty();
        return reconcile(ledger(), state, capacity);
    }
    private ResearchLedger ledger() { return data.ledger(); }
    static Optional<PrimalCellData.State> reconcile(ResearchLedger ledger, PrimalCellData.State state) { return reconcile(ledger, state, Long.MAX_VALUE); }
    static Optional<PrimalCellData.State> reconcile(ResearchLedger ledger, PrimalCellData.State state, long capacity) {
        try {
            if (capacity < 1 || state.amount() > capacity) return Optional.empty();
            var changes = ledger.changesForCell(state.cellId());
            if (state.authorityRevision() > changes.size()) return Optional.empty();
            // A pending component write is acknowledged only by its exact durable operation and revision.
            for (var pending : state.receipts().entrySet()) {
                var change = ledger.cellChanges.get(pending.getKey());
                if (change == null || !change.cellId().equals(state.cellId()) || change.delta() != pending.getValue()) return Optional.empty();
                int position = changes.indexOf(change);
                if (position < 0 || position + 1L > state.authorityRevision()) return Optional.empty();
            }
            long amount = state.amount();
            for (int i = Math.toIntExact(state.authorityRevision()); i < changes.size(); i++) {
                amount = Math.addExact(amount, changes.get(i).delta());
                if (amount < 0 || amount > capacity) return Optional.empty();
            }
            return Optional.of(new PrimalCellData.State(state.cellId(), amount, changes.size(), Map.of()));
        } catch (RuntimeException invalid) { return Optional.empty(); }
    }
    static Optional<MatterCellData.State> reconcile(ResearchLedger ledger, MatterCellData.State state, long capacity) {
        try {
            // Validate the raw list first: a duplicate/invalid unknown row must never be silently normalized by withCredit.
            MatterInventory.restore(new MatterInventory.Snapshot(capacity, state.contents()), MatterCellData.MAX_TYPES);
            var credits = new ConversionClaims(ledger).credits(state.cellId());
            if (state.creditRevision() > credits.size()) return Optional.empty();
            var result = state;
            for (int i = Math.toIntExact(state.creditRevision()); i < credits.size(); i++) {
                var credit = credits.get(i); result = result.withCredit(credit.target(), credit.amount());
            }
            MatterInventory.restore(new MatterInventory.Snapshot(capacity, result.contents()), MatterCellData.MAX_TYPES);
            return Optional.of(result);
        } catch (RuntimeException invalid) { return Optional.empty(); }
    }
    private void persist() {
        if (failed || !data.ledger().writable()) throw new IllegalStateException("Research authority unavailable");
        try { writeState(path, data.ledger().toJson()); }
        catch (IOException problem) { failed = true; throw new IllegalStateException("Cannot durably record research payment; research cells locked", problem); }
    }
    static ResearchLedger readState(Path path) throws IOException {
        if (Files.size(path) > 64 * 1024 * 1024) throw new IOException("Research authority exceeds supported size");
        try {
            var envelope = JsonParser.parseString(Files.readString(path)).getAsJsonObject(); var state = envelope.getAsJsonObject("state");
            if (envelope.get("schema").getAsInt() != 1 || !ResearchCatalogue.hash(state.toString()).equals(envelope.get("sha256").getAsString())) throw new IOException("Invalid research authority checksum/schema");
            var durable = ResearchLedger.fromJson(state); if (!durable.writable()) throw new IOException("Unknown research authority state"); return durable;
        } catch (RuntimeException invalid) { throw new IOException("Invalid research authority document", invalid); }
    }
    static void writeState(Path path, JsonObject state) throws IOException {
        var envelope = new JsonObject(); envelope.addProperty("schema", 1);
        envelope.addProperty("sha256", ResearchCatalogue.hash(state.toString())); envelope.add("state", state);
        var bytes = StandardCharsets.UTF_8.encode(envelope.toString()); var temporary = path.resolveSibling(path.getFileName() + ".pending");
        Files.createDirectories(path.getParent());
        try (var channel = FileChannel.open(temporary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            while (bytes.hasRemaining()) channel.write(bytes); channel.force(true);
        }
        Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        try (var directory = FileChannel.open(path.getParent(), StandardOpenOption.READ)) { directory.force(true); }
    }
}
