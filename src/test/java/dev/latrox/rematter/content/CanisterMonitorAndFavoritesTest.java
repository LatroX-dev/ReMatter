package dev.latrox.rematter.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.latrox.rematter.chemistry.BuiltinChemistry;
import dev.latrox.rematter.chemistry.MatterInventory;
import dev.latrox.rematter.chemistry.MatterNetworkInventory;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.ReservedSubstances;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.content.MachineSnapshot.StockKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class CanisterMonitorAndFavoritesTest {
    @Test
    void stockFavoritesRoundTripImportLimitAndPreserveUnknownIdsAndFutureSchemas() {
        var saved = new StockFavoritesSavedData();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        StockKey copper = new StockKey("rematter:copper", "solid");
        StockKey unknownModGas = new StockKey("futuremod:xenon_plasma", "gas");

        assertTrue(saved.toggle(alice, copper));
        assertTrue(saved.toggle(alice, unknownModGas));
        assertEquals(Set.of(copper, unknownModGas), saved.of(alice));

        // Toggle again removes copper while keeping unknownModGas.
        assertTrue(saved.toggle(alice, copper));
        assertEquals(Set.of(unknownModGas), saved.of(alice));

        // One-time import populates Bob when empty, then refuses to overwrite existing server pins.
        assertTrue(saved.importIfEmpty(bob, List.of(copper)));
        assertFalse(saved.importIfEmpty(bob, List.of(unknownModGas)));
        assertEquals(Set.of(copper), saved.of(bob));

        // Enforce MAX_PER_PLAYER bound.
        var many = new ArrayList<StockKey>();
        for (int i = 0; i < StockFavoritesSavedData.MAX_PER_PLAYER + 8; i++) {
            many.add(new StockKey("rematter:sub_" + i, "solid"));
        }
        UUID carol = UUID.randomUUID();
        assertTrue(saved.importIfEmpty(carol, many));
        assertEquals(StockFavoritesSavedData.MAX_PER_PLAYER, saved.of(carol).size());
        assertFalse(saved.toggle(carol, new StockKey("rematter:overflow", "gas")));

        // Round-trip via NBT preserves both players and unknown SubstanceIds.
        CompoundTag tag = saved.toTag();
        var restored = StockFavoritesSavedData.fromTag(tag);
        assertEquals(Set.of(unknownModGas), restored.of(alice));
        assertEquals(Set.of(copper), restored.of(bob));
        assertEquals(StockFavoritesSavedData.MAX_PER_PLAYER, restored.of(carol).size());

        // Unknown future schema is preserved verbatim and locked against destructive edits.
        var futureTag = new CompoundTag();
        futureTag.putInt("schema", 99);
        futureTag.putString("future_payload", "keep_me");
        var futureSaved = StockFavoritesSavedData.fromTag(futureTag);
        assertFalse(futureSaved.toggle(alice, copper));
        assertEquals(futureTag, futureSaved.toTag());
    }

    @Test
    void pressurizedCanisterDataValidatesPhasesRoundTripsAndFailsClosedOnTamperedTags() {
        assertTrue(PressurizedCanisterData.validMatter(BuiltinChemistry.HYDROGEN));
        assertTrue(PressurizedCanisterData.validMatter(BuiltinChemistry.WATER));
        assertFalse(PressurizedCanisterData.validMatter(BuiltinChemistry.COPPER));
        assertFalse(PressurizedCanisterData.validMatter(new SubstanceKey(ReservedSubstances.PRIMAL_MATTER, Phase.GAS)));

        SubstanceKey unknownGas = new SubstanceKey(SubstanceId.parse("futuremod:argon"), Phase.GAS);
        var matterData = PressurizedCanisterData.ofMatter(unknownGas, 12_500);
        var decodedMatter = assertInstanceOf(PressurizedCanisterData.State.Matter.class, matterData.decode());
        assertEquals(unknownGas, decodedMatter.key());
        assertEquals(12_500, decodedMatter.amount());

        Identifier lavaId = Identifier.withDefaultNamespace("lava");
        var fluidData = PressurizedCanisterData.ofFluid(lavaId, 4_000);
        var decodedFluid = assertInstanceOf(PressurizedCanisterData.State.Fluid.class, fluidData.decode());
        assertEquals(lavaId, decodedFluid.fluidId());
        assertEquals(4_000, decodedFluid.amount());

        assertThrows(IllegalArgumentException.class,
                () -> PressurizedCanisterData.ofMatter(BuiltinChemistry.COPPER, 1_000));
        assertThrows(IllegalArgumentException.class,
                () -> PressurizedCanisterData.ofMatter(BuiltinChemistry.HYDROGEN, PressurizedCanisterData.CAPACITY + 1));
        assertThrows(IllegalArgumentException.class,
                () -> PressurizedCanisterData.ofFluid(Identifier.withDefaultNamespace("empty"), 1_000));

        // Extra unknown tag keys or future schema decode as State.Locked while preserving raw tag untouched.
        CompoundTag extraKeyTag = matterData.copyTag();
        extraKeyTag.putString("unexpected", "extension");
        var lockedExtra = new PressurizedCanisterData(extraKeyTag);
        assertInstanceOf(PressurizedCanisterData.State.Locked.class, lockedExtra.decode());
        assertEquals(extraKeyTag, lockedExtra.copyTag());

        CompoundTag futureSchema = fluidData.copyTag();
        futureSchema.putInt("schema", 2);
        assertInstanceOf(PressurizedCanisterData.State.Locked.class, new PressurizedCanisterData(futureSchema).decode());
    }

    @Test
    void configurationCardCellFilterRoundTripsUnrestrictedAndRestrictedSpecs() {
        var unrestrictedCard = ConfigurationCardData.fromCellFilter(CellPartitionData.ANY);
        assertEquals("cell_filter", unrestrictedCard.kind());
        var unrestricted = unrestrictedCard.cellFilterSetup().orElseThrow();
        assertFalse(unrestricted.spec().restricted());
        assertTrue(unrestricted.spec().allowed().isEmpty());

        var allowed = Set.of(BuiltinChemistry.COPPER, BuiltinChemistry.HYDROGEN);
        var restrictedCard = ConfigurationCardData.fromCellFilter(new CellPartitionData.Spec(true, allowed));
        var restricted = restrictedCard.cellFilterSetup().orElseThrow();
        assertTrue(restricted.spec().restricted());
        assertEquals(allowed, restricted.spec().allowed());
    }

    @Test
    void networkRateTrackerComputesPerMinuteDeltasWithoutCellSwapSpikes() {
        var cellA = new MatterInventory(64_000);
        assertTrue(cellA.apply(Map.of(), Map.of(BuiltinChemistry.HYDROGEN, 1_000L, BuiltinChemistry.COPPER, 5_000L)));
        var net1 = new MatterNetworkInventory(List.of(cellA));

        var tracker = new NetworkRateTracker();
        assertTrue(tracker.sampleRatesPerMinute(net1, 100L).isEmpty());

        // +600 HYDROGEN and -300 COPPER over 60 ticks (3 seconds = 1/20 minute) -> +12,000 u/min and -6,000 u/min.
        assertTrue(net1.apply(Map.of(BuiltinChemistry.COPPER, 300L), Map.of(BuiltinChemistry.HYDROGEN, 600L)));
        Map<SubstanceKey, Long> rates = tracker.sampleRatesPerMinute(net1, 160L);
        assertEquals(12_000L, rates.get(BuiltinChemistry.HYDROGEN));
        assertEquals(-6_000L, rates.get(BuiltinChemistry.COPPER));

        // Simulate inserting a second pre-filled Matter Cell (+50,000 OXYGEN): member identity set changes,
        // so tracker resets its baseline and reports 0 spurious rate spike on the swap tick!
        var cellB = new MatterInventory(64_000);
        assertTrue(cellB.apply(Map.of(), Map.of(BuiltinChemistry.OXYGEN, 50_000L)));
        var net2 = new MatterNetworkInventory(List.of(cellA, cellB));
        assertTrue(tracker.sampleRatesPerMinute(net2, 180L).isEmpty(),
                "Mounting a pre-filled Matter Cell must not cause a fake production rate spike");

        // Subsequent real consumption on the 2-cell network is tracked accurately (-100 OXYGEN over 40 ticks -> -3,000 u/min).
        assertTrue(net2.apply(Map.of(BuiltinChemistry.OXYGEN, 100L), Map.of()));
        Map<SubstanceKey, Long> afterSwapRates = tracker.sampleRatesPerMinute(net2, 220L);
        assertEquals(-3_000L, afterSwapRates.get(BuiltinChemistry.OXYGEN));
    }
}
