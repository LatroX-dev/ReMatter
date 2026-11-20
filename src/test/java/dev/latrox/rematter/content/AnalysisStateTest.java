package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.*;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisStateTest {
    private static final SubstanceKey COPPER = BuiltinChemistry.COPPER;
    private static final MaterialProfile PROFILE = new MaterialProfile("minecraft:copper_ingot",1,Map.of(COPPER,1000L),8000,16000,100);

    @Test void completedResultRestoresForTheSameSampleAndEquivalentReloadedProfile() {
        AnalysisState before = AnalysisState.completed(PROFILE);
        AnalysisState restored = AnalysisState.restore(before.itemId(),before.profileFingerprint());
        assertEquals(before,restored);
        var reloaded = new MaterialProfile("minecraft:copper_ingot",1,Map.of(COPPER,1000L),8000,16000,100);
        assertNotSame(PROFILE,reloaded);
        assertTrue(restored.matches("minecraft:copper_ingot",true,reloaded));
    }

    @Test void anotherSampleMissingProfileOrComponentsNeverReuseAnalysis() {
        var result=AnalysisState.completed(PROFILE);
        assertFalse(result.matches("minecraft:gold_ingot",true,PROFILE));
        assertFalse(result.matches("minecraft:copper_ingot",false,PROFILE));
        assertFalse(result.matches("minecraft:copper_ingot",true,null));
        assertFalse(result.matches("",true,PROFILE));
        assertFalse(result.matches("minecraft:copper_ingot",true,
                new MaterialProfile("example:copper_ingot",1,PROFILE.matter(),8000,16000,100)));
    }

    @Test void changedBatchOrQuantityInvalidatesWithoutRounding() {
        var result=AnalysisState.completed(PROFILE);
        assertFalse(result.matches(PROFILE.itemId(),true,new MaterialProfile(PROFILE.itemId(),9,PROFILE.matter(),8000,16000,100)));
        assertFalse(result.matches(PROFILE.itemId(),true,new MaterialProfile(PROFILE.itemId(),1,Map.of(COPPER,999L),8000,16000,100)));
        assertFalse(result.matches(PROFILE.itemId(),true,new MaterialProfile(PROFILE.itemId(),1,Map.of(COPPER,1001L),8000,16000,100)));
    }

    @Test void changedSubstanceOrPhaseInvalidatesEvenWhenTotalQuantityMatches() {
        var result=AnalysisState.completed(PROFILE);
        var liquidCopper=new SubstanceKey(COPPER.id(),Phase.LIQUID);
        var other=new SubstanceKey(SubstanceId.parse("example:other_metal"),Phase.SOLID);
        assertFalse(result.matches(PROFILE.itemId(),true,new MaterialProfile(PROFILE.itemId(),1,Map.of(liquidCopper,1000L),8000,16000,100)));
        assertFalse(result.matches(PROFILE.itemId(),true,new MaterialProfile(PROFILE.itemId(),1,Map.of(other,1000L),8000,16000,100)));
    }

    @Test void everyProfileRevisionFieldParticipates() {
        var result=AnalysisState.completed(PROFILE);
        assertFalse(result.matches(PROFILE.itemId(),true,new MaterialProfile(PROFILE.itemId(),1,PROFILE.matter(),8001,16000,100)));
        assertFalse(result.matches(PROFILE.itemId(),true,new MaterialProfile(PROFILE.itemId(),1,PROFILE.matter(),8000,16001,100)));
        assertFalse(result.matches(PROFILE.itemId(),true,new MaterialProfile(PROFILE.itemId(),1,PROFILE.matter(),8000,16000,101)));
    }

    @Test void mapInsertionOrderIsNotAProfileChange() {
        var second=new SubstanceKey(SubstanceId.parse("example:trace"),Phase.GAS);
        Map<SubstanceKey,Long> a=new LinkedHashMap<>();a.put(COPPER,999L);a.put(second,1L);
        Map<SubstanceKey,Long> b=new LinkedHashMap<>();b.put(second,1L);b.put(COPPER,999L);
        var first=new MaterialProfile(PROFILE.itemId(),1,a,8000,16000,100);
        var reordered=new MaterialProfile(PROFILE.itemId(),1,b,8000,16000,100);
        assertEquals(AnalysisState.completed(first),AnalysisState.completed(reordered));
    }

    @Test void missingOrMalformedSavedStateCannotGrantAnAnalysis() {
        var valid=AnalysisState.completed(PROFILE);
        assertEquals(AnalysisState.EMPTY,AnalysisState.restore("",""));
        assertEquals(AnalysisState.EMPTY,AnalysisState.restore("invalid item",valid.profileFingerprint()));
        assertEquals(AnalysisState.EMPTY,AnalysisState.restore(valid.itemId(),"not-a-profile-hash"));
        assertFalse(AnalysisState.EMPTY.matches(PROFILE.itemId(),true,PROFILE));
        assertFalse(AnalysisState.restore(valid.itemId(),"0".repeat(64)).matches(PROFILE.itemId(),true,PROFILE));
    }
}
