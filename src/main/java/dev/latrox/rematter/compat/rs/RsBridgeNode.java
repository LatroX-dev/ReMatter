package dev.latrox.rematter.compat.rs;

import com.refinedmods.refinedstorage.api.autocrafting.*;
import com.refinedmods.refinedstorage.api.autocrafting.task.*;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.impl.node.patternprovider.PatternProviderNetworkNode;
import com.refinedmods.refinedstorage.api.network.storage.*;
import com.refinedmods.refinedstorage.api.resource.*;
import com.refinedmods.refinedstorage.api.storage.*;
import dev.latrox.rematter.automation.AutomationPort;
import dev.latrox.rematter.automation.AutomationRecipeCatalogue;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Native RS planner/provider; the host owns transferred inputs and performs real machine work. */
public final class RsBridgeNode extends PatternProviderNetworkNode implements StorageProvider {
    private final AutomationPort port;
    private final RsMatterStorage matter;
    private final Map<UUID,Entry> catalogue=new HashMap<>();
    private final Actor actor;
    private List<AutomationPort.Recipe> previous=List.of();
    private int previousSize;
    private boolean enabled=true;
    private boolean delivering;
    private java.util.function.Predicate<Task> taskAdmission=task->true;
    private record Entry(AutomationPort.Recipe recipe,Pattern pattern) {}
    public RsBridgeNode(AutomationPort port) {
        super(4,AutomationRecipeCatalogue.MAX_RECIPES);this.port=port;
        actor=()->"ReMatter";matter=new RsMatterStorage(port);
        setId(new ExternalPatternSinkId(port.bridgeId()));
    }
    @Override public Storage getStorage(){return matter.storage();}
    List<Pattern> patterns(){return catalogue.values().stream().map(Entry::pattern).toList();}
    public void disable(){enabled=false;refreshCatalogue();matter.refresh();}
    public void refreshCatalogue() {
        List<AutomationPort.Recipe> recipes=enabled&&port.active()?port.recipes():List.of();
        if(recipes.equals(previous)){matter.refresh();return;}
        catalogue.clear();int index=0;
        for(var recipe:recipes) {
            var inputs=recipe.inputs().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(e->new Ingredient(e.getValue(),List.of(RsResourceKeys.nativeKey(e.getKey())))).toList();
            var outputs=recipe.outputs().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(e->new ResourceAmount(RsResourceKeys.nativeKey(e.getKey()),e.getValue())).toList();
            UUID id=UUID.nameUUIDFromBytes((port.bridgeId()+"|"+recipe.id()+"|"+recipe.revision()).getBytes(StandardCharsets.UTF_8));
            Pattern pattern=new Pattern(id,PatternLayout.external(inputs,outputs));
            if(catalogue.put(id,new Entry(recipe,pattern))!=null)throw new IllegalStateException("Duplicate recipe identity");
            tryUpdatePattern(index++,pattern);
        }
        for(int i=index;i<previousSize;i++)tryUpdatePattern(i,null);
        previousSize=index;previous=recipes;matter.refresh();
    }
    @Override public ExternalPatternSink.Result insertAll(Pattern pattern,Collection<ResourceAmount> resources,Action action) {
        if(!enabled||!port.active()||port.busy())return ExternalPatternSink.Result.LOCKED;
        var entry=catalogue.get(pattern.id());
        if(entry==null||!entry.pattern().equals(pattern))return ExternalPatternSink.Result.SKIPPED;
        var transferred=new TreeMap<AutomationPort.Resource,Long>();
        try {
            for(var amount:resources) {
                var resource=RsResourceKeys.commonKey(amount.resource());
                if(resource.isEmpty()||amount.amount()<=0)return ExternalPatternSink.Result.REJECTED;
                transferred.merge(resource.get(),amount.amount(),Math::addExact);
            }
            int batches=exactBatches(entry.recipe().inputs(),transferred);
            if(batches==0)return ExternalPatternSink.Result.REJECTED;
            return port.acceptTransferred(entry.recipe().id(),entry.recipe().revision(),batches,transferred,RsMatterStorage.translate(action))
                    ?ExternalPatternSink.Result.ACCEPTED:ExternalPatternSink.Result.REJECTED;
        } catch(ArithmeticException invalid){return ExternalPatternSink.Result.REJECTED;}
    }
    static int exactBatches(Map<AutomationPort.Resource,Long> perBatch,Map<AutomationPort.Resource,Long> transferred) {
        if(!perBatch.keySet().equals(transferred.keySet()))return 0;
        long batches=0;
        for(var entry:perBatch.entrySet()) {
            long amount=transferred.get(entry.getKey());
            if(amount<=0||amount%entry.getValue()!=0)return 0;
            long count=amount/entry.getValue();
            if(count>64||batches!=0&&batches!=count)return 0;batches=count;
        }
        return Math.toIntExact(batches);
    }
    @Override public void doWork() {
        refreshCatalogue();
        if(!enabled||!port.active())return;
        boolean hadTasks=!getTasks().isEmpty();
        super.doWork();
        if(hadTasks||!getTasks().isEmpty())port.changed();
        if(!isActive()||getNetwork()==null)return;
        flushOutputs(getNetwork().getComponent(StorageNetworkComponent.class));
    }
    void setTaskAdmission(java.util.function.Predicate<Task> admission){taskAdmission=java.util.Objects.requireNonNull(admission);}
    /** RS has no way to refuse a planned task. A refused task is cancelled before its first step, so it never extracts anything. */
    @Override public void addTask(Task task) {
        super.addTask(task);
        boolean admitted;
        try {admitted=taskAdmission.test(task);} catch(RuntimeException invalid){admitted=false;}
        if(!admitted)super.cancelTask(task.getId());
        port.changed();
    }
    /** Restored tasks were admitted before they were saved. */
    void restoreTask(Task task){super.addTask(task);}
    @Override public void cancelTask(TaskId task){super.cancelTask(task);port.changed();}
    void flushOutputs(InsertableStorage storage) {
        if(delivering)return;
        delivering=true;
        try {
            for(var delivery:port.pendingOutputs()) {
                if(!enabled||!port.active())break;
                try {
                    if(!port.beginDelivery(delivery.orderId(),delivery.resource(),delivery.amount()))break;
                    long accepted=storage.insert(RsResourceKeys.nativeKey(delivery.resource()),delivery.amount(),Action.EXECUTE,actor);
                    if(accepted<0||accepted>delivery.amount())throw new IllegalStateException("Invalid network acceptance");
                    port.ackOutput(delivery.orderId(),delivery.resource(),accepted);
                } catch(RuntimeException uncertain){port.quarantineDelivery(delivery.orderId(),uncertain.getClass().getSimpleName());break;}
            }
        } finally {delivering=false;}
    }
}
