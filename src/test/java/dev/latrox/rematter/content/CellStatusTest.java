package dev.latrox.rematter.content;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CellStatusTest {
    @Test void indicatorsDistinguishEmptyStoredFullAndPreservedUnreadableCells() {
        assertEquals(CellStatus.ABSENT,CellStatus.of(false,false,0,0,0,0));
        assertEquals(CellStatus.LOCKED,CellStatus.of(true,false,0,64000,0,256));
        assertEquals(CellStatus.EMPTY,CellStatus.of(true,true,0,64000,0,256));
        assertEquals(CellStatus.STORED,CellStatus.of(true,true,1,64000,1,256));
        assertEquals(CellStatus.STORED,CellStatus.of(true,true,63999,64000,1,256));
        assertEquals(CellStatus.FULL,CellStatus.of(true,true,64000,64000,1,256));
        assertEquals(CellStatus.FULL,CellStatus.of(true,true,256,64000,256,256));
        assertEquals(CellStatus.LOCKED,CellStatus.decode(7));
    }
}
