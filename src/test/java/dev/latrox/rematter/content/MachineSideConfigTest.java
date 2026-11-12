package dev.latrox.rematter.content;

import dev.latrox.rematter.content.MachinePortMode.Side;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MachineSideConfigTest {
    @Test void cyclingReturnsThePreviousModeAndUnconfiguredSidesStayImplicit() {
        var sides = new MachineSideConfig();
        assertEquals(MachinePortMode.AUTOMATIC, sides.mode(MachineKind.SYNTHESIZER, Side.BACK));
        var previous = sides.cycle(MachineKind.SYNTHESIZER, Side.BACK, true);
        assertEquals(MachinePortMode.AUTOMATIC, previous);
        assertNotEquals(MachinePortMode.AUTOMATIC, sides.mode(MachineKind.SYNTHESIZER, Side.BACK));
        assertEquals(MachinePortMode.AUTOMATIC, sides.mode(MachineKind.SYNTHESIZER, Side.LEFT));
    }

    @Test void theFrontAndEveryOtherClosedSideAreDisabledAndRefuseEveryCycle() {
        for (var kind : MachineKind.values()) {
            var sides = new MachineSideConfig();
            for (var side : Side.values()) {
                if (MachinePortMode.configurable(kind, side)) continue;
                var before = sides.mode(kind, side);
                assertNull(sides.cycle(kind, side, true), kind + " " + side + " accepted a forward step");
                assertNull(sides.cycle(kind, side, false), kind + " " + side + " accepted a backward step");
                assertEquals(before, sides.mode(kind, side));
                if (!PortSides.available(kind, side)) assertEquals(MachinePortMode.DISABLED, before, kind + " " + side);
            }
            assertEquals(MachinePortMode.DISABLED, sides.mode(kind, Side.FRONT), kind + " front");
        }
    }

    @Test void forwardAndBackwardVisitTheSameSupportedRolesInOppositeOrder() {
        for (var kind : MachineKind.values()) {
            if (!MachinePortMode.configurable(kind)) continue;
            var sides = new MachineSideConfig();
            var forward = new java.util.ArrayList<MachinePortMode>();
            do { forward.add(sides.mode(kind, Side.BACK)); sides.cycle(kind, Side.BACK, true); }
            while (sides.mode(kind, Side.BACK) != MachinePortMode.AUTOMATIC);
            var backward = new java.util.ArrayList<MachinePortMode>();
            do { backward.add(sides.mode(kind, Side.BACK)); sides.cycle(kind, Side.BACK, false); }
            while (sides.mode(kind, Side.BACK) != MachinePortMode.AUTOMATIC);
            assertEquals(EnumSet.copyOf(forward), EnumSet.copyOf(backward), kind.id());
            assertEquals(forward.get(1), backward.get(backward.size() - 1), kind.id());
            assertTrue(forward.contains(MachinePortMode.DISABLED) && forward.contains(MachinePortMode.MATTER), kind.id());
        }
    }

    @Test void clientVisualsRoundTripEverySideAndCloseTheUnavailableOnes() {
        var sides = new MachineSideConfig();
        sides.cycle(MachineKind.DECOMPOSER, Side.LEFT, true);
        sides.cycle(MachineKind.DECOMPOSER, Side.BOTTOM, true);
        sides.cycle(MachineKind.DECOMPOSER, Side.BOTTOM, true);
        var copy = new MachineSideConfig();
        copy.readVisuals(MachineKind.DECOMPOSER, sides.visuals(MachineKind.DECOMPOSER));
        for (var side : Side.values()) assertEquals(sides.mode(MachineKind.DECOMPOSER, side), copy.mode(MachineKind.DECOMPOSER, side));
        assertEquals(MachinePortMode.DISABLED, PortSides.unpack(sides.visuals(MachineKind.DECOMPOSER), Side.FRONT));
        assertEquals(MachinePortMode.DISABLED, PortSides.unpack(sides.visuals(MachineKind.DECOMPOSER), Side.TOP));
        // A forged update cannot open the front on the client either.
        copy.readVisuals(MachineKind.DECOMPOSER, 0);
        for (var side : Side.values())
            assertEquals(PortSides.available(MachineKind.DECOMPOSER, side) ? MachinePortMode.AUTOMATIC : MachinePortMode.DISABLED,
                    copy.mode(MachineKind.DECOMPOSER, side));
    }

    @Test void aSealedTopFollowsTheOneSwitch() {
        var sides = new MachineSideConfig();
        var top = sides.mode(MachineKind.DRIVE, Side.TOP);
        switch (PortSides.SEALED_TOP) {
            case CONNECTION -> assertEquals(MachinePortMode.AUTOMATIC, top);
            case OPT_IN -> {
                assertEquals(MachinePortMode.DISABLED, top);
                assertEquals(MachinePortMode.DISABLED, sides.cycle(MachineKind.DRIVE, Side.TOP, true));
                assertEquals(MachinePortMode.AUTOMATIC, sides.mode(MachineKind.DRIVE, Side.TOP), "one click enables the top");
                // The enabled top is the stored choice; the initial role of a later version must not undo it.
                var copy = new MachineSideConfig();
                copy.readVisuals(MachineKind.DRIVE, sides.visuals(MachineKind.DRIVE));
                assertEquals(MachinePortMode.AUTOMATIC, copy.mode(MachineKind.DRIVE, Side.TOP));
            }
            case CLOSED -> { assertEquals(MachinePortMode.DISABLED, top); assertNull(sides.cycle(MachineKind.DRIVE, Side.TOP, true)); }
        }
    }
}
