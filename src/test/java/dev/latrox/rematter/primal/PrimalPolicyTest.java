package dev.latrox.rematter.primal;

import static org.junit.jupiter.api.Assertions.*;
import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.content.MatterCellData;
import dev.latrox.rematter.data.ChemistryData;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PrimalPolicyTest {
    private static final SubstanceKey PRIMAL = new SubstanceKey(ReservedSubstances.PRIMAL_MATTER, Phase.SOLID);
    private static ChemistryRegistry catalogue() {
        var b = ChemistryRegistry.builder();
        BuiltinChemistry.create().substances().forEach(b::addSubstance);
        b.addProfile(new MaterialProfile("minecraft:iron_ingot", Map.of(BuiltinChemistry.IRON, 1000L), 1000, 1000, 40));
        b.allowSynthesis("minecraft:iron_ingot", SynthesisCategory.RAW_MATERIAL);
        return b.build();
    }
    @Test void ordinaryInventoriesNeverInsertSpendOrTransferPrimalButRestorePreservesIt() {
        var inventory = new MatterInventory(64000);
        assertEquals(0, inventory.insert(PRIMAL, 1000)); assertFalse(inventory.apply(Map.of(), Map.of(PRIMAL, 1000L)));
        var saved = new MatterInventory.Snapshot(64000, List.of(new SubstanceStack(PRIMAL, 1000)));
        var restored = MatterInventory.restore(saved, 512);
        assertEquals(saved, restored.snapshot()); assertEquals(0, restored.extract(PRIMAL, 1000));
        assertFalse(restored.apply(Map.of(PRIMAL, 1000L), Map.of()));
        assertEquals(0, restored.transferTo(inventory, PRIMAL, 1000)); assertEquals(saved, restored.snapshot());
        var component = MatterCellData.from(restored); assertTrue(component.open(64000).isEmpty());
        assertEquals(component.copyTag(), new MatterCellData(component.copyTag()).copyTag());
    }
    @Test void ordinaryProfilesAndDataOverridesCannotTurnTheReservedResourceIntoChemistry() {
        assertThrows(IllegalArgumentException.class, () -> ChemistryData.requireUnreservedSubstance(PRIMAL.id()));
        var b = ChemistryRegistry.builder(); BuiltinChemistry.create().substances().forEach(b::addSubstance);
        b.addProfile(new MaterialProfile("test:ordinary", Map.of(PRIMAL, 1000L), 1, 1, 1));
        assertThrows(IllegalArgumentException.class, b::build);
        var components = ChemistryRegistry.builder(); components.addSubstance(ReservedSubstances.primalDefinition());
        var id = SubstanceId.parse("test:fake_compound");
        components.addSubstance(new SubstanceDefinition(id, "test.name", "", SubstanceKind.COMPOUND, new Composition(Map.of(PRIMAL.id(), 1L)), Set.of(Phase.SOLID)));
        assertThrows(IllegalArgumentException.class, components::build);
    }
    @Test void converterNeedsKnownUsefulExplicitSolidElementAndKeepsExactSplitPrices() {
        var registry = catalogue();
        assertFalse(PrimalPricing.eligible(registry, BuiltinChemistry.IRON, id -> false));
        assertFalse(PrimalPricing.eligible(registry, BuiltinChemistry.WATER, id -> true));
        assertFalse(PrimalPricing.eligible(registry, PRIMAL, id -> true));
        assertFalse(PrimalPricing.eligible(registry, BuiltinChemistry.CALCIUM, id -> true));
        assertFalse(PrimalPricing.eligible(registry, BuiltinChemistry.GOLD, id -> true), "An allowlist entry without an actual use stays hidden");
        var whole = PrimalPricing.quote(registry, BuiltinChemistry.IRON, 1000, id -> true);
        var quarter = PrimalPricing.quote(registry, BuiltinChemistry.IRON, 250, id -> true);
        assertEquals(1000, whole.primal()); assertEquals(64000, whole.energy());
        assertEquals(whole.primal(), quarter.primal() * 4); assertEquals(whole.energy(), quarter.energy() * 4);
        assertThrows(IllegalArgumentException.class, () -> PrimalPricing.quote(registry, BuiltinChemistry.IRON, 249, id -> true));
        assertEquals(Long.MAX_VALUE, PrimalPricing.ceilScale(Long.MAX_VALUE, 1000, 1000));
        assertEquals(9_214_148_664_817_921_032L, PrimalPricing.ceilScale(Long.MAX_VALUE, 999, 1000));
        assertThrows(ArithmeticException.class, () -> PrimalPricing.ceilScale(Long.MAX_VALUE, 1001, 1000));
    }
    @Test void explicitDimensionsAndCentralIslandExclusionDoNotDependOnBiomeNames() {
        assertTrue(PrimalPlacementPolicy.permits("minecraft:the_end", "minecraft:the_end", 1040, 1040, 0));
        assertFalse(PrimalPlacementPolicy.permits("minecraft:the_end", "minecraft:the_end", 1040, 0, 0));
        assertFalse(PrimalPlacementPolicy.permits("minecraft:the_end", "minecraft:the_end", 1040, 1024, 0));
        assertFalse(PrimalPlacementPolicy.permits("pack:resource_world", "minecraft:overworld", 0, 20000, 20000));
        assertTrue(PrimalPlacementPolicy.permits("minecraft:overworld", "minecraft:overworld", 0, 0, 0));
        assertTrue(EntangledLinks.withinRange(new net.minecraft.core.BlockPos(0, 0, 0), new net.minecraft.core.BlockPos(32, 0, 0)));
        assertFalse(EntangledLinks.withinRange(new net.minecraft.core.BlockPos(0, 0, 0), new net.minecraft.core.BlockPos(32, 1, 0)));
    }
}
