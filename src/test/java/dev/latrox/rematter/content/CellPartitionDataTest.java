package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.*;
import java.util.*;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CellPartitionDataTest {
    private static final SubstanceKey IRON=new SubstanceKey(SubstanceId.parse("test:iron"),Phase.SOLID);
    private static final SubstanceKey GAS=new SubstanceKey(SubstanceId.parse("missing:gas"),Phase.GAS);
    @Test void independentVersionedPolicyKeepsFullUnknownIdentifiersAndPhases(){
        var data=CellPartitionData.allowOnly(List.of(IRON,GAS));var state=data.open().orElseThrow();
        assertEquals(Set.of(IRON,GAS),state.allowed());assertTrue(state.accepts(GAS));
        assertFalse(state.accepts(new SubstanceKey(GAS.id(),Phase.LIQUID)));
        assertEquals(data,new CellPartitionData(data.copyTag()));
    }
    @Test void anyAndEmptyWhitelistHaveDifferentMeaning(){
        assertTrue(CellPartitionData.unrestricted().open().orElseThrow().accepts(GAS));
        assertFalse(CellPartitionData.allowOnly(List.of()).open().orElseThrow().accepts(GAS));
    }
    @Test void invalidOrFuturePoliciesRemainByteForBytePreserved(){
        var raw=new CompoundTag();raw.putInt("schema",99);raw.putString("future","keep");
        var data=new CellPartitionData(raw);assertTrue(data.open().isEmpty());assertEquals(raw,data.copyTag());
        var old=CellPartitionData.allowOnly(List.of(IRON)).copyTag();old.putString("extra","keep");
        assertTrue(new CellPartitionData(old).open().isEmpty());
    }
    @Test void duplicateOrMalformedEntriesDoNotSilentlyWidenTheFilter(){
        var raw=CellPartitionData.allowOnly(List.of(IRON)).copyTag();var rows=raw.getList("keys").orElseThrow();rows.add(rows.get(0).copy());
        assertTrue(new CellPartitionData(raw).open().isEmpty());
        raw=CellPartitionData.allowOnly(List.of(IRON)).copyTag();raw.getList("keys").orElseThrow().getCompound(0).orElseThrow().putString("phase","UNKNOWN");
        assertTrue(new CellPartitionData(raw).open().isEmpty());
    }
    @Test void policySizeIsBoundedAndPrimalCannotEnterOrdinaryCellConfiguration(){
        var many=new ArrayList<SubstanceKey>();for(int i=0;i<513;i++)many.add(new SubstanceKey(SubstanceId.parse("test:k"+i),Phase.SOLID));
        assertThrows(IllegalArgumentException.class,()->CellPartitionData.allowOnly(many));
        var primal=new SubstanceKey(ReservedSubstances.PRIMAL_MATTER,Phase.SOLID);
        assertThrows(IllegalArgumentException.class,()->CellPartitionData.allowOnly(List.of(primal)));
    }
}
