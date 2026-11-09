package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.MatterAmounts;
import dev.latrox.rematter.chemistry.SubstanceKey;
import java.util.*;

/** Unpaid work promises. Actual resources remain in their authoritative inventories until each commit. */
public final class BatchReservations {
    public static final class Ticket {
        private final UUID worker;
        private final Object topology;
        private final Set<?> members;
        private final Map<SubstanceKey, Long> inputs, outputs;
        private int remaining;
        private long touched;
        private boolean valid = true;
        Ticket(UUID worker, Object topology, Set<?> members, Map<SubstanceKey, Long> inputs,
               Map<SubstanceKey, Long> outputs, int remaining, long touched) {
            this.worker=worker; this.topology=topology; this.members=Set.copyOf(members);
            this.inputs=MatterAmounts.copy(inputs); this.outputs=MatterAmounts.copy(outputs);
            this.remaining=remaining; this.touched=touched;
        }
        public boolean valid() { return valid && remaining > 0; }
        public int remaining() { return valid() ? remaining : 0; }
    }
    private final Map<UUID, Ticket> claims = new HashMap<>();

    public void release(UUID worker) {
        var old=claims.remove(worker); if(old!=null)old.valid=false;
    }
    public void touch(Ticket ticket, long tick) { if(ticket.valid())ticket.touched=tick; }
    public void completed(Ticket ticket) {
        if(!ticket.valid() || claims.get(ticket.worker)!=ticket) throw new IllegalStateException("No live batch promise");
        if(--ticket.remaining==0)release(ticket.worker);
    }
    public long planned(Object topology, SubstanceKey key) {
        long amount=0;
        for(var t:claims.values())if(t.valid()&&t.topology.equals(topology))
            amount=Math.addExact(amount,Math.multiplyExact(t.outputs.getOrDefault(key,0L),t.remaining));
        return amount;
    }
    public Ticket reserve(UUID worker, Object topology, Set<?> members, long tick,
                          Map<SubstanceKey,Long> stock, long capacity,
                          Map<SubstanceKey,Long> inputs, Map<SubstanceKey,Long> outputs,
                          int limit, long energy, long cost, long physicalLimit, long target, SubstanceKey targetKey) {
        release(worker);
        // Topology changes retire overlapping promises before any new work can book their inputs.
        var iterator=claims.values().iterator();
        while(iterator.hasNext()) {
            var t=iterator.next();
            if(t.touched<tick-2 || !t.topology.equals(topology)&&!Collections.disjoint(t.members,members)) {
                t.valid=false; iterator.remove();
            }
        }
        if(limit<1||cost<=0||physicalLimit<1)return null;
        long count=Math.min(Math.min(limit,energy/cost),physicalLimit);
        var held=new TreeMap<SubstanceKey,Long>(); long occupied=0;
        for(var t:claims.values())if(t.valid()&&t.topology.equals(topology)) {
            t.inputs.forEach((key,amount)->held.merge(key,Math.multiplyExact(amount,t.remaining),Math::addExact));
            long delta=total(t.outputs)-total(t.inputs);
            if(delta>0)occupied=Math.addExact(occupied,Math.multiplyExact(delta,t.remaining));
        }
        for(var entry:inputs.entrySet())
            count=Math.min(count,Math.max(0,stock.getOrDefault(entry.getKey(),0L)-held.getOrDefault(entry.getKey(),0L))/entry.getValue());
        long delta=total(outputs)-total(inputs);
        if(delta>0)count=Math.min(count,Math.max(0,capacity-total(stock)-occupied)/delta);
        if(target>0&&targetKey!=null) {
            long batch=outputs.getOrDefault(targetKey,0L);
            if(batch<=0)return null;
            long current=Math.addExact(stock.getOrDefault(targetKey,0L),planned(topology,targetKey));
            count=Math.min(count,Math.max(0,target-current)/batch);
        }
        if(count<=0)return null;
        var ticket=new Ticket(worker,topology,members,inputs,outputs,(int)count,tick);
        claims.put(worker,ticket); return ticket;
    }
    private static long total(Map<SubstanceKey,Long> amounts) {
        long result=0;for(long amount:amounts.values())result=Math.addExact(result,amount);return result;
    }
}
