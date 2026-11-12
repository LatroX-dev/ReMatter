package dev.latrox.rematter.energy;

import java.util.function.IntUnaryOperator;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** One rollback-aware allowance is shared by every face, callback and item slot of a physical cube. */
final class EnergyCubeBudget extends SnapshotJournal<EnergyCubeBudget.State> {
    record State(long tick,int spent){}
    private final int rate;
    private State state=new State(Long.MIN_VALUE,0);
    EnergyCubeBudget(int rate){if(rate<=0)throw new IllegalArgumentException("Positive energy rate required");this.rate=rate;}
    int remaining(long tick){return rate-(state.tick==tick?state.spent:0);}
    int transfer(long tick,int requested,TransactionContext transaction,IntUnaryOperator operation){
        TransferPreconditions.checkNonNegative(requested);
        int allowed=Math.min(requested,remaining(tick));
        if(allowed==0)return 0;
        int previous=state.tick==tick?state.spent:0;
        updateSnapshots(transaction);
        // Reserve before calling external code, so re-entry cannot borrow this same allowance again.
        state=new State(tick,previous+allowed);
        int moved=operation.applyAsInt(allowed);
        if(moved<0||moved>allowed)throw new IllegalStateException("Energy handler violated its offered amount");
        // Nested calls may have spent the unreserved part while the operation ran.
        state=new State(tick,state.spent-(allowed-moved));
        return moved;
    }
    @Override protected State createSnapshot(){return state;}
    @Override protected void revertToSnapshot(State saved){state=saved;}
}
