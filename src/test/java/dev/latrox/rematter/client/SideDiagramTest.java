package dev.latrox.rematter.client;

import dev.latrox.rematter.content.MachinePortMode.Side;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SideDiagramTest {
    @Test void allCompactAlternativesFitTheExistingSettingsAreaWithoutOverlappingHitTargets() {
        for (var layout : SideDiagram.Layout.values()) {
            var faces = new EnumMap<Side, SideDiagram.Bounds>(Side.class);
            for (var side : Side.values()) {
                var bounds = SideDiagram.place(layout, side);
                assertTrue(bounds.size() >= 17, layout + ": face too small for the port glyph");
                assertTrue(bounds.x() >= 0 && bounds.y() >= 0);
                assertTrue(bounds.x() + bounds.size() <= SideDiagram.WIDTH, layout + ": outside panel width");
                assertTrue(bounds.y() + bounds.size() <= SideDiagram.HEIGHT, layout + ": outside panel height");
                for (var other : faces.values()) assertFalse(
                        bounds.x() < other.x() + other.size() && bounds.x() + bounds.size() > other.x()
                        && bounds.y() < other.y() + other.size() && bounds.y() + bounds.size() > other.y(),
                        layout + ": overlapping side controls");
                faces.put(side, bounds);
            }
        }
    }

    @Test void recommendedNetKeepsThePhysicalNeighborsOfTheFrontAndTheBackBeyondTheRight() {
        var layout = SideDiagram.Layout.FACE_NET;
        var front = SideDiagram.place(layout, Side.FRONT);
        var left = SideDiagram.place(layout, Side.LEFT);
        var right = SideDiagram.place(layout, Side.RIGHT);
        var back = SideDiagram.place(layout, Side.BACK);
        var top = SideDiagram.place(layout, Side.TOP);
        var bottom = SideDiagram.place(layout, Side.BOTTOM);
        assertEquals(front.x(), left.x() + left.size());
        assertEquals(front.x() + front.size(), right.x());
        assertEquals(right.x() + right.size(), back.x());
        assertEquals(front.y(), left.y());
        assertEquals(front.y(), right.y());
        assertEquals(front.y(), back.y());
        assertEquals(front.x(), top.x());
        assertEquals(front.x(), bottom.x());
        assertEquals(front.y(), top.y() + top.size());
        assertEquals(front.y() + front.size(), bottom.y());
    }
}
