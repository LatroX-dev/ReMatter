package dev.latrox.rematter.research;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.junit.jupiter.api.Test;
import static dev.latrox.rematter.research.ResearchLedgerTest.*;
import static org.junit.jupiter.api.Assertions.*;

class ResearchSessionsTest {
    private final ResearchContext context = ResearchContext.personal(OWNER);
    private final MaterialResearch sand = family("sand", DiscoveryTier.I, "");
    private final MaterialResearch quartz = family("quartz", DiscoveryTier.I, "");
    private ResearchLedger enabled() { var ledger = new ResearchLedger(); ledger.overrideDiscovery(OWNER, true); return ledger; }
    private ResearchSessions engine(ResearchLedger ledger, MaterialResearch... families) { return new ResearchSessions(ledger, new ResearchCatalogue(List.of(families)), 32, new Random(7)); }
    private static final class Pay implements ResearchSessions.Payment {
        long primal, energy; int calls;
        @Override public boolean debit(UUID op, long primal, long energy, TransactionContext tx) { this.primal += primal; this.energy += energy; calls++; return true; }
    }
    private ResearchSessions.Result tick(ResearchSessions engine, UUID id, long tick, ResearchSessions.Payment payment) {
        return engine.advance(context, id, tick, true, true, DiscoveryTier.STEP_ENERGY, DiscoveryTier.STEP_TICKS, payment);
    }
    @Test void discoveryIsPaidInFourStepsAndCannotRunFasterThroughAnotherMachine() {
        var ledger = enabled(); var engine = engine(ledger, sand, quartz); var payment = new Pay();
        var id = engine.beginDiscovery(context, DiscoveryTier.I, true, 600_000).orElseThrow().id();
        assertEquals(ResearchSessions.Result.PAID, tick(engine, id, 0, payment));
        assertEquals(ResearchSessions.Result.WAITING, tick(engine, id, 0, payment));
        assertEquals(1, engine.session(context, id).orElseThrow().elapsedTicks());
        for (int i = 1; i < 5_999; i++) tick(engine, id, i, payment);
        assertTrue(ledger.knownFamilies(context).isEmpty());
        assertEquals(ResearchSessions.Result.LEARNED, tick(engine, id, 5_999, payment));
        assertEquals(1, ledger.knownFamilies(context).size()); assertTrue(ledger.knownFamilies(context).contains(engine.session(context, id).orElseThrow().resultFamily())); assertEquals(Set.of(SILICA), ledger.knownSubstances(context));
        assertEquals(16_000, payment.primal); assertEquals(2_400_000, payment.energy); assertEquals(4, payment.calls);
        var second = engine.beginDiscovery(context, DiscoveryTier.I, true, 600_000).orElseThrow();
        assertNotEquals(id, second.id()); assertEquals(1, second.candidates());
    }
    @Test void cancellationReloadAndDifferentMachineResumeSameHiddenOrder() {
        var ledger = enabled(); var engine = engine(ledger, sand, quartz);
        var id = engine.beginDiscovery(context, DiscoveryTier.I, true, 600_000).orElseThrow().id();
        assertEquals("", engine.session(context, id).orElseThrow().resultFamily());
        tick(engine, id, 1, new Pay()); engine.pause(context, id);
        var before = ledger.toJson().getAsJsonArray("sessions").get(0).getAsJsonObject().get("order").deepCopy();
        var loaded = ResearchLedger.fromJson(ledger.toJson());
        var resumed = new ResearchSessions(loaded, new ResearchCatalogue(List.of(sand, quartz)), 32, new Random(999));
        assertEquals(id, resumed.beginDiscovery(context, DiscoveryTier.I, true, 600_000).orElseThrow().id());
        assertEquals(before, loaded.toJson().getAsJsonArray("sessions").get(0).getAsJsonObject().get("order"));
        assertEquals(1, resumed.session(context, id).orElseThrow().paidSteps());
        assertFalse(resumed.session(ResearchContext.personal(UUID.randomUUID()), id).isPresent());
    }
    @Test void analysisOfPlannedCandidateUsesNextUnknownWithoutAnotherRoll() {
        var ledger = enabled(); var engine = engine(ledger, sand, quartz); var pay = new Pay();
        var id = engine.beginDiscovery(context, DiscoveryTier.I, true, 600_000).orElseThrow().id();
        String planned = ledger.toJson().getAsJsonArray("sessions").get(0).getAsJsonObject().getAsJsonArray("order").get(0).getAsString();
        var first = planned.equals(sand.id()) ? sand : quartz; var other = first == sand ? quartz : sand;
        tick(engine, id, 0, pay); ledger.learn(context, first, LearningPath.ANALYSIS);
        for (int i = 1; i < 6_000; i++) tick(engine, id, i, pay);
        assertEquals(LearningPath.DISCOVERY, ledger.path(context, other.id())); assertEquals(16_000, pay.primal);
        assertEquals(ResearchSessions.State.COMPLETE, engine.session(context, id).orElseThrow().state());
    }
    @Test void exhaustedPoolStopsDebitsAndCreatesExactlyOneCapacityAwareRefund() {
        var ledger = enabled(); var engine = engine(ledger, sand); var pay = new Pay();
        var id = engine.beginDiscovery(context, DiscoveryTier.I, true, 600_000).orElseThrow().id(); tick(engine, id, 0, pay);
        ledger.learn(context, sand, LearningPath.ANALYSIS);
        assertEquals(ResearchSessions.Result.REFUND_DUE, tick(engine, id, 1, pay));
        assertEquals(ResearchSessions.Result.UNAVAILABLE, tick(engine, id, 2, pay)); assertEquals(1, pay.calls);
        var claims = engine.refundClaims(context); assertEquals(1, claims.size());
        assertEquals(0, engine.claimRefund(context, claims.getFirst(), 4_000, (op, amount, tx) -> 0));
        assertEquals(1_000, engine.claimRefund(context, claims.getFirst(), 1_000, (op, amount, tx) -> amount));
        assertEquals(3_000, engine.refundDue(context));
        var loaded = ResearchLedger.fromJson(ledger.toJson()); var resumed = engine(loaded, sand);
        assertEquals(3_000, resumed.claimRefund(context, claims.getFirst(), 9_000, (op, amount, tx) -> amount));
        assertEquals(0, resumed.claimRefund(context, claims.getFirst(), 9_000, (op, amount, tx) -> fail("Paid claim must not replay")));
        assertEquals(0, resumed.refundDue(context)); assertTrue(resumed.beginDiscovery(context, DiscoveryTier.I, true, 600_000).isEmpty());
    }
    @Test void missingOrChangedDefinitionsPauseWithoutRefundOrReroll() {
        var ledger = enabled(); var engine = engine(ledger, sand, quartz);
        var id = engine.beginDiscovery(context, DiscoveryTier.I, true, 600_000).orElseThrow().id(); tick(engine, id, 0, new Pay());
        var changed = engine(ledger, sand);
        assertEquals(ResearchSessions.Result.PAUSED, tick(changed, id, 1, (op, primal, energy, tx) -> fail("Must not debit changed definition")));
        assertEquals(ResearchSessions.State.DEFINITION_CHANGED, changed.session(context, id).orElseThrow().state());
        assertEquals(0, changed.refundDue(context)); assertEquals(id, changed.beginDiscovery(context, DiscoveryTier.I, true, 600_000).orElseThrow().id());
    }
    @Test void dimensionsAreOwnerProofAndTierCannotMixCandidates() {
        var ledger = enabled(); var end = family("end_stone", DiscoveryTier.III, "minecraft:the_end"); var engine = engine(ledger, sand, end);
        assertTrue(engine.beginDiscovery(context, DiscoveryTier.III, true, 600_000).isEmpty());
        ledger.visit(UUID.randomUUID(), "minecraft:the_end"); assertEquals(0, engine.available(context, DiscoveryTier.III));
        ledger.visit(OWNER, "minecraft:the_end");
        var view = engine.beginDiscovery(context, DiscoveryTier.III, true, 600_000).orElseThrow(); assertEquals(16, view.totalSteps()); assertEquals(1, view.candidates());
        var order = ledger.toJson().getAsJsonArray("sessions").get(0).getAsJsonObject().getAsJsonArray("order"); assertEquals(List.of(end.id()), order.asList().stream().map(e -> e.getAsString()).toList());
    }
    @Test void derivedFamilyDoesNotTeachUnknownConstituentsOrOtherEquivalentFamily() {
        var ledger = new ResearchLedger(); var engine = engine(ledger, sand, quartz);
        assertTrue(engine.beginDerive(context, sand.id(), true, 256_000, 1_800).isEmpty());
        ledger.learnSubstances(context, Set.of(SILICA));
        var id = engine.beginDerive(context, sand.id(), true, 256_000, 1_800).orElseThrow().id();
        for (int i = 0; i < 1_800; i++) engine.advance(context, id, i, false, true, 256_000, 1_800, (op, primal, energy, tx) -> true);
        assertTrue(ledger.knows(context, sand.id())); assertFalse(ledger.knows(context, quartz.id())); assertEquals(Set.of(SILICA), ledger.knownSubstances(context));
    }
    @Test void startingKnowledgeSuppliesDeriveConstituentsWithoutBecomingEarnedProgress() {
        var start = starting(sand); var ledger = new ResearchLedger(); var engine = engine(ledger, start, quartz);
        assertFalse(engine.canDerive(context, start)); assertTrue(engine.canDerive(context, quartz));
        assertTrue(engine.beginDerive(context, start.id(), true, 256_000, 1_800).isEmpty());
        assertEquals(1, engine.available(context, DiscoveryTier.I));
        assertTrue(engine.beginDiscovery(context, DiscoveryTier.I, true, 600_000).isEmpty());
        assertTrue(ledger.knownFamilies(context).isEmpty()); assertTrue(ledger.knownSubstances(context).isEmpty()); assertEquals(0, ledger.earnedFamilies(context));
        var id = engine.beginDerive(context, quartz.id(), true, 256_000, 1_800).orElseThrow().id();
        for (int i = 0; i < 1_800; i++) engine.advance(context, id, i, false, true, 256_000, 1_800, (op, primal, energy, tx) -> true);
        assertEquals(quartz.id(), engine.session(context, id).orElseThrow().resultFamily());
        assertEquals(Set.of(quartz.id()), ledger.knownFamilies(context)); assertEquals(1, ledger.earnedFamilies(context));
        assertFalse(engine.canDerive(context, quartz)); assertEquals(0, engine.available(context, DiscoveryTier.I));
    }
    @Test void discoveryNeverRollsStartingKnowledgeAndReplacesOnlyWithAnotherUnknownFamily() {
        var start = starting(sand); var glass = family("glass", DiscoveryTier.I, "");
        var ledger = enabled(); var engine = engine(ledger, start, quartz, glass); var pay = new Pay();
        assertEquals(2, engine.available(context, DiscoveryTier.I));
        var view = engine.beginDiscovery(context, DiscoveryTier.I, true, 600_000).orElseThrow(); assertEquals(2, view.candidates());
        var order = ledger.toJson().getAsJsonArray("sessions").get(0).getAsJsonObject().getAsJsonArray("order");
        assertEquals(Set.of(quartz.id(), glass.id()), order.asList().stream().map(e -> e.getAsString()).collect(java.util.stream.Collectors.toSet()));
        var planned = order.get(0).getAsString().equals(quartz.id()) ? quartz : glass; var replacement = planned == quartz ? glass : quartz;
        tick(engine, view.id(), 0, pay); ledger.learn(context, planned, LearningPath.ANALYSIS);
        assertEquals(1, engine.session(context, view.id()).orElseThrow().candidates());
        for (int i = 1; i < 6_000; i++) tick(engine, view.id(), i, pay);
        assertEquals(replacement.id(), engine.session(context, view.id()).orElseThrow().resultFamily());
        assertEquals(LearningPath.DISCOVERY, ledger.path(context, replacement.id())); assertFalse(ledger.knows(context, start.id()));
        assertEquals(2, ledger.earnedFamilies(context)); assertEquals(16_000, pay.primal);
        assertEquals(0, engine.available(context, DiscoveryTier.I)); assertTrue(engine.beginDiscovery(context, DiscoveryTier.I, true, 600_000).isEmpty());
        assertEquals(0, engine.refundDue(context));
    }
    private static MaterialResearch starting(MaterialResearch family) {
        return new MaterialResearch(family.id(), family.name(), family.items(), family.substances(), family.derive(), true,
                family.discoveryTier(), family.requiredDimension(), family.derivePrimal());
    }
    @Test void failedDebitOrDurableWriteRollsBackProgressAndKnowledge() {
        var ledger = enabled(); var engine = engine(ledger, sand); var id = engine.beginDiscovery(context, DiscoveryTier.I, true, 600_000).orElseThrow().id();
        var before = ledger.toJson(); assertEquals(ResearchSessions.Result.WAITING, tick(engine, id, 0, (op, primal, energy, tx) -> false)); assertEquals(before, ledger.toJson());
        ledger.authority(() -> { throw new IllegalStateException("Disk unavailable"); });
        assertThrows(IllegalStateException.class, () -> tick(engine, id, 0, (op, primal, energy, tx) -> {
            ledger.recordCellChange(new ResearchLedger.CellChange(UUID.randomUUID(), op, -primal, context.identity()), tx); ledger.recordEnergyChange(new ResearchLedger.EnergyChange(UUID.randomUUID(), op, OWNER, energy), tx); return true;
        }));
        assertEquals(before, ledger.toJson());
    }
    @Test void durablePaymentsMustHaveCellAuthorityRecord() {
        var ledger = enabled(); ledger.authority(() -> {}); var engine = engine(ledger, sand);
        var id = engine.beginDiscovery(context, DiscoveryTier.I, true, 600_000).orElseThrow().id();
        assertThrows(IllegalStateException.class, () -> tick(engine, id, 0, (op, primal, energy, tx) -> true));
        assertEquals(0, engine.session(context, id).orElseThrow().paidSteps());
    }
    @Test void authorityReceiptMustMatchExactPriceAndRefundAmount() {
        var ledger = enabled(); ledger.authority(() -> {}); var engine = engine(ledger, sand);
        var id = engine.beginDiscovery(context, DiscoveryTier.I, true, 600_000).orElseThrow().id(); var cell = UUID.randomUUID(); var machine = UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> tick(engine, id, 0, (op, primal, energy, tx) -> {
            ledger.recordCellChange(new ResearchLedger.CellChange(cell, op, -1, context.identity()), tx);
            ledger.recordEnergyChange(new ResearchLedger.EnergyChange(machine, op, OWNER, energy), tx); return true;
        }));
        assertEquals(0, engine.session(context, id).orElseThrow().paidSteps());
        tick(engine, id, 0, (op, primal, energy, tx) -> {
            ledger.recordCellChange(new ResearchLedger.CellChange(cell, op, -primal, context.identity()), tx);
            ledger.recordEnergyChange(new ResearchLedger.EnergyChange(machine, op, OWNER, energy), tx); return true;
        });
        ledger.learn(context, sand, LearningPath.ANALYSIS); tick(engine, id, 1, (op, primal, energy, tx) -> fail("Exhausted pool must not pay again"));
        var claim = engine.refundClaims(context).getFirst();
        assertThrows(IllegalStateException.class, () -> engine.claimRefund(context, claim, 4_000, (op, amount, tx) -> {
            ledger.recordCellChange(new ResearchLedger.CellChange(cell, op, 1, context.identity()), tx); return amount;
        }));
        assertEquals(4_000, engine.refundDue(context));
    }
    @Test void changedIdentityCannotAdvanceOldPaidSession() {
        var ledger = enabled(); var team = ResearchIdentity.team(UUID.randomUUID());
        var joined = new ResearchContext(OWNER, team, List.of(ResearchIdentity.player(OWNER), team)); var engine = engine(ledger, sand);
        var id = engine.beginDiscovery(joined, DiscoveryTier.I, true, 600_000).orElseThrow().id();
        assertEquals(ResearchSessions.Result.UNAVAILABLE, tick(engine, id, 0, (op, primal, energy, tx) -> fail("Former team session is inaccessible")));
        assertTrue(engine.sessions(context).isEmpty()); assertTrue(engine.session(joined, id).isPresent());
    }
}
