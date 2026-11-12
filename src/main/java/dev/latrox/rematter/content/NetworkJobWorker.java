package dev.latrox.rematter.content;

import dev.latrox.rematter.automation.AutomationWorkerLease;
import dev.latrox.rematter.automation.OrderLedger;
import dev.latrox.rematter.content.preparation.PreparationTier;
import dev.latrox.rematter.content.preparation.PreparationWork;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Executes one transferred automation order at a time. The ledger owns its inputs; the machine pays only FE. */
final class NetworkJobWorker {
    private final MachineBlockEntity machine;
    private final UUID id = UUID.randomUUID();
    private final AutomationWorkerLease<MachineAutomationHost> lease = new AutomationWorkerLease<>();
    private @Nullable MachineMatterNetwork assignedTopology;
    private String signature = "";

    NetworkJobWorker(MachineBlockEntity machine) { this.machine = machine; }

    UUID id() { return id; }
    private int parallelBatches;
    int parallelRunning() {return parallelBatches;}
    boolean free() { return lease.current().isEmpty(); }
    boolean readyClaim() {
        var order=currentOrder();if(order==null||!machine.acceptsNetworkWork())return false;
        var recipe=order.recipe();
        return MachineAutomationRecipes.supports(machine,recipe)
                &&machine.energy().getAmountAsLong()>=machine.cost(baseEnergy(recipe.energy()));
    }
    boolean has(MachineAutomationHost host, UUID order) { return lease.matches(host, order); }
    boolean shouldYield(String backend, boolean otherBackendReady) { return lease.shouldYield(backend, otherBackendReady); }

    boolean tryBind(MachineAutomationHost host, UUID order) {
        if (lease.matches(host, order)) return true;
        if (lease.current().isPresent() || !machine.acceptsNetworkWork() || !host.active()) return false;
        var topology = machine.matterNetwork(true);
        if (!topology.ready() || !topology.interfaces().contains(host.machine()) || !lease.tryAcquire(host, order)) return false;
        machine.resetProgressRetainingTransfer(); assignedTopology = topology; signature = "";
        return true;
    }

    void release(MachineAutomationHost host, UUID order) {
        if (!lease.release(host, order)) return;
        assignedTopology = null; signature = ""; parallelBatches=0;
        machine.resetProgressRetainingTransfer(); machine.work().processedThisTick = false;
    }

    void release() {
        var claim = lease.current().orElse(null);
        if (claim != null) release(claim);
    }

    private void release(AutomationWorkerLease.Claim<MachineAutomationHost> claim) {
        claim.owner().release(claim.orderId(), machine);
        // Also clear an orphaned local claim, without ever touching another interface's binding.
        release(claim.owner(), claim.orderId());
    }

    /** The order this machine currently executes, revalidated by its interface. */
    OrderLedger.@Nullable Order currentOrder() {
        var claim = lease.current().orElse(null);
        return claim == null ? null : claim.owner().order(claim.orderId(), machine);
    }

    /** Recipe FE before upgrades, including the machine tier's preparation multiplier. */
    long baseEnergy(long recipeEnergy) {
        return MachineEnergyPolicy.scaled(recipeEnergy, PreparationTier.energyMultiplier(machine.kind()));
    }

