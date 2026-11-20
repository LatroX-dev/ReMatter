package dev.latrox.rematter.content;

import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatterCellMountTransactionTest {
    @Test void emptyBaysCanReceiveDirtyNotificationsDuringRollbackAndRootCommit(){
        var rack=new MatterCellRack(()->{});
        var journal=new SnapshotJournal<Integer>(){
            @Override protected Integer createSnapshot(){return 0;}
            @Override protected void revertToSnapshot(Integer previous){rack.flush();}
            @Override protected void onRootCommit(Integer previous){rack.flush();}
            void enlist(TransactionContext tx){updateSnapshots(tx);}
        };
        assertDoesNotThrow(()->{try(var tx=Transaction.openRoot()){journal.enlist(tx);}});
        assertDoesNotThrow(()->{try(var tx=Transaction.openRoot()){journal.enlist(tx);tx.commit();}});
    }
}
