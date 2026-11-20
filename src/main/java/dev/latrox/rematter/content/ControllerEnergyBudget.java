package dev.latrox.rematter.content;

import java.util.function.IntUnaryOperator;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** All sides and ports share this journal, including simulated and nested transfers. */
final class ControllerEnergyBudget extends SnapshotJournal<ControllerEnergyBudget.State> {
    static final int MAX_INPUT = 131_072;
    record State(long tick,int total,int nativeSpent,int nativeRate){}
    private State state=new State(Long.MIN_VALUE,0,0,0);
    int insert(long tick,int requested,int nativeRate,TransactionContext tx,IntUnaryOperator delegate) {
        if(requested<=0)return 0;
        State before=state.tick()==tick?state:new State(tick,0,0,0);
        int best=Math.max(before.nativeRate(),nativeRate);
        int limit=Math.max(0,MAX_INPUT-before.total());
        if(nativeRate>0)limit=Math.min(limit,Math.max(0,best-before.nativeSpent()));
        int allowed=Math.min(requested,limit);if(allowed==0)return 0;
        updateSnapshots(tx);
        // Reserve before calling another handler. The same controller cannot re-enter with a fresh allowance.
        state=new State(tick,before.total()+allowed,before.nativeSpent()+(nativeRate>0?allowed:0),best);
        int moved=delegate.applyAsInt(allowed);
        if(moved<0||moved>allowed)throw new IllegalStateException("Invalid energy transfer result");
        state=new State(tick,before.total()+moved,before.nativeSpent()+(nativeRate>0?moved:0),moved>0?best:before.nativeRate());
        return moved;
    }
    @Override protected State createSnapshot(){return state;}
    @Override protected void revertToSnapshot(State saved){state=saved;}
}
