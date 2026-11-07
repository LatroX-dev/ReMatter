package dev.latrox.rematter.automation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import dev.latrox.rematter.automation.AutomationPort.Recipe;

/**
 * Queued orders whose recipe vanished or changed. A change of process parameters alone continues under the
 * current recipe; a changed or missing material vector cancels the order after a grace period, which turns
 * its transferred inputs into refunds. Tracking is runtime-only and restarts after a reload.
 */
public final class StaleOrders {
    private final long delay;
    private final Map<UUID,Long> since=new HashMap<>();

    public StaleOrders(long delay) {
        if(delay<0)throw new IllegalArgumentException("Negative grace period");
        this.delay=delay;
    }
    public boolean waiting() { return !since.isEmpty(); }
    public void clear() { since.clear(); }

    /** Returns whether the ledger changed. {@code release} runs before an order with a live worker lease is cancelled. */
    public boolean retire(OrderLedger ledger,Function<String,Recipe> current,long now,Consumer<UUID> release) {
        Objects.requireNonNull(ledger);Objects.requireNonNull(current);Objects.requireNonNull(release);
        boolean changed=false;
        var stale=new HashSet<UUID>();
        for(var order:ledger.snapshot().orders()) {
            if(order.cancelled()||order.quarantined()||order.remainingBatches()==0)continue;
            var recipe=current.apply(order.recipe().id());
            if(order.recipe().equals(recipe))continue;
            if(recipe!=null&&ledger.rebase(order.id(),recipe)){changed=true;continue;}
            if(now-since.computeIfAbsent(order.id(),ignored->now)<delay){stale.add(order.id());continue;}
            if(order.workerId()!=null)release.accept(order.id());
            changed|=ledger.cancel(order.id());
        }
        since.keySet().retainAll(stale);
        return changed;
    }
}
