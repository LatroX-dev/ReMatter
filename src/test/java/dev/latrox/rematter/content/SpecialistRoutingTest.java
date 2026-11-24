package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.automation.AutomationRecipeCatalogue;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpecialistRoutingTest {
    @Test void removedSpecialistsHaveNoRegistryKindsOrShippedAssetsAndCannotBeSynthesisTargets() {
        for(String id:Set.of("electrolyzer","separator","thermal_processor","crystallizer","air_separator")) {
            assertTrue(java.util.Arrays.stream(MachineKind.values()).noneMatch(kind->kind.id().equals(id)),id);
            for(String folder:java.util.List.of("blockstates","items","models/block"))
                assertNull(getClass().getResource("/assets/rematter/"+folder+"/"+id+".json"),id+" remains in "+folder);
        }
        assertThrows(IllegalArgumentException.class,()->new SynthesisRequirement("crystallizer",1,""));
    }
    @Test void everyProcessHasOneCompactMachineAndOnlySupportedIndustrialAlternatives() {
        for(var process:ShippedChemistry.standalone().processes()) {
            var machines=MachineRouting.machines(process.type());assertFalse(machines.isEmpty());
            for(var kind:MachineKind.values())assertEquals(machines.contains(kind),MachineRouting.supports(kind,process.type()));
        }
        assertTrue(MachineRouting.supports(MachineKind.REACTOR,ProcessType.PHASE_CHANGE));
        assertTrue(MachineRouting.supports(MachineKind.REACTOR,ProcessType.SEPARATION));
    }
    @Test void allCrystalFormsUseTheSameMachineAndDiamondTierIsRetainedInExternalPatterns() {
        var registry=ShippedChemistry.load(mod->!mod.equals("enderio"),ShippedChemistry.MAIN_RESOURCES);
        var recipes=AutomationRecipeCatalogue.chemistry(registry,id->true,id->true);
        for(String id:Set.of("minecraft:diamond","minecraft:diamond_block","alltheores:diamond_dust")) {
            assertTrue(registry.synthesisPolicy().allowsMachine(id,"material_synthesizer"),id);
            assertFalse(registry.synthesisPolicy().allowsMachine(id,"crystallizer"),id);
            var pattern=recipes.stream().filter(r->r.id().equals(AutomationRecipeCatalogue.id("synthesize_item",id))).findFirst().orElseThrow();
            assertEquals("material_synthesizer",pattern.workerKind());assertEquals(2,pattern.requiredTier());
        }
        // Mekanism diamond dust unifies into alltheores:diamond_dust when AllTheOres is present and takes over cleanly when absent.
        assertEquals("alltheores:diamond_dust",registry.synthesisPolicy().canonicalTarget("mekanism:dust_diamond"));
        var withoutAto=ShippedChemistry.load(mod->!mod.equals("enderio")&&!mod.equals("alltheores"),ShippedChemistry.MAIN_RESOURCES);
        assertTrue(withoutAto.synthesisPolicy().allowsMachine("mekanism:dust_diamond","material_synthesizer"));
        var mekPattern=AutomationRecipeCatalogue.chemistry(withoutAto,id->true,id->true).stream()
                .filter(r->r.id().equals(AutomationRecipeCatalogue.id("synthesize_item","mekanism:dust_diamond"))).findFirst().orElseThrow();
        assertEquals("material_synthesizer",mekPattern.workerKind());assertEquals(2,mekPattern.requiredTier());
        assertFalse(registry.synthesisPolicy().allowsItem("rematter:diamond_powder"));
        assertTrue(registry.synthesisPolicy().allowsMachine("minecraft:ender_pearl","material_synthesizer"));
    }
    @Test void optionalEnderIoBlocksOnlyTheExactUnsafeOutputsAndThePackSuppliesSafeOverrides() {
        var free=ShippedChemistry.load(mod->!mod.equals("enderio"),ShippedChemistry.MAIN_RESOURCES);
        var guarded=ShippedChemistry.standalone();
        for(String item:Set.of("minecraft:quartz","minecraft:obsidian")) {
            assertTrue(free.synthesisPolicy().allowsItem(item));assertFalse(guarded.synthesisPolicy().allowsItem(item));
        }
        assertTrue(guarded.synthesisPolicy().allowsItem("minecraft:quartz_block"));
    }
}
