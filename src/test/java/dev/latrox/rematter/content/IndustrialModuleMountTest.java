package dev.latrox.rematter.content;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IndustrialModuleMountTest {
    @Test void clickedSupportFaceDeterminesTheSocketBeforeAControllerExists() {
        var floor = IndustrialModuleMount.forPlacement(Direction.UP, true).orElseThrow();
        var ceiling = IndustrialModuleMount.forPlacement(Direction.DOWN, true).orElseThrow();
        assertFalse(floor.suspended()); assertTrue(ceiling.suspended());
        assertEquals(Direction.DOWN, floor.anchorDirection());
        assertEquals(Direction.UP, ceiling.anchorDirection());
        assertEquals(Direction.UP, floor.anchorFace());
        assertEquals(Direction.DOWN, ceiling.anchorFace());
        for (var side : Direction.Plane.HORIZONTAL)
            assertTrue(IndustrialModuleMount.forPlacement(side, true).isEmpty(), "No side-mounted substitute");
        for (var side : Direction.values())
            assertTrue(IndustrialModuleMount.forPlacement(side, false).isEmpty(), "No unsupported placement");
    }

    @Test void validatedFloorAndCeilingUseTheSameOrientationAsTheirClickedFaces() {
        for (int crown = 1; crown <= 6; crown++) {
            assertEquals(IndustrialModuleMount.forPlacement(Direction.UP, true), IndustrialModuleMount.atInteriorHeight(0, crown));
            assertEquals(IndustrialModuleMount.forPlacement(Direction.DOWN, true), IndustrialModuleMount.atInteriorHeight(crown, crown));
            for (int y = 1; y < crown; y++) assertTrue(IndustrialModuleMount.atInteriorHeight(y, crown).isEmpty());
            assertTrue(IndustrialModuleMount.atInteriorHeight(-1, crown).isEmpty());
            assertTrue(IndustrialModuleMount.atInteriorHeight(crown + 1, crown).isEmpty());
        }
    }
}
