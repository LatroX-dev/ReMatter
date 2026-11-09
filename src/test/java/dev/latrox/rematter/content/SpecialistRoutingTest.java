package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.automation.AutomationRecipeCatalogue;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpecialistRoutingTest {
    @Test void everyProcessHasOneCompactMachineAndOnlySupportedIndustrialAlternatives() {
        for(var process:ShippedChemistry.standalone().processes()) {
            var machines=MachineRouting.machines(process.type());assertFalse(machines.isEmpty());
            for(var kind:MachineKind.values())assertEquals(machines.contains(kind),MachineRouting.supports(kind,process.type()));
        }
        assertFalse(MachineRouting.supports(MachineKind.REACTOR,ProcessType.PHASE_CHANGE));
        assertFalse(MachineRouting.supports(MachineKind.REACTOR,ProcessType.SEPARATION));
    }
    @Test void allCrystalFormsUseTheSameMachineAndDiamondTierIsRetainedInExternalPatterns() {
        var registry=ShippedChemistry.load(mod->!mod.equals("enderio"),ShippedChemistry.MAIN_RESOURCES);
        var recipes=AutomationRecipeCatalogue.chemistry(registry,id->true,id->true);
        for(String id:Set.of("minecraft:diamond","minecraft:diamond_block","mekanism:dust_diamond")) {
            assertTrue(registry.synthesisPolicy().allowsMachine(id,"crystallizer"));
            assertFalse(registry.synthesisPolicy().allowsMachine(id,"material_synthesizer"));
            var pattern=recipes.stream().filter(r->r.id().equals(AutomationRecipeCatalogue.id("synthesize_item",id))).findFirst().orElseThrow();
            assertEquals("crystallizer",pattern.workerKind());assertEquals(2,pattern.requiredTier());
        }
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
