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

    @Test void aDriveIsActiveWhileAnyBayHoldsAReadableCell() {
        assertFalse(CellStatus.anyReadable(0),"No cells");
        for(var status:CellStatus.values()) {
            boolean readable=status==CellStatus.EMPTY||status==CellStatus.STORED||status==CellStatus.FULL;
            assertEquals(readable,status.readable(),status.name());
            for(int bay=0;bay<6;bay++)assertEquals(readable,CellStatus.anyReadable(status.ordinal()<<bay*3),status+" in bay "+bay);
        }
        int locked=CellStatus.LOCKED.ordinal();
        assertFalse(CellStatus.anyReadable(locked|locked<<3|locked<<15),"Only unreadable cells");
        assertTrue(CellStatus.anyReadable(locked|CellStatus.STORED.ordinal()<<15),"One readable cell among unreadable ones");
        assertFalse(CellStatus.anyReadable(7<<6),"An unknown status value counts as unreadable");
    }
}
