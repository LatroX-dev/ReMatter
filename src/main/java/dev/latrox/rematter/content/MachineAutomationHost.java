package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.automation.AutomationAdapter;
import dev.latrox.rematter.automation.AutomationBackends;
import dev.latrox.rematter.automation.AutomationPort;
import dev.latrox.rematter.automation.OrderLedger;
import dev.latrox.rematter.automation.OrderLedgerCodec;
import dev.latrox.rematter.chemistry.SubstanceKey;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/** The interface alone owns transferred inputs and undelivered outputs. Workers only hold temporary leases. */
public final class MachineAutomationHost implements AutomationPort {
    private final MachineBlockEntity machine;
    private UUID id = UUID.randomUUID();
    private String backend = "";
    private OrderLedger ledger = new OrderLedger();
    private @Nullable Tag lockedData;
    private CompoundTag adapterData = new CompoundTag();
    private @Nullable AutomationAdapter adapter;
    private Object chemistryIdentity, recipeIdentity;
    private Map<String,Recipe> catalogue = Map.of();
    private Map<String,Recipe> eligibleCatalogue = Map.of();
    private Set<MachineKind> eligibleWorkerKinds = Set.of();
    private List<Recipe> eligibleRecipes = List.of();
    private final Map<UUID,MachineBlockEntity> assigned = new HashMap<>();
    private final Map<UUID,Long> completedAt = new HashMap<>();
    private boolean closed, ticking, lease, catalogueInvalid;
    private long loadedAt = Long.MIN_VALUE;

