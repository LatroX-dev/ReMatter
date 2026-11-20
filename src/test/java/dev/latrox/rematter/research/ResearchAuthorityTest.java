package dev.latrox.rematter.research;

import dev.latrox.rematter.primal.PrimalCellData;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.latrox.rematter.research.ResearchLedgerTest.*;
import static org.junit.jupiter.api.Assertions.*;

class ResearchAuthorityTest {
    @TempDir Path directory;
    private record WalletState(PrimalCellData.State cell,long energy){}
    private static final class Wallet extends SnapshotJournal<WalletState> implements ResearchSessions.Payment {
        final ResearchLedger ledger;final UUID machine=UUID.randomUUID();
        PrimalCellData.State cell=new PrimalCellData.State(UUID.randomUUID(),256_000,0,Map.of());long energy=2_400_000;
        Wallet(ResearchLedger ledger){this.ledger=ledger;}
        @Override protected WalletState createSnapshot(){return new WalletState(cell,energy);}
        @Override protected void revertToSnapshot(WalletState previous){cell=previous.cell();energy=previous.energy();}
        @Override public boolean debit(UUID operation,long primal,long fe,TransactionContext tx){
            assertEquals(4_000,primal);assertEquals(600_000,fe);updateSnapshots(tx);
            ledger.recordCellChange(new ResearchLedger.CellChange(cell.cellId(),operation,-primal,ResearchIdentity.player(OWNER)),tx);
            ledger.recordEnergyChange(new ResearchLedger.EnergyChange(machine,operation,OWNER,fe),tx);
            cell=cell.withReceipt(operation,-primal);energy-=fe;return true;
        }
    }
    private static ResearchSessions engine(ResearchLedger ledger){
        return new ResearchSessions(ledger,new ResearchCatalogue(List.of(family("diamond",DiscoveryTier.III,""))),32,new Random(7));
    }
    private static void write(Path path,ResearchLedger ledger){
        try{ResearchAuthority.writeState(path,ledger.toJson());}catch(IOException problem){throw new UncheckedIOException(problem);}
    }
    private static ResearchSessions.Result firstStep(ResearchSessions engine,UUID session,ResearchSessions.Payment payment){
        return engine.advance(ResearchContext.personal(OWNER),session,0,true,true,600_000,1_500,payment);
    }
    private static void assertRestartKeepsPaidStep(ResearchLedger loaded,UUID session,UUID cell,UUID machine){
        var context=ResearchContext.personal(OWNER);var resumed=engine(loaded);
        var view=resumed.beginDiscovery(context,DiscoveryTier.III,true,600_000).orElseThrow();
        assertEquals(session,view.id());assertEquals(16,view.totalSteps());assertEquals(1,view.paidSteps());
        assertEquals(1,view.elapsedTicks());assertEquals("",view.resultFamily());assertTrue(loaded.knownFamilies(context).isEmpty());
        var oldPhysical=new PrimalCellData.State(cell,256_000,0,Map.of());
        var recovered=ResearchAuthority.reconcile(loaded,oldPhysical,256_000).orElseThrow();
        assertEquals(cell,recovered.cellId());assertEquals(252_000,recovered.amount());assertEquals(1,recovered.authorityRevision());
        assertEquals(recovered,ResearchAuthority.reconcile(loaded,recovered,256_000).orElseThrow());
        assertEquals(256_000,oldPhysical.amount(),"Reconciliation must not edit stale physical data during reads");
        assertEquals(1,loaded.changesForCell(cell).size());assertEquals(1,loaded.energyChanges.size());assertEquals(1,loaded.payments.size());
        var operation=loaded.changesForCell(cell).getFirst().operation();
        assertEquals(Map.of(operation,600_000L),loaded.outstandingEnergy(machine,Map.of()));
        assertTrue(loaded.outstandingEnergy(machine,Map.of(operation,600_000L)).isEmpty());
        assertEquals(ResearchSessions.Result.WORKING,resumed.advance(context,session,1,true,true,600_000,1_500,
                (op,primal,energy,tx)->fail("A paid step must not debit again after restart")));
        assertEquals(1,resumed.session(context,session).orElseThrow().paidSteps());
    }
    @Test void nativeFilesystemFirstDiscoveryPaymentPublishesAndRestartsWithoutDoubleDebit() throws Exception {
        var ledger=new ResearchLedger();ledger.overrideDiscovery(OWNER,true);var path=directory.resolve("data/rematter/research-authority.json");
        ledger.authority(()->write(path,ledger));var engine=engine(ledger);var wallet=new Wallet(ledger);
        var session=engine.beginDiscovery(ResearchContext.personal(OWNER),DiscoveryTier.III,true,600_000).orElseThrow().id();
        assertEquals(ResearchSessions.Result.PAID,firstStep(engine,session,wallet));
        assertEquals(252_000,wallet.cell.amount());assertEquals(1_800_000,wallet.energy);
        var durable=ResearchAuthority.readState(path);assertEquals(ledger.toJson(),durable.toJson());
        assertEquals(1,durable.authorityRevision);assertFalse(Files.exists(path.resolveSibling("research-authority.json.pending")));
        assertRestartKeepsPaidStep(durable,session,wallet.cell.cellId(),wallet.machine);
    }
    @Test void failureAfterAtomicPublicationRollsBackMemoryButRestartRecoversDurablePayment() throws Exception {
        var ledger=new ResearchLedger();ledger.overrideDiscovery(OWNER,true);var engine=engine(ledger);var wallet=new Wallet(ledger);
        var session=engine.beginDiscovery(ResearchContext.personal(OWNER),DiscoveryTier.III,true,600_000).orElseThrow().id();
        var before=ledger.toJson();var cellBefore=wallet.cell;long energyBefore=wallet.energy;
        var path=directory.resolve("data/rematter/research-authority.json");ResearchAuthority.writeState(path,before);
        var attemptedCell=new AtomicReference<PrimalCellData.State>();
        ledger.authority(()->{
            try{ResearchAuthority.writeState(path,ledger.toJson(),parent->{
                assertEquals(path.getParent(),parent);attemptedCell.set(wallet.cell);
                throw new IOException("Injected directory failure after atomic publication");
            });}catch(IOException problem){throw new UncheckedIOException(problem);}
        });
        var failure=assertThrows(UncheckedIOException.class,()->firstStep(engine,session,wallet));
        assertEquals(0,failure.getSuppressed().length);assertEquals(before,ledger.toJson());
        assertEquals(cellBefore,wallet.cell);assertEquals(energyBefore,wallet.energy);
        assertEquals(0,engine.session(ResearchContext.personal(OWNER),session).orElseThrow().paidSteps());
        var durable=ResearchAuthority.readState(path);assertEquals(1,durable.authorityRevision);assertNotEquals(before,durable.toJson());
        assertFalse(Files.exists(path.resolveSibling("research-authority.json.pending")),"Atomic rename already published the post-state");
        var canonical=ResearchAuthority.reconcile(durable,attemptedCell.get(),256_000).orElseThrow();
        assertEquals(252_000,canonical.amount());assertEquals(1,canonical.authorityRevision());assertTrue(canonical.receipts().isEmpty());
        var bytes=Files.readAllBytes(path);assertRestartKeepsPaidStep(durable,session,cellBefore.cellId(),wallet.machine);
        assertArrayEquals(bytes,Files.readAllBytes(path),"Recovery reads and paid work may not rewrite or reset the authority");
    }
    @Test void failureBeforeAtomicPublicationPreservesExistingAuthority() throws Exception {
        var path=directory.resolve("data/rematter/research-authority.json");var ledger=new ResearchLedger();
        ResearchAuthority.writeState(path,ledger.toJson());var before=Files.readAllBytes(path);
        Files.createDirectory(path.resolveSibling("research-authority.json.pending"));ledger.authorityRevision=1;
        assertThrows(IOException.class,()->ResearchAuthority.writeState(path,ledger.toJson()));
        assertArrayEquals(before,Files.readAllBytes(path));assertEquals(0,ResearchAuthority.readState(path).authorityRevision);
    }
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
