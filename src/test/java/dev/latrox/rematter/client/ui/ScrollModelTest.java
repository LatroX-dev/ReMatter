package dev.latrox.rematter.client.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScrollModelTest {
    @Test void navigationKeepsTheSelectedRecipeInsideTheViewport() {
        var model=new ScrollModel(7);model.setSize(23);model.select(0);
        model.key(269);assertEquals(22,model.selected());assertEquals(16,model.offset());
        model.key(266);assertEquals(15,model.selected());assertEquals(15,model.offset());
        model.key(268);assertEquals(0,model.selected());assertEquals(0,model.offset());
        model.key(265);assertEquals(0,model.selected());
    }
    @Test void filteringAndResizingCannotLeaveAnEmptyPageBeyondTheResults() {
        var model=new ScrollModel(4);model.setSize(500);model.setOffset(496);
        model.setSize(2);assertEquals(0,model.offset());assertEquals(2,model.end());
        model.setSize(0);assertEquals(-1,model.selected());assertEquals(0,model.offset());
        model.setSize(30);model.select(29);model.setVisibleRows(20);assertEquals(10,model.offset());
    }
    @Test void trackpadFractionsAccumulateAndWheelDirectionIsConventional() {
        var model=new ScrollModel(5);model.setSize(100);
        model.wheel(-.2);assertEquals(0,model.offset());
        model.wheel(-.2);assertEquals(1,model.offset());
        model.wheel(-1);assertEquals(4,model.offset());
        model.wheel(1);assertEquals(1,model.offset());
        model.wheel(Double.NaN);assertEquals(1,model.offset());
    }
    @Test void scrollingIsBoundedEvenForUntrustedLargeDeltas() {
        var model=new ScrollModel(5);model.setSize(10);
        model.scroll(Integer.MAX_VALUE);assertEquals(5,model.offset());
        model.scroll(Integer.MIN_VALUE);assertEquals(0,model.offset());
        assertFalse(model.key(65));
    }
}
