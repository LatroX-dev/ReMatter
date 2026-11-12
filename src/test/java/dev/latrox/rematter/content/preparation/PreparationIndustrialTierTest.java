package dev.latrox.rematter.content.preparation;

import dev.latrox.rematter.content.MachineKind;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreparationIndustrialTierTest {
    @Test void tierTwoBelongsOnlyToTheIndustrialPreparator(){
        for(var kind:MachineKind.values())assertEquals(kind==MachineKind.INDUSTRIAL_PREPARATOR,PreparationTier.permits(kind,2,"preparation"),kind.id());
        assertFalse(PreparationTier.permits(MachineKind.SINTER_FURNACE,2,"sintering"));
        assertTrue(PreparationTier.permits(MachineKind.SINTER_FURNACE,1,"sintering"));
        assertFalse(PreparationTier.permits(MachineKind.HAND_PREPARATOR,1,"preparation"));
        assertTrue(PreparationTier.permits(MachineKind.HAND_PREPARATOR,0,"preparation"));
    }
}
