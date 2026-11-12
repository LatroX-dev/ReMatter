package dev.latrox.rematter.content.industrial;

import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.content.*;
import dev.latrox.rematter.data.ChemistryDocuments;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IndustrialProcessTest {
    private static final Path DATA=Path.of("src/main/resources/data/rematter/rematter/process");
    @Test void allTenProceduresConserveElementsAndRouteOnlyToIndustrialControllers()throws Exception{
        var registry=ShippedChemistry.standalone();int count=0;
        try(var paths=Files.list(DATA)){
            for(var path:paths.toList()){
                var id=SubstanceId.parse("rematter:"+path.getFileName().toString().replace(".json",""));
                var process=ChemistryDocuments.process(id,ShippedChemistry.read(path));
                assertEquals(registry.elementAmounts(process.inputs()),registry.elementAmounts(process.outputs()),id.toString());
                assertTrue(process.minField()==4||process.minField()==8);assertNotNull(process.product());
                assertTrue(process.outputs().keySet().stream().anyMatch(key->key.id().equals(process.product())));
                assertEquals(MachineKind.INDUSTRIAL_REACTOR,MachineRouting.primary(process));
                assertFalse(MachineRouting.supports(MachineKind.REACTOR,process));assertTrue(MachineRouting.supports(MachineKind.INDUSTRIAL_REACTOR,process));
                assertEquals(List.of(MachineKind.INDUSTRIAL_REACTOR),MachineRouting.machines(process));
                assertTrue(MatterAmounts.total(process.outputs())<=8*MatterAmounts.total(process.inputs()));count++;
            }
        }
        assertEquals(10,count);
    }
    @Test void referencePackClosesEveryNewProcessUntilItsOwnAudit(){
        var registry=ShippedChemistry.load(ShippedChemistry.MAIN_RESOURCES,Path.of("integrations/reforging/datapack"));
        assertTrue(registry.processes().stream().noneMatch(process->process.minField()>0));
    }
    @Test void unavailableUsefulProductsAreFilteredWithoutDiscardingBalancedDefinitions(){
        var registry=ShippedChemistry.load(mod->mod.equals("minecraft")||mod.equals("rematter"),ShippedChemistry.MAIN_RESOURCES);
        assertTrue(registry.recipe(SubstanceId.parse("rematter:alumina_reduction")).isEmpty());
        assertTrue(registry.recipe(SubstanceId.parse("rematter:quartz_growth")).isPresent());
        assertTrue(registry.recipe(SubstanceId.parse("rematter:diamond_growth")).isPresent());
    }
    @Test void evenElementBalancedProcessesCannotExpandMoreThanEightfold(){
        var carbon=BuiltinChemistry.CARBON;
        var concentrated=new SubstanceDefinition(SubstanceId.parse("test:concentrate"),"substance.test.concentrate","",SubstanceKind.MIXTURE,new Composition(Map.of(carbon.id(),100L)),Set.of(Phase.SOLID));
        var recipe=new ProcessRecipe(SubstanceId.parse("test:oversized"),ProcessType.SEPARATION,Map.of(new SubstanceKey(concentrated.id(),Phase.SOLID),1L),Map.of(carbon,100L),100,20);
        assertThrows(IllegalArgumentException.class,()->ChemistryFixtures.copyOf(BuiltinChemistry.create()).addSubstance(concentrated).addProcess(recipe).build());
    }
}
