package dev.latrox.rematter.research;

import dev.latrox.rematter.primal.PrimalCellData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.latrox.rematter.research.ResearchLedgerTest.*;
import static org.junit.jupiter.api.Assertions.*;

class ResearchAuthorityTest {
    @TempDir Path directory;
    @Test void stalePhysicalCellPaysAuthoritativeDebitOnceAndReadNeverMutatesRawState() {
        var ledger = new ResearchLedger(); var cell = UUID.randomUUID(); var operation = UUID.randomUUID();
        try (var tx = Transaction.openRoot()) { ledger.recordCellChange(new ResearchLedger.CellChange(cell, operation, -4_000, ResearchIdentity.player(OWNER)), tx); tx.commit(); }
        var oldPhysical = new PrimalCellData.State(cell, 8_000, 0, Map.of());
        var canonical = ResearchAuthority.reconcile(ledger, oldPhysical).orElseThrow();
        assertEquals(4_000, canonical.amount()); assertEquals(8_000, oldPhysical.amount()); assertTrue(oldPhysical.receipts().isEmpty());
        assertEquals(canonical, ResearchAuthority.reconcile(ledger, canonical).orElseThrow());
        assertEquals(1, canonical.authorityRevision()); assertTrue(canonical.receipts().isEmpty());
        assertTrue(ResearchAuthority.reconcile(ledger, new PrimalCellData.State(cell, 4_000, 2, Map.of())).isEmpty());
        assertTrue(ResearchAuthority.reconcile(ledger, new PrimalCellData.State(cell, 1_000, 0, Map.of())).isEmpty(), "An underfunded stale cell must lock");
        assertTrue(ResearchAuthority.reconcile(ledger, new PrimalCellData.State(cell, 8_000, 1, Map.of(operation, -1_000L))).isEmpty(), "Receipt signature may not change");
    }
    @Test void moreThan4096PaymentsUseCompactWatermarkAndDoNotRestoreConsumedResources() {
        var ledger = new ResearchLedger(); var cell = UUID.randomUUID();
        for (int i = 0; i < 10_000; i++) {
            var operation = UUID.nameUUIDFromBytes(("payment-" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ledger.cellChanges.put(operation, new ResearchLedger.CellChange(cell, operation, i % 2 == 0 ? -1 : 1, ResearchIdentity.player(OWNER)));
        }
        var original = new PrimalCellData.State(cell, 1_000, 0, Map.of());
        var canonical = ResearchAuthority.reconcile(ledger, original).orElseThrow();
        assertEquals(10_000, canonical.authorityRevision()); assertEquals(1_000, canonical.amount()); assertTrue(canonical.receipts().isEmpty());
        assertEquals(canonical, ResearchAuthority.reconcile(ledger, original).orElseThrow());
        assertEquals(canonical, ResearchAuthority.reconcile(ledger, canonical).orElseThrow());
        var consumed = new PrimalCellData.State(cell, 250, canonical.authorityRevision(), Map.of());
        assertEquals(250, ResearchAuthority.reconcile(ledger, consumed).orElseThrow().amount());
        var data = PrimalCellData.from(canonical);
        var encoded = PrimalCellData.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, data).getOrThrow();
        var decoded = PrimalCellData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, encoded).getOrThrow();
        assertEquals(canonical, decoded.open(16_000).orElseThrow());
        assertTrue(decoded.copyTag().getList("receipts").orElseThrow().isEmpty());
        assertTrue(decoded.copyTag().toString().length() < 400, "The physical cell must not carry operation history");
    }
    @Test void staleCellCannotCrossAnInvalidDebitOrCapacityPrefixEvenIfFinalAmountWouldFit() {
        var ledger = new ResearchLedger(); var cell = UUID.randomUUID();
        var debit = UUID.randomUUID(); var refund = UUID.randomUUID();
        ledger.cellChanges.put(debit, new ResearchLedger.CellChange(cell, debit, -4_000, ResearchIdentity.player(OWNER)));
        ledger.cellChanges.put(refund, new ResearchLedger.CellChange(cell, refund, 4_000, ResearchIdentity.player(OWNER)));
        assertTrue(ResearchAuthority.reconcile(ledger, new PrimalCellData.State(cell, 0, 0, Map.of()), 16_000).isEmpty());
        var other = new ResearchLedger();
        other.cellChanges.put(refund, new ResearchLedger.CellChange(cell, refund, 4_000, ResearchIdentity.player(OWNER)));
        other.cellChanges.put(debit, new ResearchLedger.CellChange(cell, debit, -4_000, ResearchIdentity.player(OWNER)));
        assertTrue(ResearchAuthority.reconcile(other, new PrimalCellData.State(cell, 1_000, 0, Map.of()), 2_000).isEmpty());
    }
    @Test void authorityRefundCannotPayTwiceOnLoadedReceipt() {
        var ledger = new ResearchLedger(); var cell = UUID.randomUUID(); var operation = UUID.randomUUID();
        try (var tx = Transaction.openRoot()) { ledger.recordCellChange(new ResearchLedger.CellChange(cell, operation, 4_000, ResearchIdentity.player(OWNER)), tx); tx.commit(); }
        var canonical = ResearchAuthority.reconcile(ledger, new PrimalCellData.State(cell, 0, 0, Map.of())).orElseThrow();
        assertEquals(4_000, canonical.amount()); assertEquals(canonical, ResearchAuthority.reconcile(ledger, canonical).orElseThrow());
    }
    @Test void durablePostStateRoundTripsAndChecksumFailureNeverBecomesEmptyKnowledge() throws Exception {
        var path = directory.resolve("research/authority.json"); var ledger = new ResearchLedger();
        ledger.learn(ResearchContext.personal(OWNER), family("sand", DiscoveryTier.I, ""), LearningPath.ANALYSIS);
        ledger.authorityRevision = 17;
        ResearchAuthority.writeState(path, ledger.toJson());
        assertEquals(ledger.toJson(), ResearchAuthority.readState(path).toJson());
        Files.writeString(path, Files.readString(path).replace("ANALYSIS", "STARTING"));
        assertThrows(java.io.IOException.class, () -> ResearchAuthority.readState(path));
        assertTrue(Files.readString(path).contains("STARTING"));
    }
    @Test void energyDebtSurvivesIndependentMachineRollbackAndPartialRepayment() {
        var ledger = new ResearchLedger(); var machine = UUID.randomUUID(); var operation = UUID.randomUUID();
        try (var tx = Transaction.openRoot()) { ledger.recordEnergyChange(new ResearchLedger.EnergyChange(machine, operation, OWNER, 600_000), tx); tx.commit(); }
        var loaded = ResearchLedger.fromJson(ledger.toJson());
        assertEquals(Map.of(operation, 600_000L), loaded.outstandingEnergy(machine, Map.of()));
        assertEquals(Map.of(operation, 400_000L), loaded.outstandingEnergy(machine, Map.of(operation, 200_000L)));
        assertTrue(loaded.outstandingEnergy(machine, Map.of(operation, 600_000L)).isEmpty());
        assertTrue(loaded.outstandingEnergy(UUID.randomUUID(), Map.of()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> loaded.outstandingEnergy(machine, Map.of(operation, 600_001L)));
    }
}
