package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MachineReadinessTest {
    @Test void fullTypeTableIsDistinctFromRemainingQuantityCapacity() {
        var a=new dev.latrox.rematter.chemistry.SubstanceKey(dev.latrox.rematter.chemistry.SubstanceId.parse("test:a"),dev.latrox.rematter.chemistry.Phase.SOLID);
        var b=new dev.latrox.rematter.chemistry.SubstanceKey(dev.latrox.rematter.chemistry.SubstanceId.parse("test:b"),dev.latrox.rematter.chemistry.Phase.GAS);
        var store=new dev.latrox.rematter.chemistry.MatterInventory(64000,1);store.insert(a,1);
        assertEquals("storage_types_full",MachineReadiness.check(true,store,java.util.Map.of(),java.util.Map.of(b,1L),true,100,10));
        assertEquals(63999,store.freeSpace());
    }
    private static final SubstanceKey WATER = new SubstanceKey(SubstanceId.parse("test:water"), Phase.LIQUID);
    private static final SubstanceKey GAS = new SubstanceKey(SubstanceId.parse("test:gas"), Phase.GAS);
    private static final Map<SubstanceKey, Long> INPUT = Map.of(WATER, 1000L);
    private static final Map<SubstanceKey, Long> OUTPUT = Map.of(GAS, 1500L);

    @Test void disconnectedProductionPrecedesEveryResourceReason() {
        assertEquals("no_storage", MachineReadiness.check(true, null, INPUT, OUTPUT, false, 0, 100));
    }
    @Test void standaloneInspectionNeedsEnergyButNoMatterNetwork() {
        assertEquals("insufficient_energy", MachineReadiness.check(false, null, Map.of(), Map.of(), true, 499, 500));
        assertEquals("ready", MachineReadiness.check(false, null, Map.of(), Map.of(), true, 500, 500));
    }
    @Test void missingInputsPrecedeCapacityOutputAndEnergyWithoutMutation() {
        var store = new MatterInventory(1000);
        store.insert(WATER, 999);
        var before = store.snapshot();
        assertEquals("missing_matter", MachineReadiness.check(true, store, INPUT, OUTPUT, false, 0, 100));
        assertEquals(before, store.snapshot());
    }
    @Test void reservationIsUnavailableRatherThanMisreportedAsFullStorage() {
        var store = new MatterInventory(3000);
        store.insert(WATER, 1000);
        store.reserve(INPUT, OUTPUT).orElseThrow();
        var before = store.snapshot();
        assertEquals("missing_matter", MachineReadiness.check(true, store, INPUT, OUTPUT, true, 100, 100));
        assertEquals(before, store.snapshot());
    }
    @Test void capacityUsesNetExpansionAndIncludesReservedSpace() {
        var store = new MatterInventory(1500);
        store.insert(WATER, 1000);
        assertEquals("ready", MachineReadiness.check(true, store, INPUT, OUTPUT, true, 100, 100));
        store.reserve(Map.of(), Map.of(GAS, 1L)).orElseThrow();
        assertEquals("storage_full", MachineReadiness.check(true, store, INPUT, OUTPUT, true, 100, 100));
    }
    @Test void fullOutputPrecedesEnergyAndEachFixRevealsTheNextReason() {
        var store = new MatterInventory(1000);
        store.insert(WATER, 1000);
        assertEquals("storage_full", MachineReadiness.check(true, store, INPUT, OUTPUT, false, 0, 100));
        store.setCapacity(1500);
        assertEquals("output_blocked", MachineReadiness.check(true, store, INPUT, OUTPUT, false, 0, 100));
        assertEquals("insufficient_energy", MachineReadiness.check(true, store, INPUT, OUTPUT, true, 99, 100));
        assertEquals("ready", MachineReadiness.check(true, store, INPUT, OUTPUT, true, 100, 100));
        assertEquals(1000, store.amount(WATER));
        assertEquals(0, store.amount(GAS));
    }
    @Test void distributedInputsAndOutputCapacityAreCheckedWithoutMovingAnyUnits() {
        var a = new MatterInventory(1000,2); a.insert(WATER,400);
        var b = new MatterInventory(1000,2); b.insert(WATER,600);
        var aBefore = a.snapshot(); var bBefore = b.snapshot();
        var network = new MatterNetworkInventory(java.util.List.of(a,b));
        assertEquals("ready",MachineReadiness.check(true,network,INPUT,OUTPUT,true,100,100));
        assertEquals(aBefore,a.snapshot()); assertEquals(bBefore,b.snapshot());
        assertEquals("missing_matter",MachineReadiness.check(true,new MatterNetworkInventory(java.util.List.of(a)),INPUT,OUTPUT,true,100,100));
        assertEquals(aBefore,a.snapshot()); assertEquals(bBefore,b.snapshot());
    }
    @Test void repeatedChecksReuseTheStorageVerdictUntilTheStoreChanges() {
        var counted = new CountingStore(new MatterInventory(1000));
        counted.store.insert(WATER, 1000);
        var readiness = new MachineReadiness();
        assertEquals("storage_full", readiness.checkRepeated(true, counted, INPUT, OUTPUT, true, 100, 100));
        assertEquals("storage_full", readiness.checkRepeated(true, counted, INPUT, OUTPUT, true, 100, 100));
        assertEquals(1, counted.plans);
        counted.store.setCapacity(1500);
        assertEquals("ready", readiness.checkRepeated(true, counted, INPUT, OUTPUT, true, 100, 100));
        assertEquals(2, counted.plans);
        assertEquals("insufficient_energy", readiness.checkRepeated(true, counted, INPUT, OUTPUT, true, 99, 100));
        assertEquals("output_blocked", readiness.checkRepeated(true, counted, INPUT, OUTPUT, false, 100, 100));
        assertEquals(2, counted.plans);
        assertEquals("ready", readiness.checkRepeated(true, counted, INPUT, Map.of(GAS, 1000L), true, 100, 100));
        assertEquals(3, counted.plans);
        assertEquals("no_storage", readiness.checkRepeated(true, null, INPUT, OUTPUT, true, 100, 100));
    }
    /** Counts full plans, so the test sees whether a verdict was reused. */
    private static final class CountingStore implements MatterStore {
        private final MatterInventory store;
        private int plans;
        CountingStore(MatterInventory store) { this.store = store; }
        @Override public long capacity() { return store.capacity(); }
        @Override public long total() { return store.total(); }
        @Override public long freeSpace() { return store.freeSpace(); }
        @Override public int typeCount() { return store.typeCount(); }
        @Override public long amount(SubstanceKey key) { return store.amount(key); }
        @Override public long available(SubstanceKey key) { return store.available(key); }
        @Override public long reserved(SubstanceKey key) { return store.reserved(key); }
        @Override public Map<SubstanceKey, Long> contents() { return store.contents(); }
        @Override public Map<SubstanceKey, Long> availableContents() { return store.availableContents(); }
        @Override public boolean hasTypeSpace(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs) { return store.hasTypeSpace(inputs, outputs); }
        @Override public boolean canApply(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs) { plans++; return store.canApply(inputs, outputs); }
        @Override public boolean apply(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs) { return store.apply(inputs, outputs); }
        @Override public long revision() { return store.revision(); }
        @Override public java.util.List<MatterInventory> members() { return store.members(); }
    }
}