    void tick() {
        var work = machine.work();
        if (!machine.acceptsNetworkWork()) {
            release();
            work.status = machine.industrialStructureReady() ? "automation_clear_slots" : IndustrialStructure.inspect(machine).diagnostic();
            return;
        }
        var claim = lease.current().orElse(null);
        if (claim == null) { work.status = "network_controlled"; return; }
        var host = claim.owner(); var orderId = claim.orderId();
        var order = host.order(orderId, machine);
        var topology = machine.matterNetwork(false);
        if (order == null || !topology.ready() || assignedTopology == null || !assignedTopology.sameTopology(topology)) {
            release(claim); work.status = "storage_changed"; return;
        }
        var recipe = order.recipe();
        if(!MachineAutomationRecipes.supports(machine,recipe)){host.cancelRestricted(orderId,machine);work.status="requires_research";return;}
        if(!machine.progressAllows(recipe.requiresAdvancement())) {host.cancelRestricted(orderId,machine);work.status="requires_advancement";return;}
        if(machine.crystallizationTier()<recipe.requiredTier()) {release(claim);work.status="requires_pressure";return;}
        long required = machine.cost(baseEnergy(recipe.energy()));
        int speed = machine.speed();
        String current = recipe.revision() + ":" + required + ":" + speed + ":" + machine.parallelCapacity();
        if (IndustrialStructure.isIndustrial(machine.kind())) current += ":" + IndustrialStructure.inspect(machine).layoutFingerprint();
        if (!signature.isEmpty() && !signature.equals(current)) { release(claim); work.status = "network_controlled"; return; }
        signature = current;
        var energy = machine.energy();
        if(parallelBatches==0){
            int capacity=MachineAutomationRecipes.capacity(machine,recipe,machine.parallelCapacity());
            var inputs=new java.util.TreeMap<dev.latrox.rematter.chemistry.SubstanceKey,Long>();recipe.inputs().forEach((key,amount)->{if(key.kind()==dev.latrox.rematter.automation.AutomationPort.Kind.MATTER)inputs.put(key.matterKey(),amount);});
            capacity=machine.capacityForInputs(inputs,capacity);
            parallelBatches=(int)Math.min(Math.min(capacity,order.remainingBatches()),energy.getAmountAsLong()/Math.max(1,required));
        }
        if (parallelBatches==0||energy.getAmountAsLong() < required*parallelBatches) { work.status = "insufficient_energy"; return; }
        var inMatter=recipe.inputs().entrySet().stream().filter(e->e.getKey().kind()==dev.latrox.rematter.automation.AutomationPort.Kind.MATTER).map(java.util.Map.Entry::getValue).toList();
        var outMatter=recipe.outputs().entrySet().stream().filter(e->e.getKey().kind()==dev.latrox.rematter.automation.AutomationPort.Kind.MATTER).map(java.util.Map.Entry::getValue).toList();
        boolean transferred=machine.advanceNetworkTransfer(orderId+":"+recipe.revision(),ControllerThroughput.units(inMatter,outMatter,parallelBatches));
        if (!host.advance(orderId, machine, speed)) { release(claim); work.status = "network_controlled"; return; }
        order = host.order(orderId, machine);
        if (order == null) { release(claim); work.status = "network_controlled"; return; }
        work.progress = order.progress(); work.duration = recipe.ticks(); work.processedThisTick = true;
        machine.visuals().capture(MachineVisuals.firstItem(recipe.inputs()), MachineVisuals.firstItem(recipe.outputs()), work.progress, work.duration);
        boolean preparation = PreparationTier.isPreparation(machine.kind());
        work.preparationStage = preparation ? PreparationWork.stage(work.progress, work.duration) : -1;
        work.preparationMixing = preparation && recipe.inputs().values().stream().mapToLong(Long::longValue).sum() > 1;
        work.status = preparation ? PreparationWork.stageName(work.preparationStage, work.preparationMixing, machine.kind() == MachineKind.SINTER_FURNACE) : "working";
        if(work.progress>=work.duration&&!transferred){work.status="cable_limited";return;}
        if (work.progress >= work.duration) {
            var fresh = machine.matterNetwork(true);
            if (!fresh.ready() || !assignedTopology.sameTopology(fresh) || !machine.acceptsNetworkWork() || energy.getAmountAsLong() < required) {
                release(claim); work.status = "storage_changed"; return;
            }
            // The ledger owns these inputs. Completion never withdraws the same matter or items again.
            int completed=parallelBatches;
            for(int lane=0;lane<completed;lane++) {
                if(lane>0&&!host.advance(orderId,machine,recipe.ticks())) {release(claim);return;}
                var finishing=host.order(orderId,machine);
                if(finishing==null||!host.complete(orderId,machine)) {release(claim);work.status="network_controlled";return;}
                energy.set((int)(energy.getAmountAsLong()-required));
                machine.guidance(dev.latrox.rematter.guide.GuidanceEvent.HANDS_OFF);
                if(machine.kind().synthesis()) {
                    machine.guidance(dev.latrox.rematter.guide.GuidanceEvent.PUT_IT_BACK);
                    if(recipe.requiredTier()>1)machine.guidance(dev.latrox.rematter.guide.GuidanceEvent.PROPER_TOOLS);
                }
                if(machine.kind()==MachineKind.INDUSTRIAL_REACTOR)
                    dev.latrox.rematter.ReMatter.chemistry().processes().stream()
                            .filter(process->dev.latrox.rematter.automation.AutomationRecipeCatalogue.id("process",process.id().toString()).equals(recipe.id())
                                    &&process.requiresKnownOutputs()&&process.type()==dev.latrox.rematter.chemistry.ProcessType.CRYSTALLIZATION)
                            .findFirst().ifPresent(process->machine.guidance(dev.latrox.rematter.guide.GuidanceEvent.GROWN_NOT_MINED));
                if(machine.kind()==MachineKind.DECOMPOSER)for(var input:recipe.inputs().keySet())if(input.kind()!=dev.latrox.rematter.automation.AutomationPort.Kind.MATTER)machine.learnMaterial(input.id(),dev.latrox.rematter.research.LearningPath.DECOMPOSITION);
                if(machine.kind()==MachineKind.REACTOR||machine.kind()==MachineKind.INDUSTRIAL_REACTOR){
                    var inputs=new java.util.TreeMap<dev.latrox.rematter.chemistry.SubstanceKey,Long>();var outputs=new java.util.TreeMap<dev.latrox.rematter.chemistry.SubstanceKey,Long>();
                    recipe.inputs().forEach((k,v)->{if(k.kind()==dev.latrox.rematter.automation.AutomationPort.Kind.MATTER)inputs.put(k.matterKey(),v);});recipe.outputs().forEach((k,v)->{if(k.kind()==dev.latrox.rematter.automation.AutomationPort.Kind.MATTER)outputs.put(k.matterKey(),v);});machine.learnProcess(inputs,outputs);
                }
                if(finishing.remainingBatches()==1)lease.completed(host,orderId,host.backend());
            }
            machine.completeNetworkTransfer();
            parallelBatches=0;host.changed(); work.progress = 0; work.duration = 0;
        }
        machine.setChanged();
    }
}
