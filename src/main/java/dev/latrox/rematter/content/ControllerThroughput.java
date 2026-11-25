package dev.latrox.rematter.content;

import java.util.*;

/** Exact transfer work: one active controller, one budget per tick, no idle credit. */
public final class ControllerThroughput {
    private String operation = "";
    private long required, paid, tick = Long.MIN_VALUE, spent;

    public boolean advance(String signature,long total,long gameTick,long rate) {
        if(total<0||rate<=0)throw new IllegalArgumentException("Invalid transfer work");
        if(!operation.equals(signature)||required!=total){operation=signature;required=total;paid=0;}
        if(tick!=gameTick){tick=gameTick;spent=0;}
        long accepted=Math.min(required-paid,Math.max(0,rate-spent));
        paid+=accepted;spent+=accepted;
        return paid==required;
    }

    /** Advances work with a shared per-tick allowance (which may be 0 when a shared relay is saturated) and returns units accepted. */
    long advanceBounded(String signature, long total, long gameTick, long maxRate, long sharedAllowance) {
        if (total < 0 || maxRate <= 0) throw new IllegalArgumentException("Invalid transfer work");
        if (!operation.equals(signature) || required != total) { operation = signature; required = total; paid = 0; }
        if (tick != gameTick) { tick = gameTick; spent = 0; }
        long localRemaining = Math.max(0L, maxRate - spent);
        long allowance = Math.min(localRemaining, Math.max(0L, sharedAllowance));
        long accepted = Math.min(required - paid, allowance);
        paid += accepted;
        spent += accepted;
        return accepted;
    }

    boolean isComplete() { return paid == required; }
    /** Completion discards that job's work; another completion in the same tick gets only the remainder. */
    public void completed(){operation="";required=paid=0;}
    public long paid(){return paid;}
    public long remaining(){return required-paid;}
    public String operation(){return operation;}

    public static long units(Collection<Long> inputs,Collection<Long> outputs,int batches) {
        if(batches<1)throw new IllegalArgumentException("Invalid batches");
        long sum=0;
        for(var values:List.of(inputs,outputs))for(long value:values){if(value<0)throw new IllegalArgumentException("Negative transfer");sum=Math.addExact(sum,value);}
        return Math.multiplyExact(sum,batches);
    }

    /** Widest route: the weakest cable on the best usable route; equal routes never add their rates. */
    static <N> int bestTier(N origin,Set<N> destinations,Map<N,Integer> cableTiers,Map<N,List<N>> neighbors) {
        record Visit<N>(N node,int quality){}
        var queue=new PriorityQueue<Visit<N>>(Comparator.<Visit<N>>comparingInt(Visit::quality).reversed());
        var quality=new HashMap<N,Integer>();quality.put(origin,3);queue.add(new Visit<>(origin,3));
        int best=-1;
        while(!queue.isEmpty()){
            var current=queue.remove();if(current.quality()!=quality.get(current.node()))continue;
            if(destinations.contains(current.node()))best=Math.max(best,current.quality()==3?0:current.quality());
            for(var next:neighbors.getOrDefault(current.node(),List.of())){
                int candidate=Math.min(current.quality(),cableTiers.getOrDefault(next,3));
                if(candidate>quality.getOrDefault(next,-1)){quality.put(next,candidate);queue.add(new Visit<>(next,candidate));}
            }
        }
        return Math.max(0,best);
    }

    record Route<N>(int rate, List<N> path) {
        boolean reachable() { return rate > 0 && !path.isEmpty(); }
    }

    /** Widest exact rate across cables and inline valves; equal routes never add their rates. */
    static <N> int bestRate(N origin,Set<N> destinations,Map<N,Integer> nodeRates,Map<N,List<N>> neighbors,int defaultRate) {
        var route = bestRoute(origin, destinations, nodeRates, neighbors, defaultRate);
        return route.rate() <= 0 ? defaultRate : route.rate();
    }

    /** Widest directed route across cables and inline valves, returning both bottleneck rate and traversed nodes. */
    static <N> Route<N> bestRoute(N origin, Set<N> destinations, Map<N, Integer> nodeRates, Map<N, List<N>> neighbors, int defaultRate) {
        record Visit<N>(N node, int rate, int hops) {}
        var queue = new PriorityQueue<Visit<N>>(Comparator.<Visit<N>>comparingInt(Visit::rate).reversed()
                .thenComparingInt(Visit::hops));
        var rates = new HashMap<N, Integer>();
        var hops = new HashMap<N, Integer>();
        var prev = new HashMap<N, N>();
        rates.put(origin, Integer.MAX_VALUE);
        hops.put(origin, 0);
        queue.add(new Visit<>(origin, Integer.MAX_VALUE, 0));
        int bestRate = -1;
        int bestHops = Integer.MAX_VALUE;
        N bestDest = null;
        while (!queue.isEmpty()) {
            var current = queue.remove();
            if (current.rate() != rates.get(current.node()) || current.hops() != hops.get(current.node())) continue;
            if (destinations.contains(current.node())) {
                int eff = current.rate() == Integer.MAX_VALUE ? defaultRate : current.rate();
                if (eff > bestRate || (eff == bestRate && current.hops() < bestHops)) {
                    bestRate = eff;
                    bestHops = current.hops();
                    bestDest = current.node();
                }
            }
            for (var next : neighbors.getOrDefault(current.node(), List.of())) {
                int candidate = Math.min(current.rate(), nodeRates.getOrDefault(next, Integer.MAX_VALUE));
                if (candidate <= 0) continue;
                int nextHops = current.hops() + 1;
                int prevRate = rates.getOrDefault(next, -1);
                int prevHops = hops.getOrDefault(next, Integer.MAX_VALUE);
                if (candidate > prevRate || (candidate == prevRate && nextHops < prevHops)) {
                    rates.put(next, candidate);
                    hops.put(next, nextHops);
                    prev.put(next, current.node());
                    queue.add(new Visit<>(next, candidate, nextHops));
                }
            }
        }
        if (bestDest == null || bestRate <= 0) return new Route<>(0, List.of());
        var path = new ArrayList<N>();
        for (N cur = bestDest; cur != null; cur = prev.get(cur)) {
            path.add(cur);
            if (cur.equals(origin)) break;
        }
        Collections.reverse(path);
        return new Route<>(bestRate, List.copyOf(path));
    }
}
