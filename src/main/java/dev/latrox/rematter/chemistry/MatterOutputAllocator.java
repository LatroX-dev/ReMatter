package dev.latrox.rematter.chemistry;

import java.util.*;

/** Bounded exact-capacity flow with per-cell type-slot constraints; exhaustion rejects without mutation. */
final class MatterOutputAllocator {
    private static final int MAX_PLANS = 256;
    private static final int WORK_BUDGET = 2_000_000;
    private MatterOutputAllocator() {}

    static List<Map<SubstanceKey,Long>> allocate(List<MatterInventory> cells, Map<SubstanceKey,Long> outputs, boolean typesOnly, Budget budget) {
        var keys=List.copyOf(outputs.keySet()); int count=cells.size(); long required=MatterAmounts.total(outputs);
        // Only the output keys matter here, so each cell answers per key instead of copying its whole type table.
        var existing=new ArrayList<BitSet>(); int[] freeTypes=new int[count]; long[] freeSpace=new long[count];
        for(int i=0;i<count;i++) {
            var cell=cells.get(i); var types=new BitSet(keys.size());
            for(int k=0;k<keys.size();k++) if(cell.occupiesType(keys.get(k))) types.set(k);
            existing.add(types); freeTypes[i]=cell.maximumTypes()-cell.typeCount();
            freeSpace[i]=typesOnly?required:cell.freeSpace();
        }
        var pending=new ArrayDeque<BitSet>(); var visited=new HashSet<BitSet>(); pending.add(new BitSet());
        int plans=0;
        try {
            while(!pending.isEmpty() && plans++<MAX_PLANS) {
                var forbidden=pending.removeLast(); if(!visited.add(forbidden)) continue;
                int source=keys.size()+count, sink=source+1;
                var flow=new Flow(sink+1,budget); var links=new ArrayList<Link>();
                for(int k=0;k<keys.size();k++) {
                    var key=keys.get(k); long amount=outputs.get(key); flow.add(source,k,amount);
                    // Existing types first avoids needless fragmentation; bounded alternatives fix conflicts.
                    for(int pass=0;pass<2;pass++) for(int c=0;c<count;c++) {
                        boolean present=existing.get(c).get(k);
                        if(!cells.get(c).accepts(key) || (pass==0)!=present || freeSpace[c]==0 || !present&&freeTypes[c]==0 || forbidden.get(k*count+c)) continue;
                        var edge=flow.add(k,keys.size()+c,Math.min(amount,freeSpace[c]));
                        links.add(new Link(k,c,edge,present));
                    }
                }
                for(int c=0;c<count;c++) flow.add(keys.size()+c,sink,freeSpace[c]);
                if(flow.maximum(source,sink,required)!=required) continue;
                int[] used=new int[count];
                for(var link:links) if(!link.existing()&&link.edge().sent()>0) used[link.cell()]++;
                int conflict=-1;
                for(int c=0;c<count;c++) if(used[c]>freeTypes[c]) {conflict=c;break;}
                if(conflict<0) {
                    var result=new ArrayList<Map<SubstanceKey,Long>>(count);
                    for(int c=0;c<count;c++) result.add(new TreeMap<>());
                    for(var link:links) if(link.edge().sent()>0) result.get(link.cell()).put(keys.get(link.key()),link.edge().sent());
                    return result.stream().map(MatterAmounts::copy).toList();
                }
                // Any legal solution must omit at least one currently used new-type edge in this cell.
                for(var link:links) if(link.cell()==conflict&&!link.existing()&&link.edge().sent()>0) {
                    var next=(BitSet)forbidden.clone(); next.set(link.key()*count+link.cell());
                    if(!visited.contains(next)) pending.add(next);
                    if(pending.size()>=MAX_PLANS) break;
                }
            }
        } catch (BudgetExceeded exhausted) { /* No real inventory has been touched. */ }
        return null;
    }
    private record Link(int key,int cell,Edge edge,boolean existing) {}
    static final class Budget {
        int remaining=WORK_BUDGET;
        void work() { if(--remaining<0) throw new BudgetExceeded(); }
        boolean exhausted() { return remaining<0; }
    }
    private static final class BudgetExceeded extends RuntimeException {
        private BudgetExceeded() { super(null,null,false,false); }
    }
    private static final class Edge {
        final int target,reverse; final long initial; long remaining;
        Edge(int target,int reverse,long capacity) {this.target=target;this.reverse=reverse;this.initial=capacity;this.remaining=capacity;}
        long sent() {return initial-remaining;}
    }
    private static final class Flow {
        final List<List<Edge>> graph; final Budget budget; final int[] depth,next;
        Flow(int size,Budget budget) {
            this.budget=budget; graph=new ArrayList<>(size); for(int i=0;i<size;i++)graph.add(new ArrayList<>());
            depth=new int[size];next=new int[size];
        }
        Edge add(int from,int to,long capacity) {
            budget.work(); var edge=new Edge(to,graph.get(to).size(),capacity);
            graph.get(from).add(edge);graph.get(to).add(new Edge(from,graph.get(from).size()-1,0));return edge;
        }
        long maximum(int source,int sink,long wanted) {
            long sent=0;
            while(sent<wanted&&levels(source,sink)) {
                Arrays.fill(next,0); long part;
                while(sent<wanted&&(part=send(source,sink,wanted-sent))>0) sent=Math.addExact(sent,part);
            }
            return sent;
        }
        boolean levels(int source,int sink) {
            Arrays.fill(depth,-1);depth[source]=0;var queue=new ArrayDeque<Integer>();queue.add(source);
            while(!queue.isEmpty()) {
                int node=queue.removeFirst();
                for(var edge:graph.get(node)) {budget.work();if(edge.remaining>0&&depth[edge.target]<0){depth[edge.target]=depth[node]+1;queue.add(edge.target);}}
            }return depth[sink]>=0;
        }
        long send(int node,int sink,long amount) {
            if(node==sink)return amount;
            for(;next[node]<graph.get(node).size();next[node]++) {
                budget.work();var edge=graph.get(node).get(next[node]);
                if(edge.remaining<=0||depth[edge.target]!=depth[node]+1)continue;
                long sent=send(edge.target,sink,Math.min(amount,edge.remaining));
                if(sent>0){edge.remaining-=sent;graph.get(edge.target).get(edge.reverse).remaining+=sent;return sent;}
            }return 0;
        }
    }
}