    MachineAutomationHost(MachineBlockEntity machine) { this.machine = machine; }
    public MachineBlockEntity machine() { return machine; }
    public String backend() {
        if (backend.isEmpty()) backend = AutomationBackends.available().contains("ae2") ? "ae2"
                : AutomationBackends.available().stream().findFirst().orElse("");
        return backend;
    }
    @Override public UUID ownerId() { return machine.networkOwner(); }
    @Override public UUID bridgeId() { return id; }
    private boolean live() { return !closed && machine.networkLive() && ownerId() != null; }
    private boolean elected(MachineMatterNetwork network) {
        return network.ready() && !network.interfaces().isEmpty() && network.interfaces().getFirst() == machine;
    }
    @Override public boolean active() {
        return live() && lockedData == null && machine.automation() && lease
                && elected(machine.matterNetwork(false));
    }
    /** Only the selected optional backend can obtain a capability delegate. */
    public @Nullable AutomationAdapter adapter(String requested) {
        if (!live() || lockedData != null || !machine.automation() || !elected(machine.matterNetwork(false)) || !backend().equals(requested)) return null;
        ensureAdapter();
        return adapter;
    }
    private void ensureAdapter() {
        if (adapter != null || !live() || lockedData != null || backend().isEmpty()) return;
        adapter = AutomationBackends.create(backend(),machine,this);
        if (adapter != null && adapterData.contains(backend())) {
            var data = adapterData.getCompound(backend()).orElseThrow(() -> new IllegalStateException("Locked native adapter state"));
            adapter.load(TagValueInput.create(ProblemReporter.DISCARDING,machine.getLevel().registryAccess(),data));
        }
    }
    private void saveAdapter() {
        if (adapter == null || machine.getLevel() == null) return;
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,machine.getLevel().registryAccess());
        adapter.save(output);
        adapterData.put(adapter.id(),output.buildResult());
    }
    private void revoke() {
        lease = false;
        releaseAll();
        if (adapter != null) {
            saveAdapter();
            adapter.close();
            adapter = null;
            if (machine.getLevel() != null) machine.getLevel().invalidateCapabilities(machine.getBlockPos());
        }
    }
    public void close() { revoke(); closed = true; }
    public boolean cycleBackend() {
        if (!live() || lockedData != null || !ledger.snapshot().orders().isEmpty() || adapter != null && adapter.hasTasks()) return false;
        var choices=AutomationBackends.available(); if(choices.size()<2)return false;
        int current=choices.indexOf(backend());
        revoke(); backend=choices.get((current+1)%choices.size());
        changed(); return true;
    }
    public String status() {
        if(lockedData!=null)return "automation_data_locked";
        if(catalogueInvalid)return "automation_catalogue_invalid";
        if(!machine.automation())return "automation_off";
        var network=machine.matterNetwork(false);
        if(!network.ready())return network.status();
        if(!elected(network))return "automation_interface_conflict";
        if(backend().isEmpty()||!AutomationBackends.available().contains(backend()))return "automation_backend_missing";
        if(adapter==null||!adapter.connected())return "automation_disconnected";
        if(ledger.snapshot().orders().stream().anyMatch(OrderLedger.Order::quarantined))return "automation_quarantined";
        return ledger.snapshot().orders().isEmpty()?"ready":"network_controlled";
    }
    public void tick() {
        if(!live()||ticking||Transaction.getCurrentOpenedTransaction()!=null)return;
        ticking=true;
        try {
            var level=(ServerLevel)machine.getLevel();
            if(loadedAt==Long.MIN_VALUE)loadedAt=level.getGameTime();
            var network=machine.matterNetwork(false);
            if(lockedData!=null||!machine.automation()||!elected(network)) {revoke();return;}
            // Revoke old providers synchronously before publishing the chosen provider after a merge.
            for(var other:network.interfaces())if(other!=machine) {
                var host=other.automationHost(); if(host!=null)host.revoke();
            }
            lease=true;
            ensureAdapter();
            refreshCatalogue();
            validateClaims(network);
            if(adapter!=null)adapter.tick();
            if(adapter==null||!adapter.connected()) {releaseAll();return;}
            for(var worker:network.workers()) {
                if(!worker.acceptsNetworkWork()||assigned.containsValue(worker))continue;
                for(var order:ledger.snapshot().orders()) {
                    Recipe current=catalogue.get(order.recipe().id());
                    if(order.workerId()!=null||order.cancelled()||order.quarantined()||order.remainingBatches()==0
                            ||current==null||!current.equals(order.recipe())||!MachineAutomationRecipes.supports(worker.kind(),current))continue;
                    if(ledger.claim(order.id(),worker.networkWorkerId(),current.revision())) {
                        assigned.put(order.id(),worker);worker.bindNetworkOrder(this,order.id());changed();break;
                    }
                }
            }
        } finally { ticking=false; }
    }
    private void validateClaims(MachineMatterNetwork network) {
        for(var entry:new ArrayList<>(assigned.entrySet())) {
            var order=ledger.order(entry.getKey()).orElse(null);var worker=entry.getValue();
            if(order==null||order.remainingBatches()==0||!worker.acceptsNetworkWork()
                    ||!network.workers().contains(worker)||!Objects.equals(catalogue.get(order.recipe().id()),order.recipe())) release(entry.getKey(),worker);
        }
    }
    private void releaseAll() { for(var entry:new ArrayList<>(assigned.entrySet()))release(entry.getKey(),entry.getValue()); }
    void release(UUID orderId,MachineBlockEntity worker) {
        if(assigned.get(orderId)!=worker)return;
        ledger.release(orderId,worker.networkWorkerId());assigned.remove(orderId);worker.releaseNetworkOrder(this,orderId);changed();
    }
    OrderLedger.@Nullable Order order(UUID orderId,MachineBlockEntity worker) {
        if(!active()||adapter==null||!adapter.connected()||assigned.get(orderId)!=worker
                ||!machine.isCurrentNetworkWorker(ownerId(),worker))return null;
        refreshCatalogue();var order=ledger.order(orderId).orElse(null);
        return order!=null&&Objects.equals(catalogue.get(order.recipe().id()),order.recipe())?order:null;
    }
    boolean advance(UUID orderId,MachineBlockEntity worker,int ticks) {
        var order=order(orderId,worker);
        if(order==null)return false;
        boolean changed=ledger.advance(orderId,worker.networkWorkerId(),order.recipe().revision(),ticks);
        if(changed)changed();return changed;
    }
    /** Caller has just revalidated topology, worker identity, upgrades, FE and this exact recipe revision. */
    boolean complete(UUID orderId,MachineBlockEntity worker) {
        var order=order(orderId,worker);if(order==null)return false;
        if(!ledger.complete(orderId,worker.networkWorkerId(),order.recipe().revision()))return false;
        completedAt.put(orderId,machine.getLevel().getGameTime());return true;
    }
    private void refreshCatalogue() {
        if(!(machine.getLevel() instanceof ServerLevel level))return;
        Object chemistry=ReMatter.chemistry(),recipes=level.getServer().getRecipeManager().recipeMap();
        if(chemistryIdentity==chemistry&&recipeIdentity==recipes)return;
        var next=new LinkedHashMap<String,Recipe>();
        try {
            for(var recipe:MachineAutomationRecipes.catalogue(level))next.put(recipe.id(),recipe);
            catalogue=Map.copyOf(next);catalogueInvalid=false;
        } catch (IllegalArgumentException | ArithmeticException invalid) { catalogue=Map.of();catalogueInvalid=true; }
        chemistryIdentity=chemistry;recipeIdentity=recipes;
    }
    @Override public List<Recipe> recipes() {
        if(!active())return List.of();refreshCatalogue();
        var kinds=EnumSet.noneOf(MachineKind.class);
        for(var worker:machine.matterNetwork(false).workers())if(worker.networkJobs())kinds.add(worker.kind());
        if(eligibleCatalogue!=catalogue||!eligibleWorkerKinds.equals(kinds)) {
            eligibleRecipes=catalogue.values().stream().filter(recipe->kinds.stream()
                    .anyMatch(kind->MachineAutomationRecipes.supports(kind,recipe)))
                    .sorted(java.util.Comparator.comparing(Recipe::id)).toList();
            eligibleCatalogue=catalogue;eligibleWorkerKinds=Set.copyOf(kinds);
        }
        return eligibleRecipes;
    }
    @Override public boolean busy() { return !active()||ledger.busy(); }
    @Override public boolean acceptTransferred(String recipeId,String revision,int batches,Map<Resource,Long> inputs,Action action) {
        if(!active()||Transaction.getCurrentOpenedTransaction()!=null)return false;
        Recipe current=recipes().stream().filter(recipe->recipe.id().equals(recipeId)&&recipe.revision().equals(revision)).findFirst().orElse(null);
        if(current==null)return false;
        boolean accepted=ledger.accept(current,batches,inputs,action);
        if(accepted&&action==Action.EXECUTE)changed();return accepted;
    }
    @Override public Map<SubstanceKey,Long> availableEntries() {
        return active()?machine.matterNetwork(false).inventory().availableContents():Map.of();
    }
    @Override public long insert(SubstanceKey key,long maximum,Action action) {
        if(!active()||maximum<=0||Transaction.getCurrentOpenedTransaction()!=null)return 0;
        var network=machine.matterNetwork(true);if(!elected(network))return 0;
        long accepted=MachineBlockEntity.insertable(network.inventory(),key,maximum);
        if(accepted==0||action==Action.SIMULATE)return accepted;
        if(!network.inventory().apply(Map.of(),Map.of(key,accepted)))return 0;network.markChanged();changed();return accepted;
    }
    @Override public long extract(SubstanceKey key,long maximum,Action action) {
        if(!active()||maximum<=0||Transaction.getCurrentOpenedTransaction()!=null)return 0;
        var network=machine.matterNetwork(true);if(!elected(network))return 0;
        long extracted=Math.min(maximum,network.inventory().available(key));
        if(extracted==0||action==Action.SIMULATE)return extracted;
        if(!network.inventory().apply(Map.of(key,extracted),Map.of()))return 0;network.markChanged();changed();return extracted;
    }
    @Override public List<Delivery> pendingOutputs() {
        if(!active()||machine.getLevel().getGameTime()<=loadedAt)return List.of();
        long now=machine.getLevel().getGameTime();
        return ledger.pendingOutputs().stream().filter(delivery->completedAt.getOrDefault(delivery.orderId(),Long.MIN_VALUE)<now).toList();
    }
    @Override public boolean beginDelivery(UUID orderId,Resource resource,long offered) {
        if(!active()||offered<=0||Transaction.getCurrentOpenedTransaction()!=null)return false;
        boolean held=pendingOutputs().stream().anyMatch(delivery->delivery.orderId().equals(orderId)
                &&delivery.resource().equals(resource)&&delivery.amount()>=offered);
        if(!held||!ledger.beginDelivery(orderId,resource,offered))return false;
        // A save or block drop during the external write must retain the durable in-flight guard.
        changed();
        // Marking dirty can invoke neighbor callbacks. Never write externally after losing this source.
        return active();
    }
    @Override public void ackOutput(UUID orderId,Resource resource,long accepted) {
        // A topology change can revoke the lease after a valid external write; the same live source
        // may still record its exact acknowledgement. A detached copy must remain quarantined.
        if(!live()||lockedData!=null||accepted<0)throw new IllegalStateException("Output source unavailable");
        ledger.finishDelivery(orderId,resource,accepted);changed();
    }
    @Override public void quarantineDelivery(UUID orderId,String reason) {
        if(!live()||lockedData!=null)return;
        ledger.quarantineDelivery(orderId,reason);changed();
    }
    @Override public void changed() { machine.setChanged(); }
    public boolean hasContents() { return lockedData!=null||!ledger.snapshot().orders().isEmpty()||!adapterData.isEmpty()||adapter!=null&&adapter.hasTasks(); }
    Tag save() {
        if(lockedData!=null)return lockedData.copy();
        saveAdapter();var tag=new CompoundTag();tag.putInt("schema",1);tag.putString("id",id.toString());tag.putString("backend",backend());
        tag.putString("ledger",OrderLedgerCodec.write(ledger));tag.put("adapters",adapterData.copy());return tag;
    }
    void load(Tag raw) {
        revoke();closed=false;loadedAt=Long.MIN_VALUE;completedAt.clear();
        try {
            if (!(raw instanceof CompoundTag tag) || !tag.keySet().equals(Set.of("schema","id","backend","ledger","adapters"))
                    || !(tag.get("schema") instanceof IntTag) || !(tag.get("id") instanceof StringTag)
                    || !(tag.get("backend") instanceof StringTag) || !(tag.get("ledger") instanceof StringTag)
                    || !(tag.get("adapters") instanceof CompoundTag)) throw new IllegalArgumentException("Unknown interface payload");
            var nativeData=(CompoundTag)tag.get("adapters");
            for(String backendId:nativeData.keySet())if(!(nativeData.get(backendId) instanceof CompoundTag))
                throw new IllegalArgumentException("Unknown native adapter payload");
            if(tag.getIntOr("schema",-1)!=1)throw new IllegalArgumentException("Unknown interface schema");
            UUID loadedId=UUID.fromString(tag.getStringOr("id",""));String loadedBackend=tag.getStringOr("backend","");
            if(!loadedBackend.isEmpty()&&!List.of("ae2","rs").contains(loadedBackend))throw new IllegalArgumentException("Unknown backend");
            var read=OrderLedgerCodec.read(tag.getStringOr("ledger",""));if(!read.readable())throw new IllegalArgumentException("Unknown queue");
            ledger=read.ledger().orElseThrow();id=loadedId;backend=loadedBackend;adapterData=tag.getCompound("adapters").orElse(new CompoundTag()).copy();lockedData=null;
            // Leases are runtime-only. Every unfinished batch restarts unpaid against freshly validated workers.
            for(var order:ledger.snapshot().orders())if(order.workerId()!=null)ledger.release(order.id(),order.workerId());
        } catch(RuntimeException unknown) { lockedData=raw.copy(); }
    }
}
