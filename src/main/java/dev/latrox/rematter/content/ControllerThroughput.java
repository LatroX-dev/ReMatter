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
}
