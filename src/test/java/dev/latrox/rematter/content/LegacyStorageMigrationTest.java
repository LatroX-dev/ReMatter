package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static dev.latrox.rematter.chemistry.BuiltinChemistry.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyStorageMigrationTest {
    @Test void exactMixedContentsMoveOnceAndOnlyTheCellOwnsThemAfterPersistence() {
        var source = new MatterInventory(1_000_000);
        var unknown = SubstanceKey.of("missing_mod:retained", Phase.GAS);
        source.insert(COPPER, 1000); source.insert(WATER, 33); source.insert(unknown, 17);
        var expected = source.contents(); var cell = new MatterInventory(64_000, 512);
        var before = source.snapshot();
        assertTrue(LegacyStorageMigration.canMigrate(source, cell));
        assertEquals(before, source.snapshot()); assertEquals(0, cell.total());
        assertTrue(LegacyStorageMigration.migrate(source, cell));
        assertEquals(0, source.total()); assertEquals(expected, cell.contents());
        assertFalse(LegacyStorageMigration.migrate(source, cell));
        assertEquals(expected, MatterCellData.from(cell).open(64_000).orElseThrow().contents());
        assertEquals(0, MatterInventory.restore(source.snapshot()).total());
    }
    @Test void insufficientCapacityOrTypesNeverPartiallyMovesLegacyResources() {
        var source = new MatterInventory(1_000_000); source.insert(COPPER, 70_000); source.insert(WATER, 1);
        assertRejected(source, new MatterInventory(64_000, 512), "migration_disk_capacity");
        assertRejected(source, new MatterInventory(256_000, 1), "migration_disk_types");
        var manyTypes = new MatterInventory(1_000_000);
        for (int i=0;i<513;i++) manyTypes.insert(SubstanceKey.of("test:retained_"+i,Phase.SOLID),1);
        assertRejected(manyTypes,new MatterInventory(1_000_000,512),"migration_disk_types");
        assertEquals(513,manyTypes.typeCount());
    }
    @Test void nonemptyOrReservedDestinationsAndReservedLegacyRemainUnchanged() {
        var source = new MatterInventory(1000); source.insert(COPPER, 1000);
        var nonempty = new MatterInventory(64_000, 512); nonempty.insert(WATER, 1);
        assertRejected(source, nonempty, "migration_disk_not_empty");
        var reserved = new MatterInventory(64_000, 512); reserved.reserve(Map.of(),Map.of(WATER,1L)).orElseThrow();
        assertRejected(source, reserved, "migration_busy");
        source.reserve(Map.of(COPPER,1L), Map.of()).orElseThrow();
        assertRejected(source, new MatterInventory(64_000,512), "migration_busy");
    }
    @Test void maximumExactLongAndEmptyLegacyCanMoveWithoutOverflowOrInventingCapacity() {
        var source = new MatterInventory(Long.MAX_VALUE); source.insert(COPPER, Long.MAX_VALUE);
        var cell = new MatterInventory(Long.MAX_VALUE,512);
        assertTrue(LegacyStorageMigration.migrate(source,cell));
        assertEquals(Long.MAX_VALUE,cell.amount(COPPER)); assertEquals(0,source.total());
        assertFalse(source.transferAllTo(cell));
        var empty = new MatterInventory(64_000,512);
        assertTrue(LegacyStorageMigration.migrate(source,empty));
        assertEquals(64_000,empty.capacity()); assertEquals(0,empty.total());
        assertFalse(empty.transferAllTo(empty));
    }
    private static void assertRejected(MatterInventory source,MatterInventory destination,String reason) {
        var a=source.snapshot(); var b=destination.snapshot();
        assertEquals(reason,LegacyStorageMigration.readiness(source,destination));
        assertFalse(LegacyStorageMigration.migrate(source,destination));
        assertEquals(a,source.snapshot()); assertEquals(b,destination.snapshot());
    }
}
