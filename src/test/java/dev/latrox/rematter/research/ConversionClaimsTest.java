package dev.latrox.rematter.research;

import dev.latrox.rematter.chemistry.BuiltinChemistry;
import dev.latrox.rematter.primal.PrimalPricing;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.junit.jupiter.api.Test;
import static dev.latrox.rematter.research.ResearchLedgerTest.*;
import static org.junit.jupiter.api.Assertions.*;

class ConversionClaimsTest {
    private final UUID machine = UUID.randomUUID();
    private final PrimalPricing.Quote quote = new PrimalPricing.Quote(BuiltinChemistry.IRON, 1_000, 1_000, 64_000, 400);
    private static final class Resources extends SnapshotJournal<long[]> {
        long primal = 8_000, energy = 1_200_000, ordinary;
        @Override protected long[] createSnapshot() { return new long[]{primal, energy, ordinary}; }
        @Override protected void revertToSnapshot(long[] values) { primal = values[0]; energy = values[1]; ordinary = values[2]; }
        boolean debit(UUID op, long primal, long energy, TransactionContext tx) { updateSnapshots(tx); this.primal -= primal; this.energy -= energy; return true; }
        Optional<UUID> output(UUID cell, long amount, TransactionContext tx) { updateSnapshots(tx); ordinary += amount; return Optional.of(cell); }
    }
    @Test void repeatedConversionOperationHasOnePaidClaimAndBoundTargetOwnerRevision() {
        var ledger = new ResearchLedger(); var engine = new ConversionClaims(ledger); var id = UUID.randomUUID(); var resources = new Resources();
        var first = engine.commit(OWNER, machine, id, quote, 64_000, resources::debit).orElseThrow();
        var repeated = engine.commit(OWNER, machine, id, quote, 64_000, (op, primal, energy, tx) -> fail("An existing claim cannot pay again")).orElseThrow();
        assertEquals(first, repeated); assertEquals(7_000, resources.primal); assertEquals(1_136_000, resources.energy);
        assertTrue(engine.commit(UUID.randomUUID(), machine, id, quote, 64_000, resources::debit).isEmpty());
        var changed = new PrimalPricing.Quote(BuiltinChemistry.COPPER, 1_000, 1_000, 64_000, 400);
        assertTrue(engine.commit(OWNER, machine, id, changed, 64_000, resources::debit).isEmpty());
    }
    @Test void blockedOutputRetainsClaimAndPartialPayoutSurvivesReloadWithoutDuplication() {
        var ledger = new ResearchLedger(); var engine = new ConversionClaims(ledger); var resources = new Resources(); var id = UUID.randomUUID(); var cell = UUID.randomUUID();
        engine.commit(OWNER, machine, id, quote, 64_000, resources::debit).orElseThrow();
        assertEquals(0, engine.claim(OWNER, id, 1_000, (op, target, amount, tx) -> Optional.empty()));
        assertEquals(250, engine.claim(OWNER, id, 250, (op, target, amount, tx) -> resources.output(cell, amount, tx)));
        var loaded = ResearchLedger.fromJson(ledger.toJson()); var restored = new ConversionClaims(loaded);
        assertEquals(750, restored.claim(OWNER, id, 9_000, (op, target, amount, tx) -> resources.output(cell, amount, tx)));
        assertEquals(0, restored.claim(OWNER, id, 9_000, (op, target, amount, tx) -> fail("Consumed claim cannot replay")));
        assertEquals(1_000, resources.ordinary); assertEquals(2, restored.credits(cell).size());
        assertEquals(250, restored.credits(cell).get(0).amount()); assertEquals(750, restored.credits(cell).get(1).amount());
        assertEquals(restored.credits(cell), new ConversionClaims(ResearchLedger.fromJson(loaded.toJson())).credits(cell));
    }
    @Test void staleOrdinaryCellReconcilesCreditOnceAndLocksCapacityOrSignatureMismatch() {
        var ledger = new ResearchLedger(); var engine = new ConversionClaims(ledger); var id = UUID.randomUUID(); var cell = UUID.randomUUID();
        engine.commit(OWNER, machine, id, quote, 64_000, (op, primal, energy, tx) -> true).orElseThrow();
        engine.claim(OWNER, id, 1_000, (op, target, amount, tx) -> Optional.of(cell));
        var canonical = engine.reconcile(cell, Map.of(), Map.of(), 64_000, 8).orElseThrow();
        assertEquals(Map.of(BuiltinChemistry.IRON, 1_000L), canonical.matter());
        assertEquals(canonical, engine.reconcile(cell, canonical.matter(), canonical.receipts(), 64_000, 8).orElseThrow());
        assertTrue(engine.reconcile(cell, Map.of(), Map.of(), 500, 8).isEmpty());
        var credit = engine.credits(cell).getFirst();
        assertTrue(engine.reconcile(cell, Map.of(), Map.of(credit.operation(), new ConversionClaims.OrdinaryReceipt(BuiltinChemistry.COPPER, 1_000)), 64_000, 8).isEmpty());
    }
    @Test void ordinaryWatermarkReconcilesOnlyMissingCreditsAndKeepsConsumedMatterConsumed() {
        var ledger = new ResearchLedger(); var engine = new ConversionClaims(ledger); var id = UUID.randomUUID(); var cell = UUID.randomUUID();
        engine.commit(OWNER, machine, id, quote, 64_000, (op, primal, energy, tx) -> true).orElseThrow();
        engine.claim(OWNER, id, 250, (op, target, amount, tx) -> Optional.of(cell));
        engine.claim(OWNER, id, 750, (op, target, amount, tx) -> Optional.of(cell));
        var stale = new dev.latrox.rematter.content.MatterCellData.State(cell, java.util.List.of(), 0);
        var canonical = ResearchAuthority.reconcile(ledger, stale, 64_000).orElseThrow();
        assertEquals(2, canonical.creditRevision()); assertEquals(1_000, canonical.contents().getFirst().quantity());
        var partlyConsumed = new dev.latrox.rematter.content.MatterCellData.State(cell, java.util.List.of(), 1);
        var remainder = ResearchAuthority.reconcile(ledger, partlyConsumed, 64_000).orElseThrow();
        assertEquals(750, remainder.contents().getFirst().quantity());
        var consumed = new dev.latrox.rematter.content.MatterCellData.State(cell, java.util.List.of(), 2);
        assertTrue(ResearchAuthority.reconcile(ledger, consumed, 64_000).orElseThrow().contents().isEmpty());
        assertTrue(ResearchAuthority.reconcile(ledger, new dev.latrox.rematter.content.MatterCellData.State(cell, java.util.List.of(), 3), 64_000).isEmpty());
        assertTrue(ResearchAuthority.reconcile(ledger, stale, 500).isEmpty());
    }
    @Test void authorityWriteFailureRollsBackPhysicalDebitAndClaimTogether() {
        var ledger = new ResearchLedger(); ledger.authority(() -> { throw new IllegalStateException("No disk"); });
        var engine = new ConversionClaims(ledger); var resources = new Resources(); var id = UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> engine.commit(OWNER, machine, id, quote, 64_000, (op, primal, energy, tx) -> {
            ledger.recordCellChange(new ResearchLedger.CellChange(UUID.randomUUID(), op, -primal, ResearchIdentity.player(OWNER)), tx);
            ledger.recordEnergyChange(new ResearchLedger.EnergyChange(machine, op, OWNER, energy), tx); return resources.debit(op, primal, energy, tx);
        }));
        assertEquals(8_000, resources.primal); assertEquals(1_200_000, resources.energy); assertTrue(engine.claims(OWNER, machine).isEmpty());
    }
    @Test void authorityWriteFailureRollsBackPayoutAndKeepsResultOwner() {
        var ledger = new ResearchLedger(); var engine = new ConversionClaims(ledger); var resources = new Resources(); var id = UUID.randomUUID(); var cell = UUID.randomUUID();
        engine.commit(OWNER, machine, id, quote, 64_000, resources::debit).orElseThrow();
        ledger.authority(() -> { throw new IllegalStateException("No disk"); });
        assertThrows(IllegalStateException.class, () -> engine.claim(OWNER, id, 1_000, (op, target, amount, tx) -> resources.output(cell, amount, tx)));
        assertEquals(0, resources.ordinary); assertEquals(1_000, engine.claims(OWNER, machine).getFirst().remaining()); assertTrue(engine.credits(cell).isEmpty());
        assertEquals(0, engine.claim(UUID.randomUUID(), id, 1_000, (op, target, amount, tx) -> fail("Other owner cannot withdraw")));
    }
}
