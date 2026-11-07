package dev.latrox.rematter.content;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MachineSideConfigTest {
    @Test void cyclingReturnsThePreviousModeAndAutomaticFacesStayImplicit() {
        var sides = new MachineSideConfig();
        assertEquals(MachinePortMode.AUTOMATIC, sides.mode(MachinePortMode.Side.BACK));
        assertEquals(0, sides.visuals());
        var previous = sides.cycle(MachineKind.SYNTHESIZER, MachinePortMode.Side.BACK);
        assertEquals(MachinePortMode.AUTOMATIC, previous);
        assertNotEquals(MachinePortMode.AUTOMATIC, sides.mode(MachinePortMode.Side.BACK));
        assertEquals(MachinePortMode.AUTOMATIC, sides.mode(MachinePortMode.Side.LEFT));
    }

    @Test void aSidelessQueryRequiresTheRoleOnEveryFace() {
        var sides = new MachineSideConfig();
        assertTrue(sides.allowsEverywhere(MachinePortMode.POWER));
        while (sides.mode(MachinePortMode.Side.TOP) != MachinePortMode.DISABLED) sides.cycle(MachineKind.SYNTHESIZER, MachinePortMode.Side.TOP);
        assertFalse(sides.allowsEverywhere(MachinePortMode.POWER));
    }

    @Test void clientVisualsRoundTripEveryFace() {
        var sides = new MachineSideConfig();
        sides.cycle(MachineKind.DECOMPOSER, MachinePortMode.Side.LEFT);
        sides.cycle(MachineKind.DECOMPOSER, MachinePortMode.Side.BOTTOM);
        sides.cycle(MachineKind.DECOMPOSER, MachinePortMode.Side.BOTTOM);
        var copy = new MachineSideConfig();
        copy.readVisuals(sides.visuals());
        for (var side : MachinePortMode.Side.values()) assertEquals(sides.mode(side), copy.mode(side));
        copy.readVisuals(0);
        for (var side : MachinePortMode.Side.values()) assertEquals(MachinePortMode.AUTOMATIC, copy.mode(side));
    }
}
