package dev.latrox.rematter.automation;

import java.util.*;
import dev.latrox.rematter.automation.AutomationPort.*;

/** Durable ownership of transferred ingredients and completed outputs, independent of external mods. */
public final class OrderLedger {
    public static final int SCHEMA=1, MAX_ORDERS=64, MAX_BATCHES=64;
    public record Order(UUID id,Recipe recipe,int batches,int remainingBatches,int progress,
                        Map<Resource,Long> ownedInputs,Map<Resource,Long> pendingOutputs,
                        boolean cancelled,String quarantineReason,UUID workerId) {
        public Order {
            Objects.requireNonNull(id);Objects.requireNonNull(recipe);Objects.requireNonNull(quarantineReason);
            ownedInputs=ResourceAmounts.copy(ownedInputs);pendingOutputs=ResourceAmounts.copy(pendingOutputs);
            if(batches<1||batches>MAX_BATCHES||remainingBatches<0||remainingBatches>batches||progress<0||progress>recipe.ticks()
                    || quarantineReason.length()>256 || (cancelled||remainingBatches==0)&&(progress!=0||workerId!=null))
                throw new IllegalArgumentException("Invalid order state");
            var expected=ResourceAmounts.multiply(recipe.inputs(),remainingBatches);
            if(!cancelled&&!ownedInputs.equals(expected)||cancelled&&!within(ownedInputs,expected))
                throw new IllegalArgumentException("Transferred input ownership mismatch");
            if(!within(pendingOutputs,ResourceAmounts.multiply(recipe.outputs(),batches-remainingBatches)))
                throw new IllegalArgumentException("Outputs exceed completed work");
            ResourceAmounts.multiply(recipe.inputs(),batches);ResourceAmounts.multiply(recipe.outputs(),batches);
        }
        public boolean quarantined() { return !quarantineReason.isEmpty(); }
    }
    public record Snapshot(int schema,List<Order> orders) {
        public Snapshot { orders=List.copyOf(orders); }
    }
    private static final String DELIVERY_IN_FLIGHT="delivery_in_flight";
    private record Attempt(Resource resource,long offered,boolean refund) {}
    private final LinkedHashMap<UUID,Order> orders=new LinkedHashMap<>();
    // Never serialized: a saved/drop copy cannot prove what an external receiver accepted.
    private final Map<UUID,Attempt> deliveryAttempts=new HashMap<>();
    public synchronized Snapshot snapshot() { return new Snapshot(SCHEMA,List.copyOf(orders.values())); }
    public static OrderLedger restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot);
        if(snapshot.schema()!=SCHEMA||snapshot.orders().size()>MAX_ORDERS)throw new IllegalArgumentException("Unsupported order ledger");
        var ledger=new OrderLedger();
        for(var order:snapshot.orders())if(ledger.orders.putIfAbsent(order.id(),order)!=null)throw new IllegalArgumentException("Duplicate order id");
        if(ledger.remainingBatches()>MAX_BATCHES||!OrderLedgerCodec.fits(snapshot))throw new IllegalArgumentException("Queue exceeds durable bounds");
        return ledger;
    }
    public synchronized int remainingBatches() { return orders.values().stream().filter(o->!o.cancelled()).mapToInt(Order::remainingBatches).sum(); }
    public synchronized boolean busy() { return orders.size()>=MAX_ORDERS||remainingBatches()>=MAX_BATCHES; }
    public synchronized boolean isEmpty() { return orders.isEmpty(); }
    public synchronized Optional<Order> order(UUID id) { return Optional.ofNullable(orders.get(id)); }
    public synchronized Optional<Order> next() { return orders.values().stream().filter(OrderLedger::pendingWork).filter(o->o.workerId()==null).findFirst(); }
    private static boolean pendingWork(Order order) {
        return order.remainingBatches()>0&&!order.cancelled()&&!order.quarantined();
    }
    public synchronized boolean accept(Recipe recipe,int batches,Map<Resource,Long> transferred,Action action) {
        Objects.requireNonNull(recipe);Objects.requireNonNull(action);
        if(batches<1||batches>MAX_BATCHES||busy()||remainingBatches()+batches>MAX_BATCHES)return false;
        try {
            var exact=ResourceAmounts.multiply(recipe.inputs(),batches);
            ResourceAmounts.multiply(recipe.outputs(),batches);
            if(!exact.equals(ResourceAmounts.copy(transferred)))return false;
            UUID id;do{id=UUID.randomUUID();}while(orders.containsKey(id));
            var prepared=new Order(id,recipe,batches,batches,0,exact,Map.of(),false,"",null);
            var future=new ArrayList<>(orders.values());future.add(prepared);
            if(!OrderLedgerCodec.fits(new Snapshot(SCHEMA,future)))return false;
            if(action==Action.EXECUTE)orders.put(id,prepared);
            return true;
        }catch(ArithmeticException overflow){return false;}
    }
    public synchronized boolean claim(UUID id,UUID workerId,String currentRevision) {
        Objects.requireNonNull(workerId);var order=orders.get(id);
        if(order==null||!pendingWork(order)||!order.recipe().revision().equals(currentRevision)
                ||order.workerId()!=null&&!order.workerId().equals(workerId))return false;
        orders.put(id,copy(order,order.remainingBatches(),order.progress(),order.ownedInputs(),order.pendingOutputs(),false,"",workerId));return true;
    }
    public synchronized boolean advance(UUID id,UUID workerId,String currentRevision,int workTicks) {
        if(workTicks<1)throw new IllegalArgumentException("Positive work required");
        var order=ownedWork(id,workerId,currentRevision);if(order==null)return false;
        int progress=(int)Math.min(order.recipe().ticks(),(long)order.progress()+workTicks);
        orders.put(id,copy(order,order.remainingBatches(),progress,order.ownedInputs(),order.pendingOutputs(),false,"",workerId));return true;
    }
    /** Same callback-free server commit as the host's validated FE debit; never debits shared matter again. */
    public synchronized boolean complete(UUID id,UUID workerId,String currentRevision) {
        var order=ownedWork(id,workerId,currentRevision);
        if(order==null||order.progress()<order.recipe().ticks())return false;
        var outputs=new TreeMap<>(order.pendingOutputs());order.recipe().outputs().forEach((key,amount)->outputs.merge(key,amount,Math::addExact));
        int remaining=order.remainingBatches()-1;
        var completed=copy(order,remaining,0,ResourceAmounts.multiply(order.recipe().inputs(),remaining),outputs,false,"",remaining==0?null:workerId);
        orders.put(id,completed);return true;
    }
    /** Process parameters may change without orphaning work: the same material vector continues under the current recipe. */
    public synchronized boolean rebase(UUID id,Recipe current) {
        Objects.requireNonNull(current);var order=orders.get(id);
        if(order==null||!pendingWork(order)||order.workerId()!=null||order.recipe().equals(current)
                ||!order.recipe().id().equals(current.id())||!order.recipe().workerKind().equals(current.workerKind())
                ||!order.recipe().inputs().equals(current.inputs())||!order.recipe().outputs().equals(current.outputs()))return false;
        var rebased=new Order(order.id(),current,order.batches(),order.remainingBatches(),0,order.ownedInputs(),order.pendingOutputs(),false,"",null);
        var future=new ArrayList<>(orders.values());future.replaceAll(existing->existing.id().equals(id)?rebased:existing);
        if(!OrderLedgerCodec.fits(new Snapshot(SCHEMA,future)))return false;
        orders.put(id,rebased);return true;
    }
    public synchronized boolean release(UUID id,UUID workerId) {
        var order=orders.get(id);if(order==null||workerId==null||!workerId.equals(order.workerId()))return false;
        orders.put(id,copy(order,order.remainingBatches(),0,order.ownedInputs(),order.pendingOutputs(),order.cancelled(),order.quarantineReason(),null));return true;
    }
    private Order ownedWork(UUID id,UUID workerId,String revision) {
        var order=orders.get(id);
        return order!=null&&pendingWork(order)&&workerId!=null&&workerId.equals(order.workerId())&&order.recipe().revision().equals(revision)?order:null;
    }
    public synchronized List<Delivery> pendingOutputs() {
        var result=new ArrayList<Delivery>();
        for(var order:orders.values())if(!order.quarantined())order.pendingOutputs().forEach((resource,amount)->result.add(new Delivery(order.id(),resource,amount)));
        return List.copyOf(result);
    }
    /** A reentrant save sees the entire order quarantined before any external resource insertion. */
    public synchronized boolean beginDelivery(UUID id,Resource resource,long offered) {
        Objects.requireNonNull(resource);
        var order=orders.get(id);
        if(order==null||order.quarantined()||offered<=0||offered>order.pendingOutputs().getOrDefault(resource,0L)
                ||deliveryAttempts.containsKey(id))return false;
        var guarded=copy(order,order.remainingBatches(),order.progress(),order.ownedInputs(),order.pendingOutputs(),
                order.cancelled(),DELIVERY_IN_FLIGHT,order.workerId());
        orders.put(id,guarded);
        deliveryAttempts.put(id,new Attempt(resource,offered,false));
        return true;
    }
    /** The same durable guard protects returning the owned inputs of a cancelled order. */
    public synchronized boolean beginRefund(UUID id,Resource resource,long offered) {
        Objects.requireNonNull(resource);
        var order=orders.get(id);
        if(order==null||!order.cancelled()||order.quarantined()||offered<=0||offered>order.ownedInputs().getOrDefault(resource,0L)
                ||deliveryAttempts.containsKey(id))return false;
        orders.put(id,copy(order,order.remainingBatches(),0,order.ownedInputs(),order.pendingOutputs(),true,DELIVERY_IN_FLIGHT,null));
        deliveryAttempts.put(id,new Attempt(resource,offered,true));
        return true;
    }
    /** Only the original live ledger can acknowledge an attempt, including a receiver accepting zero. */
    public synchronized void finishDelivery(UUID id,Resource resource,long accepted) {
        var order=attempted(id,resource,accepted,false);
        var outputs=subtract(order.pendingOutputs(),resource,accepted);
        deliveryAttempts.remove(id);
        putOrRemove(copy(order,order.remainingBatches(),order.progress(),order.ownedInputs(),outputs,order.cancelled(),"",order.workerId()));
    }
    /** Returns remain held until the external receiver confirms the exact actually accepted amounts. */
    public synchronized void finishRefund(UUID id,Resource resource,long accepted) {
        var order=attempted(id,resource,accepted,true);
        var inputs=subtract(order.ownedInputs(),resource,accepted);
        deliveryAttempts.remove(id);
        putOrRemove(copy(order,order.remainingBatches(),0,inputs,order.pendingOutputs(),true,"",null));
    }
    private Order attempted(UUID id,Resource resource,long accepted,boolean refund) {
        var order=required(id);var attempt=deliveryAttempts.get(id);
        if(attempt==null||attempt.refund()!=refund||!DELIVERY_IN_FLIGHT.equals(order.quarantineReason()))
            throw new IllegalStateException("No live delivery attempt");
        if(!attempt.resource().equals(resource)||accepted<0||accepted>attempt.offered())
            throw new IllegalArgumentException("Acknowledgement does not match the offered resources");
        return order;
    }
    /** Stops the remaining batches. Their owned inputs become refunds; completed outputs are still delivered. */
    public synchronized boolean cancel(UUID id) {
        var order=orders.get(id);
        if(order==null||order.cancelled()||order.quarantined())return false;
        putOrRemove(copy(order,order.remainingBatches(),0,order.ownedInputs(),order.pendingOutputs(),true,"",null));
        return true;
    }
    public synchronized List<Delivery> pendingRefunds() {
        var result=new ArrayList<Delivery>();
        for(var order:orders.values())if(order.cancelled()&&!order.quarantined())
            order.ownedInputs().forEach((resource,amount)->result.add(new Delivery(order.id(),resource,amount)));
        return List.copyOf(result);
    }
    public synchronized void quarantineDelivery(UUID id,String reason) {
        if(reason==null||reason.isBlank()||reason.length()>256)throw new IllegalArgumentException("Quarantine requires a bounded reason");
        var order=required(id);
        deliveryAttempts.remove(id);
        orders.put(id,copy(order,order.remainingBatches(),order.progress(),order.ownedInputs(),order.pendingOutputs(),order.cancelled(),reason,order.workerId()));
    }
    /** A stopped delivery that no live attempt still guards, so its owner may take the held resources by hand. */
    public synchronized Optional<Order> releasable(UUID id) {
        var order=orders.get(id);
        return order!=null&&order.quarantined()&&!deliveryAttempts.containsKey(id)?Optional.of(order):Optional.empty();
    }
    public synchronized Optional<Order> firstReleasable() {
        return orders.values().stream().filter(order->order.quarantined()&&!deliveryAttempts.containsKey(order.id())).findFirst();
    }
    /** Everything a stopped order still holds: its owned inputs and its undelivered outputs. */
    public static Map<Resource,Long> held(Order order) {
        var result=new TreeMap<>(order.ownedInputs());
        order.pendingOutputs().forEach((resource,amount)->result.merge(resource,amount,Math::addExact));
        return ResourceAmounts.copy(result);
    }
    /**
     * Records resources the owner received by hand from a stopped delivery. The remaining batches are cancelled,
     * inputs are taken before outputs, and the order ends once it holds nothing; anything left stays quarantined.
     */
    public synchronized boolean releaseHeld(UUID id,Map<Resource,Long> taken) {
        var order=releasable(id).orElse(null);
        if(order==null||order.workerId()!=null)return false;
        var remaining=ResourceAmounts.copy(taken);
        if(remaining.isEmpty()||!within(remaining,held(order)))return false;
        var inputs=new TreeMap<>(order.ownedInputs());var outputs=new TreeMap<>(order.pendingOutputs());
        remaining.forEach((resource,amount)->{
            long fromInputs=Math.min(amount,inputs.getOrDefault(resource,0L));
            take(inputs,resource,fromInputs);take(outputs,resource,amount-fromInputs);
        });
        putOrRemove(copy(order,order.remainingBatches(),0,inputs,outputs,true,order.quarantineReason(),null));
        return true;
    }
    private static void take(Map<Resource,Long> vector,Resource resource,long amount) {
        if(amount==0)return;
        long left=vector.get(resource)-amount;
        if(left==0)vector.remove(resource);else vector.put(resource,left);
    }
    private Order required(UUID id) {return Objects.requireNonNull(orders.get(id),"Unknown order");}
    private void putOrRemove(Order order) {
        if((order.remainingBatches()==0||order.cancelled())&&order.ownedInputs().isEmpty()&&order.pendingOutputs().isEmpty())orders.remove(order.id());
        else orders.put(order.id(),order);
    }
    private static Map<Resource,Long> subtract(Map<Resource,Long> vector,Resource resource,long accepted) {
        Objects.requireNonNull(resource);long held=vector.getOrDefault(resource,0L);
        if(accepted<0||accepted>held)throw new IllegalArgumentException("Acknowledgement exceeds held resources");
        var result=new TreeMap<>(vector);if(accepted==held)result.remove(resource);else result.put(resource,held-accepted);
        return result;
    }
    private static boolean within(Map<Resource,Long> values,Map<Resource,Long> maximum) {
        return values.entrySet().stream().allMatch(entry->entry.getValue()<=maximum.getOrDefault(entry.getKey(),0L));
    }
    private static Order copy(Order order,int remaining,int progress,Map<Resource,Long> inputs,Map<Resource,Long> outputs,
                              boolean cancelled,String quarantine,UUID worker) {
        return new Order(order.id(),order.recipe(),order.batches(),remaining,progress,inputs,outputs,cancelled,quarantine,worker);
    }
}
