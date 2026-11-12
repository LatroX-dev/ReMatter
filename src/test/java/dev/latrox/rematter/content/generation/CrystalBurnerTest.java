package dev.latrox.rematter.content.generation;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class CrystalBurnerTest {
    private static final CrystalFuelData.Fuel FUEL = new CrystalFuelData.Fuel("rematter:matrix_crystal", 1, 2048);
    @Test void aCrystalIsConsumedOnceAndItsWholeEnergySurvivesBlockedOutputAndReload() {
        var burner = new CrystalBurner();
        var full = burner.tick(0, 64, FUEL, 64);
        assertEquals(0, full.consumedItems()); assertEquals(0, full.producedEnergy());
        assertTrue(burner.apply(full)); assertEquals(0, burner.remaining());
        var start = burner.tick(2400000, 64, FUEL, 64);
        assertEquals(1, start.consumedItems()); assertEquals(64, start.producedEnergy());
        assertEquals(0, burner.remaining(), "A plan must never consume a resource");
        assertTrue(burner.apply(start)); assertEquals(1984, burner.remaining());
        var saved = burner.save(); var reloaded = new CrystalBurner(); reloaded.load(saved);
        assertEquals(saved, reloaded.save());
        var pause = reloaded.tick(0, 256, FUEL, 64); assertEquals(0, pause.consumedItems()); assertTrue(reloaded.apply(pause));
        long total = 64;
        while (reloaded.remaining() > 0) {
            var step = reloaded.tick(29, 256, null, 0);
            assertEquals(0, step.consumedItems()); total += step.producedEnergy(); assertTrue(reloaded.apply(step));
        }
        assertEquals(2048, total); assertEquals(0, reloaded.remaining());
    }
    @Test void industrialRateChangesTimeButNeverYieldOrItemConsumption() {
        long[] output = new long[2]; int[] ticks = new int[2];
        for (int i = 0; i < 2; i++) {
            var burner = new CrystalBurner(); int items = 1;
            do {
                var plan = burner.tick(Long.MAX_VALUE, i == 0 ? 64 : 256, FUEL, items);
                items -= plan.consumedItems(); output[i] += plan.producedEnergy(); ticks[i]++; assertTrue(burner.apply(plan));
            } while (burner.remaining() > 0);
            assertEquals(0, items);
        }
        assertArrayEquals(new long[]{2048, 2048}, output); assertArrayEquals(new int[]{32, 8}, ticks);
    }
    @Test void stalePlansAndFuelReloadCannotPayTheSameCrystalAgain() {
        var burner = new CrystalBurner(); var first = burner.tick(64000, 64, FUEL, 1);
        assertTrue(burner.apply(first)); assertFalse(burner.apply(first));
        var changedFuel = new CrystalFuelData.Fuel("rematter:matrix_crystal", 1, 1_000_000);
        var next = burner.tick(64000, 256, changedFuel, 64);
        assertEquals(0, next.consumedItems()); assertEquals(1728, next.nextRemaining());
        assertTrue(burner.apply(next));
    }
    @Test void unknownAndMalformedSavedEnergyStayLockedAndExactlyPreserved() {
        var tag = new CompoundTag(); tag.putInt("schema", 7); tag.putString("future", "preserve"); tag.putLong("remaining", Long.MAX_VALUE);
        var burner = new CrystalBurner(); burner.load(tag);
        assertFalse(burner.readable()); assertEquals(tag, burner.save());
        var step = burner.tick(Long.MAX_VALUE, 256, FUEL, 64);
        assertEquals(0, step.producedEnergy()); assertEquals(0, step.consumedItems()); assertFalse(burner.apply(step));
        tag.putInt("schema", 1); tag.remove("future"); tag.putLong("remaining", -1); burner.load(tag);
        assertFalse(burner.readable()); assertEquals(tag, burner.save());
        tag.putLong("remaining", 2048); burner.load(tag); assertTrue(burner.readable()); assertEquals(2048, burner.remaining());
    }
}
