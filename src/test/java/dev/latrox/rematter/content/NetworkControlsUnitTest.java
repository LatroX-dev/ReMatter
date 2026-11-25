package dev.latrox.rematter.content;

import static org.junit.jupiter.api.Assertions.*;

import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.ReservedSubstances;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.content.EnergyReactorRecipes.CoolantMode;
import dev.latrox.rematter.content.EnergyReactorRecipes.MatterFuelFilter;
import dev.latrox.rematter.content.MachineSnapshot.StockKey;
import dev.latrox.rematter.content.MatterCableRelayBlockEntity.ChannelMode;
import dev.latrox.rematter.content.MatterCableRelayBlockEntity.DirectionMode;
import dev.latrox.rematter.content.MatterCableRelayBlockEntity.FilterRule;
import dev.latrox.rematter.content.MatterCableRelayBlockEntity.GateMode;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class NetworkControlsUnitTest {
    @Test void energyReactorFiltersAndCoolantModesMatchExpectedRecipes() {
        assertEquals(MatterFuelFilter.AUTO, MatterFuelFilter.load("unknown"));
        assertEquals(MatterFuelFilter.URANIUM, MatterFuelFilter.load("uranium"));
        assertEquals(CoolantMode.AUTO, CoolantMode.load("unknown"));
        assertEquals(CoolantMode.OFF, CoolantMode.load("off"));

        for (var fuel : EnergyReactorRecipes.MATTER_FUELS) {
            assertTrue(MatterFuelFilter.AUTO.accepts(fuel.id()));
            assertEquals("uranium".equals(fuel.id()), MatterFuelFilter.URANIUM.accepts(fuel.id()));
            assertEquals("void_condensate".equals(fuel.id()), MatterFuelFilter.VOID_CONDENSATE.accepts(fuel.id()));
            assertEquals("lithium_fusion".equals(fuel.id()), MatterFuelFilter.LITHIUM_FUSION.accepts(fuel.id()));
        }

        assertTrue(CoolantMode.AUTO.allowsWater() && CoolantMode.AUTO.allowsNitrogen());
        assertFalse(CoolantMode.OFF.allowsWater() || CoolantMode.OFF.allowsNitrogen());
        assertTrue(CoolantMode.WATER.allowsWater() && !CoolantMode.WATER.allowsNitrogen());
        assertTrue(!CoolantMode.NITROGEN.allowsWater() && CoolantMode.NITROGEN.allowsNitrogen());
    }

    @Test void valveModesCycleAndLoadCleanly() {
        assertEquals(GateMode.ALWAYS_OPEN, GateMode.load("unknown"));
        assertEquals(GateMode.REDSTONE_HIGH, GateMode.load("redstone_high"));
        assertEquals(GateMode.REDSTONE_LOW, GateMode.load("redstone_low"));
        assertEquals(ChannelMode.BOTH, ChannelMode.load("unknown"));
        assertEquals(ChannelMode.POWER_ONLY, ChannelMode.load("power_only"));
        assertEquals(ChannelMode.MATTER_ONLY, ChannelMode.load("matter_only"));

        assertEquals(GateMode.ALWAYS_OPEN, GateMode.ALWAYS_OPEN.next().next().next());
        assertEquals(ChannelMode.BOTH, ChannelMode.BOTH.next().next().next());
        assertEquals(DirectionMode.BIDIRECTIONAL, DirectionMode.load("unknown"));
        assertEquals(DirectionMode.ONE_WAY, DirectionMode.load("one_way"));
        assertEquals(DirectionMode.BIDIRECTIONAL, DirectionMode.BIDIRECTIONAL.next().next());
    }

    @Test void relayFilterRuleEvaluatesStatesWhitelistBlacklistAndSanitizes() {
        var iron = SubstanceKey.of("rematter:iron", Phase.SOLID);
        var water = SubstanceKey.of("rematter:water", Phase.LIQUID);
        var h2 = SubstanceKey.of("rematter:hydrogen", Phase.GAS);
        var primal = new SubstanceKey(ReservedSubstances.PRIMAL_MATTER, Phase.SOLID);

        assertTrue(FilterRule.OPEN.isUnrestricted());
        assertTrue(FilterRule.OPEN.allowsOrdinary(iron) && FilterRule.OPEN.allowsOrdinary(water)
                && FilterRule.OPEN.allowsOrdinary(h2) && FilterRule.OPEN.allowsPrimal());

        var liquidOnly = new FilterRule(MatterCableRelayBlockEntity.STATE_LIQUID, false, Set.of());
        assertFalse(liquidOnly.allowsOrdinary(iron));
        assertTrue(liquidOnly.allowsOrdinary(water));
        assertFalse(liquidOnly.allowsOrdinary(h2));
        assertFalse(liquidOnly.allowsPrimal());

        var emptyWhitelist = new FilterRule(MatterCableRelayBlockEntity.STATE_ALL, true, Set.of());
        assertFalse(emptyWhitelist.allowsOrdinary(iron));
        assertTrue(emptyWhitelist.allowsPrimal());

        var ironWhitelist = new FilterRule(MatterCableRelayBlockEntity.STATE_ALL, true, Set.of(iron));
        assertTrue(ironWhitelist.allowsOrdinary(iron));
        assertFalse(ironWhitelist.allowsOrdinary(water));

        var ironBlacklist = new FilterRule(MatterCableRelayBlockEntity.STATE_ALL, false, Set.of(iron));
        assertFalse(ironBlacklist.allowsOrdinary(iron));
        assertTrue(ironBlacklist.allowsOrdinary(water));

        var sanitized = FilterRule.sanitize(List.of(iron, primal, iron, water));
        assertEquals(List.of(iron, water), sanitized);
    }

    @Test void networkReservesSavedDataRoundTripsAndPreservesFutureSchema() {
        UUID owner = UUID.randomUUID();
        var data = new NetworkReservesSavedData();
        var uranium = new StockKey("rematter:uranium", "solid");
        var primal = new StockKey(ReservedSubstances.PRIMAL_MATTER.toString(), "solid");

        assertTrue(data.set(owner, uranium, 25_000L));
        assertTrue(data.set(owner, primal, 100_000L));
        assertEquals(25_000L, data.amount(owner, SubstanceKey.of("rematter:uranium", Phase.SOLID)));
        assertEquals(100_000L, data.amount(owner, new SubstanceKey(ReservedSubstances.PRIMAL_MATTER, Phase.SOLID)));

        // Round-trip through NBT
        CompoundTag tag = data.toTag();
        var loaded = NetworkReservesSavedData.fromTag(tag);
        assertEquals(25_000L, loaded.amount(owner, SubstanceKey.of("rematter:uranium", Phase.SOLID)));
        assertEquals(100_000L, loaded.amount(owner, new SubstanceKey(ReservedSubstances.PRIMAL_MATTER, Phase.SOLID)));

        // Setting 0 removes the entry cleanly
        assertTrue(loaded.set(owner, uranium, 0L));
        assertEquals(0L, loaded.amount(owner, SubstanceKey.of("rematter:uranium", Phase.SOLID)));
        assertEquals(1, loaded.of(owner).size());

        // Future schema is preserved unmodified and refuses writes
        CompoundTag future = new CompoundTag();
        future.putInt("schema", 99);
        future.putString("future_blob", "preserve-me");
        var locked = NetworkReservesSavedData.fromTag(future);
        assertFalse(locked.set(owner, uranium, 500L));
        assertEquals(future, locked.toTag());
    }
}
